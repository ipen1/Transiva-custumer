package com.transiva.app;

import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

public final class RepeatOrderData {

    public final String orderId;
    public final String orderType;

    public final String pickupAddress;
    public final double pickupLat;
    public final double pickupLng;

    public final String deliveryAddress;
    public final double deliveryLat;
    public final double deliveryLng;

    public final String note;

    public final int restaurantId;
    public final String restaurantName;
    public final JSONArray foodItems;
    public final String deliveryMode;

    private RepeatOrderData(
            String orderId,
            String orderType,
            String pickupAddress,
            double pickupLat,
            double pickupLng,
            String deliveryAddress,
            double deliveryLat,
            double deliveryLng,
            String note,
            int restaurantId,
            String restaurantName,
            JSONArray foodItems,
            String deliveryMode
    ) {
        this.orderId = safe(orderId);
        this.orderType = safe(orderType);

        this.pickupAddress = safe(pickupAddress);
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;

        this.deliveryAddress = safe(deliveryAddress);
        this.deliveryLat = deliveryLat;
        this.deliveryLng = deliveryLng;

        this.note = safe(note);

        this.restaurantId = restaurantId;
        this.restaurantName = safe(restaurantName);
        this.foodItems = foodItems == null
                ? new JSONArray()
                : foodItems;
        this.deliveryMode = safe(deliveryMode).isEmpty()
                ? "standard"
                : safe(deliveryMode);
    }

    public static RepeatOrderData fromOrder(
            JSONObject order
    ) {
        if (order == null) {
            order = new JSONObject();
        }

        return new RepeatOrderData(
                first(
                        order.optString("order_id"),
                        order.optString("id")
                ),
                first(
                        order.optString("order_type"),
                        order.optString("service_type"),
                        order.optString("service")
                ),
                order.optString("pickup_address", ""),
                order.optDouble("pickup_lat", 0),
                order.optDouble("pickup_lng", 0),
                order.optString("delivery_address", ""),
                order.optDouble("delivery_lat", 0),
                order.optDouble("delivery_lng", 0),
                first(
                        order.optString("plain_note"),
                        order.optString("note")
                ),
                order.optInt("restaurant_id", 0),
                order.optString("restaurant_name", ""),
                order.optJSONArray("food_items"),
                order.optString("delivery_mode", "standard")
        );
    }

    public static RepeatOrderData fromIntent(
            Intent intent
    ) {
        String raw = intent == null
                ? ""
                : intent.getStringExtra("repeat_order_json");

        try {
            return fromOrder(new JSONObject(raw));
        } catch (Exception ignored) {
            return fromOrder(new JSONObject());
        }
    }

    public void putInto(Intent intent) {
        if (intent == null) {
            return;
        }

        intent.putExtra(
                "repeat_order_json",
                toJson().toString()
        );
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        try {
            json.put("order_id", orderId);
            json.put("order_type", orderType);

            json.put("pickup_address", pickupAddress);
            json.put("pickup_lat", pickupLat);
            json.put("pickup_lng", pickupLng);

            json.put("delivery_address", deliveryAddress);
            json.put("delivery_lat", deliveryLat);
            json.put("delivery_lng", deliveryLng);

            json.put("plain_note", note);

            json.put("restaurant_id", restaurantId);
            json.put("restaurant_name", restaurantName);
            json.put("food_items", foodItems);
            json.put("delivery_mode", deliveryMode);
        } catch (Exception ignored) {
        }

        return json;
    }

    public boolean isFood() {
        String type = orderType.toLowerCase();

        return type.contains("food");
    }

    public boolean isCar() {
        String type = orderType.toLowerCase();

        return type.contains("car")
                || type.contains("mobil");
    }

    public boolean hasValidRideCoordinates() {
        return validCoordinate(pickupLat, pickupLng)
                && validCoordinate(deliveryLat, deliveryLng);
    }

    public static boolean validCoordinate(
            double latitude,
            double longitude
    ) {
        return latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180
                && latitude != 0
                && longitude != 0;
    }

    private static String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            value = safe(value);

            if (
                    !value.isEmpty()
                            && !"null".equalsIgnoreCase(value)
                            && !"undefined".equalsIgnoreCase(value)
            ) {
                return value;
            }
        }

        return "";
    }
}
