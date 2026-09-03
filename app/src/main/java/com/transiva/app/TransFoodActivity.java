package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
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

public class TransFoodActivity extends Activity {

    private final CustomerFeatureRuntimeController featureRuntime = new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.FOOD);

    private static final String BASE_URL = ApiConfig.ROOT;
    private static final int TIMEOUT_MS = 20000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout page;
    private LinearLayout root;
    private ProgressBar progressBar;
    private View stickyCartBar;

    private final List<JSONObject> restaurants = new ArrayList<>();
    private final List<JSONObject> menus = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
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
    private String voucherCode = "";
    private double deliveryFee = 0;
    private double standardFee = 0;
    private double hematFee = 0;
    private double distanceKm = 0;
    private int hematRemaining = 0; // saldo coin
    private int hematLimit = 1000; // minimum redeem coin
    private int coinValueRupiah = 1;
    private int coinMinOrderAfterDiscount = 1000;
    private String hematTier = "BRONZE";
    private final Runnable realtimeFoodRefresh = new Runnable() { @Override public void run() { if (!isFinishing()) { if (activeRestaurant == null) loadRestaurants(false); else loadMenus(activeRestaurant.optInt("id",0), false); mainHandler.postDelayed(this, CustomerPerformanceManager.pollingBackground(TransFoodActivity.this, 30000L)); } } };

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
        if (getIntent() != null) homeSearchQuery = firstNonEmpty(getIntent().getStringExtra("global_search_query"), "");
        buildBase();
        CustomerBestOffer.load(this, "TransFood", offer -> featureRuntime.post(mainHandler, () -> {
            if (offer != null && (voucherCode == null || voucherCode.trim().isEmpty())) {
                String code = offer.optString("promo_code", "").trim();
                if (!code.isEmpty()) voucherCode = code;
            }
        }));
        showRestaurantList();
        loadRestaurants(true);
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
        clearStickyCartBar();
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
        TextView sub = text("Pilihan merchant terbaik di sekitar kamu", 13, "#EAF4FF", false);
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
            homeSearchQuery = "";
            showRestaurantList();
        });
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void addHomeSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Pilihan merchant", 18, "#123B6B", true);
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
        search.setHint("Cari makanan atau nama merchant...");
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
            addStatusTo(homeResultsBox, "Memuat merchant...");
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

        if (q.length() == 0 && !allMenuSearchItems.isEmpty()) {
            List<MenuSearchItem> smart = new ArrayList<>(allMenuSearchItems);
            java.util.Collections.sort(smart, (a,b) -> Double.compare(smartMenuScore(b), smartMenuScore(a)));
            TextView smartTitle = text("✨ Pilihan pintar untuk kamu", 16, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, smartTitle, 0, 0, 0, dp(4));
            TextView smartSub = text("Terlaris • terbaru • rating • jarak hingga 15 km", 11, "#64748B", false);
            addWithMarginTo(homeResultsBox, smartSub, 0, 0, 0, dp(10));
            int smartLimit = Math.min(8, smart.size());
            for (int i=0;i<smartLimit;i++) addMenuSearchCard(homeResultsBox, smart.get(i));
            TextView restoTitleSmart = text("Merchant di sekitar titik antar", 16, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, restoTitleSmart, 0, dp(8), 0, dp(10));
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

            TextView restoTitle = text("Merchant terkait", 15, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, restoTitle, 0, dp(8), 0, dp(10));
        }

        if ("best".equals(homeMode)) {
            java.util.Collections.sort(restoHits, (a, b) -> {
                double sa = a.optDouble("rating",0) * Math.log10(10 + a.optInt("review_count",0));
                double sb = b.optDouble("rating",0) * Math.log10(10 + b.optInt("review_count",0));
                return Double.compare(sb, sa);
            });
        } else if ("newest".equals(homeMode)) {
            java.util.Collections.sort(restoHits, (a,b) -> Integer.compare(b.optInt("id",0), a.optInt("id",0)));
        } else if ("discount".equals(homeMode)) {
            java.util.Collections.sort(restoHits, (a,b) -> Boolean.compare(b.optBoolean("has_food_promo",false), a.optBoolean("has_food_promo",false)));
        }

        if (restoHits.isEmpty()) {
            addStatusTo(homeResultsBox, "Merchant tidak ditemukan untuk: " + homeSearchQuery);
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
        TextView owner = text("Milik merchant: " + firstNonEmpty(item.restaurantName, "Merchant") + " • " + Math.max(0,(int)Math.round(item.restaurantDistanceKm)) + " km", 12, "#64748B", false);
        owner.setPadding(0, dp(3), 0, 0);
        body.addView(owner);
        if (item.discountActive) { TextView old=text(rupiah(item.originalPrice),11,"#94A3B8",false);old.setPaintFlags(old.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);body.addView(old);body.addView(text("🏷️ -"+String.format(Locale.US,"%.0f%%",item.discountPercent)+" merchant",11,"#C2410C",true)); }
        TextView price = text(rupiah(item.price), 14, "#0B7CFF", true);
        price.setPadding(0, dp(4), 0, 0);
        body.addView(price);
        if (item.trackStock) {
            TextView stock = text(item.stock <= 0 ? "Habis" : ("Stok " + item.stock), 11, item.stock <= 0 ? "#EF4444" : "#16A34A", true);
            stock.setPadding(0, dp(3), 0, 0);
            body.addView(stock);
        }

        TextView open = text("Buka menu", 11, "#0B7CFF", true);
        open.setPadding(0, dp(5), 0, 0);
        body.addView(open);

        addWithMarginTo(parent, card, 0, 0, 0, dp(10));
    }

    private double smartMenuScore(MenuSearchItem item) {
        if (item == null) return 0;
        double popularity = Math.log10(1d + Math.max(0,item.salesCount)) * 42d;
        double rating = Math.max(0d, Math.min(5d,item.restaurantRating)) * 14d;
        double distance = Math.max(0d, 15d - Math.max(0d,item.restaurantDistanceKm)) * 3.2d;
        double newest = Math.min(18d, Math.log10(10d + Math.max(0,item.menuId)) * 3d);
        double discount = item.discountActive ? Math.min(12d, item.discountPercent * 0.4d) : 0d;
        return popularity + rating + distance + newest + discount;
    }

    private void openRestaurantFromSearch(MenuSearchItem item) {
        JSONObject r = findRestaurantById(item.restaurantId);
        if (r == null) r = item.restaurant;
        if (r == null) {
            showInfo("Merchant", "Data merchant untuk menu ini belum lengkap.");
            return;
        }
        if (r.optInt("is_open", 1) != 1) {
            showInfo("Merchant Tutup", "Merchant sedang tidak menerima orderan.");
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
                showInfo("Merchant Tutup", "Merchant sedang tidak menerima orderan.");
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

        TextView name = text(firstNonEmpty(r.optString("name"), "Merchant"), 15, "#0F172A", true);
        name.setMaxLines(2);
        body.addView(name);

        String availabilityReason = firstNonEmpty(r.optString("availability_reason"), open ? "" : "Merchant sedang tidak menerima pesanan");
        String todayHours = firstNonEmpty(r.optString("today_hours"), "");
        TextView info = text(open ? ("⭐ " + r.optString("rating", "0.0") + " (" + r.optInt("review_count",0) + ") • " + firstNonEmpty(r.optString("duration"), "15 menit")) : ("🔴 " + availabilityReason), 11, "#64748B", false);
        info.setPadding(0, dp(5), 0, 0);
        body.addView(info);
        if (!r.isNull("distance_rounded_km")) {
            int roundedKm = Math.max(0, r.optInt("distance_rounded_km", 0));
            TextView dist = text("📍 " + roundedKm + " km dari titik antar", 11, "#475569", true);
            dist.setPadding(0, dp(4), 0, 0); body.addView(dist);
        }
        if (!todayHours.isEmpty()) {
            TextView hours = text("🕒 " + todayHours, 10, "#64748B", false);
            hours.setPadding(0, dp(4), 0, 0);
            body.addView(hours);
        }

        if (r.optBoolean("has_food_promo", false)) {
            TextView promo = text("💸 " + firstNonEmpty(r.optString("promo_label"), "Promo tersedia"), 10, "#B45309", true);
            promo.setPadding(0, dp(6), 0, 0);
            body.addView(promo);
        }
        if (r.optBoolean("has_merchant_discount", false)) {
            TextView promo = text("🏷️ " + firstNonEmpty(r.optString("merchant_discount_label"), "Diskon merchant"), 10, "#C2410C", true);
            promo.setPadding(0, dp(5), 0, 0); body.addView(promo);
        }
        if (r.optInt("review_count",0) >= 10 && r.optDouble("rating",0) >= 4.5) {
            TextView hot = text("🔥 Favorit customer", 10, "#DC2626", true);
            hot.setPadding(0, dp(4), 0, 0);
            body.addView(hot);
        }

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
        buildTopBar("Detail Merchant", firstNonEmpty(activeRestaurant != null ? activeRestaurant.optString("name") : "", "Menu makanan"), true);
        if (menus.isEmpty()) addStatus("Memuat menu...");
        else renderMenus();
    }

    private void renderMenus() {
        root.removeViews(1, Math.max(0, root.getChildCount() - 1));

        LinearLayout resto = card();
        resto.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView merchantBanner = new ImageView(this);
        merchantBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        merchantBanner.setBackgroundColor(Color.parseColor("#EAF4FF"));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, dp(150));
        bannerLp.setMargins(0, 0, 0, dp(12));
        resto.addView(merchantBanner, bannerLp);
        loadImage(merchantBanner, absoluteUrl(firstNonEmpty(activeRestaurant.optString("banner"), "assets/no-image.png")));

        LinearLayout merchantTitleRow = new LinearLayout(this);
        merchantTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView merchantName = text(firstNonEmpty(activeRestaurant.optString("name"), "Merchant"), 19, "#0B3A78", true);
        merchantTitleRow.addView(merchantName, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton merchantFav = favoriteButton(activeRestaurant.optBoolean("is_favorite"));
        merchantFav.setContentDescription("Favoritkan merchant");
        merchantFav.setOnClickListener(v -> toggleFavorite("merchant", activeRestaurant.optInt("id", 0), activeRestaurant, merchantFav));
        merchantTitleRow.addView(merchantFav, new LinearLayout.LayoutParams(dp(46), dp(46)));
        resto.addView(merchantTitleRow);

        double socialRating = activeRestaurant.optDouble("social_rating", activeRestaurant.optDouble("rating", 0));
        int socialReviews = activeRestaurant.optInt("social_review_count", activeRestaurant.optInt("review_count", 0));
        TextView merchantRating = text("⭐ " + String.format(Locale.US, "%.1f", socialRating) + " • " + socialReviews + " penilaian customer  ›", 12, "#64748B", true);
        merchantRating.setPadding(0, dp(7), 0, dp(2));
        merchantRating.setOnClickListener(v -> showMerchantReviews(activeRestaurant));
        resto.addView(merchantRating);
        boolean accepting = activeRestaurant.optInt("is_open", 1) == 1;
        String reason = firstNonEmpty(activeRestaurant.optString("availability_reason"), accepting ? "Menerima pesanan" : "Merchant sedang tidak menerima pesanan");
        TextView operational = text(accepting ? "🟢 Menerima pesanan" : ("🔴 " + reason), 13, accepting ? "#16A34A" : "#EF4444", true);
        operational.setPadding(0, dp(5), 0, 0);
        resto.addView(operational);
        String todayHours = firstNonEmpty(activeRestaurant.optString("today_hours"), "");
        if (!todayHours.isEmpty()) {
            TextView hours = text("🕒 Jam hari ini: " + todayHours, 12, "#64748B", false);
            hours.setPadding(0, dp(4), 0, 0);
            resto.addView(hours);
        }
        resto.addView(text((hasRestoLocation() ? "📍 Lokasi merchant tersedia" : "⚠️ Lokasi merchant belum tersedia"), 13, "#64748B", false));
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
        boolean trackStock = m.optInt("track_stock", 0) == 1;
        int stock = m.optInt("stock", -1);
        if (trackStock && stock <= 0) active = false;

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

        String desc = firstNonEmpty(m.optString("description"), "");
        if (!desc.isEmpty()) {
            TextView d = text(desc, 12, "#64748B", false);
            d.setMaxLines(3);
            d.setPadding(0, dp(4), 0, 0);
            body.addView(d);
        }

        boolean merchantDiscount = m.optInt("discount_active",0) == 1 && m.optDouble("discount_percent",0) > 0;
        if (merchantDiscount) {
            TextView oldPrice = text(rupiah(m.optDouble("original_display_price", m.optDouble("original_price", m.optDouble("price",0)))), 12, "#94A3B8", false);
            oldPrice.setPaintFlags(oldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); oldPrice.setPadding(0,dp(5),0,0); body.addView(oldPrice);
            TextView disc = text("🏷️ Diskon merchant " + String.format(Locale.US,"%.0f%%",m.optDouble("discount_percent",0)), 11, "#C2410C", true); body.addView(disc);
        }
        TextView price = text(rupiah(m.optDouble("price", 0)), 15, "#0B7CFF", true);
        price.setPadding(0, dp(merchantDiscount?2:6), 0, dp(4));
        body.addView(price);

        LinearLayout socialRow = new LinearLayout(this);
        socialRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton fav = favoriteButton(m.optBoolean("is_favorite"));
        fav.setContentDescription("Favoritkan menu");
        fav.setOnClickListener(v -> toggleFavorite("menu", m.optInt("id", 0), m, fav));
        socialRow.addView(fav, new LinearLayout.LayoutParams(dp(44), dp(40)));
        Button review = choiceButton("⭐ " + String.format(Locale.US, "%.1f", m.optDouble("rating", 0)) + " (" + m.optInt("review_count", 0) + ")", false);
        LinearLayout.LayoutParams reviewLp = new LinearLayout.LayoutParams(-2, dp(40));
        reviewLp.setMargins(dp(8), 0, 0, 0);
        socialRow.addView(review, reviewLp);
        review.setOnClickListener(v -> showMenuReviews(m));
        body.addView(socialRow);

        if (trackStock) {
            String stockText = stock <= 0 ? "Stok habis" : ("Stok tersisa: " + stock);
            TextView st = text(stockText, 12, stock <= 0 ? "#EF4444" : (stock <= 5 ? "#D97706" : "#16A34A"), true);
            st.setPadding(0, 0, 0, dp(4));
            body.addView(st);
        }

        JSONArray options = m.optJSONArray("options");
        if (hasSelectableOptions(options)) {
            TextView opt = text(buildOptionPreview(options), 11, "#7C3AED", true);
            opt.setPadding(0, dp(2), 0, dp(5));
            body.addView(opt);
        }

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

            minus.setOnClickListener(v -> {
                removeOneFromCart(m.optInt("id", 0));
                renderMenus();
            });
            plus.setOnClickListener(v -> {
                int currentQty = getQty(m.optInt("id", 0));
                if (trackStock && currentQty >= stock) {
                    showInfo("Stok tidak cukup", "Stok " + firstNonEmpty(m.optString("name"), "menu") + " tersisa " + stock + ".");
                    return;
                }
                if (hasSelectableOptions(options)) showOptionPicker(m);
                else {
                    addConfiguredItem(m, new JSONArray(), "", 0);
                    renderMenus();
                }
            });
        }
        addWithMargin(card, 0, 0, 0, dp(12));
    }

    private boolean hasSelectableOptions(JSONArray groups) {
        if (groups == null) return false;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null) continue;
            JSONArray items = g.optJSONArray("items");
            if (items == null || items.length() == 0) continue;
            String type = firstNonEmpty(g.optString("type"), "option");
            // Varian tunggal Regular + Rp0 adalah default, jadi langsung masuk keranjang.
            if ("variant".equalsIgnoreCase(type) && items.length() == 1) {
                JSONObject only = items.optJSONObject(0);
                if (only != null && ("regular".equalsIgnoreCase(firstNonEmpty(only.optString("name"), "regular")) || "reguler".equalsIgnoreCase(firstNonEmpty(only.optString("name"), "regular")))
                        && only.optDouble("price", 0) <= 0) continue;
            }
            return true;
        }
        return false;
    }

    private String buildOptionPreview(JSONArray groups) {
        if (groups == null) return "";
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null) continue;
            JSONArray items = g.optJSONArray("items");
            if (items == null || items.length() == 0) continue;
            labels.add(firstNonEmpty(g.optString("label"), g.optString("type"), "Pilihan"));
        }
        return labels.isEmpty() ? "" : ("Pilihan: " + android.text.TextUtils.join(" • ", labels));
    }

    private void showOptionPicker(JSONObject menu) {
        JSONArray groups = menu.optJSONArray("options");
        if (!hasSelectableOptions(groups)) {
            addConfiguredItem(menu, new JSONArray(), "", 0);
            renderMenus();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(4), dp(8), 0);
        final List<OptionSelection> controls = new ArrayList<>();

        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            JSONArray items = group.optJSONArray("items");
            if (items == null || items.length() == 0) continue;

            String type = firstNonEmpty(group.optString("type"), "option");
            String label = firstNonEmpty(group.optString("label"), "Pilihan");
            TextView title = text(label + ("variant".equalsIgnoreCase(type) ? " • pilih satu" : " • boleh lebih dari satu"), 14, "#0B3A78", true);
            title.setPadding(0, dp(8), 0, dp(4));
            box.addView(title);

            OptionSelection selection = new OptionSelection();
            selection.type = type;
            selection.label = label;
            selection.items = items;

            if ("variant".equalsIgnoreCase(type)) {
                RadioGroup rg = new RadioGroup(this);
                rg.setOrientation(RadioGroup.VERTICAL);
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.optJSONObject(j);
                    if (item == null) continue;
                    RadioButton rb = new RadioButton(this);
                    rb.setText(optionLabel(item));
                    rb.setTag(j);
                    rg.addView(rb);
                    if (j == 0) rb.setChecked(true);
                }
                selection.radioGroup = rg;
                box.addView(rg);
            } else {
                LinearLayout checks = new LinearLayout(this);
                checks.setOrientation(LinearLayout.VERTICAL);
                for (int j = 0; j < items.length(); j++) {
                    JSONObject item = items.optJSONObject(j);
                    if (item == null) continue;
                    CheckBox cb = new CheckBox(this);
                    cb.setText(optionLabel(item));
                    cb.setTag(j);
                    checks.addView(cb);
                }
                selection.checkBoxContainer = checks;
                box.addView(checks);
            }
            controls.add(selection);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new TransivaAlertDialogBuilder(this)
                .setTitle(firstNonEmpty(menu.optString("name"), "Pilih opsi"))
                .setView(scroll)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tambah", (d, w) -> {
                    try {
                        JSONArray selected = new JSONArray();
                        List<String> names = new ArrayList<>();
                        double extra = 0;

                        for (OptionSelection c : controls) {
                            if (c.radioGroup != null) {
                                int checkedId = c.radioGroup.getCheckedRadioButtonId();
                                RadioButton checked = c.radioGroup.findViewById(checkedId);
                                if (checked != null) {
                                    int idx = ((Integer) checked.getTag()).intValue();
                                    JSONObject source = c.items.optJSONObject(idx);
                                    if (source != null) {
                                        JSONObject pick = new JSONObject();
                                        pick.put("type", c.type);
                                        pick.put("label", c.label);
                                        pick.put("name", source.optString("name"));
                                        selected.put(pick);
                                        extra += source.optDouble("price", 0);
                                        names.add(source.optString("name"));
                                    }
                                }
                            } else if (c.checkBoxContainer != null) {
                                for (int j = 0; j < c.checkBoxContainer.getChildCount(); j++) {
                                    View child = c.checkBoxContainer.getChildAt(j);
                                    if (!(child instanceof CheckBox) || !((CheckBox) child).isChecked()) continue;
                                    int idx = ((Integer) child.getTag()).intValue();
                                    JSONObject source = c.items.optJSONObject(idx);
                                    if (source == null) continue;
                                    JSONObject pick = new JSONObject();
                                    pick.put("type", c.type);
                                    pick.put("label", c.label);
                                    pick.put("name", source.optString("name"));
                                    selected.put(pick);
                                    extra += source.optDouble("price", 0);
                                    names.add(source.optString("name"));
                                }
                            }
                        }
                        addConfiguredItem(menu, selected, android.text.TextUtils.join(", ", names), extra);
                        renderMenus();
                    } catch (Exception e) {
                        showInfo("Pilihan menu", "Gagal membaca pilihan menu.");
                    }
                })
                .show();
    }

    private String optionLabel(JSONObject item) {
        double extra = item.optDouble("price", 0);
        return firstNonEmpty(item.optString("name"), "Pilihan") + (extra > 0 ? ("  +" + rupiah(extra)) : "");
    }

    private void addConfiguredItem(JSONObject menu, JSONArray selectedOptions, String optionText, double optionExtra) {
        int id = menu.optInt("id", 0);
        String key = id + "|" + selectedOptions.toString();
        CartItem found = null;
        for (CartItem c : cart) if (key.equals(c.key)) { found = c; break; }
        if (found == null) {
            found = new CartItem();
            found.id = id;
            found.restaurantId = menu.optInt("restaurant_id", activeRestaurant.optInt("id", 0));
            found.name = firstNonEmpty(menu.optString("name"), "Menu");
            found.basePrice = menu.optDouble("price", 0);
            found.price = found.basePrice + Math.max(0, optionExtra);
            found.qty = 0;
            found.key = key;
            found.optionText = optionText;
            found.selectedOptions = selectedOptions;
            cart.add(found);
        }
        found.qty++;
    }

    private void removeOneFromCart(int menuId) {
        for (int i = cart.size() - 1; i >= 0; i--) {
            CartItem c = cart.get(i);
            if (c.id != menuId) continue;
            c.qty--;
            if (c.qty <= 0) cart.remove(i);
            return;
        }
    }

    private void clearStickyCartBar() {
        if (stickyCartBar != null && stickyCartBar.getParent() instanceof FrameLayout) {
            ((FrameLayout) stickyCartBar.getParent()).removeView(stickyCartBar);
        }
        stickyCartBar = null;
        if (root != null) root.setPadding(dp(16), dp(18), dp(16), dp(28));
    }

    private void buildCartBar() {
        clearStickyCartBar();
        if (cart.isEmpty() || currentScreen != 1) return;
        LinearLayout bar = card();
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        bar.setElevation(dp(12));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        int totalQty = 0; for (CartItem c : cart) totalQty += c.qty;
        left.addView(text(totalQty + " item • Total Belanja", 12, "#64748B", false));
        left.addView(text(rupiah(foodTotal()), 20, "#0B3A78", true));
        bar.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        Button checkout = primaryButton("Checkout");
        checkout.setOnClickListener(v -> showCheckout());
        bar.addView(checkout, new LinearLayout.LayoutParams(dp(130), dp(48)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(78));
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(dp(12), 0, dp(12), dp(10));
        page.addView(bar, lp);
        stickyCartBar = bar;
        root.setPadding(dp(16), dp(18), dp(16), dp(116));
    }

    private void showCheckout() {
        currentScreen = 2;
        clearStickyCartBar();
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
        info.addView(text(firstNonEmpty(activeRestaurant.optString("name"), "Merchant"), 16, "#0F172A", true));
        String opReason = firstNonEmpty(activeRestaurant.optString("availability_reason"), "");
        String opHours = firstNonEmpty(activeRestaurant.optString("today_hours"), "");
        if (!opHours.isEmpty()) info.addView(text("🕒 " + opHours, 12, "#64748B", false));
        if (activeRestaurant.optInt("is_open", 1) != 1 && !opReason.isEmpty()) info.addView(text("🔴 " + opReason, 12, "#EF4444", true));
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
            if (item.optionText != null && !item.optionText.isEmpty()) {
                TextView options = text(item.optionText, 11, "#7C3AED", false);
                options.setPadding(0, dp(2), 0, 0);
                col.addView(options);
            }
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
        String hematCaption = "Hemat • Coin\n" + hematRemaining + " koin";
        Button hemat = choiceButton(hematCaption, "hemat".equals(deliveryMode));
        hemat.setPadding(dp(8), 0, dp(8), 0);
        hemat.setMinWidth(0);
        hemat.setMinimumWidth(0);
        TierBadgeUi.applyToButton(hemat, hematTier, dp(24), dp(3));
        boolean coinReady = hematRemaining >= hematLimit;
        hemat.setEnabled(coinReady);
        hemat.setAlpha(coinReady ? 1f : 0.5f);
        standard.setOnClickListener(v -> { deliveryMode = "standard"; deliveryFee = standardFee; renderCheckout(); });
        hemat.setOnClickListener(v -> { deliveryMode = "hemat"; deliveryFee = standardFee; renderCheckout(); });
        row.addView(standard, new LinearLayout.LayoutParams(0, dp(60), 1));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, dp(60), 1); hlp.setMargins(dp(10),0,0,0);
        row.addView(hemat, hlp);
        delivery.addView(row);
        addWithMargin(delivery, 0, 0, 0, dp(14));

        LinearLayout voucherCard = card();
        voucherCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        voucherCard.addView(text("Voucher Ongkir", 16, "#0B3A78", true));
        voucherCard.addView(text("Voucher hanya berlaku untuk akun terverifikasi. Masukkan kode promo atau voucher Royalti.", 12, "#64748B", false));
        EditText voucherField = new EditText(this);
        voucherField.setSingleLine(true); voucherField.setHint("Contoh: TRV-XXXXXXXXXXXX"); voucherField.setText(voucherCode);
        voucherCard.addView(voucherField, new LinearLayout.LayoutParams(-1, dp(52)));
        Button saveVoucher = choiceButton(voucherCode.isEmpty()?"Gunakan Voucher":"Voucher: "+voucherCode, !voucherCode.isEmpty());
        saveVoucher.setOnClickListener(v -> { voucherCode = voucherField.getText().toString().trim().toUpperCase(Locale.US); renderCheckout(); });
        LinearLayout.LayoutParams svlp=new LinearLayout.LayoutParams(-1,dp(48));svlp.setMargins(0,dp(8),0,0);voucherCard.addView(saveVoucher,svlp);
        addWithMargin(voucherCard, 0, 0, 0, dp(14));

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
        double grossTotal = foodTotal() + standardFee;
        double coinDiscount = 0;
        if ("hemat".equals(deliveryMode) && hematRemaining >= hematLimit) {
            coinDiscount = CustomerFinancialRules.foodCoinDiscount(hematRemaining, coinValueRupiah, grossTotal, coinMinOrderAfterDiscount);
        }
        total.addView(summaryLine("Total makanan", foodTotal()));
        total.addView(summaryLine("Ongkir", standardFee));
        if (coinDiscount > 0) total.addView(summaryLine("Potongan Transiva Coin", -coinDiscount));
        total.addView(summaryLine("Total bayar", Math.max(0, grossTotal - coinDiscount)));
        boolean acceptingNow = activeRestaurant != null && activeRestaurant.optInt("is_open", 1) == 1;
        Button order = primaryButton(acceptingNow ? "Buat Pesanan" : "Merchant Tidak Menerima Pesanan");
        order.setEnabled(acceptingNow);
        order.setAlpha(acceptingNow ? 1f : 0.55f);
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

    private void loadRestaurants() { loadRestaurants(true); }
    private void loadRestaurants(boolean showLoading) {
        if (showLoading) setLoading(true);
        featureRuntime.execute(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_food_restaurants.php?user_id=" + Uri.encode(String.valueOf(userId)) + "&v=" + System.currentTimeMillis());
                restaurants.clear();
                JSONArray arr = res.optJSONArray("restaurants");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) restaurants.add(arr.getJSONObject(i));
                    featureRuntime.post(mainHandler, () -> { if (showLoading) setLoading(false); if (activeRestaurant == null) showRestaurantList(); loadAllMenuIndex(); });
                } else throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat merchant"));
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { if (showLoading) { setLoading(false); root.removeAllViews(); buildTopBar("Trans Food", "", true); addStatus("Koneksi gagal memuat merchant"); showInfo("Gagal", e.getMessage()); } });
            }
        });
    }

    private void loadAllMenuIndex() {
        featureRuntime.execute(() -> {
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
                            item.restaurantName = firstNonEmpty(r.optString("name"), "Merchant");
                            item.restaurant = r;
                            item.menuId = m.optInt("id", 0);
                            item.menuName = firstNonEmpty(m.optString("name"), "Menu");
                            item.category = firstNonEmpty(m.optString("category"), "Menu");
                            item.description = firstNonEmpty(m.optString("description"), "");
                            item.image = firstNonEmpty(m.optString("image"), "assets/no-image.png");
                            item.price = m.optDouble("price", 0);
                            item.originalPrice = m.optDouble("original_display_price", m.optDouble("original_price", item.price));
                            item.discountPercent = m.optDouble("discount_percent", 0);
                            item.discountActive = m.optInt("discount_active",0) == 1 && item.discountPercent > 0;
                            item.salesCount = m.optLong("sales_count", 0);
                            item.restaurantRating = r.optDouble("rating", 0);
                            item.restaurantDistanceKm = r.isNull("distance_km") ? 15d : r.optDouble("distance_km", 15d);
                            item.active = m.optInt("is_active", 1) == 1;
                            item.trackStock = m.optInt("track_stock", 0) == 1;
                            item.stock = m.optInt("stock", -1);
                            if (item.active) fresh.add(item);
                        }
                    } catch (Exception ignored) {}
                }
                featureRuntime.post(mainHandler, () -> {
                    allMenuSearchItems.clear();
                    allMenuSearchItems.addAll(fresh);
                    if (activeRestaurant == null) renderHomeResults();
                });
            } catch (Exception ignored) {}
        });
    }

    private void loadMenus(int restaurantId) { loadMenus(restaurantId, true); }
    private void loadMenus(int restaurantId, boolean showLoading) {
        if (showLoading) setLoading(true);
        featureRuntime.execute(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_food_menus.php?restaurant_id=" + restaurantId + "&v=" + System.currentTimeMillis());
                menus.clear();
                // Jangan menimpa object merchant dari daftar secara mentah.
                // Endpoint menu tidak selalu mengirim field profil lengkap (terutama banner),
                // sehingga banner yang tampil di list bisa hilang di halaman detail.
                JSONObject detailRestaurant = res.optJSONObject("restaurant");
                if (detailRestaurant != null) {
                    JSONObject merged = new JSONObject();
                    if (activeRestaurant != null) {
                        java.util.Iterator<String> oldKeys = activeRestaurant.keys();
                        while (oldKeys.hasNext()) {
                            String key = oldKeys.next();
                            try { merged.put(key, activeRestaurant.opt(key)); } catch (Exception ignored) {}
                        }
                    }
                    java.util.Iterator<String> detailKeys = detailRestaurant.keys();
                    while (detailKeys.hasNext()) {
                        String key = detailKeys.next();
                        Object value = detailRestaurant.opt(key);
                        // Field string kosong dari endpoint detail tidak boleh menghapus profil valid.
                        if (value instanceof String && ((String) value).trim().isEmpty() && merged.has(key)) continue;

                        // Banner pada daftar merchant berasal langsung dari get_food_restaurants.php
                        // dan sudah terbukti dapat dimuat. Jangan biarkan endpoint menu menimpa
                        // banner valid tersebut dengan path hasil normalisasi yang berbeda.
                        if ("banner".equals(key)) {
                            String existingBanner = firstNonEmpty(merged.optString("banner"), "").trim();
                            if (!existingBanner.isEmpty()) continue;
                        }
                        try { merged.put(key, value); } catch (Exception ignored) {}
                    }
                    activeRestaurant = merged;
                }
                JSONArray arr = res.optJSONArray("menus");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) menus.add(arr.getJSONObject(i));
                    try { mergeFoodSocial(getJson(BASE_URL + "server/customer_food_social.php?restaurant_id=" + restaurantId)); } catch (Exception ignored) {}
                    featureRuntime.post(mainHandler, () -> { if (showLoading) setLoading(false); showMenuPage(); });
                } else throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat menu"));
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { if (showLoading) { setLoading(false); showMenuPage(); addStatus("Gagal memuat menu"); showInfo("Gagal", e.getMessage()); } });
            }
        });
    }

    private void calculateOngkirThenRender() {
        setLoading(true);
        featureRuntime.execute(() -> {
            try {
                String url = BASE_URL + "server/calculateOngkir.php?service_type=Transbike" +
                        "&restaurant_id=" + Uri.encode(String.valueOf(activeRestaurant.optInt("id", 0))) +
                        "&user_id=" + Uri.encode(String.valueOf(userId)) +
                        "&delivery_mode=" + Uri.encode(deliveryMode) +
                        "&v=" + System.currentTimeMillis();
                JSONObject res = getJson(url);
                if (!res.optBoolean("success", false)) throw new Exception(firstNonEmpty(res.optString("message"), "Gagal menghitung ongkir"));
                standardFee = res.optDouble("standard_price", res.optDouble("price", 0));
                // TransFood Hemat memakai coin pada total checkout, bukan memotong ongkir dua kali.
                deliveryFee = standardFee;
                hematFee = standardFee;
                distanceKm = res.optDouble("distance_km", 0);
                hematRemaining = res.optInt("hemat_remaining", 0);
                hematLimit = res.optInt("hemat_limit", 0);
                hematTier = firstNonEmpty(res.optString("hemat_tier"), "BRONZE");
                coinValueRupiah = res.optInt("coin_value_rupiah", 1);
                coinMinOrderAfterDiscount = res.optInt("coin_min_order_after_discount", 1000);
                TierBadgeUi.saveActiveTier(this, hematTier);
                featureRuntime.post(mainHandler, () -> { setLoading(false); renderCheckout(); });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { setLoading(false); deliveryFee = standardFee = hematFee = 0; distanceKm = 0; renderCheckout(); showInfo("Ongkir", e.getMessage()); });
            }
        });
    }

    private void createFoodOrder() {
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (activeRestaurant == null || activeRestaurant.optInt("id", 0) <= 0) { showInfo("Gagal", "Merchant tidak valid."); return; }
        if (activeRestaurant.optInt("is_open", 1) != 1) {
            showInfo("Merchant tidak menerima pesanan", firstNonEmpty(activeRestaurant.optString("availability_reason"), "Merchant sedang tutup atau pause."));
            return;
        }
        if (cart.isEmpty()) { showInfo("Keranjang kosong", "Tambahkan menu terlebih dahulu."); return; }
        setLoading(true);
        featureRuntime.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("restaurant_id", activeRestaurant.optInt("id", 0));
                payload.put("delivery_mode", deliveryMode);
                payload.put("payment_method", paymentMethod);
                payload.put("voucher_code", voucherCode);
                JSONArray items = new JSONArray();
                for (CartItem c : cart) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.id);
                    o.put("qty", c.qty);
                    o.put("selected_options", c.selectedOptions == null ? new JSONArray() : c.selectedOptions);
                    items.put(o);
                }
                payload.put("items", items);
                JSONObject res = postJson(BASE_URL + "server/create_food_order.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Pesanan berhasil dibuat" : "Gagal membuat pesanan");
                featureRuntime.post(mainHandler, () -> {
                    setLoading(false);
                    if (ok) {
                        new TransivaAlertDialogBuilder(this)
                                .setTitle("Berhasil")
                                .setMessage(msg + "\n\nOrder ID: " + res.optString("order_id", "-"))
                                .setPositiveButton("OK", (d, w) -> finish())
                                .show();
                    } else showInfo("Gagal", msg);
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { setLoading(false); showInfo("Error", "Koneksi gagal membuat pesanan makanan."); });
            }
        });
    }

    private void mergeFoodSocial(JSONObject social) {
        if (social == null || !social.optBoolean("success", false)) return;
        try {
            JSONObject merchant = social.optJSONObject("merchant");
            if (merchant != null && activeRestaurant != null) {
                activeRestaurant.put("is_favorite", merchant.optBoolean("is_favorite", false));
                int merchantReviews = merchant.optInt("review_count", 0);
                if (merchantReviews > 0) activeRestaurant.put("social_rating", merchant.optDouble("rating", activeRestaurant.optDouble("rating", 0)));
                else activeRestaurant.put("social_rating", activeRestaurant.optDouble("rating", 0));
                activeRestaurant.put("social_review_count", merchantReviews > 0 ? merchantReviews : activeRestaurant.optInt("review_count", 0));
            }
            JSONObject menuMap = social.optJSONObject("menus");
            if (menuMap != null) {
                for (JSONObject m : menus) {
                    JSONObject sm = menuMap.optJSONObject(String.valueOf(m.optInt("id", 0)));
                    if (sm == null) continue;
                    m.put("is_favorite", sm.optBoolean("is_favorite", false));
                    m.put("rating", sm.optDouble("rating", 0));
                    m.put("review_count", sm.optInt("review_count", 0));
                }
            }
        } catch (Exception ignored) {}
    }

    private void toggleFavorite(String type, int targetId, JSONObject target, ImageButton button) {
        if (targetId <= 0) return;
        boolean next = !target.optBoolean("is_favorite", false);
        button.setEnabled(false);
        featureRuntime.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("action", "favorite");
                payload.put("type", type);
                payload.put("target_id", targetId);
                payload.put("favorite", next);
                JSONObject res = postJson(BASE_URL + "server/customer_food_social.php", payload);
                featureRuntime.post(mainHandler, () -> {
                    button.setEnabled(true);
                    if (!res.optBoolean("success", false)) { showInfo("Favorit", res.optString("message", "Gagal memperbarui favorit.")); return; }
                    try { target.put("is_favorite", res.optBoolean("is_favorite", next)); } catch (Exception ignored) {}
                    setFavoriteIcon(button, target.optBoolean("is_favorite", false));
                    // Favorit langsung menjadi sinyal personalisasi untuk AI rekomendasi dashboard.
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { button.setEnabled(true); showInfo("Favorit", "Koneksi gagal memperbarui favorit."); });
            }
        });
    }

    private void showMenuReviews(JSONObject menu) {
        int menuId = menu.optInt("id", 0);
        if (menuId <= 0) return;
        setLoading(true);
        featureRuntime.execute(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/customer_food_social.php?action=reviews&menu_id=" + menuId);
                featureRuntime.post(mainHandler, () -> { setLoading(false); showReviewDialog(menu, res); });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { setLoading(false); showInfo("Penilaian Menu", "Gagal memuat komentar customer."); });
            }
        });
    }

    private void showReviewDialog(JSONObject menu, JSONObject data) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        JSONArray reviews = data == null ? null : data.optJSONArray("reviews");

        double average = 0;
        int count = reviews == null ? 0 : reviews.length();
        if (reviews != null) {
            for (int i = 0; i < reviews.length(); i++) {
                JSONObject r = reviews.optJSONObject(i);
                if (r != null) average += r.optInt("rating", 0);
            }
            if (count > 0) average /= count;
        }

        TextView score = text("⭐ " + String.format(Locale.US, "%.1f", average) + " / 5", 23, "#0B3A78", true);
        score.setGravity(Gravity.CENTER);
        box.addView(score);
        TextView total = text(count + " penilaian customer", 12, "#64748B", false);
        total.setGravity(Gravity.CENTER);
        total.setPadding(0, dp(5), 0, dp(10));
        box.addView(total);

        if (reviews == null || reviews.length() == 0) {
            box.addView(text("Belum ada penilaian untuk menu ini.", 13, "#64748B", false));
        } else {
            int[] distribution = new int[6];
            for (int i = 0; i < reviews.length(); i++) {
                JSONObject r = reviews.optJSONObject(i);
                if (r == null) continue;
                int value = Math.max(1, Math.min(5, r.optInt("rating", 0)));
                distribution[value]++;
            }
            for (int star = 5; star >= 1; star--) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(text(star + " ★", 13, "#334155", true), new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(text(String.valueOf(distribution[star]), 13, "#64748B", false));
                box.addView(row, new LinearLayout.LayoutParams(-1, dp(30)));
            }
        }
        TextView note = text("Untuk memberi rating menu, buka Aktivitas → Riwayat lalu pilih pesanan yang sudah selesai.", 12, "#64748B", false);
        note.setPadding(0, dp(10), 0, 0);
        box.addView(note);

        new TransivaAlertDialogBuilder(this)
                .setTitle(firstNonEmpty(menu.optString("name"), "Penilaian Menu"))
                .setView(box)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void submitMenuReview(JSONObject menu, int rating, String comment) {
        featureRuntime.execute(() -> {
            try {
                JSONObject payload = new JSONObject(); payload.put("action", "review"); payload.put("menu_id", menu.optInt("id",0)); payload.put("rating", rating); payload.put("comment", comment == null ? "" : comment.trim());
                JSONObject res = postJson(BASE_URL + "server/customer_food_social.php", payload);
                featureRuntime.post(mainHandler, () -> {
                    if (res.optBoolean("success", false)) {
                        try { menu.put("rating", res.optDouble("rating", rating)); menu.put("review_count", res.optInt("review_count", 1)); } catch (Exception ignored) {}
                        showInfo("Terima kasih", "Penilaian menu berhasil disimpan dan dapat dilihat customer lain."); renderMenus();
                    } else showInfo("Penilaian Menu", res.optString("message", "Penilaian gagal disimpan."));
                });
            } catch (Exception e) { featureRuntime.post(mainHandler, () -> showInfo("Penilaian Menu", "Koneksi gagal menyimpan penilaian.")); }
        });
    }

    private void showMerchantReviews(JSONObject merchant) {
        int rid = merchant == null ? 0 : merchant.optInt("id", 0); if (rid <= 0) return;
        setLoading(true);
        featureRuntime.execute(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/customer_food_social.php?action=merchant_reviews&restaurant_id=" + rid);
                featureRuntime.post(mainHandler, () -> { setLoading(false); showMerchantReviewDialog(merchant, res); });
            } catch (Exception e) { featureRuntime.post(mainHandler, () -> { setLoading(false); showInfo("Penilaian Merchant", "Gagal memuat penilaian merchant."); }); }
        });
    }

    private void showMerchantReviewDialog(JSONObject merchant, JSONObject data) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));

        double average = merchant == null ? 0 : merchant.optDouble("social_rating", merchant.optDouble("rating", 0));
        int count = merchant == null ? 0 : merchant.optInt("social_review_count", merchant.optInt("review_count", 0));
        if (data != null) {
            JSONArray reviews = data.optJSONArray("reviews");
            if (reviews != null) {
                count = reviews.length();
                double sum = 0;
                for (int i = 0; i < reviews.length(); i++) {
                    JSONObject r = reviews.optJSONObject(i);
                    if (r != null) sum += r.optInt("rating", 0);
                }
                if (count > 0) average = sum / count;
            }
        }

        TextView score = text("⭐ " + String.format(Locale.US, "%.1f", average) + " / 5", 25, "#0B3A78", true);
        score.setGravity(Gravity.CENTER);
        box.addView(score, new LinearLayout.LayoutParams(-1, -2));

        TextView total = text(count + " penilaian customer", 13, "#64748B", false);
        total.setGravity(Gravity.CENTER);
        total.setPadding(0, dp(6), 0, dp(10));
        box.addView(total, new LinearLayout.LayoutParams(-1, -2));

        int[] distribution = new int[6];
        JSONArray reviews = data == null ? null : data.optJSONArray("reviews");
        if (reviews != null) {
            for (int i = 0; i < reviews.length(); i++) {
                JSONObject r = reviews.optJSONObject(i);
                if (r == null) continue;
                int rating = Math.max(1, Math.min(5, r.optInt("rating", 0)));
                distribution[rating]++;
            }
        }
        for (int star = 5; star >= 1; star--) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(star + " ★", 13, "#334155", true);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(String.valueOf(distribution[star]), 13, "#64748B", false));
            box.addView(row, new LinearLayout.LayoutParams(-1, dp(30)));
        }

        TextView note = text("Penilaian hanya dapat diberikan dari Riwayat di menu Aktivitas setelah pesanan selesai.", 12, "#64748B", false);
        note.setPadding(0, dp(10), 0, 0);
        box.addView(note);

        new TransivaAlertDialogBuilder(this)
                .setTitle("Rating " + firstNonEmpty(merchant == null ? "" : merchant.optString("name"), "Merchant"))
                .setView(box)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private void submitMerchantReview(JSONObject merchant,int rating,String comment){
        featureRuntime.execute(()->{try{JSONObject payload=new JSONObject();payload.put("action","merchant_review");payload.put("restaurant_id",merchant.optInt("id",0));payload.put("rating",rating);payload.put("comment",comment==null?"":comment.trim());JSONObject res=postJson(BASE_URL+"server/customer_food_social.php",payload);featureRuntime.post(mainHandler, ()->{if(res.optBoolean("success",false)){try{merchant.put("social_rating",res.optDouble("rating",rating));merchant.put("social_review_count",res.optInt("review_count",1));}catch(Exception ignored){}showInfo("Terima kasih","Penilaian merchant berhasil disimpan.");renderMenus();}else showInfo("Penilaian Merchant",res.optString("message","Penilaian gagal disimpan."));});}catch(Exception e){featureRuntime.post(mainHandler, ()->showInfo("Penilaian Merchant","Koneksi gagal menyimpan penilaian."));}});
    }

    private ImageButton favoriteButton(boolean active) { ImageButton b=new ImageButton(this); b.setPadding(dp(9),dp(9),dp(9),dp(9)); b.setScaleType(ImageView.ScaleType.CENTER_INSIDE); b.setBackground(roundStroke(active?"#FFF1F2":"#FFFFFF","#FECDD3",dp(18),1)); setFavoriteIcon(b,active); return b; }
    private void setFavoriteIcon(ImageButton b, boolean active) { b.setImageResource(active ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline); b.setBackground(roundStroke(active?"#FFF1F2":"#FFFFFF","#FECDD3",dp(18),1)); }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = CustomerApiClient.open(this, urlText);
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("GET");
        return new JSONObject(readStream(c));
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = CustomerApiClient.open(this, urlText);
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
        String finalUrl = absoluteUrl(firstNonEmpty(urlText, ""));
        // Phase 2: all TransFood remote images share the app memory/disk cache and bounded executor.
        // fallback=0 keeps the currently rendered bitmap while quantity/cart UI is redrawn, avoiding flicker.
        RemoteImageLoader.loadCenterCrop(view, finalUrl, 0);
    }

    private String absoluteUrl(String value) {
        return ImageUrlResolver.resolve(value);
    }

    private int getQty(int id) { int q = 0; for (CartItem c : cart) if (c.id == id) q += c.qty; return q; }
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
    private void showInfo(String title, String msg) { try { new TransivaAlertDialogBuilder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) {
        return CustomerUiPrimitives.dp(this, v);
    }
    private String rupiah(double v) {
        return CustomerCommonFormatters.rupiahSpacedTruncate(v);
    }
    private String firstNonEmpty(String... values) {
        return CustomerCommonFormatters.firstBasic(values);
    }

    private static class MenuSearchItem { int restaurantId; int menuId; String restaurantName; String menuName; String category; String description; String image; double price; double originalPrice; double discountPercent; boolean discountActive; long salesCount; double restaurantRating; double restaurantDistanceKm; boolean active; boolean trackStock; int stock; JSONObject restaurant; }
    private static class CartItem { int id; int restaurantId; String name; double basePrice; double price; int qty; String key; String optionText; JSONArray selectedOptions; }
    private static class OptionSelection { String type; String label; JSONArray items; RadioGroup radioGroup; LinearLayout checkBoxContainer; }



    @Override
    protected void onResume() {
        super.onResume();
        featureRuntime.onResume();
        mainHandler.removeCallbacks(realtimeFoodRefresh);
        mainHandler.postDelayed(realtimeFoodRefresh, CustomerPerformanceManager.pollingBackground(this, 30000L));
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(realtimeFoodRefresh);
        featureRuntime.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        featureRuntime.destroy();
        try { mainHandler.removeCallbacksAndMessages(null); } catch (Exception ignored) {}
        super.onDestroy();
    }
}