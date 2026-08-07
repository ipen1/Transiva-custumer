package com.transiva.app;

import android.content.Context;
import java.net.HttpURLConnection;

/** Compatibility facade. New code should use CustomerApiClient directly. */
public final class ApiSecurity {
    private ApiSecurity() {}
    public static void apply(Context context, HttpURLConnection connection) {
        CustomerApiClient.applySecurity(context, connection);
    }
    public static String idempotencyKey(String action) {
        return CustomerApiClient.idempotencyKey(action);
    }
}
