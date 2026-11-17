package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;

/**
 * Policy keamanan Customer dari server.
 * Default fail-secure: root dan fake GPS aktif bila policy belum dapat dibaca.
 */
public final class CustomerSecurityPolicy {
    private static final String POLICY_URL =
            "https://transiva.my.id/server/customer_security_policy.php";
    private static final String PREF = "customer_security_policy";
    private static final long CACHE_MS = 5L * 60L * 1000L;

    private CustomerSecurityPolicy() {}

    public static final class Policy {
        public final boolean rootEnabled;
        public final boolean fakeGpsEnabled;
        public final String source;

        Policy(boolean rootEnabled, boolean fakeGpsEnabled, String source) {
            this.rootEnabled = rootEnabled;
            this.fakeGpsEnabled = fakeGpsEnabled;
            this.source = source == null ? "" : source;
        }
    }

    /** Blocking; panggil hanya dari worker/background thread. */
    public static Policy resolve(Context context) {
        return resolve(context, false, 0);
    }

    /** Paksa baca server; dipakai saat FCM memberi sinyal perubahan policy. */
    public static Policy resolveFresh(Context context) {
        return resolve(context, true, 0);
    }

    static Policy resolveForStartup(Context context, int budgetMs) {
        return resolve(context, false, budgetMs);
    }

    static Policy confirmForStartup(Context context) {
        return resolve(context, true, 3500);
    }

    static boolean hasFreshCache(Context context) {
        long checkedAt = checkedAt(context);
        long age = System.currentTimeMillis() - checkedAt;
        return checkedAt > 0L && age >= 0L && age < CACHE_MS;
    }

    private static synchronized Policy resolve(Context context, boolean forceRefresh, int budgetMs) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String policyOwner = ownerKey(app);
        if (Thread.currentThread().isInterrupted()
                || (!forceRefresh && hasFreshCache(app)) || !TransivaNetworkMonitor.isOnline()) {
            return cached(app);
        }

        String userId = "";
        String username = "";
        try {
            SessionManager session = new SessionManager(app);
            if (session.isLoggedIn()) {
                userId = safe(session.getUserId());
                username = safe(session.getUsername());
            }
        } catch (Throwable ignored) { }

        HttpURLConnection connection = null;
        CustomerTransportSession owner = CustomerTransportSession.capture(app);
        try (CustomerReadDeadline limit = budgetMs > 0 ? new CustomerReadDeadline(budgetMs) : null) {
            StringBuilder link = new StringBuilder(POLICY_URL);
            link.append("?app=customer");
            if (!userId.isEmpty()) link.append("&user_id=").append(Uri.encode(userId));
            if (!username.isEmpty()) link.append("&username=").append(Uri.encode(username));

            connection = CustomerApiClient.open(app, link.toString());
            if (limit != null) limit.attach(connection);
            owner.pin(app, connection);
            int timeout = limit == null ? 4500 : budgetMs;
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Transiva-App", "Android-Customer");

            int code = connection.getResponseCode();
            InputStream input = code >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream();

            String body = BoundedResponseReader.read(input, 64 * 1024, timeout);
            if (code < 200 || code >= 300 || Thread.currentThread().isInterrupted()
                    || (limit != null && android.os.SystemClock.elapsedRealtime() >= limit.endAt)) return cached(app);
            owner.check(app);

            JSONObject json = new JSONObject(body);
            if (!json.optBoolean("success", false)) return cached(app);

            boolean root = json.optBoolean("root_detection_enabled", true);
            boolean fake = json.optBoolean("fake_gps_detection_enabled", true);
            String source = json.optString("policy_source", "global");

            prefs.edit()
                    .putBoolean("root_detection_enabled", root)
                    .putBoolean("fake_gps_detection_enabled", fake)
                    .putString("policy_source", source)
                    .putString("policy_owner", policyOwner)
                    .putLong("checked_at", now)
                    .apply();

            return new Policy(root, fake, source);
        } catch (Throwable ignored) {
            return cached(app);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static Policy cached(Context context) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);

        if (!ownerKey(context).equals(p.getString("policy_owner", null))) {
            return new Policy(true, true, "fallback");
        }
        return new Policy(
                p.getBoolean("root_detection_enabled", true),
                p.getBoolean("fake_gps_detection_enabled", true),
                p.getString("policy_source", "fallback")
        );
    }

    public static boolean rootEnabledCached(Context context) {
        return cached(context).rootEnabled;
    }

    public static boolean fakeGpsEnabledCached(Context context) {
        return cached(context).fakeGpsEnabled;
    }

    public static void invalidate(Context context) {
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .remove("checked_at")
                    .apply();
        } catch (Throwable ignored) { }
    }

    public static long checkedAt(Context context) {
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE);
            return ownerKey(context).equals(prefs.getString("policy_owner", null))
                    ? prefs.getLong("checked_at", 0L) : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String ownerKey(Context context) {
        SessionManager session = new SessionManager(context);
        if (!session.isLoggedIn()) return "";
        String id = safe(session.getUserId());
        return id.isEmpty() ? "username:" + safe(session.getUsername()) : "id:" + id;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
