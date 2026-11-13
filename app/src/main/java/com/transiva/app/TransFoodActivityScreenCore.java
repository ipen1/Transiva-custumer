package com.transiva.app;

import com.transiva.app.FoodCartStore.Cart;
import com.transiva.app.FoodCartStore.CartItem;

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

import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
class TransFoodActivityScreenCore extends CustomerAdaptiveActivity {



    protected final CustomerFeatureRuntimeController featureRuntime = new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.FOOD);

    protected static final String BASE_URL = ApiConfig.ROOT;
    protected static final int TIMEOUT_MS = 20000;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected FrameLayout page;
    protected LinearLayout root;
    private LinearLayout foodHeader;
    protected ProgressBar progressBar;
    protected View stickyCartBar;

    protected final List<JSONObject> restaurants = new ArrayList<>();
    protected final List<JSONObject> menus = new ArrayList<>();
    private List<CartItem> cart = new ArrayList<>();
    private FoodCartStore cartStore;
    private Cart activeCart;
    private ImageButton savedCartButton;
    private TextView savedCartBadge;
    private final Bundle checkoutNotes = new Bundle();
    private FoodCheckoutNote singleCheckoutNote;
    private final java.util.Map<Integer, Cart> merchantDrafts = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> cartPhotos = new java.util.HashMap<>();
    private static final int COMBINED_CHECKOUT = 5;
    private static final int COMBINED_RESULT = 6;
    private FoodCombinedCheckout.Plan combinedPlan;
    private String combinedPayment = "cash", combinedSessionId = "";
    private String combinedQuoteError = "";
    private int combinedGeneration;
    private int combinedCheckoutScroll;
    private boolean combinedQuoting, editingCombinedAddress;
    private JSONObject cartOriginRestaurant;
    private String cartOriginSearch = "";
    private boolean checkoutFromCarts, menuFromCarts;
    private boolean refreshCheckoutRestaurant;
    private int checkoutRestaurantId;
    private String splitCartDraft = "";
    private String defaultVoucherCode = "";
    protected final List<MenuSearchItem> allMenuSearchItems = new ArrayList<>();
    protected JSONObject activeRestaurant;
    protected String menuSearchQuery = "";
    protected String homeSearchQuery = "";
    protected LinearLayout homeResultsBox;
    protected Runnable homeSearchRunnable;
    protected int currentScreen = 0; // 0=home, 1=menu, 2=checkout, 3=success, 4=saved carts
    protected String homeMode = "nearby";
    protected volatile boolean restaurantsLoadFinished = false;
    protected boolean restaurantsLoading = false;
    protected String foodLoadError = "";
    protected int savedHomeScroll = 0;
    protected ScrollView foodScroll;
    private FoodVirtualList virtualFood;

    private int expandedToppingMenu;
    private long toppingSelectionOrder;
    private final java.util.Map<Integer, TextView> menuQuantityViews = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.List<KabarMitraUi.Avatar>> merchantAvatars = new java.util.HashMap<>();
    private TextView cartQuantityLabel, cartTotalLabel;
    private JSONObject kabarFeed;
    private long kabarClock, kabarAnchor, kabarNextFetch;
    private boolean kabarFetching;
    private JSONObject detailStoryResponse;
    private int detailStoryRid;
    private long detailStoryAnchor;
    private int pendingProfileRid,profileStoryGeneration;
    private int menuLoadGeneration;
    private final Runnable storyExpiryTick = new Runnable(){public void run(){
        updateMerchantAvatars(); updateFoodStoryRail(); mainHandler.postDelayed(this, 1000);
    }};

    private FoodStoryRail foodStoryRail;
    private boolean favoritesLoading, favoritesLoaded, foodOrderSubmitting;
    private final Runnable requoteCombined = () -> {
        if (currentScreen == COMBINED_CHECKOUT && !foodOrderSubmitting) quoteCombinedCheckout();
    };
    private boolean voucherExpanded, editingCheckoutAddress;
    private String voucherDraft="";
    private int royaltyVoucherDiscount;
    private String royaltyVoucherNote="",royaltyPreviewKey="";
    private int checkoutQuoteGeneration;
    private boolean checkoutQuoteReady;
    private Button checkoutSubmitButton;
    private String favoritesError="";
    private final java.util.Set<Integer> favoriteMerchantIds=new java.util.HashSet<>();
    private final java.util.Set<Integer> favoriteMenuIds=new java.util.HashSet<>();
    private ArrayList<String> successOrderIds = new ArrayList<>();
    private boolean successWasRecovered, foodForeground;
    private long successActivityAt;
    private final Runnable activityAfterFood=()->openOrderActivity();

    protected int userId = 0;
    protected String username = "User";
    protected String deliveryMode = "standard";
    protected String paymentMethod = "cash";
    protected String voucherCode = "";
    protected double deliveryFee = 0;
    protected double standardFee = 0;
    protected double hematFee = 0;
    protected double distanceKm = 0;
    protected int hematRemaining = 0; // saldo coin
    protected int hematLimit = 1000; // minimum redeem coin
    protected int coinValueRupiah = 1;
    protected int coinMaxRedeemPercent = 100;
    protected int coinMinOrderAfterDiscount = 1000;
    protected String hematTier = "BRONZE";
    protected SplitBillManager splitBillManager;
    protected int lastFoodQuote = 0;
    protected boolean menusLoadFinished = false;

    protected final Runnable realtimeFoodRefresh = new Runnable() { @Override public void run() { if (!isFinishing() && currentScreen != 3) { refreshMerchantStories(); if (activeRestaurant == null) loadRestaurants(false); else loadMenus(activeRestaurant.optInt("id",0), false); mainHandler.postDelayed(this, CustomerPerformanceManager.pollingBackground(TransFoodActivityScreenCore.this, 30000L)); } } };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerCustomBackNavigation();
        if(!DeliveryAddressGate.valid(this)){DeliveryAddressGate.showUnavailable(this,"TransFood");DeliveryAddressGate.require(this,"TransFood");return;}
        try {
            CustomerWindowPolicy.status(getWindow(), Color.WHITE);
            CustomerWindowPolicy.navigation(getWindow(), Color.WHITE);
            int flags = CustomerWindowPolicy.LIGHT_STATUS;
            if (android.os.Build.VERSION.SDK_INT >= 26) flags |= CustomerWindowPolicy.LIGHT_NAVIGATION;
            CustomerWindowPolicy.appearance(getWindow(), flags);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                getWindow().setNavigationBarContrastEnforced(false);
                getWindow().setStatusBarContrastEnforced(false);
            }
        } catch (Exception ignored) {}
        loadSession();
        cartStore = new FoodCartStore(this, userId);
        if (savedInstanceState != null && savedInstanceState.getInt("food_note_user") == userId) {
            Bundle notes = savedInstanceState.getBundle("food_checkout_notes");
            if (notes != null) checkoutNotes.putAll(notes);
        }
        if(getIntent()!=null)defaultVoucherCode=firstNonEmpty(getIntent().getStringExtra("royalty_voucher_code"),"");
        voucherCode=defaultVoucherCode;
        if (getIntent() != null) homeSearchQuery = firstNonEmpty(getIntent().getStringExtra("global_search_query"), "");
        buildBase();
        if (savedInstanceState != null) {
            ArrayList<String> orders = savedInstanceState.getStringArrayList("food_success_order_ids");
            if (orders != null && !orders.isEmpty()) {
                successWasRecovered = savedInstanceState.getBoolean("food_success_recovered");
                showFoodOrderSuccess(orders, savedInstanceState.getLong("food_success_due"));
                return;
            }
        }
        CustomerBestOffer.load(this, "TransFood", offer -> featureRuntime.post(mainHandler, () -> {
            if (offer != null && (voucherCode == null || voucherCode.trim().isEmpty())) {
                String code = offer.optString("promo_code", "").trim();
                if (!code.isEmpty()) {
                    defaultVoucherCode = code;
                    if (currentScreen != 2) voucherCode = code;
                }
            }
        }));
        int storyRid=getIntent()==null?0:getIntent().getIntExtra("story_restaurant_id",0);
        if(storyRid>0){
            try{activeRestaurant=new JSONObject();activeRestaurant.put("id",storyRid);activeRestaurant.put("name",getIntent().getStringExtra("story_restaurant_name"));}catch(Exception ignored){}
            selectRestaurant(activeRestaurant);showMenuPage();loadMenus(storyRid);
        }else{showRestaurantList();loadRestaurants(true);}
    }

    protected void loadSession() {
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

    protected void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));
        MitraPullScrollView scroll = new MitraPullScrollView(this);
        foodScroll = scroll;
        virtualFood=new FoodVirtualList(this,()->{if(root!=null)rebindRetainedViews();});
        scroll.setFillViewport(false);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        page.addView(layout, new FrameLayout.LayoutParams(-1, -1));
        foodHeader = new LinearLayout(this);
        foodHeader.setOrientation(LinearLayout.VERTICAL);
        foodHeader.setBackgroundColor(Color.WHITE);
        foodHeader.setElevation(dp(3));
        layout.addView(foodHeader, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout content = new FrameLayout(this);
        layout.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar pullIndicator = new ProgressBar(this);
        pullIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams pullLp = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pullLp.topMargin = dp(8);
        content.addView(pullIndicator, pullLp);
        scroll.indicator = pullIndicator;
        scroll.allowed = () -> currentScreen == 0 && (virtualFood==null||!virtualFood.isPresented()||!virtualFood.canScrollVertically(-1));
        scroll.refresh = () -> {
            kabarNextFetch=0;
            refreshMerchantStories(scroll::finishRefresh);
            catalogRefreshEpoch=System.currentTimeMillis();loadRestaurants(false);
            if("favorites".equals(homeMode))loadFavoriteIds();
        };
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        page.addView(progressBar, lp);
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    protected void showRestaurantList() {
        leaveVirtualFood();
        ++menuLoadGeneration;featureRuntime.cancel("food-menus");featureRuntime.cancel("food-social");featureRuntime.cancel("food-profile-story");++profileStoryGeneration;pendingProfileRid=0;
        renderedMenuRid=0;menusRequestLoading=false;menusOffset=0;menusHasMore=false;
        setLoading(false);
        currentScreen = 0;
        if(foodScroll!=null)foodScroll.setFillViewport(false);
        root.setGravity(Gravity.TOP);
        clearStickyCartBar();
        root.removeAllViews();
        merchantAvatars.clear();
        selectRestaurant(null);
        buildTopBar("TransFood", "Makan enak, dekat denganmu", true);
        addDeliveryLocationRow();
        addHomeSearchBar();
        addQuickMenus();
        addHomeSectionHeader();

        homeResultsBox = new LinearLayout(this);
        homeResultsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(homeResultsBox, new LinearLayout.LayoutParams(-1, -2));
        renderHomeResults();
        if (foodScroll != null && savedHomeScroll > 0 && (virtualFood==null||!virtualFood.isPresented())) foodScroll.post(() -> foodScroll.scrollTo(0, savedHomeScroll));
    }

    protected void addPremiumHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(12), dp(16), dp(12));
        hero.setBackground(roundGradient("#087BFF", "#0754D8", dp(20)));
        hero.setElevation(dp(3));
        hero.addView(text("TRANSFOOD DELIVERY", 10, "#DDEEFF", true));
        TextView title = text("Mau makan apa hari ini?", 19, "#FFFFFF", true);
        title.setPadding(0, dp(3), 0, 0);
        hero.addView(title);
        hero.addView(text("Temukan makanan enak di sekitarmu", 12, "#EAF4FF", false));
        if (voucherCode != null && !voucherCode.trim().isEmpty()) {
            TextView promo = text("Promo tersedia • " + voucherCode, 11, "#FFFFFF", true);
            promo.setPadding(0, dp(5), 0, 0); hero.addView(promo);
        }
        addWithMargin(hero, 0, 0, 0, dp(10));
    }

    protected void addDeliveryLocationRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(14), 1));
        String address = DeliveryAddressGate.address(this);
        TextView location = text("📍 Antar ke · " + firstNonEmpty(address, "Lokasi akun / titik antar"), 12, "#123B6B", true);
        location.setMaxLines(2);
        location.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(location, new LinearLayout.LayoutParams(-1, -2));

        row.setClickable(true);
        row.setOnClickListener(v -> DeliveryAddressGate.edit(this,"TransFood"));
        addWithMargin(row, 0, 0, 0, dp(10));

    }

    protected void addQuickMenus() {
        foodStoryRail=new FoodStoryRail(this,()->{
            homeMode="favorites".equals(homeMode)?"nearby":"favorites";
            if(foodScroll!=null)savedHomeScroll=foodScroll.getScrollY();
            showRestaurantList();loadRestaurants(true);loadMenuSearch(homeSearchQuery);
            if("favorites".equals(homeMode))loadFavoriteIds();
        });
        addWithMargin(foodStoryRail,0,0,0,dp(8));
        updateFoodStoryRail();
    }
    private void updateFoodStoryRail(){
        if(foodStoryRail!=null && currentScreen==0)foodStoryRail.update(kabarFeed,storyNow(),"favorites".equals(homeMode),storyHasMore,kabarFetching,this::loadMoreStories);
    }

    protected void addQuickMenu(LinearLayout row, String label, String drawableName, String mode) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(4), dp(4), dp(4));
        item.setClickable(true);
        item.setBackground(round(homeMode.equals(mode) ? "#EAF4FF" : "#FFFFFF", dp(18)));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int id = "ic_kabar_mitra".equals(drawableName) ? R.drawable.ic_kabar_mitra : getResources().getIdentifier(drawableName, "drawable", getPackageName());
        if (id != 0) icon.setImageResource(id);
        item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView name = text(label, 12, homeMode.equals(mode) ? "#087BFF" : "#183B66", true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(5), 0, 0);
        item.addView(name, new LinearLayout.LayoutParams(-1, -2));
        item.setOnClickListener(v -> {
            homeMode = mode;
            if (foodScroll != null) savedHomeScroll = foodScroll.getScrollY();
            showRestaurantList();loadRestaurants(true);if("favorites".equals(mode))loadFavoriteIds();loadMenuSearch(homeSearchQuery);
        });
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }

    protected void addHomeSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("favorites".equals(homeMode)?"Favoritmu":"Pilihan di sekitarmu", 16, "#123B6B", true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = text("favorites".equals(homeMode)?"Semua toko":"", 12, "#087BFF", true);
        tag.setOnClickListener(v->{homeMode="nearby";showRestaurantList();loadRestaurants(true);loadMenuSearch(homeSearchQuery);});
        row.addView(tag);
        addWithMargin(row, 0, 0, 0, dp(8));
    }

    protected void addHomeSearchBar() {
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setText(homeSearchQuery);
        search.setTextSize(14);
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
                ++menuSearchGeneration;featureRuntime.cancel("food-search");menuIndexLoading=false;lastMenuIndexAt=0;menuSearchOffset=0;menuSearchHasMore=false;
                if (homeSearchRunnable != null) mainHandler.removeCallbacks(homeSearchRunnable);
                ++restaurantGeneration;featureRuntime.cancel("food-merchants");restaurantsLoading=false;
                homeSearchRunnable = () -> { renderHomeResults(); loadMenuSearch(homeSearchQuery); loadRestaurants(false); };
                mainHandler.postDelayed(homeSearchRunnable, 280);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
        lp.setMargins(0, 0, 0, dp(8));
        root.addView(search, lp);
    }

    protected void renderHomeResults() {
        if (homeResultsBox == null || currentScreen != 0) return;
        LinearLayout liveResults=homeResultsBox;renderLiveHome=liveResults;IncrementalLinearRenderer.begin(liveResults);
        homeResultsBox=new LinearLayout(this);homeResultsBox.setOrientation(LinearLayout.VERTICAL);
        try {

        if (restaurants.isEmpty() && homeSearchQuery.trim().isEmpty() && !"favorites".equals(homeMode)) {
            String msg = !restaurantsLoadFinished ? "Menyiapkan merchant di sekitarmu..."
                    : !foodLoadError.isEmpty() ? "Koneksi terganggu. Merchant belum berhasil dimuat."
                    : "Belum ada merchant yang menerima pesanan di area ini.";
            addStatusTo(homeResultsBox, msg);
            if (restaurantsLoadFinished) {
                TextView retry = text("↻ Coba lagi", 13, "#087BFF", true);
                retry.setGravity(Gravity.CENTER); retry.setPadding(dp(12), dp(12), dp(12), dp(12));
                retry.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(14), 1));
                retry.setOnClickListener(v -> loadRestaurants(true));
                addWithMarginTo(homeResultsBox, retry, 0, dp(8), 0, dp(12));
            }
            return;
        }

        String q = homeSearchQuery == null ? "" : homeSearchQuery.trim().toLowerCase(Locale.ROOT);
        if("favorites".equals(homeMode)) { renderFavorites(q);addHomePageControls(q);return; }

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
            TextView smartTitle = text("Menu yang mungkin kamu suka", 14, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, smartTitle, 0, 0, 0, dp(4));

            int smartLimit = Math.min(3, smart.size());
            for (int i=0;i<smartLimit;i++) addMenuSearchCard(homeResultsBox, smart.get(i));
            TextView restoTitleSmart = text("Toko di sekitarmu", 14, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, restoTitleSmart, 0, dp(8), 0, dp(10));
        }

        if (q.length() > 0) {
            TextView menuTitle = text("Hasil menu makanan", 15, "#0B3A78", true);
            addWithMarginTo(homeResultsBox, menuTitle, 0, 0, 0, dp(10));

            if (menuHits.isEmpty()) {
                String msg = menuIndexLoading ? "Mencari menu…" : "Menu belum ditemukan. Coba kata lain atau muat lagi.";
                addStatusTo(homeResultsBox, msg);
            } else {
                int limit = menuHits.size();
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
            addStatusTo(homeResultsBox, q.isEmpty() ? "Belum ada merchant untuk filter ini." : "Merchant tidak ditemukan untuk: " + homeSearchQuery);
        } else {
            addRestaurantGrid(homeResultsBox, restoHits);
        }
        addHomePageControls(q);
        } finally {
            LinearLayout plan=homeResultsBox;homeResultsBox=liveResults;
            IncrementalLinearRenderer.apply(liveResults,plan,0);IncrementalLinearRenderer.end(liveResults);renderLiveHome=null;presentHomeList();rebindRetainedViews();
        }
    }

    private void addHomePageControls(String q){
        if(catalogCacheServing)addStatusTo(homeResultsBox,restaurantsLoading?"Data tersimpan · memperbarui merchant…":"Menampilkan data tersimpan. Tarik untuk mencoba lagi.");
        if(restaurantHasMore || !foodLoadError.isEmpty()) {
            TextView more=text(restaurantsLoading?"Memuat merchant…":!foodLoadError.isEmpty()?"↻ Coba lagi memuat merchant":"Lihat merchant berikutnya",13,"#087BFF",true);
            more.setPadding(dp(12),dp(14),dp(12),dp(14));more.setGravity(Gravity.CENTER);
            more.setOnClickListener(v->loadRestaurantPage(restaurantRequestContext().equals(acceptedRestaurantContext)?restaurantOffset:0));more.setEnabled(!restaurantsLoading);
            homeResultsBox.addView(more);
        }
        if((!q.isEmpty()||"favorites".equals(homeMode))&&menuSearchHasMore){
            TextView more=text(menuIndexLoading?"Memuat menu…":"Lihat menu berikutnya",13,"#087BFF",true);
            more.setPadding(dp(12),dp(14),dp(12),dp(14));more.setOnClickListener(v->loadMenuSearchPage(homeSearchQuery,menuSearchOffset));more.setEnabled(!menuIndexLoading);
            homeResultsBox.addView(more);
        }
    }

    private void loadFavoriteIds(){
        if(favoritesLoading)return;favoritesLoading=true;favoritesError="";renderHomeResults();
        java.util.concurrent.Future<?> task=featureRuntime.execute(()->{try{
            JSONObject response=getJson(BASE_URL+"server/customer_food_social.php?action=favorites");
            featureRuntime.post(mainHandler,()->{
                favoritesLoading=false;
                if(response.optBoolean("success")){
                    favoriteMerchantIds.clear();favoriteMenuIds.clear();
                    JSONArray stores=response.optJSONArray("merchant_ids"),items=response.optJSONArray("menu_ids");
                    if(stores!=null)for(int i=0;i<stores.length();i++)favoriteMerchantIds.add(stores.optInt(i));
                    if(items!=null)for(int i=0;i<items.length();i++)favoriteMenuIds.add(items.optInt(i));
                    favoritesLoaded=true;
                }else favoritesError=response.optString("message","Favorit belum dapat dimuat.");
                if(currentScreen==0&&"favorites".equals(homeMode))renderHomeResults();
            });
        }catch(Exception e){featureRuntime.post(mainHandler,()->{favoritesLoading=false;favoritesError="Favorit belum berhasil dimuat. Tarik ke bawah untuk mencoba lagi.";if(currentScreen==0&&"favorites".equals(homeMode))renderHomeResults();});}});
        if(task==null){favoritesLoading=false;favoritesError="Sistem sedang sibuk. Tarik ke bawah untuk mencoba lagi.";renderHomeResults();}
    }
    private void renderFavorites(String query){
        if(!favoritesError.isEmpty())addStatusTo(homeResultsBox,favoritesError);
        if(!favoritesLoaded){if(favoritesError.isEmpty())addStatusTo(homeResultsBox,"Menyiapkan favoritmu…");return;}
        boolean shown=false;
        for(MenuSearchItem item:allMenuSearchItems)if(favoriteMenuIds.contains(item.menuId)&&(query.isEmpty()||contains(item.menuName,query)||contains(item.restaurantName,query))){addMenuSearchCard(homeResultsBox,item);shown=true;}
        List<JSONObject> stores=new ArrayList<>();
        for(JSONObject store:restaurants)if(favoriteMerchantIds.contains(store.optInt("id"))&&(query.isEmpty()||contains(store.optString("name"),query)||contains(store.optString("category"),query)))stores.add(store);
        if(!stores.isEmpty()){addRestaurantGrid(homeResultsBox,stores);shown=true;}
        if(!shown)addStatusTo(homeResultsBox,query.isEmpty()?"Belum ada favorit di sekitarmu. Tekan ikon hati pada toko atau menu yang kamu suka, ya.":"Belum ketemu favorit yang cocok dengan pencarianmu.");
    }

    protected void addRestaurantGrid(LinearLayout parent,List<JSONObject> data){
        for(int i=0;i<data.size();i+=2){
            final JSONObject left=data.get(i),right=i+1<data.size()?data.get(i+1):null;
            FoodDeferredCard row=new FoodDeferredCard(this,"merchant-row:"+left.optInt("id"),left.toString()+(right==null?"":right.toString()),()->{
                LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.setMargins(0,0,dp(7),dp(14));box.addView(createRestaurantCard(left),a);
                LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,right==null?1:-2,1);b.setMargins(dp(7),0,0,dp(14));box.addView(right==null?new View(this):createRestaurantCard(right),b);return box;
            });parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private String searchCardSignature(MenuSearchItem item){return item.menuName+":"+item.category+":"+item.price+":"+item.originalPrice+":"+item.image+":"+item.restaurant.toString()+":"+item.discountPercent+":"+item.discountActive+":"+item.stock+":"+item.trackStock+":"+item.active;}
    protected void addMenuSearchCard(LinearLayout parent,MenuSearchItem item){
        FoodDeferredCard deferred=new FoodDeferredCard(this,"search:"+item.menuId,searchCardSignature(item),()->{
            LinearLayout plan=new LinearLayout(this);addMenuSearchCardNow(plan,item);View card=plan.getChildAt(0);plan.removeView(card);return card;
        });addWithMarginTo(parent,deferred,0,0,0,dp(10));
    }
    private void addMenuSearchCardNow(LinearLayout parent, MenuSearchItem item) {
        String key="search:"+item.menuId,fingerprint=searchCardSignature(item);
        if(IncrementalLinearRenderer.unchanged(renderLiveHome,key,fingerprint)){
            View placeholder=new View(this);IncrementalLinearRenderer.mark(placeholder,key,fingerprint);addWithMarginTo(parent,placeholder,0,0,0,dp(10));return;
        }
        LinearLayout card = card();
        card.setBackground(foodBlueCard());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setClickable(true);
        card.setOnClickListener(v -> openRestaurantFromSearch(item));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(round("#EAF4FF",dp(12)));
        img.setClipToOutline(true);
        card.addView(img, new LinearLayout.LayoutParams(dp(76), dp(76)));
        loadImage(img, absoluteUrl(firstNonEmpty(item.image, "assets/no-image.png")));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, 0, 0);
        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        body.addView(text(firstNonEmpty(item.menuName, "Menu"), 16, "#0F172A", true));
        TextView owner = text(firstNonEmpty(item.restaurantName, "Merchant") + " • " + Math.max(0,(int)Math.round(item.restaurantDistanceKm)) + " km", 12, "#64748B", false);
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

        IncrementalLinearRenderer.mark(card,"search:"+item.menuId,searchCardSignature(item));
        addWithMarginTo(parent, card, 0, 0, 0, dp(10));
    }

    protected double smartMenuScore(MenuSearchItem item) {
        if (item == null) return 0;
        double popularity = Math.log10(1d + Math.max(0,item.salesCount)) * 42d;
        double rating = Math.max(0d, Math.min(5d,item.restaurantRating)) * 14d;
        double distance = Math.max(0d, 15d - Math.max(0d,item.restaurantDistanceKm)) * 3.2d;
        double newest = Math.min(18d, Math.log10(10d + Math.max(0,item.menuId)) * 3d);
        double discount = item.discountActive ? Math.min(12d, item.discountPercent * 0.4d) : 0d;
        return popularity + rating + distance + newest + discount;
    }

    private JSONObject restaurantForCart(int rid, String name) {
        JSONObject store = findRestaurantById(rid);
        if (store != null) return store;
        JSONObject placeholder = new JSONObject();
        try { placeholder.put("id", rid).put("name", name); }
        catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        return placeholder;
    }

    private void selectRestaurant(JSONObject restaurant) {
        int previous = activeCart == null ? 0 : activeCart.restaurantId;
        int next = restaurant == null ? 0 : restaurant.optInt("id");
        if (previous != next) expandedToppingMenu = 0;
        activeRestaurant = restaurant;
        loadActiveCart();
    }

    private void loadActiveCart() {
        int rid = activeRestaurant == null ? 0 : activeRestaurant.optInt("id");
        Cart draft = merchantDrafts.get(rid);
        if (rid > 0 && draft == null) {
            draft = new Cart(rid, "");
            merchantDrafts.put(rid, draft);
        }
        setActiveCart(draft);
    }

    private void setActiveCart(Cart draft) {
        activeCart = draft;
        cart = activeCart == null ? new ArrayList<>() : activeCart.items;
        for (CartItem line : cart) toppingSelectionOrder = Math.max(toppingSelectionOrder, line.selectionOrder);
    }

    void openStoryMerchant(int rid,String name){
        if (rid <= 0 || currentScreen >= 2 || foodOrderSubmitting) return;
        JSONObject store = activeRestaurant != null && activeRestaurant.optInt("id") == rid
                ? activeRestaurant : restaurantForCart(rid, name);
        selectRestaurant(store);
        menuSearchQuery = "";
        showMenuPage();
        loadMenus(rid,true);
    }

    protected void openRestaurantFromSearch(MenuSearchItem item) {
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
        selectRestaurant(r);
        menuSearchQuery = item.menuName;
        showMenuPage();
        loadMenus(item.restaurantId);
    }

    protected JSONObject findRestaurantById(int id) {
        for (JSONObject r : restaurants) if (r.optInt("id", 0) == id) return r;
        return null;
    }

    protected boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }



    protected LinearLayout createRestaurantCard(JSONObject r) {
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
            selectRestaurant(r);
            menuSearchQuery = "";
            showMenuPage();
            loadMenus(r.optInt("id", 0));
        });

        FrameLayout portrait = new FrameLayout(this);
        ImageView backdrop = new ImageView(this) {
            @Override public void setImageBitmap(android.graphics.Bitmap bitmap) {
                if (bitmap != null && android.os.Build.VERSION.SDK_INT < 31) {
                    bitmap = android.graphics.Bitmap.createScaledBitmap(
                            android.graphics.Bitmap.createScaledBitmap(bitmap, 12, 8, true), 144, 96, true);
                }
                super.setImageBitmap(bitmap);
            }
        };
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackgroundColor(Color.parseColor("#BBD9F5"));
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    dp(11), dp(11), android.graphics.Shader.TileMode.CLAMP));
        }
        portrait.addView(backdrop, new FrameLayout.LayoutParams(-1, dp(66)));
        loadImage(backdrop, firstNonEmpty(r.optString("banner"), "assets/default-food.png"));
        View veil = new View(this);
        veil.setBackground(roundGradient("#006FAFDD", "#446FAFDD", 0));
        portrait.addView(veil, new FrameLayout.LayoutParams(-1, dp(66)));
        KabarMitraUi.Avatar profile = merchantAvatar(r, 70);
        FrameLayout.LayoutParams profileLp = new FrameLayout.LayoutParams(dp(70), dp(70), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        portrait.addView(profile, profileLp);
        card.addView(portrait, new LinearLayout.LayoutParams(-1, dp(93)));
        card.setClipToOutline(true);

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

    private LinearLayout renderLiveRoot,renderLiveHome;
    private int renderedMenuRid;
    protected void showMenuPage() {
        int rid=activeRestaurant==null?0:activeRestaurant.optInt("id");
        if(currentScreen==1&&renderedMenuRid==rid&&root.getChildCount()>0){renderMenus();return;}
        if(renderedMenuRid!=rid){menus.clear();menusLoadFinished=false;menusOffset=0;menusHasMore=false;menusRequestLoading=false;menusError="";menusCached=false;acceptedMenuContext="";}
        leaveVirtualFood();renderedMenuRid=rid;
        currentScreen = 1;
        clearStickyCartBar();
        root.removeAllViews();
        buildTopBar("Detail Merchant", firstNonEmpty(activeRestaurant != null ? activeRestaurant.optString("name") : "", "Menu makanan"), true);
        if (menus.isEmpty()) addStatus(menusLoadFinished ? "Belum ada menu makanan yang tersedia dari merchant ini." : "Memuat menu...");
        else renderMenus();
    }

    protected void renderMenus() {
        LinearLayout liveRoot=root;renderLiveRoot=liveRoot;IncrementalLinearRenderer.begin(liveRoot);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        try {
        if(IncrementalLinearRenderer.unchanged(renderLiveRoot,"merchant-header",activeRestaurant.toString())){
            View placeholder=new View(this);IncrementalLinearRenderer.mark(placeholder,"merchant-header",activeRestaurant.toString());addWithMargin(placeholder,0,0,0,dp(12));
        }else{
        LinearLayout resto = card();
        resto.setPadding(dp(16), dp(14), dp(16), dp(14));

        menuQuantityViews.clear();
        merchantAvatars.clear();
        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        KabarMitraUi.Avatar merchantProfile = merchantAvatar(activeRestaurant, 64);
        identity.addView(merchantProfile,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout identityWords=new LinearLayout(this);identityWords.setOrientation(LinearLayout.VERTICAL);
        identityWords.setPadding(dp(12),0,dp(6),0);
        TextView merchantName = text(firstNonEmpty(activeRestaurant.optString("name"), "Merchant"), 19, "#0F172A", true);
        merchantName.setMaxLines(2);identityWords.addView(merchantName);
        identity.addView(identityWords,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton merchantFav = favoriteButton(activeRestaurant.optBoolean("is_favorite"));
        merchantFav.setContentDescription("Favoritkan merchant");
        merchantFav.setOnClickListener(v -> toggleFavorite("merchant", activeRestaurant.optInt("id", 0), activeRestaurant, merchantFav));
        identity.addView(merchantFav,new LinearLayout.LayoutParams(dp(44),dp(44)));
        resto.addView(identity);

        String merchantCategory = firstNonEmpty(activeRestaurant.optString("category"), "Makanan & minuman");
        String location = activeRestaurant.isNull("distance_km") ? "" : String.format(Locale.US, " · %.1f km", activeRestaurant.optDouble("distance_km", 0));
        identityWords.addView(text(merchantCategory + location, 12, "#64748B", false));
        double socialRating = activeRestaurant.optDouble("social_rating", activeRestaurant.optDouble("rating", 0));
        int socialReviews = activeRestaurant.optInt("social_review_count", activeRestaurant.optInt("review_count", 0));
        String ratingLabel = socialReviews > 0 ? "★ " + String.format(Locale.US, "%.1f", socialRating) + " · " + socialReviews + " ulasan  ›" : "☆ Belum ada ulasan  ›";
        TextView merchantRating = text(ratingLabel, 12, "#64748B", true);
        merchantRating.setPadding(0, dp(7), 0, dp(8));
        merchantRating.setOnClickListener(v -> showMerchantReviews(activeRestaurant));
        resto.addView(merchantRating);

        boolean accepting = activeRestaurant.optInt("is_open", 1) == 1;
        String reason = firstNonEmpty(activeRestaurant.optString("availability_reason"), "Merchant sedang tidak menerima pesanan");
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView operational = text(accepting ? "● Menerima pesanan" : "● Toko sedang tutup", 11, accepting ? "#16A34A" : "#EF4444", true);
        operational.setMaxLines(2);
        actions.addView(operational, new LinearLayout.LayoutParams(0, -2, 1));
        Button followStore = choiceButton("Memuat…", false);
        followStore.setTextSize(12);followStore.setMaxLines(1);
        followStore.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams followLp = new LinearLayout.LayoutParams(dp(120), dp(44));
        followLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(followStore, followLp);
        KabarMitraView.bindFollow(this, featureRuntime, mainHandler, activeRestaurant.optInt("id", 0), followStore, activeRestaurant, () -> { kabarNextFetch = 0; detailStoryResponse = null;kabarFeed=null;storyOffset=0;storyCursor=0;storyHasMore=false; refreshMerchantStories(); if (activeRestaurant != null) refreshProfileStories(activeRestaurant.optInt("id")); });
        resto.addView(actions);
        if (!accepting) resto.addView(text(reason, 12, "#64748B", false));
        String todayHours = firstNonEmpty(activeRestaurant.optString("today_hours"), "");
        if (!todayHours.isEmpty()) {
            TextView hours = text("Jam hari ini · " + todayHours, 12, "#64748B", false);
            hours.setPadding(0, dp(8), 0, 0);
            resto.addView(hours);
        }
        IncrementalLinearRenderer.mark(resto,"merchant-header",activeRestaurant.toString());
        addWithMargin(resto, 0, 0, 0, dp(12));
        }
        addWithMargin(text("Menu pilihan", 17, "#123B6B", true), 0, 0, 0, dp(6));
        if (menuSearchQuery != null && !menuSearchQuery.trim().isEmpty()) {
            TextView fromSearch=text("Hasil untuk “"+menuSearchQuery+"”",12,"#64748B",false);
            addWithMargin(fromSearch,0,0,0,dp(8));
        }
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

        if (shown == 0) addStatus(!menusLoadFinished?"Memuat menu…":menuSearchQuery.isEmpty()?"Belum ada menu makanan yang tersedia dari merchant ini.":"Menu tidak ditemukan untuk: " + menuSearchQuery);
        addMenuPageControls();

        } finally {
            LinearLayout plan=root;root=liveRoot;
            IncrementalLinearRenderer.apply(liveRoot,plan,0);IncrementalLinearRenderer.end(liveRoot);renderLiveRoot=null;presentMenuList();rebindRetainedViews();
        }
        buildCartBar();
    }

    private void leaveVirtualFood(){if(virtualFood!=null&&virtualFood.isPresented()&&foodScroll!=null&&root!=null)virtualFood.leave(foodScroll,root);}
    private void presentHomeList(){if(virtualFood!=null&&currentScreen==0)virtualFood.present(foodScroll,root,FoodVirtualList.take(homeResultsBox,0),"home:"+homeMode+":"+homeSearchQuery,dp(16),dp(18),dp(16),dp(28));}
    private void presentMenuList(){if(virtualFood!=null&&currentScreen==1&&activeRestaurant!=null)virtualFood.present(foodScroll,root,FoodVirtualList.take(root,0),"menu:"+activeRestaurant.optInt("id")+":"+menuSearchQuery,dp(16),dp(18),dp(16),stickyCartBar==null?dp(28):stickyCartBar.getHeight()+dp(28));}
    private void setFoodPadding(int l,int t,int r,int b){if(virtualFood!=null&&virtualFood.isPresented())virtualFood.bottomPadding(l,t,r,b,root);else if(root!=null)root.setPadding(l,t,r,b);}

    private void rebindRetainedViews(){
        menuQuantityViews.clear();merchantAvatars.clear();collectRetainedViews(root);if(virtualFood!=null&&virtualFood.isPresented())collectRetainedViews(virtualFood);updateMerchantAvatars();syncToppingCards();
    }
    private void collectRetainedViews(View view){
        Object tag=view.getTag();
        if(tag instanceof String&&((String)tag).startsWith("qty:"))menuQuantityViews.put(Integer.parseInt(((String)tag).substring(4)),(TextView)view);
        if(tag instanceof String&&((String)tag).startsWith("avatar:")){
            int rid=Integer.parseInt(((String)tag).substring(7));
            java.util.List<KabarMitraUi.Avatar> list=merchantAvatars.get(rid);
            if(list==null){list=new ArrayList<>();merchantAvatars.put(rid,list);}list.add((KabarMitraUi.Avatar)view);
        }
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectRetainedViews(group.getChildAt(i));}
    }

    protected void addMenuCard(JSONObject m){
        FoodDeferredCard deferred=new FoodDeferredCard(this,"menu:"+m.optInt("id"),m.toString()+":"+getQty(m.optInt("id")),()->{
            LinearLayout saved=root,plan=new LinearLayout(this);root=plan;
            try{addMenuCardNow(m);}finally{root=saved;}
            View card=plan.getChildAt(0);plan.removeView(card);return card;
        });addWithMargin(deferred,0,0,0,dp(12));
    }
    private void addMenuCardNow(JSONObject m) {
        String key="menu:"+m.optInt("id"),fingerprint=m.toString()+":"+getQty(m.optInt("id"));
        if(IncrementalLinearRenderer.unchanged(renderLiveRoot,key,fingerprint)){
            View placeholder=new View(this);IncrementalLinearRenderer.mark(placeholder,key,fingerprint);addWithMargin(placeholder,0,0,0,dp(12));return;
        }
        boolean active = m.optInt("is_active", 1) == 1;
        boolean trackStock = m.optInt("track_stock", 0) == 1;
        int stock = m.optInt("stock", -1);
        if (trackStock && stock <= 0) active = false;

        LinearLayout card = card();
        card.setBackground(foodBlueCard());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setGravity(Gravity.TOP);
        card.addView(mainRow, new LinearLayout.LayoutParams(-1, -2));
        card.setAlpha(active ? 1f : 0.55f);

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        img.setBackground(round("#EAF4FF", dp(14)));
        img.setClipToOutline(true);
        mainRow.addView(img, new LinearLayout.LayoutParams(dp(84), dp(84)));


        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), 0, 0, 0);
        mainRow.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        TextView menuTitle = text(firstNonEmpty(m.optString("name"), "Menu"), 16, "#0F172A", true);
        body.addView(menuTitle);
        body.addView(text(firstNonEmpty(m.optString("category"), "Menu"), 12, "#94A3B8", false));

        String desc = m.optString("description","");
        TextView description = text(desc, 12, "#64748B", false);
        description.setMaxLines(2);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        description.setPadding(0, dp(4), 0, 0);
        description.setVisibility(desc.isEmpty()?View.GONE:View.VISIBLE);
        body.addView(description);

        boolean merchantDiscount = m.optInt("discount_active",0) == 1 && m.optDouble("discount_percent",0) > 0;
        if (merchantDiscount) {
            TextView oldPrice = text(rupiah(m.optDouble("original_display_price", m.optDouble("original_price", m.optDouble("price",0)))), 12, "#94A3B8", false);
            oldPrice.setPaintFlags(oldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); oldPrice.setPadding(0,dp(5),0,0); body.addView(oldPrice);
            TextView disc = text("🏷️ Diskon merchant " + String.format(Locale.US,"%.0f%%",m.optDouble("discount_percent",0)), 11, "#C2410C", true); body.addView(disc);
        }
        TextView price = text(rupiah(m.optDouble("price", 0)), 15, "#0B7CFF", true);
        price.setPadding(0, dp(merchantDiscount?2:6), 0, dp(4));
        body.addView(price);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        boolean compactFooter=getResources().getConfiguration().screenWidthDp<360 || getResources().getConfiguration().fontScale>1.2f;
        footer.setOrientation(compactFooter?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        footer.setPadding(0, dp(10), 0, 0);
        card.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout socialRow = new LinearLayout(this);
        socialRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton fav = favoriteButton(m.optBoolean("is_favorite"));
        fav.setContentDescription("Favoritkan menu");
        fav.setOnClickListener(v -> toggleFavorite("menu", m.optInt("id", 0), m, fav));
        socialRow.addView(fav, new LinearLayout.LayoutParams(dp(40), dp(40)));
        Button review = choiceButton("⭐ " + String.format(Locale.US, "%.1f", m.optDouble("rating", 0)) + " (" + m.optInt("review_count", 0) + ")", false);
        LinearLayout.LayoutParams reviewLp = new LinearLayout.LayoutParams(-2, dp(40));
        reviewLp.setMargins(dp(8), 0, 0, 0);
        socialRow.addView(review, reviewLp);
        review.setOnClickListener(v -> showMenuReviews(m));
        review.setTextSize(11);
        review.setMinWidth(0);
        review.setMinimumWidth(0);
        review.setPadding(dp(6), 0, dp(6), 0);
        footer.addView(socialRow, compactFooter?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0, -2, 1));

        if (trackStock) {
            String stockText = stock <= 0 ? "Stok habis" : ("Stok tersisa: " + stock);
            TextView st = text(stockText, 12, stock <= 0 ? "#EF4444" : (stock <= 5 ? "#D97706" : "#16A34A"), true);
            st.setPadding(0, 0, 0, dp(4));
            body.addView(st);
        }

        JSONArray options = m.optJSONArray("options");
        JSONArray toppings = bundleToppings(options);
        if (toppings == null && hasSelectableOptions(options)) {
            TextView opt = text(buildOptionPreview(options), 11, "#7C3AED", true);
            opt.setPadding(0, dp(2), 0, dp(5));
            body.addView(opt);
        }

        if (toppings != null && active) {
            ToppingDropdown dropdown = new ToppingDropdown(m, toppings, img, menuTitle, description, price);
            footer.addView(dropdown.toggle, compactFooter ? new LinearLayout.LayoutParams(-1,-2)
                    : new LinearLayout.LayoutParams(-2,-2));
            card.addView(dropdown,new LinearLayout.LayoutParams(-1,-2));
            mainRow.setOnClickListener(v -> dropdown.toggle());
            img.setOnClickListener(v -> dropdown.toggle());
            dropdown.bind();
        } else if (!active) {
            body.addView(text("Tidak tersedia", 12, "#EF4444", true));
        } else {
            LinearLayout qty = new LinearLayout(this);
            qty.setGravity(Gravity.CENTER_VERTICAL);
            Button minus = tinyButton("−");
            Button plus = tinyButton("+");
            TextView value = text(String.valueOf(getQty(m.optInt("id", 0))), 15, "#0F172A", true);
            value.setTag("qty:"+m.optInt("id"));
            menuQuantityViews.put(m.optInt("id", 0), value);
            value.setGravity(Gravity.CENTER);
            value.setIncludeFontPadding(false);
            qty.addView(minus, new LinearLayout.LayoutParams(dp(40), dp(40)));
            qty.addView(value, new LinearLayout.LayoutParams(dp(30), dp(40)));
            qty.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout.LayoutParams qtyLp=new LinearLayout.LayoutParams(-2,-2);if(compactFooter){qtyLp.gravity=Gravity.END;qtyLp.topMargin=dp(6);}footer.addView(qty,qtyLp);

            minus.setOnClickListener(v -> {
                removeOneFromCart(m.optInt("id", 0));
                refreshCartUi();
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
                    refreshCartUi();
                }
            });
        }
        if (toppings == null || !active) {
            loadImage(img,absoluteUrl(firstNonEmpty(m.optString("image"),"assets/no-image.png")));
            img.setContentDescription("Lihat detail "+m.optString("name","menu"));
            img.setOnClickListener(v -> showMenuDescription(m));
            description.setOnClickListener(v -> showMenuDescription(m));
        }
        IncrementalLinearRenderer.mark(card,"menu:"+m.optInt("id"),m.toString()+":"+getQty(m.optInt("id")));
        addWithMargin(card, 0, 0, 0, dp(12));
    }

    private void showMenuDescription(JSONObject menu) {
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(16));scroll.addView(body);
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(photo,new LinearLayout.LayoutParams(-1,dp(200)));
        RemoteImageLoader.loadPreserveScale(photo,absoluteUrl(firstNonEmpty(menu.optString("image"),"assets/no-image.png")),0);
        TextView description=text(firstNonEmpty(menu.optString("description"),"Belum ada deskripsi menu."),14,"#64748B",false);
        description.setPadding(0,dp(12),0,0);body.addView(description);
        new TransivaAlertDialogBuilder(this).setTitle(menu.optString("name","Detail menu")).setView(scroll).setPositiveButton("Selesai",null).show();
    }

    protected boolean hasSelectableOptions(JSONArray groups) {
        if (groups == null) return false;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null) continue;
            JSONArray items = g.optJSONArray("items");
            if (items == null || items.length() == 0) continue;
            String type = firstNonEmpty(g.optString("type"), "option");
            // Varian tunggal Regular + Rp0 adalah default, jadi bisa langsung dipilih.
            if ("variant".equalsIgnoreCase(type) && items.length() == 1) {
                JSONObject only = items.optJSONObject(0);
                if (only != null && ("regular".equalsIgnoreCase(firstNonEmpty(only.optString("name"), "regular")) || "reguler".equalsIgnoreCase(firstNonEmpty(only.optString("name"), "regular")))
                        && only.optDouble("price", 0) <= 0) continue;
            }
            return true;
        }
        return false;
    }

    protected String buildOptionPreview(JSONArray groups) {
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

    protected void showOptionPicker(JSONObject menu) {
        JSONArray groups = menu.optJSONArray("options");
        if (!hasSelectableOptions(groups)) {
            addConfiguredItem(menu, new JSONArray(), "", 0);
            refreshCartUi();
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
                        refreshCartUi();
                    } catch (Exception e) {
                        showInfo("Pilihan menu", "Gagal membaca pilihan menu.");
                    }
                })
                .show();
    }

    private JSONArray bundleToppings(JSONArray groups) {
        if (groups == null) return null;
        for (int i=0;i<groups.length();i++) {
            JSONObject group=groups.optJSONObject(i);
            JSONArray items=group==null?null:group.optJSONArray("items");
            if (group!=null && "bundle_topping".equals(group.optString("type")) && items!=null && items.length()>0) return items;
        }
        return null;
    }

    private void syncToppingCards() {
        if (currentScreen!=1) return;
        if (root!=null) syncToppingCards(root);
        if (virtualFood!=null && virtualFood.isPresented()) syncToppingCards(virtualFood);
    }

    private void syncToppingCards(View view) {
        if (view instanceof ToppingDropdown) {
            ((ToppingDropdown)view).bind();
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) syncToppingCards(group.getChildAt(i));
        }
    }

    private final class ToppingDropdown extends LinearLayout {
        final JSONObject menu;
        final JSONArray options;
        final int id;
        final Button toggle;
        final TextView summary, heading, description, price;
        final ImageView photo;
        final LinearLayout choices;
        final List<ToppingRow> rows=new ArrayList<>();
        String boundPhoto="";
        boolean populated;

        ToppingDropdown(JSONObject menu, JSONArray options, ImageView photo,
                        TextView heading, TextView description, TextView price) {
            super(TransFoodActivityScreenCore.this);
            this.menu=menu; this.options=options; this.photo=photo;
            this.heading=heading; this.description=description; this.price=price;
            id=menu.optInt("id");
            setOrientation(VERTICAL);
            toggle=choiceButton("Pilih topping ▾",false);
            toggle.setTextSize(12);
            toggle.setMinimumHeight(dp(44));
            toggle.setMinHeight(dp(44));
            toggle.setOnClickListener(v->toggle());
            summary=text("",12,"#0B4B93",true);
            summary.setPadding(dp(10),dp(8),dp(10),dp(8));
            summary.setBackground(round("#EAF4FF",dp(12)));
            summary.setOnClickListener(v->toggle());
            LinearLayout.LayoutParams summaryLp=new LinearLayout.LayoutParams(-1,-2);
            summaryLp.topMargin=dp(6);
            addView(summary,summaryLp);
            choices=new LinearLayout(TransFoodActivityScreenCore.this);
            choices.setOrientation(VERTICAL);
            addView(choices,new LinearLayout.LayoutParams(-1,-2));
        }

        void toggle() {
            expandedToppingMenu=expandedToppingMenu==id?0:id;
            syncToppingCards();
        }

        void bind() {
            boolean expanded=expandedToppingMenu==id;
            if (expanded && !populated) populate();
            choices.setVisibility(expanded?VISIBLE:GONE);
            int total=getQty(id);
            toggle.setText(expanded?"Tutup topping ▴":total>0?"Ubah pilihan ▾":"Pilih topping ▾");
            toggle.setContentDescription(toggle.getText()+", "+menu.optString("name"));
            CartItem latest=null;
            List<String> lines=new ArrayList<>();
            for (CartItem item:cart) {
                if (item.id!=id) continue;
                if (latest==null || item.selectionOrder>latest.selectionOrder) latest=item;
                lines.add(item.qty+"× "+firstNonEmpty(item.optionText,"Tanpa topping"));
            }
            summary.setVisibility(total>0?VISIBLE:GONE);
            if (total>0) summary.setText("Dipilih · "+android.text.TextUtils.join("  •  ",lines));
            String name=firstNonEmpty(menu.optString("name"),"Menu");
            String photoUrl=menu.optString("image");
            String detail=menu.optString("description");
            double shownPrice=menu.optDouble("price",0);
            if (latest!=null) {
                photoUrl=firstNonEmpty(latest.image,photoUrl);
                shownPrice=latest.price;
                JSONObject option=latest.selectedOptions.optJSONObject(0);
                if (option!=null && "bundle_topping".equals(option.optString("type"))) {
                    for (int i=0;i<options.length();i++) {
                        JSONObject candidate=options.optJSONObject(i);
                        if (candidate==null || !option.optString("name").equals(candidate.optString("name"))) continue;
                        detail=candidate.optString("description");
                        break;
                    }
                }
            }
            heading.setText(latest==null || latest.optionText.isEmpty()?name:name+" · "+latest.optionText);
            description.setText(detail);
            description.setVisibility(detail.isEmpty()?GONE:VISIBLE);
            price.setText(rupiah(shownPrice)+(latest==null?"":" / porsi"));
            String resolved=absoluteUrl(firstNonEmpty(photoUrl,"assets/no-image.png"));
            if (!resolved.equals(boundPhoto)) {
                boundPhoto=resolved;
                loadImage(photo,resolved);
            }
            boolean canAdd=canAddToppingPortion();
            for (ToppingRow row:rows) {
                CartItem line=findCartItem(row.key);
                int count=line==null?0:line.qty;
                row.quantity.setText(String.valueOf(count));
                row.minus.setEnabled(count>0);
                row.plus.setEnabled(canAdd && count<99);
            }
        }

        boolean canAddToppingPortion() {
            boolean tracked=menu.optInt("track_stock",0)==1 || menu.optBoolean("track_stock");
            return !tracked || getQty(id)<menu.optInt("stock",0);
        }

        void populate() {
            populated=true;
            TextView label=text("Pilih jumlah per topping",12,"#47647F",true);
            label.setPadding(0,dp(9),0,dp(3));
            choices.addView(label);
            addChoice(null);
            for (int i=0;i<options.length();i++) {
                JSONObject option=options.optJSONObject(i);
                if (option!=null && !option.optString("name").trim().isEmpty() && option.optLong("display_price")>0) addChoice(option);
            }
            JSONArray legacy=new JSONArray();
            JSONArray groups=menu.optJSONArray("options");
            if (groups!=null) for (int i=0;i<groups.length();i++) {
                JSONObject group=groups.optJSONObject(i);
                if (group!=null && !"bundle_topping".equals(group.optString("type"))) legacy.put(group);
            }
            if (hasSelectableOptions(legacy)) {
                Button other=choiceButton("Varian dan tambahan lain",false);
                other.setOnClickListener(v->{
                    if (!canAddToppingPortion()) {
                        showInfo("Stok habis","Jumlah yang dipilih sudah mencapai stok menu.");
                        return;
                    }
                    try { showOptionPicker(new JSONObject(menu.toString()).put("options",legacy)); }
                    catch (org.json.JSONException error) { showInfo("Pilihan menu","Pilihan belum dapat dimuat."); }
                });
                choices.addView(other);
            }
        }

        void addChoice(JSONObject option) {
            String name=option==null?"Tanpa topping":option.optString("name");
            double shown=option==null?menu.optDouble("price",0):option.optDouble("display_price",0);
            String image=option==null?menu.optString("image"):firstNonEmpty(option.optString("image"),menu.optString("image"));
            JSONArray selection=new JSONArray();
            JSONObject display;
            try {
                if (option!=null) selection.put(new JSONObject().put("type","bundle_topping").put("name",name));
                display=new JSONObject(menu.toString()).put("price",shown).put("image",image);
            } catch (org.json.JSONException error) { return; }
            String key=id+"|"+selection.toString();

            LinearLayout row=new LinearLayout(TransFoodActivityScreenCore.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(round("#F3F7FC",dp(12)));
            row.setPadding(dp(6),dp(4),dp(6),dp(4));
            ImageView thumb=new ImageView(TransFoodActivityScreenCore.this);
            thumb.setBackground(round("#EAF4FF",dp(8)));
            thumb.setClipToOutline(true);
            row.addView(thumb,new LinearLayout.LayoutParams(dp(42),dp(42)));
            loadImage(thumb,absoluteUrl(firstNonEmpty(image,"assets/no-image.png")));
            LinearLayout words=new LinearLayout(TransFoodActivityScreenCore.this);
            words.setOrientation(VERTICAL);
            words.setPadding(dp(7),0,dp(3),0);
            TextView title=text(name,13,"#0F172A",true);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(title);
            String detail=option==null?"":option.optString("description");
            if (!detail.isEmpty()) {
                TextView subtitle=text(detail,11,"#64748B",false);
                subtitle.setSingleLine(true);
                subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(subtitle);
            }
            words.addView(text(rupiah(shown),12,"#0B4B93",true));
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));

            Button minus=tinyButton("−"), plus=tinyButton("+");
            minus.setContentDescription("Kurangi "+name);
            plus.setContentDescription("Tambah "+name);
            TextView quantity=text("0",14,"#0F172A",true);
            quantity.setGravity(Gravity.CENTER);
            row.addView(minus,new LinearLayout.LayoutParams(dp(44),dp(44)));
            row.addView(quantity,new LinearLayout.LayoutParams(dp(24),dp(44)));
            row.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
            minus.setOnClickListener(v->{
                CartItem line=findCartItem(key);
                if (line!=null && --line.qty==0) cart.remove(line);
                refreshCartUi();
            });
            plus.setOnClickListener(v->{
                CartItem line=findCartItem(key);
                if (!canAddToppingPortion() || line!=null && line.qty>=99) return;
                addConfiguredItem(display,selection,option==null?"":name,0);
                refreshCartUi();
            });
            rows.add(new ToppingRow(key,quantity,minus,plus));
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);
            rowLp.bottomMargin=dp(4);
            choices.addView(row,rowLp);
        }
    }

    private static final class ToppingRow {
        final String key;
        final TextView quantity;
        final Button minus, plus;
        ToppingRow(String key, TextView quantity, Button minus, Button plus) {
            this.key=key; this.quantity=quantity; this.minus=minus; this.plus=plus;
        }
    }

    protected String optionLabel(JSONObject item) {
        double extra = item.optDouble("price", 0);
        return firstNonEmpty(item.optString("name"), "Pilihan") + (extra > 0 ? ("  +" + rupiah(extra)) : "");
    }

    private CartItem findCartItem(String key) {
        for (CartItem item:cart) if (key.equals(item.key)) return item;
        return null;
    }

    protected void addConfiguredItem(JSONObject menu, JSONArray selectedOptions, String optionText, double optionExtra) {
        int id = menu.optInt("id", 0);
        if (activeCart == null || id <= 0 || menu.optInt("restaurant_id", activeCart.restaurantId) != activeCart.restaurantId) return;
        String key = id + "|" + selectedOptions.toString();
        CartItem found = findCartItem(key);
        if (found == null) {
            found = new CartItem();
            found.id = id;
            found.name = firstNonEmpty(menu.optString("name"), "Menu");
            found.price = menu.optDouble("price", 0) + Math.max(0, optionExtra);
            found.qty = 0;
            found.key = key;
            found.optionText = optionText;
            found.image = menu.optString("image");
            found.selectedOptions = selectedOptions;
            cart.add(found);
        }
        if (found.qty >= 99) {
            showInfo("Jumlah maksimum", "Satu pilihan menu bisa dipesan maksimal 99 porsi.");
            return;
        }
        found.qty++;
        found.selectionOrder=++toppingSelectionOrder;
    }

    protected void removeOneFromCart(int menuId) {
        for (int i = cart.size() - 1; i >= 0; i--) {
            CartItem c = cart.get(i);
            if (c.id != menuId) continue;
            c.qty--;
            if (c.qty <= 0) cart.remove(i);
            return;
        }
    }

    protected void clearStickyCartBar() {
        if (stickyCartBar != null && stickyCartBar.getParent() instanceof FrameLayout) {
            ((FrameLayout) stickyCartBar.getParent()).removeView(stickyCartBar);
        }
        stickyCartBar = null;
        checkoutSubmitButton = null;
        cartQuantityLabel = null; cartTotalLabel = null;
        if (root != null) setFoodPadding(dp(16),dp(18),dp(16),dp(28));
    }

    protected void refreshCartUi() {
        updateCartButton();
        if(virtualFood!=null)virtualFood.updateQuantities(this::getQty);
        for (java.util.Map.Entry<Integer, TextView> entry : menuQuantityViews.entrySet())
            entry.getValue().setText(String.valueOf(getQty(entry.getKey())));
        syncToppingCards();
        buildCartBar();
    }

    private void saveMerchantDraft() {
        if (currentScreen != 1 || activeCart == null || cart.isEmpty()) return;
        if (ReliableOrderClient.hasPendingFoodDraft(this, activeCart.draftId)) {
            showInfo("Checkout masih diperiksa", "Buka Checkout untuk memeriksa hasil pesanan sebelumnya sebelum memasukkan pilihan ini ke keranjang.");
            return;
        }
        activeCart.name = firstNonEmpty(activeRestaurant.optString("name"), "Toko");
        activeCart.photo = activeRestaurant.optString("banner");
        int quantity = activeCart.quantity();
        if (!cartStore.addDraft(activeCart)) {
            showInfo("Keranjang", "Pilihan belum tersimpan. Maksimal 99 porsi per varian.");
            return;
        }
        merchantDrafts.remove(activeCart.restaurantId);
        loadActiveCart();
        refreshCartUi();
        android.widget.Toast.makeText(this, quantity + " item ditambahkan ke keranjang", android.widget.Toast.LENGTH_SHORT).show();
    }

    private ImageButton cartIconButton(String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_food_cart_premium);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackground(roundStroke("#EDF6FF", "#D7E9FD", dp(15), 1));
        button.setContentDescription(description);
        return button;
    }

    protected void buildCartBar() {
        if (!cart.isEmpty() && currentScreen == 1 && stickyCartBar != null && stickyCartBar.getParent() == page
                && cartQuantityLabel != null && cartTotalLabel != null) {
            int count = 0; for (CartItem item : cart) count += item.qty;
            cartQuantityLabel.setText(count + " item dipilih");
            cartTotalLabel.setText(rupiah(foodTotal()));
            return;
        }
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
        cartQuantityLabel = text(totalQty + " item dipilih", 12, "#64748B", false);
        cartTotalLabel = text(rupiah(foodTotal()), 20, "#0B3A78", true);
        left.addView(cartQuantityLabel);
        left.addView(cartTotalLabel);
        bar.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton add = cartIconButton("Masukkan pilihan ke keranjang");
        add.setOnClickListener(v -> saveMerchantDraft());
        bar.addView(add, new LinearLayout.LayoutParams(dp(46), dp(46)));
        Button checkout = primaryButton("Checkout");
        checkout.setTextSize(13);
        checkout.setPadding(dp(8), 0, dp(8), 0);
        checkout.setOnClickListener(v -> showCheckout());
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-2, dp(46));
        action.leftMargin = dp(6);
        bar.addView(checkout, action);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(dp(12), 0, dp(12), dp(10));
        page.addView(bar, lp);
        stickyCartBar = bar;
        setFoodPadding(dp(16),dp(18),dp(16),dp(116));
        bar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(stickyCartBar==bar&&currentScreen==1)setFoodPadding(dp(16),dp(18),dp(16),bar.getHeight()+dp(28));});
    }

    protected void showCheckout() {
        if (activeCart == null || cart.isEmpty() || foodOrderSubmitting) return;
        if (checkoutRestaurantId != activeCart.restaurantId) {
            checkoutRestaurantId = activeCart.restaurantId;
            deliveryMode = "standard";
            paymentMethod = "cash";
            voucherCode = defaultVoucherCode;
        }
        bindCheckoutSplitBill();
        royaltyPreviewKey = "";
        leaveVirtualFood();
        ++menuLoadGeneration;featureRuntime.cancel("food-menus");featureRuntime.cancel("food-social");menusRequestLoading=false;
        currentScreen = 2;
        voucherDraft=voucherCode;voucherExpanded=false;
        clearStickyCartBar();
        root.removeAllViews();
        buildTopBar("Checkout", "Satu pesanan untuk toko ini", true);
        addStatus("Menghitung ongkir...");
        if (foodScroll != null) foodScroll.scrollTo(0, 0);
        calculateOngkirThenRender();
    }



    private void bindCheckoutSplitBill() {
        StringBuilder splitKey = new StringBuilder(activeCart.draftId);
        for (CartItem line : cart) splitKey.append('/').append(line.lineId).append(':').append(line.qty);
        String splitScope = java.util.UUID.nameUUIDFromBytes(
                splitKey.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if (!splitScope.equals(splitCartDraft)) {
            if (splitBillManager != null) splitBillManager.dispose();
            splitCartDraft = splitScope;
            splitBillManager = new SplitBillManager(this, (key,size,ready) -> {
                if (currentScreen == 2 && !foodOrderSubmitting) renderCheckout();
            }, userId + ".food." + splitCartDraft + ".");
            if (splitBillManager.groupSize() > 1) paymentMethod = "balance";
        }
    }

    protected void renderCheckout() {
        if(currentScreen!=2 || root==null || activeRestaurant==null)return;
        int scrollY=foodScroll==null?0:foodScroll.getScrollY();
        clearStickyCartBar();
        root.removeAllViews();
        LinearLayout address=checkoutCard();
        LinearLayout addressTitle=new LinearLayout(this);addressTitle.setGravity(Gravity.CENTER_VERTICAL);
        addressTitle.addView(text("Antar ke",13,"#0F172A",true),new LinearLayout.LayoutParams(0,-2,1));
        Button edit=choiceButton("Ubah",false);edit.setTextSize(12);
        edit.setOnClickListener(v->{editingCheckoutAddress=true;DeliveryAddressGate.edit(this,"TransFood");});
        addressTitle.addView(edit,new LinearLayout.LayoutParams(dp(64),dp(44)));address.addView(addressTitle);
        address.addView(text("Penerima · "+firstNonEmpty(new SessionManager(this).getName(),username,"Customer"),12,"#64748B",false));
        TextView addressText=text(firstNonEmpty(DeliveryAddressGate.address(this),"Alamat belum tersedia"),13,"#64748B",false);
        addressText.setMaxLines(3);addressText.setEllipsize(android.text.TextUtils.TruncateAt.END);address.addView(addressText);
        addWithMargin(address,0,0,0,dp(10));

        LinearLayout items = compactCartCard();
        addCartMerchant(items, activeCart, null);
        if (distanceKm > 0) items.addView(text(String.format(Locale.US, "Jarak antar · %.2f km", distanceKm), 11, "#64748B", false));
        if (activeRestaurant.optInt("is_open",1)!=1) items.addView(text(firstNonEmpty(activeRestaurant.optString("availability_reason"),"Merchant sedang tutup"),12,"#EF4444",true));
        for (CartItem item : cart) addCheckoutProduct(items, activeCart, item, null, checkoutFromCarts);
        addWithMargin(items, 0, 0, 0, dp(8));

        LinearLayout noteCard = checkoutCard();
        singleCheckoutNote = addCheckoutNote(noteCard, activeCart, checkoutFromCarts);
        addWithMargin(noteCard, 0, 0, 0, dp(8));

        LinearLayout delivery=checkoutCard();delivery.addView(text("Pengantaran",14,"#0F172A",true));
        boolean coinReady=hematRemaining>=hematLimit;
        LinearLayout choices=new LinearLayout(this);choices.setPadding(0,dp(8),0,0);
        Button standard=checkoutChoice("Standar\n"+rupiah(standardFee),"standard".equals(deliveryMode));
        Button hemat=checkoutChoice("Pakai poin\n"+hematRemaining+" poin","hemat".equals(deliveryMode));
        hemat.setEnabled(coinReady);hemat.setAlpha(coinReady?1f:.65f);
        standard.setOnClickListener(v->{deliveryMode="standard";deliveryFee=standardFee;renderCheckout();});
        hemat.setOnClickListener(v->{deliveryMode="hemat";deliveryFee=standardFee;renderCheckout();});
        addCheckoutPair(choices,standard,hemat,56);delivery.addView(choices);
        if(!coinReady){TextView why=text("Saldo "+hematRemaining+" poin · perlu minimal "+hematLimit+" poin untuk Hemat.",11,"#64748B",false);why.setPadding(0,dp(6),0,0);delivery.addView(why);}
        addWithMargin(delivery,0,0,0,dp(10));

        LinearLayout voucher=checkoutCard();
        TextView voucherToggle=text(voucherCode.isEmpty()?"Punya voucher? Gunakan  ›":"Voucher dipilih · "+voucherCode+"  ›",13,"#0B7CFF",true);
        voucherToggle.setGravity(Gravity.CENTER_VERTICAL);voucherToggle.setMinHeight(dp(44));voucher.addView(voucherToggle);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setVisibility(voucherExpanded?View.VISIBLE:View.GONE);
        form.addView(text("Voucher berlaku untuk akun terverifikasi. Potongan dikonfirmasi saat pesanan diproses.",11,"#64748B",false));
        EditText code=new EditText(this);code.setSingleLine(true);code.setTextSize(14);code.setHint("Masukkan kode voucher");code.setText(voucherDraft);
        code.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){voucherDraft=s.toString();}public void afterTextChanged(Editable e){}});
        form.addView(code,new LinearLayout.LayoutParams(-1,dp(48)));
        Button apply=choiceButton("Gunakan voucher",false);apply.setTextSize(13);form.addView(apply,new LinearLayout.LayoutParams(-1,dp(44)));
        apply.setOnClickListener(v->{voucherCode=voucherDraft.trim().toUpperCase(Locale.US);voucherDraft=voucherCode;voucherExpanded=false;hideFoodKeyboard(code);renderCheckout();});
        voucherToggle.setOnClickListener(v->{voucherExpanded=!voucherExpanded;form.setVisibility(voucherExpanded?View.VISIBLE:View.GONE);if(!voucherExpanded)hideFoodKeyboard(code);voucherToggle.setText(voucherExpanded?"Voucher ongkir  ⌃":voucherCode.isEmpty()?"Punya voucher? Gunakan  ›":"Voucher dipilih · "+voucherCode+"  ›");});
        Button chooseRoyalty=choiceButton("Pilih voucher saya",false);chooseRoyalty.setTextSize(13);
        chooseRoyalty.setOnClickListener(v->RoyaltyVoucherPicker.show(this,(int)Math.round(foodTotal()+standardFee),(int)Math.round(standardFee),voucherCode,(selected,discount)->{voucherCode=selected;voucherDraft=selected;royaltyVoucherDiscount=discount;royaltyPreviewKey="";voucherExpanded=false;renderCheckout();}));voucher.addView(chooseRoyalty,new LinearLayout.LayoutParams(-1,dp(44)));
        voucher.addView(form);if(!royaltyVoucherNote.isEmpty())voucher.addView(text(royaltyVoucherNote,11,"#64748B",false));addWithMargin(voucher,0,0,0,dp(10));

        LinearLayout pay=checkoutCard();pay.addView(text("Pembayaran",14,"#0F172A",true));
        LinearLayout payments=new LinearLayout(this);payments.setPadding(0,dp(8),0,0);
        Button cash=checkoutChoice("Tunai","cash".equals(paymentMethod));Button balance=checkoutChoice("Transiva Pay","balance".equals(paymentMethod));
        cash.setOnClickListener(v -> {
            paymentMethod = "cash";
            if (splitBillManager != null && splitBillManager.groupSize() > 1) splitBillManager.clear();
            else renderCheckout();
        });
        balance.setOnClickListener(v->{paymentMethod="balance";renderCheckout();});
        addCheckoutPair(payments,cash,balance,48);pay.addView(payments);
        if("balance".equals(paymentMethod)){
            Button split=choiceButton(splitBillManager!=null&&splitBillManager.groupSize()>1?(splitBillManager.ready()?"✓ Split Bill ":"Menunggu Split Bill ")+splitBillManager.groupSize()+" orang":"Split Bill · Bayar bersama",false);
            split.setTextSize(12);split.setEnabled(checkoutQuoteReady && activeRestaurant.optInt("is_open", 1) == 1);split.setOnClickListener(v->showFoodSplitDialog());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.topMargin=dp(6);pay.addView(split,lp);
        }
        addWithMargin(pay,0,0,0,dp(10));

        LinearLayout total=checkoutCard();total.addView(text("Rincian pembayaran",14,"#0F172A",true));
        double grossTotal=foodTotal()+standardFee;
        String previewKey=checkoutQuoteGeneration+":"+voucherCode+":"+Math.round(grossTotal)+":"+standardFee;
        if(!previewKey.equals(royaltyPreviewKey)){royaltyPreviewKey=previewKey;royaltyVoucherDiscount=0;royaltyVoucherNote=voucherCode.isEmpty()?"":"Memeriksa potongan voucher…";final String expected=previewKey;
            if(!voucherCode.isEmpty())RoyaltyVoucherPicker.preview(this,voucherCode,(int)Math.round(grossTotal),(int)Math.round(standardFee),(discount,note)->{if(!expected.equals(royaltyPreviewKey)||currentScreen!=2)return;royaltyVoucherDiscount=discount;royaltyVoucherNote=note;renderCheckout();});}
        double afterVoucher=Math.max(0,grossTotal-royaltyVoucherDiscount);
        double coinDiscount=0;
        if("hemat".equals(deliveryMode)&&hematRemaining>=hematLimit)coinDiscount=CustomerFinancialRules.foodCoinDiscount(hematRemaining,coinValueRupiah,afterVoucher,coinMinOrderAfterDiscount,coinMaxRedeemPercent,hematLimit);
        total.addView(summaryLine("Subtotal makanan",foodTotal()));total.addView(summaryLine("Ongkir",standardFee));
        if(royaltyVoucherDiscount>0)total.addView(summaryLine("Potongan voucher",-royaltyVoucherDiscount));
        if(coinDiscount>0)total.addView(summaryLine("Potongan Poin Transiva",-coinDiscount));
        double amount=Math.max(0,afterVoucher-coinDiscount);lastFoodQuote=(int)Math.round(amount);
        if(!checkoutQuoteReady)total.addView(text("Ongkir belum terkonfirmasi. Coba hitung kembali sebelum memesan.",12,"#EF4444",false));
        total.addView(summaryLine("Total bayar",amount));addWithMargin(total,0,0,0,dp(12));
        buildCheckoutBar(amount);
        if(foodScroll!=null)foodScroll.post(()->{if(currentScreen==2)foodScroll.scrollTo(0,scrollY);});
    }

    private FoodCheckoutNote addCheckoutNote(LinearLayout parent, Cart orderCart, boolean savedCart) {
        String key = checkoutNoteKey(orderCart, savedCart);
        FoodCheckoutNote note = new FoodCheckoutNote(this, checkoutNotes.getString(key, ""), value -> {
            if (value.isEmpty()) checkoutNotes.remove(key);
            else checkoutNotes.putString(key, value);
            if (!parent.isAttachedToWindow()) {
                if (currentScreen == 2) renderCheckout();
                else if (currentScreen == COMBINED_CHECKOUT) renderCombinedCheckout();
            }
        });
        note.setEnabled(!foodOrderSubmitting);
        parent.addView(note, new LinearLayout.LayoutParams(-1, -2));
        return note;
    }

    private static String checkoutNoteKey(Cart orderCart, boolean savedCart) {
        if (!savedCart) return "direct:" + orderCart.draftId;
        List<String> ids = new ArrayList<>();
        for (CartItem item : orderCart.items) ids.add(item.lineId + ":" + item.qty);
        java.util.Collections.sort(ids);
        return "saved:" + orderCart.draftId + ":" + android.text.TextUtils.join("/", ids);
    }

    private void hideFoodKeyboard(View field){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);}
    private LinearLayout checkoutCard(){LinearLayout c=card();c.setPadding(dp(14),dp(12),dp(14),dp(12));return c;}
    private Button checkoutChoice(String label,boolean selected){
        Button b=choiceButton((selected?"✓ ":"")+label,selected);b.setTextSize(13);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);b.setContentDescription(label+(selected?", dipilih":""));return b;
    }
    private void addCheckoutPair(LinearLayout row,View left,View right,int height){
        row.addView(left,new LinearLayout.LayoutParams(0,dp(height),1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(height),1);lp.leftMargin=dp(8);row.addView(right,lp);
    }
    private void buildCheckoutBar(double amount){
        LinearLayout bar=checkoutCard();bar.setGravity(Gravity.CENTER_VERTICAL);bar.setElevation(dp(12));
        boolean narrow=getResources().getConfiguration().screenWidthDp<360 || getResources().getConfiguration().fontScale>1.2f;
        bar.setOrientation(narrow?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(text("Total bayar",11,"#64748B",false));words.addView(text(rupiah(amount),20,"#0F172A",true));
        bar.addView(words,narrow?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        boolean accepting=refreshCheckoutRestaurant || activeRestaurant.optInt("is_open",1)==1;
        Button order=primaryButton(!accepting?"Toko sedang tutup":!checkoutQuoteReady?"Coba hitung ongkir":"Buat Pesanan");checkoutSubmitButton=order;order.setTextSize(14);order.setEnabled(accepting&&!foodOrderSubmitting);order.setAlpha(accepting?1f:.55f);order.setOnClickListener(v->{if(checkoutQuoteReady)createFoodOrder();else calculateOngkirThenRender();});
        LinearLayout.LayoutParams olp=new LinearLayout.LayoutParams(narrow?-1:dp(150),dp(48));if(narrow)olp.topMargin=dp(8);else olp.leftMargin=dp(8);bar.addView(order,olp);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);lp.setMargins(dp(12),0,dp(12),dp(10));page.addView(bar,lp);stickyCartBar=bar;
        bar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(stickyCartBar==bar&&currentScreen==2)setFoodPadding(dp(16),dp(18),dp(16),bar.getHeight()+dp(28));});
    }

    protected void showFoodSplitDialog() {
        if (!"balance".equals(paymentMethod)) { showInfo("Split Bill","Pilih Transiva Pay terlebih dahulu."); return; }
        final String[] opts={"Sendiri","2 orang","3 orang","4 orang"};
        new TransivaAlertDialogBuilder(this).setTitle("Split Bill TransFood").setSingleChoiceItems(opts,Math.max(0,Math.min(3,splitBillManager.groupSize()-1)),(d,w)->{d.dismiss();if(w==0){splitBillManager.clear();renderCheckout();}else splitBillManager.start(w+1,lastFoodQuote,"food");}).setNegativeButton("Batal",null).show();
    }

    protected LinearLayout summaryLine(String label, double value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 14, "#64748B", false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(rupiah(value), 15, "#0F172A", true));
        return row;
    }

    private String restaurantPayloadSignature = "";
    private boolean menuIndexLoading = false;
    private String menuIndexSignature = "";
    private long lastMenuIndexAt = 0L;
    private boolean catalogCacheServing;
    private String acceptedRestaurantContext="",inFlightRestaurantContext="";
    private String restaurantRequestContext(){return homeMode+":"+homeSearchQuery.trim();}
    private long catalogRefreshEpoch;
    private int restaurantOffset,restaurantGeneration;
    private boolean restaurantHasMore;
    protected void loadRestaurants() { loadRestaurants(true); }
    protected void loadRestaurants(boolean showLoading) {
        if(restaurantsLoading){
            if(inFlightRestaurantContext.equals(restaurantRequestContext()))return;
            ++restaurantGeneration;featureRuntime.cancel("food-merchants");restaurantsLoading=false;
        }
        // Refresh the loaded prefix; unchanged cards and later loaded pages stay visible.
        loadRestaurantPage(0);
    }
    private void loadRestaurantPage(int offset) {
        if(restaurantsLoading)return;
        restaurantsLoading=true;foodLoadError="";
        final int generation=++restaurantGeneration;
        final String context=restaurantRequestContext();inFlightRestaurantContext=context;
        final boolean replace=!context.equals(acceptedRestaurantContext);
        final int requestedLimit=offset==0&&!replace?Math.max(20,Math.min(50,restaurantOffset)):20;
        final String query=homeSearchQuery.trim(),sort=homeMode;final long refresh=catalogRefreshEpoch;
        if(currentScreen==0)renderHomeResults();
        featureRuntime.executeLatest("food-merchants",CustomerLoadingCoordinator.CONTENT,()->{
            try{
                String url=BASE_URL+"server/get_food_restaurants.php?user_id="+userId+"&limit="+requestedLimit+"&offset="+offset+"&q="+Uri.encode(query)+"&sort="+Uri.encode(sort)+"&refresh="+refresh;
                JSONObject cached=offset==0?TransivaHttpRepository.cachedCatalog(this,url):null;
                if(cached!=null&&cached.optBoolean("success")&&cached.optJSONArray("restaurants")!=null)featureRuntime.post(mainHandler,()->acceptRestaurantPage(cached,cached.optJSONArray("restaurants"),offset,requestedLimit,context,replace,generation,true));
                JSONObject response=TransivaHttpRepository.getJsonFresh(this,url,TIMEOUT_MS);
                JSONArray rows=response.optJSONArray("restaurants");
                if(!response.optBoolean("success")||rows==null)throw new Exception("Gagal memuat merchant");
                featureRuntime.post(mainHandler,()->acceptRestaurantPage(response,rows,offset,requestedLimit,context,replace,generation,false));
            }catch(Exception error){featureRuntime.post(mainHandler,()->{
                if(generation!=restaurantGeneration||!context.equals(restaurantRequestContext()))return;
                restaurantsLoading=false;restaurantsLoadFinished=true;foodLoadError="network";
                if(currentScreen==0&&activeRestaurant==null)renderHomeResults();
            });}
        },()->featureRuntime.post(mainHandler,()->{restaurantsLoading=false;foodLoadError="busy";restaurantsLoadFinished=true;if(currentScreen==0)renderHomeResults();}));
    }

    private void acceptRestaurantPage(JSONObject response,JSONArray rows,int offset,int requestedLimit,String context,boolean replace,int generation,boolean cached){
                    if(generation!=restaurantGeneration||!context.equals(restaurantRequestContext()))return;
                    if(!cached)restaurantsLoading=false;restaurantsLoadFinished=true;foodLoadError="";catalogCacheServing=cached;
                    // Merge by id; a refresh of the prefix never discards already loaded later pages.
                    java.util.LinkedHashMap<Integer,JSONObject> merged=new java.util.LinkedHashMap<>();
                    if(offset==0)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)merged.put(row.optInt("id"),row);}
                    if(offset>0)for(JSONObject row:restaurants)merged.put(row.optInt("id"),row);
                    else if(!replace&&response.optBoolean("has_more"))for(int i=Math.min(requestedLimit,restaurants.size());i<restaurants.size();i++){JSONObject row=restaurants.get(i);if(!merged.containsKey(row.optInt("id")))merged.put(row.optInt("id"),row);}
                    if(offset>0)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)merged.put(row.optInt("id"),row);}
                    acceptedRestaurantContext=context;restaurants.clear();restaurants.addAll(merged.values());
                    if(replace||offset>0||restaurantOffset<=requestedLimit||!response.optBoolean("has_more")){restaurantOffset=response.optInt("next_offset",offset+rows.length());restaurantHasMore=response.optBoolean("has_more");}
                    if(currentScreen==0&&activeRestaurant==null)renderHomeResults();
                    if(!cached){if(lastMenuIndexAt==0&&!menuIndexLoading)loadAllMenuIndex();refreshMerchantStories();}
    }

    private int menuSearchGeneration = 0;
    private String lastMenuQuery = "",lastMenuMode="";
    private int menuSearchOffset;
    private boolean menuSearchHasMore;
    protected void loadAllMenuIndex() { loadMenuSearch(homeSearchQuery); }
    private void loadMenuSearch(String query) { loadMenuSearchPage(query,0); }
    private void loadMenuSearchPage(String query,int offset) {
        final String requested = query == null ? "" : query.trim();
        final String mode=homeMode;
        if(offset>0&&menuIndexLoading)return;
        if (offset==0 && requested.equals(lastMenuQuery) && mode.equals(lastMenuMode) && android.os.SystemClock.elapsedRealtime()-lastMenuIndexAt < 30000L) return;
        final int generation=++menuSearchGeneration;
        menuIndexLoading = true;menuSearchOffset=offset;if(currentScreen==0)renderHomeResults();
        featureRuntime.executeLatest("food-search",CustomerLoadingCoordinator.SEARCH,() -> {
            try {
                JSONObject response = getJson(BASE_URL + "server/search_food_menus.php?user_id=" + userId + "&q=" + Uri.encode(requested) + "&limit=30&offset="+offset+"&favorites="+("favorites".equals(mode)?1:0));
                if (!response.optBoolean("success", false)) throw new Exception("Search unavailable");
                JSONArray rows = response.optJSONArray("menus");
                List<MenuSearchItem> fresh = new ArrayList<>();
                if (rows != null) for (int i=0; i<rows.length(); i++) {
                    JSONObject m=rows.optJSONObject(i); if(m==null)continue;
                    MenuSearchItem item=new MenuSearchItem();
                    item.restaurantId=m.optInt("restaurant_id");item.menuId=m.optInt("id");
                    item.restaurantName=m.optString("restaurant_name","Merchant");
                    item.menuName=m.optString("name","Menu");item.category=m.optString("category","Menu");
                    item.description=m.optString("description","");item.image=m.optString("image","assets/no-image.png");
                    item.price=m.optDouble("price");item.originalPrice=m.optDouble("original_display_price",m.optDouble("original_price",item.price));
                    item.discountPercent=m.optDouble("discount_percent");item.discountActive=m.optInt("discount_active")==1;
                    item.restaurantRating=m.optDouble("restaurant_rating");item.restaurantDistanceKm=15d;
                    item.restaurant=m.optJSONObject("restaurant");if(item.restaurant!=null)item.restaurantDistanceKm=item.restaurant.optDouble("distance_km",15d);
                    item.active=m.optInt("is_active",1)==1;item.stock=m.optInt("stock",-1);item.trackStock=m.optInt("track_stock")==1;item.salesCount=m.optLong("sales_count");
                    if(item.trackStock&&item.stock<=0)item.active=false;if(!item.active)continue;

                    if(item.restaurant==null){item.restaurant=new JSONObject();item.restaurant.put("id",item.restaurantId);item.restaurant.put("name",item.restaurantName);}
                    fresh.add(item);
                }
                featureRuntime.post(mainHandler,()->{
                    if(generation!=menuSearchGeneration||!requested.equals(homeSearchQuery.trim())||!mode.equals(homeMode))return;
                    menuIndexLoading=false;lastMenuMode=mode;lastMenuQuery=requested;lastMenuIndexAt=android.os.SystemClock.elapsedRealtime();
                    for(MenuSearchItem item:fresh){JSONObject store=findRestaurantById(item.restaurantId);if(store!=null){item.restaurant=store;item.restaurantDistanceKm=store.optDouble("distance_km",15d);}}
                    menuSearchOffset=response.optInt("next_offset",offset+fresh.size());menuSearchHasMore=response.optBoolean("has_more");
                    String signature=mode+":"+requested+":"+offset+":"+response.optString("signature",rows==null?"":rows.toString());
                    if(!signature.equals(menuIndexSignature)){
                        menuIndexSignature=signature;if(offset==0)allMenuSearchItems.clear();
                        java.util.Set<Integer> ids=new java.util.HashSet<>();for(MenuSearchItem item:allMenuSearchItems)ids.add(item.menuId);
                        for(MenuSearchItem item:fresh)if(ids.add(item.menuId))allMenuSearchItems.add(item);
                    }
                    if(currentScreen==0&&activeRestaurant==null)renderHomeResults();
                });
            }catch(Exception ignored){featureRuntime.post(mainHandler,()->{if(generation==menuSearchGeneration){menuIndexLoading=false;lastMenuIndexAt=0;menuSearchHasMore=true;if(currentScreen==0)renderHomeResults();}});}
        },()->featureRuntime.post(mainHandler,()->{if(generation==menuSearchGeneration){menuIndexLoading=false;lastMenuIndexAt=0;menuSearchHasMore=true;if(currentScreen==0)renderHomeResults();}}));
    }

    private int menusOffset,menusRequestRid,failedMenusOffset;
    private boolean menusHasMore,menusRequestLoading,menusCached;
    private String menusRequestQuery="",menusError="",acceptedMenuContext="";
    protected void loadMenus(int rid){loadMenus(rid,true);}
    protected void loadMenus(int rid,boolean showLoading){if(currentScreen==1)loadMenuPage(rid,0,showLoading);}
    private void loadMenuPage(int rid,int pageOffset,boolean showLoading){
        String requested=menuSearchQuery==null?"":menuSearchQuery.trim();
        final boolean newContext=!(rid+":"+requested).equals(acceptedMenuContext);
        final int offset=newContext?0:pageOffset;
        if(menusRequestLoading&&menusRequestRid==rid&&menusRequestQuery.equals(requested))return;
        final int generation=++menuLoadGeneration;menusRequestLoading=true;menusRequestRid=rid;menusRequestQuery=requested;menusError="";failedMenusOffset=offset;
        final int limit=offset==0&&!newContext?Math.max(40,Math.min(50,menusOffset)):40;
        if(showLoading&&menus.isEmpty()){setLoading(true);menusLoadFinished=false;}
        final String url=BASE_URL+"server/get_food_menus.php?restaurant_id="+rid+"&q="+Uri.encode(requested)+"&limit="+limit+"&offset="+offset;
        Runnable rejected=()->featureRuntime.post(mainHandler,()->menuPageFailed(rid,generation,"Sistem sedang sibuk. Coba lagi."));
        featureRuntime.executeLatest("food-menus",CustomerLoadingCoordinator.CONTENT,()->{
            try{
                JSONObject cached=offset==0?TransivaHttpRepository.cachedCatalog(this,url):null;
                if(cached!=null&&cached.optJSONArray("menus")!=null)featureRuntime.post(mainHandler,()->acceptMenuPage(cached,rid,offset,limit,generation,true));
                JSONObject response=TransivaHttpRepository.getJsonFresh(this,url,TIMEOUT_MS);
                if(!response.optBoolean("success")||response.optJSONArray("menus")==null)throw new Exception(response.optString("message","Gagal memuat menu"));
                featureRuntime.post(mainHandler,()->acceptMenuPage(response,rid,offset,limit,generation,false));
            }catch(Exception error){featureRuntime.post(mainHandler,()->menuPageFailed(rid,generation,"Koneksi gagal. Data yang sudah tampil tetap tersedia."));}
        },rejected);
    }
    private void acceptMenuPage(JSONObject response,int rid,int offset,int limit,int generation,boolean cached){
        if(generation!=menuLoadGeneration||activeRestaurant==null||activeRestaurant.optInt("id")!=rid||currentScreen!=1)return;
        boolean newContext=!(rid+":"+menusRequestQuery).equals(acceptedMenuContext);
        if(!cached)menusRequestLoading=false;menusCached=cached;menusError="";menusLoadFinished=true;setLoading(false);
        JSONObject detail=response.optJSONObject("restaurant");if(detail!=null){java.util.Iterator<String> keys=detail.keys();while(keys.hasNext()){String key=keys.next();Object value=detail.opt(key);if(value instanceof String&&((String)value).trim().isEmpty()&&activeRestaurant.has(key))continue;try{activeRestaurant.put(key,value);}catch(Exception ignored){}}}
        java.util.LinkedHashMap<Integer,JSONObject> old=new java.util.LinkedHashMap<>(),merged=new java.util.LinkedHashMap<>();for(JSONObject menu:menus)old.put(menu.optInt("id"),menu);
        if(offset>0)merged.putAll(old);
        JSONArray rows=response.optJSONArray("menus");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;JSONObject previous=old.get(row.optInt("id"));
            if(previous!=null)for(String key:new String[]{"is_favorite","rating","review_count"})if(previous.has(key)&&!row.has(key))try{row.put(key,previous.opt(key));}catch(Exception ignored){}
            merged.put(row.optInt("id"),row);
        }
        if(offset==0&&!newContext&&response.optBoolean("has_more"))for(int i=Math.min(limit,menus.size());i<menus.size();i++){JSONObject row=menus.get(i);if(!merged.containsKey(row.optInt("id")))merged.put(row.optInt("id"),row);}
        menus.clear();menus.addAll(merged.values());
        if(newContext||offset>0||menusOffset<=limit||!response.optBoolean("has_more")){menusOffset=response.optInt("next_offset",offset+rows.length());menusHasMore=response.optBoolean("has_more");}
        acceptedMenuContext=rid+":"+menusRequestQuery;renderMenus();if(!cached){loadMenuSocial(rid,generation);refreshMerchantStories();refreshProfileStories(rid);}
    }
    private void menuPageFailed(int rid,int generation,String message){
        if(generation!=menuLoadGeneration||activeRestaurant==null||activeRestaurant.optInt("id")!=rid||currentScreen!=1)return;
        menusRequestLoading=false;menusLoadFinished=true;menusError=message;setLoading(false);renderMenus();
    }
    private void addMenuPageControls(){
        if(menusCached)addStatus("Data menu tersimpan · "+(menusRequestLoading?"memperbarui…":"tarik/kembali untuk memperbarui"));
        if(!menusError.isEmpty())addStatus(menusError);
        if(menusHasMore||!menusError.isEmpty()){
            TextView more=text(menusRequestLoading?"Memuat menu…":!menusError.isEmpty()?"↻ Coba lagi":"Lihat menu berikutnya",13,"#087BFF",true);
            more.setPadding(dp(12),dp(14),dp(12),dp(14));more.setGravity(Gravity.CENTER);more.setEnabled(!menusRequestLoading);
            more.setOnClickListener(v->{if(activeRestaurant!=null)loadMenuPage(activeRestaurant.optInt("id"),menusError.isEmpty()?menusOffset:failedMenusOffset,false);});addWithMargin(more,0,0,0,dp(12));
        }
    }

    private void loadMenuSocial(int rid,int generation){
        featureRuntime.executeLatest("food-social",CustomerLoadingCoordinator.OPTIONAL,()->{
            try{
                JSONObject social=getJson(BASE_URL+"server/customer_food_social.php?restaurant_id="+rid);
                featureRuntime.post(mainHandler,()->{
                    if(generation!=menuLoadGeneration||currentScreen!=1||activeRestaurant==null||activeRestaurant.optInt("id")!=rid)return;
                    String before=menuSnapshot();mergeFoodSocial(social);if(!before.equals(menuSnapshot()))renderMenus();
                });
            }catch(Exception ignored){}
        },null);
    }

    private String menuSnapshot() {
        try { return OrderPayloadFingerprint.canonical(activeRestaurant) + OrderPayloadFingerprint.canonical(new JSONArray(menus)); }
        catch (Exception ignored) { return ""; }
    }

    private long storyNow() { return kabarClock + (android.os.SystemClock.elapsedRealtime() - kabarAnchor) / 1000; }
    private JSONObject cachedKabarFeed() {
        if (kabarFeed == null) return null;
        try { JSONObject copy = new JSONObject(kabarFeed.toString()); copy.put("server_time", storyNow()); return copy; }
        catch (Exception ignored) { return null; }
    }
    private void acceptKabarFeed(JSONObject feed) {
        kabarFeed = feed; kabarClock = feed.optLong("server_time"); kabarAnchor = android.os.SystemClock.elapsedRealtime();
        kabarNextFetch = kabarAnchor + 30000;
        updateMerchantAvatars();
        updateFoodStoryRail();
    }
    private KabarMitraUi.Avatar merchantAvatar(JSONObject merchant, int size) {
        KabarMitraUi ui = new KabarMitraUi(this);
        int rid = merchant.optInt("id");
        KabarMitraUi.Avatar avatar = ui.avatar(firstNonEmpty(merchant.optString("banner"), "assets/default-food.png"), size,
                kabarFeed != null && KabarMitraUi.hasStory(kabarFeed.optJSONArray("stories"), rid, storyNow()));
        java.util.List<KabarMitraUi.Avatar> list = merchantAvatars.get(rid);
        if (list == null) { list = new ArrayList<>(); merchantAvatars.put(rid, list); }
        avatar.setTag("avatar:"+rid);list.add(avatar); updateMerchantAvatars(); return avatar;
    }
    private void updateMerchantAvatars() {
        JSONArray feedStories = kabarFeed == null ? null : kabarFeed.optJSONArray("stories");
        for (java.util.Map.Entry<Integer, java.util.List<KabarMitraUi.Avatar>> entry : merchantAvatars.entrySet()) {
            int rid = entry.getKey();
            JSONArray stories = rid == detailStoryRid && detailStoryResponse != null ? detailStoryResponse.optJSONArray("stories") : feedStories;
            long now = rid == detailStoryRid && detailStoryResponse != null
                    ? detailStoryResponse.optLong("server_time") + (android.os.SystemClock.elapsedRealtime() - detailStoryAnchor)/1000 : storyNow();
            boolean available = KabarMitraUi.hasStory(stories, rid, now);
            if (!available && kabarFeed != null && !(rid == detailStoryRid && detailStoryResponse != null)) {
                for (String section : new String[]{"followed", "discover"}) {
                    JSONArray stores = kabarFeed.optJSONArray(section);
                    if (stores != null) for (int i=0;i<stores.length();i++) {
                        JSONObject store=stores.optJSONObject(i);
                        if (store!=null && store.optInt("id")==rid && store.optLong("story_expires_at")>now) available=true;
                    }
                }
            }
            for (KabarMitraUi.Avatar avatar : entry.getValue()) {
                avatar.setStory(available);
                if (available) avatar.setOnClickListener(v -> new KabarMitraUi(this).openStories(featureRuntime, mainHandler, rid, stories,
                        rid == detailStoryRid && detailStoryResponse != null ? detailStoryResponse.optLong("server_time")+(android.os.SystemClock.elapsedRealtime()-detailStoryAnchor)/1000 : storyNow(), avatar));
                else { avatar.setOnClickListener(null); avatar.setClickable(false); }
            }
        }
    }
    private void refreshProfileStories(int rid) {
        if(pendingProfileRid==rid||(rid==detailStoryRid&&detailStoryResponse!=null&&android.os.SystemClock.elapsedRealtime()-detailStoryAnchor<30000))return;
        pendingProfileRid=rid;final int generation=++profileStoryGeneration;
        Runnable failed=()->featureRuntime.post(mainHandler,()->{if(generation==profileStoryGeneration)pendingProfileRid=0;});
        featureRuntime.executeLatest("food-profile-story",CustomerLoadingCoordinator.OPTIONAL,()->{
            try{
                JSONObject response=getJson(BASE_URL+"server/customer_kabar_mitra.php?restaurant_id="+rid);
                featureRuntime.post(mainHandler,()->{
                    if(generation!=profileStoryGeneration)return;pendingProfileRid=0;
                    if(activeRestaurant!=null&&activeRestaurant.optInt("id")==rid&&response.optBoolean("success")&&response.has("stories")){
                        detailStoryRid=rid;detailStoryResponse=response;detailStoryAnchor=android.os.SystemClock.elapsedRealtime();updateMerchantAvatars();
                    }
                });
            }catch(Exception error){failed.run();}
        },failed);
    }
    private int storyOffset,storyCursor;
    private boolean storyHasMore;
    private void refreshMerchantStories() { refreshMerchantStories(null); }
    private void refreshMerchantStories(Runnable complete) {
        if(kabarFetching || android.os.SystemClock.elapsedRealtime()<kabarNextFetch){if(complete!=null)complete.run();return;}
        loadStoryPage(0,complete);
    }
    void openCompleteStory(int rid,View source){new KabarMitraUi(this).openStories(featureRuntime,mainHandler,rid,null,storyNow(),source);}
    private void loadMoreStories(){if(storyHasMore&&!kabarFetching)loadStoryPage(storyOffset,null);}
    private void loadStoryPage(int offset,Runnable complete){
        kabarFetching=true;updateFoodStoryRail();
        Runnable failed=()->featureRuntime.post(mainHandler,()->{kabarFetching=false;kabarNextFetch=android.os.SystemClock.elapsedRealtime()+5000;updateFoodStoryRail();if(complete!=null)complete.run();});
        java.util.concurrent.Future<?> task=featureRuntime.executeLatest("food-story-feed",CustomerLoadingCoordinator.OPTIONAL,()->{
            try{
                JSONObject response=getJson(BASE_URL+"server/customer_kabar_mitra.php?limit=20&offset="+offset+"&before_story_id="+(offset>0?storyCursor:0));
                if(!response.optBoolean("success"))throw new Exception("Stories unavailable");
                featureRuntime.post(mainHandler,()->{
                    kabarFetching=false;
                    if(kabarFeed!=null){
                        for(String section:new String[]{"stories","followed","discover"}){
                            JSONArray incoming=response.optJSONArray(section),old=kabarFeed.optJSONArray(section),combined=new JSONArray();java.util.Set<Integer> ids=new java.util.HashSet<>();
                            int boundary=Integer.MAX_VALUE;if(incoming!=null)for(int i=0;i<incoming.length();i++){JSONObject row=incoming.optJSONObject(i);if(row!=null)boundary=Math.min(boundary,row.optInt("id"));}
                            if(incoming!=null)for(int i=0;i<incoming.length();i++){JSONObject row=incoming.optJSONObject(i);if(row!=null&&ids.add(row.optInt("id")))combined.put(row);}
                            if(old!=null)for(int i=0;i<old.length();i++){
                                JSONObject row=old.optJSONObject(i);if(row==null)continue;
                                boolean story="stories".equals(section);
                                if(story&&row.optLong("expires_at")<=response.optLong("server_time"))continue;
                                if(offset==0&&((story&&row.optInt("id")>=boundary)||(!story&&i<20)))continue;
                                if(ids.add(row.optInt("id")))combined.put(row);
                            }
                            try{response.put(section,combined);}catch(Exception ignored){}
                        }
                    }
                    if(offset>0||storyOffset<=20){storyOffset=response.optInt("next_offset",offset+20);storyHasMore=response.optBoolean("has_more");storyCursor=response.optInt("next_story_before",storyCursor);}
                    acceptKabarFeed(response);if(complete!=null)complete.run();
                });
            }catch(Exception error){failed.run();}
        },failed);
        if(task==null){kabarFetching=false;if(complete!=null)complete.run();}
    }

    protected void calculateOngkirThenRender() {
        if(currentScreen!=2||activeRestaurant==null)return;
        final int quoteGeneration=++checkoutQuoteGeneration,restaurantId=activeRestaurant.optInt("id");
        final String quoteMode=deliveryMode;
        final boolean checkRestaurant=refreshCheckoutRestaurant;
        checkoutQuoteReady=false;
        deliveryFee=standardFee=hematFee=distanceKm=0;
        clearStickyCartBar();setLoading(true);
        java.util.concurrent.Future<?> task=featureRuntime.execute(() -> {
            try {
                if (checkRestaurant) {
                    JSONObject catalog = TransivaHttpRepository.getJsonFresh(this, BASE_URL
                            + "server/get_food_menus.php?restaurant_id=" + restaurantId + "&limit=1", TIMEOUT_MS);
                    JSONObject restaurant = catalog.optJSONObject("restaurant");
                    if (!catalog.optBoolean("success") || restaurant == null || restaurant.optInt("id") != restaurantId)
                        throw new IllegalStateException("Toko belum dapat diperiksa.");
                    boolean closed = restaurant.optInt("is_open", 1) != 1;
                    featureRuntime.post(mainHandler, () -> {
                        if (quoteGeneration != checkoutQuoteGeneration || currentScreen != 2) return;
                        activeRestaurant = restaurant;
                        refreshCheckoutRestaurant = false;
                        if (closed) {
                            setLoading(false);
                            renderCheckout();
                        }
                    });
                    if (closed) return;
                }
                String url=BASE_URL+"server/calculateOngkir.php?service_type=Transbike&restaurant_id="+restaurantId+"&user_id="+userId+"&delivery_mode="+Uri.encode(quoteMode)+"&v="+System.currentTimeMillis();
                JSONObject res=getJson(url);
                if(!res.optBoolean("success",false))throw new Exception(firstNonEmpty(res.optString("message"),"Gagal menghitung ongkir"));
                featureRuntime.post(mainHandler,()->{
                    if(quoteGeneration!=checkoutQuoteGeneration||currentScreen!=2||activeRestaurant==null||activeRestaurant.optInt("id")!=restaurantId)return;
                    standardFee=res.optDouble("standard_price",res.optDouble("price",0));
                    // Keep the existing server quote and Coin calculation; commit one response on the UI thread.
                    deliveryFee=hematFee=standardFee;distanceKm=res.optDouble("distance_km",0);
                    hematRemaining=res.optInt("hemat_remaining",0);hematLimit=res.optInt("hemat_limit",0);
                    hematTier=firstNonEmpty(res.optString("hemat_tier"),"BRONZE");coinValueRupiah=res.optInt("coin_value_rupiah",1);coinMinOrderAfterDiscount=res.optInt("coin_min_order_after_discount",1000);coinMaxRedeemPercent=res.optInt("coin_max_redeem_percent",100);
                    TierBadgeUi.saveActiveTier(this,hematTier);checkoutQuoteReady=true;setLoading(false);renderCheckout();
                });
            }catch(Exception e){featureRuntime.post(mainHandler,()->{
                if(quoteGeneration!=checkoutQuoteGeneration||currentScreen!=2)return;
                setLoading(false);deliveryFee=standardFee=hematFee=0;distanceKm=0;renderCheckout();showInfo("Ongkir",e.getMessage());
            });}
        },()->featureRuntime.post(mainHandler,()->{if(quoteGeneration==checkoutQuoteGeneration&&currentScreen==2){setLoading(false);renderCheckout();showInfo("Ongkir","Sistem sedang sibuk. Coba hitung ongkir lagi, ya.");}}));
        if(task==null){setLoading(false);renderCheckout();showInfo("Ongkir","Sistem sedang sibuk. Coba hitung ongkir lagi, ya.");}
    }

    protected void createFoodOrder() {
        if(foodOrderSubmitting || currentScreen!=2)return;
        if(!checkoutQuoteReady){calculateOngkirThenRender();return;}
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (activeRestaurant == null || activeRestaurant.optInt("id", 0) <= 0) { showInfo("Gagal", "Merchant tidak valid."); return; }
        if (activeRestaurant.optInt("is_open", 1) != 1) {
            showInfo("Merchant tidak menerima pesanan", firstNonEmpty(activeRestaurant.optString("availability_reason"), "Merchant sedang tutup atau pause."));
            return;
        }
        if (cart.isEmpty()) { showInfo("Keranjang kosong", "Tambahkan menu terlebih dahulu."); return; }
        if ("balance".equals(paymentMethod) && splitBillManager != null && splitBillManager.groupSize()>1 && !splitBillManager.ready()) { showInfo("Split Bill","Tunggu semua peserta menerima undangan."); splitBillManager.showStatus(); return; }
        final JSONObject payload = new JSONObject();
        try {
            payload.put("user_id", userId);
            payload.put("restaurant_id", activeRestaurant.optInt("id", 0));
            payload.put("delivery_mode", deliveryMode);
            payload.put("payment_method", paymentMethod);
            payload.put("voucher_code", voucherCode);
            if (splitBillManager != null && !splitBillManager.sessionKey().isEmpty()) payload.put("split_session_key", splitBillManager.sessionKey());
            if (splitBillManager != null && splitBillManager.groupSize() > 1) payload.put("split_bill_required", true);
            payload.put("items", FoodCombinedCheckout.items(cart, checkoutFromCarts));
            FoodCheckoutNote.addTo(payload, checkoutNotes.getString(checkoutNoteKey(activeCart, checkoutFromCarts), ""));
            if (!checkoutFromCarts) payload.put("_direct_food_draft", activeCart.draftId);
        }catch(Exception invalid){showInfo("Pesanan","Rincian pesanan belum siap. Silakan coba lagi.");return;}
        requestFoodConfirmation(payload, null);
    }

    private void requestFoodConfirmation(JSONObject payload, JSONObject updatedQuote) {
        if (currentScreen!=2 || isFinishing() || isDestroyed()) return;
        foodOrderSubmitting=true;
        if (checkoutSubmitButton!=null) checkoutSubmitButton.setEnabled(false);
        if (singleCheckoutNote != null) singleCheckoutNote.setEnabled(false);
        if (updatedQuote!=null) {
            presentFoodQuote(payload,updatedQuote,true);
            return;
        }
        setLoading(true);
        featureRuntime.execute(() -> {
            try {
                String url=BASE_URL+"server/create_food_order.php";
                JSONObject recovered=ReliableOrderClient.recoverPendingFood(this,url,TIMEOUT_MS);
                if (recovered!=null) {
                    featureRuntime.post(mainHandler,()->{
                        finishFoodSubmission();
                        if(currentScreen==2) completeCartCheckout(recovered);
                    });
                    return;
                }
                JSONObject quote=FoodCheckoutConfirmation.quote(this,url,payload,TIMEOUT_MS);
                featureRuntime.post(mainHandler,()->{
                    setLoading(false);
                    if(currentScreen==2) presentFoodQuote(payload,quote,quote.optLong("total")!=lastFoodQuote
                            || quote.optLong("food_total")!=Math.round(foodTotal())
                            || quote.optLong("delivery_fee")!=Math.round(standardFee));
                    else finishFoodSubmission();
                });
            } catch (Exception error) {
                featureRuntime.post(mainHandler,()->foodSubmissionError(error));
            }
        },()->featureRuntime.post(mainHandler,()->foodSubmissionError(new IllegalStateException("Sistem sedang sibuk. Coba lagi."))));
    }

    private void presentFoodQuote(JSONObject payload,JSONObject quote,boolean changed) {
        FoodCheckoutConfirmation.show(this,quote,changed,payload.optString("customer_note"),fingerprint->{
            try {
                JSONObject confirmed=new JSONObject(payload.toString()).put("expected_quote",fingerprint);
                submitConfirmedFood(confirmed);
            } catch (Exception error) { foodSubmissionError(error); }
        },this::finishFoodSubmission);
    }

    private void submitConfirmedFood(JSONObject payload) {
        setLoading(true);
        featureRuntime.execute(() -> {
            try {
                JSONObject response=ReliableOrderClient.create(this,BASE_URL+"server/create_food_order.php",payload,TIMEOUT_MS);
                featureRuntime.post(mainHandler,()->{
                    finishFoodSubmission();
                    if(currentScreen!=2) return;
                    if(response.optBoolean("success")) {
                        completeCartCheckout(response);
                        return;
                    }
                    JSONObject next=response.optJSONObject("quote");
                    if ("FOOD_QUOTE_CHANGED".equals(response.optString("code")) && next!=null) {
                        requestFoodConfirmation(payload,next);
                        return;
                    }
                    showInfo("Gagal",response.optString("message","Pesanan belum dibuat."));
                });
            } catch (Exception error) {
                featureRuntime.post(mainHandler,()->foodSubmissionError(error));
            }
        },()->featureRuntime.post(mainHandler,()->foodSubmissionError(new IllegalStateException("Sistem sedang sibuk. Coba lagi."))));
    }

    private void finishFoodSubmission() {
        foodOrderSubmitting=false;
        restoreCheckoutSubmit();
        if (singleCheckoutNote != null) singleCheckoutNote.setEnabled(true);
        setLoading(false);
    }

    private void foodSubmissionError(Exception error) {
        finishFoodSubmission();
        if (currentScreen != 2) return;
        if (error instanceof ReliableOrderClient.FoodCartChangedException) {
            handleBack();
            loadActiveCart();
            updateCartButton();
            if (currentScreen == 1) renderMenus();
            showInfo("Keranjang", error.getMessage());
            return;
        }
        String message = error instanceof IllegalStateException ? error.getMessage() : ReliableOrderClient.failureMessage(error);
        showInfo("Pesanan", firstNonEmpty(message, "Pesanan belum dapat diproses."));
    }

    private void restoreCheckoutSubmit(){if(checkoutSubmitButton!=null)checkoutSubmitButton.setEnabled(activeRestaurant!=null&&activeRestaurant.optInt("is_open",1)==1);}

    private void removeConfirmedDraftItems(JSONObject receipt) {
        if (receipt == null) return;
        int rid = receipt.optInt("restaurant_id");
        Cart draft = merchantDrafts.get(rid);
        JSONArray purchased = receipt.optJSONArray("_direct_food_items");
        if (draft == null || purchased == null || !draft.draftId.equals(receipt.optString("_direct_food_draft"))) return;
        for (int i = 0; i < purchased.length(); i++) {
            JSONObject bought = purchased.optJSONObject(i);
            if (bought == null || bought.optInt("qty") <= 0) continue;
            for (int j = 0; j < draft.items.size(); j++) {
                CartItem pending = draft.items.get(j);
                if (!pending.lineId.equals(bought.optString("_draft_line_id"))) continue;
                pending.qty -= Math.min(99, bought.optInt("qty"));
                if (pending.qty <= 0) draft.items.remove(j);
                break;
            }
        }
        if (draft.items.isEmpty()) merchantDrafts.remove(rid);
    }

    private void completeCartCheckout(JSONObject receipt) {
        if (activeCart != null && activeCart.restaurantId == receipt.optInt("restaurant_id")
                && !receipt.optBoolean("recovered_attempt")) {
            checkoutNotes.remove(checkoutNoteKey(activeCart, checkoutFromCarts));
        }
        removeConfirmedDraftItems(receipt);
        int rid = receipt.optInt("restaurant_id");
        if (splitBillManager != null && activeCart != null && rid == activeCart.restaurantId
                && !splitBillManager.sessionKey().isEmpty()
                && splitBillManager.sessionKey().equals(receipt.optString("split_session_key"))) {
            currentScreen = 3;
            splitBillManager.clear();
        }
        successWasRecovered = receipt.optBoolean("recovered_attempt");
        showFoodOrderSuccess(java.util.Collections.singletonList(receipt.optString("order_id")), 0);
    }

    private void showFoodOrderSuccess(List<String> orderIds, long due) {
        leaveVirtualFood();
        foodScroll.setFillViewport(true);
        android.view.inputmethod.InputMethodManager keyboard =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(page.getWindowToken(), 0);
        foodHeader.setVisibility(View.GONE);
        currentScreen = 3;
        successOrderIds = new ArrayList<>(orderIds);
        long duration = Math.min(9000, 4400 + Math.max(0, orderIds.size() - 2) * 300L);
        successActivityAt = due > 0 ? due : android.os.SystemClock.elapsedRealtime() + duration;
        mainHandler.removeCallbacks(realtimeFoodRefresh);
        mainHandler.removeCallbacks(storyExpiryTick);
        clearStickyCartBar();
        setLoading(false);
        root.removeAllViews();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(16), dp(24), dp(20));
        FoodOrderSuccessView celebration = new FoodOrderSuccessView(this);
        root.addView(celebration, new LinearLayout.LayoutParams(-1, dp(155)));

        TextView title = text(successWasRecovered ? "Pesanan sudah tercatat"
                : orderIds.size() > 1 ? "Semua pesanan terkirim!" : "Pesanan terkirim!", 24, "#0B3A78", true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView message = text("Nomor pesanan", 12, "#64748B", false);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(8), 0, dp(6));
        root.addView(message);

        LinearLayout receipts = compactCartCard();
        for (String orderId : orderIds) {
            if (orderId.isEmpty()) continue;
            TextView receipt = text(orderId, 13, "#0B3A78", true);
            receipt.setGravity(Gravity.CENTER);
            receipt.setPadding(0, dp(4), 0, dp(4));
            receipt.setTextIsSelectable(true);
            receipts.addView(receipt);
        }
        addWithMargin(receipts, 0, 0, 0, dp(10));
        if (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()) {
            receipts.setAlpha(0f);
            receipts.setTranslationY(dp(10));
            receipts.animate().alpha(1f).translationY(0f).setStartDelay(300).setDuration(430).start();
        }
        TextView hint = text("Membuka Aktivitas sebentar lagi…", 12, "#64748B", false);
        hint.setGravity(Gravity.CENTER);
        addWithMargin(hint, 0, 0, 0, dp(8));
        Button activity = primaryButton("Lihat Aktivitas");
        activity.setOnClickListener(v -> openOrderActivity());
        root.addView(activity, new LinearLayout.LayoutParams(-1, dp(46)));
        foodScroll.scrollTo(0, 0);
        scheduleOrderActivity();
    }

    private void scheduleOrderActivity() {
        mainHandler.removeCallbacks(activityAfterFood);
        if (foodForeground) mainHandler.postDelayed(activityAfterFood,
                Math.max(0, successActivityAt - android.os.SystemClock.elapsedRealtime()));
    }

    private void openOrderActivity() {
        if (isFinishing() || currentScreen != 3) return;
        mainHandler.removeCallbacks(activityAfterFood);
        startActivity(new android.content.Intent(this, CustomerHistoryActivity.class));
        finish();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("food_note_user", userId);
        out.putBundle("food_checkout_notes", checkoutNotes);
        if (currentScreen == 3) {
            out.putStringArrayList("food_success_order_ids", successOrderIds);
            out.putLong("food_success_due", successActivityAt);
            out.putBoolean("food_success_recovered", successWasRecovered);
        }
    }

    protected void mergeFoodSocial(JSONObject social) {
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

    protected void toggleFavorite(String type, int targetId, JSONObject target, ImageButton button) {
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
                    java.util.Set<Integer> ids="merchant".equals(type)?favoriteMerchantIds:favoriteMenuIds;
                    if(target.optBoolean("is_favorite"))ids.add(targetId);else ids.remove(targetId);
                    // Favorit langsung menjadi sinyal personalisasi untuk AI rekomendasi dashboard.
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { button.setEnabled(true); showInfo("Favorit", "Koneksi gagal memperbarui favorit."); });
            }
        });
    }

    protected void showMenuReviews(JSONObject menu) {
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

    protected void showReviewDialog(JSONObject menu, JSONObject data) {
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

    protected void submitMenuReview(JSONObject menu, int rating, String comment) {
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

    protected void showMerchantReviews(JSONObject merchant) {
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
        return TransivaHttpRepository.getJson(this, urlText, TIMEOUT_MS);
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        return TransivaHttpRepository.postJson(this, urlText, payload, TIMEOUT_MS);
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

    private void updateCartButton() {
        if (savedCartButton == null) return;
        int count = 0;
        for (Cart draft : cartStore.all()) count += draft.quantity();
        savedCartBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        savedCartBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        savedCartButton.setContentDescription(count > 0 ? "Keranjang, " + count + " item" : "Keranjang");
    }

    private void openSavedCarts() {
        if (foodOrderSubmitting) return;
        if (!menuFromCarts) {
            cartOriginRestaurant = currentScreen == 1 ? activeRestaurant : null;
            cartOriginSearch = menuSearchQuery;
        }
        showSavedCarts();
    }

    private void showSavedCarts() {
        leaveVirtualFood();
        ++menuLoadGeneration;
        featureRuntime.cancel("food-menus");
        featureRuntime.cancel("food-social");
        menusRequestLoading = false;
        renderedMenuRid = 0;
        currentScreen = 4;
        menuFromCarts = false;
        selectRestaurant(null);
        clearStickyCartBar();
        setLoading(false);
        foodScroll.setFillViewport(false);
        root.setGravity(Gravity.TOP);
        root.removeAllViews();
        buildTopBar("Keranjang", "Pilih menu untuk checkout", true);
        foodScroll.scrollTo(0, 0);
        renderSavedCarts();
        loadMissingCartPhotos();
    }

    private String cartPhoto(Cart draft) {
        JSONObject restaurant = findRestaurantById(draft.restaurantId);
        return firstNonEmpty(cartPhotos.get(draft.restaurantId),
                restaurant == null ? "" : restaurant.optString("banner"),
                activeRestaurant != null && activeRestaurant.optInt("id") == draft.restaurantId
                        ? activeRestaurant.optString("banner") : "", draft.photo);
    }

    private void loadMissingCartPhotos() {
        List<Cart> missing = new ArrayList<>();
        for (Cart draft : cartStore.all()) if (cartPhoto(draft).isEmpty()) missing.add(draft);
        if (missing.isEmpty()) return;
        featureRuntime.executeLatest("food-cart-photos", CustomerLoadingCoordinator.OPTIONAL, () -> {
            for (Cart draft : missing) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    JSONObject data = TransivaHttpRepository.getJson(this, BASE_URL
                            + "server/get_food_menus.php?restaurant_id=" + draft.restaurantId + "&limit=1", 6000);
                    JSONObject restaurant = data.optJSONObject("restaurant");
                    if (restaurant == null) continue;
                    String photo = restaurant.optString("banner");
                    if (photo.isEmpty()) continue;
                    featureRuntime.post(mainHandler, () -> {
                        cartPhotos.put(draft.restaurantId, photo);
                        if (currentScreen == 4) renderSavedCarts();
                        if (currentScreen == COMBINED_CHECKOUT && combinedPlan != null && !foodOrderSubmitting) {
                            combinedCheckoutScroll = foodScroll.getScrollY();
                            renderCombinedCheckout();
                        }
                    });
                } catch (Exception ignored) { /* Placeholders remain available offline. */ }
            }
        }, null);
    }

    private ImageView cartThumbnail(String photo, boolean merchant) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round("#EAF4FF", dp(merchant ? 40 : 10)));
        image.setClipToOutline(true);
        loadImage(image, absoluteUrl(firstNonEmpty(photo, merchant
                ? "assets/default-restaurant.png" : "assets/no-image.png")));
        return image;
    }

    private LinearLayout compactCartCard() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundStroke("#FFFFFF", "#DCE8F6", dp(16), 1));
        box.setElevation(dp(1));
        box.setPadding(dp(10), dp(6), dp(10), dp(8));
        return box;
    }

    private CheckBox cartCheck(String description, int selected, int total) {
        CheckBox check = new CheckBox(this);
        check.setMinWidth(0);
        check.setPadding(0, 0, 0, 0);
        check.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xFF087CFF, 0xFF8299B3}));
        check.setChecked(total > 0 && selected == total);
        check.setContentDescription(description + (selected > 0 && selected < total ? ", sebagian dipilih" : ""));
        return check;
    }

    private void addCartMerchant(LinearLayout box, Cart draft, CheckBox check) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(3));
        if (check != null) row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(cartThumbnail(cartPhoto(draft), true), new LinearLayout.LayoutParams(dp(27), dp(27)));
        String label = firstNonEmpty(draft.name, activeRestaurant == null ? "" : activeRestaurant.optString("name"), "Toko");
        TextView name = text(label, 14, "#0F172A", true);
        name.setPadding(dp(7), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
    }

    private LinearLayout cartProduct(String photo, String name, String options, int qty,
                                     double price, double subtotal) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.addView(cartThumbnail(photo, false), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(7), 0, dp(5), 0);
        words.addView(text(name, 13, "#0F172A", true));
        if (options != null && !options.isEmpty()) words.addView(text(options, 11, "#64748B", false));
        words.addView(text(qty + " × " + rupiah(price), 11, "#64748B", false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(rupiah(subtotal), 12, "#0B3A78", true));
        return row;
    }

    private void addCheckoutProduct(LinearLayout section, Cart draft, CartItem item, JSONObject quote, boolean saved) {
        boolean priced = quote != null && quote.optInt("id") == item.id && quote.optInt("qty") == item.qty;
        double price = priced ? quote.optDouble("price", item.price) : item.price;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(cartThumbnail(item.image, false), new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(8), 0, dp(4), 0);
        details.addView(text(item.name, 13, "#0F172A", true));
        if (!item.optionText.isEmpty()) details.addView(text(item.optionText, 11, "#64748B", false));
        details.addView(text(rupiah(price) + " / porsi", 11, "#64748B", false));
        Button noteButton = FoodCheckoutNote.itemButton(this, item.name, item.note,
                value -> updateCheckoutProduct(draft, item, item.qty, value, saved));
        details.addView(noteButton, new LinearLayout.LayoutParams(-2, dp(44)));
        if (!item.note.isEmpty()) {
            TextView preview = text(item.note, 12, "#475569", false);
            preview.setMaxLines(3);
            preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(preview);
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout quantity = new LinearLayout(this);
        quantity.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = checkoutQuantityButton("−", "Kurangi " + item.name);
        Button plus = checkoutQuantityButton("+", "Tambah " + item.name);
        minus.setEnabled(!foodOrderSubmitting);
        plus.setEnabled(!foodOrderSubmitting && item.qty < 99);
        noteButton.setEnabled(!foodOrderSubmitting);
        minus.setOnClickListener(v -> updateCheckoutProduct(draft, item, item.qty - 1, item.note, saved));
        plus.setOnClickListener(v -> updateCheckoutProduct(draft, item, item.qty + 1, item.note, saved));
        quantity.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView count = text(String.valueOf(item.qty), 14, "#0F172A", true);
        count.setGravity(Gravity.CENTER);
        quantity.addView(count, new LinearLayout.LayoutParams(dp(26), -2));
        quantity.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(quantity);
        section.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private Button checkoutQuantityButton(String symbol, String description) {
        Button button = checkoutChoice(symbol, false);
        button.setTextSize(19);
        button.setMinimumHeight(0);
        button.setMinHeight(0);
        button.setContentDescription(description);
        return button;
    }

    private void updateCheckoutProduct(Cart draft, CartItem item, int qty, String note, boolean saved) {
        boolean combined = currentScreen == COMBINED_CHECKOUT;
        if ((!combined && currentScreen != 2) || foodOrderSubmitting || qty < 0 || qty > 99) return;
        if (!combined && (draft != activeCart || !cart.contains(item))) return;
        if (ReliableOrderClient.hasPendingFoodOrder(this)) {
            showInfo("Pesanan sedang diperiksa", "Ketuk Buat Pesanan untuk memeriksa hasil pesanan sebelumnya sebelum mengubah menu.");
            return;
        }
        boolean quantityChanged = qty != item.qty;
        if (!quantityChanged && item.note.equals(note)) return;
        String oldNoteKey = checkoutNoteKey(draft, saved);
        String orderNote = checkoutNotes.getString(oldNoteKey, "");
        if (quantityChanged && !combined && splitBillManager != null && splitBillManager.groupSize() > 1) {
            showInfo("Split Bill aktif", "Pilih Sendiri di bagian Split Bill sebelum mengubah jumlah menu. Setelah itu kamu bisa buat pembagian baru.");
            return;
        }
        if (saved && !cartStore.updateCheckoutLine(draft.restaurantId, item, qty, note)) {
            if (combined) quoteCombinedCheckout();
            else handleBack();
            showInfo("Keranjang berubah", "Periksa kembali menu yang dipilih.");
            return;
        }
        if (combined) {
            combinedCheckoutScroll = foodScroll.getScrollY();
            if (quantityChanged) {
                checkoutNotes.remove(oldNoteKey);
                Cart remaining = cartStore.get(draft.restaurantId).selectedOnly();
                if (!remaining.items.isEmpty() && !orderNote.isEmpty()) {
                    checkoutNotes.putString(checkoutNoteKey(remaining, true), orderNote);
                }
                if (cartStore.selectedCarts().isEmpty()) {
                    handleBack();
                    return;
                }
                ++combinedGeneration;
                featureRuntime.cancel("food-combined-quote");
                combinedPlan = null;
                combinedQuoting = true;
                mainHandler.removeCallbacks(requoteCombined);
                mainHandler.postDelayed(requoteCombined, 350);
            }
            renderCombinedCheckout();
            return;
        }
        if (saved) setActiveCart(cartStore.get(draft.restaurantId).selectedOnly());
        else if (qty == 0) cart.remove(item);
        else {
            item.qty = qty;
            item.note = note;
        }
        if (cart.isEmpty()) {
            checkoutNotes.remove(oldNoteKey);
            handleBack();
            return;
        }
        if (quantityChanged) {
            checkoutNotes.remove(oldNoteKey);
            if (!orderNote.isEmpty()) checkoutNotes.putString(checkoutNoteKey(activeCart, saved), orderNote);
            bindCheckoutSplitBill();
        }
        renderCheckout();
    }

    private LinearLayout compactSummary(String label, double amount, boolean emphasized) {
        LinearLayout line = new LinearLayout(this);
        line.setPadding(0, dp(2), 0, dp(2));
        line.addView(text(label, 12, "#64748B", emphasized), new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(text(rupiah(amount), 12, "#0B3A78", emphasized));
        return line;
    }

    private void saveCartSelection(List<Cart> drafts) {
        if (!cartStore.saveAll(drafts)) showInfo("Keranjang", "Isi keranjang berubah. Silakan coba lagi.");
        renderSavedCarts();
    }

    private void renderSavedCarts() {
        int scrollY = foodScroll.getScrollY();
        root.removeAllViews();
        clearStickyCartBar();
        List<Cart> drafts = cartStore.all();
        if (drafts.isEmpty()) {
            addStatus("Keranjang masih kosong. Pilih makanan atau minuman dari toko favoritmu.");
            return;
        }
        int selectedLines = 0, allLines = 0, selectedQuantity = 0;
        double total = 0;
        for (Cart draft : drafts) {
            Cart chosen = draft.selectedOnly();
            allLines += draft.items.size();
            selectedLines += chosen.items.size();
            selectedQuantity += chosen.quantity();
            total += chosen.subtotal();
        }
        LinearLayout selectAll = compactCartCard();
        CheckBox all = cartCheck("Pilih semua menu", selectedLines, allLines);
        all.setText(all.isChecked() ? "Semua dipilih" : "Pilih semua");
        all.setTextSize(13);
        all.setOnCheckedChangeListener((button, checked) -> {
            for (Cart draft : drafts) for (CartItem line : draft.items) line.selected = checked;
            saveCartSelection(drafts);
        });
        selectAll.addView(all, new LinearLayout.LayoutParams(-1, dp(44)));
        addWithMargin(selectAll, 0, 0, 0, dp(6));

        for (Cart draft : drafts) {
            Cart chosen = draft.selectedOnly();
            LinearLayout store = compactCartCard();
            CheckBox storeCheck = cartCheck("Pilih semua menu " + draft.name, chosen.items.size(), draft.items.size());
            storeCheck.setOnCheckedChangeListener((button, checked) -> {
                for (CartItem line : draft.items) line.selected = checked;
                saveCartSelection(java.util.Collections.singletonList(draft));
            });
            addCartMerchant(store, draft, storeCheck);
            if (!chosen.items.isEmpty() && chosen.items.size() < draft.items.size()) {
                LinearLayout merchantRow = (LinearLayout) store.getChildAt(0);
                merchantRow.addView(text(chosen.items.size() + "/" + draft.items.size() + " dipilih", 11, "#64748B", false));
            }
            for (CartItem line : draft.items) {
                LinearLayout row = cartProduct(line.image, line.name, line.optionText, line.qty,
                        line.price, line.price * line.qty);
                String itemLabel = line.name + (line.optionText.isEmpty() ? "" : " " + line.optionText);
                CheckBox itemCheck = cartCheck("Pilih " + itemLabel, line.selected ? 1 : 0, 1);
                itemCheck.setOnCheckedChangeListener((button, checked) -> {
                    line.selected = checked;
                    saveCartSelection(java.util.Collections.singletonList(draft));
                });
                row.addView(itemCheck, 0, new LinearLayout.LayoutParams(dp(44), dp(44)));
                Runnable remove = () -> {
                    draft.items.remove(line);
                    saveCartSelection(java.util.Collections.singletonList(draft));
                };
                TextView delete = text("×", 20, "#C4294E", true);
                delete.setGravity(Gravity.CENTER);
                delete.setContentDescription("Hapus semua porsi " + itemLabel);
                delete.setOnClickListener(v -> remove.run());
                row.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(44)));
                FoodCartSwipeRow swipe = new FoodCartSwipeRow(this, remove);
                row.setBackgroundColor(Color.WHITE);
                swipe.addView(row, new FrameLayout.LayoutParams(-1, -2));
                store.addView(swipe, new LinearLayout.LayoutParams(-1, -2));
            }
            store.addView(compactSummary("Subtotal dipilih", chosen.subtotal(), true));
            Button edit = checkoutChoice("Tambah menu", false);
            edit.setOnClickListener(v -> {
                selectRestaurant(restaurantForCart(draft.restaurantId, draft.name));
                menuSearchQuery = "";
                menuFromCarts = true;
                showMenuPage();
                loadMenus(draft.restaurantId, true);
            });
            Button order = checkoutChoice("Checkout toko", false);
            order.setEnabled(!chosen.items.isEmpty());
            order.setOnClickListener(v -> {
                Cart selected = cartStore.get(draft.restaurantId).selectedOnly();
                if (selected.items.isEmpty()) { renderSavedCarts(); return; }
                selectRestaurant(restaurantForCart(draft.restaurantId, draft.name));
                setActiveCart(selected);
                checkoutFromCarts = true;
                refreshCheckoutRestaurant = true;
                showCheckout();
            });
            LinearLayout actions = new LinearLayout(this);
            actions.setPadding(0, dp(3), 0, 0);
            addCheckoutPair(actions, edit, order, 44);
            store.addView(actions);
            addWithMargin(store, 0, 0, 0, dp(7));
        }
        showCombinedBar("Subtotal · belum termasuk ongkir", total,
                "Checkout (" + selectedQuantity + ")", selectedLines == 0 ? null : this::openCombinedCheckout);
        foodScroll.post(() -> { if (currentScreen == 4) foodScroll.scrollTo(0, scrollY); });
    }

    private void showCombinedBar(String label, double amount, String action, Runnable run) {
        LinearLayout bar = compactCartCard();
        boolean narrow = getResources().getConfiguration().screenWidthDp < 360
                || getResources().getConfiguration().fontScale > 1.2f;
        bar.setOrientation(narrow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setElevation(dp(12));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(label, 11, "#64748B", false));
        words.addView(text(rupiah(amount), 19, "#0B3A78", true));
        bar.addView(words, narrow ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        Button button = primaryButton(action);
        button.setTextSize(13);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setEnabled(run != null);
        button.setAlpha(run == null ? .55f : 1f);
        if (run != null) button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(narrow ? -1 : -2, dp(48));
        if (narrow) actionParams.topMargin = dp(6);
        else actionParams.leftMargin = dp(8);
        bar.addView(button, actionParams);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lp.setMargins(dp(12), 0, dp(12), dp(10));
        page.addView(bar, lp);
        stickyCartBar = bar;
        setFoodPadding(dp(16), dp(14), dp(16), dp(130));
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (stickyCartBar == bar) setFoodPadding(dp(16), dp(14), dp(16), bar.getHeight() + dp(24));
        });
    }

    private void openCombinedCheckout() {
        List<Cart> drafts = cartStore.selectedCarts();
        if (userId <= 0 || drafts.isEmpty()) return;
        currentScreen = COMBINED_CHECKOUT;
        combinedSessionId = FoodCombinedCheckout.sessionId(this, userId, drafts);
        combinedPayment = FoodCombinedCheckout.payment(this, userId);
        buildTopBar("Checkout", "Ringkasan pilihanmu", true);
        combinedCheckoutScroll = 0;
        foodScroll.scrollTo(0, 0);
        quoteCombinedCheckout();
    }

    private void quoteCombinedCheckout() {
        mainHandler.removeCallbacks(requoteCombined);
        final List<Cart> drafts = cartStore.selectedCarts();
        if (drafts.isEmpty()) { showSavedCarts(); return; }
        int generation = ++combinedGeneration;
        String payment = combinedPayment;
        combinedSessionId = FoodCombinedCheckout.sessionId(this, userId, drafts);
        String sessionId = combinedSessionId;
        combinedPlan = null;
        combinedQuoteError = "";
        combinedQuoting = true;
        renderCombinedCheckout();
        Runnable rejected = () -> featureRuntime.post(mainHandler,
                () -> finishCombinedQuote(generation, null, "Sistem sedang sibuk. Coba lagi."));
        featureRuntime.executeLatest("food-combined-quote", CustomerLoadingCoordinator.CONTENT, () -> {
            try {
                JSONObject previous = FoodCombinedCheckout.recover(this);
                if (previous != null) {
                    featureRuntime.post(mainHandler, () -> {
                        if (currentScreen == COMBINED_CHECKOUT && combinedGeneration == generation)
                            showCombinedResult(FoodCombinedCheckout.recovered(previous), 0);
                    });
                    return;
                }
                FoodCombinedCheckout.Plan plan = FoodCombinedCheckout.quote(this, userId, drafts, payment, sessionId);
                featureRuntime.post(mainHandler, () -> finishCombinedQuote(generation, plan, ""));
            } catch (Exception error) {
                String message = error instanceof IllegalStateException ? error.getMessage()
                        : ReliableOrderClient.failureMessage(error);
                featureRuntime.post(mainHandler, () -> finishCombinedQuote(generation, null, message));
            }
        }, rejected);
    }

    private void finishCombinedQuote(int generation, FoodCombinedCheckout.Plan plan, String error) {
        if (currentScreen != COMBINED_CHECKOUT || combinedGeneration != generation) return;
        combinedCheckoutScroll = foodScroll.getScrollY();
        combinedQuoting = false;
        combinedPlan = plan;
        combinedQuoteError = error;
        renderCombinedCheckout();
    }

    private void renderCombinedCheckout() {
        clearStickyCartBar();
        root.removeAllViews();
        LinearLayout address = checkoutCard();
        address.addView(text("Alamat pengantaran", 15, "#0F172A", true));
        address.addView(text(combinedPlan == null ? DeliveryAddressGate.address(this) : combinedPlan.address,
                13, "#64748B", false));
        Button edit = checkoutChoice("Ubah alamat", false);
        edit.setEnabled(!foodOrderSubmitting);
        edit.setOnClickListener(v -> {
            combinedCheckoutScroll = foodScroll.getScrollY();
            editingCombinedAddress = true;
            DeliveryAddressGate.edit(this, "TransFood");
        });
        address.addView(edit, new LinearLayout.LayoutParams(-1, dp(44)));
        addWithMargin(address, 0, 0, 0, dp(10));

        LinearLayout payment = checkoutCard();
        payment.addView(text("Metode pembayaran", 15, "#0F172A", true));
        LinearLayout options = new LinearLayout(this);
        Button cash = checkoutChoice("Tunai", "cash".equals(combinedPayment));
        Button wallet = checkoutChoice("Transiva Pay", "balance".equals(combinedPayment));
        cash.setEnabled(!foodOrderSubmitting && !combinedQuoting);
        wallet.setEnabled(!foodOrderSubmitting && !combinedQuoting);
        cash.setOnClickListener(v -> chooseCombinedPayment("cash"));
        wallet.setOnClickListener(v -> chooseCombinedPayment("balance"));
        addCheckoutPair(options, cash, wallet, 46);
        payment.addView(options);
        payment.addView(text("Pengantaran standar dari setiap toko", 12, "#64748B", false));
        addWithMargin(payment, 0, 0, 0, dp(10));

        List<Cart> drafts = cartStore.selectedCarts();
        for (Cart draft : drafts) {
            LinearLayout section = compactCartCard();
            addCartMerchant(section, draft, null);
            FoodCombinedCheckout.StoreQuote store = null;
            if (combinedPlan != null) {
                for (FoodCombinedCheckout.StoreQuote quotedStore : combinedPlan.stores) {
                    if (quotedStore.cart.restaurantId == draft.restaurantId) {
                        store = quotedStore;
                        break;
                    }
                }
            }
            JSONArray quoted = store == null ? null : store.quote.optJSONArray("items");
            for (int i = 0; i < draft.items.size(); i++) {
                addCheckoutProduct(section, draft, draft.items.get(i),
                        quoted == null ? null : quoted.optJSONObject(i), true);
            }
            if (store != null) {
                section.addView(compactSummary("Subtotal menu", store.quote.optLong("food_total"), false));
                section.addView(compactSummary("Ongkir toko", store.quote.optLong("delivery_fee"), false));
                section.addView(compactSummary("Total toko", store.quote.optLong("total"), true));
            }
            addCheckoutNote(section, draft, true);
            addWithMargin(section, 0, 0, 0, dp(10));
        }
        int targetScroll = combinedCheckoutScroll;
        foodScroll.post(() -> {
            if (currentScreen == COMBINED_CHECKOUT) foodScroll.scrollTo(0, targetScroll);
        });

        if (combinedPlan == null) {
            addStatus(combinedQuoting ? "Memperbarui total checkout…" : firstNonEmpty(combinedQuoteError, "Rincian checkout belum tersedia."));
            if (!combinedQuoting) {
                Button retry = primaryButton("Hitung ulang");
                retry.setOnClickListener(v -> {
                    combinedCheckoutScroll = foodScroll.getScrollY();
                    quoteCombinedCheckout();
                });
                addWithMargin(retry, 0, 0, 0, dp(10));
            }
            return;
        }

        LinearLayout totals = checkoutCard();
        totals.addView(text("Rincian pembayaran", 15, "#0F172A", true));
        totals.addView(summaryLine("Subtotal semua menu", combinedPlan.food));
        totals.addView(summaryLine("Total ongkir", combinedPlan.delivery));
        totals.addView(summaryLine("Total akhir", combinedPlan.total));
        totals.addView(text("Setiap toko memiliki nomor pesanan dan pengantaran sendiri. Jika satu pesanan terkendala, pesanan yang sudah tercatat tetap berjalan.", 12, "#64748B", false));
        addWithMargin(totals, 0, 0, 0, dp(10));
        if (!foodOrderSubmitting) showCombinedBar("Total akhir", combinedPlan.total, "Buat pesanan", this::submitCombinedCheckout);
        else addStatus("Membuat pesanan tiap toko…");
    }

    private void chooseCombinedPayment(String payment) {
        if (payment.equals(combinedPayment) || foodOrderSubmitting) return;
        combinedPayment = payment;
        combinedCheckoutScroll = foodScroll.getScrollY();
        FoodCombinedCheckout.savePayment(this, userId, payment);
        quoteCombinedCheckout();
    }

    private void submitCombinedCheckout() {
        if (currentScreen != COMBINED_CHECKOUT || combinedPlan == null || foodOrderSubmitting) return;
        FoodCombinedCheckout.Plan approved = combinedPlan;
        java.util.Map<Integer, String> notes = new java.util.HashMap<>();
        java.util.Map<String, String> itemNotes = new java.util.HashMap<>();
        for (Cart draft : cartStore.selectedCarts()) {
            notes.put(draft.restaurantId, checkoutNotes.getString(checkoutNoteKey(draft, true), ""));
            for (CartItem item : draft.items) itemNotes.put(item.lineId, item.note);
        }
        combinedCheckoutScroll = foodScroll.getScrollY();
        foodOrderSubmitting = true;
        renderCombinedCheckout();
        setLoading(true);
        featureRuntime.execute(() -> {
            FoodCombinedCheckout.Result result = FoodCombinedCheckout.submit(this, approved, notes, itemNotes);
            featureRuntime.post(mainHandler, () -> {
                if (!result.recoveredPrevious) {
                    for (int i = 0; i < result.orderIds.size(); i++) {
                        checkoutNotes.remove(checkoutNoteKey(approved.stores.get(i).cart, true));
                    }
                }
                showCombinedResult(result, approved.stores.size());
            });
        }, () -> featureRuntime.post(mainHandler, () -> {
            foodOrderSubmitting = false;
            setLoading(false);
            renderCombinedCheckout();
            showInfo("Checkout", "Sistem sedang sibuk. Coba lagi.");
        }));
    }

    private void showCombinedResult(FoodCombinedCheckout.Result result, int expected) {
        foodOrderSubmitting = false;
        combinedQuoting = false;
        combinedPlan = null;
        removeConfirmedDraftItems(result.recoveredReceipt);
        if (!result.recoveredPrevious && expected > 0 && result.orderIds.size() == expected) {
            successWasRecovered = false;
            List<String> receipts = FoodCombinedCheckout.confirmed(this, userId, combinedSessionId, true);
            showFoodOrderSuccess(receipts.isEmpty() ? result.orderIds : receipts, 0);
            return;
        }
        currentScreen = COMBINED_RESULT;
        clearStickyCartBar();
        setLoading(false);
        root.removeAllViews();
        buildTopBar("Hasil checkout", "TransFood", true);
        foodScroll.scrollTo(0, 0);

        LinearLayout summary = checkoutCard();
        String status = result.recoveredPrevious ? "Pesanan sebelumnya ditemukan"
                : result.orderIds.isEmpty() ? "Belum ada pesanan terkonfirmasi" : "Sebagian pesanan terkirim";
        summary.addView(text(status, 19, "#0F172A", true));
        if (!result.problem.isEmpty()) summary.addView(text(result.problem, 13, "#64748B", false));
        if (!result.orderIds.isEmpty()) {
            for (String orderId : result.orderIds) {
                Button track = checkoutChoice(orderId + "  ›", false);
                track.setOnClickListener(v -> startActivity(new android.content.Intent(this, CustomerFoodOrderStatusActivity.class)
                        .putExtra("order_id", orderId)));
                summary.addView(track, new LinearLayout.LayoutParams(-1, dp(46)));
            }
            if (!result.recoveredPrevious) summary.addView(summaryLine("Total pesanan terkonfirmasi", result.total));
        }
        summary.addView(text("Periksa Aktivitas bila hasil pesanan belum pasti. Item yang belum terkonfirmasi masih dapat diperiksa lewat Keranjang.", 12, "#64748B", false));
        addWithMargin(summary, 0, 0, 0, dp(10));

        if (!cartStore.selectedCarts().isEmpty()) {
            Button resume = primaryButton("Lanjut checkout yang tersisa");
            resume.setOnClickListener(v -> {
                currentScreen = COMBINED_CHECKOUT;
                combinedCheckoutScroll = 0;
                buildTopBar("Checkout", "Ringkasan pilihan yang tersisa", true);
                foodScroll.scrollTo(0, 0);
                quoteCombinedCheckout();
            });
            addWithMargin(resume, 0, 0, 0, dp(8));
        }
        Button cartButton = checkoutChoice("Kembali ke keranjang", false);
        cartButton.setOnClickListener(v -> showSavedCarts());
        addWithMargin(cartButton, 0, 0, 0, dp(10));
        Button orders = checkoutChoice("Lihat pesanan aktif", false);
        orders.setOnClickListener(v -> startActivity(new android.content.Intent(this, LiveOrderCenterActivity.class)));
        addWithMargin(orders, 0, 0, 0, dp(10));
    }

    private void buildTopBar(String title, String sub, boolean back) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
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
        savedCartButton = null;
        savedCartBadge = null;
        if (currentScreen == 0 || currentScreen == 1) {
            FrameLayout cartIcon = new FrameLayout(this);
            savedCartButton = cartIconButton("Keranjang");
            savedCartButton.setOnClickListener(v -> openSavedCarts());
            cartIcon.addView(savedCartButton, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.BOTTOM | Gravity.LEFT));
            savedCartBadge = text("", 10, "#FFFFFF", true);
            savedCartBadge.setGravity(Gravity.CENTER);
            savedCartBadge.setPadding(dp(3), 0, dp(3), 0);
            savedCartBadge.setMinWidth(dp(16));
            savedCartBadge.setBackground(round("#146BD4", dp(12)));
            cartIcon.addView(savedCartBadge, new FrameLayout.LayoutParams(-2, dp(17), Gravity.TOP | Gravity.RIGHT));
            row.addView(cartIcon, new LinearLayout.LayoutParams(dp(51), dp(49)));
            updateCartButton();
        }
        foodHeader.setVisibility(View.VISIBLE);
        foodHeader.removeAllViews();
        foodHeader.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void handleBack() {
        if(foodOrderSubmitting){android.widget.Toast.makeText(this,"Pesanan sedang diproses. Tunggu sebentar, ya.",android.widget.Toast.LENGTH_SHORT).show();return;}
        if(currentScreen==3){openOrderActivity();return;}
        if (currentScreen == COMBINED_CHECKOUT || currentScreen == COMBINED_RESULT) {
            ++combinedGeneration;
            mainHandler.removeCallbacks(requoteCombined);
            featureRuntime.cancel("food-combined-quote");
            showSavedCarts();
            return;
        }
        if (currentScreen == 2) {
            checkoutQuoteGeneration++;setLoading(false);
            refreshCheckoutRestaurant = false;
            if (checkoutFromCarts) {
                checkoutFromCarts = false;
                showSavedCarts();
            } else {
                loadActiveCart();
                showMenuPage();
                if (!menusLoadFinished && activeRestaurant != null) loadMenus(activeRestaurant.optInt("id"), true);
            }
            return;
        }

        if (currentScreen == 4) {
            JSONObject origin = cartOriginRestaurant;
            cartOriginRestaurant = null;
            if (origin != null) {
                selectRestaurant(origin);
                menuSearchQuery = cartOriginSearch;
                showMenuPage();
                loadMenus(origin.optInt("id"), true);
                return;
            }
            showRestaurantList();
            if (!restaurantsLoadFinished) loadRestaurants(true);
            return;
        }

        if (currentScreen==1 && expandedToppingMenu!=0) {
            expandedToppingMenu=0;
            syncToppingCards();
            return;
        }
        if (currentScreen == 1 && menuFromCarts) {
            showSavedCarts();
            return;
        }
        if (currentScreen == 1 || activeRestaurant != null) {
            menus.clear();
            expandedToppingMenu=0;
            menuSearchQuery = "";
            showRestaurantList();
            if (!restaurantsLoadFinished) loadRestaurants(true);
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
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); if("#0F172A".equals(color)||"#0B3A78".equals(color)||"#123B6B".equals(color))color=CustomerAppSettings.isDarkMode(this)?"#EEF5FF":"#133152";
        else if("#64748B".equals(color)||"#94A3B8".equals(color))color=CustomerAppSettings.isDarkMode(this)?"#A9B8CC":"#60758E";
        t.setTextColor(Color.parseColor(color));
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

    private GradientDrawable foodBlueCard() {
        boolean dark = CustomerAppSettings.isDarkMode(this);
        GradientDrawable shade = roundGradient(dark ? "#182B42" : "#FFFFFF",
                dark ? "#203C57" : "#DCEEFF", dp(19));
        shade.setStroke(dp(1), Color.parseColor(dark ? "#385775" : "#D8E9F8"));
        return shade;
    }

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
    private static class OptionSelection { String type; String label; JSONArray items; RadioGroup radioGroup; LinearLayout checkBoxContainer; }



    @Override
    protected void onResume() {
        super.onResume();
        if (userId > 0 && !String.valueOf(userId).equals(CustomerSessionGuard.capture(this).account)) {
            finish();
            return;
        }
        foodForeground = true;
        featureRuntime.onResume();
        if (currentScreen == 3) { scheduleOrderActivity(); return; }
        mainHandler.removeCallbacks(storyExpiryTick);
        mainHandler.post(storyExpiryTick);
        if(restaurantsLoadFinished||activeRestaurant!=null)refreshMerchantStories();
        if (!DeliveryAddressGate.valid(this)) { return; }
        if(root==null){recreate();return;}
        if(editingCheckoutAddress){editingCheckoutAddress=false;if(currentScreen==2){clearStickyCartBar();root.removeAllViews();addStatus("Memperbarui alamat dan ongkir…");calculateOngkirThenRender();}}
        if (editingCombinedAddress) {
            editingCombinedAddress = false;
            if (currentScreen == COMBINED_CHECKOUT && !foodOrderSubmitting) quoteCombinedCheckout();
        }
        if (foodOrderSubmitting && currentScreen == COMBINED_CHECKOUT) setLoading(true);
        catalogRefreshEpoch=System.currentTimeMillis();
        if(currentScreen==0&&root!=null){showRestaurantList();loadRestaurants(false);loadMenuSearch(homeSearchQuery);}
        else if(currentScreen==1&&activeRestaurant!=null){
            loadActiveCart();
            updateCartButton();
            if (menusLoadFinished) renderMenus();
            loadMenus(activeRestaurant.optInt("id"),menus.isEmpty());
        } else if(currentScreen==4) renderSavedCarts();
        mainHandler.removeCallbacks(realtimeFoodRefresh);
        mainHandler.postDelayed(realtimeFoodRefresh, CustomerPerformanceManager.pollingBackground(this, 30000L));
    }

    @Override
    protected void onPause() {
        foodForeground = false;
        if(homeSearchRunnable!=null)mainHandler.removeCallbacks(homeSearchRunnable);
        for(String key:new String[]{"food-merchants","food-search","food-menus","food-social","food-profile-story","food-story-feed"})featureRuntime.cancel(key);
        ++restaurantGeneration;++menuSearchGeneration;++menuLoadGeneration;++profileStoryGeneration;
        restaurantsLoading=false;menuIndexLoading=false;menusRequestLoading=false;kabarFetching=false;pendingProfileRid=0;kabarNextFetch=0;
        setLoading(false);
        mainHandler.removeCallbacks(activityAfterFood);
        mainHandler.removeCallbacks(realtimeFoodRefresh);
        mainHandler.removeCallbacks(storyExpiryTick);
        if (foodScroll instanceof MitraPullScrollView) ((MitraPullScrollView)foodScroll).finishRefresh();
        featureRuntime.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (splitBillManager != null) splitBillManager.dispose();
        if(virtualFood!=null)virtualFood.dispose();
        featureRuntime.destroy();
        try { mainHandler.removeCallbacksAndMessages(null); } catch (Exception ignored) {}
        super.onDestroy();
    }

}
