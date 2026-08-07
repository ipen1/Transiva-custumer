package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class CustomerFcmTokenSync {
    private static final long MIN_SYNC_MS = 6L * 60L * 60L * 1000L;
    private CustomerFcmTokenSync() {}

    public static void syncIfNeeded(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("transiva_fcm", Context.MODE_PRIVATE);
        long last = p.getLong("server_sync_at", 0L);
        if (System.currentTimeMillis() - last < MIN_SYNC_MS) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token == null || token.trim().length() < 20) return;
            upload(app, token.trim());
        });
    }

    public static void forceSync(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token == null || token.trim().length() < 20) return;
            upload(app, token.trim());
        });
    }

    private static void upload(Context app, String token) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                SessionManager sm = new SessionManager(app);
                String auth = sm.getToken();
                if (auth == null || auth.trim().isEmpty()) return;
                c = CustomerApiClient.open(app, TransivaFirebaseService.BASE_URL + "save_fcm_token.php");
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                JSONObject body = new JSONObject();
                body.put("fcm_token", token);
                body.put("token", token);
                body.put("role", "customer");
                body.put("platform", "android_native");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
                int code = c.getResponseCode();
                if (code >= 200 && code < 300) {
                    app.getSharedPreferences("transiva_fcm", Context.MODE_PRIVATE).edit()
                            .putString("fcm_token", token)
                            .putLong("server_sync_at", System.currentTimeMillis())
                            .apply();
                }
            } catch (Throwable ignored) {
            } finally { if (c != null) c.disconnect(); }
        }, "customer-fcm-sync").start();
    }
}
