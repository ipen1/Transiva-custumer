package com.transiva.app;

import org.json.JSONArray;
import org.json.JSONObject;

final class FoodToppingSelection {
    private static final String BUNDLE = "bundle_topping";

    private FoodToppingSelection() {}

    static boolean required(JSONObject menu) {
        if (menu.optBoolean("topping_required")) return true;
        JSONArray groups = menu.optJSONArray("options");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group != null && BUNDLE.equals(group.optString("type"))
                        && group.optBoolean("required")) return true;
            }
        }
        return false;
    }

    static boolean hasTopping(JSONArray choices) {
        if (choices == null) return false;
        for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i);
            if (choice != null && BUNDLE.equals(choice.optString("type"))
                    && !choice.optString("name").trim().isEmpty()) return true;
        }
        return false;
    }
}
