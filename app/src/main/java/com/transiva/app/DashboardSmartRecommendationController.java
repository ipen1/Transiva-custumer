package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * Owns the smart recommendation card so CustomerDashboardActivity stays a lifecycle/view host.
 * Network work remains bound to the dashboard lifecycle through CustomerLifecycleNetworkScope.
 */
public final class DashboardSmartRecommendationController {
    public interface Host {
        String activeOrderText();
        double balance();
        String location();
        boolean hasActiveOrder();
        String favoriteService();
        void openTrackedService(String service);
    }

    private final Activity activity;
    private final CustomerLifecycleNetworkScope networkScope;
    private final Handler uiHandler;
    private final Host host;

    private LinearLayout card;
    private TextView titleText;
    private TextView messageText;
    private TextView actionText;
    private LottieAnimationView mascot;
    private Runnable action;
    private JSONObject homeFavorite;
    private JSONObject workFavorite;
    private int familyCount = -1;
    private int familyMax = 1;

    public DashboardSmartRecommendationController(Activity activity,
                                                   CustomerLifecycleNetworkScope networkScope,
                                                   Handler uiHandler,
                                                   Host host) {
        this.activity = activity;
        this.networkScope = networkScope;
        this.uiHandler = uiHandler;
        this.host = host;
    }

    public void attach(LinearLayout content) {
        if (content == null || card != null) return;

        // Dashboard Trans Asisten 3.0: mascot-first entry point, not a generic AI card.
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(10), dp(8));
        card.setBackground(Shape.gradient("#061A39", "#0878F9", dp(22)));
        card.setElevation(dp(4));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, dp(112));
        cardLp.setMargins(0, 0, 0, dp(13));
        content.addView(card, cardLp);

        mascot = new LottieAnimationView(activity);
        mascot.setAnimation("trans_assistant_premium.json");
        mascot.setImageAssetsFolder("images/");
        mascot.setRenderMode(com.airbnb.lottie.RenderMode.HARDWARE);
        mascot.setMinAndMaxFrame(0, 59);
        mascot.setRepeatCount(ValueAnimator.INFINITE);
        mascot.setContentDescription("Buka Trans Asisten 3.0");
        mascot.playAnimation();
        LinearLayout.LayoutParams mascotLp = new LinearLayout.LayoutParams(dp(94), dp(94));
        mascotLp.setMargins(0, 0, dp(5), 0);
        card.addView(mascot, mascotLp);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -1, 1);
        copyLp.setMargins(dp(3), 0, 0, 0);
        card.addView(copy, copyLp);

        TextView badge = text("TRANS ASISTEN 3.0  •  AKTIF", 9, "#9CEEFF", true);
        badge.setLetterSpacing(.08f);
        copy.addView(badge);

        titleText = text("Mau kirim barang?", 15, "#FFFFFF", true);
        titleText.setMaxLines(1);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(3), 0, 0);
        copy.addView(titleText, titleLp);

        messageText = text("Tanya saya apa saja — pesan, perjalanan, makanan, lokasi, dan pesanan.", 10, "#D8EDFF", false);
        messageText.setMaxLines(2);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(-1, -2);
        messageLp.setMargins(0, dp(2), 0, dp(5));
        copy.addView(messageText, messageLp);

        actionText = text("Tanya Asisten  ›", 10, "#075EF4", true);
        actionText.setGravity(Gravity.CENTER);
        actionText.setPadding(dp(10), dp(6), dp(10), dp(6));
        actionText.setBackground(Shape.round("#FFFFFF", dp(13)));
        copy.addView(actionText, new LinearLayout.LayoutParams(-2, -2));

        View.OnClickListener openAssistant = v -> openAssistant(currentPrompt());
        card.setOnClickListener(openAssistant);
        mascot.setOnClickListener(openAssistant);
        actionText.setOnClickListener(openAssistant);

        refresh();
        loadFavorites();
        loadFamilyMeta();
    }

    private void openAssistant(String prompt) {
        Intent i = new Intent(activity, TransAssistantActivity.class);
        i.putExtra("ai_prompt", prompt == null ? "" : prompt);
        activity.startActivity(i);
    }

    private String currentPrompt() {
        CharSequence t = titleText == null ? "" : titleText.getText();
        String value = t == null ? "" : t.toString();
        if (value.contains("makan") || value.contains("lapar")) return "Saya lapar";
        if (value.contains("Pulang") || value.contains("pulang")) return "Pulang kantor";
        if (value.contains("Kirim") || value.contains("kirim")) return "Mau kirim barang";
        if (value.contains("pesanan") || value.contains("Pesanan")) return "Cek pesanan";
        if (value.contains("mobil") || value.contains("Mobil")) return "Cari mobil";
        return "";
    }

    public void refresh() {
        if (messageText == null) return;
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        boolean weekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY;

        String title;
        String message;
        String button;
        Runnable nextAction;

        if (host.hasActiveOrder()) {
            title = "Pesanan sedang berjalan";
            message = host.activeOrderText() + ". Pantau aktivitas terbaru pesanan Anda.";
            button = "Pantau ›";
            nextAction = () -> activity.startActivity(new Intent(activity, CustomerHistoryActivity.class));
        } else if (hour >= 5 && hour < 11 && workFavorite != null) {
            title = "Trans Asisten • Berangkat ke Kantor? 🏢";
            message = "Tujuan Kantor sudah tersimpan. Saya bisa isi lokasi jemput Anda otomatis.";
            button = "Motor ›";
            nextAction = () -> openFavorite(workFavorite, false);
        } else if (hour >= 16 && hour < 22 && homeFavorite != null) {
            title = "Trans Asisten • Pulang ke Rumah? 🏠";
            message = "Rumah sudah siap sebagai tujuan. Titik jemput akan mengikuti lokasi Anda sekarang.";
            button = "Motor ›";
            nextAction = () -> openFavorite(homeFavorite, false);
        } else if ((hour >= 22 || hour < 5) && homeFavorite != null) {
            title = "Trans Asisten • Pulang lebih nyaman 🌙";
            message = "Saya siapkan Rumah sebagai tujuan dan TransCar untuk perjalanan malam.";
            button = "Mobil ›";
            nextAction = () -> openFavorite(homeFavorite, true);
        } else if (host.balance() > 0 && host.balance() < 20000) {
            title = "Saldo Transiva Pay menipis";
            message = "Isi saldo sekarang agar pembayaran layanan berikutnya tetap lancar.";
            button = "Top Up ›";
            nextAction = () -> activity.startActivity(new Intent(activity, CustomerTopUpActivity.class));
        } else if (familyCount == 0 && hour >= 14 && hour < 17) {
            title = "Trans Asisten • Family belum diatur 👨‍👩‍👧";
            message = "Anda punya " + familyMax + " slot Family. Tambahkan orang terdekat agar bisa dipesankan perjalanan lebih cepat.";
            button = "Atur Family ›";
            nextAction = () -> activity.startActivity(new Intent(activity, TransivaFamilyActivity.class));
        } else if (weekend && hour >= 8 && hour < 18) {
            title = "Kebutuhan akhir pekan lebih praktis";
            message = "Kirim barang dengan aman dari " + host.location() + " menggunakan TransSend.";
            button = "Kirim ›";
            nextAction = () -> host.openTrackedService("TransSend");
        } else if (hour >= 10 && hour < 14) {
            title = "Waktunya makan siang 🍜";
            message = "Temukan menu favorit dan merchant terdekat lewat TransFood.";
            button = "Pesan ›";
            nextAction = () -> host.openTrackedService("TransFood");
        } else if (hour >= 17 && hour < 21) {
            title = "Perjalanan pulang lebih mudah";
            message = "Pesan TransRide dari " + host.location() + " tanpa perlu menunggu lama.";
            button = "Ride ›";
            nextAction = () -> host.openTrackedService("TransRide");
        } else if (hour >= 21 || hour < 5) {
            title = "Perjalanan malam yang praktis 🌙";
            message = "Gunakan TransCar untuk perjalanan yang lebih nyaman malam ini.";
            button = "TransCar ›";
            nextAction = () -> host.openTrackedService("TransCar");
        } else {
            String favorite = host.favoriteService();
            if (favorite != null && !favorite.isEmpty()) {
                title = "Pilihan favorit Anda ✦";
                message = "Anda cukup sering menggunakan " + favorite + ". Buka lagi layanan favorit Anda dari " + host.location() + ".";
                button = "Buka ›";
                nextAction = () -> host.openTrackedService(favorite);
            } else {
                title = "Trans Asisten siap membantu ✦";
                message = "Tanyakan kebutuhan Anda seperti ‘mau pesan barang’, ‘pulang kantor’, ‘lapar’, atau ‘cari mobil’.";
                button = "Tanya Asisten ›";
                nextAction = () -> { Intent i = new Intent(activity, GlobalSearchActivity.class); i.putExtra("ai_prompt", ""); activity.startActivity(i); };
            }
        }

        // Keep the smart context, but present it as speech beside the animated robot.
        String assistantTitle = title;
        if (host.hasActiveOrder()) assistantTitle = "Mau cek pesanan?";
        else if (hour >= 5 && hour < 11) assistantTitle = "Mau berangkat kerja?";
        else if (hour >= 10 && hour < 14) assistantTitle = "Lapar? Cari makanan?";
        else if (hour >= 16 && hour < 22) assistantTitle = "Mau pulang kantor?";
        else if (weekend && hour >= 8 && hour < 18) assistantTitle = "Mau kirim barang?";
        else if (hour >= 21 || hour < 5) assistantTitle = "Butuh mobil malam ini?";
        else assistantTitle = "Mau kirim barang?";

        titleText.setText(assistantTitle);
        messageText.setText("Tanya saya: “mau kirim barang”, “pulang kantor”, “lapar”, atau kebutuhan lainnya.");
        actionText.setText("Tanya Asisten  ›");
        action = nextAction; // retained for compatibility with existing smart recommendation state.
        card.setAlpha(0f);
        card.animate().alpha(1f).setDuration(280L).start();
    }

    private void loadFavorites() {
        networkScope.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = CustomerApiClient.open(activity, "https://transiva.my.id/server/customer_favorites.php?action=list");
                conn.setRequestMethod("GET");
                JSONObject o = new JSONObject(read(conn));
                JSONArray a = o.optJSONArray("places");
                JSONObject home = null, work = null;
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject x = a.optJSONObject(i); if (x == null) continue;
                    if ("home".equalsIgnoreCase(x.optString("type"))) home = x;
                    else if ("work".equalsIgnoreCase(x.optString("type"))) work = x;
                }
                final JSONObject fHome = home, fWork = work;
                networkScope.post(uiHandler, () -> { homeFavorite = fHome; workFavorite = fWork; refresh(); });
            } catch (Exception ignored) {
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void loadFamilyMeta() {
        networkScope.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = CustomerApiClient.open(activity, "https://transiva.my.id/server/customer_family.php?action=list");
                conn.setRequestMethod("GET");
                JSONObject o = new JSONObject(read(conn));
                final int count = o.optInt("member_count", -1);
                final int max = Math.max(1, o.optInt("max_members", 1));
                networkScope.post(uiHandler, () -> { familyCount = count; familyMax = max; refresh(); });
            } catch (Exception ignored) {
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private String read(HttpURLConnection conn) throws Exception {
        InputStream in = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private void openFavorite(JSONObject place, boolean car) {
        if (place == null) { activity.startActivity(new Intent(activity, FavoritePlacesActivity.class)); return; }
        double lat = place.optDouble("latitude", 0), lng = place.optDouble("longitude", 0);
        if (lat == 0 || lng == 0) { activity.startActivity(new Intent(activity, FavoritePlacesActivity.class)); return; }
        Intent i = new Intent(activity, car ? PassengerCarActivity.class : TransRideActivity.class);
        i.putExtra("smart_favorite", true);
        i.putExtra("smart_destination_lat", lat);
        i.putExtra("smart_destination_lng", lng);
        i.putExtra("smart_destination_address", first(place.optString("address"), place.optString("label"), "Tujuan favorit"));
        i.putExtra("smart_destination_label", first(place.optString("label"), "Tujuan"));
        activity.startActivity(i);
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(value); v.setTextSize(sp); v.setTextColor(android.graphics.Color.parseColor(color));
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return v;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static String first(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        return "";
    }
}
