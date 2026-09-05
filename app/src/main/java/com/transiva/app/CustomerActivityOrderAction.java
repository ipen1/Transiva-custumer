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
        TransivaNetworkExecutor.execute(() -> {
            try {
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
                    if (success) {
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
        });
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
