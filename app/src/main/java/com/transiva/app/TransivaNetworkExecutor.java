package com.transiva.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded worker pool to prevent unbounded manual network threads. */
public final class TransivaNetworkExecutor {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "transiva-net-" + IDS.incrementAndGet());
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((thread, error) ->
                TransivaCrashReporter.record(error, "network_worker_uncaught", thread.getName()));
        return t;
    };

    // 4 workers are enough for mobile HTTP I/O. Queue is deliberately bounded so
    // a bad network cannot create hundreds of pending tasks and OOM the process.
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            4, 4, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), FACTORY,
            new ThreadPoolExecutor.CallerRunsPolicy());

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

    public static int queuedTasks() { return POOL.getQueue().size(); }
}
