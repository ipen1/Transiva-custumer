package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import java.util.List;

/** Local device/application state used by Trans Asisten 3.0. No external AI required. */
public final class TransAssistantRuntimeContext {
    public final boolean online;
    public final boolean locationEnabled;
    public final boolean overlayAllowed;
    public final boolean hasActiveOrder;
    public final String activeOrderId;
    public final String activeOrderStatus;
    public final String activeService;

    private TransAssistantRuntimeContext(boolean online, boolean locationEnabled, boolean overlayAllowed,
                                         boolean hasActiveOrder, String activeOrderId, String activeOrderStatus,
                                         String activeService) {
        this.online=online; this.locationEnabled=locationEnabled; this.overlayAllowed=overlayAllowed;
        this.hasActiveOrder=hasActiveOrder; this.activeOrderId=activeOrderId;
        this.activeOrderStatus=activeOrderStatus; this.activeService=activeService;
    }

    public static TransAssistantRuntimeContext read(Context c) {
        boolean gps=false;
        try {
            LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            gps=lm!=null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Throwable ignored) { }
        boolean overlay=Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(c);
        boolean online;
        try { online=TransivaNetworkMonitor.isOnline(); } catch(Throwable t) { online=true; }
        SharedPreferences sp=c.getSharedPreferences("transiva", Context.MODE_PRIVATE);
        String id=safe(sp.getString("active_order_id", ""));
        String status=safe(sp.getString("active_order_status", sp.getString("order_status", "")));
        String service=safe(sp.getString("active_service_name", sp.getString("active_order_type", "")));
        boolean active=!id.isEmpty() && !CustomerOrderState.isEnded(CustomerOrderState.normalize(status));
        try {
            List<UnifiedLiveOrderCenter.Order> orders=UnifiedLiveOrderCenter.snapshot();
            UnifiedLiveOrderCenter.Order order=UnifiedLiveOrderCenter.primary(orders);
            if(order!=null){ active=true; id=order.id; status=order.status; service=order.title(); }
        } catch(Throwable ignored) { }
        return new TransAssistantRuntimeContext(online,gps,overlay,active,id,status,service);
    }

    private static String safe(String s){ return s==null?"":s.trim(); }
}
