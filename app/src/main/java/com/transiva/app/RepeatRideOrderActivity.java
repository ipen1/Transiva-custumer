package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class RepeatRideOrderActivity extends Activity {

    private static final String CREATE_ORDER_URL =
            "https://transiva.my.id/server/createOrder.php";

    private RepeatOrderData data;

    private EditText pickupInput;
    private EditText deliveryInput;
    private EditText noteInput;
    private TextView coordinateInfo;
    private ProgressBar loading;

    private int userId;
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

        if (!data.hasValidRideCoordinates()) {
            new AlertDialog.Builder(this)
                    .setTitle("Lokasi lama tidak lengkap")
                    .setMessage(
                            "Koordinat jemput atau tujuan order lama tidak tersedia. Gunakan halaman layanan utama untuk memilih lokasi kembali."
                    )
                    .setNegativeButton(
                            "Tutup",
                            (dialog, which) -> finish()
                    )
                    .setPositiveButton(
                            "Buka Layanan",
                            (dialog, which) -> openNormalService()
                    )
                    .show();
        }
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
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
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
                        data.isCar()
                                ? "Pesan Lagi TransCar"
                                : "Pesan Lagi TransRide",
                        21,
                        "#0B3A78",
                        true
                )
        );

        titleBox.addView(
                RepeatOrderUi.text(
                        this,
                        "Lokasi lama sudah diisi otomatis",
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
        gap(root, 14);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(
                RepeatOrderUi.roundStroke(
                        this,
                        "#FFFFFF",
                        "#DDE8F5",
                        19,
                        1
                )
        );
        card.setElevation(dp(2));
        root.addView(card);

        card.addView(label("Lokasi Jemput"));
        pickupInput = input(data.pickupAddress);
        card.addView(pickupInput);
        gap(card, 10);

        card.addView(label("Lokasi Tujuan"));
        deliveryInput = input(data.deliveryAddress);
        card.addView(deliveryInput);
        gap(card, 10);

        card.addView(label("Catatan Driver"));
        noteInput = input(data.note);
        noteInput.setHint("Catatan opsional");
        card.addView(noteInput);
        gap(card, 12);

        coordinateInfo = RepeatOrderUi.text(
                this,
                "Jemput: "
                        + data.pickupLat
                        + ", "
                        + data.pickupLng
                        + "\nTujuan: "
                        + data.deliveryLat
                        + ", "
                        + data.deliveryLng,
                10,
                "#7B8DA3",
                false
        );

        coordinateInfo.setPadding(
                dp(10),
                dp(9),
                dp(10),
                dp(9)
        );

        coordinateInfo.setBackground(
                RepeatOrderUi.round(
                        this,
                        "#F2F7FD",
                        12
                )
        );

        card.addView(coordinateInfo);
        gap(card, 14);

        Button order = RepeatOrderUi.primary(
                this,
                data.isCar()
                        ? "Konfirmasi Order Mobil"
                        : "Konfirmasi Order Motor",
                13
        );

        order.setOnClickListener(
                view -> confirmOrder()
        );

        card.addView(
                order,
                new LinearLayout.LayoutParams(-1, dp(50))
        );

        Button editMap = RepeatOrderUi.outline(
                this,
                "Pilih Lokasi Baru",
                13
        );

        editMap.setOnClickListener(
                view -> openNormalService()
        );

        LinearLayout.LayoutParams editLp =
                new LinearLayout.LayoutParams(-1, dp(48));

        editLp.setMargins(0, dp(8), 0, 0);
        card.addView(editMap, editLp);

        loading = new ProgressBar(this);
        loading.setVisibility(ProgressBar.GONE);

        LinearLayout.LayoutParams loadingLp =
                new LinearLayout.LayoutParams(dp(42), dp(42));

        loadingLp.gravity = Gravity.CENTER;
        loadingLp.setMargins(0, dp(15), 0, 0);
        root.addView(loading, loadingLp);

        return scroll;
    }

    private void confirmOrder() {
        if (submitting) {
            return;
        }

        if (userId <= 0) {
            toast("Sesi pengguna tidak ditemukan");
            return;
        }

        String pickupAddress =
                pickupInput.getText().toString().trim();

        String deliveryAddress =
                deliveryInput.getText().toString().trim();

        if (
                pickupAddress.isEmpty()
                        || deliveryAddress.isEmpty()
        ) {
            toast("Alamat jemput dan tujuan wajib diisi");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Pesan Lagi")
                .setMessage(
                        "Tarif akan dihitung ulang menggunakan aturan terbaru. Order tidak menggunakan harga lama."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton(
                        "Buat Order",
                        (dialog, which) ->
                                submitOrder(
                                        pickupAddress,
                                        deliveryAddress
                                )
                )
                .show();
    }

    private void submitOrder(
            String pickupAddress,
            String deliveryAddress
    ) {
        submitting = true;
        loading.setVisibility(ProgressBar.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject payload =
                        new JSONObject();

                payload.put("user_id", userId);
                payload.put(
                        "order_type",
                        data.isCar()
                                ? "Transcar"
                                : "Transbike"
                );

                payload.put(
                        "driver_type",
                        data.isCar()
                                ? "car"
                                : "bike"
                );

                payload.put(
                        "pickup",
                        point(
                                data.pickupLat,
                                data.pickupLng,
                                pickupAddress
                        )
                );

                payload.put(
                        "delivery",
                        point(
                                data.deliveryLat,
                                data.deliveryLng,
                                deliveryAddress
                        )
                );

                payload.put(
                        "userLocation",
                        point(
                                data.pickupLat,
                                data.pickupLng,
                                pickupAddress
                        )
                );

                payload.put(
                        "note",
                        noteInput
                                .getText()
                                .toString()
                                .trim()
                );

                JSONObject response =
                        RepeatOrderApi.post(
                                CREATE_ORDER_URL,
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
                                            "Order baru berhasil dibuat"
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

    private JSONObject point(
            double latitude,
            double longitude,
            String address
    ) throws Exception {
        JSONObject point = new JSONObject();
        point.put("latitude", latitude);
        point.put("longitude", longitude);
        point.put("address", address);
        return point;
    }

    private void openNormalService() {
        startActivity(
                new Intent(
                        this,
                        data.isCar()
                                ? PassengerCarActivity.class
                                : TransRideActivity.class
                )
        );
        finish();
    }

    private TextView label(String value) {
        TextView label = RepeatOrderUi.text(
                this,
                value,
                11,
                "#52647A",
                true
        );

        label.setPadding(0, 0, 0, dp(5));
        return label;
    }

    private EditText input(String value) {
        EditText edit = new EditText(this);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(13);
        edit.setTextColor(Color.parseColor("#0F172A"));
        edit.setHintTextColor(Color.parseColor("#94A3B8"));
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(
                RepeatOrderUi.roundStroke(
                        this,
                        "#F9FBFF",
                        "#D7E6F8",
                        13,
                        1
                )
        );

        edit.setLayoutParams(
                new LinearLayout.LayoutParams(-1, dp(50))
        );

        return edit;
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
