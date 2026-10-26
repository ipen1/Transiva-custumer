package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/** One account/session boundary and delivery ledger shared by FCM and polling. */
final class CustomerChatNoticeState {
    private static final long HOUR = 3600000L;
    private CustomerChatNoticeState() {}

    private static SharedPreferences prefs(Context c, String user) {
        return c.getSharedPreferences("customer_chat_notice_v3_" + user, Context.MODE_PRIVATE);
    }

    static synchronized void begin(Context c, String user, String token, boolean newLogin) {
        if (user == null || user.isEmpty() || token == null || token.isEmpty()) return;
        SharedPreferences p = prefs(c, user);
        String hash = FcmTokenCoordinator.hash(token);
        long update = 0L;
        try { update = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime; }
        catch (Exception ignored) { }

        if (newLogin || !hash.equals(p.getString("owner", ""))
                || p.getInt("build", -1) != BuildConfig.VERSION_CODE
                || p.getLong("install_update", -1L) != update || !p.contains("boundary")) {
            // Keep delivered IDs across logout/login. Never import an unowned legacy cursor.
            p.edit().putString("owner", hash).putInt("build", BuildConfig.VERSION_CODE)
                    .putLong("install_update", update).putLong("boundary", System.currentTimeMillis()).remove("cursor").commit();
        }
    }

    static void ensure(Context c) {
        SessionManager s = new SessionManager(c);
        if (s.isLoggedIn()) begin(c, s.getUserId(), s.getToken(), false);
    }

    static synchronized long boundary(Context c, String user) {
        return prefs(c, user).getLong("boundary", System.currentTimeMillis());
    }

    // Send elapsed age, not the phone's epoch, so server/phone timezone and clock offsets do not matter.
    static int maxAge(long boundary) {
        return (int)Math.min(3600L, Math.max(0L, (System.currentTimeMillis() - boundary) / 1000L));
    }

    static synchronized int cursor(Context c, String user) {
        return prefs(c, user).getInt("cursor", -1);
    }

    static synchronized void advance(Context c, String user, long boundary, int id) {
        SharedPreferences p = prefs(c, user);
        if (p.getLong("boundary", -1) == boundary)
            p.edit().putInt("cursor", Math.max(p.getInt("cursor", 0), id)).commit();
    }

    static synchronized boolean claim(Context c, String user, long boundary, int id) {
        if (id <= 0) return false;
        SharedPreferences p = prefs(c, user);
        if (p.getLong("boundary", -1) != boundary || p.contains("message_" + id)) return false;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = p.edit();
        for (Map.Entry<String, ?> item : p.getAll().entrySet()) {
            if (item.getKey().startsWith("message_") && item.getValue() instanceof Long
                    && now - (Long)item.getValue() > HOUR) e.remove(item.getKey());
        }
        // A failed disk write must not publish an alert that cannot be deduplicated.
        return e.putLong("message_" + id, now).commit();
    }
}
