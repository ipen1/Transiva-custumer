package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;

/** Network/data boundary extracted from CustomerHistoryActivity. */
public final class CustomerHistoryRepository {
    private final Context context;
    private final int timeoutMs;
    public CustomerHistoryRepository(Context context, int timeoutMs) {
        this.context = context.getApplicationContext();
        this.timeoutMs = timeoutMs;
    }
    public JSONObject get(String endpoint) throws Exception {
        return TransivaHttpRepository.getJson(context, endpoint, timeoutMs);
    }
    public JSONObject post(String endpoint, JSONObject payload) throws Exception {
        return TransivaHttpRepository.postJson(context, endpoint, payload, timeoutMs);
    }
}
