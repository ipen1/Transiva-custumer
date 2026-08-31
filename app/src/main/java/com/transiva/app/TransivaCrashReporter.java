package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLException;

/**
 * Centralized, privacy-safe Crashlytics integration.
 *
 * Non-fatal events are deduplicated to avoid flooding Crashlytics when a phone
 * is offline or the server is temporarily unavailable. Never pass tokens,
 * request/response bodies, phone numbers, PINs, passwords or exact locations.
 */
public final class TransivaCrashReporter {
    private static final long DEFAULT_DEDUP_WINDOW_MS = 5L * 60L * 1000L;
    private static final Map<String, Long> LAST_REPORTED = new ConcurrentHashMap<>();
    private static volatile boolean initialized;
    private static volatile Context appContext;

    private TransivaCrashReporter() { }

    public static void initialize(Context context) {
        if (context == null || initialized) return;
        synchronized (TransivaCrashReporter.class) {
            if (initialized) return;
            try {
                Context app = context.getApplicationContext();
                appContext = app;
                boolean debug = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
                crashlytics.setCrashlyticsCollectionEnabled(!debug);
                crashlytics.setCustomKey("app_role", "customer");
                crashlytics.setCustomKey("android_sdk", Build.VERSION.SDK_INT);
                crashlytics.setCustomKey("device_manufacturer", safe(Build.MANUFACTURER));
                crashlytics.setCustomKey("device_model", safe(Build.MODEL));
                crashlytics.setCustomKey("network_state", networkState(app));
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
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("current_screen", activity.getClass().getSimpleName());
            crashlytics.setCustomKey("network_state", networkState(activity));
        } catch (Throwable ignored) { }
    }

    public static void user(Context context) {
        if (context == null) return;
        try {
            // Username is intentionally not sent. Use a stable, non-reversible local ID.
            String installationId = DeviceIdentityManager.getInstallationUuid(context);
            String anonymousId = Integer.toHexString(installationId == null ? 0 : installationId.hashCode());
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setUserId(anonymousId.isEmpty() ? "guest" : "device_" + anonymousId);
            crashlytics.setCustomKey("session_logged_in", TransivaSession.isLoggedIn(context));
        } catch (Throwable ignored) { }
    }

    public static void log(String message) {
        try {
            FirebaseCrashlytics.getInstance().log(safe(message));
        } catch (Throwable ignored) { }
    }

    public static void record(Throwable error, String operation) {
        record(error, operation, "general", DEFAULT_DEDUP_WINDOW_MS);
    }

    public static void record(Throwable error, String operation, String fingerprint) {
        record(error, operation, fingerprint, DEFAULT_DEDUP_WINDOW_MS);
    }

    public static void recordNetworkFailure(Throwable error, String method, String path) {
        if (error == null) return;
        String type;
        if (error instanceof SocketTimeoutException) {
            type = "timeout";
        } else if (error instanceof UnknownHostException) {
            type = "dns_or_offline";
        } else if (error instanceof SSLException) {
            type = "ssl";
        } else {
            type = "io";
        }

        String safePath = endpointName(path);
        setKey("network_error_type", type);
        setKey("http_method", safe(method).toUpperCase(Locale.US));
        setKey("endpoint", safePath);
        setKey("network_state", networkState(appContext));
        record(error, "network_request", type + ":" + safePath, DEFAULT_DEDUP_WINDOW_MS);
    }

    public static void recordHttpStatus(int status, String method, String path, long durationMs) {
        // Report only actionable statuses. Normal client errors (400/401/403/404) are not crashes.
        if (status != 429 && status < 500) return;

        String safePath = endpointName(path);
        setKey("http_status", status);
        setKey("http_method", safe(method).toUpperCase(Locale.US));
        setKey("endpoint", safePath);
        setKey("request_duration_ms", Math.max(0L, durationMs));
        setKey("network_state", networkState(appContext));

        String category = status == 429 ? "rate_limited" : "server_error";
        NonFatalHttpException error = new NonFatalHttpException(category, status, safePath);
        record(error, "http_response", category + ":" + status + ":" + safePath,
                status == 429 ? 15L * 60L * 1000L : DEFAULT_DEDUP_WINDOW_MS);
    }

    public static void recordInvalidResponse(String path, int status) {
        String safePath = endpointName(path);
        setKey("http_status", status);
        setKey("endpoint", safePath);
        record(new IllegalStateException("Server returned a non-JSON response"),
                "invalid_server_response", "invalid_json:" + safePath,
                15L * 60L * 1000L);
    }

    public static void recordBridgeFailure(Throwable error, String eventName) {
        if (error == null) return;
        setKey("bridge_event", safe(eventName));
        record(error, "webview_bridge", "bridge:" + safe(eventName), DEFAULT_DEDUP_WINDOW_MS);
    }

    private static void record(Throwable error, String operation, String fingerprint, long windowMs) {
        if (error == null) return;
        try {
            String key = safe(operation) + "|" + safe(fingerprint) + "|" + error.getClass().getName();
            long now = SystemClock.elapsedRealtime();
            Long previous = LAST_REPORTED.put(key, now);
            if (previous != null && now - previous < Math.max(10_000L, windowMs)) {
                return;
            }

            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("last_operation", safe(operation));
            crashlytics.setCustomKey("network_state", networkState(appContext));
            crashlytics.recordException(error);
        } catch (Throwable ignored) { }
    }

    private static void setKey(String key, String value) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(safe(key), safe(value));
        } catch (Throwable ignored) { }
    }

    private static void setKey(String key, int value) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(safe(key), value);
        } catch (Throwable ignored) { }
    }

    private static void setKey(String key, long value) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(safe(key), value);
        } catch (Throwable ignored) { }
    }

    public static void networkStateChanged(String state) {
        try {
            FirebaseCrashlytics c = FirebaseCrashlytics.getInstance();
            c.setCustomKey("network_state", safe(state));
            c.log("network:" + safe(state));
        } catch (Throwable ignored) { }
    }

    public static void orderState(String anonymousOrderKey, String status, String source) {
        try {
            FirebaseCrashlytics c = FirebaseCrashlytics.getInstance();
            c.setCustomKey("order_state", safe(status));
            c.setCustomKey("order_source", safe(source));
            c.setCustomKey("order_trace", safe(anonymousOrderKey));
            c.log("order_state:" + safe(status));
        } catch (Throwable ignored) { }
    }

    private static String endpointName(String path) {
        String value = safe(path).trim();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        while (value.startsWith("/")) value = value.substring(1);
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        // Keep only a harmless endpoint filename, never query values.
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.isEmpty() ? "unknown_endpoint" : safe(value);
    }

    private static String networkState(Context context) {
        if (context == null) return "unknown";
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "unknown";
            Network network = cm.getActiveNetwork();
            if (network == null) return "offline";
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) return "unknown";
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "no_internet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "vpn";
            return "other";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        value = value.trim();
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private static final class NonFatalHttpException extends Exception {
        NonFatalHttpException(String category, int status, String endpoint) {
            super("Non-fatal " + category + " (HTTP " + status + ") at " + endpoint);
        }
    }
}
