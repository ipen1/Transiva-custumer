package com.transiva.app.customer.presentation;

import android.os.Handler;
import android.os.Looper;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CustomerDashboardPresenter {

    private final CustomerDashboardRepository repository;
    private CustomerDashboardContract.View view;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
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
        request(username, userId, false);
    }

    private void request(String username, int userId, boolean visibleLoading) {
        if (loading || view == null) return;
        loading = true;
        if (visibleLoading) view.showLoading(true);

        executor.execute(() -> {
            try {
                DashboardState state = repository.load(username, userId);
                main.post(() -> {
                    loading = false;
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
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
