package com.transiva.app;

import org.json.JSONArray;
import org.json.JSONObject;

/** Additive ride metadata shared by Customer, Driver and Admin APIs. */
public final class RideEcosystemFeatures {
    public final JSONArray waypoints = new JSONArray();
    public boolean tripMonitoring = true;
    public boolean audioProtect = false;
    public int groupSize = 1;
    public String splitFareMode = "none";
    public boolean familySharedPayment = false;
    public int familyPayerId = 0;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("schema_version", 2);
            o.put("waypoints", waypoints);
            o.put("trip_monitoring", tripMonitoring);
            o.put("audio_protect", audioProtect);
            o.put("group_size", Math.max(1, groupSize));
            o.put("split_fare_mode", splitFareMode == null ? "none" : splitFareMode);
            o.put("family_shared_payment", familySharedPayment);
            o.put("family_payer_id", familyPayerId);
        } catch (Exception ignored) {}
        return o;
    }

    public void clearWaypoints() { while (waypoints.length() > 0) waypoints.remove(waypoints.length()-1); }
    public boolean addWaypoint(double lat, double lng, String address) {
        if (waypoints.length() >= 2 || Math.abs(lat) > 90 || Math.abs(lng) > 180 || (lat == 0 && lng == 0)) return false;
        try { waypoints.put(new JSONObject().put("sequence", waypoints.length()+1).put("latitude",lat).put("longitude",lng).put("address",address == null ? "Pemberhentian" : address)); return true; }
        catch (Exception e) { return false; }
    }
}
