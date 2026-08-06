package com.transiva.app;

import android.content.Context;
import java.net.HttpURLConnection;
import java.util.UUID;

public final class ApiSecurity {
    private ApiSecurity() {}
    public static void apply(Context context, HttpURLConnection connection) {
        if (context == null || connection == null) return;
        SessionManager session = new SessionManager(context);
        String token = session.getToken();
        if (token != null && !token.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        String deviceUuid = DeviceIdentityManager.getInstallationUuid(context);
        if (deviceUuid != null && !deviceUuid.trim().isEmpty()) {
            connection.setRequestProperty("X-Device-UUID", deviceUuid.trim());
        }
        connection.setRequestProperty("Accept", "application/json");
    }
    public static String idempotencyKey(String action) {
        return (action == null ? "action" : action) + "-" + UUID.randomUUID();
    }
}
