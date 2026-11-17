package com.transiva.app;

import android.os.SystemClock;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Deadline for a single read. Socket cancellation never runs on the UI thread. */
final class CustomerReadDeadline implements AutoCloseable {
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(2, task -> {
        Thread thread = new Thread(task, "transiva-http-deadline");
        thread.setDaemon(true);
        return thread;
    });
    static { TIMER.setRemoveOnCancelPolicy(true); }

    final long endAt;
    private final AtomicReference<HttpURLConnection> socket = new AtomicReference<>();
    private final ScheduledFuture<?> timeout;

    CustomerReadDeadline(int budgetMs) {
        endAt = SystemClock.elapsedRealtime() + budgetMs;
        timeout = TIMER.schedule(() -> {
            HttpURLConnection connection = socket.getAndSet(null);
            if (connection != null) connection.disconnect();
        }, budgetMs, TimeUnit.MILLISECONDS);
    }

    void attach(HttpURLConnection connection) throws SocketTimeoutException {
        socket.set(connection);
        if (SystemClock.elapsedRealtime() >= endAt) {
            socket.compareAndSet(connection, null);
            throw new SocketTimeoutException("Waktu permintaan habis");
        }
    }

    void detach(HttpURLConnection connection) {
        socket.compareAndSet(connection, null);
    }

    @Override public void close() {
        timeout.cancel(false);
    }
}
