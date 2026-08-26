package com.transiva.app;

import android.content.Context;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/** Single customer HTTP/security gateway for Transiva-owned endpoints. */
public final class CustomerApiClient {
    private CustomerApiClient() {}

    public static HttpURLConnection open(Context context, String urlText) throws IOException {
        URL url = new URL(urlText);
        String scheme = url.getProtocol() == null ? "" : url.getProtocol().toLowerCase();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IOException("Unsupported network scheme");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("X-Request-ID", idempotencyKey("request"));
        if (isTransivaOwned(urlText)) applySecurity(context, connection);
        return connection;
    }

    public static void applySecurity(Context context, HttpURLConnection connection) {
        if (context == null || connection == null) return;
        SessionManager session = new SessionManager(context.getApplicationContext());
        String token = session.getToken();
        if (token != null && !token.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        String uuid = DeviceIdentityManager.getInstallationUuid(context.getApplicationContext());
        if (uuid != null && !uuid.trim().isEmpty()) {
            connection.setRequestProperty("X-Device-UUID", uuid.trim());
        }
        connection.setRequestProperty("X-App-Scope", "customer");
        connection.setRequestProperty("Accept", "application/json");
    }

    public static String idempotencyKey(String action) {
        return (action == null || action.trim().isEmpty() ? "action" : action.trim()) + "-" + UUID.randomUUID();
    }

    private static boolean isTransivaOwned(String url) {
        if (url == null) return false;
        String v = url.trim().toLowerCase();
        return v.startsWith("https://transiva.my.id/");
    }
}
