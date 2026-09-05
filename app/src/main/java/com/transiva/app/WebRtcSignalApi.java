package com.transiva.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class WebRtcSignalApi {
    private static final int TIMEOUT_MS = 20000;
    private static final String ENDPOINT = "https://transiva.my.id/server/webrtc_call.php";

    private WebRtcSignalApi() {}

    public static JSONObject post(SessionManager session, JSONObject payload) throws Exception {
        if (session == null || session.getAppContext() == null) {
            throw new IllegalStateException("Sesi signaling tidak tersedia");
        }
        JSONObject json = TransivaHttpRepository.postJson(session.getAppContext(), ENDPOINT, payload, TIMEOUT_MS);
        if (!json.optBoolean("success", false)) {
            throw new IllegalStateException(json.optString("message", "Signaling gagal"));
        }
        return json;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
