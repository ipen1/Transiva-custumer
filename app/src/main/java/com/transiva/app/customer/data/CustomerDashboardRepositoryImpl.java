package com.transiva.app.customer.data;

import android.net.Uri;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.Promo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class CustomerDashboardRepositoryImpl
        implements CustomerDashboardRepository {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final int TIMEOUT = 15000;

    @Override
    public DashboardState load(
            String username,
            int userId
    ) throws Exception {
        double balance = loadBalance(username);
        String order = loadActiveOrder(username, userId);
        List<Promo> promos = loadPromos();

        return new DashboardState(
                balance,
                order,
                promos
        );
    }

    private double loadBalance(String username)
            throws Exception {
        JSONObject json = get(
                BASE_URL
                        + "server/getBalance.php?username="
                        + Uri.encode(username)
                        + "&_="
                        + System.currentTimeMillis()
        );

        return json.optBoolean("success", false)
                ? json.optDouble("balance", 0)
                : 0;
    }

    private String loadActiveOrder(
            String username,
            int userId
    ) {
        try {
            JSONObject json = get(
                    BASE_URL
                            + "server/customer_get_active_orders.php?user_id="
                            + userId
                            + "&username="
                            + Uri.encode(username)
                            + "&_="
                            + System.currentTimeMillis()
            );

            JSONArray orders = json.optJSONArray("orders");

            if (
                    !json.optBoolean("success", false)
                            || orders == null
                            || orders.length() == 0
            ) {
                return "Belum ada pesanan aktif";
            }

            JSONObject order = orders.optJSONObject(0);

            if (order == null) {
                return "Belum ada pesanan aktif";
            }

            String service = first(
                    order.optString("service_name"),
                    order.optString("order_type"),
                    "Pesanan"
            );

            String status = first(
                    order.optString("status"),
                    "pending"
            ).replace("_", " ");

            String driver = first(
                    order.optString("driver"),
                    order.optString("driver_username"),
                    ""
            );

            return service
                    + " • "
                    + status
                    + (
                    driver.isEmpty()
                            ? ""
                            : "\nDriver: " + driver
            );

        } catch (Exception ignored) {
            return "Status pesanan belum dapat diperbarui";
        }
    }

    /**
     * Promo hanya berasal dari database.
     *
     * Penting:
     * - Tidak ada fallback/dummy.
     * - Database kosong menghasilkan List kosong.
     * - Error jaringan juga menghasilkan List kosong agar promo lama
     *   tidak muncul kembali secara palsu.
     */
    private List<Promo> loadPromos() {
        List<Promo> result = new ArrayList<>();

        try {
            JSONObject json = get(
                    BASE_URL
                            + "server/customer_get_promos.php?role=customer&_="
                            + System.currentTimeMillis()
            );

            if (!json.optBoolean("success", false)) {
                return result;
            }

            JSONArray promos = json.optJSONArray("promos");

            if (promos == null || promos.length() == 0) {
                return result;
            }

            for (
                    int i = 0;
                    i < promos.length() && result.size() < 2;
                    i++
            ) {
                JSONObject item = promos.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                int id = item.optInt("id", 0);
                String title = item.optString("title", "").trim();
                String description =
                        item.optString("description", "").trim();

                // Abaikan data rusak/tidak lengkap.
                if (id <= 0 || title.isEmpty()) {
                    continue;
                }

                result.add(
                        new Promo(
                                id,
                                title,
                                description,
                                item.optString(
                                        "promo_code",
                                        ""
                                ).trim(),
                                item.optString(
                                        "image_url",
                                        ""
                                ).trim(),
                                safeColor(
                                        item.optString(
                                                "theme_start",
                                                "#0759E8"
                                        ),
                                        "#0759E8"
                                ),
                                safeColor(
                                        item.optString(
                                                "theme_end",
                                                "#18B5FF"
                                        ),
                                        "#18B5FF"
                                )
                        )
                );
            }

        } catch (Exception ignored) {
            // Tidak membuat promo dummy saat API gagal.
            result.clear();
        }

        return result;
    }

    private JSONObject get(String endpoint)
            throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(endpoint).openConnection();

            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            // Cegah Android/proxy memakai respons promo lama.
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache, no-store, must-revalidate"
            );
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Expires", "0");

            int code = connection.getResponseCode();

            InputStream stream =
                    code >= 200 && code < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (stream == null) {
                throw new IllegalStateException(
                        "Empty response"
                );
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    stream,
                                    "UTF-8"
                            )
                    );

            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            reader.close();

            if (code < 200 || code >= 400) {
                throw new IllegalStateException(
                        "HTTP " + code
                );
            }

            return new JSONObject(body.toString());

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String safeColor(
            String value,
            String fallback
    ) {
        if (value == null) {
            return fallback;
        }

        String color = value.trim();

        if (!color.matches("^#[0-9a-fA-F]{6}$")) {
            return fallback;
        }

        return color;
    }

    private String first(String... values) {
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
