package com.transiva.app.customer.data;

import android.content.Context;
import android.net.Uri;

import com.transiva.app.CustomerApiClient;
import com.transiva.app.ApiConfig;
import com.transiva.app.CustomerResourceConfig;
import com.transiva.app.TransivaHttpRepository;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.Promo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

public final class CustomerDashboardRepositoryImpl
        implements CustomerDashboardRepository {

    private static final String BASE_URL = ApiConfig.ROOT;
    private static final int TIMEOUT = 15000;

    private final Context context;

    public CustomerDashboardRepositoryImpl(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context tidak boleh null");
        }
        this.context = context.getApplicationContext();
    }

    @Override
    public DashboardState load(
            String username,
            int userId
    ) throws Exception {
        double balance = loadBalance(username);
        JSONObject activeOrder = loadActiveOrderJson(username, userId);
        String order = formatActiveOrder(activeOrder);
        JSONObject loyalty = loadOptional(BASE_URL + "server/customer_loyalty.php");
        JSONObject referral = loadOptional(BASE_URL + "server/customer_referral.php");
        JSONObject bestOffer = loadOptional(BASE_URL + "server/customer_best_offer.php");
        List<Promo> promos = loadPromos();

        return new DashboardState(
                balance, order, activeOrder, loyalty, referral, bestOffer, promos
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

    private JSONObject loadActiveOrderJson(String username, int userId) {
        try {
            JSONObject json = get(BASE_URL + "server/customer_get_active_orders.php?user_id="
                    + userId + "&username=" + Uri.encode(username) + "&_=" + System.currentTimeMillis());
            JSONArray orders = json.optJSONArray("orders");
            if (!json.optBoolean("success", false) || orders == null || orders.length() == 0) return null;
            return orders.optJSONObject(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatActiveOrder(JSONObject order) {
        if (order == null) return "Belum ada pesanan aktif";
        String service = first(order.optString("service_name"), order.optString("order_type"), "Pesanan");
        String status = com.transiva.app.OrderStatusPresentation.label(first(order.optString("status"), "pending"), service);
        String driver = first(order.optString("driver"), order.optString("driver_username"), "");
        return service + " • " + status + (driver.isEmpty() ? "" : "\nDriver: " + driver);
    }

    private JSONObject loadOptional(String endpoint) {
        try {
            JSONObject json = get(endpoint + (endpoint.contains("?") ? "&" : "?") + "_=" + System.currentTimeMillis());
            return json.optBoolean("success", false) ? json : null;
        } catch (Exception ignored) {
            return null;
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
            result.clear();
        }

        // Signed data-only resource snapshot may provide emergency/campaign banners.
        // It is used only when the live promo API has no usable rows.
        if (result.isEmpty()) {
            JSONArray bundled = CustomerResourceConfig.banners(context);
            for (int i = 0; i < bundled.length() && result.size() < 2; i++) {
                JSONObject item = bundled.optJSONObject(i);
                if (item == null) continue;
                String title = item.optString("title", "").trim();
                if (title.isEmpty()) continue;
                result.add(new Promo(
                        -1000 - i,
                        title,
                        item.optString("description", "").trim(),
                        item.optString("code", "").trim(),
                        item.optString("image_url", "").trim(),
                        safeColor(item.optString("theme_start", "#0759E8"), "#0759E8"),
                        safeColor(item.optString("theme_end", "#18B5FF"), "#18B5FF")
                ));
            }
        }

        return result;
    }

    private JSONObject get(String endpoint) throws Exception {
        return TransivaHttpRepository.getJson(context, endpoint, TIMEOUT);
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
