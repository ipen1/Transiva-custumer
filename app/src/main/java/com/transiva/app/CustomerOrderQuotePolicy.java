package com.transiva.app;

import org.json.JSONObject;

/** A refreshed fare always requires another explicit tap; it must never retry an order automatically. */
final class CustomerOrderQuotePolicy {
    private CustomerOrderQuotePolicy() {}
    static boolean refreshRequired(JSONObject response) {
        return response != null && !response.optBoolean("success")
                && !response.optBoolean("order_created", true) && response.optBoolean("refresh_quote");
    }
    static boolean refreshRequired(Exception error) {
        return error instanceof TransivaHttpRepository.ServerResponseException
                && refreshRequired(((TransivaHttpRepository.ServerResponseException) error).response);
    }
}
