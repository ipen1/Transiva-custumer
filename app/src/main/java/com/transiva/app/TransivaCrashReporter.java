package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

/** Centralized, privacy-safe Crashlytics integration. */
public final class TransivaCrashReporter {
    private static volatile boolean initialized;

    private TransivaCrashReporter() { }

    public static void initialize(Context context) {
        if (context == null || initialized) return;
        synchronized (TransivaCrashReporter.class) {
            if (initialized) return;
            try {
                Context app = context.getApplicationContext();
                boolean debug = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                crashlytics.setCrashlyticsCollectionEnabled(!debug);
                crashlytics.setCustomKey("app_role", "customer");
                crashlytics.setCustomKey("android_sdk", Build.VERSION.SDK_INT);
                crashlytics.setCustomKey("device_manufacturer", safe(Build.MANUFACTURER));
                crashlytics.setCustomKey("device_model", safe(Build.MODEL));
                crashlytics.log("Transiva Customer application initialized");
                initialized = true;
            } catch (Throwable ignored) {
                // Crash reporting must never prevent the app from opening.
            }
        }
    }

    public static void screen(Activity activity) {
        if (activity == null) return;
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(
                    "current_screen", activity.getClass().getSimpleName());
        } catch (Throwable ignored) { }
    }

    public static void user(Context context) {
        if (context == null) return;
        try {
            String username = TransivaSession.getUsername(context).trim();
            FirebaseCrashlytics.getInstance().setUserId(username.isEmpty() ? "guest" : username);
            FirebaseCrashlytics.getInstance().setCustomKey(
                    "session_logged_in", TransivaSession.isLoggedIn(context));
        } catch (Throwable ignored) { }
    }

    public static void log(String message) {
        try {
            FirebaseCrashlytics.getInstance().log(safe(message));
        } catch (Throwable ignored) { }
    }

    public static void record(Throwable error, String operation) {
        if (error == null) return;
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("last_operation", safe(operation));
            crashlytics.recordException(error);
        } catch (Throwable ignored) { }
    }

    private static String safe(String value) {
        if (value == null) return "";
        value = value.trim();
        return value.length() > 120 ? value.substring(0, 120) : value;
    }
}
