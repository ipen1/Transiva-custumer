package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ScrollView;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.ClipboardManager;
import android.content.ClipData;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.ApiException;
import android.util.Log;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
class PassengerTransportActivityLegacyCore extends CustomerAdaptiveActivity {



    private JSONObject stopPricing;
    private TextView stopPricingText;
    private int maxStopCount() { return stopPricing==null?2:Math.max(0,Math.min(2,stopPricing.optInt("max_stops",2))); }
    private void applyStopPricing(JSONObject rule) {
        if(rule==null)return;
        stopPricing=rule;
        if(stopPricingText!=null)stopPricingText.setText("Singgah Rp"+formatMoney(rule.optInt("stop_fee"))+"/titik • gratis "+(rule.optInt("free_seconds")/60)+" menit "+(rule.optInt("free_seconds")%60)+" detik/titik, lalu Rp"+formatMoney(rule.optInt("rate_per_minute"))+"/menit dimulai. Maks. "+maxStopCount()+" singgah. Biaya singgah & tunggu 100% untuk driver.");
        updateWaypointButton();
    }
    private void loadStopPricing() {
        featureRuntime.execute(() -> {try {
            JSONObject payload=new JSONObject().put("service_type",isCarService()?"Transcar":"Transbike");
            JSONObject result=postJson(BASE_URL+"server/ride_stop_config.php",payload);
            featureRuntime.post(mainHandler,() -> {if(!destroyed&&result.optBoolean("success"))applyStopPricing(result.optJSONObject("stop_pricing"));});
        }catch(Exception ignored){}},() -> {});
    }
    protected boolean isCarService() { return false; }
    protected String serviceName() { return isCarService() ? "Transcar" : "TransRide"; }
    protected String offerService() { return isCarService() ? "TransCar" : "TransRide"; }
    protected String driverType() { return isCarService() ? "car" : "bike"; }
    protected String serviceTitle() { return isCarService() ? "Transcar" : "TransRide"; }
    protected String orderButtonText() { return isCarService() ? "🚘 Order Mobil" : "🏍️ Order Motor"; }
    protected String orderNoun() { return isCarService() ? "mobil" : "motor"; }

    protected final CustomerFeatureRuntimeController featureRuntime =
            new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.SEARCH);
    protected CustomerGeocodingRepository geocodingRepository;

    protected static final String BASE_URL = "https://transiva.my.id/";
    protected static final String CREATE_ORDER_URL = BASE_URL + "server/createOrder.php";
    protected static final String PAYMENT_QUOTE_URL = BASE_URL + "server/ride_payment_quote.php";
    protected static final String HEMAT_STATUS_URL = BASE_URL + "server/customer_point_status.php";
    protected static final String RESOLVE_MAPS_URL = BASE_URL + "server/resolve_google_maps.php";
    protected static final String GET_BUSINESSES_URL = BASE_URL + "server/getBusinesses.php";
    protected static final String GET_LAUNDRIES_URL = BASE_URL + "server/admin_get_laundries.php";
    protected static final String GET_ONLINE_DRIVERS_URL = BASE_URL + "server/get_map_drivers.php";
    protected static final int REQ_LOCATION = 44;
    protected static final int REQ_PLACE_AUTOCOMPLETE = 45;
    protected static final int TIMEOUT_MS = 25000;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());

    protected TransivaGoogleMapView mapView;
    protected TextView pickupText, deliveryText, modeText, fareText, paymentSummaryText, driverAvailabilityText;
    protected TextView distanceInfoText, durationInfoText, originalPriceText, finalPriceText, discountInfoText, wizardStepText, tripRouteSummaryText;
    protected Button voucherChoiceBtn, noteChoiceBtn, paymentChoiceBtn;
    protected LinearLayout bookingDetailsCard;
    protected EditText googleMapInput, noteInput, voucherInput;
    private String driverNoteDraft = "";
    private boolean orderAfterNote;
    protected Button pickupBtn, deliveryBtn, gpsBtn, orderBtn, backBtn, useLinkBtn;
    protected ProgressBar progressBar;

    protected boolean mapReady = false;
    // 2.2: preview lokasi peta terpisah dari tujuan yang sudah dikonfirmasi.
    protected volatile boolean deliverySelectionLocked = false;
    protected volatile String pendingDeliveryTitle = "";
    protected volatile String pendingDeliverySubtitle = "";
    // 2.4: guards reverse-geocode/Places callbacks so an older map position can never overwrite the latest candidate.
    protected volatile long destinationResolveGeneration = 0L;

    // 2.5 API Saver: keep Google Places traffic intentionally low.
    protected static final int PLACES_MIN_QUERY_CHARS = 3;
    protected static final long PLACES_DEBOUNCE_MS = 700L;
    protected static final long PLACES_ERROR_COOLDOWN_MS = 30L * 60L * 1000L;
    protected volatile long placesCooldownUntilMs = 0L;
    protected final java.util.LinkedHashMap<String, String> nearbyLandmarkCache = new java.util.LinkedHashMap<String, String>() {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, String> e) { return size() > 48; }
    };
    protected boolean ordering = false;
    protected String mode = "pickup";

    /*
     * SATU-SATUNYA PENGATURAN UKURAN MARKER JEMPUT/TUJUAN.
     *
     * Marker center Android dan marker tersimpan Leaflet memakai ukuran yang sama.
     * Cukup ubah angka MARKER_WIDTH_DP untuk memperbesar atau memperkecil semuanya.
     *
     * Rekomendasi:
     * 36 = kecil
     * 40 = sedang
     * 44 = besar
     */
    protected static final int MARKER_WIDTH_DP = 40;

    // Rasio mengikuti file ikon asli (46 x 58).
    protected static final int MARKER_HEIGHT_DP =
            Math.round(MARKER_WIDTH_DP * 58f / 46f);

    protected static final int MARKER_BOX_WIDTH_DP = MARKER_WIDTH_DP;
    protected static final int MARKER_BOX_HEIGHT_DP = MARKER_HEIGHT_DP;
    protected static final int MARKER_IMAGE_WIDTH_DP = MARKER_WIDTH_DP;
    protected static final int MARKER_IMAGE_HEIGHT_DP = MARKER_HEIGHT_DP;

    protected static final int MARKER_ANCHOR_X_PX =
            MARKER_BOX_WIDTH_DP / 2;

    protected static final int MARKER_ANCHOR_Y_PX =
            Math.max(1, MARKER_IMAGE_HEIGHT_DP - 2);
    protected String username = "";
    protected String authToken = "";
    protected String paymentMethod = "cash";
    protected String scheduledAt = "";
    protected String priceMode = "standard";
    protected int familyMemberId = 0;
    protected final RideEcosystemFeatures ecosystemFeatures = new RideEcosystemFeatures();
    protected Button waypointQuickButton, waypointBtn, groupRideBtn, safetyRideBtn, familyBtn;
    protected LinearLayout waypointHeaderRows;
    protected LinearLayout ecosystemRow;
    protected SplitBillManager splitBillManager;
    protected int lastQuotedFare = 0;
    protected int royaltyOriginalFare = 0;
    protected boolean selectingWaypoint = false;
    protected String familyMemberName = "";
    protected boolean smartFavoriteIntent = false;
    protected int userId = 0;

    protected double pickupLat = 0, pickupLng = 0;
    protected double deliveryLat = 0, deliveryLng = 0;
    protected double centerLat = -0.018137, centerLng = 120.087380;
    protected double pickLat = 0, pickLng = 0;

    protected String pickupAddress = "Lokasi Jemput";
    protected String deliveryAddress = "Lokasi Pengantaran";
    protected boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        geocodingRepository = new CustomerGeocodingRepository(this);
        initializePlacesAutocomplete();
        splitBillManager = new SplitBillManager(this, (key,size,ready) -> { ecosystemFeatures.groupSize=size; ecosystemFeatures.splitFareMode=size>1?"custom":"none"; if(groupRideBtn!=null) groupRideBtn.setText(size>1 ? (ready?"✅ Split "+size:"⏳ Split "+size) : "💳 Split Pay"); });
        try {
            CustomerWindowPolicy.status(getWindow(), Color.parseColor("#071426"));
            CustomerWindowPolicy.navigation(getWindow(), Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readUser();
        familyMemberId = getIntent() == null ? 0 : getIntent().getIntExtra("family_member_id", 0);
        familyMemberName = getIntent() == null ? "" : firstNonEmpty(getIntent().getStringExtra("family_member_name"), "");
        if (getIntent() != null && getIntent().getBooleanExtra("split_pay_entry", false)) paymentMethod = "balance";
        if(savedInstanceState!=null) driverNoteDraft=savedInstanceState.getString("driver_note_draft","");
        buildLayout();
        loadStopPricing();
        String royaltyVoucher=getIntent()==null?"":getIntent().getStringExtra("royalty_voucher_code");
        if(royaltyVoucher!=null&&!royaltyVoucher.isEmpty()&&voucherInput!=null){voucherInput.setText(royaltyVoucher);if(voucherChoiceBtn!=null)voucherChoiceBtn.setText("🏷 Voucher dipilih");}

        applySmartFavoriteIntent();
        CustomerBestOffer.load(this, offerService(), offer -> featureRuntime.post(mainHandler, () -> {
            if (offer != null && voucherInput != null && voucherInput.getText().toString().trim().isEmpty()) {
                String code = offer.optString("promo_code", "").trim();
                if (!code.isEmpty()) {
                    voucherInput.setText(code);
                    if (voucherChoiceBtn != null) voucherChoiceBtn.setText("🏷 " + code);
                }
            }
        }));
        applySharedLocationIntent();
        requestLocationIfNeeded();
        if (savedInstanceState == null && getIntent() != null
                && getIntent().getBooleanExtra("focus_destination_search", false)) {
            mode = "delivery";
            featureRuntime.post(mainHandler, () -> { if (!destroyed) openDestinationAutocomplete(); });
        }
    }

    protected void readUser() {
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);

        try {
            SessionManager session = new SessionManager(this);
            // Token dibaca terpisah dari status session agar session lama yang masih valid
            // tetap dapat dimigrasikan oleh SessionManager.getToken().
            authToken = session.getToken();
            if (session.isLoggedIn()) {
                username = firstNonEmpty(
                        session.getUsername(),
                        session.getName(),
                        sp.getString("username", ""),
                        sp.getString("user_username", ""),
                        sp.getString("player_username", "")
                );

                try {
                    userId = Integer.parseInt(firstNonEmpty(
                            session.getId(),
                            session.getUserId(),
                            String.valueOf(sp.getInt("user_id", 0)),
                            String.valueOf(sp.getInt("id", 0)),
                            "0"
                    ));
                } catch (Exception ignored) {
                    userId = 0;
                }

                return;
            }
        } catch (Exception ignored) {}

        username = firstNonEmpty(
                sp.getString("username", ""),
                sp.getString("user_username", ""),
                sp.getString("player_username", "")
        );

        userId = sp.getInt(
                "user_id",
                sp.getInt("id", 0)
        );

        if (userId <= 0) {
            try {
                userId = Integer.parseInt(firstNonEmpty(
                        sp.getString("user_id", ""),
                        sp.getString("id", ""),
                        "0"
                ));
            } catch (Exception ignored) {
                userId = 0;
            }
        }
    }

    protected void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F4F8FD"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int screenHeightDp = (int) (getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density);
        final boolean compactScreen = screenHeightDp < 700;
        final boolean shortScreen = screenHeightDp < 780;
        root.setPadding(dp(10), dp(compactScreen ? 6 : 8), dp(10), dp(compactScreen ? 6 : 8));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        /* =========================
         * HEADER COMPACT
         * ========================= */
        LinearLayout topCard = new LinearLayout(this);
        topCard.setOrientation(LinearLayout.VERTICAL);
        topCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        topCard.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));
        root.addView(topCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        topCard.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(32)));

        TextView title = text(serviceTitle(), compactScreen ? 18 : 19, "#0B3A78", true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        wizardStepText = text("● Tujuan   ›   2 Harga   ›   3 Pesan", compactScreen ? 9 : 10, "#0B7CFF", true);
        wizardStepText.setPadding(0, 0, 0, 0);
        wizardStepText.setVisibility(View.GONE);
        topCard.addView(wizardStepText, new LinearLayout.LayoutParams(-1, -2));

        modeText = text("Pilih tujuan", compactScreen ? 9 : 10, "#64748B", false);
        modeText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(modeText, new LinearLayout.LayoutParams(0, -1, 1.25f));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        closeLp.setMargins(dp(4), 0, 0, 0);
        titleRow.addView(close, closeLp);
        backBtn = close;
        close.setOnClickListener(v -> finish());

        LinearLayout pointRow = new LinearLayout(this);
        pointFields = pointRow;
        pointRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pointRowLp = new LinearLayout.LayoutParams(-1, -2);
        pointRowLp.setMargins(0, dp(compactScreen ? 4 : 5), 0, 0);
        topCard.addView(pointRow, pointRowLp);

        pickupBtn = compactPointButton("●  Jemput", "Lokasi Anda • ketuk untuk ubah   ›", "#16A34A");
        deliveryBtn = compactPointButton("●  Mau ke mana?", "Cari tujuan / tempel link Maps   ›", "#EF4444");
        pointRow.addView(pickupBtn, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout.LayoutParams deliveryLp = new LinearLayout.LayoutParams(-1, dp(44));
        deliveryLp.setMargins(0, dp(4), 0, 0);
        waypointHeaderRows = new LinearLayout(this);
        waypointHeaderRows.setOrientation(LinearLayout.VERTICAL);
        pointRow.addView(waypointHeaderRows, new LinearLayout.LayoutParams(-1, -2));
        pointRow.addView(deliveryBtn, deliveryLp);
        pointUtilityRow = new LinearLayout(this);
        pointUtilityRow.setGravity(Gravity.CENTER_VERTICAL);
        pickupGpsBadge = text("GPS sedang mencari…", 10, "#64748B", false);
        pickupGpsBadge.setSingleLine(true);
        pickupGpsBadge.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pickupGpsBadge.setPadding(dp(4), 0, dp(4), 0);
        pickupGpsBadge.setOnClickListener(v -> showGpsPickupDialog());
        pointUtilityRow.addView(pickupGpsBadge, new LinearLayout.LayoutParams(0, dp(36), 1));
        waypointHeaderAdd = smallButton("＋ Singgah", "#F0F7FF", "#0B7CFF", "#D7E6F8");
        waypointHeaderAdd.setTextSize(10);
        waypointHeaderAdd.setMinHeight(0); waypointHeaderAdd.setMinimumHeight(0);
        waypointHeaderAdd.setPadding(dp(8), 0, dp(8), 0);
        waypointHeaderAdd.setContentDescription("Tambah persinggahan, opsional, maksimal dua titik");
        waypointHeaderAdd.setOnClickListener(v -> showWaypointDialog());
        pointUtilityRow.addView(waypointHeaderAdd, new LinearLayout.LayoutParams(dp(94), dp(34)));
        pointRow.addView(pointUtilityRow, new LinearLayout.LayoutParams(-1, -2));
        refreshWaypointHeader();

        pickupText = text("Penjemputan: belum dipilih", 9, "#334155", false);
        deliveryText = text("Pengantaran: belum dipilih", 9, "#334155", false);
        pickupText.setVisibility(View.GONE);
        deliveryText.setVisibility(View.GONE);

        // Input teknis tersembunyi untuk resolver link Google Maps.
        // UI publik cukup dua field di atas agar tidak membingungkan pengguna awam.
        googleMapInput = new EditText(this);
        googleMapInput.setVisibility(View.GONE);
        topCard.addView(googleMapInput, new LinearLayout.LayoutParams(1, 1));
        useLinkBtn = new Button(this);
        useLinkBtn.setVisibility(View.GONE);

        /* =========================
         * MAP - PRIORITAS RUANG UTAMA
         * ========================= */
        FrameLayout mapBox = new FrameLayout(this);
        mapBox.setBackground(roundStroke("#EAF4FF", "#AFCFF2", dp(14), 1));
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, 0, 1);
        mapLp.setMargins(0, dp(compactScreen ? 4 : 5), 0, dp(compactScreen ? 4 : 5));
        root.addView(mapBox, mapLp);

        mapView = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.PICKER);
        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(-1, -1);
        webLp.setMargins(dp(1), dp(1), dp(1), dp(1));
        mapBox.addView(mapView, webLp);
        mapView.initialize(null, new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat, double lng) {
                mapReady = true;
                centerLat = lat; centerLng = lng; pickLat = lat; pickLng = lng;
                if (smartFavoriteIntent && validCoord(deliveryLat, deliveryLng) && mapView != null) {
                    mapView.setDelivery(deliveryLat, deliveryLng, deliveryAddress);
                }
                if (validCoord(pickupLat, pickupLng)) {
                    mapView.setPickup(pickupLat, pickupLng, pickupAddress);
                }
                if (liveGpsBest != null && followInitialGpsCamera) {
                    mapView.centerOnInitialLocation(liveGpsBest.getLatitude(), liveGpsBest.getLongitude(), 17f);
                }
                startLiveGps();
                loadMapPlaces();
                loadOnlineDrivers();
                refreshSmartAvailability();
            }
            @Override public void onCenterChanged(double lat, double lng) {
                centerLat = lat; centerLng = lng; pickLat = lat; pickLng = lng;
            }
        });
        mapView.setGestureListener(new TransivaGoogleMapView.GestureListener() {
            @Override public void onGestureStart() {
                pointSelectionGeneration++;
                pointSearchHandler.removeCallbacksAndMessages(null);
                if (transportPlaceSearch != null) transportPlaceSearch.cancel();
                hideKeyboard();
                if (pointSearchScroll != null) pointSearchScroll.setVisibility(View.GONE);
                if (pointSearchResults != null) pointSearchResults.setTag("");
                followInitialGpsCamera = false;
                if ("pickup".equals(mode)) { pickupGpsSelected = false; pickupGpsPinned = true; }
                // Setelah tujuan dikonfirmasi, menggeser peta tidak boleh mengubah nama tujuan.
                if (selectingWaypoint || (!"pickup".equals(mode) && deliverySelectionLocked)) return;
                destinationResolveGeneration++;
                pendingDeliveryTitle = ""; pendingDeliverySubtitle = "";
                if ("pickup".equals(mode)) { if (pickupBtn != null) pickupBtn.setText("●  Jemput di mana?\nMencari lokasi…"); }
                else if (deliveryBtn != null) deliveryBtn.setText("●  Mau ke mana?\nMencari lokasi…");
            }
            @Override public void onGestureEnd() {
                if (selectingWaypoint || (!"pickup".equals(mode) && deliverySelectionLocked)) return;
                final double lat = pickLat, lng = pickLng;
                if (!validCoord(lat, lng)) return;
                final boolean resolvingPickup = "pickup".equals(mode);
                final long generation = ++destinationResolveGeneration;
                featureRuntime.execute(() -> {
                    final String road = reverseAddress(lat, lng);
                    final String admin = buildAdministrativeFallback(lat, lng);
                    featureRuntime.post(mainHandler, () -> resolveNearestGooglePlace(lat, lng, googleName -> {
                        if (destroyed || selectingWaypoint || resolvingPickup != "pickup".equals(mode) || (!resolvingPickup && deliverySelectionLocked) || generation != destinationResolveGeneration) return;
                        String localName = findNearestPlaceName(lat, lng);
                        String landmark = cleanLandmarkName(firstNonEmpty(googleName, localName, ""));
                        String title;
                        if (!landmark.isEmpty()) title = "Dekat " + landmark;
                        else if (!admin.isEmpty()) title = "Dekat " + admin;
                        else if (!road.isEmpty()) title = compactDisplayName(road);
                        else title = String.format(Locale.US, "%.5f, %.5f", lat, lng);
                        String subtitle = compactDisplayName(road);
                        if (subtitle.equalsIgnoreCase(title) || (!landmark.isEmpty() && subtitle.toLowerCase(Locale.ROOT).contains(landmark.toLowerCase(Locale.ROOT)))) subtitle = admin;
                        if (resolvingPickup) {
                            if (pickupBtn != null) pickupBtn.setText("●  Jemput di mana?\n" + title + "?");
                            return;
                        }
                        pendingDeliveryTitle = title;
                        pendingDeliverySubtitle = subtitle;
                        if (deliveryBtn != null) deliveryBtn.setText("●  Mau ke mana?\nKe " + title + "?");
                    }));
                }, () -> featureRuntime.post(mainHandler, () -> { setLoading(false);toastDialog("Sistem sedang sibuk. Coba lagi."); }));
            }
        });

        mapView.setCenterActionListener(() -> {
            if (selectingWaypoint) {
                addWaypointFromCenter();
                return;
            }
            if (!deliverySelectionLocked && "delivery".equals(mode) && validCoord(pickLat, pickLng)) {
                confirmPendingDeliveryFromMap();
                return;
            }
            if ("pickup".equals(mode)) { setPointFromCenter(); return; }
            if (validCoord(pickupLat, pickupLng) && validCoord(deliveryLat, deliveryLng)) handleWizardPrimaryAction();
            else setPointFromCenter();
        });

        gpsBtn = smallButton("⌖", "#FFFFFF", "#0B7CFF", "#9DCAFF");
        gpsBtn.setTextSize(18);
        FrameLayout.LayoutParams gpsMapLp = new FrameLayout.LayoutParams(dp(42), dp(42));
        gpsMapLp.gravity = Gravity.BOTTOM | Gravity.END;
        gpsMapLp.setMargins(0, 0, dp(10), dp(10));
        mapBox.addView(gpsBtn, gpsMapLp);


        /* =========================
         * DRAIV-STYLE BOTTOM CARD
         * ========================= */
        LinearLayout bottomCard = new LinearLayout(this);
        bookingDetailsCard = bottomCard;
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(10), dp(9), dp(10), dp(10));
        bottomCard.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        LinearLayout.LayoutParams bottomLp = new LinearLayout.LayoutParams(-1, -2);
        bottomLp.setMargins(0, dp(6), 0, 0);
        root.addView(bottomCard, bottomLp);
        bottomCard.setVisibility(View.GONE);

        driverAvailabilityText = text("Tidak ada driver " + serviceName() + " yang online", 10, "#B91C1C", true);
        driverAvailabilityText.setGravity(Gravity.CENTER);
        driverAvailabilityText.setPadding(dp(8), dp(5), dp(8), dp(5));
        driverAvailabilityText.setBackground(roundStroke("#FEF2F2", "#FECACA", dp(10), 1));
        driverAvailabilityText.setVisibility(View.GONE);
        LinearLayout.LayoutParams driverAvailabilityLp = new LinearLayout.LayoutParams(-1, -2);
        driverAvailabilityLp.setMargins(0, 0, 0, dp(6));
        bottomCard.addView(driverAvailabilityText, driverAvailabilityLp);

        voucherInput = new EditText(this);
        voucherInput.setSingleLine(true);
        noteInput = new EditText(this);
        noteInput.setSingleLine(true);
        noteInput.setText(driverNoteDraft);

        // 2.3 FINAL ORDER UX: tampilkan hanya keputusan utama. Fitur lanjutan tetap tersedia
        // melalui panel Opsi perjalanan agar halaman final tidak terasa penuh.
        stopPricingText=text("Biaya persinggahan mengikuti tarif admin. Waktu tunggu dihitung per titik.",10,"#0B477D",false);
        bottomCard.addView(stopPricingText);
        TextView finalSectionTitle = text("Ringkasan & pembayaran", 12, "#0B3A78", true);
        finalSectionTitle.setPadding(dp(2), 0, dp(2), dp(5));
        bottomCard.addView(finalSectionTitle, new LinearLayout.LayoutParams(-1, -2));
        tripRouteSummaryText = text("Jemput → Tujuan", 9, "#64748B", false);
        tripRouteSummaryText.setSingleLine(true);
        tripRouteSummaryText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottomCard.addView(tripRouteSummaryText, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomCard.addView(quickRow, new LinearLayout.LayoutParams(-1, dp(42)));

        paymentChoiceBtn = smallButton("balance".equals(paymentMethod) ? "💳 Pembayaran: Transiva Pay" : "💵 Pembayaran: Tunai", "#FFFFFF", "#0B3A78", "#C8D9EC");
        voucherChoiceBtn = smallButton("🏷 Voucher", "#FFFFFF", "#0B3A78", "#C8D9EC");
        paymentChoiceBtn.setTextSize(10);
        voucherChoiceBtn.setTextSize(10);
        quickRow.addView(paymentChoiceBtn, new LinearLayout.LayoutParams(0, -1, 1.2f));
        LinearLayout.LayoutParams voucherLp = new LinearLayout.LayoutParams(0, -1, 0.8f);
        voucherLp.setMargins(dp(6), 0, 0, 0);
        quickRow.addView(voucherChoiceBtn, voucherLp);

        LinearLayout scheduleRow = new LinearLayout(this);
        scheduleRow.setOrientation(LinearLayout.HORIZONTAL);
        scheduleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams scheduleRowLp = new LinearLayout.LayoutParams(-1, dp(40));
        scheduleRowLp.setMargins(0, dp(5), 0, 0);
        bottomCard.addView(scheduleRow, scheduleRowLp);
        Button scheduleBtn = smallButton("🕒 Jadwal: Sekarang", "#FFFFFF", "#0B3A78", "#C8D9EC");
        Button optionsBtn = smallButton("＋ Opsi perjalanan", "#F8FBFF", "#0B7CFF", "#BBD8F7");
        scheduleRow.addView(scheduleBtn, new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams optionsLp = new LinearLayout.LayoutParams(0,-1,1);
        optionsLp.setMargins(dp(6),0,0,0);
        scheduleRow.addView(optionsBtn, optionsLp);

        noteChoiceBtn = smallButton(driverNoteDraft.isEmpty()?"📝 Catatan":"📝 Catatan tersimpan", "#FFFFFF", "#0B3A78", "#C8D9EC");
        Button hematBtn = smallButton("Pakai poin", "#EAF4FF", "#0B7CFF", "#9DCAFF");
        hematBtn.setTextSize(11);
        hematBtn.setMinWidth(0); hematBtn.setMinimumWidth(0);
        TierBadgeUi.applyToButton(hematBtn, TierBadgeUi.getCachedActiveTier(this), dp(18), dp(2));
        familyBtn = smallButton(familyMemberId > 0 ? "👨‍👩‍👧 " + firstNonEmpty(familyMemberName,"Family") : "👨‍👩‍👧 Family", "#FFFFFF", "#0B3A78", "#C8D9EC");
        waypointBtn = smallButton("＋ Tambahkan persinggahan (Opsional)", "#FFFFFF", "#0B3A78", "#C8D9EC");
        groupRideBtn = smallButton("💳 Split Pay", "#FFFFFF", "#0B3A78", "#C8D9EC");
        safetyRideBtn = smallButton("🛡 Guardian", "#EAF4FF", "#0B7CFF", "#9DCAFF");

        LinearLayout advancedBox = new LinearLayout(this);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.setPadding(0, dp(5), 0, 0);
        advancedBox.setVisibility(View.GONE);
        bottomCard.addView(advancedBox, new LinearLayout.LayoutParams(-1, -2));
        // Persinggahan is a first-class option, visible for cash and wallet alike.
        Button waypointQuickBtn = smallButton("＋ Tambahkan persinggahan (Opsional)", "#F0F7FF", "#0B7CFF", "#BBD8F7");
        LinearLayout.LayoutParams waypointQuickLp = new LinearLayout.LayoutParams(-1, dp(44));
        waypointQuickLp.setMargins(0, dp(6), 0, dp(2));
        bottomCard.addView(waypointQuickBtn, waypointQuickLp);
        waypointQuickBtn.setOnClickListener(v -> showWaypointDialog());
        waypointQuickButton = waypointQuickBtn;


        LinearLayout advancedRow1 = new LinearLayout(this); advancedRow1.setOrientation(LinearLayout.HORIZONTAL);
        advancedBox.addView(advancedRow1, new LinearLayout.LayoutParams(-1, dp(40)));
        advancedRow1.addView(noteChoiceBtn,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams hLp=new LinearLayout.LayoutParams(0,-1,1); hLp.setMargins(dp(5),0,dp(5),0); advancedRow1.addView(hematBtn,hLp);
        advancedRow1.addView(familyBtn,new LinearLayout.LayoutParams(0,-1,1));

        ecosystemRow = new LinearLayout(this); ecosystemRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ecosystemLp = new LinearLayout.LayoutParams(-1, dp(40)); ecosystemLp.setMargins(0,dp(5),0,0);
        advancedBox.addView(ecosystemRow, ecosystemLp);
        ecosystemRow.addView(waypointBtn,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams grpLp=new LinearLayout.LayoutParams(0,-1,1); grpLp.setMargins(dp(5),0,dp(5),0); ecosystemRow.addView(groupRideBtn,grpLp);
        ecosystemRow.addView(safetyRideBtn,new LinearLayout.LayoutParams(0,-1,1));

        optionsBtn.setOnClickListener(v -> {
            boolean open = advancedBox.getVisibility() != View.VISIBLE;
            advancedBox.setVisibility(open ? View.VISIBLE : View.GONE);
            optionsBtn.setText(open ? "− Tutup opsi" : "＋ Opsi perjalanan");
            advancedBox.setAlpha(0f); advancedBox.animate().alpha(1f).setDuration(180L).start();
        });
        scheduleBtn.setOnClickListener(v -> showScheduleDialog(scheduleBtn));
        hematBtn.setOnClickListener(v -> {
            if ("hemat".equals(priceMode)) { priceMode = "standard"; TierBadgeUi.restoreHematButton(hematBtn, TierBadgeUi.getCachedActiveTier(this), dp(18), dp(2)); requestPaymentQuote(); }
            else checkHematAccess(hematBtn);
        });
        familyBtn.setOnClickListener(v -> startActivity(new Intent(this, TransivaFamilyActivity.class)));
        waypointBtn.setOnClickListener(v -> showWaypointDialog());
        groupRideBtn.setOnClickListener(v -> showGroupRideDialog());
        safetyRideBtn.setOnClickListener(v -> showSafetyOptionsDialog());
        voucherChoiceBtn.setOnClickListener(v -> showVoucherDialog());
        noteChoiceBtn.setOnClickListener(v -> showNoteDialog());
        paymentChoiceBtn.setOnClickListener(v -> showPaymentDialog());
        updateTransPayOnlyFeatures();

        LinearLayout estimateRow = new LinearLayout(this);
        estimateRow.setOrientation(LinearLayout.HORIZONTAL);
        estimateRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams estimateLp = new LinearLayout.LayoutParams(-1, dp(62));
        estimateLp.setMargins(dp(2), dp(6), dp(2), dp(5));
        bottomCard.addView(estimateRow, estimateLp);

        LinearLayout tripInfo = new LinearLayout(this);
        tripInfo.setOrientation(LinearLayout.VERTICAL);
        tripInfo.setGravity(Gravity.CENTER_VERTICAL);
        estimateRow.addView(tripInfo, new LinearLayout.LayoutParams(0, -1, 1));

        distanceInfoText = text("TransRide • Jarak -", 11, "#0B3A78", true);
        durationInfoText = text("Estimasi waktu -", 10, "#64748B", false);
        tripInfo.addView(distanceInfoText, new LinearLayout.LayoutParams(-1, dp(28)));
        tripInfo.addView(durationInfoText, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout priceBox = new LinearLayout(this);
        priceBox.setOrientation(LinearLayout.VERTICAL);
        priceBox.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        estimateRow.addView(priceBox, new LinearLayout.LayoutParams(0, -1, 1));

        originalPriceText = text("", 10, "#94A3B8", false);
        originalPriceText.setGravity(Gravity.END);
        originalPriceText.setVisibility(View.GONE);
        priceBox.addView(originalPriceText, new LinearLayout.LayoutParams(-1, dp(20)));

        finalPriceText = text("Rp -", 19, "#0B3A78", true);
        finalPriceText.setOnClickListener(v -> {
            if (!ordering && !locationQuoteReady && !quoteLoading && locationReady()) requestPaymentQuote();
        });
        finalPriceText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        priceBox.addView(finalPriceText, new LinearLayout.LayoutParams(-1, dp(30)));

        discountInfoText = text("", 9, "#16A34A", true);
        discountInfoText.setGravity(Gravity.END);
        discountInfoText.setVisibility(View.GONE);
        priceBox.addView(discountInfoText, new LinearLayout.LayoutParams(-1, dp(16)));

        fareText = text("Menghitung biaya perjalanan", 8, "#64748B", false);
        fareText.setVisibility(View.GONE);
        paymentSummaryText = text("", 8, "#64748B", false);
        paymentSummaryText.setVisibility(View.GONE);

        // CTA wizard selalu berada di posisi yang sama pada bagian bawah kartu.
        orderBtn = smallButton("LANJUT — PILIH TUJUAN", "#0B7CFF", "#FFFFFF", "#0B7CFF");
        orderBtn.setTextSize(14);
        orderBtn.setVisibility(View.GONE);
        orderBtn.setEnabled(false);
        LinearLayout.LayoutParams orderLp = new LinearLayout.LayoutParams(-1, dp(50));
        orderLp.setMargins(0, dp(7), 0, 0);
        bottomCard.addView(orderBtn, orderLp);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(50), dp(50));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

        CustomerAdaptivePanels.install(root, mapBox);
        setContentView(page);
        CustomerAppSettings.apply(this);
        bindActions();
        updateModeUI();
    }



    protected void showScheduleDialog(Button target) {
        final java.util.Calendar c = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(this, (d, y, m, day) -> {
            new android.app.TimePickerDialog(this, (t, hour, minute) -> {
                java.util.Calendar chosen = java.util.Calendar.getInstance();
                chosen.set(y, m, day, hour, minute, 0);
                if (chosen.getTimeInMillis() < System.currentTimeMillis() + 15 * 60 * 1000L) {
                    toastDialog("Jadwal minimal 15 menit dari sekarang."); return;
                }
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                scheduledAt = f.format(chosen.getTime());
                java.text.SimpleDateFormat label = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
                target.setText("🕒 " + label.format(chosen.getTime()));
                if ("balance".equals(paymentMethod)) { paymentMethod = "cash"; if (paymentChoiceBtn != null) paymentChoiceBtn.setText("💵 Pembayaran: Tunai"); }
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true).show();
        }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    protected void showVoucherDialog() {
        RoyaltyVoucherPicker.show(this,royaltyOriginalFare,royaltyOriginalFare,voucherInput==null?"":voucherInput.getText().toString(),(code,discount)->{
            if(voucherInput!=null)voucherInput.setText(code);
            voucherChoiceBtn.setText(code.isEmpty()?"🏷 Voucher":"🏷 Voucher dipilih");
            requestPaymentQuote();
        });
    }

    protected void showNoteDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setText(noteInput == null ? "" : noteInput.getText().toString());
        input.setHint("Contoh: jemput di depan pagar");
        input.setPadding(dp(14), dp(8), dp(14), dp(8));

        final android.app.AlertDialog noteDialog = new TransivaAlertDialogBuilder(this)
                .setTitle("Catatan untuk driver")
                .setView(input)
                .setNegativeButton("Batal", (dialog, which) -> orderAfterNote=false)
                .setPositiveButton("Simpan", null)
                .create();
        noteDialog.setOnCancelListener(dialog -> orderAfterNote=false);
        noteDialog.show();
        noteDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String note=input.getText().toString().trim();
            if(orderAfterNote && note.isEmpty()) { input.setError("Isi petunjuk singkat untuk driver"); input.requestFocus(); return; }
            driverNoteDraft=note;
            if(noteInput!=null) noteInput.setText(note);
            if(noteChoiceBtn!=null) noteChoiceBtn.setText(note.isEmpty()?"📝 Catatan":"📝 Catatan tersimpan");
            boolean resumeOrder=orderAfterNote; orderAfterNote=false; noteDialog.dismiss();
            if(resumeOrder) createOrder();
        });
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("driver_note_draft", noteInput==null?driverNoteDraft:noteInput.getText().toString());
        super.onSaveInstanceState(outState);
    }

    protected void showWaypointDialog() {
        if (isRouteConfirmationLocked()) return;
        int count = ecosystemFeatures.waypoints.length();
        if (count >= maxStopCount()) {
            new TransivaAlertDialogBuilder(this)
                    .setTitle("Tambah Tujuan")
                    .setMessage("Batas layanan saat ini " + maxStopCount() + " persinggahan. Hapus titik jika ingin memilih ulang.")
                    .setNegativeButton("Hapus semua", (d,w) -> {
                        ecosystemFeatures.clearWaypoints();
                        selectingWaypoint = false;
                        if (mapView != null) {
                            mapView.clearWaypoints();
                            updateModeUI();
                        }
                        updateWaypointButton();
                        requestPaymentQuote();
                        requestVisibleOsrmRoute();
                    })
                    .setPositiveButton("Tutup", null)
                    .show();
            return;
        }

        selectingWaypoint = true;
        if (mapView != null) {
            mapView.setWaypointSelectionMode(count + 1);
            mapView.showCenterPin(true);
        }
        if (modeText != null) {
            modeText.setText("Geser peta ke Stop " + (count + 1) + ", lalu tekan TAMBAH STOP " + (count + 1));
        }
        android.widget.Toast.makeText(this, "Geser peta ke lokasi stop lalu tekan TAMBAH STOP " + (count + 1), android.widget.Toast.LENGTH_LONG).show();
    }

    protected void addWaypointFromCenter() {
        double lat = centerLat;
        double lng = centerLng;
        if (mapView != null && mapView.isReady()) {
            com.google.android.gms.maps.model.LatLng c = mapView.getCenter();
            if (c != null && validCoord(c.latitude, c.longitude)) {
                lat = c.latitude;
                lng = c.longitude;
                centerLat = lat;
                centerLng = lng;
                pickLat = lat;
                pickLng = lng;
            }
        }
        if (!validCoord(lat,lng)) { toastDialog("Posisi peta belum tersedia."); return; }

        if(ecosystemFeatures.waypoints.length()>=maxStopCount()){toastDialog("Batas persinggahan " + maxStopCount() + " titik.");return;}
        final int sequence = ecosystemFeatures.waypoints.length() + 1;
        if (!ecosystemFeatures.addWaypoint(lat,lng,"Stop " + sequence)) {
            toastDialog("Maksimal 2 pemberhentian.");
            selectingWaypoint = false;
            updateModeUI();
            return;
        }

        selectingWaypoint = false;
        if (mapView != null) mapView.setWaypoints(ecosystemFeatures.waypoints);
        updateWaypointButton();
        updateModeUI();
        requestPaymentQuote();
        requestVisibleOsrmRoute();

        final double stopLat = lat, stopLng = lng;
        featureRuntime.execute(() -> {
            String address = buildSmartAddress(stopLat, stopLng);
            featureRuntime.post(mainHandler, () -> {
                if (destroyed) return;
                try {
                    JSONObject wp = ecosystemFeatures.waypoints.optJSONObject(sequence - 1);
                    if (wp != null && address != null && !address.trim().isEmpty()) wp.put("address", address);
                } catch (Exception ignored) {}
                if (mapView != null) mapView.setWaypoints(ecosystemFeatures.waypoints);
                updateWaypointButton();
                requestPaymentQuote();
            });
        }, () -> featureRuntime.post(mainHandler, () -> { setLoading(false);toastDialog("Sistem sedang sibuk. Coba lagi."); }));

        updateModeUI();
        showWaypointNotePopup(sequence);
    }

    protected void showWaypointNotePopup(final int sequence) {
        String existingGlobalNote = noteInput == null ? "" : noteInput.getText().toString().trim();
        if (!existingGlobalNote.isEmpty()) {
            try { JSONObject wp = ecosystemFeatures.waypoints.optJSONObject(sequence - 1); if (wp != null && wp.optString("note","").trim().isEmpty()) wp.put("note", existingGlobalNote); } catch (Exception ignored) {}
            if (mapView != null) mapView.setWaypoints(ecosystemFeatures.waypoints);
            requestPaymentQuote(); requestVisibleOsrmRoute();
            return;
        }
        final EditText input = new EditText(this);
        input.setHint("Contoh: Singgah di ATM / ambil barang / tunggu di depan");
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        new TransivaAlertDialogBuilder(this)
                .setTitle("Catatan Stop " + sequence)
                .setMessage("Tambahkan catatan agar driver tahu apa yang perlu dilakukan di titik ini.")
                .setView(input)
                .setNegativeButton("Lewati", (d,w) -> { requestPaymentQuote(); requestVisibleOsrmRoute(); })
                .setPositiveButton("Simpan", (d,w) -> {
                    String note = input.getText().toString().trim();
                    try {
                        JSONObject wp = ecosystemFeatures.waypoints.optJSONObject(sequence - 1);
                        if (wp != null) wp.put("note", note);
                    } catch (Exception ignored) {}
                    if (mapView != null) mapView.setWaypoints(ecosystemFeatures.waypoints);
                    requestPaymentQuote();
                    requestVisibleOsrmRoute();
                    android.widget.Toast.makeText(this, "Stop " + sequence + " berhasil ditambahkan", android.widget.Toast.LENGTH_SHORT).show();
                }).show();
    }

    protected boolean isRouteConfirmationLocked() {
        return bookingDetailsCard != null && bookingDetailsCard.getVisibility() == View.VISIBLE;
    }

    protected void refreshWaypointHeader() {
        if (waypointHeaderRows == null) return;
        waypointHeaderRows.removeAllViews();
        int count = ecosystemFeatures.waypoints.length();
        for (int i = 0; i < count; i++) {
            final int position = i;
            JSONObject wp = ecosystemFeatures.waypoints.optJSONObject(i);
            String name = "Singgah " + (i + 1);
            String address = wp == null ? "" : wp.optString("address", "");
            if (address.trim().isEmpty()) address = "Titik dipilih di peta";
            Button row = compactPointButton("●  " + name, address, "#D97706");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
            lp.topMargin = dp(4);
            waypointHeaderRows.addView(row, lp);
            row.setContentDescription(name + ", " + address + ". Ketuk untuk mengubah atau menghapus.");
            row.setEnabled(!isRouteConfirmationLocked());
            row.setOnClickListener(v -> { if (isRouteConfirmationLocked()) return; new TransivaAlertDialogBuilder(this)
                    .setTitle(name)
                    .setMessage(addressForWaypoint(position))
                    .setPositiveButton("Ubah lokasi", (dialog, which) -> {
                        removeWaypointAt(position);
                        showWaypointDialog();
                    })
                    .setNegativeButton("Hapus", (dialog, which) -> removeWaypointAt(position))
                    .setNeutralButton("Batal", null).show(); });
        }
        if (waypointHeaderAdd != null) waypointHeaderAdd.setVisibility(
                count < maxStopCount() && !isRouteConfirmationLocked() ? View.VISIBLE : View.GONE);
        waypointHeaderRows.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }
    protected String addressForWaypoint(int index) {
        JSONObject wp = ecosystemFeatures.waypoints.optJSONObject(index);
        return wp == null ? "" : wp.optString("address", "Titik persinggahan");
    }
    protected void removeWaypointAt(int index) {
        if (isRouteConfirmationLocked()) return;
        JSONArray updated = new JSONArray();
        for (int i = 0; i < ecosystemFeatures.waypoints.length(); i++)
            if (i != index) updated.put(ecosystemFeatures.waypoints.optJSONObject(i));
        ecosystemFeatures.clearWaypoints();
        for (int i = 0; i < updated.length(); i++) {
            JSONObject wp = updated.optJSONObject(i);
            if (wp == null) continue;
            if (ecosystemFeatures.addWaypoint(wp.optDouble("latitude"), wp.optDouble("longitude"), wp.optString("address"))) {
                JSONObject target = ecosystemFeatures.waypoints.optJSONObject(i);
                if (target != null) try { target.put("note", wp.optString("note")); } catch (Exception ignored) {}
            }
        }
        if (mapView != null) mapView.setWaypoints(ecosystemFeatures.waypoints);
        updateWaypointButton();
        updateModeUI();
        requestPaymentQuote();
        requestVisibleOsrmRoute();
    }
    protected void updateWaypointButton(){
        int count = ecosystemFeatures.waypoints.length();
        if (waypointBtn != null) waypointBtn.setText(count >= maxStopCount() ? "✓ Persinggahan " + count + "/" + maxStopCount() : "＋ Persinggahan " + count + "/" + maxStopCount());
        if (waypointQuickButton != null) waypointQuickButton.setVisibility(View.GONE);
        refreshWaypointHeader();
    }

    protected void showGroupRideDialog() {
        if (!"balance".equals(paymentMethod)) { toastDialog("Split Pay hanya tersedia jika pembayaran Transiva Pay dipilih."); return; }
        final String[] options={"Sendiri (tanpa split)","2 orang","3 orang","4 orang"};
        new TransivaAlertDialogBuilder(this).setTitle("Split Pay").setSingleChoiceItems(options, Math.max(0, Math.min(3, ecosystemFeatures.groupSize-1)), (d,which)->{
            d.dismiss();
            if(which==0){ ecosystemFeatures.groupSize=1; ecosystemFeatures.splitFareMode="none"; if(splitBillManager!=null)splitBillManager.clear(); if(groupRideBtn!=null)groupRideBtn.setText("💳 Split Pay"); return; }
            int size=which+1; ecosystemFeatures.groupSize=size; ecosystemFeatures.splitFareMode="custom";
            if(splitBillManager!=null) splitBillManager.start(size,lastQuotedFare,"ride");
        }).setNegativeButton("Batal",null).show();
    }

    protected void updateTransPayOnlyFeatures() {
        boolean show="balance".equals(paymentMethod);
        if(familyBtn!=null) familyBtn.setVisibility(show || familyMemberId > 0 ? View.VISIBLE : View.GONE);
        if(waypointBtn!=null) waypointBtn.setVisibility(View.GONE);
        if(groupRideBtn!=null) groupRideBtn.setVisibility(show?View.VISIBLE:View.GONE);
        if(!show){
            // Payment selection must not erase the passenger selected in Transiva Family.
            ecosystemFeatures.groupSize=1; ecosystemFeatures.splitFareMode="none";
        }
    }

    protected void showSafetyOptionsDialog() {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8),0,dp(8),0);
        final android.widget.Switch guardian=new android.widget.Switch(this); guardian.setText("Trip Monitoring otomatis"); guardian.setChecked(ecosystemFeatures.tripMonitoring); box.addView(guardian);
        final android.widget.Switch audio=new android.widget.Switch(this); audio.setText("AudioProtect / Safety Recording"); audio.setChecked(ecosystemFeatures.audioProtect); box.addView(audio);
        TextView info=text("AudioProtect hanya aktif saat perjalanan dan file disimpan privat di aplikasi. Android akan menampilkan notifikasi saat mikrofon aktif.",11,"#64748B",false); info.setPadding(0,dp(8),0,0); box.addView(info);
        new TransivaAlertDialogBuilder(this).setTitle("Proteksi perjalanan").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->{
            ecosystemFeatures.tripMonitoring=guardian.isChecked(); ecosystemFeatures.audioProtect=audio.isChecked();
            if(audio.isChecked() && android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},91);
            if(safetyRideBtn!=null)safetyRideBtn.setText(ecosystemFeatures.tripMonitoring?"🛡 Guardian":"🛡 Safety");
        }).show();
    }

    protected void refreshSmartAvailability(){
        if(!validCoord(pickupLat,pickupLng))return;
        SmartAvailabilityClient.check(this,pickupLat,pickupLng,serviceName(),r->{
            if(r==null||!r.optBoolean("success",false)||driverAvailabilityText==null)return;
            String label=r.optString("label",""); int score=r.optInt("availability_score",-1); boolean low=r.optBoolean("low_availability",false);
            if(!label.isEmpty()){driverAvailabilityText.setText((low?"⚠ ":"✓ ")+label+(score>=0?" • "+score+"%":""));driverAvailabilityText.setVisibility(View.VISIBLE);}
        });
    }

    protected void checkHematAccess(Button hematBtn) {
        if (!ensureAuthTokenForOrder()) return;
        hematBtn.setEnabled(false);
        TierBadgeUi.showSpinner(hematBtn, dp(20));
        featureRuntime.execute(() -> {
            try {
                JSONObject res = postJson(HEMAT_STATUS_URL, new JSONObject());
                featureRuntime.post(mainHandler, () -> {
                    hematBtn.setEnabled(true);
                    if (!res.optBoolean("success", false)) {
                        TierBadgeUi.restoreHematButton(hematBtn, TierBadgeUi.getCachedActiveTier(this), dp(20), dp(2));
                        toastDialog(firstNonEmpty(res.optString("message", ""), "Status Poin Transiva belum dapat diperiksa."));
                        return;
                    }
                    JSONObject coin = res.optJSONObject("points");
                    if(coin==null)coin=res.optJSONObject("coin");
                    if (coin == null) coin = res.optJSONObject("ride");
                    int balanceCoin = coin == null ? 0 : coin.optInt("balance", coin.optInt("remaining", 0));
                    int minRedeem = coin == null ? 1000 : coin.optInt("min_redeem_coins", coin.optInt("limit", 1000));
                    int value = coin == null ? 1 : coin.optInt("coin_value_rupiah", 1);
                    String tier = coin == null ? "BRONZE" : coin.optString("tier", "BRONZE");
                    boolean canRedeem = coin != null && coin.optBoolean("can_redeem", balanceCoin >= minRedeem);
                    TierBadgeUi.saveActiveTier(this, tier);
                    TierBadgeUi.restoreHematButton(hematBtn, tier, dp(20), dp(2));
                    if (!canRedeem) {
                        priceMode = "standard";
                        new TransivaAlertDialogBuilder(this)
                                .setTitle("Poin belum cukup")
                                .setMessage("Saldo kamu " + balanceCoin + " poin. Minimal " + minRedeem + " poin untuk potongan pembayaran.")
                                .setPositiveButton("Mengerti", null).show();
                        return;
                    }
                    priceMode = "hemat";
                    new TransivaAlertDialogBuilder(this)
                            .setTitle("Hemat dengan Poin Transiva")
                            .setMessage("Saldo " + balanceCoin + " poin • 1 poin = Rp" + value + ". Sistem akan memakai poin otomatis sesuai batas aman yang diatur admin.")
                            .setPositiveButton("Pakai poin", (d, w) -> requestPaymentQuote())
                            .setNegativeButton("Batal", (d, w) -> { priceMode = "standard"; TierBadgeUi.restoreHematButton(hematBtn, tier, dp(20), dp(2)); })
                            .show();
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> { hematBtn.setEnabled(true); TierBadgeUi.restoreHematButton(hematBtn, TierBadgeUi.getCachedActiveTier(this), dp(20), dp(2)); toastDialog(TransivaUserMessage.network()); });
            }
        }, () -> { Exception e = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis"); 
                featureRuntime.post(mainHandler, () -> { hematBtn.setEnabled(true); TierBadgeUi.restoreHematButton(hematBtn, TierBadgeUi.getCachedActiveTier(this), dp(20), dp(2)); toastDialog(TransivaUserMessage.network()); });
             });
    }

    protected void showPaymentDialog() {
        String[] methods = {"Tunai", "Transiva Pay"};
        int checked = paymentMethod.equals("balance") ? 1 : 0;

        new TransivaAlertDialogBuilder(this)
                .setTitle("Pilih metode pembayaran")
                .setSingleChoiceItems(methods, checked, (dialog, which) -> {
                    paymentMethod = which == 1 ? "balance" : "cash";
                    paymentChoiceBtn.setText(which == 1 ? "💳 Pembayaran: Transiva Pay" : "💵 Pembayaran: Tunai");
                    updateTransPayOnlyFeatures();
                    if (!"balance".equals(paymentMethod) && splitBillManager != null) splitBillManager.clear();
                    dialog.dismiss();
                    requestPaymentQuote();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    protected void bindActions() {
        pickupBtn.setOnClickListener(v -> { if (isRouteConfirmationLocked()) return; mode = "pickup"; openDestinationAutocomplete(); });
        deliveryBtn.setOnClickListener(v -> { if (isRouteConfirmationLocked()) return; mode = "delivery"; openDestinationAutocomplete(); });
        gpsBtn.setOnClickListener(v -> goToMyLocation());
        backBtn.setOnClickListener(v -> finish());
        orderBtn.setOnClickListener(v -> handleWizardPrimaryAction());
        googleMapInput.setOnClickListener(v -> openDestinationAutocomplete());
        useLinkBtn.setOnClickListener(v -> openDestinationAutocomplete());
    }

    /**
     * Smart destination search for TransRide + TransCar.
     * Places only selects a destination; Transiva's existing order, route and fare flow stays unchanged.
     */
    protected void initializePlacesAutocomplete() {
        try {
            if (Places.isInitialized()) return;
            String apiKey = getString(R.string.google_maps_key);
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                Places.initializeWithNewPlacesApiEnabled(getApplicationContext(), apiKey.trim());
            }
        } catch (Exception ignored) {
            // Map picker remains available as a safe fallback.
        }
    }

    private LinearLayout pointFields;
    private LinearLayout pointUtilityRow;
    private Button waypointHeaderAdd;
    private FrameLayout pointSearchHost;
    private EditText pointSearchInput;
    private LinearLayout pointSearchResults;
    private ScrollView pointSearchScroll;
    private final Handler pointSearchHandler = new Handler(Looper.getMainLooper());
    private long pointSelectionGeneration;

    protected void openDestinationAutocomplete() {
        if (ordering || isRouteConfirmationLocked()) return;
        followInitialGpsCamera = false;
        if ("pickup".equals(mode)) { pickupGpsSelected = false; pickupGpsPinned = true; }
        showSmartDestinationSearch();
    }

    /** The selected field becomes the search box; the native map stays in place. */
    protected void showSmartDestinationSearch() {
        final boolean compactScreen = getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density < 700;
        closePointSearch();
        final String selectedMode = "pickup".equals(mode) ? "pickup" : "delivery";
        activeSearchOriginLat = currentSearchOriginLat();
        activeSearchOriginLng = currentSearchOriginLng();
        final double oldLat = "pickup".equals(selectedMode) ? pickupLat : deliveryLat;
        final double oldLng = "pickup".equals(selectedMode) ? pickupLng : deliveryLng;
        handlePointButtonClick(selectedMode);
        if (mapView != null && validCoord(oldLat, oldLng)) {
            centerLat = pickLat = oldLat; centerLng = pickLng = oldLng;
            mapView.centerOnInitialLocation(oldLat, oldLng, 17f);
        }
        final Button selectedButton = "pickup".equals(selectedMode) ? pickupBtn : deliveryBtn;
        final FrameLayout host = new FrameLayout(this);
        host.setTag(selectedMode);
        pointSearchHost = host;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        host.addView(box, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText search = new EditText(this);
        pointSearchInput = search;
        search.setSingleLine(true);
        search.setTextSize(12);
        search.setPadding(dp(10), dp(3), dp(8), dp(3));
        search.setHint("pickup".equals(selectedMode) ? "Cari jemput / tempel link Maps" : "Cari tujuan / tempel link Maps");
        search.setContentDescription("pickup".equals(selectedMode) ? "Pencarian lokasi jemput" : "Pencarian lokasi tujuan");
        search.setTextColor(Color.parseColor("#0B3A78"));
        search.setBackground(roundStroke("#FFFFFF", "pickup".equals(selectedMode) ? "#16A34A" : "#EF4444", dp(14), 2));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        row.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button map = smallButton("Peta", "#EAF4FF", "#0B7CFF", "#C8D9EC");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(54), dp(42));
        mlp.setMargins(dp(4), 0, 0, 0);
        row.addView(map, mlp);
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        pointSearchScroll = scroll;
        LinearLayout results = new LinearLayout(this);
        pointSearchResults = results;
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));
        scroll.setVisibility(View.GONE);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, dp(compactScreen ? 126 : 150)));
        int index = pointFields.indexOfChild(selectedButton);
        pointFields.addView(host, index, new LinearLayout.LayoutParams(-1, -2));
        selectedButton.setVisibility(View.GONE);
        if (pointUtilityRow != null) pointUtilityRow.setVisibility(View.GONE);
        map.setOnClickListener(v -> { closePointSearch(); updateModeUI(); });
        initializePlacesAutocomplete();
        final AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int st, int count, int after) { }
            public void afterTextChanged(Editable e) { }
            public void onTextChanged(CharSequence c, int st, int before, int count) {
                pointSelectionGeneration++;
                setLoading(false);
                pointSearchHandler.removeCallbacksAndMessages(null);
                if (transportPlaceSearch != null) transportPlaceSearch.cancel();
                final String query = c.toString().trim();
                results.setTag(query);
                results.removeAllViews();
                scroll.scrollTo(0, 0);
                scroll.setVisibility(query.length() >= PLACES_MIN_QUERY_CHARS ? View.VISIBLE : View.GONE);
                pointSearchHandler.postDelayed(() -> {
                    if (!isPointSearchCurrent(host, results, query)) return;
                    String mapsLink = TransportMapsInput.findLink(query);
                    if (!mapsLink.isEmpty()) {
                        showPlacesFriendlyFallback(results, "Membaca lokasi dari Google Maps…");
                        googleMapInput.setText(mapsLink);
                        useGoogleMapLinkForMode();
                    } else if (query.length() >= PLACES_MIN_QUERY_CHARS) {
                        loadSmartPredictions(query, token, results, host);
                    }
                }, PLACES_DEBOUNCE_MS);
            }
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            pointSearchHandler.removeCallbacksAndMessages(null);
            final String query = search.getText().toString().trim();
            String link = TransportMapsInput.findLink(query);
            if (!link.isEmpty()) {
                googleMapInput.setText(link); useGoogleMapLinkForMode();
            } else if (query.length() >= PLACES_MIN_QUERY_CHARS) {
                results.setTag(query); scroll.setVisibility(View.VISIBLE);
                loadSmartPredictions(query, token, results, host);
            }
            hideKeyboard();
            return true;
        });
        if (getIntent() != null && "delivery".equals(selectedMode)) {
            String query = getIntent().getStringExtra("destination_query");
            getIntent().removeExtra("destination_query");
            if (query != null && !query.trim().isEmpty()) {
                search.setText(query.trim()); search.setSelection(search.length());
            }
        }
        search.requestFocus();
        search.post(() -> {
            if (pointSearchHost != host || destroyed) return;
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private boolean isPointSearchCurrent(FrameLayout host, LinearLayout results, String query) {
        return !destroyed && host != null && host.getParent() != null
                && mode.equals(host.getTag()) && query.equals(results.getTag());
    }

    private void closePointSearch() {
        hideKeyboard();
        setLoading(false);
        pointSearchHandler.removeCallbacksAndMessages(null);
        if (transportPlaceSearch != null) transportPlaceSearch.cancel();
        pointSelectionGeneration++;
        if (pointSearchHost != null && pointSearchHost.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)pointSearchHost.getParent()).removeView(pointSearchHost);
        pointSearchHost = null; pointSearchInput = null;
        pointSearchResults = null; pointSearchScroll = null;
        if (pointUtilityRow != null) pointUtilityRow.setVisibility(View.VISIBLE);
        if (pickupBtn != null) pickupBtn.setVisibility(View.VISIBLE);
        if (deliveryBtn != null) deliveryBtn.setVisibility(View.VISIBLE);
    }

    private TransportPlaceSearch transportPlaceSearch;
    private double activeSearchOriginLat = Double.NaN, activeSearchOriginLng = Double.NaN;

    protected void loadSmartPredictions(String query, AutocompleteSessionToken token, LinearLayout results, FrameLayout overlay) {
        if (query == null || !isPointSearchCurrent(overlay, results, query.trim())) return;
        showPlacesFriendlyFallback(results, "Mencari tempat di sekitar…");
        try {
            if (transportPlaceSearch == null) transportPlaceSearch = new TransportPlaceSearch(Places.createClient(this));
            transportPlaceSearch.search(query, currentSearchOriginLat(), currentSearchOriginLng(), token,
                    new TransportPlaceSearch.Callback() {
                public void onResults(java.util.List<TransportSearchRanking.Result> rows, boolean wider, boolean complete) {
                    if (!isPointSearchCurrent(overlay, results, query.trim())) return;
                    renderNearbyResults(rows, wider, complete, token, results, overlay);
                }
                public void onFailure() {
                    if (!isPointSearchCurrent(overlay, results, query.trim())) return;
                    showPlacesFriendlyFallback(results, "Tempat belum dapat dimuat. Coba lagi, tempel link Maps, atau pilih di peta.");
                }
            });
        } catch (Exception error) {
            placesErrorDetail("TRANSPORT_SEARCH", error);
            showPlacesFriendlyFallback(results, "Pencarian belum tersedia. Tempel link Maps atau pilih di peta.");
        }
    }

    protected double currentSearchOriginLat() {
        if (pointSearchHost != null && validCoord(activeSearchOriginLat, activeSearchOriginLng)) return activeSearchOriginLat;
        if (validCoord(pickupLat, pickupLng)) return pickupLat;
        if (liveGpsBest != null) return liveGpsBest.getLatitude();
        return validCoord(centerLat, centerLng) ? centerLat : Double.NaN;
    }
    protected double currentSearchOriginLng() {
        if (pointSearchHost != null && validCoord(activeSearchOriginLat, activeSearchOriginLng)) return activeSearchOriginLng;
        if (validCoord(pickupLat, pickupLng)) return pickupLng;
        if (liveGpsBest != null) return liveGpsBest.getLongitude();
        return validCoord(centerLat, centerLng) ? centerLng : Double.NaN;
    }

    protected void showPlacesFriendlyFallback(LinearLayout results, String message) {
        if (results == null) return;
        results.removeAllViews();
        TextView info = text(message, 11, "#64748B", false);
        info.setPadding(dp(8), dp(10), dp(8), dp(10));
        results.addView(info);
    }

    private void renderNearbyResults(java.util.List<TransportSearchRanking.Result> rows, boolean wider, boolean complete,
                                     AutocompleteSessionToken token, LinearLayout results, FrameLayout host) {
        int previousScroll = pointSearchScroll == null ? 0 : pointSearchScroll.getScrollY();
        results.removeAllViews();
        if (rows.isEmpty()) {
            showPlacesFriendlyFallback(results, "Belum ada hasil. Coba nama tempat lebih lengkap atau pilih di peta.");
            return;
        }
        TextView heading = text(wider ? "Area sekitar belum ada hasil · area diperluas"
                : (complete ? "Sekitar lokasi · jarak lurus" : "Sekitar lokasi · melengkapi hasil…"), 9, "#64748B", false);
        heading.setPadding(dp(8), dp(5), dp(8), dp(5));
        results.addView(heading);
        int n = Math.min(10, rows.size());
        for (int i = 0; i < n; i++) {
            TransportSearchRanking.Result place = rows.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(9), dp(8), dp(9), dp(8));
            row.setMinimumHeight(dp(56));
            LinearLayout titleLine = new LinearLayout(this);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(place.name, 12, "#172033", true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView distance = text(TransportSearchRanking.distanceLabel(place.meters), 10, "#0B7CFF", true);
            distance.setGravity(Gravity.END);
            distance.setSingleLine(true);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(62), -2);
            dlp.leftMargin = dp(6);
            titleLine.addView(distance, dlp);
            row.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));
            String address = place.address.replaceAll("(?i),?\\s*Indonesia\\s*$", "").trim();
            TextView subtitle = text(address.isEmpty() ? "Ketuk untuk memilih lokasi" : address, 10, "#64748B", false);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            subtitle.setPadding(0, dp(3), 0, 0);
            row.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
            row.setContentDescription(place.name + ", " + place.address + ", jarak lurus " + TransportSearchRanking.distanceLabel(place.meters));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setOnClickListener(v -> selectNearbyResult(place, token, host));
            results.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i < n - 1) {
                View divider = new View(this); divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
                results.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (pointSearchScroll != null) pointSearchScroll.scrollTo(0, previousScroll);
    }

    private void selectNearbyResult(TransportSearchRanking.Result row, AutocompleteSessionToken token, FrameLayout host) {
        final String selectedMode = mode;
        final long generation = ++pointSelectionGeneration;
        if (transportPlaceSearch != null) transportPlaceSearch.cancel();
        if (row.hasCoordinates()) {
            applyNearbyResult(row, host, selectedMode, generation);
            return;
        }
        try {
            java.util.List<Place.Field> fields = java.util.Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);
            Places.createClient(this).fetchPlace(FetchPlaceRequest.builder(row.id, fields).setSessionToken(token).build())
                    .addOnSuccessListener(response -> {
                Place p = response.getPlace(); LatLng point = p.getLatLng();
                if (point == null) {
                    if (!destroyed && generation == pointSelectionGeneration) toastDialog("Koordinat tempat belum tersedia. Pilih di peta.");
                    return;
                }
                applyNearbyResult(new TransportSearchRanking.Result(row.id, firstNonEmpty(p.getName(), row.name),
                        firstNonEmpty(p.getAddress(), row.address), point.latitude, point.longitude, row.meters), host, selectedMode, generation);
            }).addOnFailureListener(error -> {
                if (!destroyed && generation == pointSelectionGeneration && host.getParent() != null)
                    toastDialog("Tempat belum dapat dipilih. Coba lagi atau pilih di peta.");
            });
        } catch (Exception error) { toastDialog("Tempat belum dapat dipilih. Coba lagi atau pilih di peta."); }
    }

    private void applyNearbyResult(TransportSearchRanking.Result row, FrameLayout host, String selectedMode, long generation) {
        if (destroyed || generation != pointSelectionGeneration || host.getParent() == null
                || !selectedMode.equals(mode) || !validCoord(row.latitude, row.longitude)) return;
        String address = row.address.isEmpty() ? row.name : row.name + ", " + row.address;
        if ("pickup".equals(selectedMode)) {
            pickupGpsSelected = false; pickupGpsPinned = true;
            pickupLat = row.latitude; pickupLng = row.longitude; pickupAddress = address;
            pickupText.setText("Penjemputan: " + address);
            pickupBtn.setText("●  Jemput\n" + shortAddress(row.name));
            updateGpsBadge(liveGpsBest);
            if (mapView != null) { mapView.setPickup(pickupLat, pickupLng, address); mapView.moveTo(pickupLat, pickupLng, 17f); }
        } else {
            deliveryLat = row.latitude; deliveryLng = row.longitude; deliveryAddress = address;
            deliverySelectionLocked = true;
            deliveryText.setText("Pengantaran: " + address);
            deliveryBtn.setText("●  Tujuan\n" + shortAddress(row.name));
            if (mapView != null) { mapView.setDelivery(deliveryLat, deliveryLng, address); mapView.moveTo(deliveryLat, deliveryLng, 17f); }
        }
        closePointSearch(); updateModeUI(); requestPaymentQuote();
    }

    protected String placesErrorDetail(String stage, Exception e) {
        String type = e == null ? "null" : e.getClass().getName();
        String msg = (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) ? "(tanpa pesan)" : e.getMessage().trim();
        String cause = "";
        if (e != null && e.getCause() != null) {
            cause = "\nCause: " + e.getCause().getClass().getSimpleName() + ": " + String.valueOf(e.getCause().getMessage());
        }
        int statusCode = -1;
        String statusName = "NON_API_EXCEPTION";
        if (e instanceof ApiException) {
            ApiException ae = (ApiException)e;
            statusCode = ae.getStatusCode();
            statusName = placesStatusName(statusCode);
        }
        String runtimeKey = "";
        try { runtimeKey = getString(R.string.google_maps_key).trim(); } catch (Exception ignored) {}
        String keyTail = runtimeKey.length() <= 7 ? runtimeKey : runtimeKey.substring(runtimeKey.length() - 7);
        String sha1 = runtimeSigningSha1();
        String expectedTail = "ThmOXqo";
        String keyMatch = expectedTail.equals(keyTail) ? "COCOK" : "TIDAK COCOK";
        String diag = "Places gagal [" + stage + "]" +
                "\nStatus: " + statusName + " (" + statusCode + ")" +
                "\nException: " + type +
                "\nMessage: " + msg + cause +
                "\nSDK mode: Places API (New)" +
                "\nPackage: " + getPackageName() +
                "\nAPI key runtime: ..." + keyTail +
                "\nKey Cloud (...ThmOXqo): " + keyMatch +
                "\nSHA-1 runtime: " + sha1 +
                "\nJika status 9011 + key/SHA cocok: cek Billing dan restriction Places API (New) pada project key ini.";
        Log.e("TransivaPlaces", diag, e);
        return diag;
    }

    @SuppressWarnings("deprecation")
    protected String runtimeSigningSha1() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            if (pi.signatures == null || pi.signatures.length == 0) return "TIDAK TERBACA";
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(pi.signatures[0].toByteArray());
            StringBuilder out = new StringBuilder();
            for (byte b : digest) {
                if (out.length() > 0) out.append(':');
                out.append(String.format(java.util.Locale.US, "%02X", b & 0xFF));
            }
            return out.toString();
        } catch (Exception e) {
            Log.e("TransivaPlaces", "Gagal membaca SHA-1 runtime", e);
            return "ERROR: " + e.getClass().getSimpleName();
        }
    }

    protected String placesStatusName(int code) {
        switch (code) {
            case 0: return "SUCCESS";
            case 7: return "NETWORK_ERROR";
            case 8: return "INTERNAL_ERROR";
            case 10: return "DEVELOPER_ERROR";
            case 13: return "ERROR";
            case 14: return "INTERRUPTED";
            case 15: return "TIMEOUT";
            case 16: return "CANCELED";
            case 17: return "API_NOT_CONNECTED";
            default: return "STATUS_" + code;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PLACE_AUTOCOMPLETE) return;

        if (resultCode == RESULT_OK && data != null) {
            try {
                Place place = Autocomplete.getPlaceFromIntent(data);
                LatLng point = place.getLatLng();
                if (point == null || !validCoord(point.latitude, point.longitude)) {
                    toastDialog("Lokasi tempat belum tersedia. Coba pilih tempat lain atau gunakan peta.");
                    return;
                }

                deliveryLat = point.latitude;
                deliveryLng = point.longitude;
                deliveryAddress = firstNonEmpty(place.getName(), place.getAddress(), "Tujuan dipilih");
                googleMapInput.setText(deliveryAddress);
                deliveryText.setText("Pengantaran: " + deliveryAddress);
                deliveryBtn.setText("●  Tujuan\n" + shortAddress(deliveryAddress));
                mode = "delivery";

                if (mapView != null) {
                    mapView.setDelivery(deliveryLat, deliveryLng, deliveryAddress);
                    mapView.moveTo(deliveryLat, deliveryLng, 17f);
                }

                hideKeyboard();
                updateModeUI();
                requestPaymentQuote();
            } catch (Exception e) {
                toastDialog("Tempat tidak dapat dibuka. Coba lagi atau pilih langsung dari peta.");
            }
        } else if (resultCode == AutocompleteActivity.RESULT_ERROR && data != null) {
            try {
                Status status = Autocomplete.getStatusFromIntent(data);
                String message = status == null ? "" : firstNonEmpty(status.getStatusMessage(), "");
                if (!message.toLowerCase(Locale.ROOT).contains("cancel")) {
                    showPlacesErrorDialog();
                }
            } catch (Exception ignored) {
                showPlacesErrorDialog();
            }
        }
    }


    /** Pesan ramah pengguna saat layanan pencarian tempat belum dapat dipakai. */
    protected void showPlacesErrorDialog() {
        try {
            new TransivaAlertDialogBuilder(this)
                    .setTitle("Cari tujuan")
                    .setMessage("Pencarian tempat belum dapat digunakan. Coba lagi, atau pilih titik tujuan langsung dari peta.")
                    .setNegativeButton("Pilih di Peta", (d, w) -> {
                        mode = "delivery";
                        updateModeUI();
                        if (mapView != null && validCoord(centerLat, centerLng)) mapView.moveTo(centerLat, centerLng, 16f);
                    })
                    .setPositiveButton("Coba Lagi", (d, w) -> openDestinationAutocomplete())
                    .show();
        } catch (Exception ignored) {
            mode = "delivery";
            updateModeUI();
        }
    }

    /** Tombol ringkasan di atas mengaktifkan pemilihan ulang titik terkait. */
    protected void handlePointButtonClick(String requestedMode) {
        pointSelectionGeneration++;
        setLoading(false);
        destinationResolveGeneration++;
        locationQuoteReady = false; quoteLoading = false; ++quoteVersion; ++routePreviewVersion;
        followInitialGpsCamera = false;
        if ("pickup".equals(requestedMode)) { pickupGpsSelected = false; pickupGpsPinned = true; }
        mode = requestedMode;
        if ("pickup".equals(requestedMode)) {
            pickupLat = 0;
            pickupLng = 0;
            pickupAddress = "Lokasi jemput belum dipilih";
            pickupBtn.setText("●  Jemput\nCari lokasi / geser peta");
            ++pickupAddressRequest; pickupAddressResolving = false;
            if (mapView != null) mapView.clearPickup();
        } else {
            deliveryLat = 0;
            deliveryLng = 0;
            deliveryAddress = "Lokasi tujuan belum dipilih";
            deliveryBtn.setText("●  Tujuan\nCari lokasi / geser peta");
            ++deliveryAddressRequest;
            deliverySelectionLocked = false; pendingDeliveryTitle = ""; pendingDeliverySubtitle = "";
            if (mapView != null) mapView.clearDelivery();
        }
        updateModeUI();
    }




    /** Mengunci kandidat tujuan peta. Nama/koordinat tidak lagi mengikuti gesture setelah dikonfirmasi. */
    protected void confirmPendingDeliveryFromMap() {
        closePointSearch();
        final double lat = validCoord(pickLat, pickLng) ? pickLat : centerLat;
        final double lng = validCoord(pickLat, pickLng) ? pickLng : centerLng;
        if (!validCoord(lat, lng)) return;
        String title = firstNonEmpty(pendingDeliveryTitle, buildAdministrativeFallback(lat, lng), compactDisplayName(reverseAddress(lat, lng)), String.format(Locale.US, "%.5f, %.5f", lat, lng));
        String subtitle = firstNonEmpty(pendingDeliverySubtitle, "");
        deliveryLat = lat; deliveryLng = lng;
        deliveryAddress = subtitle.isEmpty() ? title : title + ", " + subtitle;
        deliverySelectionLocked = true;
        deliveryText.setText("Pengantaran: " + deliveryAddress);
        deliveryBtn.setText("●  Tujuan\n" + title + (subtitle.isEmpty() ? "" : "  •  " + subtitle));
        deliveryBtn.setScaleX(0.97f); deliveryBtn.setScaleY(0.97f); deliveryBtn.setAlpha(0.72f);
        deliveryBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260L).start();
        if (mapView != null) {
            mapView.setDelivery(deliveryLat, deliveryLng, deliveryAddress);
            mapView.showCenterPin(false);
        }
        setWizardProgress(1);
        updateModeUI();
        requestPaymentQuote();
    }

    protected void setPointFromCenter() {
        closePointSearch();
        double selectedLat = validCoord(pickLat, pickLng) ? pickLat : centerLat;
        double selectedLng = validCoord(pickLat, pickLng) ? pickLng : centerLng;
        if (!validCoord(selectedLat, selectedLng)) return;

        if ("pickup".equals(mode)) {
            pickupGpsSelected = false; pickupGpsPinned = true;
            updateGpsBadge(liveGpsBest);
            pickupLat = selectedLat;
            pickupLng = selectedLng;
            pickupAddress = "Mencari alamat jemput...";

            pickupText.setText("Penjemputan: " + pickupAddress);
            pickupBtn.setText("●  Jemput\nMencari alamat...");
            if (mapView != null) mapView.setPickup(pickupLat, pickupLng, pickupAddress);

            resolveAddressAsync(true, pickupLat, pickupLng);
            mode = "delivery";
        } else {
            deliveryLat = selectedLat;
            deliveryLng = selectedLng;
            deliveryAddress = "Mencari alamat pengantaran...";

            deliveryText.setText("Pengantaran: " + deliveryAddress);
            deliveryBtn.setText("●  Tujuan\nMencari alamat...");
            if (mapView != null) mapView.setDelivery(deliveryLat, deliveryLng, deliveryAddress);

            resolveAddressAsync(false, deliveryLat, deliveryLng);
        }

        updateModeUI();

    }

    private PassengerLocationController passengerLocation;
    private Location liveGpsBest;
    private boolean pickupGpsSelected = true;
    private boolean pickupGpsPinned = false;
    private long pickupAddressRequest = 0;
    private android.widget.TextView pickupGpsBadge;
    private boolean followInitialGpsCamera = true;
    private boolean screenLocationActive;
    private boolean explicitGpsRequest;
    private long lastGpsAppliedAt = 0L;
    private void updateGpsBadge(Location loc) {
        if (pickupGpsBadge == null) return;
        String quality = gpsQuality(loc);
        pickupGpsBadge.setText(!pickupGpsSelected ? "Jemput dipilih manual" :
                (loc == null ? "GPS sedang mencari…" :
                (Math.abs(System.currentTimeMillis() - loc.getTime()) > 15000 ? "Ketuk untuk perbarui GPS" :
                "GPS ±" + Math.round(loc.getAccuracy()) + " m")));
        pickupGpsBadge.setContentDescription(quality + ". Ketuk untuk detail lokasi.");
        boolean fresh = loc != null && Math.abs(System.currentTimeMillis()-loc.getTime()) <= 15000 && loc.hasAccuracy();
        int color = !fresh || loc.getAccuracy()>50 ? 0xFFCC4444 : loc.getAccuracy()>20 ? 0xFFAD7900 : 0xFF16865B;
        pickupGpsBadge.setTextColor(!pickupGpsSelected ? 0xFF64748B : color);
    }
    private String gpsQuality(Location loc) {
        if (loc == null) return "GPS mencari sinyal…";
        long age = Math.max(0, System.currentTimeMillis() - loc.getTime());
        if (age > 15000) return "🔴 GPS lama • perbarui lokasi";
        if (!loc.hasAccuracy()) return "🔴 Akurasi belum diketahui";
        int m = Math.round(loc.getAccuracy());
        return (m <= 20 ? "🟢 " : m <= 50 ? "🟡 " : "🔴 ") + "Akurasi GPS ±" + m + " m";
    }
    private void showGpsPickupDialog() {
        Location loc = liveGpsBest;
        new TransivaAlertDialogBuilder(this).setTitle("Lokasi jemput & GPS")
            .setMessage(gpsQuality(loc) + "\n\n" + (loc == null ? "Menunggu lokasi terkini." :
                "Ketelitian adalah perkiraan radius GPS, bukan jaminan titik tepat."))
            .setPositiveButton("⌖ Tetapkan GPS terbaru", (d,w) -> goToMyLocation())
            .setNegativeButton("Pilih manual", (d,w) -> { pickupGpsSelected=false; pickupGpsPinned=true; followInitialGpsCamera=false; mode="pickup"; openDestinationAutocomplete(); })
            .setNeutralButton("Tutup", null).show();
    }
    private void applyGpsPickup(Location loc, boolean force) {
        if (loc == null || !loc.hasAccuracy() || loc.getAccuracy() > 100f) return;
        if (Math.max(0L, (android.os.SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1000000L) > 15000L) return;
        if (!force && (!pickupGpsSelected || pickupGpsPinned || isRouteConfirmationLocked())) return;
        if (!force && liveGpsBest != null && lastGpsAppliedAt > 0 &&
                loc.getTime() < lastGpsAppliedAt) return;
        pickupLat=loc.getLatitude(); pickupLng=loc.getLongitude();
        lastGpsAppliedAt=loc.getTime();
        pickupAddress="Mencari alamat lokasi terbaru…";
        if (pickupBtn != null) pickupBtn.setText("●  Jemput\n"+shortAddress(pickupAddress));
        updateGpsBadge(loc);
        if (pickupText != null) pickupText.setText("Penjemputan: "+pickupAddress);
        if (mapView != null) { mapView.setPickup(pickupLat,pickupLng,pickupAddress); if (force) mapView.moveTo(pickupLat,pickupLng,17f); }
        resolveAddressAsync(true,pickupLat,pickupLng);
    }
    private boolean hasLocationPermission() {
        return checkSelfPermissionCompat(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermissionCompat(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLiveGps() {
        if (!screenLocationActive || !hasLocationPermission()) return;
        if (passengerLocation == null) passengerLocation = new PassengerLocationController(this,
                new PassengerLocationController.Callback() {
            @Override public void onLocation(Location loc, boolean current) {
                if (!screenLocationActive || destroyed || isFinishing()) return;
                liveGpsBest = loc;
                updateGpsBadge(loc);
                if (!pickupGpsSelected || pickupGpsPinned || isRouteConfirmationLocked()) return;
                // A recent cache can position the map immediately, but only a fresh,
                // accurate fix becomes the order's pickup coordinate.
                if (followInitialGpsCamera && mapView != null) {
                    mapView.centerOnInitialLocation(loc.getLatitude(), loc.getLongitude(), 17f);
                }
                if (!current) return;
                applyGpsPickup(loc, false);
                if (lastGpsAppliedAt != loc.getTime()) return;
                pickupGpsPinned = true;
                explicitGpsRequest = false;
                updateGpsBadge(loc);
                mode = "delivery";
                updateModeUI();
                loadOnlineDrivers();
                refreshSmartAvailability();
            }
            @Override public void onUnavailable(String message) {
                if (!screenLocationActive || destroyed || isFinishing()) return;
                if (pickupGpsBadge != null && !validCoord(pickupLat, pickupLng)) {
                    pickupGpsBadge.setText("Pilih jemput di peta / GPS");
                    pickupGpsBadge.setContentDescription(message);
                }
                if (explicitGpsRequest) { explicitGpsRequest = false; toastDialog(message); }
            }
        });
        passengerLocation.start();
    }

    private void stopLiveGps() {
        if (passengerLocation != null) passengerLocation.stop();
    }

    protected void goToMyLocation() {
        if (isRouteConfirmationLocked()) return;
        closePointSearch();
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        explicitGpsRequest = true;
        pickupGpsSelected = true;
        pickupGpsPinned = false;
        followInitialGpsCamera = true;
        lastGpsAppliedAt = 0L;
        stopLiveGps();
        startLiveGps();
    }

    /** Smart Favorite: tujuan dari server favorit, jemput otomatis dari GPS saat ini. */
    protected void applySmartFavoriteIntent() {
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra("smart_favorite", false)) return;
        double lat = intent.getDoubleExtra("smart_destination_lat", 0d);
        double lng = intent.getDoubleExtra("smart_destination_lng", 0d);
        if (!validCoord(lat, lng)) return;
        smartFavoriteIntent = true;
        deliveryLat = lat; deliveryLng = lng;
        deliveryAddress = firstNonEmpty(intent.getStringExtra("smart_destination_address"), intent.getStringExtra("smart_destination_label"), "Tujuan favorit");
        if (deliveryText != null) deliveryText.setText("Pengantaran: " + deliveryAddress);
        if (deliveryBtn != null) deliveryBtn.setText("●  Tujuan\n" + deliveryAddress);
        mode = "delivery";
        updateModeUI();
    }

    /** Applies a WhatsApp/Google Maps location received through SharedLocationActivity. */
    protected void applySharedLocationIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        String shared = intent.getStringExtra("shared_location_uri");
        String role = intent.getStringExtra("shared_location_role");
        if (shared == null || shared.trim().isEmpty()) return;

        final boolean asPickup = "pickup".equalsIgnoreCase(role);
        if (asPickup) { pickupGpsSelected = false; pickupGpsPinned = true; }
        followInitialGpsCamera = false;
        final String rawLocation = shared.trim();
        setLoading(true);

        featureRuntime.execute(() -> {
            String resolved = rawLocation;
            try {
                if (resolved.startsWith("geo:")) {
                    resolved = resolved.substring(4);
                }

                if (resolved.contains("maps.app.goo.gl") || resolved.contains("goo.gl/maps")) {
                    JSONObject response = postJson(
                            RESOLVE_MAPS_URL,
                            new JSONObject().put("url", resolved)
                    );
                    if (response.optBoolean("success", false)
                            && !response.optString("url", "").isEmpty()) {
                        resolved = response.optString("url");
                    }
                }

                final double[] coordinate = extractLatLng(resolved);
                featureRuntime.post(mainHandler, () -> {
                    setLoading(false);
                    if (coordinate == null || !validCoord(coordinate[0], coordinate[1])) {
                        toastDialog("Lokasi dari WhatsApp/Google Maps tidak bisa dibaca.");
                        return;
                    }

                    if (asPickup) {
                        pickupLat = coordinate[0];
                        pickupLng = coordinate[1];
                        pickupAddress = "Mencari alamat jemput...";
                        pickupText.setText("Penjemputan: " + pickupAddress);
                        pickupBtn.setText("●  Jemput\nMencari alamat...");
                        mode = "pickup";
                        if (mapView != null) {
                            mapView.setPickup(pickupLat, pickupLng, pickupAddress);
                            mapView.moveTo(pickupLat, pickupLng, 17f);
                        }
                        resolveAddressAsync(true, pickupLat, pickupLng);
                    } else {
                        deliveryLat = coordinate[0];
                        deliveryLng = coordinate[1];
                        deliveryAddress = "Mencari alamat pengantaran...";
                        deliveryText.setText("Pengantaran: " + deliveryAddress);
                        deliveryBtn.setText("●  Tujuan\\nMencari alamat...");
                        mode = "delivery";
                        if (mapView != null) {
                            mapView.setDelivery(deliveryLat, deliveryLng, deliveryAddress);
                            mapView.moveTo(deliveryLat, deliveryLng, 17f);
                        }
                        resolveAddressAsync(false, deliveryLat, deliveryLng);
                    }
                    updateModeUI();
                });
            } catch (Exception ignored) {
                featureRuntime.post(mainHandler, () -> {
                    setLoading(false);
                    toastDialog("Gagal membuka lokasi yang dibagikan.");
                });
            }
        }, () -> { Exception ignored = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis"); 
                featureRuntime.post(mainHandler, () -> {
                    setLoading(false);
                    toastDialog("Gagal membuka lokasi yang dibagikan.");
                });
             });
    }

    protected void useGoogleMapLink() {
        final String link = TransportMapsInput.findLink(googleMapInput.getText().toString());
        final String selectedMode = mode;
        final long selectionGeneration = ++pointSelectionGeneration;
        if (link.length() == 0) {
            toastDialog("Tempel link Google Maps jemput atau tujuan terlebih dahulu.");
            return;
        }

        hideKeyboard();
        setLoading(true);

        featureRuntime.execute(() -> {
            String finalLink = link;

            try {
                if (link.contains("maps.app.goo.gl") || link.contains("goo.gl/maps")) {
                    JSONObject res = postJson(RESOLVE_MAPS_URL, new JSONObject().put("url", link));
                    if (res.optBoolean("success", false) && res.optString("url", "").length() > 0) {
                        finalLink = res.optString("url");
                    }
                }

                double[] c = extractLatLng(finalLink);

                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || selectionGeneration != pointSelectionGeneration || !selectedMode.equals(mode)) return;
                    setLoading(false);

                    if (c == null || !validCoord(c[0], c[1])) {
                        toastDialog("Link Google Maps tidak bisa dibaca.");
                        return;
                    }

                    if ("pickup".equals(selectedMode)) {
                        pickupGpsSelected = false; pickupGpsPinned = true;
                        pickupLat=c[0]; pickupLng=c[1]; pickupAddress="Mencari alamat jemput...";
                        pickupText.setText("Penjemputan: "+pickupAddress); pickupBtn.setText("●  Lokasi Jemput\nMencari alamat...");
                        if(mapView!=null){mapView.setPickup(pickupLat,pickupLng,pickupAddress);mapView.moveTo(pickupLat,pickupLng,17f);}
                        resolveAddressAsync(true,pickupLat,pickupLng);
                    } else {
                        deliverySelectionLocked = true;
                        deliveryLat=c[0]; deliveryLng=c[1]; deliveryAddress="Mencari alamat pengantaran...";
                        deliveryText.setText("Pengantaran: "+deliveryAddress); deliveryBtn.setText("●  Mau ke mana?\nMencari alamat...");
                        if(mapView!=null){mapView.setDelivery(deliveryLat,deliveryLng,deliveryAddress);mapView.moveTo(deliveryLat,deliveryLng,17f);}
                        resolveAddressAsync(false,deliveryLat,deliveryLng);
                    }
                    googleMapInput.setText("");
                    closePointSearch();
                    updateModeUI();
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || selectionGeneration != pointSelectionGeneration || !selectedMode.equals(mode)) return;
                    setLoading(false);
                    toastDialog("Gagal membaca link Google Maps.");
                });
            }
        }, () -> { Exception e = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis"); 
                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || selectionGeneration != pointSelectionGeneration || !selectedMode.equals(mode)) return;
                    setLoading(false);
                    toastDialog("Gagal membaca link Google Maps.");
                });
             });
    }


    protected void useGoogleMapLinkForMode() {
        useGoogleMapLink();
    }

    protected double[] extractLatLng(String link) {
        return TransportMapsInput.coordinates(link);
    }


    protected void resolveAddressAsync(boolean isPickup, double lat, double lng) {
        final long requestId = isPickup ? ++pickupAddressRequest : ++deliveryAddressRequest;
        if(isPickup) { pickupAddressResolving=true; resolvingPickupLat=lat; resolvingPickupLng=lng; }
        locationQuoteReady=false; ++quoteVersion; updateWizardState();
        featureRuntime.execute(() -> {
            String road = reverseAddress(lat, lng);
            String localMerchant = findNearestPlaceName(lat, lng);
            featureRuntime.post(mainHandler, () -> resolveNearestGooglePlace(lat, lng, (googleName) -> {
                if (destroyed || (isPickup && (requestId != pickupAddressRequest ||
                    Math.abs(lat-pickupLat)>0.000001 || Math.abs(lng-pickupLng)>0.000001))) return;
                if(!isPickup && (requestId!=deliveryAddressRequest || Math.abs(lat-deliveryLat)>0.000001 || Math.abs(lng-deliveryLng)>0.000001)) return;
                if(isPickup) pickupAddressResolving=false;
                String nearName = firstNonEmpty(googleName, localMerchant, "");
                String address;
                if (!nearName.isEmpty() && !road.isEmpty()) address = "Dekat " + nearName + ", " + road;
                else if (!nearName.isEmpty()) address = "Dekat " + nearName;
                else if (!road.isEmpty()) address = road;
                else address = String.format(Locale.US, "%.6f, %.6f", lat, lng);
                applyResolvedAddress(isPickup, address);
            }));
        }, () -> featureRuntime.post(mainHandler, () -> { setLoading(false);toastDialog("Sistem sedang sibuk. Coba lagi."); }));
    }

    protected interface NearbyPlaceCallback { void onResult(String name); }

    /** Cari landmark Google terdekat agar titik hasil geser peta mudah dipahami driver. */
    protected void resolveNearestGooglePlace(double lat, double lng, NearbyPlaceCallback callback) {
        try {
            if (!Places.isInitialized() || System.currentTimeMillis() < placesCooldownUntilMs) { callback.onResult(""); return; }
            final String cacheKey=String.format(Locale.US,"%.4f,%.4f",lat,lng);
            if(nearbyLandmarkCache.containsKey(cacheKey)){ callback.onResult(nearbyLandmarkCache.get(cacheKey)); return; }
            PlacesClient client = Places.createClient(this);
            java.util.List<Place.Field> fields = java.util.Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG);
            SearchNearbyRequest request = SearchNearbyRequest.builder(CircularBounds.newInstance(new LatLng(lat, lng), 250.0), fields)
                    .setMaxResultCount(10).build();
            client.searchNearby(request).addOnSuccessListener(response -> {
                String bestName = ""; double best = 121.0;
                for (Place place : response.getPlaces()) {
                    LatLng point = place.getLatLng(); String name = cleanLandmarkName(place.getName());
                    if (point == null || name.isEmpty()) continue;
                    double d = distanceMeter(lat, lng, point.latitude, point.longitude);
                    // A POI farther than ~120 m is often misleading for a driver's pickup/dropoff label.
                    if (d <= 120.0 && d < best) { best = d; bestName = name; }
                }
                nearbyLandmarkCache.put(cacheKey,bestName);
                callback.onResult(bestName);
            }).addOnFailureListener(error -> {
                placesErrorDetail("NEARBY_LANDMARK", error);
                if(error instanceof ApiException) placesCooldownUntilMs=System.currentTimeMillis()+PLACES_ERROR_COOLDOWN_MS;
                callback.onResult("");
            });
        } catch (Exception ignored) { callback.onResult(""); }
    }

    /** Removes placeholder/generic names that must never be sent to drivers. */
    protected String cleanLandmarkName(String value) {
        String v = firstNonEmpty(value, "").trim();
        if (v.isEmpty()) return "";
        String n = v.toLowerCase(Locale.ROOT);
        if (n.equals("lokasi dipilih") || n.equals("selected location") || n.equals("pin dipilih") ||
                n.equals("lokasi") || n.equals("unnamed road") || n.equals("jalan tanpa nama")) return "";
        if (n.startsWith("dekat ")) v = v.substring(6).trim();
        return v;
    }

    /**
     * Driver-safe fallback when Google has no useful POI near the pin.
     * Prefer village/sub-locality + district/sub-admin-area + postal code instead of a generic placeholder.
     */
    protected String buildAdministrativeFallback(double lat, double lng) {
        try {
            if (!Geocoder.isPresent()) return "";
            Geocoder g = new Geocoder(this, new Locale("id", "ID"));
            List<Address> rows = g.getFromLocation(lat, lng, 1);
            if (rows == null || rows.isEmpty()) return "";
            Address a = rows.get(0);
            String village = firstNonEmpty(a.getSubLocality(), a.getLocality(), "");
            String district = firstNonEmpty(a.getSubAdminArea(), "");
            String postal = firstNonEmpty(a.getPostalCode(), "");
            StringBuilder out = new StringBuilder();
            if (!village.isEmpty()) out.append(village);
            if (!district.isEmpty() && !district.equalsIgnoreCase(village)) {
                if (out.length() > 0) out.append(", ");
                out.append(district);
            }
            if (!postal.isEmpty()) {
                if (out.length() > 0) out.append(" • ");
                out.append(postal);
            }
            return out.toString().trim();
        } catch (Exception ignored) { return ""; }
    }

    protected void applyResolvedAddress(boolean isPickup, String address) {
        if (destroyed) return;
        if (isPickup) {
            pickupAddress = address;
            pickupText.setText("Penjemputan: " + address);
            pickupBtn.setText("●  Jemput\n" + shortAddress(address));
            updateGpsBadge(liveGpsBest);
            if (mapView != null) mapView.setPickup(pickupLat, pickupLng, address);
        } else {
            deliveryAddress = address;
            deliveryText.setText("Pengantaran: " + address);
            deliveryBtn.setText("●  Tujuan\n" + shortAddress(address));
            if (mapView != null) mapView.setDelivery(deliveryLat, deliveryLng, address);
        }
        if (validCoordinate(pickupLat, pickupLng) && validCoordinate(deliveryLat, deliveryLng)) requestPaymentQuote();
    }



    protected String buildSmartAddress(double lat, double lng) {
        String nearName = findNearestPlaceName(lat, lng);
        String roadName = reverseAddress(lat, lng);

        if (nearName.length() > 0 && roadName.length() > 0) {
            return "Dekat " + nearName + ", " + roadName;
        }

        if (nearName.length() > 0) {
            return "Dekat " + nearName;
        }

        if (roadName.length() > 0) {
            return roadName;
        }

        return String.format(Locale.US, "%.6f, %.6f", lat, lng);
    }

    protected String reverseAddress(double lat, double lng) {
        return geocodingRepository.reverse(lat, lng);
    }


    protected String compactDisplayName(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() == 0) return "";

        String[] parts = v.split(",");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length && i < 3; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(part);
        }

        return sb.length() > 0 ? sb.toString() : v;
    }

    protected String findNearestPlaceName(double lat, double lng) {
        String food = findNearestFromUrl(GET_BUSINESSES_URL, "businesses", lat, lng, 150);
        if (food.length() > 0) return food;

        String laundry = findNearestFromUrl(GET_LAUNDRIES_URL, "laundries", lat, lng, 150);
        if (laundry.length() > 0) return laundry;

        return "";
    }

    protected String findNearestFromUrl(String urlText, String arrayKey, double lat, double lng, double maxMeter) {
        try {
            JSONObject res = getJson(urlText + "?v=" + System.currentTimeMillis());

            if (!res.optBoolean("success", false)) return "";

            JSONArray arr = res.optJSONArray(arrayKey);
            if (arr == null) return "";

            String bestName = "";
            double bestDistance = maxMeter;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;

                double itemLat = item.optDouble("latitude", 0);
                double itemLng = item.optDouble("longitude", 0);

                if (!validCoord(itemLat, itemLng)) continue;

                double d = distanceMeter(lat, lng, itemLat, itemLng);

                if (d <= bestDistance) {
                    bestDistance = d;
                    bestName = firstNonEmpty(
                            item.optString("name", ""),
                            item.optString("business_name", ""),
                            item.optString("title", "")
                    );
                }
            }

            return bestName;
        } catch (Exception ignored) {
            return "";
        }
    }

    protected void loadMapPlaces() {
        featureRuntime.execute(() -> {
            try {
                JSONObject food = getJson(GET_BUSINESSES_URL + "?v=" + System.currentTimeMillis());
                JSONObject laundry = getJson(GET_LAUNDRIES_URL + "?v=" + System.currentTimeMillis());

                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || !mapReady) return;

                    if (mapView != null) mapView.clearPlaces();
                    drawPlaces(food.optJSONArray("businesses"), "TransFood");
                    drawPlaces(laundry.optJSONArray("laundries"), "TransLaundry");
                });
            } catch (Exception ignored) {}
        }, () -> { Exception ignored = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis");  });
    }

    protected void drawPlaces(JSONArray arr, String type) {
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;

            double lat = item.optDouble("latitude", 0);
            double lng = item.optDouble("longitude", 0);

            if (!validCoord(lat, lng)) continue;

            String name = firstNonEmpty(
                    item.optString("name", ""),
                    item.optString("business_name", ""),
                    type
            );

            String address = firstNonEmpty(
                    item.optString("address", ""),
                    item.optString("category", ""),
                    ""
            );

            if (mapView != null) mapView.addPlace(lat, lng, name, type, address);
        }
    }

    protected void loadOnlineDrivers() {
        if (destroyed) return;

        featureRuntime.execute(() -> {
            try {
                JSONObject res = getJson(GET_ONLINE_DRIVERS_URL + "?type=" + driverType() + "&v=" + System.currentTimeMillis());
                JSONArray arr = res.optJSONArray("drivers");

                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || !mapReady) return;

                    if (mapView != null) mapView.clearOnlineDrivers();

                    int onlineCount = arr == null ? 0 : arr.length();
                    int availableCount = 0;
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject driver = arr.optJSONObject(i);
                            if (driver != null && !isDriverBusy(driver)) availableCount++;
                        }
                    }
                    boolean noDriversOnline = onlineCount == 0;
                    boolean allDriversBusy = onlineCount > 0 && availableCount == 0;
                    if (driverAvailabilityText != null) {
                        driverAvailabilityText.setVisibility((noDriversOnline || allDriversBusy) ? View.VISIBLE : View.GONE);
                        if (noDriversOnline) {
                            driverAvailabilityText.setText("Semua driver menjalankan tugas, orderan tetap kami terima");
                            driverAvailabilityText.setTextColor(Color.parseColor("#B45309"));
                            driverAvailabilityText.setBackground(roundStroke("#FFF7ED", "#FED7AA", dp(10), 1));
                        } else if (allDriversBusy) {
                            driverAvailabilityText.setText("Semua driver menjalankan tugas, orderan tetap kami terima");
                            driverAvailabilityText.setTextColor(Color.parseColor("#1D4ED8"));
                            driverAvailabilityText.setBackground(roundStroke("#EFF6FF", "#BFDBFE", dp(10), 1));
                        }
                    }

                    if (arr == null) return;

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject d = arr.optJSONObject(i);
                        if (d == null) continue;

                        double lat = d.optDouble("latitude", 0);
                        double lng = d.optDouble("longitude", 0);

                        if (!validCoord(lat, lng)) continue;

                        String name = firstNonEmpty(
                                d.optString("name", ""),
                                d.optString("username", ""),
                                "Driver"
                        );

                        if (mapView != null) mapView.addOnlineDriver(lat, lng, name, isCarService());
                    }
                });
            } catch (Exception ignored) {}
        }, () -> { Exception ignored = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis");  });

        mainHandler.postDelayed(() -> {
            if (!destroyed && mapReady && !isFinishing()) {
                loadOnlineDrivers();
            }
        }, 15000);
    }

    protected JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection conn = null;

        try {
            conn = CustomerApiClient.open(this, urlText);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            String requestToken = refreshAuthToken();
            if (!requestToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + requestToken);
                conn.setRequestProperty("X-App-Scope", "customer");
            }
            conn.setRequestProperty("X-Device-UUID", DeviceIdentityManager.getInstallationUuid(this));

            String body = readStream(conn.getInputStream()).trim();
            return body.length() == 0 ? new JSONObject() : new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    protected double distanceMeter(double lat1, double lng1, double lat2, double lng2) {
        return CustomerGeoMath.distanceMeters(lat1, lng1, lat2, lng2);
    }

    protected String refreshAuthToken() {
        try {
            String latest = new SessionManager(this).getToken();
            if (latest != null && !latest.trim().isEmpty()) {
                authToken = latest.trim();
            }
        } catch (Exception ignored) {}
        return authToken == null ? "" : authToken.trim();
    }

    protected boolean ensureAuthTokenForOrder() {
        if (!refreshAuthToken().isEmpty()) return true;
        toastDialog("Sesi login tidak memiliki token autentikasi. Silakan login ulang sekali untuk memperbarui sesi.");
        return false;
    }

    protected void createOrder() {
        if (ordering) return;
        if(!locationReady() || !locationQuoteReady) { updateWizardState(); return; }

        if (userId <= 0) {
            readUser();
        }

        if (userId <= 0) {
            toastDialog("Akun belum terbaca. Silakan masuk lagi.");
            return;
        }

        if (!ensureAuthTokenForOrder()) {
            return;
        }

        if (!validCoord(pickupLat, pickupLng)) {
            toastDialog("Pilih lokasi jemput terlebih dahulu.");
            return;
        }

        if (!validCoord(deliveryLat, deliveryLng)) {
            toastDialog("Pilih lokasi pengantaran terlebih dahulu.");
            return;
        }

        if (noteInput == null || noteInput.getText().toString().trim().isEmpty()) {
            orderAfterNote=true;
            showNoteDialog();
            return;
        }

        final String submittedDriverNote=noteInput.getText().toString().trim();
        ordering = true;
        setLoading(true);
        orderBtn.setEnabled(false);
        orderBtn.setText("Mencari driver...");

        featureRuntime.execute(() -> {
            try {
                String orderId = "ORD-" + System.currentTimeMillis();

                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat);
                pickup.put("longitude", pickupLng);
                pickup.put("address", firstNonEmpty(pickupAddress, "Lokasi Jemput"));

                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);
                delivery.put("address", firstNonEmpty(deliveryAddress, "Lokasi Pengantaran"));

                JSONObject userLocation = new JSONObject();
                userLocation.put("latitude", pickupLat);
                userLocation.put("longitude", pickupLng);

                JSONObject payload = new JSONObject();
                payload.put("id", orderId);
                payload.put("user_id", userId);
                payload.put("username", username);
                payload.put("customer", username);
                payload.put("order_type", serviceName());
                payload.put("driver_type", driverType());
                payload.put("service_type", serviceName());
                payload.put("service_name", serviceName());
                payload.put("price_mode", priceMode);
                payload.put("scheduled_at", scheduledAt);
                payload.put("family_member_id", familyMemberId);
                payload.put("price_guarantee", true);
                payload.put("pickup", pickup);
                payload.put("delivery", delivery);
                payload.put("pickup_address", firstNonEmpty(pickupAddress, "Lokasi Jemput"));
                payload.put("delivery_address", firstNonEmpty(deliveryAddress, "Lokasi Pengantaran"));
                payload.put("userLocation", userLocation);
                payload.put("note", submittedDriverNote);
                payload.put("payment_method", paymentMethod);
                payload.put("voucher_code", voucherInput == null ? "" : voucherInput.getText().toString().trim().toUpperCase(Locale.US));
                JSONObject eco = ecosystemFeatures.toJson();
                payload.put("ecosystem", eco);
                payload.put("waypoints", eco.optJSONArray("waypoints"));
                payload.put("trip_monitoring", ecosystemFeatures.tripMonitoring);
                payload.put("audio_protect", ecosystemFeatures.audioProtect);
                payload.put("group_size", ecosystemFeatures.groupSize);
                payload.put("split_fare_mode", ecosystemFeatures.splitFareMode);
                if (splitBillManager != null && !splitBillManager.sessionKey().isEmpty()) payload.put("split_session_key", splitBillManager.sessionKey());
                if (splitBillManager != null && splitBillManager.groupSize() > 1) payload.put("split_bill_required", true);

                JSONObject res = ReliableOrderClient.create(this, CREATE_ORDER_URL, payload, TIMEOUT_MS);
                featureRuntime.post(mainHandler, () -> handleOrderResult(res));
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> {
                    resetOrderButton();
                    toastDialog(ReliableOrderClient.failureMessage(e));
                });
            }
        }, () -> { Exception e = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis"); 
                featureRuntime.post(mainHandler, () -> {
                    resetOrderButton();
                    toastDialog(ReliableOrderClient.failureMessage(e));
                });
             });
    }

    protected void handleOrderResult(JSONObject res) {
        resetOrderButton();

        if (res == null || !res.optBoolean("success", false)) {
            String msg = res != null
                    ? res.optString("message", "Gagal membuat order " + orderNoun() + ".")
                    : "Gagal membuat order " + orderNoun() + ".";
            toastDialog(TransivaUserMessage.fromServer(msg));
            return;
        }

        String orderId = firstNonEmpty(res.optString("order_id", ""), res.optString("id", ""));
        syncEcosystemOrder(orderId);
        if (orderId.length() == 0) {
            toastDialog("Order berhasil, tetapi ID order tidak ditemukan.");
            return;
        }

        if ("scheduled".equalsIgnoreCase(res.optString("status", ""))) {
            toastDialog("Perjalanan dijadwalkan untuk " + res.optString("scheduled_at", scheduledAt) + ". Driver akan dicari sekitar 15 menit sebelum jadwal.");
            finish();
            return;
        }

        getSharedPreferences("transiva", MODE_PRIVATE).edit()
                .putString("active_order_id", orderId)
                .putString("active_order_type", serviceName())
                .putString("active_driver_type", driverType())
                .putString("active_service_name", serviceName())
                .putString("pickup_lat", String.valueOf(pickupLat))
                .putString("pickup_lng", String.valueOf(pickupLng))
                .putString("delivery_lat", String.valueOf(deliveryLat))
                .putString("delivery_lng", String.valueOf(deliveryLng))
                .putString("pickup_address", firstNonEmpty(pickupAddress, "Lokasi Jemput"))
                .putString("delivery_address", firstNonEmpty(deliveryAddress, "Lokasi Pengantaran"))
                .putString("active_order_price", res.optString("price", ""))
                .putString("active_order_payment_method", res.optString("payment_method", paymentMethod))
                .putBoolean("active_order_trip_monitoring", ecosystemFeatures.tripMonitoring)
                .putBoolean("active_order_audio_protect", ecosystemFeatures.audioProtect)
                .putInt("active_order_group_size", ecosystemFeatures.groupSize)
                .putString("active_order_split_fare_mode", ecosystemFeatures.splitFareMode)
                .putString("active_order_voucher", res.optString("voucher_code", ""))
                .apply();

        try {
            Intent i = new Intent(this, SearchDriverActivity.class);
            i.putExtra("order_id", orderId);
            i.putExtra("active_order_id", orderId);
            i.putExtra("active_driver_type", driverType());
            i.putExtra("driver_type", driverType());
            i.putExtra("active_order_type", serviceName());
            i.putExtra("pickup_lat", String.valueOf(pickupLat));
            i.putExtra("pickup_lng", String.valueOf(pickupLng));
            i.putExtra("delivery_lat", String.valueOf(deliveryLat));
            i.putExtra("delivery_lng", String.valueOf(deliveryLng));
            i.putExtra("pickup_address", firstNonEmpty(pickupAddress, "Lokasi Jemput"));
            i.putExtra("delivery_address", firstNonEmpty(deliveryAddress, "Lokasi Pengantaran"));
            startActivity(i);
            finish();
        } catch (Exception e) {
            toastDialog("Order " + serviceName() + " berhasil dibuat. ID: " + orderId);
        }
    }

    protected void syncEcosystemOrder(String orderId){
        if(orderId==null||orderId.trim().isEmpty())return;
        JSONObject b=new JSONObject(); try{b.put("action","sync");b.put("order_id",orderId);b.put("ecosystem",ecosystemFeatures.toJson());}catch(Exception ignored){}
        featureRuntime.execute(()->{try{RideSafetyApi.post(this,"ride_ecosystem_order.php",b);}catch(Exception ignored){}}, () -> { Exception ignored = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis");  });
    }




    private int routePreviewVersion = 0;
    private int quoteVersion = 0;
    private boolean locationQuoteReady;
    private boolean quoteLoading;
    private boolean pickupAddressResolving;
    private double resolvingPickupLat, resolvingPickupLng;
    private long deliveryAddressRequest;
    private boolean locationReady() {
        boolean busy=pickupAddressResolving && Math.abs(pickupLat-resolvingPickupLat)<0.000001 && Math.abs(pickupLng-resolvingPickupLng)<0.000001;
        return !busy && validCoord(pickupLat,pickupLng) && validCoord(deliveryLat,deliveryLng)
            && addressReady(pickupAddress) && addressReady(deliveryAddress);
    }
    private boolean addressReady(String address) {
        String value=address==null?"":address.trim().toLowerCase(Locale.ROOT);
        return !value.isEmpty() && !value.contains("mencari") && !value.contains("mengambil") && !value.contains("belum") && !value.contains("menunggu");
    }


    private void requestPaymentQuote() {
        final int quoteRequest = ++quoteVersion;
        locationQuoteReady=false;
        quoteLoading=false;
        updateWizardState();
        if(!locationReady()) return;
        quoteLoading=true;
        if (originalPriceText != null) originalPriceText.setVisibility(View.GONE);
        if (discountInfoText != null) discountInfoText.setVisibility(View.GONE);
        requestVisibleOsrmRoute();
        if (!validCoordinate(pickupLat, pickupLng)
                || !validCoordinate(deliveryLat, deliveryLng)) {
            if (paymentSummaryText != null) {
                paymentSummaryText.setText("Pilih titik penjemputan dan pengantaran");
            }
            if (distanceInfoText != null) distanceInfoText.setText(serviceName() + " • Jarak -");
            if (durationInfoText != null) durationInfoText.setText("Estimasi waktu -");
            if (finalPriceText != null) finalPriceText.setText("Rp -");
            if (originalPriceText != null) originalPriceText.setVisibility(View.GONE);
            if (discountInfoText != null) discountInfoText.setVisibility(View.GONE);
            return;
        }

        double fallbackDistance = 0;
        double previousLat = pickupLat, previousLng = pickupLng;
        for (int i = 0; i < ecosystemFeatures.waypoints.length(); i++) {
            JSONObject stop = ecosystemFeatures.waypoints.optJSONObject(i);
            if (stop == null) continue;
            double lat = stop.optDouble("latitude", Double.NaN);
            double lng = stop.optDouble("longitude", Double.NaN);
            if (!validCoord(lat, lng)) continue;
            fallbackDistance += distanceMeter(previousLat, previousLng, lat, lng);
            previousLat = lat; previousLng = lng;
        }
        fallbackDistance += distanceMeter(previousLat, previousLng, deliveryLat, deliveryLng);
        final double fallbackKm = Math.max(0.1, fallbackDistance * 1.25 / 1000.0);

        // Tampilkan estimasi jarak/waktu segera, lalu harga diisi dari database.
        final double fallbackMinutes = Math.max(
                1.0,
                (fallbackKm / 25.0) * 60.0
        );

        distanceInfoText.setText(String.format(new Locale("id", "ID"), serviceName() + " • %.1f km", fallbackKm));
        durationInfoText.setText(String.format(new Locale("id", "ID"), "± %.0f menit • estimasi perjalanan", fallbackMinutes));
        finalPriceText.setTextSize(19);
        finalPriceText.setContentDescription("Sedang menghitung tarif");
        finalPriceText.setText("Menghitung...");
        finalPriceText.setTextColor(Color.parseColor("#64748B"));
        paymentSummaryText.setText("Menghitung biaya perjalanan...");

        featureRuntime.execute(() -> {
            try {
                JSONObject payload = new JSONObject();

                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat);
                pickup.put("longitude", pickupLng);

                JSONObject delivery = new JSONObject();
                delivery.put("latitude", deliveryLat);
                delivery.put("longitude", deliveryLng);

                payload.put("pickup", pickup);
                payload.put("delivery", delivery);

                // Field datar ditambahkan untuk kompatibilitas endpoint lama/hosting cache.
                payload.put("pickup_lat", pickupLat);
                payload.put("pickup_lng", pickupLng);
                payload.put("delivery_lat", deliveryLat);
                payload.put("delivery_lng", deliveryLng);
                payload.put("service_type", isCarService() ? "Transcar" : "Transbike");
                payload.put("payment_method", paymentMethod);
                payload.put("price_mode", priceMode);
                payload.put("ecosystem", ecosystemFeatures.toJson());
                payload.put("waypoints", ecosystemFeatures.waypoints);
                payload.put(
                        "voucher_code",
                        voucherInput == null
                                ? ""
                                : voucherInput.getText().toString()
                                .trim()
                                .toUpperCase(Locale.US)
                );

                JSONObject res = postJson(PAYMENT_QUOTE_URL, payload);

                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || quoteRequest != quoteVersion) return;
                    quoteLoading=false;

                    if (!res.optBoolean("success", false)) {
                        String message = firstNonEmpty(
                                res.optString("message", ""),
                                "Tarif belum dapat dihitung"
                        );
                        paymentSummaryText.setText(TransivaUserMessage.fromServer(message));
                        showQuoteRetry();
                        finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                        return;
                    }

                    applyStopPricing(res.optJSONObject("stop_pricing"));
                    int original = jsonInt(
                            res,
                            "original_price",
                            jsonInt(res, "standard_price", 0)
                    );
                    royaltyOriginalFare=original;
                    int discount = jsonInt(res, "discount", 0);
                    int voucherDiscount = jsonInt(res, "voucher_discount", Math.max(0,discount-jsonInt(res,"hemat_discount",0)));
                    String appliedVoucher=res.optString("voucher_code", "");
                    if(voucherChoiceBtn!=null)voucherChoiceBtn.setText(appliedVoucher.isEmpty()?"🏷 Voucher":"🏷 Hemat Rp"+formatMoney(voucherDiscount));
                    int total = jsonInt(
                            res,
                            "price",
                            jsonInt(res, "final_price", original)
                    );
                    int balance = jsonInt(res, "balance", 0);
                    int hematRemaining = jsonInt(res, "hemat_remaining", -1);
                    int hematLimit = jsonInt(res, "hemat_limit", -1);
                    String hematTier = res.optString("hemat_tier", "");

                    double distanceKm = jsonDouble(
                            res,
                            "distance_km",
                            fallbackKm
                    );
                    double durationMinutes = jsonDouble(
                            res,
                            "duration_minutes",
                            jsonDouble(res, "estimated_minutes", fallbackMinutes)
                    );

                    distanceKm = Math.max(0.1, distanceKm);
                    durationMinutes = Math.max(1.0, durationMinutes);

                    distanceInfoText.setText(String.format(new Locale("id", "ID"), serviceName() + " • %.1f km", distanceKm));
                    durationInfoText.setText(String.format(new Locale("id", "ID"), "± %.0f menit • estimasi perjalanan", durationMinutes));

                    if (!CustomerFinancialRules.validRideQuote(original, total)) {
                        paymentSummaryText.setText("Biaya belum tersedia");
                        showQuoteRetry();
                        finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                        return;
                    }

                    locationQuoteReady=true;
                    lastQuotedFare = total;
                    finalPriceText.setContentDescription("Tarif Rp " + formatMoney(total));
                    finalPriceText.setText("Rp " + formatMoney(total));
                    updateWizardState();

                    if (discount > 0 && original > total) {
                        originalPriceText.setVisibility(View.VISIBLE);
                        originalPriceText.setText("Rp " + formatMoney(original));
                        originalPriceText.setPaintFlags(
                                originalPriceText.getPaintFlags()
                                        | Paint.STRIKE_THRU_TEXT_FLAG
                        );
                        finalPriceText.setTextColor(Color.parseColor("#16A34A"));
                        discountInfoText.setVisibility(View.VISIBLE);
                        discountInfoText.setText(
                                "Hemat Rp " + formatMoney(discount)
                        );
                    } else {
                        originalPriceText.setVisibility(View.GONE);
                        originalPriceText.setPaintFlags(
                                originalPriceText.getPaintFlags()
                                        & ~Paint.STRIKE_THRU_TEXT_FLAG
                        );
                        finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                        discountInfoText.setVisibility(View.GONE);
                    }

                    String label = paymentMethod.equals("balance")
                            ? "Transiva Pay"
                            : "Tunai";
                    paymentChoiceBtn.setText(paymentMethod.equals("balance")
                            ? "💳 Pembayaran: Transiva Pay"
                            : "💵 Pembayaran: Tunai");

                    String info = label;
                    if (paymentMethod.equals("balance")) {
                        info += " • Saldo Rp" + formatMoney(balance);
                    }
                    if (ecosystemFeatures.groupSize > 1) {
                        int share = (int)Math.ceil(total / (double)ecosystemFeatures.groupSize);
                        info += " • Group " + ecosystemFeatures.groupSize + " ≈ Rp" + formatMoney(share) + "/orang";
                    }
                    if (ecosystemFeatures.waypoints.length() > 0) {
                        info += " • " + ecosystemFeatures.waypoints.length() + " stop";
                    }

                    if(res.optInt("stop_fee")>0)info+="\nTermasuk biaya singgah Rp"+formatMoney(res.optInt("stop_fee"))+" • waktu tunggu dihitung terpisah";
                    fareText.setText("Biaya perjalanan: Rp" + formatMoney(total));
                    paymentSummaryText.setText(info);
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || quoteRequest != quoteVersion) return;
                    quoteLoading=false;
                    String quoteError=e instanceof TransivaHttpRepository.ServerResponseException
                            ? e.getMessage() : "Biaya belum bisa dimuat. Coba lagi.";
                    paymentSummaryText.setText(TransivaUserMessage.fromServer(quoteError));
                    showQuoteRetry();
                    finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                });
            }
        }, () -> { Exception e = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis"); 
                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || quoteRequest != quoteVersion) return;
                    quoteLoading=false;
                    paymentSummaryText.setText("Biaya belum bisa dimuat. Coba lagi.");
                    showQuoteRetry();
                    finalPriceText.setTextColor(Color.parseColor("#0B3A78"));
                });
             });
    }

    private void showQuoteRetry() {
        finalPriceText.setTextSize(13);
        finalPriceText.setText("Coba lagi ↻");
        finalPriceText.setContentDescription("Tarif belum tersedia. Ketuk untuk mencoba lagi");
        updateWizardState();
    }

    private int jsonInt(JSONObject json, String key, int fallback) {
        return CustomerJsonValues.intValue(json, key, fallback);
    }

    private double jsonDouble(JSONObject json, String key, double fallback) {
        return CustomerJsonValues.doubleValue(json, key, fallback);
    }

    private String formatMoney(int value) {
        return CustomerCommonFormatters.formatMoneyInt(value);
    }

    private boolean validCoordinate(double latitude, double longitude) {
        return CustomerGeoMath.valid(latitude, longitude);
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        return TransivaHttpRepository.postJson(this, urlText, payload, TIMEOUT_MS);
    }

    private String readStream(InputStream stream) throws Exception {
        return CustomerIo.readUtf8(stream);
    }

    private void updateModeUI() {
        boolean pickupMode = "pickup".equals(mode);

        boolean routeComplete = validCoord(pickupLat, pickupLng) && validCoord(deliveryLat, deliveryLng);
        if (bookingDetailsCard != null) bookingDetailsCard.setVisibility(routeComplete ? View.VISIBLE : View.GONE);
        if (pickupBtn != null) { pickupBtn.setEnabled(!routeComplete); pickupBtn.setClickable(!routeComplete); }
        if (deliveryBtn != null) { deliveryBtn.setEnabled(!routeComplete); deliveryBtn.setClickable(!routeComplete); }
        if (waypointQuickButton != null) waypointQuickButton.setVisibility(View.GONE);
        if (waypointBtn != null) waypointBtn.setVisibility(View.GONE);
        refreshWaypointHeader();
        modeText.setText(
                routeComplete
                        ? "Rute siap"
                        : (pickupMode ? "Cek titik jemput" : "Cari atau pilih tujuan")
        );
        if (tripRouteSummaryText != null && routeComplete) {
            String from = compactDisplayName(firstNonEmpty(pickupAddress, "Lokasi jemput"));
            String to = compactDisplayName(firstNonEmpty(deliveryAddress, "Tujuan"));
            StringBuilder itinerary = new StringBuilder(from);
            for (int i = 0; i < ecosystemFeatures.waypoints.length(); i++) {
                itinerary.append("  →  ").append(compactDisplayName(addressForWaypoint(i)));
            }
            itinerary.append("  →  ").append(to);
            tripRouteSummaryText.setText(itinerary.toString());
        }

        pickupBtn.setAlpha(pickupMode ? 1f : .80f);
        deliveryBtn.setAlpha(pickupMode ? .80f : 1f);
        if (mapView != null) {
            if (routeComplete) {
                mapView.showOrderAction(false, "");
            } else {
                mapView.setSelectionMode(pickupMode ? "pickup" : "delivery");
                mapView.showCenterPin(true);
            }
        }        updateWizardState();
    }

    private void requestVisibleOsrmRoute() {
        if (!mapReady || mapView == null || !validCoord(pickupLat, pickupLng)) return;
        final int previewRequest = ++routePreviewVersion;
        final JSONArray routeStops = new JSONArray();
        try {
            routeStops.put(new JSONArray().put(pickupLat).put(pickupLng));
            for (int i = 0; i < ecosystemFeatures.waypoints.length(); i++) {
                JSONObject wp = ecosystemFeatures.waypoints.optJSONObject(i);
                if (wp == null) continue;
                double lat = wp.optDouble("latitude", Double.NaN);
                double lng = wp.optDouble("longitude", Double.NaN);
                if (validCoord(lat, lng)) routeStops.put(new JSONArray().put(lat).put(lng));
            }
            if (validCoord(deliveryLat, deliveryLng)) routeStops.put(new JSONArray().put(deliveryLat).put(deliveryLng));
        } catch (Exception ignored) {}

        if (routeStops.length() < 2) return;
        featureRuntime.execute(() -> {
            try {
                JSONArray merged = new JSONArray();
                for (int i = 0; i < routeStops.length() - 1; i++) {
                    JSONArray a = routeStops.optJSONArray(i);
                    JSONArray b = routeStops.optJSONArray(i + 1);
                    if (a == null || b == null) continue;
                    StableRouteEngine.Result segment = StableRouteEngine.fetch(
                            a.optDouble(0), a.optDouble(1), b.optDouble(0), b.optDouble(1));
                    JSONArray pts = segment.latLngPoints;
                    for (int j = 0; pts != null && j < pts.length(); j++) {
                        if (i > 0 && j == 0) continue;
                        JSONArray pt = pts.optJSONArray(j);
                        if (pt != null) merged.put(pt);
                    }
                }
                featureRuntime.post(mainHandler, () -> {
                    if (!destroyed && previewRequest == routePreviewVersion && mapView != null) {
                        mapView.setWaypoints(ecosystemFeatures.waypoints);
                        mapView.drawRideRoute(merged);
                    }
                });
            } catch (Exception ignored) {}
        }, () -> { Exception ignored = new java.util.concurrent.RejectedExecutionException("Antrean penuh atau waktu tunggu habis");  });
    }
    private boolean validCoord(double lat, double lng) {
        return CustomerGeoMath.valid(lat, lng);
    }
    private boolean isDriverBusy(JSONObject driver) {
        if (driver == null) return false;
        Object raw = driver.opt("is_busy");
        if (raw == null || raw == JSONObject.NULL) raw = driver.opt("busy");
        if (raw == null || raw == JSONObject.NULL) raw = driver.opt("status");
        String value = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.US);
        return "1".equals(value) || "true".equals(value) || "busy".equals(value)
                || "sibuk".equals(value) || "on_trip".equals(value)
                || "in_progress".equals(value) || "driver_accepted".equals(value);
    }

    private void handleWizardPrimaryAction() {
        if(!locationReady()) { updateWizardState(); return; }
        if (!validCoord(deliveryLat, deliveryLng)) {
            mode = "delivery";
            updateModeUI();
            toastDialog("Tentukan tujuan perjalanan terlebih dahulu.");
            return;
        }
        if (!validCoord(pickupLat, pickupLng)) {
            mode = "pickup";
            updateModeUI();
            toastDialog("Periksa dan tentukan lokasi jemput.");
            return;
        }
        if (!locationQuoteReady || finalPriceText == null || finalPriceText.getText().toString().contains("-")) {
            requestPaymentQuote();
            toastDialog("Sedang menghitung harga perjalanan. Coba lagi sebentar.");
            return;
        }
        if (wizardStepText != null) {
            String green = "#16A34A";
            wizardStepText.setText(android.text.Html.fromHtml("<font color='"+green+"'><b>✓ Tujuan</b></font> &nbsp;›&nbsp; <font color='"+green+"'><b>✓ Harga</b></font> &nbsp;›&nbsp; <font color='"+green+"'><b>✓ Pesan</b></font>"));
            wizardStepText.setScaleX(.96f); wizardStepText.setScaleY(.96f);
            wizardStepText.animate().scaleX(1f).scaleY(1f).setDuration(180L).start();
        }
        createOrder();
    }

    private void setWizardProgress(int stage) {
        if (wizardStepText == null) return;
        String green = "#16A34A", blue = "#0B7CFF", gray = "#94A3B8";
        String a = stage > 0 ? "<font color='"+green+"'><b>✓ Tujuan</b></font>" : "<font color='"+blue+"'><b>● Tujuan</b></font>";
        String b = stage > 1 ? "<font color='"+green+"'><b>✓ Harga</b></font>" : (stage == 1 ? "<font color='"+blue+"'><b>● Harga</b></font>" : "<font color='"+gray+"'>2 Harga</font>");
        String c = stage >= 2 ? "<font color='"+blue+"'><b>● Pesan</b></font>" : "<font color='"+gray+"'>3 Pesan</font>";
        wizardStepText.setText(android.text.Html.fromHtml(a + " &nbsp;›&nbsp; " + b + " &nbsp;›&nbsp; " + c));
        wizardStepText.setAlpha(0.55f);
        wizardStepText.animate().alpha(1f).setDuration(220L).start();
    }

    private void updateWizardState() {
        if (orderBtn == null) return;
        if (ordering) {
            orderBtn.setVisibility(View.VISIBLE);
            orderBtn.setEnabled(false);
            return;
        }
        boolean ready = locationReady() && locationQuoteReady && lastQuotedFare >= 0;
        orderBtn.setVisibility(ready ? View.VISIBLE : View.GONE);
        orderBtn.setEnabled(ready);
        orderBtn.setAlpha(1f);
        if (wizardStepText != null) setWizardProgress(ready ? 2 : (locationReady() ? 1 : 0));
        if (ready) orderBtn.setText("PESAN " + serviceName().toUpperCase(Locale.US));
    }

    private void resetOrderButton() { ordering = false; setLoading(false); orderBtn.setEnabled(true); updateWizardState(); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private int checkSelfPermissionCompat(String p) { return android.os.Build.VERSION.SDK_INT >= 23 ? checkSelfPermission(p) : PackageManager.PERMISSION_GRANTED; }
    private void requestLocationIfNeeded() {
        if (!hasLocationPermission() && android.os.Build.VERSION.SDK_INT >= 23)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && hasLocationPermission()) startLiveGps();
    }

    private void hideKeyboard() {
        try {
            View focused = getCurrentFocus();
            View source = focused != null ? focused : (pointSearchInput != null ? pointSearchInput : googleMapInput);
            if (source != null) ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(source.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }
    private void toastDialog(String msg) { try { new TransivaAlertDialogBuilder(this).setTitle("Transiva").setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private String firstNonEmpty(String... v) {
        return CustomerCommonFormatters.firstBasic(v);
    }

    private Button compactPointButton(String title, String sub, String color) {
        Button b = new Button(this);
        b.setText(title + "\n" + sub);
        b.setAllCaps(false);
        b.setTextSize(11f);
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(10), dp(2), dp(8), dp(2));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(color));
        b.setBackground(roundStroke("#FFFFFF", "#E2E8F0", dp(14), 1));
        return b;
    }

    private String shortAddress(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() > 38) return v.substring(0, 38) + "...";
        return v;
    }

    private String shortCoord(double lat, double lng) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lng);
    }

    private String cleanError(String value) {
        String v = firstNonEmpty(value, "");
        if (v.length() == 0) return "";
        if (v.length() > 160) v = v.substring(0, 160);
        return v;
    }



    private int getDrawableId(String... names) {
        try {
            for (String name : names) {
                int id = getResources().getIdentifier(name, "drawable", getPackageName());
                if (id > 0) return id;
            }
        } catch (Exception ignored) {}
        return 0;
    }



    private Button rowButton(String value, String color) { Button b = smallButton(value, "#FFFFFF", color, "#E2E8F0"); b.setGravity(Gravity.CENTER_VERTICAL); b.setPadding(dp(12), 0, dp(12), 0); return b; }
    private Button smallButton(String value, String bg, String fg, String stroke) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT_BOLD); b.setTextColor(Color.parseColor(fg)); b.setBackground(roundStroke(bg, stroke, dp(16), 1)); return b; }
    private TextView text(String value, int sp, String color, boolean bold) { TextView tv = new TextView(this); tv.setText(value); tv.setTextSize(sp); tv.setTextColor(Color.parseColor(color)); if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD); return tv; }
    private GradientDrawable round(String color, int radius) { GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.parseColor(color)); gd.setCornerRadius(radius); return gd; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) { GradientDrawable gd = round(color, radius); gd.setStroke(dp(width), Color.parseColor(stroke)); return gd; }
    private int dp(int v) {
        return CustomerUiPrimitives.dp(this, v);
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStartMap();
    }

    @Override protected void onResume() {
        super.onResume();
        featureRuntime.onResume();
        screenLocationActive = true;
        updateGpsBadge(liveGpsBest);
        startLiveGps();
        if (mapView != null) mapView.onResumeMap();
    }

    @Override protected void onPause() {
        screenLocationActive = false;
        stopLiveGps();
        featureRuntime.onPause();
        if (mapView != null) mapView.onPauseMap();
        super.onPause();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemoryMap();
    }

    @Override protected void onStop() {
        if (mapView != null) mapView.onStopMap();
        super.onStop();
    }

    @Override protected void onDestroy() {
        featureRuntime.destroy();
        destroyed = true;
        screenLocationActive = false;
        stopLiveGps();
        closePointSearch();
        try {
            mainHandler.removeCallbacksAndMessages(null);
            if (mapView != null) {
                mapView.onDestroyMap();
                mapView = null;
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

}
