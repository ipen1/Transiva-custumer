package com.transiva.app;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded JSON reads; mutations are never retried without explicit opt-in. */
public final class TransivaHttpRepository {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    public static final class ServerResponseException extends Exception {
        public final int status;
        public final JSONObject response;
        public ServerResponseException(String message) { this(message, 0); }
        public ServerResponseException(String message, int status) { this(message, status, null); }
        public ServerResponseException(String message, int status, JSONObject response) { super(message); this.status = status; this.response = response; }
    }
    private TransivaHttpRepository() { }
    public static JSONObject getJson(Context context, String url, int timeoutMs) throws Exception {
        return measuredRead(context, url, timeoutMs, 1);
    }
    public static JSONObject getJsonOnce(Context context, String url, int timeoutMs) throws Exception {
        return measuredRead(context, url, timeoutMs, 0);
    }
    private static JSONObject measuredRead(Context context, String url, int timeoutMs, int retries) throws Exception {
        long started = SystemClock.elapsedRealtime(); boolean success = false;
        try {
            CustomerTransportSession session=CustomerTransportSession.capture(context);
            long ttl=CustomerReadCachePolicy.ttl(url);
            String cacheKey=ttl>0?OfflineJsonCache.scoped(context,"read:"+url):null;
            if(ttl>0){JSONObject cached=OfflineJsonCache.getScoped(context,cacheKey,ttl,false);if(cached!=null){session.check(context);success=true;return cached;}}
            JSONObject result=request(context,"GET",url,null,timeoutMs,retries,null,session);
            session.check(context);
            if(ttl>0&&result.optBoolean("success",false))OfflineJsonCache.putScoped(context,cacheKey,result);
            success=true;return result;
        }
        finally { try { CustomerPerformanceMetrics.load(context, CustomerPerformanceMetrics.readCategory(url), started, success); } catch (Throwable ignored) { } }
    }
    public static JSONObject postJson(Context context, String url, JSONObject body, int timeoutMs) throws Exception {
        return request(context, "POST", url, body, timeoutMs, 0, null);
    }
    public static JSONObject postJsonIdempotent(Context context, String url, JSONObject body,
                                               int timeoutMs, String key) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("idempotencyKey required");
        return request(context, "POST", url, body, timeoutMs, 1, key.trim());
    }
    static JSONObject getJsonOnce(Context context,String url,int timeoutMs,CustomerTransportSession owner)throws Exception {
        return request(context,"GET",url,null,timeoutMs,0,null,owner);
    }
    static JSONObject postJson(Context context,String url,JSONObject body,int timeoutMs,CustomerTransportSession owner)throws Exception {
        return request(context,"POST",url,body,timeoutMs,0,null,owner);
    }
    private static JSONObject request(Context context,String method,String url,JSONObject body,int timeoutMs,int retries,String key)throws Exception {
        return request(context,method,url,body,timeoutMs,retries,key,CustomerTransportSession.capture(context));
    }
    private static JSONObject request(Context context, String method, String url, JSONObject body,
                                      int timeoutMs, int retries, String key,CustomerTransportSession owner) throws Exception {
        owner.check(context);
        int socketTimeout = Math.max(3000, Math.min(30000, timeoutMs));
        long deadline = SystemClock.elapsedRealtime() + socketTimeout * 2L;
        for (int attempt = 0; ; attempt++) {
            checkCancelled(deadline);
            owner.check(context);
            if (!TransivaNetworkMonitor.isOnline()) throw new java.net.UnknownHostException("Perangkat sedang offline");
            HttpURLConnection c = null;
            long started = SystemClock.elapsedRealtime();
            try {
                c = CustomerApiClient.open(context, url);
                owner.pin(context,c);
                CustomerLoadingCoordinator.attach(c);
                CustomerRequestCancellation.attach(c);
                c.setRequestMethod(method);
                c.setConnectTimeout(remaining(deadline, socketTimeout));
                c.setReadTimeout(remaining(deadline, socketTimeout));
                if (key != null) {
                    c.setRequestProperty("Idempotency-Key", key);
                    c.setRequestProperty("X-Idempotency-Key", key);
                }
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    c.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
                }
                int code = c.getResponseCode();
                TransivaCrashReporter.recordHttpStatus(code, method, url, SystemClock.elapsedRealtime() - started);
                c.setReadTimeout(remaining(deadline, socketTimeout));
                String raw = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(), deadline);
                owner.check(context);
                if((code==401||code==403)&&NetworkSafetyRules.maySendCredentials(url)) {
                    // Login changes and logout application are serialized on the UI thread.
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                        if(owner.matches(context))CustomerApiClient.handleSessionResponse(context,code,raw);
                    });
                }
                owner.check(context);
                if (code >= 200 && code < 300) return new JSONObject(raw.isEmpty() ? "{}" : raw);
                String message = "Server belum dapat memproses permintaan (" + code + ").";
                JSONObject errorBody = null;
                try { errorBody = new JSONObject(raw); message = errorBody.optString("message", message); } catch (Exception ignored) { }
                throw new ServerResponseException(message, code, errorBody);
            } catch (ServerResponseException error) {
                // Do not let a generic catch accidentally retry 401/403/404/429.
                if (attempt >= retries || !NetworkSafetyRules.retryableStatus(error.status)) throw error;
            } catch (IOException error) {
                TransivaCrashReporter.recordNetworkFailure(error, method, url);
                if (Thread.currentThread().isInterrupted() || error instanceof javax.net.ssl.SSLException
                        || attempt >= retries) throw error;
            } finally { if (c != null) { CustomerLoadingCoordinator.detach(c); CustomerRequestCancellation.detach(c); c.disconnect(); } }
            checkCancelled(deadline);
            long delay = 220L + ThreadLocalRandom.current().nextLong(60L, 181L);
            if (SystemClock.elapsedRealtime() + delay >= deadline) throw new java.net.SocketTimeoutException("Waktu permintaan habis");
            try { Thread.sleep(delay); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
        }
    }
    private static int remaining(long deadline, int cap) throws java.net.SocketTimeoutException {
        long value = deadline - SystemClock.elapsedRealtime();
        if (value <= 0L) throw new java.net.SocketTimeoutException("Waktu permintaan habis");
        return (int) Math.max(1L, Math.min(value, cap));
    }
    private static void checkCancelled(long deadline) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Request cancelled");
        remaining(deadline, 30000);
    }
    private static String read(InputStream in, long deadline) throws Exception {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) {
                checkCancelled(deadline);
                if (out.size() + n > MAX_RESPONSE_BYTES) throw new IllegalStateException("Respons server terlalu besar");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
