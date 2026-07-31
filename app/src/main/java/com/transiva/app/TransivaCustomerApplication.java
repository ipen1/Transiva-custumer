package com.transiva.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Runs the guard on normal screens. Splash owns its own blocking startup check. */
public class TransivaCustomerApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        TransivaCrashReporter.initialize(this);
        TransivaCrashReporter.user(this);
        AdaptiveTlsPinning.install(this);
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity) {
        TransivaCrashReporter.screen(activity);
        TransivaCrashReporter.user(activity);
        if (activity instanceof SplashActivity) return;
        main.postDelayed(() -> {
            if (!activity.isFinishing()) {
                RootSecurityGuard.protect(activity);
                MockLocationGuard.protect(activity);
            }
        }, 250L);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
