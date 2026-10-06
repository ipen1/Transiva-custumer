package com.transiva.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Leaf reads only: dashboard coordinators must never execute in this pool. */
public final class DashboardReadExecutor {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            3, 3, 20L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24), task -> {
                Thread thread = new Thread(task, "transiva-dashboard-read-" + IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    static { POOL.allowCoreThreadTimeOut(true); }
    private DashboardReadExecutor() { }
    public static <T> Future<T> submit(Callable<T> task) { return POOL.submit(task); }
}
