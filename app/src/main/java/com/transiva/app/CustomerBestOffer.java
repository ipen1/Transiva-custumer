package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class CustomerBestOffer {
    public interface Callback { void onOffer(JSONObject offer); }
    private CustomerBestOffer() {}
    public static void load(Context context, String service, Callback cb) {
        new Thread(() -> {
            JSONObject offer = null;
            try {
                String url = ApiConfig.server("customer_best_offer.php") + "?service=" + java.net.URLEncoder.encode(service == null ? "" : service, "UTF-8") + "&_=" + System.currentTimeMillis();
                HttpURLConnection c = CustomerApiClient.open(context, url);
                c.setRequestMethod("GET");
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    StringBuilder b = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line; while ((line = r.readLine()) != null) b.append(line);
                    }
                    JSONObject root = new JSONObject(b.toString());
                    if (root.optBoolean("success", false)) offer = root.optJSONObject("offer");
                }
            } catch (Exception ignored) {}
            final JSONObject result = offer;
            if (cb != null) cb.onOffer(result);
        }, "best-offer").start();
    }
}
