package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RepeatFoodOrderActivity extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/";

    private static final String CREATE_FOOD_URL =
            BASE_URL + "server/create_food_order.php";

    private RepeatOrderData data;

    private LinearLayout menuBox;
    private TextView restaurantTitle;
    private TextView totalText;
    private ProgressBar loading;

    private final Map<Integer, JSONObject> menuMap =
            new LinkedHashMap<>();

    private final Map<Integer, Integer> quantities =
            new LinkedHashMap<>();

    private int userId;
    private String paymentMethod = "cash";
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        data = RepeatOrderData.fromIntent(
                getIntent()
        );

        loadSession();
        setContentView(buildScreen());
        loadRestaurantMenus();
    }

    private void loadSession() {
        try {
            SessionManager session =
                    new SessionManager(this);

            userId = Integer.parseInt(
                    first(
                            session.getId(),
                            session.getUserId(),
                            "0"
                    )
            );
        } catch (Exception ignored) {
            userId = 0;
        }
    }

    private ScrollView buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(
                Color.parseColor("#F6F9FE")
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(15), dp(15), dp(28));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = RepeatOrderUi.text(
                this,
                "‹",
                34,
                "#0B3A78",
                true
        );

        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());

        header.addView(
                back,
                new LinearLayout.LayoutParams(dp(42), dp(42))
        );

        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );

        titleBox.addView(
                RepeatOrderUi.text(
                        this,
                        "Pesan Lagi TransFood",
                        21,
                        "#0B3A78",
                        true
                )
        );

        titleBox.addView(
                RepeatOrderUi.text(
                        this,
                        "Menu restoran lama dimuat otomatis",
                        11,
                        "#64748B",
                        false
                )
        );

        header.addView(
                titleBox,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        root.addView(header);
        gap(root, 13);

        LinearLayout restaurantCard =
                new LinearLayout(this);

        restaurantCard.setOrientation(
                LinearLayout.VERTICAL
        );

        restaurantCard.setPadding(
                dp(14),
                dp(13),
                dp(14),
                dp(13)
        );

        restaurantCard.setBackground(
                RepeatOrderUi.gradient(
                        this,
                        "#0868F5",
                        "#23A7FF",
                        18
                )
        );

        restaurantTitle = RepeatOrderUi.text(
                this,
                first(
                        data.restaurantName,
                        "Restoran"
                ),
                18,
                "#FFFFFF",
                true
        );

        restaurantCard.addView(
                restaurantTitle
        );

        restaurantCard.addView(
                RepeatOrderUi.text(
                        this,
                        "Harga dan ketersediaan menu diperiksa ulang dari database.",
                        10,
                        "#EAF5FF",
                        false
                )
        );

        root.addView(restaurantCard);
        gap(root, 12);

        menuBox = new LinearLayout(this);
        menuBox.setOrientation(
                LinearLayout.VERTICAL
        );

        root.addView(menuBox);

        totalText = RepeatOrderUi.text(
                this,
                "Total makanan: Rp0",
                15,
                "#0B3A78",
                true
        );

        totalText.setGravity(Gravity.END);

        LinearLayout.LayoutParams totalLp =
                new LinearLayout.LayoutParams(-1, -2);

        totalLp.setMargins(0, dp(10), 0, dp(10));
        root.addView(totalText, totalLp);

        LinearLayout paymentRow =
                new LinearLayout(this);

        paymentRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        Button cash = RepeatOrderUi.outline(
                this,
                "Tunai",
                12
        );

        Button balance = RepeatOrderUi.outline(
                this,
                "Transiva Pay",
                12
        );

        cash.setOnClickListener(v -> {
            paymentMethod = "cash";
            toast("Pembayaran: Tunai");
        });

        balance.setOnClickListener(v -> {
            paymentMethod = "balance";
            toast("Pembayaran: Transiva Pay");
        });

        paymentRow.addView(
                cash,
                new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        LinearLayout.LayoutParams balanceLp =
                new LinearLayout.LayoutParams(0, dp(44), 1);

        balanceLp.setMargins(dp(8), 0, 0, 0);
        paymentRow.addView(balance, balanceLp);

        root.addView(paymentRow);

        Button order = RepeatOrderUi.primary(
                this,
                "Konfirmasi Pesan Makanan",
                13
        );

        order.setOnClickListener(
                view -> confirmFoodOrder()
        );

        LinearLayout.LayoutParams orderLp =
                new LinearLayout.LayoutParams(-1, dp(50));

        orderLp.setMargins(0, dp(10), 0, 0);
        root.addView(order, orderLp);

        Button openRestaurant =
                RepeatOrderUi.outline(
                        this,
                        "Buka Halaman TransFood",
                        13
                );

        openRestaurant.setOnClickListener(
                view -> {
                    startActivity(
                            new Intent(
                                    this,
                                    TransFoodActivity.class
                            )
                    );
                }
        );

        LinearLayout.LayoutParams openLp =
                new LinearLayout.LayoutParams(-1, dp(48));

        openLp.setMargins(0, dp(8), 0, 0);
        root.addView(openRestaurant, openLp);

        loading = new ProgressBar(this);

        LinearLayout.LayoutParams loadingLp =
                new LinearLayout.LayoutParams(dp(42), dp(42));

        loadingLp.gravity = Gravity.CENTER;
        loadingLp.setMargins(0, dp(13), 0, 0);
        root.addView(loading, loadingLp);

        return scroll;
    }

    private void loadRestaurantMenus() {
        if (data.restaurantId <= 0) {
            loading.setVisibility(
                    ProgressBar.GONE
            );

            showFatal(
                    "ID restoran order lama tidak tersedia."
            );

            return;
        }

        new Thread(() -> {
            try {
                JSONObject response =
                        RepeatOrderApi.get(
                                BASE_URL
                                        + "server/get_food_menus.php?restaurant_id="
                                        + data.restaurantId
                                        + "&_="
                                        + System.currentTimeMillis()
                        );

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Restoran tidak ditemukan"
                            )
                    );
                }

                JSONObject restaurant =
                        response.optJSONObject(
                                "restaurant"
                        );

                JSONArray menus =
                        response.optJSONArray(
                                "menus"
                        );

                runOnUiThread(() -> {
                    loading.setVisibility(
                            ProgressBar.GONE
                    );

                    if (restaurant != null) {
                        restaurantTitle.setText(
                                restaurant.optString(
                                        "name",
                                        data.restaurantName
                                )
                        );
                    }

                    restoreMenus(menus);
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    loading.setVisibility(
                            ProgressBar.GONE
                    );

                    showFatal(
                            "Gagal memuat restoran: "
                                    + error.getMessage()
                    );
                });
            }
        }).start();
    }

    private void restoreMenus(JSONArray menus) {
        menuMap.clear();
        quantities.clear();
        menuBox.removeAllViews();

        if (menus != null) {
            for (int i = 0; i < menus.length(); i++) {
                JSONObject menu =
                        menus.optJSONObject(i);

                if (menu != null) {
                    menuMap.put(
                            menu.optInt("id", 0),
                            menu
                    );
                }
            }
        }

        int restored = 0;
        int unavailable = 0;

        for (
                int i = 0;
                i < data.foodItems.length();
                i++
        ) {
            JSONObject old =
                    data.foodItems.optJSONObject(i);

            if (old == null) {
                continue;
            }

            int menuId = old.optInt(
                    "menu_id",
                    old.optInt("id", 0)
            );

            int qty = Math.max(
                    1,
                    old.optInt("qty", 1)
            );

            JSONObject current =
                    menuMap.get(menuId);

            if (
                    current == null
                            || current.optInt(
                            "is_active",
                            0
                    ) != 1
            ) {
                addUnavailableCard(old);
                unavailable++;
                continue;
            }

            quantities.put(menuId, qty);
            addMenuCard(current);
            restored++;
        }

        if (restored == 0) {
            menuBox.addView(
                    statusCard(
                            "Tidak ada menu lama yang masih aktif."
                    )
            );
        }

        updateTotal();

        if (unavailable > 0) {
            toast(
                    unavailable
                            + " menu lama sudah tidak tersedia"
            );
        }
    }

    private void addMenuCard(
            JSONObject menu
    ) {
        int menuId = menu.optInt("id", 0);

        LinearLayout card =
                new LinearLayout(this);

        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(10), dp(11), dp(10));
        card.setBackground(
                RepeatOrderUi.roundStroke(
                        this,
                        "#FFFFFF",
                        "#E0EAF5",
                        16,
                        1
                )
        );

        ImageView image = new ImageView(this);
        image.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        RemoteImageLoader.loadCenterCrop(
                image,
                absoluteUrl(
                        menu.optString(
                                "image",
                                ""
                        )
                ),
                drawable("ic_service_food")
        );

        card.addView(
                image,
                new LinearLayout.LayoutParams(dp(66), dp(66))
        );

        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.VERTICAL
        );

        info.setPadding(dp(10), 0, dp(8), 0);

        info.addView(
                RepeatOrderUi.text(
                        this,
                        menu.optString(
                                "name",
                                "Menu"
                        ),
                        13,
                        "#0B3A78",
                        true
                )
        );

        info.addView(
                RepeatOrderUi.text(
                        this,
                        rupiah(
                                menu.optDouble(
                                        "price",
                                        0
                                )
                        ),
                        11,
                        "#0B7CFF",
                        true
                )
        );

        card.addView(
                info,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        LinearLayout quantity =
                new LinearLayout(this);

        quantity.setGravity(Gravity.CENTER_VERTICAL);

        Button minus = miniButton("−");
        TextView count = RepeatOrderUi.text(
                this,
                String.valueOf(
                        quantities.getOrDefault(
                                menuId,
                                1
                        )
                ),
                13,
                "#0F172A",
                true
        );

        count.setGravity(Gravity.CENTER);

        Button plus = miniButton("+");

        minus.setOnClickListener(v -> {
            int value = Math.max(
                    0,
                    quantities.getOrDefault(
                            menuId,
                            1
                    ) - 1
            );

            quantities.put(menuId, value);
            count.setText(String.valueOf(value));
            updateTotal();
        });

        plus.setOnClickListener(v -> {
            int value =
                    quantities.getOrDefault(
                            menuId,
                            0
                    ) + 1;

            quantities.put(menuId, value);
            count.setText(String.valueOf(value));
            updateTotal();
        });

        quantity.addView(
                minus,
                new LinearLayout.LayoutParams(dp(35), dp(35))
        );

        quantity.addView(
                count,
                new LinearLayout.LayoutParams(dp(38), dp(35))
        );

        quantity.addView(
                plus,
                new LinearLayout.LayoutParams(dp(35), dp(35))
        );

        card.addView(quantity);

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(-1, -2);

        cardLp.setMargins(0, 0, 0, dp(8));
        menuBox.addView(card, cardLp);
    }

    private void addUnavailableCard(
            JSONObject old
    ) {
        TextView card = RepeatOrderUi.text(
                this,
                first(
                        old.optString("name"),
                        "Menu lama"
                )
                        + "\nSudah tidak tersedia",
                11,
                "#B45309",
                true
        );

        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(
                RepeatOrderUi.roundStroke(
                        this,
                        "#FFF8E8",
                        "#F8D99A",
                        14,
                        1
                )
        );

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(-1, -2);

        cardLp.setMargins(0, 0, 0, dp(8));
        menuBox.addView(card, cardLp);
    }

    private TextView statusCard(
            String value
    ) {
        TextView card = RepeatOrderUi.text(
                this,
                value,
                12,
                "#64748B",
                false
        );

        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(20), dp(14), dp(20));
        card.setBackground(
                RepeatOrderUi.roundStroke(
                        this,
                        "#FFFFFF",
                        "#E0EAF5",
                        16,
                        1
                )
        );

        return card;
    }

    private void updateTotal() {
        double total = 0;

        for (Map.Entry<Integer, Integer> entry
                : quantities.entrySet()) {
            int qty = entry.getValue();

            if (qty <= 0) {
                continue;
            }

            JSONObject menu =
                    menuMap.get(entry.getKey());

            if (menu != null) {
                total +=
                        menu.optDouble("price", 0)
                                * qty;
            }
        }

        totalText.setText(
                "Total makanan: "
                        + rupiah(total)
                        + "\nOngkir dihitung ulang server"
        );
    }

    private void confirmFoodOrder() {
        JSONArray items = selectedItems();

        if (items.length() == 0) {
            toast("Pilih minimal satu menu");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Pesan Lagi")
                .setMessage(
                        "Harga menu dan ongkir akan dihitung ulang dari database."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton(
                        "Buat Order",
                        (dialog, which) ->
                                submitFoodOrder(items)
                )
                .show();
    }

    private JSONArray selectedItems() {
        JSONArray result = new JSONArray();

        for (Map.Entry<Integer, Integer> entry
                : quantities.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }

            JSONObject item = new JSONObject();

            try {
                item.put(
                        "id",
                        entry.getKey()
                );

                item.put(
                        "menu_id",
                        entry.getKey()
                );

                item.put(
                        "qty",
                        entry.getValue()
                );

                result.put(item);
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private void submitFoodOrder(
            JSONArray items
    ) {
        if (submitting) {
            return;
        }

        if (userId <= 0) {
            toast("Sesi pengguna tidak ditemukan");
            return;
        }

        submitting = true;
        loading.setVisibility(ProgressBar.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject payload =
                        new JSONObject();

                payload.put("user_id", userId);

                payload.put(
                        "restaurant_id",
                        data.restaurantId
                );

                payload.put(
                        "delivery_mode",
                        data.deliveryMode
                );

                payload.put(
                        "payment_method",
                        paymentMethod
                );

                payload.put("items", items);

                JSONObject response =
                        RepeatOrderApi.post(
                                CREATE_FOOD_URL,
                                payload
                        );

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Order gagal"
                            )
                    );
                }

                runOnUiThread(() -> {
                    submitting = false;
                    loading.setVisibility(
                            ProgressBar.GONE
                    );

                    new AlertDialog.Builder(this)
                            .setTitle("Order Berhasil")
                            .setMessage(
                                    response.optString(
                                            "message",
                                            "Pesanan makanan berhasil dibuat"
                                    )
                            )
                            .setCancelable(false)
                            .setPositiveButton(
                                    "Lihat Aktivitas",
                                    (dialog, which) -> {
                                        Intent intent =
                                                new Intent(
                                                        this,
                                                        CustomerHistoryActivity.class
                                                );

                                        intent.addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        );

                                        startActivity(intent);
                                        finish();
                                    }
                            )
                            .show();
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    submitting = false;
                    loading.setVisibility(
                            ProgressBar.GONE
                    );

                    toast(
                            "Order gagal: "
                                    + error.getMessage()
                    );
                });
            }
        }).start();
    }

    private void showFatal(String message) {
        menuBox.removeAllViews();
        menuBox.addView(statusCard(message));
    }

    private Button miniButton(String value) {
        Button button = RepeatOrderUi.outline(
                this,
                value,
                10
        );

        button.setTextSize(16);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private String absoluteUrl(String value) {
        value = value == null
                ? ""
                : value.trim();

        if (
                value.startsWith("http://")
                        || value.startsWith("https://")
        ) {
            return value;
        }

        if (value.startsWith("/")) {
            return "https://transiva.my.id"
                    + value;
        }

        return "https://transiva.my.id/"
                + value;
    }

    private String rupiah(double value) {
        return NumberFormat
                .getCurrencyInstance(
                        new Locale("id", "ID")
                )
                .format(value);
    }

    private int drawable(String name) {
        return getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );
    }

    private void gap(
            LinearLayout parent,
            int value
    ) {
        parent.addView(
                new TextView(this),
                new LinearLayout.LayoutParams(1, dp(value))
        );
    }

    private int dp(int value) {
        return RepeatOrderUi.dp(this, value);
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private String first(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
            ) {
                return value.trim();
            }
        }

        return "";
    }
}
