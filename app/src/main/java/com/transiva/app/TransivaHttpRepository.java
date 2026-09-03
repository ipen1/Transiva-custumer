package com.transiva.app;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/** Central JSON HTTP repository with bounded retry for idempotent reads. */
public final class TransivaHttpRepository {
    private TransivaHttpRepository() { }

    public static JSONObject getJson(Context context, String url, int timeoutMs) throws Exception {
        return request(context, "GET", url, null, timeoutMs, 2, null);
    }

    public static JSONObject postJson(Context context, String url, JSONObject body, int timeoutMs) throws Exception {
        return request(context, "POST", url, body, timeoutMs, 0, null);
    }

    /** Safe POST retry is opt-in and requires one stable idempotency key supplied by the caller. */
    public static JSONObject postJsonIdempotent(Context context, String url, JSONObject body, int timeoutMs, String idempotencyKey) throws Exception {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) throw new IllegalArgumentException("idempotencyKey required");
        return request(context, "POST", url, body, timeoutMs, 1, idempotencyKey.trim());
    }

    private static JSONObject request(Context context, String method, String url, JSONObject body,
                                      int timeoutMs, int retries, String idempotencyKey) throws Exception {
        Exception last = null;
        int attempts = Math.max(1, retries + 1);
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (!TransivaNetworkMonitor.isOnline()) {
                last = new java.net.UnknownHostException("Perangkat sedang offline");
                if (attempt + 1 >= attempts) throw last;
            }
            HttpURLConnection c = null;
            long started = SystemClock.elapsedRealtime();
            try {
                c = CustomerApiClient.open(context, url);
                c.setRequestMethod(method);
                c.setConnectTimeout(Math.max(3000, timeoutMs));
                c.setReadTimeout(Math.max(3000, timeoutMs));
                c.setUseCaches(false);
                c.setRequestProperty("Accept", "application/json");
                if (idempotencyKey != null) c.setRequestProperty("X-Idempotency-Key", idempotencyKey);
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream out = c.getOutputStream()) { out.write(bytes); out.flush(); }
                }
                int code = c.getResponseCode();
                TransivaCrashReporter.recordHttpStatus(code, method, url, SystemClock.elapsedRealtime() - started);
                InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                String raw = read(in);
                if (code >= 200 && code < 300) return new JSONObject(raw.isEmpty() ? "{}" : raw);
                boolean retryable = code == 408 || code == 429 || code >= 500;
                IllegalStateException error = new IllegalStateException("HTTP " + code);
                if (!retryable || attempt + 1 >= attempts) throw error;
                last = error;
            } catch (Exception error) {
                last = error;
                TransivaCrashReporter.recordNetworkFailure(error, method, url);
                if (attempt + 1 >= attempts) throw error;
            } finally { if (c != null) c.disconnect(); }
            // P2: bounded exponential backoff + jitter prevents many clients retrying together.
            long baseDelay = Math.min(1200L, 220L * (1L << Math.min(attempt, 2)));
            long jitter = ThreadLocalRandom.current().nextLong(60L, 181L);
            try { Thread.sleep(baseDelay + jitter); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw interrupted; }
        }
        throw last == null ? new IllegalStateException("request failed") : last;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) b.append(line);
            return b.toString();
        }
    }
}
