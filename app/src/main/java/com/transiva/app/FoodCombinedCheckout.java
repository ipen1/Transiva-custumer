package com.transiva.app;

import android.app.Activity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class FoodCombinedCheckout {
    private static final String ORDER_URL = ApiConfig.server("create_food_order.php");
    private static final int TIMEOUT = 20000;

    static final class StoreQuote {
        final FoodCartStore.Cart cart;
        final JSONObject payload;
        final JSONObject quote;

        StoreQuote(FoodCartStore.Cart cart, JSONObject payload, JSONObject quote) {
            this.cart = cart;
            this.payload = payload;
            this.quote = quote;
        }
    }

    static final class Plan {
        final List<StoreQuote> stores = new ArrayList<>();
        String address = "";
        String destination = "";
        long food, delivery, total;
    }

    static final class Result {
        final List<String> orderIds = new ArrayList<>();
        long total;
        String problem = "";
        boolean recoveredPrevious;
    }

    private FoodCombinedCheckout() {}

    static JSONArray items(List<FoodCartStore.CartItem> cart) throws JSONException {
        JSONArray items = new JSONArray();
        for (FoodCartStore.CartItem line : cart) {
            items.put(new JSONObject().put("id", line.id).put("qty", line.qty)
                    .put("_cart_line_id", line.lineId).put("selected_options",
                            line.selectedOptions == null ? new JSONArray() : line.selectedOptions));
        }
        return items;
    }

    static JSONObject recover(Activity activity) throws Exception {
        return ReliableOrderClient.recoverPendingFood(activity, ORDER_URL, TIMEOUT);
    }

    static Plan quote(Activity activity, int user, List<FoodCartStore.Cart> carts, String payment) throws Exception {
        Plan plan = new Plan();
        for (FoodCartStore.Cart cart : carts) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            JSONObject payload = new JSONObject().put("user_id", user).put("restaurant_id", cart.restaurantId)
                    .put("delivery_mode", "standard").put("payment_method", payment)
                    .put("combined_checkout", true).put("items", items(cart.items));
            JSONObject quote;
            try {
                quote = FoodCheckoutConfirmation.quote(activity, ORDER_URL, payload, TIMEOUT);
            } catch (Exception error) {
                throw new IllegalStateException(cart.name + " · " + errorMessage(error, "Rincian pesanan belum dapat dimuat."), error);
            }
            JSONObject destination = quote.optJSONObject("destination");
            if (destination == null || destination.optString("address").isEmpty()
                    || destination.optString("fingerprint").isEmpty()) {
                throw new IllegalStateException("Checkout semua toko memerlukan pembaruan server TransFood.");
            }
            String fingerprint = destination.getString("fingerprint");
            if (!plan.destination.isEmpty() && !plan.destination.equals(fingerprint)) {
                throw new IllegalStateException("Alamat pengantaran berubah. Hitung ulang ongkir.");
            }
            plan.address = destination.getString("address");
            plan.destination = fingerprint;
            payload.put("expected_quote", quote.getString("fingerprint"))
                    .put("expected_destination", plan.destination);
            plan.food += quote.getLong("food_total");
            plan.delivery += quote.getLong("delivery_fee");
            plan.total += quote.getLong("total");
            plan.stores.add(new StoreQuote(cart, payload, quote));
        }
        return plan;
    }

    static Result submit(Activity activity, Plan plan) {
        Result result = new Result();
        if (plan.stores.isEmpty()) return result;
        try {
            JSONObject previous = recover(activity);
            if (previous != null) {
                return recovered(previous);
            }
            if ("balance".equals(plan.stores.get(0).payload.optString("payment_method"))) {
                JSONObject balance = TransivaHttpRepository.getJsonOnce(activity, ApiConfig.server("getBalance.php"), TIMEOUT);
                if (!balance.optBoolean("success") || !balance.has("balance")) {
                    result.problem = "Saldo Transiva Pay belum bisa diperiksa. Coba lagi.";
                    return result;
                }
                if (!(balance.optDouble("balance", -1) >= plan.total)) {
                    result.problem = "Saldo Transiva Pay kurang dari total checkout. Pilih Tunai atau isi saldo.";
                    return result;
                }
            }
        } catch (Exception error) {
            result.problem = errorMessage(error, "Pemeriksaan sebelum checkout gagal. Coba lagi.");
            return result;
        }
        for (StoreQuote store : plan.stores) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                JSONObject receipt = ReliableOrderClient.create(activity, ORDER_URL, store.payload, TIMEOUT);
                if (receipt.optBoolean("recovered_previous_attempt")) {
                    result.orderIds.add(receipt.getString("order_id"));
                    result.recoveredPrevious = true;
                    break;
                }
                if (!receipt.optBoolean("success")) {
                    result.problem = store.cart.name + " · " + receipt.optString("message", "Pesanan belum dibuat.");
                    break;
                }
                if (receipt.optInt("restaurant_id") != store.cart.restaurantId) {
                    result.problem = "Bukti pesanan perlu diperiksa di Aktivitas sebelum melanjutkan.";
                    break;
                }
                result.orderIds.add(receipt.getString("order_id"));
                result.total += store.quote.optLong("total");
            } catch (Exception error) {
                result.problem = store.cart.name + " · " + (error instanceof ReliableOrderClient.FoodCartChangedException
                        ? error.getMessage() : ReliableOrderClient.failureMessage(error));
                break;
            }
        }
        return result;
    }

    static Result recovered(JSONObject receipt) {
        Result result = new Result();
        result.recoveredPrevious = true;
        result.orderIds.add(receipt.optString("order_id"));
        return result;
    }

    private static String errorMessage(Exception error, String fallback) {
        String message = error.getMessage();
        return (error instanceof IllegalStateException || error instanceof ReliableOrderClient.PendingAttemptException
                || error instanceof TransivaHttpRepository.ServerResponseException)
                && message != null && !message.isEmpty() ? message : fallback;
    }
}
