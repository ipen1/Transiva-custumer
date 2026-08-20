package com.transiva.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.messaging.FirebaseMessaging;

import java.lang.ref.WeakReference;

/** Runs the guard on normal screens. Splash owns its own blocking startup check. */
public class TransivaCustomerApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static volatile TransivaCustomerApplication instance;
    private static volatile WeakReference<Activity> currentActivity =
            new WeakReference<>(null);

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        TransivaCrashReporter.initialize(this);
        TransivaCrashReporter.user(this);
        AdaptiveTlsPinning.install(this);
        CustomerReliabilityManager.install(this);
        CustomerMessageApi.initialize(this);
        CustomerFcmTokenSync.syncIfNeeded(this);
        registerActivityLifecycleCallbacks(this);

        // Perubahan global keamanan Customer dikirim lewat topic ini.
        try {
            FirebaseMessaging.getInstance()
                    .subscribeToTopic("transiva_customer_security");
        } catch (Throwable ignored) { }
    }

    @Override public void onActivityResumed(Activity activity) {
        currentActivity = new WeakReference<>(activity);
        TransivaCrashReporter.screen(activity);
        TransivaCrashReporter.user(activity);
        CustomerFcmTokenSync.syncIfNeeded(activity);
        AppUpdateRuntimeGate.onActivityResumed(activity);
        if (activity instanceof SplashActivity || activity instanceof UpdateDownloadActivity) return;
        main.postDelayed(() -> {
            if (!activity.isFinishing()) {
                try {
                    SessionManager session = new SessionManager(activity);
                    if (session.isLoggedIn() && "customer".equalsIgnoreCase(session.getRole())) {
                        RootSecurityGuard.protect(activity);
                        MockLocationGuard.protect(activity);
                    }
                } catch (Throwable ignored) { }
            }
        }, 250L);
    }

    /**
     * FCM hanya menjadi trigger. Source of truth tetap customer_security_policy.php.
     * Foreground: policy diterapkan langsung.
     * Background: cache dihapus; Activity berikutnya otomatis membaca policy baru.
     */
    public static void onSecurityPolicyChanged() {
        TransivaCustomerApplication app = instance;
        if (app == null) return;

        CustomerSecurityPolicy.invalidate(app);

        app.main.post(() -> {
            Activity activity = currentActivity.get();

            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()
                    || activity instanceof SplashActivity
                    || activity instanceof UpdateDownloadActivity) {
                return;
            }

            try {
                SessionManager session = new SessionManager(activity);
                if (!session.isLoggedIn()
                        || !"customer".equalsIgnoreCase(session.getRole())) {
                    return;
                }
            } catch (Throwable ignored) {
                return;
            }

            // Kedua guard membaca policy terbaru. Request kedua biasanya memakai
            // cache yang baru ditulis request pertama.
            MockLocationGuard.protectFresh(activity);
            RootSecurityGuard.protectFresh(activity);
        });
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) {
        Activity current = currentActivity.get();
        if (current == activity) {
            currentActivity = new WeakReference<>(null);
        }
    }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
    public static Application appContext() {
        return instance;
    }

}
