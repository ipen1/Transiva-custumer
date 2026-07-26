package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class TransFoodActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final int TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout page;
    private LinearLayout root;
    private ProgressBar progressBar;

    private final List<JSONObject> restaurants = new ArrayList<>();
    private final List<JSONObject> menus = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final Map<String, Bitmap> imageCache = new HashMap<>();
    private final List<MenuSearchItem> allMenuSearchItems = new ArrayList<>();
    private JSONObject activeRestaurant;
    private String menuSearchQuery = "";
    private String homeSearchQuery = "";
    private LinearLayout homeResultsBox;
    private Runnable homeSearchRunnable;
    private int currentScreen = 0; // 0=home, 1=detail menu, 2=checkout
    private String homeMode = "nearby";

    private int userId = 0;
    private String username = "User";
    private String deliveryMode = "standard";
    private String paymentMethod = "cash";
    private double deliveryFee = 0;
    private double standardFee = 0;
    private double hematFee = 0;
    private double distanceKm = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                getWindow().setNavigationBarContrastEnforced(false);
                getWindow().setStatusBarContrastEnforced(false);
            }
        } catch (Exception ignored) {}
        loadSession();
        buildBase();
        showRestaurantList();
        loadRestaurants();
    }

    private void loadSession() {
        try {
            SessionManager session = new SessionManager(this);
            if (session.isLoggedIn()) {
                username = firstNonEmpty(session.getUsername(), session.getName(), "User");
                try { userId = Integer.parseInt(firstNonEmpty(session.getId(), session.getUserId(), "0")); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception ignored) {}
        try {
            android.content.SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
            username = firstNonEmpty(sp.getString("username", ""), sp.getString("player_username", ""), "User");
            userId = sp.getInt("id", sp.getInt("user_id", 0));
        } catch (Exception ignored) {}
    }

    private void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        page.addView(progressBar, lp);
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private void showRestaurantList() {
        currentScreen = 0;
        root.removeAllViews();
        activeRestaurant = null;
        buildTopBar("Transfood", "Makanan favorit, diantar lebih cepat", true);
        addPremiumHero();
        addHomeSearchBar();
        addQuickMenus();
        addHomeSectionHeader();

        homeResultsBox = new LinearLayout(this);
        homeResultsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(homeResultsBox, new LinearLayout.LayoutParams(-1, -2));
        renderHomeResults();
    }


    private void addPremiumHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(roundGradient("#087BFF", "#0754D8", dp(24)));
        hero.setElevation(dp(5));

        TextView eyebrow = text("TRANSFOOD DELIVERY", 11, "#DDEEFF", true);
        hero.addView(eyebrow);
        TextView title = text("Lapar? Pesan yang enak sekarang.", 22, "#FFFFFF", true);
        title.setPadding(0, dp(5), 0, 0);
        hero.addView(title);
        TextView sub = text("Pilihan restoran terbaik di sekitar kamu", 13, "#EAF4FF", false);
        sub.setPadding(0, dp(6), 0, 0);
        hero.addView(sub);

        TextView badge = text("⚡ Antar cepat  •  Promo setiap hari", 12, "#0754D8", true);
        badge.setPadding(dp(12), dp(8), dp(12), dp(8));
        badge.setBackground(round("#FFFFFF", dp(16)));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.setMargins(0, dp(14), 0, 0);
        hero.addView(badge, badgeLp);
        addWithMargin(hero, 0, 0, 0, dp(14));
    }

    private void addQuickMenus() {
        LinearLayout panel = card();
        panel.setPadding(dp(10), dp(14), dp(10), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addQuickMenu(row, "Terdekat", "ic_food_nearby", "nearby");
        addQuickMenu(row, "Diskon", "ic_food_discount", "discount");
        addQuickMenu(row, "Terbaru", "ic_food_new", "newest");
        addQuickMenu(row, "Best", "ic_food_best", "best");
        addWithMargin(panel, 0, 0, 0, dp(18));
    }

    private void addQuickMenu(LinearLayout row, String label, String drawableName, String mode) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(4), dp(4), dp(4));
        item.setClickable(true);
        item.setBackground(round(homeMode.equals(mode) ? "#EAF4FF" : "#FFFFFF", dp(18)));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int id = getResources().getIdentifier(drawableName, "drawable", getPackageName());
        if (id != 0) icon.setImageResource(id);
        item.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView name = text(label, 12, homeMode.equals(mode) ? "#087BFF" : "#183B66", true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(5), 0, 0);
        item.addView(name, new LinearLayout.LayoutParams(-1, -2));
        item.setOnClickListener(v -> {
            homeMode = mode;
            if ("discount".equals(mode)) homeSearchQuery = "diskon";
            else if ("newest".equals(mode)) homeSearchQuery = "";
            else homeSearchQuery = "";
            showRestaurantList();
        });
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void addHomeSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Pilihan restoran", 18, "#123B6B", true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = text("Lihat semua", 12, "#087BFF", true);
        row.addView(tag);
        addWithMargin(row, 0, 0, 0, dp(10));
    }

    private void addHomeSearchBar() {
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setText(homeSearchQuery);
        search.setTextSize(16);
        search.setHint("Cari makanan atau nama restoran...");
        search.setHintTextColor(Color.parseColor("#94A3B8"));
        search.setTextColor(Color.parseColor("#0F172A"));
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setMinHeight(0);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));
        search.setSelection(search.getText().length());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                homeSearchQuery = s == null ? "" : s.toString();
                if (homeSearchRunnable != null) mainHandler.removeCallbacks(homeSearchRunnable);
                homeSearchRunnable = () -> renderHomeResults();
                mainHandler.postDelayed(homeSearchRunnable, 120);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, 0, 0, dp(16));
        root.addView(search, lp);
    }

    private void renderHomeResults() {
        if (homeResultsBox == null) return;
        homeResultsBox.removeAllViews();

        if (restaurants.isEmpty()) {
            addStatusTo(homeResultsBox, "Memuat restoran...");
            return;
        }

        String q = homeSearchQuery == null ? "" : homeSearchQuery.trim().toLowerCase(Locale.ROOT);

        List<MenuSearchItem> menuHits = new ArrayList<>();
        if (q.length() > 0) {
            for (MenuSearchItem item : allMenuSearchItems) {
                if (contains(item.menuName, q) || contains(item.category, q) || contains(item.description, q) || contains(item.restaurantName, q)) {
                    menuHits.add(item);
                }
            }
        }

        List<JSONObject> restoHits = new ArrayList<>();
        for (JSONObject r : restaurants) {
            if (q.length() == 0 || contains(r.optString("name"), q) || contains(r.optString("address"), q) || contains(r.optString("category"), q)) {
                restoHits.add(r);
            }
        }

        if (q.length() > 0) {
            TextView menuTitle = text("Hasil menu makanan", 15, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, menuTitle, 0, 0, 0, dp(10));

            if (menuHits.isEmpty()) {
                String msg = allMenuSearchItems.isEmpty() ? "Index menu sedang dimuat, coba lanjut ketik atau tunggu sebentar." : "Menu tidak ditemukan untuk: " + homeSearchQuery;
                addStatusTo(homeResultsBox, msg);
            } else {
                int limit = Math.min(menuHits.size(), 30);
                for (int i = 0; i < limit; i++) addMenuSearchCard(homeResultsBox, menuHits.get(i));
                if (menuHits.size() > limit) addStatusTo(homeResultsBox, "+" + (menuHits.size() - limit) + " menu lain. Ketik lebih spesifik agar hasil makin tepat.");
            }

            TextView restoTitle = text("Restoran terkait", 15, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, restoTitle, 0, dp(8), 0, dp(10));
        }

        if ("best".equals(homeMode)) {
            java.util.Collections.sort(restoHits, (a, b) -> Double.compare(b.optDouble("rating", 0), a.optDouble("rating", 0)));
        } else if ("newest".equals(homeMode)) {
            java.util.Collections.reverse(restoHits);
        }

        if (restoHits.isEmpty()) {
            addStatusTo(homeResultsBox, "Restoran tidak ditemukan untuk: " + homeSearchQuery);
        } else {
            addRestaurantGrid(homeResultsBox, restoHits);
        }
    }

    private void addRestaurantGrid(LinearLayout parent, List<JSONObject> data) {
        for (int i = 0; i < data.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            LinearLayout leftCard = createRestaurantCard(data.get(i));
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
            leftLp.setMargins(0, 0, dp(7), dp(14));
            row.addView(leftCard, leftLp);

            if (i + 1 < data.size()) {
                LinearLayout rightCard = createRestaurantCard(data.get(i + 1));
                LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -2, 1);
                rightLp.setMargins(dp(7), 0, 0, dp(14));
                row.addView(rightCard, rightLp);
            } else {
                View empty = new View(this);
                LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(0, 1, 1);
                emptyLp.setMargins(dp(7), 0, 0, 0);
                row.addView(empty, emptyLp);
            }

            parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void addMenuSearchCard(LinearLayout parent, MenuSearchItem item) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setClickable(true);
        card.setOnClickListener(v -> openRestaurantFromSearch(item));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        card.addView(img, new LinearLayout.LayoutParams(dp(76), dp(76)));
        loadImage(img, absoluteUrl(firstNonEmpty(item.image, "assets/no-image.png")));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, 0, 0);
        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        body.addView(text(firstNonEmpty(item.menuName, "Menu"), 16, "#0F172A", true));
        TextView owner = text("Milik restoran: " + firstNonEmpty(item.restaurantName, "Restoran"), 12, "#64748B", false);
        owner.setPadding(0, dp(3), 0, 0);
        body.addView(owner);
        TextView price = text(rupiah(item.price), 14, "#0B7CFF", true);
        price.setPadding(0, dp(6), 0, 0);
        body.addView(price);

        TextView open = text("Buka menu", 11, "#0B7CFF", true);
        open.setPadding(0, dp(5), 0, 0);
        body.addView(open);

        addWithMarginTo(parent, card, 0, 0, 0, dp(10));
    }

    private void openRestaurantFromSearch(MenuSearchItem item) {
        JSONObject r = findRestaurantById(item.restaurantId);
        if (r == null) r = item.restaurant;
        if (r == null) {
            showInfo("Restoran", "Data restoran untuk menu ini belum lengkap.");
            return;
        }
        if (r.optInt("is_open", 1) != 1) {
            showInfo("Restoran Tutup", "Restoran sedang tidak menerima orderan.");
            return;
        }
        activeRestaurant = r;
        cart.clear();
        menuSearchQuery = item.menuName;
        showMenuPage();
        loadMenus(item.restaurantId);
    }

    private JSONObject findRestaurantById(int id) {
        for (JSONObject r : restaurants) if (r.optInt("id", 0) == id) return r;
        return null;
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private LinearLayout createRestaurantCard(JSONObject r) {
        boolean open = r.optInt("is_open", 1) == 1;
        LinearLayout card = card();
        card.setPadding(0, 0, 0, dp(12));
        card.setClickable(true);
        card.setAlpha(open ? 1f : 0.62f);
        card.setOnClickListener(v -> {
            if (!open) {
                showInfo("Restoran Tutup", "Restoran sedang tidak menerima orderan.");
                return;
            }
            activeRestaurant = r;
            cart.clear();
            menuSearchQuery = "";
            showMenuPage();
            loadMenus(r.optInt("id", 0));
        });

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        card.addView(img, new LinearLayout.LayoutParams(-1, dp(104)));
        loadImage(img, absoluteUrl(firstNonEmpty(r.optString("banner"), "assets/default-food.png")));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), dp(10), dp(10), 0);
        card.addView(body);

        TextView name = text(firstNonEmpty(r.optString("name"), "Restoran"), 15, "#0F172A", true);
        name.setMaxLines(2);
        body.addView(name);

        TextView info = text(open ? ("⭐ " + r.optString("rating", "0.0") + " • " + firstNonEmpty(r.optString("duration"), "15 menit")) : "🔴 Tutup", 11, "#64748B", false);
        info.setPadding(0, dp(5), 0, 0);
        body.addView(info);

        TextView badge = text(open ? "Buka" : "Tutup", 10, open ? "#0B7CFF" : "#EF4444", true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(roundStroke(open ? "#EAF4FF" : "#FFF1F2", open ? "#B9DBFF" : "#FECACA", dp(20), 1));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.setMargins(0, dp(8), 0, 0);
        body.addView(badge, badgeLp);

        return card;
    }

    private void showMenuPage() {
        currentScreen = 1;
        root.removeAllViews();
        buildTopBar("Detail Restoran", firstNonEmpty(activeRestaurant != null ? activeRestaurant.optString("name") : "", "Menu makanan"), true);
        if (menus.isEmpty()) addStatus("Memuat menu...");
        else renderMenus();
    }

    private void renderMenus() {
        root.removeViews(1, Math.max(0, root.getChildCount() - 1));

        LinearLayout resto = card();
        resto.setPadding(dp(16), dp(14), dp(16), dp(14));
        resto.addView(text(firstNonEmpty(activeRestaurant.optString("name"), "Restoran"), 19, "#0B3A78", true));
        resto.addView(text((hasRestoLocation() ? "📍 Lokasi resto tersedia" : "⚠️ Lokasi resto belum tersedia"), 13, "#64748B", false));
        if (menuSearchQuery != null && menuSearchQuery.trim().length() > 0) {
            TextView fromSearch = text("Hasil pencarian: " + menuSearchQuery, 12, "#0B7CFF", true);
            fromSearch.setPadding(0, dp(8), 0, 0);
            resto.addView(fromSearch);
        }
        addWithMargin(resto, 0, 0, 0, dp(14));

        int shown = 0;
        String q = menuSearchQuery == null ? "" : menuSearchQuery.trim().toLowerCase(Locale.ROOT);
        for (JSONObject m : menus) {
            String name = firstNonEmpty(m.optString("name"), "").toLowerCase(Locale.ROOT);
            String category = firstNonEmpty(m.optString("category"), "").toLowerCase(Locale.ROOT);
            String desc = firstNonEmpty(m.optString("description"), "").toLowerCase(Locale.ROOT);
            if (q.length() == 0 || name.contains(q) || category.contains(q) || desc.contains(q)) {
                addMenuCard(m);
                shown++;
            }
        }

        if (shown == 0) addStatus("Menu tidak ditemukan untuk: " + menuSearchQuery);

        buildCartBar();
    }

    private void addMenuCard(JSONObject m) {
        boolean active = m.optInt("is_active", 1) == 1;
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setAlpha(active ? 1f : 0.55f);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        card.addView(img, new LinearLayout.LayoutParams(dp(92), dp(92)));
        loadImage(img, absoluteUrl(firstNonEmpty(m.optString("image"), "assets/no-image.png")));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, 0, 0);
        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(text(firstNonEmpty(m.optString("name"), "Menu"), 16, "#0F172A", true));
        body.addView(text(firstNonEmpty(m.optString("category"), "Menu"), 12, "#94A3B8", false));
        TextView price = text(rupiah(m.optDouble("price", 0)), 15, "#0B7CFF", true);
        price.setPadding(0, dp(6), 0, dp(6));
        body.addView(price);
        if (!active) {
            body.addView(text("Tidak tersedia", 12, "#EF4444", true));
        } else {
            LinearLayout qty = new LinearLayout(this);
            qty.setGravity(Gravity.CENTER_VERTICAL);
            Button minus = tinyButton("−");
            Button plus = tinyButton("+");
            TextView value = text(String.valueOf(getQty(m.optInt("id", 0))), 15, "#0F172A", true);
            value.setGravity(Gravity.CENTER);
            value.setIncludeFontPadding(false);
            qty.addView(minus, new LinearLayout.LayoutParams(dp(40), dp(40)));
            qty.addView(value, new LinearLayout.LayoutParams(dp(46), dp(40)));
            qty.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
            body.addView(qty);
            minus.setOnClickListener(v -> { changeQty(m, -1); renderMenus(); });
            plus.setOnClickListener(v -> { changeQty(m, 1); renderMenus(); });
        }
        addWithMargin(card, 0, 0, 0, dp(12));
    }

    private void buildCartBar() {
        if (cart.isEmpty()) return;
        LinearLayout bar = card();
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("Total Belanja", 12, "#64748B", false));
        left.addView(text(rupiah(foodTotal()), 20, "#0B3A78", true));
        bar.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        Button checkout = primaryButton("Checkout");
        checkout.setOnClickListener(v -> showCheckout());
        bar.addView(checkout, new LinearLayout.LayoutParams(dp(130), dp(48)));
        addWithMargin(bar, 0, dp(8), 0, 0);
    }

    private void showCheckout() {
        currentScreen = 2;
        root.removeAllViews();
        buildTopBar("Checkout", "Cek pesanan dan pilih pengantaran", true);
        addStatus("Menghitung ongkir...");
        calculateOngkirThenRender();
    }

    private void renderCheckout() {
        root.removeViews(1, Math.max(0, root.getChildCount() - 1));
        LinearLayout info = card();
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        info.addView(text("Resto", 12, "#64748B", true));
        info.addView(text(firstNonEmpty(activeRestaurant.optString("name"), "Restoran"), 16, "#0F172A", true));
        TextView jarak = text(distanceKm > 0 ? ("Jarak pengantaran: " + String.format(Locale.US, "%.2f", distanceKm) + " km") : "Lokasi belum lengkap, ongkir belum bisa dihitung", 13, distanceKm > 0 ? "#64748B" : "#EF4444", false);
        jarak.setPadding(0, dp(8), 0, 0);
        info.addView(jarak);
        addWithMargin(info, 0, 0, 0, dp(14));

        for (CartItem item : cart) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(item.name, 15, "#0F172A", true));
            col.addView(text(item.qty + " x " + rupiah(item.price), 12, "#64748B", false));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(rupiah(item.price * item.qty), 14, "#0B7CFF", true));
            addWithMargin(row, 0, 0, 0, dp(10));
        }

        LinearLayout delivery = card();
        delivery.setPadding(dp(14), dp(14), dp(14), dp(14));
        delivery.addView(text("Pilih Pengantaran", 16, "#0B3A78", true));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        Button standard = choiceButton("Standar\n" + rupiah(standardFee), "standard".equals(deliveryMode));
        Button hemat = choiceButton("Hemat\n" + rupiah(hematFee), "hemat".equals(deliveryMode));
        standard.setOnClickListener(v -> { deliveryMode = "standard"; deliveryFee = standardFee; renderCheckout(); });
        hemat.setOnClickListener(v -> { deliveryMode = "hemat"; deliveryFee = hematFee; renderCheckout(); });
        row.addView(standard, new LinearLayout.LayoutParams(0, dp(60), 1));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(60), 1); hlp.setMargins(dp(10),0,0,0);
        row.addView(hemat, hlp);
        delivery.addView(row);
        addWithMargin(delivery, 0, 0, 0, dp(14));

        LinearLayout pay = card();
        pay.setPadding(dp(14), dp(14), dp(14), dp(14));
        pay.addView(text("Pilih Pembayaran", 16, "#0B3A78", true));
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        prow.setPadding(0, dp(10), 0, 0);
        Button cash = choiceButton("Tunai", "cash".equals(paymentMethod));
        Button balance = choiceButton("Saldo", "balance".equals(paymentMethod));
        cash.setOnClickListener(v -> { paymentMethod = "cash"; renderCheckout(); });
        balance.setOnClickListener(v -> { paymentMethod = "balance"; renderCheckout(); });
        prow.addView(cash, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(52), 1); blp.setMargins(dp(10),0,0,0);
        prow.addView(balance, blp);
        pay.addView(prow);
        addWithMargin(pay, 0, 0, 0, dp(14));

        LinearLayout total = card();
        total.setPadding(dp(16), dp(14), dp(16), dp(14));
        total.addView(summaryLine("Total makanan", foodTotal()));
        total.addView(summaryLine("Ongkir", deliveryFee));
        total.addView(summaryLine("Total bayar", foodTotal() + deliveryFee));
        Button order = primaryButton("Buat Pesanan");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, dp(52)); olp.setMargins(0, dp(12), 0, 0);
        total.addView(order, olp);
        order.setOnClickListener(v -> createFoodOrder());
        addWithMargin(total, 0, 0, 0, dp(20));
    }

    private LinearLayout summaryLine(String label, double value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 14, "#64748B", false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(rupiah(value), 15, "#0F172A", true));
        return row;
    }

    private void loadRestaurants() {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_food_restaurants.php?v=" + System.currentTimeMillis());
                restaurants.clear();
                JSONArray arr = res.optJSONArray("restaurants");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) restaurants.add(arr.getJSONObject(i));
                    mainHandler.post(() -> { setLoading(false); showRestaurantList(); loadAllMenuIndex(); });
                } else throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat restoran"));
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); root.removeAllViews(); buildTopBar("Trans Food", "", true); addStatus("Koneksi gagal memuat restoran"); showInfo("Gagal", e.getMessage()); });
            }
        }).start();
    }

    private void loadAllMenuIndex() {
        new Thread(() -> {
            try {
                List<MenuSearchItem> fresh = new ArrayList<>();
                for (JSONObject r : restaurants) {
                    int rid = r.optInt("id", 0);
                    if (rid <= 0) continue;
                    try {
                        JSONObject res = getJson(BASE_URL + "server/get_food_menus.php?restaurant_id=" + rid + "&v=" + System.currentTimeMillis());
                        JSONArray arr = res.optJSONArray("menus");
                        if (arr == null) continue;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject m = arr.getJSONObject(i);
                            MenuSearchItem item = new MenuSearchItem();
                            item.restaurantId = rid;
                            item.restaurantName = firstNonEmpty(r.optString("name"), "Restoran");
                            item.restaurant = r;
                            item.menuId = m.optInt("id", 0);
                            item.menuName = firstNonEmpty(m.optString("name"), "Menu");
                            item.category = firstNonEmpty(m.optString("category"), "Menu");
                            item.description = firstNonEmpty(m.optString("description"), "");
                            item.image = firstNonEmpty(m.optString("image"), "assets/no-image.png");
                            item.price = m.optDouble("price", 0);
                            item.active = m.optInt("is_active", 1) == 1;
                            if (item.active) fresh.add(item);
                        }
                    } catch (Exception ignored) {}
                }
                mainHandler.post(() -> {
                    allMenuSearchItems.clear();
                    allMenuSearchItems.addAll(fresh);
                    if (activeRestaurant == null) renderHomeResults();
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void loadMenus(int restaurantId) {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_food_menus.php?restaurant_id=" + restaurantId + "&v=" + System.currentTimeMillis());
                menus.clear();
                if (res.optJSONObject("restaurant") != null) activeRestaurant = res.optJSONObject("restaurant");
                JSONArray arr = res.optJSONArray("menus");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) menus.add(arr.getJSONObject(i));
                    mainHandler.post(() -> { setLoading(false); showMenuPage(); });
                } else throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat menu"));
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showMenuPage(); addStatus("Gagal memuat menu"); showInfo("Gagal", e.getMessage()); });
            }
        }).start();
    }

    private void calculateOngkirThenRender() {
        setLoading(true);
        new Thread(() -> {
            try {
                String url = BASE_URL + "server/calculateOngkir.php?service_type=Transbike" +
                        "&restaurant_id=" + Uri.encode(String.valueOf(activeRestaurant.optInt("id", 0))) +
                        "&user_id=" + Uri.encode(String.valueOf(userId)) +
                        "&delivery_mode=" + Uri.encode(deliveryMode) +
                        "&v=" + System.currentTimeMillis();
                JSONObject res = getJson(url);
                if (!res.optBoolean("success", false)) throw new Exception(firstNonEmpty(res.optString("message"), "Gagal menghitung ongkir"));
                deliveryFee = res.optDouble("price", 0);
                standardFee = res.optDouble("standard_price", deliveryFee);
                hematFee = res.optDouble("hemat_price", deliveryFee);
                distanceKm = res.optDouble("distance_km", 0);
                mainHandler.post(() -> { setLoading(false); renderCheckout(); });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); deliveryFee = standardFee = hematFee = 0; distanceKm = 0; renderCheckout(); showInfo("Ongkir", e.getMessage()); });
            }
        }).start();
    }

    private void createFoodOrder() {
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (activeRestaurant == null || activeRestaurant.optInt("id", 0) <= 0) { showInfo("Gagal", "Restoran tidak valid."); return; }
        if (cart.isEmpty()) { showInfo("Keranjang kosong", "Tambahkan menu terlebih dahulu."); return; }
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("restaurant_id", activeRestaurant.optInt("id", 0));
                payload.put("delivery_mode", deliveryMode);
                payload.put("payment_method", paymentMethod);
                JSONArray items = new JSONArray();
                for (CartItem c : cart) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.id);
                    o.put("qty", c.qty);
                    items.put(o);
                }
                payload.put("items", items);
                JSONObject res = postJson(BASE_URL + "server/create_food_order.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Pesanan berhasil dibuat" : "Gagal membuat pesanan");
                mainHandler.post(() -> {
                    setLoading(false);
                    if (ok) {
                        new AlertDialog.Builder(this)
                                .setTitle("Berhasil")
                                .setMessage(msg + "\n\nOrder ID: " + res.optString("order_id", "-"))
                                .setPositiveButton("OK", (d, w) -> finish())
                                .show();
                    } else showInfo("Gagal", msg);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showInfo("Error", "Koneksi gagal membuat pesanan makanan."); });
            }
        }).start();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("GET");
        return new JSONObject(readStream(c));
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        os.flush(); os.close();
        return new JSONObject(readStream(c));
    }

    private String readStream(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect();
        return sb.toString();
    }

    private void loadImage(ImageView view, String urlText) {
        String finalUrl = firstNonEmpty(urlText, "");
        view.setTag(finalUrl);

        Bitmap cached = imageCache.get(finalUrl);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        // Jangan kosongkan ImageView saat render ulang qty, supaya gambar tidak kedip.
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(finalUrl).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                Bitmap bm = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bm != null) imageCache.put(finalUrl, bm);
                mainHandler.post(() -> {
                    Object tag = view.getTag();
                    if (bm != null && tag != null && finalUrl.equals(tag.toString())) {
                        view.setImageBitmap(bm);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private String absoluteUrl(String value) {
        value = firstNonEmpty(value, "assets/no-image.png").trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("/")) return BASE_URL.substring(0, BASE_URL.length() - 1) + value;
        return BASE_URL + value;
    }

    private void changeQty(JSONObject menu, int delta) {
        int id = menu.optInt("id", 0);
        CartItem found = null;
        for (CartItem c : cart) if (c.id == id) found = c;
        if (found == null && delta > 0) {
            found = new CartItem(); found.id = id; found.restaurantId = menu.optInt("restaurant_id", activeRestaurant.optInt("id", 0));
            found.name = firstNonEmpty(menu.optString("name"), "Menu"); found.price = menu.optDouble("price", 0); found.qty = 0; cart.add(found);
        }
        if (found != null) {
            found.qty += delta;
            if (found.qty <= 0) cart.remove(found);
        }
    }

    private int getQty(int id) { for (CartItem c : cart) if (c.id == id) return c.qty; return 0; }
    private double foodTotal() { double t = 0; for (CartItem c : cart) t += c.price * c.qty; return t; }
    private boolean hasRestoLocation() { return activeRestaurant != null && activeRestaurant.optString("latitude").length() > 0 && activeRestaurant.optString("longitude").length() > 0; }

    private void buildTopBar(String title, String sub, boolean back) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(16));
        if (back) {
            TextView b = text("‹", 34, "#0B3A78", true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(round("#FFFFFF", dp(18)));
            b.setOnClickListener(v -> handleBack());
            row.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text(title, 23, "#0B3A78", true));
        if (sub != null && sub.length() > 0) col.addView(text(sub, 12, "#64748B", false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void handleBack() {
        if (currentScreen == 2) {
            showMenuPage();
            return;
        }

        if (currentScreen == 1 || activeRestaurant != null) {
            activeRestaurant = null;
            menus.clear();
            cart.clear();
            menuSearchQuery = "";
            showRestaurantList();
            return;
        }

        finish();
    }

    @Override public void onBackPressed() { handleBack(); }

    private void addStatus(String message) {
        TextView t = text(message, 14, "#64748B", false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(24), dp(16), dp(24));
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));
        addWithMargin(t, 0, 0, 0, dp(12));
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(roundStroke("#FFFFFF", "#E2ECF8", dp(22), 1));
        v.setElevation(dp(2));
        return v;
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }

    private Button primaryButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round("#0B7CFF", dp(18))); return b; }
    private Button tinyButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(20);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, dp(1));
        b.setBackground(roundStroke("#EAF4FF", "#B9DBFF", dp(15), 1));
        return b;
    }
    private Button choiceButton(String s, boolean active) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#0B3A78")); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(roundStroke(active ? "#0B7CFF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(18), 1)); return b; }

    private GradientDrawable round(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) { GradientDrawable g = round(color, radius); g.setStroke(dp(sw), Color.parseColor(stroke)); return g; }
    private GradientDrawable roundGradient(String c1, String c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(c1), Color.parseColor(c2)}); g.setCornerRadius(radius); return g; }

    private void addWithMargin(View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); root.addView(v, lp); }
    private void addWithMarginTo(LinearLayout parent, View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); parent.addView(v, lp); }
    private void addStatusTo(LinearLayout parent, String message) { TextView t = text(message, 14, "#64748B", false); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(20), dp(16), dp(20)); t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1)); addWithMarginTo(parent, t, 0, 0, 0, dp(12)); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private void showInfo(String title, String msg) { try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(double v) { return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) v); }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }

    private static class MenuSearchItem { int restaurantId; int menuId; String restaurantName; String menuName; String category; String description; String image; double price; boolean active; JSONObject restaurant; }
    private static class CartItem { int id; int restaurantId; String name; double price; int qty; }
}