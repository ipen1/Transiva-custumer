package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(12), dp(12), dp(12));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#D9E9FF", dp(19), 1));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(13));
        content.addView(card, cardLp);

        TextView icon = text("✦", 23, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Shape.round("#EAF4FF", dp(18)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(11), 0, dp(8), 0);
        card.addView(copy, copyLp);

        titleText = text("Trans Asisten", 12, "#0B3A78", true);
        copy.addView(titleText);
        messageText = text("Menyiapkan rekomendasi terbaik untuk Anda...", 11, "#64748B", false);
        messageText.setMaxLines(2);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(-1, -2);
        messageLp.setMargins(0, dp(3), 0, 0);
        copy.addView(messageText, messageLp);

        actionText = text("Lihat ›", 10, "#FFFFFF", true);
        actionText.setGravity(Gravity.CENTER);
        actionText.setPadding(dp(10), dp(8), dp(10), dp(8));
        actionText.setBackground(Shape.round("#0B7CFF", dp(13)));
        card.addView(actionText, new LinearLayout.LayoutParams(-2, -2));

        View.OnClickListener listener = v -> { if (action != null) action.run(); };
        card.setOnClickListener(listener);
        actionText.setOnClickListener(listener);
        refresh();
        loadFavorites();
        loadFamilyMeta();
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

        titleText.setText(title);
        messageText.setText(message);
        actionText.setText(button);
        action = nextAction;
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
