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
        new Thread(() -> {
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

                JSONObject response =
                        post(payload);

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
        }).start();
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

    private static JSONObject post(
            JSONObject payload
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(
                                    CANCEL_URL
                            ).openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(
                    TIMEOUT_MS
            );

            connection.setReadTimeout(
                    TIMEOUT_MS
            );

            connection.setDoOutput(true);
            connection.setUseCaches(false);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            try (
                    OutputStream output =
                            connection.getOutputStream()
            ) {
                output.write(
                        payload
                                .toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );
            }

            int status =
                    connection.getResponseCode();

            InputStream stream =
                    status >= 200 && status < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (stream == null) {
                throw new IllegalStateException(
                        "Respons server kosong"
                );
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    stream,
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder raw =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }

            reader.close();

            JSONObject response =
                    new JSONObject(
                            raw.length() == 0
                                    ? "{}"
                                    : raw.toString()
                    );

            if (status < 200 || status >= 400) {
                throw new IllegalStateException(
                        response.optString(
                                "message",
                                "HTTP " + status
                        )
                );
            }

            return response;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
