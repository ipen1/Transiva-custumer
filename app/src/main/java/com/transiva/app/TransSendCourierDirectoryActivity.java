package com.transiva.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public final class TransSendCourierDirectoryActivity extends CustomerAdaptiveActivity {
    private static final int LOCATION_PERMISSION = 41;
    private static final String TITLE = "Pilih Kurir di TransSend";
    private static final Locale INDONESIAN = new Locale("id", "ID");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CustomerLifecycleNetworkScope network = new CustomerLifecycleNetworkScope();
    private LinearLayout results;
    private TextView status;
    private ProgressBar progress;
    private Button refresh;
    private CancellationTokenSource locationRequest;

    static void open(Context context) {
        context.startActivity(new Intent(context, TransSendCourierDirectoryActivity.class));
    }

    static View homeCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int inset = CustomerUiPrimitives.dp(context, 12);
        card.setPadding(inset, inset, inset, inset);
        card.setBackground(CustomerUiPrimitives.roundStroke(
                context, "#EAF4FF", "#C8E1FF", CustomerUiPrimitives.dp(context, 18), 1));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_service_send_premium);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = CustomerUiPrimitives.dp(context, 36);
        card.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPaddingRelative(inset, 0, inset, 0);
        copy.addView(CustomerUiPrimitives.text(context, TITLE, 15, "#103769", true));
        copy.addView(CustomerUiPrimitives.text(context, "Lihat kurir terdekat dan ratingnya", 12, "#315678", false));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(CustomerUiPrimitives.text(context, "›", 25, "#075BB8", true));
        card.setContentDescription(TITLE + ". Lihat kurir terdekat dan ratingnya.");
        card.setFocusable(true);
        card.setOnClickListener(v -> open(context));
        return card;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        if (hasLocationPermission()) loadCouriers();
        else showLocationPrompt();
    }

    @Override protected void onDestroy() {
        if (locationRequest != null) locationRequest.cancel();
        network.destroy();
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(16), dp(8));
        header.setBackgroundColor(Color.WHITE);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setContentDescription("Kembali");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(TITLE, 18, "#103769", true);
        title.setPaddingRelative(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), 0);
        page.addView(body, new LinearLayout.LayoutParams(-1, -2));
        body.addView(label("Kurir motor di sekitar Anda", 17, "#103769", true));
        status = label("", 14, "#315678", false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(0, dp(6), 0, dp(8));
        body.addView(status);
        refresh = new Button(this);
        refresh.setAllCaps(false);
        refresh.setMinHeight(dp(48));
        refresh.setOnClickListener(v -> {
            if (hasLocationPermission()) loadCouriers();
            else requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
        });
        body.addView(refresh, new LinearLayout.LayoutParams(-2, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        body.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(16), dp(8), dp(16), dp(16));
        scroll.addView(results);
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showLocationPrompt() {
        finishLoading("Izinkan lokasi untuk melihat kurir di sekitar Anda.", "Izinkan Lokasi");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != LOCATION_PERMISSION) return;
        if (hasLocationPermission()) loadCouriers();
        else finishLoading("Akses lokasi diperlukan. Anda juga bisa mengaktifkannya di pengaturan aplikasi.", "Izinkan Lokasi");
    }

    private void loadCouriers() {
        results.removeAllViews();
        status.setText("Mencari lokasi Anda…");
        refresh.setText("Perbarui Daftar");
        refresh.setEnabled(false);
        progress.setVisibility(View.VISIBLE);

        locationRequest = new CancellationTokenSource();
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60_000L)
                .setDurationMillis(8_000L)
                .build();
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(request, locationRequest.getToken())
                    .addOnSuccessListener(location -> {
                        if (isFinishing() || network.isDestroyed()) return;
                        if (location == null) showLocationUnavailable();
                        else fetchCouriers(location);
                    })
                    .addOnFailureListener(error -> showLocationUnavailable());
        } catch (SecurityException error) {
            showLocationPrompt();
        }
    }

    private void showLocationUnavailable() {
        if (isFinishing() || network.isDestroyed()) return;
        finishLoading("Lokasi belum ditemukan. Aktifkan layanan lokasi lalu coba lagi.", "Coba Lagi");
    }

    private void fetchCouriers(Location location) {
        status.setText("Memuat kurir terdekat…");
        network.execute(() -> {
            try {
                JSONObject point = new JSONObject()
                        .put("lat", location.getLatitude())
                        .put("lng", location.getLongitude());
                JSONObject response = TransivaHttpRepository.postJson(this,
                        ApiConfig.server("customer_transsend_couriers.php"), point, 7000);
                JSONArray couriers = response.getJSONArray("couriers");
                network.post(main, () -> showCouriers(couriers));
            } catch (TransivaHttpRepository.ServerResponseException error) {
                String message = error.status == 429
                        ? "Terlalu sering memperbarui daftar. Tunggu sebentar lalu coba lagi."
                        : "Daftar kurir belum dapat dimuat. Coba lagi.";
                network.post(main, () -> finishLoading(message, "Coba Lagi"));
            } catch (Exception error) {
                network.post(main, () -> finishLoading(
                        "Daftar kurir belum dapat dimuat. Periksa koneksi lalu coba lagi.", "Coba Lagi"));
            }
        }, () -> network.post(main, () -> finishLoading(
                "Daftar kurir belum dapat dimuat. Coba lagi.", "Coba Lagi")));
    }

    private void showCouriers(JSONArray couriers) {
        for (int i = 0; i < couriers.length(); i++) {
            JSONObject courier = couriers.optJSONObject(i);
            if (courier != null) addCourier(courier);
        }
        finishLoading(results.getChildCount() == 0
                ? "Belum ada kurir tersedia di sekitar Anda."
                : "Jarak perkiraan dari lokasi Anda · Diurutkan dari yang terdekat", "Perbarui Daftar");
        CustomerAppSettings.apply(this);
    }

    private void addCourier(JSONObject courier) {
        String name = courier.optString("name", "Kurir Transiva");
        int distance = courier.optInt("distance_band_km");
        double rating = courier.optDouble("rating");
        String ratingText = "Belum ada rating";
        if (rating >= 1 && rating <= 5) {
            NumberFormat decimal = NumberFormat.getNumberInstance(INDONESIAN);
            decimal.setMinimumFractionDigits(1);
            decimal.setMaximumFractionDigits(1);
            ratingText = "★ " + decimal.format(rating);
        }
        String distanceText = distance == 0 ? "Kurang dari 1 km" : distance + "–" + (distance + 1) + " km";

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(CustomerUiPrimitives.roundStroke(this, "#FFFFFF", "#D4E6FA", dp(16), 1));

        TextView avatar = label(name.isEmpty() ? "K" : name.substring(0, name.offsetByCodePoints(0, 1))
                .toUpperCase(INDONESIAN), 18, "#075BB8", true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        avatar.setBackground(CustomerUiPrimitives.round("#EAF4FF", dp(24)));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPaddingRelative(dp(12), 0, 0, 0);
        details.addView(label(name, 16, "#103769", true));
        details.addView(label(distanceText + "  ·  " + ratingText, 14, "#315678", false));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.bottomMargin = dp(8);
        results.addView(row, spacing);
    }

    private void finishLoading(String message, String action) {
        status.setText(message);
        refresh.setText(action);
        refresh.setEnabled(true);
        progress.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return CustomerUiPrimitives.dp(this, value);
    }

    private TextView label(String value, int size, String color, boolean bold) {
        return CustomerUiPrimitives.text(this, value, size, color, bold);
    }
}
