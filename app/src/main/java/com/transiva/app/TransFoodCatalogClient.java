package com.transiva.app;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;

final class TransFoodCatalogClient {
    private TransFoodCatalogClient() { }

    static String restaurants(int userId, int limit, int offset, String query, String sort, long refresh) {
        return ApiConfig.server("get_food_restaurants.php") + "?page_only=1&user_id=" + userId
                + "&limit=" + limit + "&offset=" + offset + "&q=" + Uri.encode(query)
                + "&sort=" + Uri.encode(sort) + "&refresh=" + refresh;
    }

    static String menus(int restaurantId, String query, int limit, int offset) {
        return ApiConfig.server("get_food_menus.php") + "?restaurant_id=" + restaurantId
                + "&q=" + Uri.encode(query) + "&limit=" + limit + "&offset=" + offset;
    }

    static JSONObject freshPage(Context context, String url, String key, int timeout, String fallback)
            throws Exception {
        JSONObject response=TransivaHttpRepository.getJsonFresh(context,url,timeout);
        if (!response.optBoolean("success") || response.optJSONArray(key)==null) {
            throw new java.io.IOException(response.optString("message",fallback));
        }
        return response;
    }
}
