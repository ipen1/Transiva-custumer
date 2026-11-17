package com.transiva.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Core reads can start even while optional cards or profile prefetch are still waiting. */
public final class DashboardReadExecutor {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadPoolExecutor CORE = pool(2, 8);
    private static final ThreadPoolExecutor OPTIONAL = pool(3, 24);

    private DashboardReadExecutor() {}

    private static ThreadPoolExecutor pool(int threads, int capacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 20L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    Thread thread = new Thread(task, "transiva-dashboard-read-" + IDS.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static <T> Future<T> submit(Callable<T> task) {
        return submit(OPTIONAL, task);
    }

    public static <T> Future<T> submitCritical(Callable<T> task) {
        return submit(CORE, task);
    }

    private static <T> Future<T> submit(ThreadPoolExecutor pool, Callable<T> task) {
        android.app.Application app = TransivaCustomerApplication.appContext();
        CustomerTransportSession owner = app == null ? null : CustomerTransportSession.capture(app);
        pool.purge();
        return pool.submit(() -> {
            try {
                if (owner != null) {
                    owner.check(app);
                    CustomerTransportSession.bind(owner);
                }
                return task.call();
            } finally {
                CustomerTransportSession.clearOwner();
            }
        });
    }
}
