package com.transiva.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded worker pool to prevent unbounded manual network threads. */
public final class TransivaNetworkExecutor {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final AtomicInteger REJECTED = new AtomicInteger();
    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "transiva-net-" + IDS.incrementAndGet());
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((thread, error) ->
                TransivaCrashReporter.record(error, "network_worker_uncaught", thread.getName()));
        return t;
    };

    /* Accepted work is never evicted. Saturation rejects only the incoming task. */
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            4, 6, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), FACTORY, new ThreadPoolExecutor.AbortPolicy());

    static { POOL.allowCoreThreadTimeOut(true); }

    private TransivaNetworkExecutor() {}

    public static Future<?> execute(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task == null");
        return admit(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                if (!CustomerAsyncError.isCancellation(error)) {
                    TransivaCrashReporter.record(error, "network_worker", "worker_task");
                }
                throw error;
            }
            return null;
        });
    }

    public static <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new IllegalArgumentException("task == null");
        return admit(() -> {
            try {
                return task.call();
            } catch (Throwable error) {
                if (!CustomerAsyncError.isCancellation(error)) {
                    TransivaCrashReporter.record(error, "network_worker", "callable_task");
                }
                if (error instanceof Exception) throw (Exception) error;
                throw new RuntimeException(error);
            }
        });
    }

    private static <T> Future<T> admit(Callable<T> task) {
        java.util.concurrent.FutureTask<T> future = new java.util.concurrent.FutureTask<>(task);
        try {
            if (POOL.getQueue().remainingCapacity() == 0) POOL.purge();
            POOL.execute(future);
        }
        catch (RejectedExecutionException error) {
            REJECTED.incrementAndGet();
            // Caller receives a completed exceptional Future; no previously accepted task is canceled.
            future = new java.util.concurrent.FutureTask<>(() -> { throw error; });
            future.run(); // Failure completion only: no network or caller task is executed here.
            TransivaCrashReporter.record(error, "network_admission_rejected", "queue_full");
            android.app.Application app = TransivaCustomerApplication.appContext();
            if (app != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(app, "Aplikasi sedang sibuk. Coba kembali sebentar lagi.", android.widget.Toast.LENGTH_LONG).show());
        }
        return future;
    }

    /** Explicit rejection callback for UI operations that need to reset progress controls. */
    public static Future<?> execute(Runnable task, Runnable onRejected) {
        Future<?> future = execute(task);
        if (future.isDone()) {
            try { future.get(); }
            catch (java.util.concurrent.ExecutionException error) {
                if (error.getCause() instanceof RejectedExecutionException && onRejected != null) onRejected.run();
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (java.util.concurrent.CancellationException ignored) { }
        }
        return future;
    }

    public static int queuedTasks() { return POOL.getQueue().size(); }
    public static int activeTasks() { return POOL.getActiveCount(); }
    public static int rejectedTasks() { return REJECTED.get(); }
    public static boolean isSaturated() { return activeTasks() >= 6 && queuedTasks() >= 56; }
}
