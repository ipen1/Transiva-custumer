package com.transiva.app;

import android.app.Activity;
import android.content.Intent;

import java.util.List;

/**
 * Server-first order recovery. Local cache is never required to rediscover an active order.
 * This protects recovery after process death, OEM cleanup, app restart, or stale preferences.
 */
public final class ActiveOrderRecovery {
    private ActiveOrderRecovery() { }
    public interface Callback { void onResult(boolean routedToTrip); }

    public static void route(Activity activity, SessionManager session, Callback callback) {
        String userId = clean(session.getUserId());
        String username = clean(session.getUsername());
        if (userId.isEmpty() && username.isEmpty()) { callback.onResult(false); return; }

        UnifiedLiveOrderCenter.refresh(activity, (orders, fromCache, error) -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            UnifiedLiveOrderCenter.Order primary = UnifiedLiveOrderCenter.primary(orders);
            if (primary == null) {
                if (!fromCache) clearLegacy(activity);
                callback.onResult(false);
                return;
            }
            UnifiedLiveOrderCenter.persistLegacyActiveOrder(activity, primary);
            Intent intent = UnifiedLiveOrderCenter.routeIntent(activity, primary);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
            callback.onResult(true);
        });
    }

    private static void clearLegacy(Activity a) {
        a.getSharedPreferences("transiva", Activity.MODE_PRIVATE).edit()
                .remove("active_order_id").remove("active_order_status").remove("active_order_source")
                .remove("active_driver_type").remove("active_order_type").remove("active_service_name")
                .remove("active_order_price").remove("order_id").remove("order_status")
                .remove("pickup_lat").remove("pickup_lng").remove("delivery_lat").remove("delivery_lng").apply();
    }
    private static String clean(String s){return s==null?"":s.trim();}
}
