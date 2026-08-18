package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.core.app.NotificationCompat;

/** Shared visual branding for Transiva notifications. */
public final class TransivaNotificationStyle {
    private static final int TRANSIVA_BLUE = 0xFF1677FF;

    private TransivaNotificationStyle() {}

    public static NotificationCompat.Builder apply(
            Context context,
            NotificationCompat.Builder builder,
            String type
    ) {
        if (builder == null) return null;

        builder.setColor(TRANSIVA_BLUE)
                .setColorized(false)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setSubText(labelFor(type));

        try {
            Bitmap raw = BitmapFactory.decodeResource(context.getResources(), R.drawable.transiva_logo);
            if (raw != null) {
                int size = Math.max(48, Math.round(52 * context.getResources().getDisplayMetrics().density));
                Bitmap scaled = Bitmap.createScaledBitmap(raw, size, size, true);
                builder.setLargeIcon(scaled);
            }
        } catch (Throwable ignored) {}

        return builder;
    }

    private static String labelFor(String type) {
        String t = type == null ? "" : type.toLowerCase();
        if (t.contains("chat") || t.contains("message")) return "Transiva Chat";
        if (t.contains("wallet") || t.contains("saldo") || t.contains("deposit") || t.contains("withdraw")) return "Transiva Wallet";
        if (t.contains("promo")) return "Promo Transiva";
        if (t.contains("call") || t.contains("webrtc")) return "Panggilan Transiva";
        if (t.contains("order") || t.contains("ride") || t.contains("food") || t.contains("pickup") || t.contains("merchant")) return "Order Transiva";
        return "Transiva";
    }
}
