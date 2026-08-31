package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Reverse-geocoding boundary with server proxy + bounded retry. */
public final class CustomerGeocodingRepository {
    private final Context context;
    public CustomerGeocodingRepository(Context context) { this.context = context.getApplicationContext(); }

    public String reverse(double lat, double lng) {
        try {
            String endpoint = CustomerResourceConfig.geocodeEndpoint(context)
                    + "?lat=" + URLEncoder.encode(String.valueOf(lat), StandardCharsets.UTF_8.name())
                    + "&lng=" + URLEncoder.encode(String.valueOf(lng), StandardCharsets.UTF_8.name());
            JSONObject json = TransivaHttpRepository.getJson(context, endpoint, 12000);
            if (!json.optBoolean("success", false)) return "";
            String address = json.optString("address", "").trim();
            if (!address.isEmpty()) return address;
            return compact(json.optString("display_name", ""));
        } catch (Exception error) {
            TransivaCrashReporter.recordNetworkFailure(error, "GET", "customer_reverse_geocode.php");
            return "";
        }
    }

    private static String compact(String value) {
        if (value == null) return "";
        String[] parts = value.split(","); StringBuilder b = new StringBuilder();
        for (int i=0; i<parts.length && i<3; i++) {
            String p=parts[i].trim(); if(p.isEmpty()) continue; if(b.length()>0)b.append(", "); b.append(p);
        }
        return b.toString();
    }
}
