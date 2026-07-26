package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class CustomerChatNotificationPoller {

    private static final String CHANNEL_ID =
            "transiva_chat_messages";

    private static final String PREFS =
            "transiva_chat_notification_state";

    private static final String KEY_LAST_ID =
            "last_incoming_chat_id";

    private static final long INTERVAL_MS = 8000L;

    private static final Handler HANDLER =
            new Handler(Looper.getMainLooper());

    private static Context appContext;
    private static int userId;
    private static boolean running;
    private static String openRoom = "";

    private CustomerChatNotificationPoller() {
    }

    public static void start(
            Context context,
            int currentUserId
    ) {
        if (
                context == null
                        || currentUserId <= 0
        ) {
            return;
        }

        appContext =
                context.getApplicationContext();

        userId = currentUserId;

        createChannel(appContext);

        if (running) {
            return;
        }

        running = true;
        HANDLER.post(checkRunnable);
    }

    public static void setOpenRoom(
            String roomId
    ) {
        openRoom = normalizeRoom(roomId);
    }

    public static void clearOpenRoom(
            String roomId
    ) {
        String normalized =
                normalizeRoom(roomId);

        if (openRoom.equals(normalized)) {
            openRoom = "";
        }
    }

    public static void requestPermission(
            Activity activity
    ) {
        if (
                activity == null
                        || Build.VERSION.SDK_INT < 33
        ) {
            return;
        }

        if (
                ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    7301
            );
        }
    }

    private static final Runnable checkRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (
                            !running
                                    || appContext == null
                                    || userId <= 0
                    ) {
                        return;
                    }

                    checkNow();

                    HANDLER.postDelayed(
                            this,
                            INTERVAL_MS
                    );
                }
            };

    private static void checkNow() {
        final Context context = appContext;
        final int currentUserId = userId;

        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        final int lastId =
                preferences.getInt(
                        KEY_LAST_ID,
                        0
                );

        new Thread(() -> {
            try {
                String endpoint =
                        "https://transiva.my.id/"
                                + "server/get_customer_chat_updates.php"
                                + "?user_id="
                                + URLEncoder.encode(
                                String.valueOf(
                                        currentUserId
                                ),
                                StandardCharsets.UTF_8.name()
                        )
                                + "&last_id="
                                + lastId
                                + "&_="
                                + System.currentTimeMillis();

                JSONObject response =
                        CustomerMessageApi.get(endpoint);

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    return;
                }

                JSONArray messages =
                        response.optJSONArray(
                                "messages"
                        );

                int newestId =
                        response.optInt(
                                "last_id",
                                lastId
                        );

                if (messages != null) {
                    for (
                            int i = 0;
                            i < messages.length();
                            i++
                    ) {
                        JSONObject message =
                                messages.optJSONObject(i);

                        if (message == null) {
                            continue;
                        }

                        int messageId =
                                message.optInt(
                                        "id",
                                        0
                                );

                        if (messageId <= lastId) {
                            continue;
                        }

                        String room =
                                normalizeRoom(
                                        message.optString(
                                                "room_id",
                                                ""
                                        )
                                );

                        if (!room.equals(openRoom)) {
                            showNotification(
                                    context,
                                    message
                            );
                        }
                    }
                }

                if (newestId > lastId) {
                    preferences.edit()
                            .putInt(
                                    KEY_LAST_ID,
                                    newestId
                            )
                            .apply();
                }

            } catch (Exception ignored) {
            }
        }).start();
    }

    private static void showNotification(
            Context context,
            JSONObject message
    ) {
        if (
                Build.VERSION.SDK_INT >= 33
                        &&
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        String participant =
                first(
                        message.optString(
                                "participant_name"
                        ),
                        "Pesan Transiva"
                );

        String rawMessage =
                message.optString(
                        "message",
                        ""
                );

        String preview =
                (rawMessage.startsWith("[[IMAGE]]") || rawMessage.startsWith("[[IMAGE2]]"))
                        ? "📷 Mengirim sebuah foto"
                        : rawMessage;

        Intent intent = new Intent(
                context,
                CustomerChatRoomActivity.class
        );

        intent.putExtra(
                "order_id",
                message.optString(
                        "order_id",
                        ""
                )
        );

        intent.putExtra(
                "room_id",
                message.optString(
                        "room_id",
                        ""
                )
        );

        intent.putExtra(
                "participant_name",
                participant
        );

        intent.putExtra(
                "order_type",
                message.optString(
                        "order_type",
                        ""
                )
        );

        intent.putExtra(
                "order_status",
                message.optString(
                        "status",
                        ""
                )
        );

        intent.putExtra(
                "read_only",
                false
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        int notificationId =
                Math.max(
                        1,
                        message.optInt(
                                "id",
                                (int)(
                                        System.currentTimeMillis()
                                                % Integer.MAX_VALUE
                                )
                        )
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        notificationId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        (
                                Build.VERSION.SDK_INT >= 23
                                        ? PendingIntent.FLAG_IMMUTABLE
                                        : 0
                        )
                );

        int smallIcon =
                context.getResources()
                        .getIdentifier(
                                "ic_notification",
                                "drawable",
                                context.getPackageName()
                        );

        if (smallIcon == 0) {
            smallIcon =
                    context.getApplicationInfo()
                            .icon;
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(smallIcon)
                        .setContentTitle(participant)
                        .setContentText(preview)
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(preview)
                        )
                        .setAutoCancel(true)
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_HIGH
                        )
                        .setCategory(
                                NotificationCompat
                                        .CATEGORY_MESSAGE
                        )
                        .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            manager.notify(
                    notificationId,
                    builder.build()
            );
        }
    }

    private static void createChannel(
            Context context
    ) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager == null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Pesan Driver dan Merchant",
                        NotificationManager
                                .IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Notifikasi pesan baru terkait order Transiva"
        );

        channel.enableVibration(CustomerAppSettings.isVibrationEnabled(context));

        manager.createNotificationChannel(
                channel
        );
    }

    private static String normalizeRoom(
            String roomId
    ) {
        return roomId == null
                ? ""
                : roomId.trim()
                .replace('_', '-')
                .toUpperCase();
    }

    private static String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
            ) {
                return value.trim();
            }
        }

        return "";
    }
}
