package com.transiva.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

/**
 * Splash security gate. Prefetches the combined customer security policy once, then both
 * local guards consume the same cached policy instead of independently hitting the server.
 */
public final class CustomerStartupSecurityGate {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private CustomerStartupSecurityGate() {}

    public static void check(Activity activity, Runnable onAllowed) {
        if (activity == null || activity.isFinishing()) return;
        TransivaNetworkExecutor.execute(() -> {
            try { CustomerSecurityPolicy.resolve(activity.getApplicationContext()); }
            catch (Throwable ignored) { }
            MAIN.post(() -> {
                if (activity.isFinishing()) return;
                RootSecurityGuard.checkBeforeContinue(activity,
                        () -> MockLocationGuard.checkBeforeContinue(activity, onAllowed));
            });
        });
    }
}
