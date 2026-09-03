package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sinkronisasi FCM customer yang aman dan tidak mengganggu koneksi API utama. */
public final class CustomerFcmTokenSync {
    private static final long MIN_SYNC_MS = 6L * 60L * 60L * 1000L;
    private static final AtomicBoolean UPLOADING = new AtomicBoolean(false);

    private CustomerFcmTokenSync() {}

    public static void syncIfNeeded(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("transiva_fcm", Context.MODE_PRIVATE);
        long last = p.getLong("server_sync_at", 0L);
        if (System.currentTimeMillis() - last < MIN_SYNC_MS) return;
        requestFirebaseToken(app);
    }

    public static void forceSync(Context context) {
        if (context == null) return;
        requestFirebaseToken(context.getApplicationContext());
    }

    private static void requestFirebaseToken(Context app) {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().length() < 20) return;
                    upload(app, token.trim());
                });
    }

    private static void upload(Context app, String token) {
        // Hindari beberapa Activity/Service mengirim token yang sama bersamaan.
        if (!UPLOADING.compareAndSet(false, true)) return;

        TransivaNetworkExecutor.execute(() -> {
            HttpURLConnection c = null;
            try {
                SessionManager sm = new SessionManager(app);
                String auth = sm.getToken();
                if (auth == null || auth.trim().isEmpty()) return;

                c = CustomerApiClient.open(
                        app,
                        TransivaFirebaseService.BASE_URL + "save_fcm_token.php"
                );
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                c.setUseCaches(false);
                c.setRequestProperty("Cache-Control", "no-store");
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                JSONObject body = new JSONObject();
                body.put("fcm_token", token);
                body.put("token", token);
                body.put("role", "customer");
                body.put("app_scope", "customer");
                body.put("platform", "android_native");

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(bytes);
                }

                int code = c.getResponseCode();
                consume(code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream());

                // Tandai tersinkron hanya jika server benar-benar menerima token.
                if (code >= 200 && code < 300) {
                    app.getSharedPreferences("transiva_fcm", Context.MODE_PRIVATE)
                            .edit()
                            .putString("fcm_token", token)
                            .putLong("server_sync_at", System.currentTimeMillis())
                            .apply();
                }
            } catch (Throwable ignored) {
                // FCM adalah kanal tambahan; kegagalan sinkron tidak boleh merusak login/dashboard.
            } finally {
                if (c != null) c.disconnect();
                UPLOADING.set(false);
            }
        });
    }

    private static void consume(InputStream in) {
        if (in == null) return;
        try {
            byte[] buffer = new byte[512];
            while (in.read(buffer) != -1) { /* drain */ }
            in.close();
        } catch (Exception ignored) {}
    }
}
