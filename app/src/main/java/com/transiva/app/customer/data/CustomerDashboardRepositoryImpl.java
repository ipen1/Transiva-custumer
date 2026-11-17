package com.transiva.app.customer.data;

import android.content.Context;
import android.net.Uri;

import com.transiva.app.ApiConfig;
import com.transiva.app.CustomerResourceConfig;
import com.transiva.app.TransivaHttpRepository;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.DashboardState.Section;
import com.transiva.app.customer.domain.Promo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CustomerDashboardRepositoryImpl
        implements CustomerDashboardRepository {

    private static final String BASE_URL = ApiConfig.ROOT;
    private static final String ACTIVE_ORDERS = BASE_URL + "server/customer_get_active_orders.php";

    private final Context context;

    public CustomerDashboardRepositoryImpl(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public DashboardState load(Section section, int userId, JSONObject order,
                               int budgetMs) throws Exception {
        switch (section) {
            case BALANCE:
                JSONObject balance = get(BASE_URL + "server/getBalance.php", budgetMs);
                return new DashboardState(balance.getDouble("balance"), null, null, null, null, null, null);
            case ORDER:
                return loadActiveOrders(userId, budgetMs);
            case ORDER_DETAILS:
                return loadOrderDetails(order, budgetMs);
            case PROMOS:
                return new DashboardState(0, null, null, null, null, null, loadPromos(budgetMs));
            case LOYALTY:
                return new DashboardState(0, null, null,
                        get(BASE_URL + "server/customer_loyalty.php", budgetMs), null, null, null);
            case REFERRAL:
                return new DashboardState(0, null, null, null,
                        get(BASE_URL + "server/customer_referral.php", budgetMs), null, null);
            case BEST_OFFER:
                return new DashboardState(0, null, null, null, null,
                        get(BASE_URL + "server/customer_best_offer.php", budgetMs), null);
            default:
                throw new IllegalArgumentException(section.name());
        }
    }

    private DashboardState loadActiveOrders(int userId, int budgetMs) throws Exception {
        long started = System.currentTimeMillis();
        JSONObject result = get(ACTIVE_ORDERS + "?detail=summary", budgetMs);
        JSONArray orders = result.getJSONArray("orders");
        JSONObject first = null;
        JSONObject assigned = null;
        JSONArray eligible = new JSONArray();
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null || com.transiva.app.CustomerOrderState.isEnded(order.optString("status"))
                    || DashboardStateCache.wasCanceled(context, Integer.toString(userId), order)) continue;
            eligible.put(order);
            if (first == null) first = order;
            if (assigned == null && com.transiva.app.CustomerOrderState.hasDriver(order.optString("status"))) {
                assigned = order;
            }
        }
        com.transiva.app.UnifiedLiveOrderCenter.acceptDashboardSnapshot(context, eligible, started);
        return new DashboardState(0, formatActiveOrder(first), first, null, null, null, null,
                assigned == null ? first : assigned);
    }

    private DashboardState loadOrderDetails(JSONObject order, int budgetMs) throws Exception {
        String id = order.optString("order_id");
        String source = order.optString("source");
        String url = ACTIVE_ORDERS + "?detail=selected&order_id=" + Uri.encode(id)
                + "&source=" + Uri.encode(source);
        JSONArray orders = get(url, budgetMs).getJSONArray("orders");
        for (int i = 0; i < orders.length(); i++) {
            JSONObject current = orders.optJSONObject(i);
            if (current != null && id.equals(current.optString("order_id"))
                    && source.equals(current.optString("source"))) {
                return new DashboardState(0, formatActiveOrder(current), current, null, null, null, null);
            }
        }
        return new DashboardState(0, null, null, null, null, null, null);
    }

    private String formatActiveOrder(JSONObject order) {
        if (order == null) return "Belum ada pesanan aktif";
        String service = first(order.optString("service_name"), order.optString("order_type"), "Pesanan");
        String status = com.transiva.app.OrderStatusPresentation.label(first(order.optString("status"), "pending"), service);
        String driver = first(order.optString("driver"), order.optString("driver_username"), "");
        return service + " • " + status + (driver.isEmpty() ? "" : "\nDriver: " + driver);
    }

    private List<Promo> loadPromos(int budgetMs) throws Exception {
        List<Promo> result = new ArrayList<>();
        JSONObject json = get(BASE_URL + "server/customer_get_promos.php?role=customer", budgetMs);
        JSONArray promos = json.getJSONArray("promos");
        if (promos.length() == 0) return result;

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

    private JSONObject get(String endpoint, int budgetMs) throws Exception {
        JSONObject json = TransivaHttpRepository.getJsonWithin(context, endpoint, budgetMs);
        if (!json.optBoolean("success", false)) {
            throw new TransivaHttpRepository.ServerResponseException("Data beranda belum dapat dimuat.", 0, json);
        }
        return json;
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
