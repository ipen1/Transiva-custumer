package com.transiva.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded worker pool to prevent unbounded manual network threads. */
public final class TransivaNetworkExecutor {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "transiva-net-" + IDS.incrementAndGet());
        t.setDaemon(true);
        return t;
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, FACTORY);
    private TransivaNetworkExecutor() {}
    public static Future<?> execute(Runnable task) { return POOL.submit(task); }
}
