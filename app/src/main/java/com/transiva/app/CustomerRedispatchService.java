package com.transiva.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Menjaga heartbeat redispatch selama order masih mencari driver, termasuk
 * ketika Activity masuk background atau aplikasi disapu dari recent apps.
 * Android tetap menampilkan notifikasi foreground sesuai kebijakan sistem.
 */
public final class CustomerRedispatchService extends Service {
    public static final String ACTION_START = "com.transiva.customer.REDISPATCH_START";
    public static final String ACTION_STOP = "com.transiva.customer.REDISPATCH_STOP";
    private static final String CHANNEL_ID = "transiva_search_driver";
    private static final int NOTIFICATION_ID = 83015;
    private static final long POLL_MS = 5000L;
    private static final String STATUS_URL = "https://transiva.my.id/server/check_order_status.php";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean requestRunning = new AtomicBoolean(false);
    private String orderId = "";

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (orderId.isEmpty()) {
                stopSelfSafely();
                return;
            }
            pollOnce();
            main.postDelayed(this, POLL_MS);
        }
    };

    public static void start(android.content.Context context, String orderId) {
        if (context == null || orderId == null || orderId.trim().isEmpty()) return;
        Intent i = new Intent(context, CustomerRedispatchService.class);
        i.setAction(ACTION_START);
        i.putExtra("order_id", orderId.trim());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i);
        else context.startService(i);
    }

    public static void stop(android.content.Context context) {
        if (context == null) return;
        Intent i = new Intent(context, CustomerRedispatchService.class);
        i.setAction(ACTION_STOP);
        try { context.startService(i); } catch (Exception ignored) { context.stopService(i); }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelfSafely();
            return START_NOT_STICKY;
        }
        String incoming = intent == null ? "" : safe(intent.getStringExtra("order_id"));
        if (incoming.isEmpty()) {
            SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
            incoming = safe(sp.getString("active_order_id", ""));
        }
        if (incoming.isEmpty()) {
            stopSelfSafely();
            return START_NOT_STICKY;
        }
        orderId = incoming;
        startForeground(NOTIFICATION_ID, buildNotification("Mencari driver • redispatch aktif"));
        main.removeCallbacks(heartbeat);
        main.post(heartbeat);
        return START_STICKY;
    }

    private void pollOnce() {
        if (!requestRunning.compareAndSet(false, true)) return;
        final String currentOrder = orderId;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(STATUS_URL).openConnection();
                ApiSecurity.apply(this, conn);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                JSONObject payload = new JSONObject();
                payload.put("order_id", currentOrder);
                try (OutputStream os = conn.getOutputStream();
                     BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                    w.write(payload.toString());
                    w.flush();
                }
                InputStream stream = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder body = new StringBuilder();
                if (stream != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line; while ((line = r.readLine()) != null) body.append(line);
                }
                if (body.length() == 0) return;
                JSONObject root = new JSONObject(body.toString());
                if (!root.optBoolean("success", false)) {
                    String msg = root.optString("message", "").toLowerCase(Locale.US);
                    if (msg.contains("tidak ditemukan") || msg.contains("selesai") || msg.contains("dibatalkan")) stopSelfSafely();
                    return;
                }
                JSONObject order = root.optJSONObject("order");
                String status = order == null ? root.optString("status", "") : order.optString("status", root.optString("status", ""));
                status = status.trim().toLowerCase(Locale.US);
                if (isTerminalOrAccepted(status)) stopSelfSafely();
            } catch (Exception ignored) {
                // Jaringan putus tidak menghentikan service; heartbeat berikutnya mencoba lagi.
            } finally {
                requestRunning.set(false);
                if (conn != null) conn.disconnect();
            }
        }, "customer-redispatch").start();
    }

    private boolean isTerminalOrAccepted(String s) {
        return s.equals("driver_accepted") || s.equals("accepted") || s.equals("taken")
                || s.equals("arrived_pickup") || s.equals("picked_up") || s.equals("on_trip")
                || s.equals("in_progress") || s.equals("ongoing") || s.equals("started")
                || s.equals("finished") || s.equals("completed")
                || s.equals("cancelled") || s.equals("canceled");
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, SearchDriverActivity.class);
        open.putExtra("order_id", orderId);
        open.putExtra("active_order_id", orderId);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 83015, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("Transiva sedang mencari driver")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Pencarian Driver", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Menjaga redispatch order tetap berjalan saat aplikasi di latar belakang.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private void stopSelfSafely() {
        main.removeCallbacks(heartbeat);
        getSharedPreferences("transiva", MODE_PRIVATE).edit().remove("redispatch_service_order_id").apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private static String safe(String v) { return v == null ? "" : v.trim(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { main.removeCallbacks(heartbeat); super.onDestroy(); }
}
