package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Penerima tautan/koordinat lokasi dari WhatsApp, Google Maps, dan menu Share.
 * Pengguna memilih apakah lokasi menjadi titik Jemput atau Antar, kemudian
 * memilih layanan TransRide atau TransCar.
 */
public class SharedLocationActivity extends Activity {

    private String sharedLocation = "";
    private String selectedRole = "";
    private TextView info;
    private Button pickupButton;
    private Button destinationButton;
    private Button rideButton;
    private Button carButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#071426"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));

        sharedLocation = readSharedLocation(getIntent());
        if (sharedLocation.isEmpty()) {
            finish();
            return;
        }

        buildUi();
    }

    private String readSharedLocation(Intent intent) {
        if (intent == null) return "";
        Uri data = intent.getData();
        if (data != null) return data.toString().trim();

        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return "";
        text = text.trim();

        // Ambil URL/geo dari teks share yang juga berisi judul atau alamat.
        String[] parts = text.split("\\s+");
        for (String part : parts) {
            String p = part.trim();
            if (p.startsWith("geo:") || p.startsWith("http://") || p.startsWith("https://")) {
                return p;
            }
        }
        return text;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(36), dp(22), dp(24));
        root.setBackgroundColor(Color.parseColor("#071426"));

        TextView logo = label("TRANSIVA", 30, "#2FA4FF", true);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, lp(-1, dp(55), 0));

        TextView title = label("Gunakan lokasi ini untuk", 22, "#FFFFFF", true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = lp(-1, -2, 0);
        titleLp.setMargins(0, dp(18), 0, dp(8));
        root.addView(title, titleLp);

        info = label("Pilih fungsi lokasi dari WhatsApp atau Google Maps", 14, "#AFC6DF", false);
        info.setGravity(Gravity.CENTER);
        root.addView(info, lp(-1, -2, 0));

        LinearLayout roleCard = card();
        LinearLayout.LayoutParams cardLp = lp(-1, -2, 0);
        cardLp.setMargins(0, dp(28), 0, 0);
        root.addView(roleCard, cardLp);

        pickupButton = actionButton("📍  Transiva Jemput", "#0B7CFF");
        destinationButton = actionButton("🏁  Transiva Antar", "#0B7CFF");
        roleCard.addView(pickupButton, buttonLp());
        roleCard.addView(destinationButton, buttonLp());

        TextView serviceTitle = label("Pilih kendaraan", 17, "#FFFFFF", true);
        LinearLayout.LayoutParams stLp = lp(-1, -2, 0);
        stLp.setMargins(0, dp(24), 0, dp(8));
        root.addView(serviceTitle, stLp);

        LinearLayout serviceRow = new LinearLayout(this);
        serviceRow.setOrientation(LinearLayout.HORIZONTAL);
        serviceRow.setGravity(Gravity.CENTER);
        root.addView(serviceRow, lp(-1, dp(64), 0));

        rideButton = actionButton("🏍  TransRide", "#13456F");
        carButton = actionButton("🚗  TransCar", "#13456F");
        rideButton.setEnabled(false);
        carButton.setEnabled(false);
        rideButton.setAlpha(.45f);
        carButton.setAlpha(.45f);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -1, 1f);
        half.setMargins(0, 0, dp(6), 0);
        serviceRow.addView(rideButton, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -1, 1f);
        half2.setMargins(dp(6), 0, 0, 0);
        serviceRow.addView(carButton, half2);

        Button cancel = actionButton("Batal", "#24364A");
        LinearLayout.LayoutParams cancelLp = lp(-1, dp(52), 0);
        cancelLp.setMargins(0, dp(24), 0, 0);
        root.addView(cancel, cancelLp);

        pickupButton.setOnClickListener(v -> selectRole("pickup"));
        destinationButton.setOnClickListener(v -> selectRole("destination"));
        rideButton.setOnClickListener(v -> openService(TransRideActivity.class));
        carButton.setOnClickListener(v -> openService(PassengerCarActivity.class));
        cancel.setOnClickListener(v -> finish());

        setContentView(root);
    }

    private void selectRole(String role) {
        selectedRole = role;
        boolean pickup = "pickup".equals(role);
        pickupButton.setBackground(background(pickup ? "#18A0FB" : "#13456F"));
        destinationButton.setBackground(background(!pickup ? "#F59E0B" : "#13456F"));
        info.setText(pickup
                ? "Lokasi akan menjadi titik jemput"
                : "Lokasi akan menjadi tujuan antar");
        rideButton.setEnabled(true);
        carButton.setEnabled(true);
        rideButton.setAlpha(1f);
        carButton.setAlpha(1f);
    }

    private void openService(Class<?> activityClass) {
        if (selectedRole.isEmpty()) return;
        Intent next = new Intent(this, activityClass);
        next.putExtra("shared_location_uri", sharedLocation);
        next.putExtra("shared_location_role", selectedRole);
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(next);
        finish();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(4));
        box.setBackground(background("#10263D"));
        return box;
    }

    private Button actionButton(String text, String color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(background(color));
        return b;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams p = lp(-1, dp(58), 0);
        p.setMargins(0, 0, 0, dp(9));
        return p;
    }

    private GradientDrawable background(String color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), Color.parseColor("#315979"));
        return d;
    }

    private TextView label(String text, int size, String color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(Color.parseColor(color));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
