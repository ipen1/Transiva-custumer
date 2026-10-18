package com.transiva.app;

import org.json.JSONObject;
import java.util.Locale;

/** Service/status policy extracted from CustomerHistoryActivity. */
public final class CustomerHistoryPolicy {
    private CustomerHistoryPolicy() {}

    public static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    public static String serviceType(JSONObject order) {
        if (order == null) return "ride";
        return normalized(CustomerCommonFormatters.firstStrict(
                order.optString("order_type"), order.optString("service_type"), order.optString("service"),
                order.optString("service_name"), "ride"));
    }

    /** UI aliases only. Never rewrites the order's service or database state. */
    public static String serviceKey(JSONObject order) {
        if (order == null) return "ride";
        JSONObject note = order.optJSONObject("note");
        if (note == null) {
            try { note = new JSONObject(order.optString("note", "{}")); }
            catch (Exception ignored) { note = new JSONObject(); }
        }
        String[] values = {order.optString("order_type"), order.optString("service_type"),
                order.optString("service"), order.optString("service_name"), order.optString("type"),
                note.optString("service_type"), note.optString("order_type"), note.optString("type")};
        boolean hasType = false;
        String transport = "";
        for (String value : values) {
            String v = normalized(value).replaceAll("[^a-z0-9]", "");
            if (v.isEmpty() || v.equals("null")) continue;
            hasType = true;
            if (v.contains("food") || v.equals("makanan")) return "food";
            if (v.contains("shop") || v.contains("mart") || v.equals("belanja")) return "shop";
            if (v.contains("pickup") || v.contains("send") || v.equals("kurir")) return "pickup";
            if (v.contains("tour") || v.contains("wisata")) return "tour";
            if (v.contains("laundry")) return "laundry";
            // Food/shop notes can identify the business service even when transport is Transbike.
            if (transport.isEmpty() && (v.contains("car") || v.contains("mobil"))) transport = "car";
            if (transport.isEmpty() && (v.contains("ride") || v.contains("bike") || v.contains("motor") || v.contains("ojek"))) transport = "ride";
        }
        String source = normalized(order.optString("_transiva_table") + " " + order.optString("source"));
        if (source.contains("pickup")) return "pickup";
        if (!transport.isEmpty()) return transport;
        return hasType ? "other" : "ride";
    }

    public static String serviceLabel(String key) {
        switch (key) {
            case "ride": return "TransRide";
            case "car": return "TransCar";
            case "food": return "TransFood";
            case "pickup": return "TransSend";
            case "shop": return "TransShop";
            case "tour": return "TransTour";
            case "laundry": return "Laundry";
            default: return "Lainnya";
        }
    }

    public static String serviceName(JSONObject order) { return serviceLabel(serviceKey(order)); }

    /** One pass per status mode, shared by badge counts and tab ordering. */
    public static java.util.Map<String, Integer> serviceCounts(java.util.List<JSONObject> orders, boolean active) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        int total = 0;
        for (JSONObject order : orders) {
            if (isActive(order.optString("status")) != active) continue;
            String key = serviceKey(order);
            Integer previous = counts.get(key);
            counts.put(key, previous == null ? 1 : previous + 1);
            total++;
        }
        counts.put("all", total);
        return counts;
    }

    public static int count(java.util.Map<String, Integer> counts, String key) {
        Integer value = counts.get(key);
        return value == null ? 0 : value;
    }

    public static int serviceCount(java.util.List<JSONObject> orders, String key, boolean active) {
        return count(serviceCounts(orders, active), key);
    }

    public static java.util.List<String> orderedServiceKeys(java.util.List<JSONObject> orders, boolean active) {
        return orderedServiceKeys(serviceCounts(orders, active));
    }

    public static java.util.List<String> orderedServiceKeys(java.util.Map<String, Integer> counts) {
        java.util.List<String> keys = new java.util.ArrayList<>(java.util.Arrays.asList(
                "ride", "car", "food", "pickup", "shop", "tour", "laundry", "other"));
        // Stable partition: populated services come immediately after Semua.
        java.util.Collections.sort(keys, (a, b) -> Boolean.compare(count(counts, b) > 0, count(counts, a) > 0));
        return keys;
    }

    public static String serviceSoftColor(JSONObject order) {
        switch (serviceKey(order)) {
            case "food": return "#FFF4E8";
            case "tour": return "#F2EDFF";
            case "laundry": return "#ECFDF5";
            case "shop": return "#FFF9E8";
            default: return "#EAF4FF";
        }
    }

    public static boolean isCompleted(String status) {
        String s = normalized(status);
        return s.contains("completed") || s.contains("complete") || s.contains("finished") || s.equals("finish")
                || s.contains("selesai") || s.contains("delivered") || s.contains("done") || s.contains("success");
    }

    public static boolean isCanceled(String status) {
        String s = normalized(status);
        return s.equals("merchant_rejected") || s.equals("rejected") || s.equals("cancel")
                || s.equals("customer_cancelled") || s.equals("customer_canceled")
                || s.equals("driver_cancelled") || s.equals("driver_canceled")
                || s.equals("canceled") || s.equals("cancelled") || s.contains("batal")
                || s.contains("failed") || s.contains("expired");
    }

    public static boolean isActive(String status) { return !isCompleted(status) && !isCanceled(status); }

    public static boolean canCustomerCancel(String status) {
        String s = normalized(status);
        return s.equals("pending") || s.equals("merchant_accepted");
    }

    public static boolean canTrack(String status) {
        String s = normalized(status);
        return s.equals("taken") || s.equals("driver_accepted") || s.equals("accepted") || s.equals("driver_assigned")
                || s.equals("assigned") || s.equals("arrived_pickup") || s.equals("on_delivery") || s.equals("arrived_delivery");
    }
}
