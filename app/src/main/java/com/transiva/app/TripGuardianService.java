package com.transiva.app;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Trip watcher with bounded requests and explicit foreground-service shutdown. */
public class TripGuardianService extends Service {
    public static final String EXTRA_ORDER_ID = "order_id";
    private static final String ONGOING = "transiva_trip_guardian_status_v2";
    private static final String ALERT = "transiva_trip_guardian";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile String orderId = "";
    private volatile boolean stopped;
    private volatile long generation;
    private int lastRisk;
    private Future<?> currentTask;
    private final Runnable poll = new Runnable() {
        @Override public void run() { if (!stopped) { check(); handler.postDelayed(this, 20000L); } }
    };
    @Override public void onCreate() { super.onCreate(); createChannels(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String incoming = intent == null ? "" : intent.getStringExtra(EXTRA_ORDER_ID);
        if (incoming == null || incoming.trim().isEmpty()) { shutdown(); return START_NOT_STICKY; }
        if (!incoming.equals(orderId)) { generation++; lastRisk = 0; }
        orderId = incoming; stopped = false;
        try { startForeground(7812, ongoing()); }
        catch (RuntimeException unavailable) { shutdown(); return START_NOT_STICKY; }
        handler.removeCallbacks(poll); handler.post(poll);
        return START_NOT_STICKY;
    }
    private void check() {
        if (stopped || orderId.isEmpty() || !busy.compareAndSet(false, true)) return;
        String requestedOrder = orderId; long requestedGeneration = generation;
        try {
            currentTask = TransivaNetworkExecutor.execute(() -> {
                try {
                    JSONObject request = new JSONObject().put("order_id", requestedOrder).put("action", "check");
                    JSONObject result = RideSafetyApi.post(this, "ride_safety_monitor.php", request);
                    handler.post(() -> {
                        if (stopped || generation != requestedGeneration || !requestedOrder.equals(orderId)) return;
                        if (!result.optBoolean("success", false)) {
                            if ("NOT_FOUND".equals(result.optString("code"))) shutdown();
                            return;
                        }
                        if (result.optBoolean("order_ended", false)) { shutdown(); return; }
                        int risk = result.optInt("risk_level", 0);
                        // Alert once per escalation; a normal state re-arms the next alert.
                        if (risk > lastRisk) alert(risk, result.optString("message", "Periksa kondisi perjalanan Anda."));
                        lastRisk = risk;
                    });
                } catch (Exception ignored) {
                } finally { busy.set(false); }
            });
        } catch (java.util.concurrent.RejectedExecutionException overloaded) { busy.set(false); }
    }
    private Notification ongoing() {
        Intent intent = new Intent(this, CustomerTripActivity.class).putExtra("order_id", orderId);
        PendingIntent open = PendingIntent.getActivity(this, 7812, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, ONGOING).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Transiva Trip Guardian aktif").setContentText("Perjalanan sedang dipantau.")
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build();
    }
    private void alert(int risk, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        Notification notification = new NotificationCompat.Builder(this, ALERT).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(risk >= 2 ? "Periksa keselamatan perjalanan" : "Trip Guardian")
                .setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build();
        manager.notify(7813, notification);
    }
    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(ONGOING, "Status Trip Guardian", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(ALERT, "Peringatan Trip Guardian", NotificationManager.IMPORTANCE_HIGH));
    }
    private void shutdown() {
        stopped = true; generation++; handler.removeCallbacksAndMessages(null);
        if (currentTask != null) currentTask.cancel(true);
        stopForeground(true); stopSelf();
    }
    @Override public void onTimeout(int startId, int foregroundServiceType) { shutdown(); }
    @Override public void onDestroy() {
        stopped = true; generation++; handler.removeCallbacksAndMessages(null);
        if (currentTask != null) currentTask.cancel(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
