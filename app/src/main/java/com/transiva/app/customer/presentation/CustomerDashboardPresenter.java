package com.transiva.app.customer.presentation;

import android.os.Handler;
import android.os.Looper;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;

import android.os.SystemClock;
import java.util.concurrent.Future;
import com.transiva.app.TransivaNetworkExecutor;

public final class CustomerDashboardPresenter {

    private final CustomerDashboardRepository repository;
    private CustomerDashboardContract.View view;
    private Future<?> currentTask;
    private volatile long lastSuccessAt;
    private static final long MIN_REFRESH_MS = 12000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean loading;

    public CustomerDashboardPresenter(
            CustomerDashboardRepository repository,
            CustomerDashboardContract.View view
    ) {
        this.repository = repository;
        this.view = view;
    }

    public void load(String username, int userId) {
        request(username, userId, true);
    }

    public void refresh(String username, int userId) {
        long now = SystemClock.elapsedRealtime();
        if (lastSuccessAt > 0L && now - lastSuccessAt < MIN_REFRESH_MS) return;
        request(username, userId, false);
    }

    private void request(String username, int userId, boolean visibleLoading) {
        if (loading || view == null) return;
        loading = true;
        if (visibleLoading) view.showLoading(true);

        currentTask = TransivaNetworkExecutor.execute(() -> {
            try {
                DashboardState state = repository.load(username, userId);
                main.post(() -> {
                    loading = false;
                    lastSuccessAt = SystemClock.elapsedRealtime();
                    if (view == null) return;
                    view.showLoading(false);
                    view.showDashboard(state);
                });
            } catch (Exception error) {
                main.post(() -> {
                    loading = false;
                    if (view == null) return;
                    view.showLoading(false);
                    view.showError("Dashboard gagal dimuat. Periksa koneksi.");
                });
            }
        });
    }

    public void destroy() {
        view = null;
        Future<?> task = currentTask;
        if (task != null) task.cancel(true);
        currentTask = null;
        main.removeCallbacksAndMessages(null);
    }
}
