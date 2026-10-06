package com.transiva.app;

import org.json.JSONObject;

/** Safety POSTs are single-attempt; never duplicate writes through automatic retry. */
public final class RideSafetyApi {
    private RideSafetyApi() { }
    public static JSONObject post(android.content.Context context, String endpoint, JSONObject body) throws Exception {
        return TransivaHttpRepository.postJson(context, ApiConfig.server(endpoint), body, 7000);
    }
}
