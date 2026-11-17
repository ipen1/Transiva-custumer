package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;

/** A successful empty result always comes from a verified server response. */
public final class SmartReorderRepository {
    private static final String URL = ApiConfig.ROOT + "server/customer_smart_reorder.php";

    public JSONObject load(Context context, int budgetMs) throws Exception {
        JSONObject result = TransivaHttpRepository.getJsonWithin(context, URL, budgetMs);
        if (!result.optBoolean("success", false) || result.optJSONArray("orders") == null) {
            throw new TransivaHttpRepository.ServerResponseException(
                    "Rekomendasi belum dapat dimuat.", 0, result);
        }
        return result;
    }
}
