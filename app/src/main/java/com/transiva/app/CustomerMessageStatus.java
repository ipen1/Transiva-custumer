package com.transiva.app;

import java.util.Locale;

public final class CustomerMessageStatus {

    private CustomerMessageStatus() {
    }

    public static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    public static boolean isEnded(String rawStatus) {
        String status = normalize(rawStatus);

        return status.equals("finished")
                || status.equals("finish")
                || status.equals("completed")
                || status.equals("canceled")
                || status.equals("cancelled")
                || status.equals("merchant_rejected");
    }

    public static boolean canSend(String rawStatus) {
        String status = normalize(rawStatus);

        return status.equals("merchant_accepted")
                || status.equals("driver_accepted")
                || status.equals("accepted")
                || status.equals("assigned")
                || status.equals("driver_assigned")
                || status.equals("taken")
                || status.equals("arrived_pickup")
                || status.equals("picked_up")
                || status.equals("on_trip")
                || status.equals("on_delivery")
                || status.equals("in_progress")
                || status.equals("ongoing")
                || status.equals("started")
                || status.equals("arrived_delivery");
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
