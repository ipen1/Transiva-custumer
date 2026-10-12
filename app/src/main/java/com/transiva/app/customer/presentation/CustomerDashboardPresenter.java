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
    private long generation;

    public CustomerDashboardPresenter(
            CustomerDashboardRepository repository,
            CustomerDashboardContract.View view
    ) {
        this.repository = repository;
        this.view = view;
    }

    public boolean isLoading() { return loading; }

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
        final long requestGeneration=++generation;
        loading = true;
        if (visibleLoading) view.showLoading(true);

        try {
        currentTask = TransivaNetworkExecutor.execute(() -> {
            try {
                DashboardState state = repository.load(username, userId);
                main.post(() -> {
                    if(requestGeneration!=generation||view==null)return;
                    loading = false;
                    lastSuccessAt = SystemClock.elapsedRealtime();
                    if (view == null) return;
                    view.showLoading(false);
                    view.showDashboard(state);
                });
            } catch (Exception error) {
                main.post(() -> {
                    if(requestGeneration!=generation||view==null)return;
                    loading = false;
                    if (view == null) return;
                    view.showLoading(false);
                    view.showError("Dashboard gagal dimuat. Periksa koneksi.");
                });
            }
        }, () -> main.post(() -> {
            if(requestGeneration!=generation||view==null)return;
            loading = false;
            if (view != null) { view.showLoading(false); view.showError("Aplikasi sedang sibuk. Coba muat ulang sebentar lagi."); }
        }));
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            loading = false;
            if (view != null) {
                view.showLoading(false);
                view.showError("Aplikasi sedang sibuk. Coba muat ulang sebentar lagi.");
            }
        }
    }

    public void cancelLoad() {
        ++generation;Future<?> task=currentTask;currentTask=null;if(task!=null)task.cancel(true);
        loading=false;if(view!=null)view.showLoading(false);
    }
    public void destroy() {
        view = null;
        cancelLoad();
        Future<?> task = currentTask;
        if (task != null) task.cancel(true);
        currentTask = null;
        main.removeCallbacksAndMessages(null);
    }
}
