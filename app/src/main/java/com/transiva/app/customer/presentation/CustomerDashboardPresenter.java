package com.transiva.app.customer.presentation;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.transiva.app.TransivaHttpRepository.ServerResponseException;
import com.transiva.app.TransivaNetworkExecutor;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

public final class CustomerDashboardPresenter {

    private final CustomerDashboardRepository repository;
    private CustomerDashboardContract.View view;
    private Future<?> currentTask;
    private volatile long lastFinishedAt;
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
        if (lastFinishedAt > 0L && now - lastFinishedAt < MIN_REFRESH_MS) return;
        request(username, userId, false);
    }

    public void refreshAfterCancellation(String username, int userId) {
        cancelLoad();
        lastFinishedAt = 0L;
        request(username, userId, false);
    }

    private void request(String username, int userId, boolean visibleLoading) {
        if (loading || view == null) return;
        final long requestGeneration = ++generation;
        loading = true;
        if (visibleLoading) view.showLoading(true);

        try {
            currentTask = TransivaNetworkExecutor.execute(() -> {
                try {
                    DashboardState state = repository.load(username, userId);
                    main.post(() -> complete(requestGeneration, state, null));
                } catch (Exception error) {
                    main.post(() -> complete(requestGeneration, null, errorMessage(error)));
                }
            }, () -> main.post(() -> complete(requestGeneration, null,
                    "Aplikasi sedang sibuk.")));
        } catch (RejectedExecutionException busy) {
            complete(requestGeneration, null, "Aplikasi sedang sibuk.");
        }
    }

    private void complete(long requestGeneration, DashboardState state, String message) {
        if (requestGeneration != generation || view == null) return;
        currentTask = null;
        loading = false;
        lastFinishedAt = SystemClock.elapsedRealtime();
        view.showLoading(false);
        if (state != null) view.showDashboard(state);
        else view.showError(message);
    }

    private String errorMessage(Exception error) {
        Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
        if (cause instanceof ServerResponseException) {
            int status = ((ServerResponseException) cause).status;
            if (status == 401 || status == 403) return "Akses akun perlu diperiksa.";
            if (status == 429) return "Server sedang sibuk. Coba beberapa saat lagi.";
            return "Server belum dapat memuat data beranda.";
        }
        if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException) {
            return "Respons server terlalu lama.";
        }
        if (cause instanceof IOException) return "Koneksi ke server terganggu.";
        return "Data beranda belum dapat dimuat.";
    }

    public void cancelLoad() {
        ++generation;
        Future<?> task = currentTask;
        currentTask = null;
        if (task != null) task.cancel(true);
        loading = false;
        if (view != null) view.showLoading(false);
    }
    public void destroy() {
        view = null;
        cancelLoad();
        main.removeCallbacksAndMessages(null);
    }
}
