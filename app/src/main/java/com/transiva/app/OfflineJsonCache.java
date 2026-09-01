package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small stale-while-offline JSON cache for GET-style screens. Never caches wallet/order mutations. */
public final class OfflineJsonCache {
    private static final String PREF = "transiva_offline_json_v1";
    private static final long DEFAULT_TTL = 10L * 60L * 1000L;
    private OfflineJsonCache() {}

    public static void put(Context context, String key, JSONObject value) {
        if (context == null || key == null || value == null) return;
        context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(hash(key), value.toString()).putLong(hash(key)+"_at", System.currentTimeMillis()).apply();
    }

    public static JSONObject get(Context context, String key) { return get(context, key, DEFAULT_TTL, false); }

    public static JSONObject get(Context context, String key, long ttlMs, boolean allowStale) {
        if (context == null || key == null) return null;
        SharedPreferences p=context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String h=hash(key); String raw=p.getString(h, null); long at=p.getLong(h+"_at", 0L);
        if (raw == null) return null;
        if (!allowStale && (at<=0L || System.currentTimeMillis()-at > Math.max(1000L, ttlMs))) return null;
        try { return new JSONObject(raw); } catch (Exception e) { return null; }
    }

    public static void remove(Context context, String key) {
        if (context == null || key == null) return; String h=hash(key);
        context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(h).remove(h+"_at").apply();
    }

    private static String hash(String value) {
        try { MessageDigest d=MessageDigest.getInstance("SHA-256"); byte[] b=d.digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder x=new StringBuilder(); for(byte v:b)x.append(String.format("%02x", v)); return x.toString(); }
        catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}
