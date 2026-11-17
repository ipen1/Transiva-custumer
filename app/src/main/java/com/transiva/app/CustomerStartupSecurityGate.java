package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.Future;

/** The splash runs both local guards with one policy snapshot. */
public final class CustomerStartupSecurityGate {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int POLICY_BUDGET_MS = 1500;

    private CustomerStartupSecurityGate() {}

    public static void check(Activity activity, Runnable onAllowed) {
        if (!usable(activity)) return;
        Context app = activity.getApplicationContext();
        Runnable applyPolicy = () -> {
            if (!usable(activity)) return;
            CustomerSecurityPolicy.Policy policy = CustomerSecurityPolicy.cached(app);
            RootSecurityGuard.checkAtStartup(activity, policy,
                    () -> MockLocationGuard.checkAtStartup(activity, policy, onAllowed));
        };
        if (CustomerSecurityPolicy.hasFreshCache(app) || !TransivaNetworkMonitor.isOnline()) {
            applyPolicy.run();
            return;
        }

        long deadlineAt = SystemClock.elapsedRealtime() + POLICY_BUDGET_MS;
        boolean[] completed = { false };
        Future<?>[] request = new Future<?>[1];
        Runnable finish = () -> {
            if (completed[0]) return;
            completed[0] = true;
            applyPolicy.run();
        };
        Runnable timeout = () -> {
            if (request[0] != null) request[0].cancel(true);
            finish.run();
        };
        MAIN.postDelayed(timeout, POLICY_BUDGET_MS);
        Runnable done = () -> {
            MAIN.removeCallbacks(timeout);
            finish.run();
        };
        request[0] = TransivaNetworkExecutor.execute(() -> {
            long remaining = deadlineAt - SystemClock.elapsedRealtime();
            if (remaining > 0 && usable(activity)) {
                CustomerSecurityPolicy.resolveForStartup(app, (int) remaining);
            }
            MAIN.post(done);
        }, () -> MAIN.post(done));
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
