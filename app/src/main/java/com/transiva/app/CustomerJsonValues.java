package com.transiva.app;

import org.json.JSONObject;

/** Defensive JSON number parsing shared by customer order screens. */
public final class CustomerJsonValues {
    private CustomerJsonValues() {}

    public static int intValue(JSONObject json, String key, int fallback) {
        if (json == null || key == null) return fallback;
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        try {
            if (value instanceof Number) return ((Number) value).intValue();
            String clean = String.valueOf(value).replace("Rp", "").replace(".", "").replace(",", "").trim();
            return clean.isEmpty() ? fallback : (int) Math.round(Double.parseDouble(clean));
        } catch (Exception ignored) { return fallback; }
    }

    public static double doubleValue(JSONObject json, String key, double fallback) {
        if (json == null || key == null) return fallback;
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            String clean = String.valueOf(value).replace(",", ".").trim();
            return clean.isEmpty() ? fallback : Double.parseDouble(clean);
        } catch (Exception ignored) { return fallback; }
    }
}
