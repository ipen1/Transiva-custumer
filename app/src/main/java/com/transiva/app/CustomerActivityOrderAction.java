package com.transiva.app;

import android.app.Activity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CustomerActivityOrderAction {

    private static final String CANCEL_URL =
            "https://transiva.my.id/server/cancel_order.php";

    private static final int TIMEOUT_MS = 25000;

    private CustomerActivityOrderAction() {
    }

    public interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }

    public static void cancel(
            Activity activity,
            JSONObject order,
            int userId,
            String username,
            Callback callback
    ) {
        cancel(activity, order, userId, username, "", callback);
    }

    static void cancel(Activity activity, JSONObject order, int userId, String username,
                       String expectedDriver, Callback callback) {
        final CustomerSessionGuard owner = CustomerSessionGuard.capture(activity);
        TransivaNetworkExecutor.execute(() -> {
            try {
                if (!Integer.toString(userId).equals(owner.account) || !owner.matches(activity)) {
                    throw new IllegalStateException("Sesi login berubah. Silakan login kembali.");
                }
                JSONObject payload =
                        new JSONObject();

                payload.put(
                        "order_id",
                        first(
                                order.optString(
                                        "order_id"
                                ),
                                order.optString("id")
                        )
                );

                payload.put(
                        "user_id",
                        userId
                );

                payload.put(
                        "username",
                        username == null
                                ? ""
                                : username.trim()
                );

                payload.put(
                        "table",
                        sourceTable(order)
                );

                if (!expectedDriver.isEmpty()) {
                    payload.put("cancel_reason", "driver_requested");
                    payload.put("expected_driver", expectedDriver);
                }

                String token = new SessionManager(activity).getToken();
                if (token == null || token.trim().isEmpty()) {
                    throw new IllegalStateException(
                            "Sesi login tidak ditemukan. Silakan login kembali."
                    );
                }

                JSONObject response =
                        post(activity, payload, token.trim());

                boolean success =
                        response.optBoolean(
                                "success",
                                false
                        );

                String message =
                        response.optString(
                                "message",
                                success
                                        ? "Order berhasil dibatalkan"
                                        : "Order gagal dibatalkan"
                        );

                activity.runOnUiThread(() -> {
                    if (!owner.matches(activity) || activity.isFinishing() || activity.isDestroyed()) return;
                    if (success) {
                        recordConfirmedCancellation(activity, Integer.toString(userId), order);
                        callback.onSuccess(message);
                    } else {
                        callback.onError(message);
                    }
                });

            } catch (Exception error) {
                activity.runOnUiThread(
                        () -> callback.onError(
                                "Pembatalan gagal: "
                                        + error.getMessage()
                        )
                );
            }
        }, () -> activity.runOnUiThread(() -> callback.onError("Sistem sedang sibuk. Coba lagi.")));
    }

    static void recordConfirmedCancellation(android.content.Context context, String account, JSONObject order) {
        if (!account.equals(CustomerSessionGuard.capture(context).account)) return;
        String id = first(order.optString("order_id"), order.optString("id_order"), order.optString("id"));
        if (id.isEmpty()) return;
        com.transiva.app.customer.data.DashboardStateCache.rememberCancellation(context, account, order);
        android.content.SharedPreferences prefs = context.getSharedPreferences("transiva", Activity.MODE_PRIVATE);
        if (id.equals(prefs.getString("active_order_id", ""))) {
            prefs.edit().remove("active_order_id").remove("active_order_status").remove("active_order_source")
                    .remove("active_driver_type").remove("active_order_type").remove("active_service_name")
                    .remove("active_order_price").remove("active_order").remove("order_id").remove("order_status")
                    .remove("pickup_lat").remove("pickup_lng").remove("delivery_lat").remove("delivery_lng").apply();
            CustomerRedispatchService.stop(context);
        }
        UnifiedLiveOrderCenter.onCancellationRecorded();
    }

    static boolean applyConfirmedStatus(android.content.Context context, String account, JSONObject order) {
        String status = CustomerOrderState.normalize(order.optString("status"));
        if (CustomerOrderState.isEnded(status)
                || !com.transiva.app.customer.data.DashboardStateCache.wasCanceled(context, account, order)) return false;
        try {
            order.put("status", CustomerOrderState.CANCELED);
            return true;
        } catch (org.json.JSONException ignored) { return false; }
    }

    private static String sourceTable(
            JSONObject order
    ) {
        String table = first(
                order.optString(
                        "_transiva_table"
                ),
                order.optString("source"),
                ""
        ).toLowerCase();

        String type = first(
                order.optString(
                        "order_type"
                ),
                order.optString(
                        "service_type"
                ),
                ""
        ).toLowerCase();

        if (
                table.contains("pickup")
                        || type.contains("pickup")
        ) {
            return "pickup_orders";
        }

        return "orders";
    }

    private static JSONObject post(Activity activity, JSONObject payload, String token) throws Exception {
        return TransivaHttpRepository.postJson(activity, CANCEL_URL, payload, TIMEOUT_MS);
    }

    private static String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
                            && !"null".equalsIgnoreCase(
                                    value.trim()
                            )
            ) {
                return value.trim();
            }
        }

        return "";
    }
}
