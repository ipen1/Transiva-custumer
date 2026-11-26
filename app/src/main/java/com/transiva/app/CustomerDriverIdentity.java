package com.transiva.app;

import org.json.JSONObject;

/** Media and vehicle labels shared by the matching card and the active trip. */
final class CustomerDriverIdentity {
    private CustomerDriverIdentity() {}

    static String portrait(JSONObject driver, JSONObject order, JSONObject response) {
        String vehicle = vehiclePhoto(driver, order, response);
        String[] candidates = {
                driver.optString("driver_photo"), driver.optString("profile_photo"), driver.optString("photo"),
                order.optString("driver_photo"), response.optString("driver_photo")
        };
        for (String candidate : candidates) {
            String url = mediaUrl(candidate);
            if (!url.isEmpty() && !url.equals(vehicle) && !url.endsWith("/assets/default-driver.png")) {
                return url;
            }
        }
        return "";
    }

    static String vehiclePhoto(JSONObject driver, JSONObject order, JSONObject response) {
        return mediaUrl(CallPeerIdentity.first(driver.optString("vehicle_photo"),
                order.optString("vehicle_photo"), response.optString("vehicle_photo")));
    }

    static String mediaUrl(String raw) {
        String path = CallPeerIdentity.first(raw).replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        if (path.startsWith("//")) return "";
        if (path.startsWith("/")) path = path.substring(1);
        // The Driver profile uses /server/uploads for relative paths.
        return path.startsWith("uploads/") ? ApiConfig.server(path) : ImageUrlResolver.resolve(path);
    }

    static String vehicleLabel(JSONObject driver, JSONObject order, JSONObject response, boolean car) {
        String model = CallPeerIdentity.first(driver.optString("vehicle_model"),
                order.optString("vehicle_model"), response.optString("vehicle_model"));
        return model.isEmpty() ? (car ? "Mobil" : "Motor") : model;
    }
}
