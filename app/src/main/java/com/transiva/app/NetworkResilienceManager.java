package com.transiva.app;

import android.content.Context;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Network resilience for SAFE/idempotent background work only.
 * Financial/order-creation writes must never be queued here.
 */
public final class NetworkResilienceManager implements TransivaNetworkMonitor.Listener {
    private static final int MAX_QUEUE = 24;
    private static final Queue<Runnable> SAFE_RETRY_QUEUE = new ArrayDeque<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final NetworkResilienceManager INSTANCE = new NetworkResilienceManager();
    private NetworkResilienceManager() { }

    public static void install(Context context) {
        if (INSTALLED.compareAndSet(false, true)) TransivaNetworkMonitor.addListener(INSTANCE);
    }

    /** Use only for GET/refresh/token-sync style work that is safe to repeat. */
    public static void executeSafe(Runnable task) {
        if (task == null) return;
        if (TransivaNetworkMonitor.isOnline()) { TransivaNetworkExecutor.execute(task); return; }
        synchronized (SAFE_RETRY_QUEUE) {
            while (SAFE_RETRY_QUEUE.size() >= MAX_QUEUE) SAFE_RETRY_QUEUE.poll();
            SAFE_RETRY_QUEUE.offer(task);
        }
    }

    public static int pendingSafeRetries() { synchronized (SAFE_RETRY_QUEUE) { return SAFE_RETRY_QUEUE.size(); } }

    @Override public void onNetworkChanged(TransivaNetworkMonitor.State state) {
        if (state == TransivaNetworkMonitor.State.OFFLINE) return;
        Runnable task;
        while (true) {
            synchronized (SAFE_RETRY_QUEUE) { task = SAFE_RETRY_QUEUE.poll(); }
            if (task == null) break;
            TransivaNetworkExecutor.execute(task);
        }
        try {
            Context app = TransivaCustomerApplication.appContext();
            if (app != null) UnifiedLiveOrderCenter.refresh(app, null);
        } catch (Throwable ignored) { }
    }
}
