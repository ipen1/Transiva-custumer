package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class CustomerSettingsActivity extends Activity {
    private static final String DEVICE_URL = "https://transiva.my.id/server/customer_device_native.php";
    private final Handler main = new Handler(Looper.getMainLooper());
    private SessionManager session;
    private TextView deviceName, deviceDetail, deviceStatus;
    private Button disconnectButton;
    private ProgressBar progress;
    private boolean deviceLoading;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionManager(this);
        setContentView(buildScreen());
        CustomerAppSettings.apply(this);
        loadConnectedDevice();
    }

    @Override protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);
    }

    private LinearLayout buildScreen() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.parseColor("#F5F8FD"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, "#0B7CFF", true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Pengaturan Aplikasi", 23, "#0B3A78", true));
        titles.addView(text("Atur perangkat, getar, dan tampilan customer", 11, "#718096", false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        root.addView(sectionTitle("Tampilan & Notifikasi"), marginTop(14));
        LinearLayout preferenceCard = card();
        preferenceCard.addView(toggleRow("Getar", "Aktifkan getaran untuk notifikasi dan aksi aplikasi", CustomerAppSettings.isVibrationEnabled(this), (button, checked) -> CustomerAppSettings.setVibrationEnabled(this, checked)));
        preferenceCard.addView(divider());
        preferenceCard.addView(toggleRow("Mode Gelap", "Terapkan tema gelap pada seluruh halaman customer", CustomerAppSettings.isDarkMode(this), (button, checked) -> {
            CustomerAppSettings.setDarkMode(this, checked);
            recreate();
        }));
        root.addView(preferenceCard);

        root.addView(sectionTitle("Pembaruan"), marginTop(18));
        LinearLayout updateCard = card();
        LinearLayout updateRow = new LinearLayout(this);
        updateRow.setGravity(Gravity.CENTER_VERTICAL);
        updateRow.setPadding(0, dp(4), 0, dp(4));
        LinearLayout updateLabels = new LinearLayout(this);
        updateLabels.setOrientation(LinearLayout.VERTICAL);
        updateLabels.addView(text("Cek Pembaruan Aplikasi", 15, "#0B3A78", true));
        updateLabels.addView(text("Versi terpasang " + AppUpdateClient.installedVersionName(this), 11, "#64748B", false));
        updateRow.addView(updateLabels, new LinearLayout.LayoutParams(0, -2, 1));
        updateRow.addView(text("›", 30, "#0B7CFF", true));
        updateRow.setOnClickListener(v -> {
            Intent intent = new Intent(this, UpdateDownloadActivity.class);
            intent.putExtra(UpdateDownloadActivity.EXTRA_ROLE, "customer");
            startActivity(intent);
        });
        updateCard.addView(updateRow);
        root.addView(updateCard);

        root.addView(sectionTitle("Perangkat Terhubung"), marginTop(18));
        LinearLayout deviceCard = card();
        deviceName = text("Memeriksa perangkat...", 16, "#0B3A78", true);
        deviceDetail = text("", 11, "#64748B", false);
        deviceStatus = text("MEMERIKSA", 10, "#0B7CFF", true);
        deviceStatus.setPadding(dp(9), dp(5), dp(9), dp(5));
        deviceStatus.setBackground(round("#EAF4FF", 12));
        LinearLayout nameRow = new LinearLayout(this); nameRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nameBlock = new LinearLayout(this); nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameBlock.addView(deviceName); nameBlock.addView(deviceDetail);
        nameRow.addView(nameBlock, new LinearLayout.LayoutParams(0, -2, 1)); nameRow.addView(deviceStatus);
        deviceCard.addView(nameRow);
        TextView hint = text("Putuskan perangkat untuk memindahkan akun ke HP lain. Anda akan otomatis keluar dan dapat login di perangkat baru.", 11, "#64748B", false);
        hint.setPadding(0, dp(12), 0, dp(8)); deviceCard.addView(hint);
        disconnectButton = button("Putuskan Perangkat", "#C62828");
        disconnectButton.setEnabled(false); disconnectButton.setAlpha(.55f);
        disconnectButton.setOnClickListener(v -> confirmDisconnect());
        deviceCard.addView(disconnectButton, new LinearLayout.LayoutParams(-1, dp(48)));
        progress = new ProgressBar(this); progress.setVisibility(ProgressBar.GONE);
        deviceCard.addView(progress, new LinearLayout.LayoutParams(-1, dp(38)));
        root.addView(deviceCard);
        return shell;
    }

    private LinearLayout toggleRow(String title, String subtitle, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, "#0B3A78", true)); labels.addView(text(subtitle, 11, "#64748B", false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this); toggle.setChecked(checked); toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle); return row;
    }

    private void loadConnectedDevice() {
        if (deviceLoading || session == null || !session.isLoggedIn()) return;
        String token = first(session.getToken()); if (token.isEmpty()) return;
        deviceLoading = true; showLoading(true);
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(DEVICE_URL + "?action=get_device").openConnection();
                c.setRequestMethod("GET"); c.setRequestProperty("Authorization", "Bearer " + token); c.setConnectTimeout(30000); c.setReadTimeout(30000);
                JSONObject response = new JSONObject(read(c));
                if (!response.optBoolean("success", false)) throw new IllegalStateException(response.optString("message", "Gagal memeriksa perangkat."));
                JSONObject device = response.optJSONObject("device");
                main.post(() -> { deviceLoading = false; showLoading(false); applyDevice(device); });
            } catch (Exception e) {
                main.post(() -> { deviceLoading = false; showLoading(false); deviceName.setText("Perangkat tidak dapat diperiksa"); deviceDetail.setText(first(e.getMessage(), "Coba lagi.")); updateButton(false); });
            } finally { if (c != null) c.disconnect(); }
        }).start();
    }

    private void applyDevice(JSONObject d) {
        if (d == null || d.optInt("id", 0) <= 0) {
            deviceName.setText("Belum ada perangkat terhubung"); deviceDetail.setText("Login kembali untuk menghubungkan perangkat ini."); deviceStatus.setText("TIDAK AKTIF"); updateButton(false); return;
        }
        String name = first(d.optString("device_name"), d.optString("manufacturer") + " " + d.optString("model"), "Perangkat Android");
        deviceName.setText(name.trim()); deviceDetail.setText("Android " + first(d.optString("android_version"), "-") + "  •  Terakhir aktif " + first(d.optString("last_seen_at"), "-"));
        String status = first(d.optString("status"), "active").toLowerCase(Locale.US);
        deviceStatus.setText(status.equals("active") ? "AKTIF" : status.toUpperCase(Locale.US)); updateButton(status.equals("active"));
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this).setTitle("Putuskan perangkat?").setMessage("Perangkat ini akan dilepas dari akun dan sesi Anda akan diakhiri.")
                .setNegativeButton("Batal", null).setPositiveButton("Putuskan", (d, w) -> disconnect()).show();
    }

    private void disconnect() {
        if (deviceLoading) return; deviceLoading = true; showLoading(true); updateButton(false);
        String token = first(session.getToken());
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(DEVICE_URL).openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + token); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); c.setConnectTimeout(30000); c.setReadTimeout(30000);
                JSONObject body = new JSONObject(); body.put("action", "disconnect_device"); body.put("installation_uuid", DeviceIdentityManager.getInstallationUuid(this));
                try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                JSONObject response = new JSONObject(read(c)); if (!response.optBoolean("success", false)) throw new IllegalStateException(response.optString("message", "Perangkat gagal diputuskan."));
                main.post(() -> { Toast.makeText(this, "Perangkat berhasil diputuskan.", Toast.LENGTH_LONG).show(); session.forceLogout("customer_device_disconnected"); Intent i = new Intent(this, LoginActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(i); finish(); });
            } catch (Exception e) { main.post(() -> { deviceLoading = false; showLoading(false); updateButton(true); Toast.makeText(this, first(e.getMessage(), "Perangkat gagal diputuskan."), Toast.LENGTH_LONG).show(); }); }
            finally { if (c != null) c.disconnect(); }
        }).start();
    }

    private static String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode(); BufferedReader r = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder b = new StringBuilder(); String line; while ((line = r.readLine()) != null) b.append(line); r.close(); return b.toString();
    }
    private void showLoading(boolean show) { if (progress != null) progress.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.GONE); }
    private void updateButton(boolean enabled) { disconnectButton.setEnabled(enabled && !deviceLoading); disconnectButton.setAlpha(disconnectButton.isEnabled() ? 1f : .55f); }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(16), dp(16), dp(16), dp(16)); v.setBackground(round("#FFFFFF", 20)); v.setElevation(dp(2)); return v; }
    private TextView sectionTitle(String s) { return text(s, 13, "#0B3A78", true); }
    private LinearLayout.LayoutParams marginTop(int top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(top), 0, dp(8)); return lp; }
    private TextView divider() { TextView v = new TextView(this); v.setBackgroundColor(Color.parseColor("#E7EEF7")); v.setHeight(dp(1)); return v; }
    private Button button(String s, String color) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(color, 14)); return b; }
    private TextView text(String s, int size, String color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(Color.parseColor(color)); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private GradientDrawable round(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(dp(radius)); return g; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String first(String... values) { for (String v : values) if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v.trim())) return v.trim(); return ""; }
}
