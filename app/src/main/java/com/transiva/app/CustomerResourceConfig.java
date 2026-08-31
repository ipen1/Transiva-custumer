package com.transiva.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads data-only configuration shipped by the signed customer resource snapshot. */
public final class CustomerResourceConfig {
    private static final String DEFAULT_ROUTE = "https://transiva.my.id/server/customer_route_proxy.php";
    private static final String DEFAULT_GEOCODE = "https://transiva.my.id/server/customer_reverse_geocode.php";
    private static volatile int cachedVersion = -1;
    private static volatile JSONObject cached = new JSONObject();

    private CustomerResourceConfig() { }

    public static boolean feature(Context context, String key, boolean fallback) {
        JSONObject root = root(context);
        JSONObject flags = root.optJSONObject("feature_flags");
        return flags == null ? fallback : flags.optBoolean(key, fallback);
    }

    public static String routeEndpoint(Context context) {
        return httpsTransiva(root(context).optString("route_endpoint", ""), DEFAULT_ROUTE);
    }

    public static String geocodeEndpoint(Context context) {
        return httpsTransiva(root(context).optString("geocode_endpoint", ""), DEFAULT_GEOCODE);
    }

    public static JSONArray banners(Context context) {
        JSONArray arr = root(context).optJSONArray("dashboard_banners");
        return arr == null ? new JSONArray() : arr;
    }

    public static JSONObject root(Context context) {
        if (context == null) return new JSONObject();
        int version = CustomerResourceUpdateManager.installedVersion(context);
        if (version == cachedVersion) return cached;
        synchronized (CustomerResourceConfig.class) {
            if (version == cachedVersion) return cached;
            JSONObject next = new JSONObject();
            try {
                File file = CustomerResourceUpdateManager.file(context, "config.json");
                if (file != null && file.length() > 0 && file.length() < 512 * 1024) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        StringBuilder text = new StringBuilder(); String line;
                        while ((line = reader.readLine()) != null) text.append(line);
                        next = new JSONObject(text.toString());
                    }
                }
            } catch (Throwable error) {
                TransivaCrashReporter.record(error, "resource_config", "config_parse");
            }
            cached = next;
            cachedVersion = version;
            return cached;
        }
    }

    public static void invalidate() { cachedVersion = -1; cached = new JSONObject(); }

    private static String httpsTransiva(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.startsWith("https://transiva.my.id/") ? v : fallback;
    }
}
