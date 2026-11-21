package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

final class TransSendContactPanel {
    private static final int PICKUP = 641;
    private static final int DELIVERY = 642;
    private final Activity activity;
    private final String id;
    private final LinearLayout view;
    private final Button pickupButton;
    private final Button deliveryButton;
    private final Button orderButton;
    private final TextView summary;
    private final CustomerLifecycleNetworkScope network = new CustomerLifecycleNetworkScope();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Intent pickup;
    private Intent delivery;
    private int quoteVersion;
    private boolean active = true;
    private long price;

    TransSendContactPanel(Activity activity, String id, Bundle saved) {
        this.activity = activity;
        this.id = id;
        if (saved != null) {
            pickup = saved.getParcelable("contactPickup");
            delivery = saved.getParcelable("contactDelivery");
        }
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        int inset = CustomerUiPrimitives.dp(activity, 10);
        view.setPadding(inset, 0, inset, 0);
        view.setBackground(CustomerUiPrimitives.round("#EAF4FF", inset));
        LinearLayout row = new LinearLayout(activity);
        view.addView(row);
        pickupButton = button("Atur Jemput", () -> pick(PICKUP));
        deliveryButton = button("Atur Antar", () -> pick(DELIVERY));
        row.addView(pickupButton, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(deliveryButton, new LinearLayout.LayoutParams(0, -2, 1));
        summary = CustomerUiPrimitives.text(activity, "", 13, "#103769", true);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        summary.setMaxLines(4);
        view.addView(summary);
        orderButton = button("Pesan TransSend", this::order);
        view.addView(orderButton);
        refreshQuote();
    }

    View view() { return view; }

    private Button button(String label, Runnable click) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    private void pick(int which) {
        if (!active) return;
        Intent origin = which == PICKUP ? pickup : delivery;
        if (origin == null) origin = which == PICKUP ? delivery : pickup;
        Intent picker = new Intent(activity, TransSendContactPointActivity.class)
                .putExtra("title", which == PICKUP ? "Atur Posisi Jemput" : "Atur Posisi Antar");
        if (origin != null) picker.putExtras(origin);
        activity.startActivityForResult(picker, which);
    }

    boolean result(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICKUP && requestCode != DELIVERY) return false;
        if (resultCode == Activity.RESULT_OK && data != null && active) {
            if (requestCode == PICKUP) pickup = data;
            else delivery = data;
            refreshQuote();
        }
        return true;
    }

    void save(Bundle out) {
        out.putParcelable("contactPickup", pickup);
        out.putParcelable("contactDelivery", delivery);
    }

    void updateStatus(String status) {
        if (!active || "contact_accepted".equals(status) || status.isEmpty()) return;
        active = false;
        quoteVersion++;
        view.setVisibility(View.GONE);
    }

    void dispose() { network.destroy(); }

    private void refreshQuote() {
        price = 0;
        int request = ++quoteVersion;
        orderButton.setText("Pesan TransSend");
        orderButton.setEnabled(false);
        String points = "Jemput · " + address(pickup) + "\nAntar · " + address(delivery);
        summary.setText(points);
        if (pickup == null || delivery == null) return;
        Intent from = pickup;
        Intent to = delivery;
        summary.setText(points + "\nMenghitung estimasi…");
        network.execute(() -> {
            try {
                JSONObject quote = TransSendContactClient.quote(activity, from, to);
                long estimate = quote.optLong("price");
                if (!quote.optBoolean("success") || estimate <= 0) throw new IllegalStateException();
                String amount = NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(estimate);
                network.post(main, () -> {
                    if (request != quoteVersion || !active) return;
                    price = estimate;
                    summary.setText(points + "\nEstimasi paket kecil · Rp " + amount);
                    orderButton.setEnabled(true);
                });
            } catch (Exception error) {
                network.post(main, () -> quoteFailed(request, points));
            }
        }, () -> network.post(main, () -> quoteFailed(request, points)));
    }

    private void quoteFailed(int request, String points) {
        if (request != quoteVersion || !active) return;
        summary.setText(points + "\nEstimasi belum tersedia.");
        orderButton.setText("Hitung Ulang");
        orderButton.setEnabled(true);
    }

    private static String address(Intent point) {
        return point == null ? "Belum diatur" : point.getStringExtra("address");
    }

    private void order() {
        if (!active) return;
        if (price <= 0) {
            refreshQuote();
            return;
        }
        activity.startActivity(new Intent(activity, TransPickupActivity.class)
                .putExtra(TransSendContactClient.EXTRA_CONTACT, id)
                .putExtra("contact_pickup", pickup)
                .putExtra("contact_delivery", delivery));
    }
}
