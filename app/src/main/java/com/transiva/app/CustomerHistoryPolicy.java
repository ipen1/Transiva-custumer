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

    public static String serviceName(JSONObject order) {
        String type = serviceType(order);
        if (type.contains("food")) return "TransFood";
        if (type.contains("tour") || type.contains("wisata")) return "TransTour";
        if (type.contains("laundry")) return "Laundry";
        if (type.contains("pickup")) return "TransSend";
        if (type.contains("mart") || type.contains("shop")) return "TransShop";
        if (type.contains("car") || type.contains("mobil")) return "TransCar";
        return "TransRide";
    }

    public static String serviceSoftColor(JSONObject order) {
        String type = serviceType(order);
        if (type.contains("food")) return "#FFF4E8";
        if (type.contains("tour") || type.contains("wisata")) return "#F2EDFF";
        if (type.contains("laundry")) return "#ECFDF5";
        if (type.contains("pickup")) return "#EEF6FF";
        if (type.contains("mart") || type.contains("shop")) return "#FFF9E8";
        return "#EAF4FF";
    }

    public static boolean isCompleted(String status) {
        String s = normalized(status);
        return s.contains("completed") || s.contains("complete") || s.contains("finished") || s.equals("finish")
                || s.contains("selesai") || s.contains("delivered") || s.contains("done") || s.contains("success");
    }

    public static boolean isCanceled(String status) {
        String s = normalized(status);
        return s.equals("merchant_rejected") || s.equals("canceled") || s.equals("cancelled") || s.contains("batal")
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
