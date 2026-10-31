package com.transiva.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TransivaFirebaseService extends FirebaseMessagingService {

    public static final String BASE_URL =
            "https://transiva.my.id/server/";

    private static final String CH_ORDER =
            "transiva_order_channel_v2";
    private static final String CH_WALLET =
            "transiva_wallet_channel";
    private static final String CH_CHAT =
            "transiva_chat_channel_v2";
    private static final String CH_CALL =
            "transiva_call_channel_v4";
    private static final String CH_PROMO =
            "transiva_promo_channel";
    private static final String CH_BROADCAST =
            "transiva_broadcast_channel";
    private static final String CH_GENERAL =
            "transiva_general_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        String cleanToken = safe(token);

        if (cleanToken.isEmpty()) {
            return;
        }

        saveTokenLocal(cleanToken);
        CustomerFcmTokenSync.syncTokenNow(this, cleanToken);
    }

    @Override
    public void onMessageReceived(
            RemoteMessage remoteMessage
    ) {
        super.onMessageReceived(remoteMessage);
        createChannels();

        Map<String, String> data =
                remoteMessage.getData();

        if(data!=null&&"webrtc_call".equalsIgnoreCase(data.get("type"))&&"incoming_call".equalsIgnoreCase(data.get("event")))CallDeliveryTrace.record(this,data,"PUSH_RECEIVED","");
        if(!FcmSessionTarget.accept(this,data))return;

        if (PushExpiryPolicy.expiredOrder(data,System.currentTimeMillis())) return;

        if (data == null || data.isEmpty()) {
            String title =
                    remoteMessage.getNotification() != null
                            ? remoteMessage
                                    .getNotification()
                                    .getTitle()
                            : "Transiva";

            String body =
                    remoteMessage.getNotification() != null
                            ? remoteMessage
                                    .getNotification()
                                    .getBody()
                            : "Notifikasi baru";

            showNotification(
                    "general",
                    first(title, "Transiva"),
                    first(body, "Notifikasi baru"),
                    "",
                    "",
                    "",
                    data
            );
            return;
        }

        String type = first(
                data.get("type"),
                data.get("notif_type"),
                data.get("category"),
                "general"
        ).toLowerCase();

        if (isChat(type) && !CustomerChatPushGuard.accept(this, data)) return;

        if ("split_bill_update".equals(type) || "split_bill_invite".equals(type)) {
            Intent rt = new Intent("com.transiva.app.SPLIT_BILL_REALTIME");
            rt.setPackage(getPackageName());
            rt.putExtra("session_key", first(data.get("session_key"), ""));
            rt.putExtra("event", first(data.get("event"), type));
            sendBroadcast(rt);
        }

        if ("webrtc_call".equals(type) && "incoming_call".equalsIgnoreCase(first(data.get("event"),""))) {
            IncomingCallNotifier.receive(this,data);return;
        }

        if ("webrtc_call".equals(type)) {
            final String event = first(data.get("event"), "").toLowerCase();
            final String callId = first(data.get("call_id"), "");

            // Only a genuinely new incoming call is allowed to open the call UI.
            // All state/signaling events are consumed here so they cannot launch
            // WebRtcCallActivity again through a PendingIntent/full-screen intent.
            if ("call_accepted".equals(event) || "accepted".equals(event)) {
                IncomingCallAlertManager.resolve(callId);
                sendCallState(callId, "accepted");
                cancelCallNotification(callId);
                return;
            }

            if ("call_ended".equals(event)
                    || "call_rejected".equals(event)
                    || "call_missed".equals(event)
                    || "ended".equals(event)
                    || "rejected".equals(event)
                    || "missed".equals(event)) {
                String status;
                if (event.contains("reject")) status = "rejected";
                else if (event.contains("miss")) status = "missed";
                else status = "ended";
                IncomingCallAlertManager.resolve(callId);
                sendCallState(callId, status);
                cancelCallNotification(callId);
                return;
            }

            // SDP/candidate/ringing/update pushes are not UI launches. The active
            // call Activity already polls signaling from the backend.
            if (!"incoming_call".equals(event)) {
                return;
            }
        }


        if ("security_policy_changed".equals(type)
                || "customer_security_policy_changed".equals(type)) {
            // Tidak mempercayai nilai ON/OFF dari push.
            // Push hanya memerintahkan app untuk membaca policy terbaru dari server.
            CustomerSecurityPolicy.invalidate(this);
            TransivaCustomerApplication.onSecurityPolicyChanged();
            return;
        }

        if (type.equals("force_logout")
                || type.equals("device_reset")
                || type.equals("device_banned")
                || "1".equals(data.get("force_logout"))) {
            String reason = first(
                    data.get("reason"),
                    data.get("code"),
                    type.equals("device_banned") ? "DEVICE_BANNED" : "DEVICE_RESET"
            );
            ForceLogoutManager.execute(this, reason);
            return;
        }

        String title = first(
                data.get("title"),
                "Transiva"
        );

        String body = first(
                data.get("body"),
                data.get("message"),
                "Notifikasi baru"
        );

        String orderId = first(
                data.get("order_id"),
                data.get("id_order"),
                data.get("orderId"),
                ""
        );

        String roomId = first(
                data.get("room_id"),
                data.get("chat_room"),
                ""
        );

        String url = first(
                data.get("url"),
                data.get("link"),
                ""
        );

        showNotification(
                type,
                title,
                body,
                orderId,
                roomId,
                url,
                data
        );
    }

    private void sendCallState(String callId, String status) {
        if (callId == null || callId.trim().isEmpty()) return;
        try {
            Intent state = new Intent(WebRtcCallActivity.ACTION_CALL_STATE);
            state.setPackage(getPackageName());
            state.putExtra(WebRtcCallActivity.EXTRA_CALL_ID, callId);
            state.putExtra(WebRtcCallActivity.EXTRA_CALL_STATUS, status);
            sendBroadcast(state);
        } catch (Throwable t) {
        }
    }

    private void cancelCallNotification(String callId) {
        if (callId == null || callId.trim().isEmpty()) return;
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(Math.abs(("webrtc_call|" + callId).hashCode()));
        } catch (Throwable ignored) {}
    }

    private void showNotification(
            String type,
            String title,
            String body,
            String orderId,
            String roomId,
            String url,
            Map<String, String> data
    ) {
        if ("webrtc_call".equals(type)) {
            if(data!=null&&"incoming_call".equalsIgnoreCase(first(data.get("event"),"")))IncomingCallNotifier.receive(this,data);
            return;
        }

        body = CustomerChatPreviewFormatter.notificationText(body);
        TransivaNotificationStore.add(
                this,
                type,
                title,
                body,
                orderId,
                roomId,
                url,
                data
        );

        String event = data != null ? first(data.get("event"), data.get("status"), "").toLowerCase() : "";
        boolean arrivalPickup = "arrived_pickup".equals(event);
        boolean arrivalDelivery = "arrived_delivery".equals(event);
        boolean arrivalEvent = arrivalPickup || arrivalDelivery;

        if (arrivalEvent) {
            CustomerArrivalMessage.Content arrival = CustomerArrivalMessage.build(
                    event,
                    data != null ? first(data.get("order_type"), data.get("service_type"), "") : "",
                    data != null ? first(data.get("source"), "") : "",
                    data != null ? first(data.get("restaurant_name"), data.get("merchant_name"), "") : "",
                    data != null ? first(data.get("driver"), data.get("driver_name"), "") : "",
                    first(orderId, "")
            );
            title = arrival.title;
            body = arrival.body;
            if(data!=null && "1".equals(data.get("smart_waiting")))body += "\nLihat hitungan waktu tunggu dan tarifnya di detail perjalanan.";
        }

        String channelId = channelForType(type);

        Intent intent = arrivalEvent ? buildArrivalIntent(event, orderId, data) : buildOpenIntent(
                type,
                orderId,
                roomId,
                url,
                data
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        String callNotificationId =
                data != null ? first(data.get("call_id"), "") : "";

        int requestCode;
        if ("webrtc_call".equals(type) && !callNotificationId.isEmpty()) {
            requestCode = Math.abs(("webrtc_call|" + callNotificationId).hashCode());
        } else {
            requestCode = Math.abs(
                    (
                            type
                                    + "|"
                                    + first(orderId, "")
                                    + "|"
                                    + first(roomId, "")
                                    + "|"
                                    + System.currentTimeMillis()
                    ).hashCode()
            );
        }

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        channelId
                )
                        .setSmallIcon(getSmallIcon())
                        .setContentTitle(first(title, "Transiva"))
                        .setContentText(
                                first(body, "Notifikasi baru")
                        )
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(
                                                first(
                                                        body,
                                                        "Notifikasi baru"
                                                )
                                        )
                        )
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(priorityForType(type))
                        .setCategory(categoryForType(type))
                        .setVisibility(
                                NotificationCompat.VISIBILITY_PUBLIC
                        );

        TransivaNotificationStyle.apply(this, builder, type);

        builder.setDefaults(
                    NotificationCompat.DEFAULT_SOUND
                            | NotificationCompat.DEFAULT_VIBRATE
                            | NotificationCompat.DEFAULT_LIGHTS
            );
        

        if (arrivalEvent) {
            // Play policy: driver-arrival is a high-priority heads-up event, not a
            // full-screen intent. Full-screen is reserved for incoming calls only.
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setTimeoutAfter(15000L);
        } else if (isChat(type)) {
            wakeScreen(5000L);
        }

        if (
                Build.VERSION.SDK_INT >= 33
                        && ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        try {
            NotificationManagerCompat.from(this).notify(requestCode,builder.build());
        } catch (RuntimeException unavailable) {
            try {
                NotificationCompat.Builder fallback=new NotificationCompat.Builder(this,channelId)
                    .setSmallIcon(getSmallIcon()).setContentTitle(first(title,"Transiva"))
                    .setContentText(first(body,"Notifikasi baru")).setContentIntent(pendingIntent)
                    .setPriority(priorityForType(type)).setAutoCancel(true);
                NotificationManagerCompat.from(this).notify(requestCode,fallback.build());
            }catch(RuntimeException failed){android.util.Log.w("TransivaNotification","Notification unavailable",failed);}
        }


    }

    private Intent buildArrivalIntent(String event, String orderId, Map<String, String> data) {
        Intent i = new Intent(this, CustomerArrivalAlertActivity.class);
        i.putExtra("event", first(event, ""));
        i.putExtra("order_id", first(orderId, ""));
        i.putExtra("driver", data != null ? first(data.get("driver"), data.get("driver_name"), "") : "");
        i.putExtra("order_type", data != null ? first(data.get("order_type"), data.get("service_type"), "") : "");
        i.putExtra("source", data != null ? first(data.get("source"), "orders") : "orders");
        i.putExtra("restaurant_name", data != null ? first(data.get("restaurant_name"), data.get("merchant_name"), "") : "");
        i.putExtra("smart_waiting",data!=null?first(data.get("smart_waiting"),"0"):"0");
        i.putExtra("from_fcm", true);
        return i;
    }

    private void wakeScreen(long millis) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isInteractive()) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "Transiva:CustomerFcmWake");
            wl.acquire(Math.max(1500L, millis));
        } catch (Throwable ignored) {}
    }

    private void openArrivalScreen(Intent intent) {
        if (intent == null) return;
        try {
            Intent alert = new Intent(intent);
            alert.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            startActivity(alert);
        } catch (Throwable ignored) {
            // Android may block background Activity starts; full-screen/heads-up notification remains as fallback.
        }
    }



    private Intent buildOpenIntent(
            String type,
            String orderId,
            String roomId,
            String url,
            Map<String, String> data
    ) {
        String screen =
                data != null
                        ? first(data.get("screen"), "")
                        : "";


        if ("webrtc_call".equals(type)) {
            Intent intent = new Intent(this, WebRtcCallActivity.class);
            intent.putExtra("call_id", data != null ? first(data.get("call_id"), "") : "");
            intent.putExtra("order_id", orderId);
            intent.putExtra("source", data != null ? first(data.get("source"), "orders") : "orders");
            intent.putExtra("call_type",data!=null?first(data.get("call_type"),"audio"):"audio");
            intent.putExtra("peer_photo", data == null ? "" : CallPeerIdentity.first(
                    data.get("caller_photo"), data.get("caller_photo_url"), data.get("caller_avatar")));
            intent.putExtra("caller_name", data != null ? first(data.get("caller_name"), "Transiva") : "Transiva");
            intent.putExtra("incoming", data != null && "incoming_call".equalsIgnoreCase(first(data.get("event"), "")));
            return intent;
        }

        if ("split_bill_invite".equals(type) || "split_bill".equalsIgnoreCase(screen)) {
            Intent intent = new Intent(this, SplitBillInviteActivity.class);
            intent.putExtra("session_key", data != null ? first(data.get("session_key"), "") : "");
            intent.putExtra("from_fcm", true);
            return intent;
        }

        if (isChat(type)) {
            Intent intent = new Intent(this, CustomerChatActivity.class);
            intent.putExtra("room_id", first(roomId, orderId));
            intent.putExtra("order_id", orderId);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        if ("merchant_order_status".equals(type) || "food_order_status".equalsIgnoreCase(screen)
                || (isOrder(type) && orderId != null && orderId.toUpperCase(java.util.Locale.ROOT).startsWith("FOOD-"))) {
            Intent intent = new Intent(this, CustomerFoodOrderStatusActivity.class);
            intent.putExtra("order_id",orderId);intent.putExtra("from_fcm",true);
            if(data!=null)for(String key:new String[]{"status","restaurant_name","merchant_reject_reason"})intent.putExtra(key,first(data.get(key),""));
            return intent;
        }

        if (isOrder(type) || "customer_order".equalsIgnoreCase(screen)) {
            Intent intent = new Intent(this, CustomerTripActivity.class);
            intent.putExtra("order_id", orderId);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        if (isWallet(type)) {
            Intent intent = new Intent(this, CustomerTopUpActivity.class);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        // Promo customer langsung membuka dashboard customer.
        if (
                type.contains("promo")
                        || "customer_dashboard".equalsIgnoreCase(
                                screen
                        )
        ) {
            Intent intent = new Intent(
                    this,
                    CustomerDashboardActivity.class
            );

            intent.putExtra("from_fcm", true);
            intent.putExtra("notif_type", type);
            intent.putExtra(
                    "promo_id",
                    data != null
                            ? first(data.get("promo_id"), "")
                            : ""
            );
            return intent;
        }

        if (
                !TextUtils.isEmpty(url)
                        && (
                        url.startsWith("http://")
                                || url.startsWith("https://")
                )
        ) {
            Intent intent =
                    new Intent(this, CustomerDashboardActivity.class);

            intent.putExtra("url", url);
            intent.putExtra("from_fcm", true);
            return intent;
        }

        Intent intent =
                new Intent(this, CustomerDashboardActivity.class);

        intent.putExtra("from_fcm", true);
        intent.putExtra("notif_type", type);
        return intent;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        createChannel(
                CH_ORDER,
                "Order Transiva",
                "Order baru dan pembaruan status",
                NotificationManager.IMPORTANCE_HIGH
        );

        createChannel(
                CH_WALLET,
                "Financial Transiva",
                "Saldo, deposit, dan penarikan",
                NotificationManager.IMPORTANCE_HIGH
        );

        createChannel(
                CH_CALL,
                "Panggilan Transiva",
                "Panggilan suara dan video Driver–Customer",
                NotificationManager.IMPORTANCE_HIGH
        );

        createChannel(
                CH_CHAT,
                "Chat Transiva",
                "Pesan customer dan driver",
                NotificationManager.IMPORTANCE_HIGH
        );

        createChannel(
                CH_PROMO,
                "Promo Transiva",
                "Promo dan penawaran Transiva",
                NotificationManager.IMPORTANCE_HIGH
        );

        createChannel(
                CH_BROADCAST,
                "Broadcast Admin",
                "Pengumuman admin Transiva",
                NotificationManager.IMPORTANCE_HIGH
        );

        createChannel(
                CH_GENERAL,
                "Transiva",
                "Notifikasi umum",
                NotificationManager.IMPORTANCE_DEFAULT
        );
    }

    private void createChannel(
            String id,
            String name,
            String description,
            int importance
    ) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager == null) {
            return;
        }

        NotificationChannel existing =
                manager.getNotificationChannel(id);

        if (existing != null) {
            // Importance channel tidak bisa dinaikkan setelah dibuat.
            // Hapus channel promo lama agar dibuat ulang HIGH.
            if (
                    CH_PROMO.equals(id)
                            && existing.getImportance()
                            < NotificationManager.IMPORTANCE_HIGH
            ) {
                manager.deleteNotificationChannel(id);
            } else {
                return;
            }
        }

        NotificationChannel channel =
                new NotificationChannel(
                        id,
                        name,
                        importance
                );

        channel.setDescription(description);
        channel.enableVibration(CustomerAppSettings.isVibrationEnabled(this));
        channel.enableLights(true);

        if (CH_CALL.equals(id)) {
            // Silent channel; the full-screen Activity plays exactly one ringtone.
            channel.setSound(null, null);
            channel.enableVibration(false);
        } else {
            channel.setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            );
        }

        manager.createNotificationChannel(channel);
    }

    private String channelForType(String type) {
        type = first(type, "general").toLowerCase();

        if ("webrtc_call".equals(type)) {
            return CH_CALL;
        }

        if (isChat(type)) {
            return CH_CHAT;
        }

        if (isWallet(type)) {
            return CH_WALLET;
        }

        if (isOrder(type)) {
            return CH_ORDER;
        }

        if (type.contains("promo")) {
            return CH_PROMO;
        }

        if (
                type.contains("broadcast")
                        || type.contains("admin")
        ) {
            return CH_BROADCAST;
        }

        return CH_GENERAL;
    }

    private int priorityForType(String type) {
        type = first(type, "").toLowerCase();

        if (
                "webrtc_call".equals(type)
                        || isChat(type)
                        || isOrder(type)
                        || isWallet(type)
                        || type.contains("broadcast")
                        || type.contains("promo")
        ) {
            return NotificationCompat.PRIORITY_HIGH;
        }

        return NotificationCompat.PRIORITY_DEFAULT;
    }

    private String categoryForType(String type) {
        type = first(type, "").toLowerCase();

        if ("webrtc_call".equals(type)) {
            return NotificationCompat.CATEGORY_CALL;
        }

        if (isChat(type)) {
            return NotificationCompat.CATEGORY_MESSAGE;
        }

        if (isOrder(type) || isWallet(type)) {
            return NotificationCompat.CATEGORY_STATUS;
        }

        if (type.contains("promo")) {
            return NotificationCompat.CATEGORY_PROMO;
        }

        return NotificationCompat.CATEGORY_MESSAGE;
    }

    private boolean isChat(String type) {
        type = first(type, "").toLowerCase();

        return type.contains("chat")
                || type.contains("message");
    }

    private boolean isOrder(String type) {
        type = first(type, "").toLowerCase();

        return type.contains("order")
                || type.contains("ride")
                || type.contains("food")
                || type.contains("pickup")
                || type.contains("wisata")
                || type.contains("merchant");
    }

    private boolean isWallet(String type) {
        type = first(type, "").toLowerCase();

        return type.contains("wallet")
                || type.contains("financial")
                || type.contains("deposit")
                || type.contains("withdraw")
                || type.contains("saldo")
                || type.contains("balance");
    }

    private int getSmallIcon() {
        try {
            return R.drawable.ic_notification_bell;
        } catch (Exception ignored) {
            return android.R.drawable.ic_dialog_info;
        }
    }

    private void saveTokenLocal(String token) {
        String cleanToken = safe(token);

        getSharedPreferences(
                "transiva_fcm",
                MODE_PRIVATE
        )
                .edit()
                .putString("fcm_token", cleanToken)
                .putLong(
                        "fcm_token_saved_at",
                        System.currentTimeMillis()
                )
                .apply();

        getSharedPreferences(
                "transiva_native_session",
                MODE_PRIVATE
        )
                .edit()
                .putString("fcm_token", cleanToken)
                .putLong(
                        "fcm_token_saved_at",
                        System.currentTimeMillis()
                )
                .apply();
    }

    private void sendTokenToServer(String token) {
        FcmTokenCoordinator.onNewToken(getApplicationContext(),token);
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }

        value = value.trim();

        if (
                value.isEmpty()
                        || "null".equalsIgnoreCase(value)
                        || "undefined".equalsIgnoreCase(value)
        ) {
            return "";
        }

        return value;
    }

    private String first(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            value = safe(value);

            if (!value.isEmpty()) {
                return value;
            }
        }

        return "";
    }
}
