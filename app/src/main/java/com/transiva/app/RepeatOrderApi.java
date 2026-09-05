package com.transiva.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class RepeatOrderApi {

    private static final int TIMEOUT_MS = 25000;

    private RepeatOrderApi() {
    }

    public static JSONObject get(String url)
            throws Exception {
        return request("GET", url, null);
    }

    public static JSONObject post(
            String url,
            JSONObject body
    ) throws Exception {
        return request("POST", url, body);
    }

    private static JSONObject request(String method, String url, JSONObject body) throws Exception {
        android.app.Application app = TransivaCustomerApplication.appContext();
        if (app == null) throw new IllegalStateException("Application context unavailable");
        if ("GET".equalsIgnoreCase(method)) {
            return TransivaHttpRepository.getJson(app, url, TIMEOUT_MS);
        }
        return TransivaHttpRepository.postJson(app, url, body == null ? new JSONObject() : body, TIMEOUT_MS);
    }

}
