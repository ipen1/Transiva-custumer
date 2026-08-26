package com.transiva.app;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Handler;

/**
 * Activity-owned network/thread scope. Tasks are interrupted/cancelled when the Activity is
 * destroyed so late callbacks cannot continue doing expensive work against a dead screen.
 * This does not alter business state or database order statuses.
 */
public final class CustomerLifecycleNetworkScope {
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final Set<Future<?>> futures = ConcurrentHashMap.newKeySet();
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();

    public Future<?> execute(Runnable task) {
        if (task == null || destroyed.get()) return null;
        final Future<?>[] holder = new Future<?>[1];
        Future<?> future = TransivaNetworkExecutor.execute(() -> {
            if (destroyed.get() || Thread.currentThread().isInterrupted()) return;
            try {
                task.run();
            } finally {
                Future<?> f = holder[0];
                if (f != null) futures.remove(f);
            }
        });
        holder[0] = future;
        if (future != null) {
            if (destroyed.get()) future.cancel(true);
            else {
                futures.add(future);
                if (future.isDone()) futures.remove(future);
            }
        }
        return future;
    }

    /** Compatibility path for existing named/manual Thread code while making it lifecycle-owned. */
    public Thread newThread(Runnable task) { return newThread(task, null); }

    public Thread newThread(Runnable task, String name) {
        Runnable wrapped = () -> {
            if (destroyed.get() || Thread.currentThread().isInterrupted()) return;
            try { task.run(); } finally { threads.remove(Thread.currentThread()); }
        };
        Thread t = name == null ? new Thread(wrapped) : new Thread(wrapped, name);
        if (destroyed.get()) { t.interrupt(); return t; }
        threads.add(t);
        return t;
    }


    public boolean post(Handler handler, Runnable task) {
        if (handler == null || task == null || destroyed.get()) return false;
        return handler.post(() -> {
            if (!destroyed.get()) task.run();
        });
    }

    public boolean isDestroyed() { return destroyed.get(); }

    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) return;
        for (Future<?> f : futures) {
            try { f.cancel(true); } catch (Exception ignored) {}
        }
        futures.clear();
        for (Thread t : threads) {
            try { t.interrupt(); } catch (Exception ignored) {}
        }
        threads.clear();
    }
}
