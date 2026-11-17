package com.transiva.app.customer.presentation;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.transiva.app.CustomerReadError;
import com.transiva.app.DashboardReadExecutor;
import com.transiva.app.TransivaNetworkMonitor;
import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.DashboardState.Section;

import org.json.JSONObject;

import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

public final class CustomerDashboardPresenter {
    private static final long MIN_REFRESH_MS = 12000L;
    private static final int REQUEST_MS = 5000;
    private static final int OPTIONAL_QUEUE_MS = 10000;
    private static final Section[] BASE_SECTIONS = {
            Section.BALANCE, Section.ORDER, Section.PROMOS, Section.LOYALTY, Section.BEST_OFFER
    };

    private final CustomerDashboardRepository repository;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnumMap<Section, Load> pending = new EnumMap<>(Section.class);
    private final EnumMap<Section, Throwable> errors = new EnumMap<>(Section.class);
    private final EnumMap<Section, Long> retryAt = new EnumMap<>(Section.class);
    private final Runnable retry = this::retryFailedReads;
    private CustomerDashboardContract.View view;
    private final int userId;
    private long lastRefreshAt;
    private long lastOrderAt;
    private boolean visibleRefresh;
    private boolean referralAfterBalance;
    private boolean retryAfterCurrent;
    private JSONObject activeOrder;

    private static final class Load {
        Future<?> work;
        Runnable timeout;
    }

    public CustomerDashboardPresenter(CustomerDashboardRepository repository,
                                      CustomerDashboardContract.View view, int userId) {
        this.repository = repository;
        this.view = view;
        this.userId = userId;
    }

    public boolean isLoading() { return visibleRefresh; }

    public void load() { beginRefresh(true); }

    public void refresh() {
        long now = SystemClock.elapsedRealtime();
        if (!pending.isEmpty() || (lastRefreshAt != 0L && now - lastRefreshAt < MIN_REFRESH_MS)) return;
        beginRefresh(false);
    }

    private void beginRefresh(boolean visible) {
        if (view == null || visibleRefresh) return;
        visibleRefresh = visible;
        lastRefreshAt = SystemClock.elapsedRealtime();
        for (Section section : BASE_SECTIONS) start(section);
        // Referral may reconcile a reward. Its balance snapshot must follow the base balance read.
        referralAfterBalance = pending.containsKey(Section.BALANCE);
        if (!referralAfterBalance) start(Section.REFERRAL);
        updateProgress();
    }

    public void refreshBalance() { start(Section.BALANCE); }

    public void pollOrder() {
        if (activeOrder != null && SystemClock.elapsedRealtime() - lastOrderAt >= MIN_REFRESH_MS
                && TransivaNetworkMonitor.isOnline()) start(Section.ORDER);
    }

    public void refreshAfterCancellation() {
        cancel(Section.ORDER);
        cancel(Section.ORDER_DETAILS);
        activeOrder = null;
        start(Section.ORDER);
        updateProgress();
    }

    public void networkRestored() {
        if (!pending.isEmpty()) retryAfterCurrent = true;
        main.removeCallbacks(retry);
        main.postDelayed(retry, 350L);
    }

    public void stopRecovery() {
        main.removeCallbacks(retry);
        retryAfterCurrent = false;
    }

    private void retryFailedReads() {
        if (view == null || !TransivaNetworkMonitor.isOnline()) return;
        if (!pending.isEmpty()) {
            retryAfterCurrent = true;
            return;
        }
        retryAfterCurrent = false;
        long earliest = Long.MAX_VALUE;
        long now = SystemClock.elapsedRealtime();
        for (Section section : Section.values()) {
            Throwable error = errors.get(section);
            if (!CustomerReadError.canRetryOnReconnect(error)) continue;
            long remaining = retryRemaining(section, now);
            if (remaining > 0L) earliest = Math.min(earliest, remaining);
            else start(section);
        }
        if (earliest != Long.MAX_VALUE) main.postDelayed(retry, earliest);
    }

    private void start(Section section) {
        if (view == null || pending.containsKey(section)) return;
        long now = SystemClock.elapsedRealtime();
        long remainingRetry = retryRemaining(section, now);
        if (remainingRetry > 0L) {
            view.showError(section, CustomerReadError.message(errors.get(section), remainingRetry, failureMessage(section)));
            return;
        }
        if (section == Section.ORDER) lastOrderAt = now;
        final JSONObject order = activeOrder;
        if (section == Section.ORDER_DETAILS && order == null) return;

        Load load = new Load();
        pending.put(section, load);
        if (!TransivaNetworkMonitor.isOnline()) {
            main.post(() -> complete(section, load, null, new UnknownHostException("Offline")));
            return;
        }

        int maxWait = isCore(section) ? REQUEST_MS + 500 : OPTIONAL_QUEUE_MS;
        long deadline = now + maxWait;
        load.timeout = () -> {
            if (load.work != null) load.work.cancel(true);
            complete(section, load, null, new TimeoutException());
        };
        main.postDelayed(load.timeout, maxWait);
        try {
            java.util.concurrent.Callable<Void> request = () -> {
                try {
                    int remaining = (int) Math.min(REQUEST_MS, deadline - SystemClock.elapsedRealtime());
                    if (remaining <= 0) throw new TimeoutException();
                    DashboardState state = repository.load(section, userId, order, remaining);
                    main.post(() -> complete(section, load, state, null));
                } catch (Exception error) {
                    main.post(() -> complete(section, load, null, error));
                }
                return null;
            };
            load.work = isCore(section) ? DashboardReadExecutor.submitCritical(request)
                    : DashboardReadExecutor.submit(request);
        } catch (RejectedExecutionException busy) {
            main.post(() -> complete(section, load, null, busy));
        }
    }

    private void complete(Section section, Load load, DashboardState state, Throwable error) {
        if (view == null || pending.get(section) != load) return;
        pending.remove(section);
        if (load.timeout != null) main.removeCallbacks(load.timeout);
        if (error == null) {
            errors.remove(section);
            retryAt.remove(section);
            if (section == Section.ORDER) {
                cancel(Section.ORDER_DETAILS);
                activeOrder = state.activeOrder;
                if (activeOrder == null || activeOrder.has("smart_waiting")) {
                    errors.remove(Section.ORDER_DETAILS);
                    retryAt.remove(Section.ORDER_DETAILS);
                }
            }
            view.showSection(section, state, true);
            if (section == Section.ORDER && activeOrder != null && !activeOrder.has("smart_waiting")) {
                start(Section.ORDER_DETAILS);
            }
        } else {
            errors.put(section, error);
            long delay = CustomerReadError.retryDelay(error);
            if (delay > 0L) retryAt.put(section, SystemClock.elapsedRealtime() + delay);
            view.showError(section, CustomerReadError.message(error, delay, failureMessage(section)));
        }
        if (section == Section.BALANCE && referralAfterBalance) {
            referralAfterBalance = false;
            start(Section.REFERRAL);
        }
        updateProgress();
        if (pending.isEmpty() && retryAfterCurrent) {
            retryAfterCurrent = false;
            main.removeCallbacks(retry);
            main.post(retry);
        }
    }

    private void updateProgress() {
        if (view == null || !visibleRefresh) return;
        boolean coreLoading = pending.containsKey(Section.BALANCE) || pending.containsKey(Section.ORDER);
        view.showLoading(coreLoading);
        if (!coreLoading) {
            visibleRefresh = false;
            view.finishRefresh(!errors.containsKey(Section.BALANCE) && !errors.containsKey(Section.ORDER));
        }
    }

    private static boolean isCore(Section section) {
        return section == Section.BALANCE || section == Section.ORDER;
    }

    private long retryRemaining(Section section, long now) {
        Long available = retryAt.get(section);
        return available == null ? 0L : Math.max(0L, available - now);
    }

    private static String failureMessage(Section section) {
        if (section == Section.BALANCE) return "Saldo belum dapat dimuat.";
        if (section == Section.ORDER) return "Status pesanan belum dapat dimuat.";
        return "Bagian beranda belum dapat dimuat.";
    }

    private void cancel(Section section) {
        Load load = pending.remove(section);
        if (load == null) return;
        if (load.timeout != null) main.removeCallbacks(load.timeout);
        if (load.work != null) load.work.cancel(true);
    }

    public void cancelLoad() {
        for (Section section : Section.values()) cancel(section);
        referralAfterBalance = false;
        stopRecovery();
        visibleRefresh = false;
        if (view != null) view.showLoading(false);
    }

    public void destroy() {
        view = null;
        cancelLoad();
        main.removeCallbacksAndMessages(null);
    }
}
