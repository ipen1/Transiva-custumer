package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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
    private TextView overlayStatus;
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
        updateOverlayStatus();
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

        root.addView(sectionTitle("Performa & Baterai"), marginTop(18));
        LinearLayout performanceCard = card();
        String selectedPerf = CustomerPerformanceManager.selectedMode(this);
        String effectivePerf = CustomerPerformanceManager.effectiveMode(this);
        String recommendedPerf = CustomerPerformanceManager.recommendedMode(this);
        LinearLayout perfRow = new LinearLayout(this);
        perfRow.setGravity(Gravity.CENTER_VERTICAL);
        perfRow.setPadding(0, dp(6), 0, dp(6));
        LinearLayout perfLabels = new LinearLayout(this); perfLabels.setOrientation(LinearLayout.VERTICAL);
        TextView perfTitle = text("Mode Performa", 15, "#0B3A78", true);
        TextView perfSub = text("Aktif: " + CustomerPerformanceManager.label(effectivePerf) + " • Rekomendasi: " + CustomerPerformanceManager.label(recommendedPerf) + "\n" + CustomerPerformanceManager.deviceSummary(this), 11, "#64748B", false);
        perfLabels.addView(perfTitle); perfLabels.addView(perfSub);
        perfRow.addView(perfLabels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView perfBadge = text(CustomerPerformanceManager.label(selectedPerf).toUpperCase(Locale.ROOT), 10, "#0B7CFF", true);
        perfBadge.setPadding(dp(9), dp(5), dp(9), dp(5)); perfBadge.setBackground(round("#EAF4FF", 12));
        perfRow.addView(perfBadge);
        perfRow.setOnClickListener(v -> showPerformanceModeDialog());
        performanceCard.addView(perfRow);
        performanceCard.addView(divider());
        TextView perfHint = text("Hemat Daya mengurangi frekuensi polling, animasi, dan refresh visual. Normal menjaga keseimbangan. Performa Tinggi membuat refresh lebih agresif dan dapat meningkatkan panas serta konsumsi baterai.", 11, "#64748B", false);
        perfHint.setPadding(0, dp(10), 0, dp(4));
        performanceCard.addView(perfHint);
        root.addView(performanceCard);

        root.addView(sectionTitle("Keamanan & Fitur Pintar"), marginTop(18));
        LinearLayout securityCard = card();
        securityCard.addView(toggleRow("Biometrik PIN / Transiva Pay", "Gunakan sidik jari/wajah untuk membuka PIN dan mengotorisasi Transiva Pay di perangkat ini", BiometricSecurityManager.isEnabled(this), (button, checked) -> {
            if (checked && !BiometricSecurityManager.canUse(this)) {
                button.setChecked(false);
                new TransivaAlertDialogBuilder(this).setTitle("Biometrik belum tersedia").setMessage("Daftarkan sidik jari atau wajah yang didukung perangkat, lalu aktifkan kembali fitur ini.").setPositiveButton("OK", null).show();
                return;
            }
            BiometricSecurityManager.setEnabled(this, checked);
        }));
        securityCard.addView(divider());
        securityCard.addView(actionRow("Transiva Safety Center", "Panggilan darurat, panduan keselamatan, dan akses perjalanan", SafetyCenterActivity.class));
        securityCard.addView(divider());
        securityCard.addView(actionRow("Smart Reorder", "Pesan ulang berdasarkan riwayat favorit dan terbaru", SmartReorderActivity.class));
        securityCard.addView(divider());
        securityCard.addView(actionRow("Transiva Royalti", "Lihat poin, tier, dan progres loyalitas customer", CustomerLoyaltyActivity.class));
        root.addView(securityCard);

        if (BuildConfig.SELF_UPDATE_APK) {
            root.addView(sectionTitle("Panggilan Masuk"), marginTop(18));
            LinearLayout callCard = card();
            LinearLayout overlayRow = new LinearLayout(this);
            overlayRow.setGravity(Gravity.CENTER_VERTICAL);
            overlayRow.setPadding(0, dp(4), 0, dp(4));
            LinearLayout overlayLabels = new LinearLayout(this);
            overlayLabels.setOrientation(LinearLayout.VERTICAL);
            overlayLabels.addView(text("Tampil di atas aplikasi lain", 15, "#0B3A78", true));
            overlayLabels.addView(text("Opsional • membantu layar panggilan tampil saat Transiva berada di latar belakang", 11, "#64748B", false));
            overlayRow.addView(overlayLabels, new LinearLayout.LayoutParams(0, -2, 1));
            overlayStatus = text("OPSIONAL", 10, "#0B7CFF", true);
            overlayStatus.setPadding(dp(9), dp(5), dp(9), dp(5));
            overlayStatus.setBackground(round("#EAF4FF", 12));
            overlayRow.addView(overlayStatus);
            overlayRow.setOnClickListener(v -> explainOverlayPermission());
            callCard.addView(overlayRow);
            root.addView(callCard);
            updateOverlayStatus();
        }

        root.addView(sectionTitle("Pembaruan"), marginTop(18));
        LinearLayout updateCard = card();
        LinearLayout updateRow = new LinearLayout(this);
        updateRow.setGravity(Gravity.CENTER_VERTICAL);
        updateRow.setPadding(0, dp(4), 0, dp(4));
        LinearLayout updateLabels = new LinearLayout(this);
        updateLabels.setOrientation(LinearLayout.VERTICAL);
        updateLabels.addView(text(BuildConfig.SELF_UPDATE_APK ? "Cek Pembaruan Aplikasi" : "Pembaruan melalui Google Play", 15, "#0B3A78", true));
        updateLabels.addView(text("Versi " + AppUpdateClient.installedVersionName(this)
                + " • resource " + CustomerResourceUpdateManager.installedVersion(this), 11, "#64748B", false));
        updateRow.addView(updateLabels, new LinearLayout.LayoutParams(0, -2, 1));
        updateRow.addView(text("›", 30, "#0B7CFF", true));
        updateRow.setOnClickListener(v -> {
            if (BuildConfig.SELF_UPDATE_APK) {
                Intent intent = new Intent(this, UpdateDownloadActivity.class);
                intent.putExtra(UpdateDownloadActivity.EXTRA_ROLE, "customer");
                startActivity(intent);
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                } catch (Throwable e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                }
            }
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

    private void showPerformanceModeDialog() {
        final String[] modes = {CustomerPerformanceManager.MODE_AUTO, CustomerPerformanceManager.MODE_ECO, CustomerPerformanceManager.MODE_NORMAL, CustomerPerformanceManager.MODE_HIGH};
        final String[] labels = {"Otomatis (disarankan)", "Hemat Daya", "Normal", "Performa Tinggi"};
        String current = CustomerPerformanceManager.selectedMode(this);
        int checked = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(current)) checked = i;
        new TransivaAlertDialogBuilder(this)
                .setTitle("Mode performa customer")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String chosen = modes[which];
                    dialog.dismiss();
                    if (CustomerPerformanceManager.isAboveRecommendation(this, chosen)) {
                        new TransivaAlertDialogBuilder(this)
                                .setTitle("Mode di atas kemampuan yang disarankan")
                                .setMessage("Perangkat ini direkomendasikan menggunakan " + CustomerPerformanceManager.label(CustomerPerformanceManager.recommendedMode(this)) + ". Memilih " + CustomerPerformanceManager.label(chosen) + " dapat membuat perangkat lebih panas, baterai lebih boros, atau animasi tidak stabil.")
                                .setNegativeButton("Batal", null)
                                .setPositiveButton("Tetap gunakan", (d, w) -> applyPerformanceMode(chosen))
                                .show();
                    } else {
                        applyPerformanceMode(chosen);
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void applyPerformanceMode(String mode) {
        CustomerPerformanceManager.setSelectedMode(this, mode);
        Toast.makeText(this, "Mode performa: " + CustomerPerformanceManager.label(CustomerPerformanceManager.effectiveMode(this)), Toast.LENGTH_SHORT).show();
        recreate();
    }

    private View actionRow(String title, String subtitle, Class<?> target) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, "#0B3A78", true)); labels.addView(text(subtitle, 11, "#64748B", false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(text("›", 28, "#0B7CFF", true));
        row.setOnClickListener(v -> startActivity(new Intent(this, target))); return row;
    }

    private LinearLayout toggleRow(String title, String subtitle, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, "#0B3A78", true)); labels.addView(text(subtitle, 11, "#64748B", false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this); toggle.setChecked(checked); toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle); return row;
    }

    private void updateOverlayStatus() {
        if (overlayStatus == null) return;
        boolean enabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        overlayStatus.setText(enabled ? "AKTIF" : "OPSIONAL");
        overlayStatus.setTextColor(Color.parseColor(enabled ? "#07864B" : "#0B7CFF"));
        overlayStatus.setBackground(round(enabled ? "#EAFBF2" : "#EAF4FF", 12));
    }

    private void explainOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Perangkat ini tidak memerlukan izin tambahan.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Settings.canDrawOverlays(this)) {
            new TransivaAlertDialogBuilder(this)
                    .setTitle("Izin panggilan sudah aktif")
                    .setMessage("Transiva dapat membantu menampilkan layar panggilan saat aplikasi berada di latar belakang. Anda dapat menonaktifkannya kapan saja melalui pengaturan Android.")
                    .setNegativeButton("Tutup", null)
                    .setPositiveButton("Buka pengaturan", (d, w) -> openOverlaySettings())
                    .show();
            return;
        }
        new TransivaAlertDialogBuilder(this)
                .setTitle("Izin opsional untuk panggilan")
                .setMessage("Aktifkan hanya bila Anda ingin layar panggilan Transiva lebih mudah muncul ketika aplikasi sedang berada di latar belakang. Tanpa izin ini, notifikasi panggilan tetap digunakan.")
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Aktifkan", (d, w) -> openOverlaySettings())
                .show();
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable e) {
            Toast.makeText(this, "Pengaturan izin tidak tersedia pada perangkat ini.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadConnectedDevice() {
        if (deviceLoading || session == null || !session.isLoggedIn()) return;
        String token = first(session.getToken()); if (token.isEmpty()) return;
        deviceLoading = true; showLoading(true);
        TransivaNetworkExecutor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(DEVICE_URL + "?action=get_device").openConnection();
                c.setRequestMethod("GET"); c.setRequestProperty("Authorization", "Bearer " + token); c.setConnectTimeout(30000); c.setReadTimeout(30000);
                c.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));
                c.setRequestProperty("X-App-Scope", "customer");
                JSONObject response = new JSONObject(read(c));
                if (!response.optBoolean("success", false)) throw new IllegalStateException(response.optString("message", "Gagal memeriksa perangkat."));
                JSONObject device = response.optJSONObject("device");
                main.post(() -> { deviceLoading = false; showLoading(false); applyDevice(device); });
            } catch (Exception e) {
                main.post(() -> { deviceLoading = false; showLoading(false); deviceName.setText("Perangkat tidak dapat diperiksa"); deviceDetail.setText(first(e.getMessage(), "Coba lagi.")); updateButton(false); });
            } finally { if (c != null) c.disconnect(); }
        });
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
        new TransivaAlertDialogBuilder(this).setTitle("Putuskan perangkat?").setMessage("Perangkat ini akan dilepas dari akun dan sesi Anda akan diakhiri.")
                .setNegativeButton("Batal", null).setPositiveButton("Putuskan", (d, w) -> disconnect()).show();
    }

    private void disconnect() {
        if (deviceLoading) return; deviceLoading = true; showLoading(true); updateButton(false);
        String token = first(session.getToken());
        TransivaNetworkExecutor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(DEVICE_URL).openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + token); c.setRequestProperty("Content-Type", "application/json; charset=UTF-8"); c.setConnectTimeout(30000); c.setReadTimeout(30000);
                c.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));
                c.setRequestProperty("X-App-Scope", "customer");
                JSONObject body = new JSONObject(); body.put("action", "disconnect_device"); body.put("installation_uuid", DeviceIdentityManager.getInstallationUuid(this));
                try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                JSONObject response = new JSONObject(read(c)); if (!response.optBoolean("success", false)) throw new IllegalStateException(response.optString("message", "Perangkat gagal diputuskan."));
                main.post(() -> { Toast.makeText(this, "Perangkat berhasil diputuskan.", Toast.LENGTH_LONG).show(); session.forceLogout("customer_device_disconnected"); Intent i = new Intent(this, LoginActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(i); finish(); });
            } catch (Exception e) { main.post(() -> { deviceLoading = false; showLoading(false); updateButton(true); Toast.makeText(this, first(e.getMessage(), "Perangkat gagal diputuskan."), Toast.LENGTH_LONG).show(); }); }
            finally { if (c != null) c.disconnect(); }
        });
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
