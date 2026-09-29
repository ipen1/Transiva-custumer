package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Smart onboarding Customer.
 *
 * Prinsip:
 * - Memakai preference lama agar user existing tidak melihat onboarding ulang.
 * - Permission diminta kontekstual, satu per layar, dan selalu bisa dilewati.
 * - Tidak mengubah login, order recovery, status order, payment, atau backend.
 */
public class TransAssistantTourActivity extends Activity {
    static final String PREF = "trans_assistant_onboarding";
    static final String KEY = "done";

    private static final int REQ_LOCATION = 7311;
    private static final int PAGE_WELCOME = 0;
    private static final int PAGE_SERVICES = 1;
    private static final int PAGE_LOCATION = 2;
    private static final int PAGE_NOTIFICATION = 3;
    private static final int LAST_PAGE = PAGE_NOTIFICATION;

    private int page = PAGE_WELCOME;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout dots;
    private Button primary;
    private TextView secondary;

    public static boolean isDone(Activity activity) {
        return activity.getSharedPreferences(PREF, 0).getBoolean(KEY, false);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) page = state.getInt("page", PAGE_WELCOME);
        buildShell();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null && (page == PAGE_LOCATION || page == PAGE_NOTIFICATION)) render();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F7FAFF"));
        root.setPadding(dp(22), dp(18), dp(22), dp(18));

        TextView skip = text("Lewati", 14, true, "#145FEA");
        skip.setGravity(Gravity.END);
        skip.setPadding(dp(8), dp(6), dp(4), dp(8));
        skip.setOnClickListener(v -> finishOnboarding());
        root.addView(skip, lp(-1, dp(42)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(dots, lp(-1, dp(30)));

        primary = new Button(this);
        primary.setAllCaps(false);
        primary.setTextSize(16);
        primary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        primary.setTextColor(Color.WHITE);
        primary.setBackground(rounded("#0B63F6", 18));
        primary.setOnClickListener(v -> onPrimary());
        root.addView(primary, lp(-1, dp(56)));

        secondary = text("", 14, false, "#2456A6");
        secondary.setGravity(Gravity.CENTER);
        secondary.setPadding(0, dp(10), 0, 0);
        secondary.setOnClickListener(v -> next());
        root.addView(secondary, lp(-1, dp(42)));
        setContentView(root);
    }

    private void render() {
        content.removeAllViews();
        renderDots();
        secondary.setVisibility(View.VISIBLE);

        if (page == PAGE_WELCOME) renderWelcome();
        else if (page == PAGE_SERVICES) renderServices();
        else if (page == PAGE_LOCATION) renderLocation();
        else renderNotification();
    }

    private void renderWelcome() {
        addImage("transiva_logo", dp(126), dp(126));
        addSpacer(12);
        addTitle("Selamat Datang di Transiva");
        addBody("Transportasi, makanan, belanja, dan kebutuhan harian dalam satu aplikasi.");
        addSpacer(18);
        addInfoCard("Mudah digunakan", "Pesan layanan hanya dalam beberapa langkah.", "✓");
        addInfoCard("Harga lebih jelas", "Lihat informasi layanan sebelum melanjutkan pesanan.", "✓");
        addInfoCard("Bantuan saat dibutuhkan", "Chat, panggilan, dan bantuan tersedia dari aplikasi.", "✓");
        primary.setText("Mulai Sekarang  →");
        secondary.setText("Lewati pengenalan");
    }

    private void renderServices() {
        addStep("1/3");
        addTitle("Pilih Layanan Sesuai Kebutuhanmu");
        addBody("Semua layanan utama Transiva dapat kamu temukan dari Beranda.");
        addSpacer(12);
        addService("ic_service_ride", "TransRide", "Pesan motor atau mobil dengan mudah");
        addService("ic_service_food", "TransFood", "Pesan makanan favorit di sekitarmu");
        addService("ic_service_shop_premium", "TransShop", "Belanja kebutuhan dari toko terdekat");
        addService("ic_service_pickup", "TransPickup", "Kirim dan ambil barang dengan praktis");
        primary.setText("Lanjut  →");
        secondary.setText("Lewati");
    }

    private void renderLocation() {
        addStep("2/3");
        addImage("ic_location_pin", dp(112), dp(112));
        addTitle("Izinkan Akses Lokasi");
        addBody("Lokasi membantu Transiva menemukan titik jemput, driver, toko, dan layanan terdekat dengan lebih akurat.");
        addSpacer(14);
        addInfoCard("Titik jemput lebih akurat", "Kamu tetap dapat mengoreksi lokasi sebelum memesan.", "⌖");
        addInfoCard("Cari layanan terdekat", "Membantu menampilkan driver dan merchant di sekitar.", "⌁");
        boolean granted = hasLocation();
        primary.setText(granted ? "✓ Lokasi Sudah Aktif" : "Izinkan Lokasi");
        secondary.setText(granted ? "Lanjut" : "Nanti Saja");
    }

    private void renderNotification() {
        addStep("3/3");
        addImage("ic_notification_bell", dp(112), dp(112));
        addTitle("Aktifkan Notifikasi");
        addBody("Dapatkan kabar penting tentang status pesanan, driver, chat, dan informasi layanan.");
        addSpacer(14);
        addInfoCard("Status pesanan", "Ketahui perubahan status perjalanan atau pesanan.", "✓");
        addInfoCard("Pesan dari driver atau toko", "Jangan lewatkan chat penting saat pesanan berjalan.", "✓");
        addInfoCard("Informasi Transiva", "Terima pemberitahuan layanan yang relevan.", "✓");
        boolean granted = hasNotification();
        primary.setText(granted ? "✓ Notifikasi Sudah Aktif" : "Aktifkan Notifikasi");
        secondary.setText(granted ? "Masuk ke Transiva" : "Nanti Saja");
    }

    private void onPrimary() {
        if (page == PAGE_LOCATION) {
            if (hasLocation()) next();
            else requestLocation();
            return;
        }
        if (page == PAGE_NOTIFICATION) {
            if (hasNotification()) finishOnboarding();
            else requestNotification();
            return;
        }
        next();
    }

    private void next() {
        if (page >= LAST_PAGE) finishOnboarding();
        else {
            page++;
            render();
        }
    }

    private void requestLocation() {
        if (Build.VERSION.SDK_INT < 23) { next(); return; }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQ_LOCATION);
    }

    private void requestNotification() {
        if (Build.VERSION.SDK_INT >= 33) {
            TransivaNotificationPermission.ask(this);
        } else {
            finishOnboarding();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION) {
            page = PAGE_NOTIFICATION;
            render();
        } else if (requestCode == TransivaNotificationPermission.REQUEST_CODE) {
            finishOnboarding();
        }
    }

    private boolean hasLocation() {
        return Build.VERSION.SDK_INT < 23
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotification() {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void finishOnboarding() {
        getSharedPreferences(PREF, 0).edit().putBoolean(KEY, true).apply();
        SessionManager session = new SessionManager(this);
        Intent intent = session.isLoggedIn()
                ? new Intent(this, CustomerDashboardActivity.class)
                : new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void addStep(String value) {
        TextView v = text(value, 14, true, "#2456A6");
        v.setGravity(Gravity.START);
        content.addView(v, lp(-1, -2));
    }

    private void addTitle(String value) {
        TextView v = text(value, 27, true, "#0B1F44");
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(v, lp(-1, -2));
    }

    private void addBody(String value) {
        TextView v = text(value, 15, false, "#52647D");
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.12f);
        content.addView(v, lp(-1, -2));
    }

    private void addImage(String drawable, int w, int h) {
        int id = getResources().getIdentifier(drawable, "drawable", getPackageName());
        if (id == 0) return;
        ImageView image = new ImageView(this);
        image.setImageResource(id);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        image.setLayoutParams(p);
        content.addView(image);
    }

    private void addService(String drawable, String title, String description) {
        LinearLayout card = card();
        int id = getResources().getIdentifier(drawable, "drawable", getPackageName());
        if (id != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(id);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            card.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(66)));
        }
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text(title, 17, true, "#0B4DC8"));
        copy.addView(text(description, 13, false, "#52647D"));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(card, cardParams());
    }

    private void addInfoCard(String title, String description, String symbol) {
        LinearLayout card = card();
        TextView badge = text(symbol, 20, true, "#0B63F6");
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(52)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 14, true, "#17324D"));
        copy.addView(text(description, 12, false, "#66758A"));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(card, cardParams());
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(rounded("#FFFFFF", 18));
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(6));
        return p;
    }

    private void renderDots() {
        dots.removeAllViews();
        for (int i = 0; i <= LAST_PAGE; i++) {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextSize(i == page ? 14 : 11);
            dot.setTextColor(Color.parseColor(i == page ? "#0B63F6" : "#D5DFED"));
            dot.setGravity(Gravity.CENTER);
            dots.addView(dot, new LinearLayout.LayoutParams(dp(24), -1));
        }
    }

    private TextView text(String value, int size, boolean bold, String color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.parseColor(color));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private android.graphics.drawable.GradientDrawable rounded(String color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private void addSpacer(int height) {
        View v = new View(this);
        content.addView(v, lp(1, dp(height)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
