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

    /*
     * P0: never execute network work on the caller/UI thread.
     * When saturated, cancel the oldest queued FutureTask and admit the newest task.
     * The rejection handler performs queue operations only; it never runs task.run().
     */
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            4, 4, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), FACTORY,
            (task, executor) -> {
                if (executor.isShutdown()) throw new RejectedExecutionException("Transiva network pool is shutdown");
                Runnable dropped = executor.getQueue().poll();
                if (dropped instanceof Future<?>) {
                    try { ((Future<?>) dropped).cancel(true); } catch (Throwable ignored) {}
                }
                REJECTED.incrementAndGet();
                if (!executor.getQueue().offer(task)) {
                    throw new RejectedExecutionException("Transiva network queue saturated");
                }
            });

    static { POOL.allowCoreThreadTimeOut(true); }

    private TransivaNetworkExecutor() {}

    public static Future<?> execute(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task == null");
        return POOL.submit(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                TransivaCrashReporter.record(error, "network_worker", "worker_task");
                throw error;
            }
        });
    }

    public static <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new IllegalArgumentException("task == null");
        return POOL.submit(() -> {
            try {
                return task.call();
            } catch (Throwable error) {
                TransivaCrashReporter.record(error, "network_worker", "callable_task");
                if (error instanceof Exception) throw (Exception) error;
                throw new RuntimeException(error);
            }
        });
    }

    public static int queuedTasks() { return POOL.getQueue().size(); }
    public static int activeTasks() { return POOL.getActiveCount(); }
    public static int rejectedTasks() { return REJECTED.get(); }
    public static boolean isSaturated() { return activeTasks() >= 4 && queuedTasks() >= 56; }
}
