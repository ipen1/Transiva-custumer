package com.transiva.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 2 image-only worker pool.
 * Keeps image download/decode/disk-cache work away from transactional API workers.
 */
public final class TransivaImageExecutor {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadFactory FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "transiva-image-" + IDS.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.setUncaughtExceptionHandler((t, error) ->
                TransivaCrashReporter.record(error, "image_worker_uncaught", t.getName()));
        return thread;
    };

    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            2, 2, 20L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(40),
            FACTORY,
            (task, executor) -> {
                if (executor.isShutdown()) {
                    throw new RejectedExecutionException("Transiva image pool is shutdown");
                }
                Runnable dropped = executor.getQueue().poll();
                if (dropped instanceof Future<?>) {
                    try { ((Future<?>) dropped).cancel(true); } catch (Throwable ignored) {}
                }
                // Prefer the newest/bound-to-current-screen image. Never run on the UI caller.
                if (!executor.getQueue().offer(task)) {
                    throw new RejectedExecutionException("Transiva image queue saturated");
                }
            });

    static { POOL.allowCoreThreadTimeOut(true); }

    private TransivaImageExecutor() {}

    public static Future<?> execute(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task == null");
        return POOL.submit(() -> {
            try {
                task.run();
            } catch (Throwable error) {
                TransivaCrashReporter.record(error, "image_worker", "image_task");
                throw error;
            }
        });
    }

    public static int queuedTasks() { return POOL.getQueue().size(); }
    public static int activeTasks() { return POOL.getActiveCount(); }
}
