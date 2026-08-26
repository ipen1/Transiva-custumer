package com.transiva.app;

import java.util.Locale;

public final class CustomerMessageStatus {

    private CustomerMessageStatus() {
    }

    public static String normalize(String value) {
        return CustomerOrderState.normalize(value);
    }

    public static boolean isEnded(String rawStatus) {
        return CustomerOrderState.isEnded(rawStatus);
    }

    public static boolean canSend(String rawStatus) {
        return CustomerOrderState.canChat(rawStatus);
    }

    public static String availabilityLabel(
            String rawStatus,
            boolean history
    ) {
        if (history || isEnded(rawStatus)) {
            return "Riwayat • hanya dapat dibaca";
        }

        if (canSend(rawStatus)) {
            return "Chat aktif";
        }

        return "Chat tersedia setelah order diterima";
    }

    public static String orderLabel(
            String rawStatus,
            String serviceType
    ) {
        return OrderStatusPresentation.label(rawStatus, serviceType);
    }
}
