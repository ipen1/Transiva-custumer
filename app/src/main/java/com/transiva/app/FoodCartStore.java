package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local drafts are isolated by account and restaurant. The server owns checkout prices. */
final class FoodCartStore {
    private static final String PREFS = "transiva_order_attempts";
    private final SharedPreferences prefs;
    private final String prefix;

    FoodCartStore(Context context, int userId) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefix = userId > 0 ? prefix(String.valueOf(userId)) : "";
    }

    private static String prefix(String user) { return "food_cart:" + user + ":"; }

    static final class Cart {
        final int restaurantId;
        final List<CartItem> items = new ArrayList<>();
        String name = "";
        String photo = "";
        String draftId = UUID.randomUUID().toString();
        private String snapshot = "";

        Cart(int restaurantId, String snapshot) {
            this.restaurantId = restaurantId;
            this.snapshot = snapshot;
            if (snapshot.isEmpty()) return;
            try {
                JSONObject saved = new JSONObject(snapshot);
                name = saved.optString("name");
                photo = saved.optString("photo");
                draftId = saved.optString("draft_id", draftId);
                JSONArray lines = saved.getJSONArray("items");
                for (int i = 0; i < lines.length(); i++) {
                    JSONObject row = lines.optJSONObject(i);
                    if (row == null || row.optInt("id") <= 0 || row.optInt("qty") <= 0
                            || row.optString("line_id").isEmpty()) continue;
                    CartItem line = new CartItem(row.optString("line_id"));
                    line.id = row.optInt("id");
                    line.qty = row.optInt("qty");
                    line.price = row.optDouble("price", 0);
                    line.name = row.optString("name", "Menu");
                    line.optionText = row.optString("option_text");
                    line.image = row.optString("image");
                    line.selectionOrder = row.optLong("selection_order");
                    JSONArray options = row.optJSONArray("selected_options");
                    line.selectedOptions = options == null ? new JSONArray() : options;
                    line.key = line.id + "|" + line.selectedOptions.toString();
                    items.add(line);
                }
            } catch (JSONException invalid) {
                android.util.Log.w("FoodCartStore", "Cannot read food cart", invalid);
            }
        }

        int quantity() {
            int total = 0;
            for (CartItem item : items) total += item.qty;
            return total;
        }

        double subtotal() {
            double total = 0;
            for (CartItem item : items) total += item.price * item.qty;
            return total;
        }

        private String serialize() throws JSONException {
            JSONArray rows = new JSONArray();
            for (CartItem line : items) {
                rows.put(new JSONObject().put("id", line.id).put("qty", line.qty)
                        .put("price", line.price).put("name", line.name).put("option_text", line.optionText)
                        .put("image", line.image).put("selection_order", line.selectionOrder)
                        .put("selected_options", line.selectedOptions).put("line_id", line.lineId));
            }
            return new JSONObject().put("name", name).put("photo", photo).put("draft_id", draftId).put("items", rows).toString();
        }
    }

    static final class CartItem {
        final String lineId;
        int id, qty;
        String name, key, optionText, image;
        double price;
        long selectionOrder;
        JSONArray selectedOptions;

        CartItem() { this(UUID.randomUUID().toString()); }
        private CartItem(String lineId) { this.lineId = lineId; }
    }

    Cart get(int restaurantId) {
        return new Cart(restaurantId, prefix.isEmpty() ? "" : prefs.getString(prefix + restaurantId, ""));
    }

    List<Cart> all() {
        List<Cart> result = new ArrayList<>();
        if (prefix.isEmpty()) return result;
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith(prefix)) continue;
            int restaurantId = Integer.parseInt(key.substring(prefix.length()));
            Cart cart = get(restaurantId);
            if (!cart.items.isEmpty()) result.add(cart);
        }
        java.util.Collections.sort(result, (a, b) -> {
            int nameOrder = a.name.compareToIgnoreCase(b.name);
            return nameOrder != 0 ? nameOrder : Integer.compare(a.restaurantId, b.restaurantId);
        });
        return result;
    }

    boolean save(Cart cart) {
        if (prefix.isEmpty() || cart.restaurantId <= 0) return false;
        String key = prefix + cart.restaurantId;
        synchronized (FoodCartStore.class) {
            if (!cart.snapshot.equals(prefs.getString(key, ""))) return false;
            try {
                String next = cart.items.isEmpty() ? "" : cart.serialize();
                SharedPreferences.Editor edit = prefs.edit();
                if (next.isEmpty()) edit.remove(key);
                else edit.putString(key, next);
                // SharedPreferences publishes the edit in memory before scheduling its disk write.
                edit.apply();
                cart.snapshot = next;
                if (next.isEmpty()) cart.draftId = UUID.randomUUID().toString();
                return true;
            } catch (JSONException invalid) {
                return false;
            }
        }
    }

    static boolean matchesDraft(SharedPreferences prefs, String user, JSONObject payload) {
        JSONArray items = payload.optJSONArray("items");
        if (items == null) return true;
        Cart cart = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !item.has("_cart_line_id")) continue; // Legacy and reorder clients.
            if (cart == null) {
                int rid = payload.optInt("restaurant_id");
                cart = new Cart(rid, prefs.getString(prefix(user) + rid, ""));
            }
            String lineId = item.optString("_cart_line_id");
            boolean found = false;
            for (CartItem line : cart.items) {
                if (line.lineId.equals(lineId) && line.id == item.optInt("id") && line.qty >= item.optInt("qty")) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** Called under the same lock as save, in the commit that acknowledges the order attempt. */
    static void consume(SharedPreferences prefs, SharedPreferences.Editor edit, String user,
                        JSONObject payload) throws JSONException {
        int restaurantId = payload.optInt("restaurant_id");
        JSONArray purchased = payload.optJSONArray("items");
        if (restaurantId <= 0 || purchased == null) return;
        String key = prefix(user) + restaurantId;
        Cart cart = new Cart(restaurantId, prefs.getString(key, ""));
        if (cart.items.isEmpty()) return;
        boolean changed = false;
        for (int i = 0; i < purchased.length(); i++) {
            JSONObject item = purchased.optJSONObject(i);
            if (item == null || item.optInt("qty") <= 0) continue;
            String lineId = item.optString("_cart_line_id");
            if (lineId.isEmpty()) continue;
            for (int j = 0; j < cart.items.size(); j++) {
                CartItem line = cart.items.get(j);
                if (!lineId.equals(line.lineId) || item.optInt("id") != line.id) continue;
                line.qty -= Math.min(99, item.optInt("qty"));
                if (line.qty <= 0) cart.items.remove(j);
                changed = true;
                break;
            }
        }
        if (changed) {
            if (cart.items.isEmpty()) edit.remove(key);
            else edit.putString(key, cart.serialize());
        }
    }
}
