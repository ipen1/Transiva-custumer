package com.transiva.app;

import java.util.Locale;

/**
 * Single source of truth for customer-side order state.
 * IMPORTANT: database status is never rewritten here. The canonical driver flow remains:
 * driver_accepted -> arrived_pickup -> on_delivery -> arrived_delivery -> finished.
 * Legacy aliases are normalized only for UI/routing compatibility.
 */
public final class CustomerOrderState {
    public static final String PENDING = "pending";
    public static final String MERCHANT_ACCEPTED = "merchant_accepted";
    public static final String DRIVER_ACCEPTED = "driver_accepted";
    public static final String ARRIVED_PICKUP = "arrived_pickup";
    public static final String ON_DELIVERY = "on_delivery";
    public static final String ARRIVED_DELIVERY = "arrived_delivery";
    public static final String FINISHED = "finished";
    public static final String CANCELED = "canceled";

    private CustomerOrderState() {}

    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        switch (s) {
            case "accepted":
            case "assigned":
            case "driver_assigned":
            case "taken":
            case "taken_by_driver":
                return DRIVER_ACCEPTED;
            case "arrived":
                return ARRIVED_PICKUP;
            case "picked_up":
            case "on_trip":
            case "in_progress":
            case "processing":
            case "ongoing":
            case "started":
                return ON_DELIVERY;
            case "finish":
            case "completed":
            case "complete":
            case "done":
            case "selesai":
                return FINISHED;
            case "cancelled":
            case "cancel":
            case "dibatalkan":
            case "rejected":
            case "expired":
            case "merchant_rejected":
                return CANCELED;
            default:
                return s;
        }
    }

    public static boolean isSearching(String raw) {
        String s = normalize(raw);
        return s.isEmpty() || PENDING.equals(s) || MERCHANT_ACCEPTED.equals(s) || "scheduled".equals(s);
    }

    public static boolean hasDriver(String raw) {
        int rank = rank(raw);
        return rank >= 20 && rank < 90;
    }

    public static boolean isTrip(String raw) {
        int rank = rank(raw);
        return rank >= 20 && rank < 90;
    }

    public static boolean isEnded(String raw) {
        String s = normalize(raw);
        return FINISHED.equals(s) || CANCELED.equals(s);
    }

    public static boolean isFinished(String raw) {
        return FINISHED.equals(normalize(raw));
    }

    public static boolean isCanceled(String raw) {
        return CANCELED.equals(normalize(raw));
    }

    public static boolean canChat(String raw) {
        return hasDriver(raw) || MERCHANT_ACCEPTED.equals(normalize(raw));
    }

    public static boolean targetsDelivery(String raw) {
        String s = normalize(raw);
        return ON_DELIVERY.equals(s) || ARRIVED_DELIVERY.equals(s) || FINISHED.equals(s);
    }

    /** Monotonic rank used only to prevent UI state from moving backwards. */
    public static int rank(String raw) {
        switch (normalize(raw)) {
            case PENDING: return 0;
            case MERCHANT_ACCEPTED: return 10;
            case DRIVER_ACCEPTED: return 20;
            case ARRIVED_PICKUP: return 30;
            case ON_DELIVERY: return 40;
            case ARRIVED_DELIVERY: return 50;
            case FINISHED: return 100;
            case CANCELED: return 110;
            default: return -1;
        }
    }

    public static String laterOf(String current, String incoming) {
        String c = normalize(current);
        String n = normalize(incoming);
        if (n.isEmpty()) return c;
        if (c.isEmpty()) return n;
        if (isEnded(c)) return c;
        if (isEnded(n)) return n;
        int cr = rank(c), nr = rank(n);
        if (cr >= 0 && nr >= 0 && nr < cr) return c;
        return n;
    }
}
