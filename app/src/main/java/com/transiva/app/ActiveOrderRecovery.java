package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memulihkan order customer hanya setelah status aktif dikonfirmasi oleh server.
 * Data SharedPreferences tidak lagi dianggap sebagai bukti bahwa order masih berjalan.
 */
public final class ActiveOrderRecovery {
    private static final String ACTIVE_ORDERS_URL =
            "https://transiva.my.id/server/customer_get_active_orders.php";
    private static final int TIMEOUT_MS = 12000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ActiveOrderRecovery() { }

    public interface Callback {
        void onResult(boolean routedToTrip);
    }

    public static void route(Activity activity, SessionManager session, Callback callback) {
        SharedPreferences sp = activity.getSharedPreferences("transiva", Activity.MODE_PRIVATE);
        String savedOrderId = clean(sp.getString("active_order_id", ""));

        // Tidak ada jejak order lokal: jangan pernah membuka activity trip/order.
        if (savedOrderId.isEmpty()) {
            callback.onResult(false);
            return;
        }

        String userId = clean(session.getUserId());
        String username = clean(session.getUsername());

        if (userId.isEmpty() && username.isEmpty()) {
            clearActiveOrder(sp);
            callback.onResult(false);
            return;
        }

        IO.execute(() -> {
            ActiveOrder activeOrder = null;
            try {
                String requestUrl = ACTIVE_ORDERS_URL
                        + "?user_id=" + Uri.encode(userId)
                        + "&username=" + Uri.encode(username)
                        + "&_=" + System.currentTimeMillis();

                JSONObject response = getJson(requestUrl);
                JSONArray orders = response.optJSONArray("orders");

                if (response.optBoolean("success", false) && orders != null) {
                    for (int index = 0; index < orders.length(); index++) {
                        JSONObject order = orders.optJSONObject(index);
                        if (order == null) continue;

                        String orderId = firstNonEmpty(
                                order.optString("order_id", ""),
                                order.optString("id_order", ""),
                                order.optString("id", "")
                        );
                        String status = clean(order.optString("status", ""))
                                .toLowerCase(Locale.US);

                        if (!orderId.isEmpty() && isActiveStatus(status)) {
                            // Utamakan order yang sama dengan cache lokal. Bila server hanya
                            // mengirim satu order aktif terbaru, gunakan order tersebut.
                            if (savedOrderId.equalsIgnoreCase(orderId) || activeOrder == null) {
                                activeOrder = new ActiveOrder(
                                        orderId,
                                        status,
                                        firstNonEmpty(
                                                order.optString("source", ""),
                                                order.optString("order_source", ""),
                                                order.optString("table", ""),
                                                sp.getString("active_order_source", "orders"),
                                                "orders"
                                        ),
                                        normalizeDriverType(firstNonEmpty(
                                                order.optString("driver_type", ""),
                                                order.optString("vehicle_type", ""),
                                                sp.getString("active_driver_type", "motor")
                                        ))
                                );
                            }
                            if (savedOrderId.equalsIgnoreCase(orderId)) break;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Saat status tidak dapat diverifikasi, jangan memaksa customer masuk trip.
                activeOrder = null;
            }

            ActiveOrder result = activeOrder;
            MAIN.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                if (result == null) {
                    clearActiveOrder(sp);
                    callback.onResult(false);
                    return;
                }

                sp.edit()
                        .putString("active_order_id", result.orderId)
                        .putString("active_order_status", result.status)
                        .putString("active_order_source", result.source)
                        .putString("active_driver_type", result.driverType)
                        .apply();

                Intent intent = new Intent(activity, CustomerTripActivity.class);
                intent.putExtra("order_id", result.orderId);
                intent.putExtra("active_order_id", result.orderId);
                intent.putExtra("order_source", result.source);
                intent.putExtra("active_driver_type", result.driverType);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(intent);
                activity.finish();
                callback.onResult(true);
            });
        });
    }

    private static boolean isActiveStatus(String status) {
        String s = clean(status).toLowerCase(Locale.US);
        if (s.isEmpty()) return true;
        return !(s.equals("finished")
                || s.equals("finish")
                || s.equals("completed")
                || s.equals("complete")
                || s.equals("done")
                || s.equals("selesai")
                || s.equals("cancelled")
                || s.equals("canceled")
                || s.equals("cancel")
                || s.equals("dibatalkan")
                || s.equals("rejected")
                || s.equals("expired"));
    }

    private static void clearActiveOrder(SharedPreferences sp) {
        sp.edit()
                .remove("active_order_id")
                .remove("active_order_status")
                .remove("active_order_source")
                .remove("active_driver_type")
                .remove("active_order_type")
                .remove("active_service_name")
                .remove("active_order_price")
                .remove("order_id")
                .remove("order_status")
                .remove("pickup_lat")
                .remove("pickup_lng")
                .remove("delivery_lat")
                .remove("delivery_lng")
                .apply();
    }

    private static JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = read(stream).trim();
            return body.isEmpty() ? new JSONObject() : new JSONObject(body);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }

    private static String normalizeDriverType(String value) {
        String type = clean(value).toLowerCase(Locale.US);
        return type.equals("car") || type.equals("mobil") ? "car" : "motor";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isEmpty()) return cleaned;
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ActiveOrder {
        final String orderId;
        final String status;
        final String source;
        final String driverType;

        ActiveOrder(String orderId, String status, String source, String driverType) {
            this.orderId = orderId;
            this.status = status;
            this.source = source;
            this.driverType = driverType;
        }
    }
}
