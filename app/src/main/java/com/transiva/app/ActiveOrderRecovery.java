package com.transiva.app;

import android.app.Activity;
import android.content.Intent;

import org.json.JSONObject;

/** Routes the verified result of the dashboard's first active-order read. */
public final class ActiveOrderRecovery {
    public static final String CHECK_ON_DASHBOARD = "transiva_check_order_on_dashboard";

    private ActiveOrderRecovery() {}

    public static boolean open(Activity activity, JSONObject order) {
        if (order == null || activity.isFinishing() || activity.isDestroyed()) return false;
        UnifiedLiveOrderCenter.Order active = new UnifiedLiveOrderCenter.Order(order);
        if (active.id.isEmpty() || CustomerOrderState.isEnded(active.status)) return false;

        UnifiedLiveOrderCenter.persistLegacyActiveOrder(activity, active);
        Intent intent = UnifiedLiveOrderCenter.routeIntent(activity, active);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }

    public static void clearLegacy(Activity activity) {
        activity.getSharedPreferences("transiva", Activity.MODE_PRIVATE).edit()
                .remove("active_order_id").remove("active_order_status").remove("active_order_source")
                .remove("active_driver_type").remove("active_order_type").remove("active_service_name")
                .remove("active_order_price").remove("order_id").remove("order_status")
                .remove("pickup_lat").remove("pickup_lng").remove("delivery_lat").remove("delivery_lng").apply();
    }
}
