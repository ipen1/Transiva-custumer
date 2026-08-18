package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class TransPickupActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 20000;
    private static final int REQ_LOCATION = 4801;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout page;
    private LinearLayout root;
    private LinearLayout bottomSheet;
    private final Runnable restoreSheetRunnable = () -> setBottomSheetHidden(false);
    private ProgressBar progressBar;
    private TransivaGoogleMapView mapView;
    private TextView mapModeText;
    private Button pickupMapBtn, deliveryMapBtn, confirmPointBtn, gpsMapBtn;
    private boolean mapReady = false;
    private String mapSelectionMode = "pickup";
    private double mapCenterLat = -0.018137, mapCenterLng = 120.087380;

    private EditText pickupAddressInput, destinationInput, itemNameInput, itemValueInput, receiverNameInput, receiverPhoneInput, noteInput;
    private TextView pickupCoordText, deliveryCoordText, priceText, distanceText, otpText;
    private Button bikeBtn, smallBtn, mediumBtn, fragileYesBtn, fragileNoBtn, cashBtn, balanceBtn, calculateBtn, orderBtn;

    private String username = "User";
    private int userId = 0;
    private double pickupLat = 0, pickupLng = 0, deliveryLat = 0, deliveryLng = 0;
    private String vehicleType = "bike";
    private String packageSize = "small";
    private String itemCategory = "Paket";
    private boolean fragile = false;
    private String paymentMethod = "cash";
    private double deliveryFee = 0, distanceKm = 0;
    private String otpCode = "";
    private LocationManager locationManager;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
            if (android.os.Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Exception ignored) {}
        loadSession();
        buildBase();
        buildForm();
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
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        username = firstNonEmpty(sp.getString("username", ""), sp.getString("player_username", ""), "User");
        userId = sp.getInt("id", sp.getInt("user_id", 0));
    }

    private void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#071426"));

        mapView = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.PICKER);
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        mapView.initialize(null, new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat, double lng) {
                mapReady = true;
                mapCenterLat = lat;
                mapCenterLng = lng;
                mapView.setSelectionMode(mapSelectionMode);
                loadPickupLocation();
            }

            @Override public void onCenterChanged(double lat, double lng) {
                mapCenterLat = lat;
                mapCenterLng = lng;
                if (mapModeText != null) {
                    mapModeText.setText("pickup".equals(mapSelectionMode) ? "Geser pin lalu pilih penjemputan" : "Geser pin lalu pilih pengantaran");
                }
            }
        });
        mapView.setGestureListener(new TransivaGoogleMapView.GestureListener() {
            @Override public void onGestureStart() {
                mainHandler.removeCallbacks(restoreSheetRunnable);
                setBottomSheetHidden(true);
            }

            @Override public void onGestureEnd() {
                mainHandler.removeCallbacks(restoreSheetRunnable);
                mainHandler.postDelayed(restoreSheetRunnable, 180L);
            }
        });

        buildMapHeader();
        buildBottomSheet();

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(52), dp(52));
        p.gravity = Gravity.CENTER;
        page.addView(progressBar, p);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }


    private void buildMapHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(8), dp(5), dp(8), dp(5));
        header.setBackground(roundStroke("#F4FFFFFF", "#D7E6F8", dp(18), 1));

        FrameLayout.LayoutParams headerLp = new FrameLayout.LayoutParams(-1, -2);
        headerLp.gravity = Gravity.TOP;
        headerLp.setMargins(dp(8), dp(7), dp(8), 0);
        page.addView(header, headerLp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(30)));

        Button back = outlineButton("‹");
        back.setTextSize(28);
        back.setPadding(0, 0, 0, dp(3));
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(34), dp(30)));

        TextView title = text("TransSend", 18, "#0B3A78", true);
        title.setPadding(dp(9), 0, 0, 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        mapModeText = text("Pilih titik jemput", 11, "#64748B", false);
        mapModeText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(mapModeText, new LinearLayout.LayoutParams(0, -1, 1.25f));

        // Pemilihan rute dibuat sama seperti TransRide/TransCar.
        LinearLayout pointRow = new LinearLayout(this);
        pointRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pointRowLp = new LinearLayout.LayoutParams(-1, dp(52));
        pointRowLp.setMargins(0, dp(5), 0, 0);
        header.addView(pointRow, pointRowLp);

        pickupMapBtn = compactMapButton("●  Jemput\nBelum dipilih", "#16A34A");
        deliveryMapBtn = compactMapButton("●  Tujuan\nBelum dipilih", "#EF4444");
        pickupMapBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        deliveryMapBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pickupMapBtn.setPadding(dp(12), 0, dp(8), 0);
        deliveryMapBtn.setPadding(dp(12), 0, dp(8), 0);

        pointRow.addView(pickupMapBtn, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams deliveryLp = new LinearLayout.LayoutParams(0, -1, 1);
        deliveryLp.setMargins(dp(6), 0, 0, 0);
        pointRow.addView(deliveryMapBtn, deliveryLp);

        pickupMapBtn.setOnClickListener(v -> reopenPointSelection("pickup"));
        deliveryMapBtn.setOnClickListener(v -> reopenPointSelection("delivery"));

        // Klik tulisan/pin tengah langsung menetapkan titik, lalu otomatis lanjut ke pengantaran.
        mapView.setCenterActionListener(() -> {
            // Jangan memakai validLocation() untuk menentukan fungsi tombol.
            // Saat tombol sudah berubah menjadi mode order, langsung proses order.
            // Ini mencegah alert tujuan palsu walaupun tujuan sudah dipilih.
            if ("order".equals(mapSelectionMode)) createPickupOrder();
            else confirmMapPoint();
        });

        gpsMapBtn = outlineButton("⌖");
        gpsMapBtn.setTextSize(21);
        FrameLayout.LayoutParams gpsLp = new FrameLayout.LayoutParams(dp(46), dp(46));
        gpsLp.gravity = Gravity.END | Gravity.TOP;
        gpsLp.setMargins(0, dp(126), dp(12), 0);
        page.addView(gpsMapBtn, gpsLp);
        gpsMapBtn.setOnClickListener(v -> loadPickupLocation());

        selectMapMode("pickup");
    }

    private void buildBottomSheet() {
        LinearLayout sheet = new LinearLayout(this);
        bottomSheet = sheet;
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(10), dp(8), dp(10), dp(8));
        sheet.setBackground(roundStroke("#F7FAFF", "#C7DBF2", dp(24), 1));
        sheet.setElevation(dp(8));

        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1, dp(330));
        sheetLp.gravity = Gravity.BOTTOM;
        sheetLp.setMargins(dp(7), 0, dp(7), dp(6));
        page.addView(sheet, sheetLp);

        TextView handle = text("━", 24, "#94A3B8", true);
        handle.setGravity(Gravity.CENTER);
        sheet.addView(handle, new LinearLayout.LayoutParams(-1, dp(26)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(2), 0, dp(2), dp(8));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
    }

    private void setBottomSheetHidden(boolean hidden) {
        if (bottomSheet == null) return;
        float target = hidden ? bottomSheet.getHeight() - dp(34) : 0f;
        bottomSheet.animate().cancel();
        if (hidden) {
            bottomSheet.setTranslationY(target);
        } else {
            bottomSheet.animate().translationY(0f).setDuration(180L).start();
        }
    }

    private Button compactMapButton(String label, String accent) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor("#0B3A78"));
        b.setBackground(roundStroke("#FFFFFF", accent, dp(14), 1));
        return b;
    }

    private void selectMapMode(String mode) {
        mapSelectionMode = "delivery".equals(mode) ? "delivery" : "pickup";
        boolean pickupMode = "pickup".equals(mapSelectionMode);

        if (mapView != null) {
            mapView.setSelectionMode(mapSelectionMode);
            mapView.showCenterPin(true);
        }
        if (mapModeText != null) {
            mapModeText.setText(pickupMode ? "Geser pin untuk penjemputan" : "Geser pin untuk pengantaran");
        }
        if (pickupMapBtn != null) setChoice(pickupMapBtn, pickupMode);
        if (deliveryMapBtn != null) setChoice(deliveryMapBtn, !pickupMode);

        double lat = pickupMode ? pickupLat : deliveryLat;
        double lng = pickupMode ? pickupLng : deliveryLng;
        if (mapReady && validCoordinate(lat, lng)) {
            mapView.moveTo(lat, lng, 17f);
        }
    }

    private void reopenPointSelection(String requestedMode) {
        boolean pickupMode = !"delivery".equals(requestedMode);
        if (mapView != null) {
            mapView.showOrderAction(false, null);
            if (pickupMode) mapView.clearPickup();
            else mapView.clearDelivery();
            mapView.clearRoute();
        }

        if (pickupMode) {
            pickupLat = 0d;
            pickupLng = 0d;
            if (pickupMapBtn != null) pickupMapBtn.setText("●  Jemput\nBelum dipilih");
            if (pickupCoordText != null) pickupCoordText.setText("📍 Geser peta lalu klik Jemput di lokasi ini");
        } else {
            deliveryLat = 0d;
            deliveryLng = 0d;
            if (deliveryMapBtn != null) deliveryMapBtn.setText("●  Tujuan\nBelum dipilih");
            if (deliveryCoordText != null) deliveryCoordText.setText("📍 Geser peta lalu klik Antar ke lokasi ini");
        }

        deliveryFee = 0d;
        distanceKm = 0d;
        if (priceText != null) priceText.setText("Ongkir: hitung dulu");
        if (distanceText != null) distanceText.setText("Jarak: -");
        if (orderBtn != null) { orderBtn.setEnabled(false); orderBtn.setAlpha(0.55f); }
        selectMapMode(pickupMode ? "pickup" : "delivery");
    }

    private void confirmMapPoint() {
        if (!validCoordinate(mapCenterLat, mapCenterLng)) {
            showInfo("Peta", "Geser peta ke lokasi yang benar terlebih dahulu.");
            return;
        }

        if ("pickup".equals(mapSelectionMode)) {
            pickupLat = mapCenterLat;
            pickupLng = mapCenterLng;
            if (mapView != null) mapView.setPickup(pickupLat, pickupLng, "Lokasi Jemput");
            if (pickupCoordText != null) {
                pickupCoordText.setText("📍 " + formatCoordinate(pickupLat, pickupLng));
            }
            if (pickupMapBtn != null) pickupMapBtn.setText("●  Jemput\n" + shortCoordinate(pickupLat, pickupLng));
            reverseGeocode(true, pickupLat, pickupLng);
            selectMapMode("delivery");
        } else {
            deliveryLat = mapCenterLat;
            deliveryLng = mapCenterLng;
            if (mapView != null) mapView.setDelivery(deliveryLat, deliveryLng, "Lokasi Antar");
            if (deliveryCoordText != null) {
                deliveryCoordText.setText("📍 " + formatCoordinate(deliveryLat, deliveryLng));
            }
            if (deliveryMapBtn != null) deliveryMapBtn.setText("●  Tujuan\n" + shortCoordinate(deliveryLat, deliveryLng));
            reverseGeocode(false, deliveryLat, deliveryLng);
            if (validLocation()) {
                drawPickupRoute();
                calculateOngkir();
                mapSelectionMode = "order";
                if (mapView != null) mapView.showOrderAction(true, "PESAN SEKARANG");
                if (mapModeText != null) mapModeText.setText("Rute siap, tekan Pesan Sekarang");
            }
        }
        updateChoices();
    }

    private void reverseGeocode(boolean pickup, double lat, double lng) {
        new Thread(() -> {
            String address = "";
            try {
                Geocoder geo = new Geocoder(this, new Locale("id", "ID"));
                List<Address> list = geo.getFromLocation(lat, lng, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    address = firstNonEmpty(
                            a.getAddressLine(0),
                            a.getFeatureName(),
                            a.getLocality(),
                            formatCoordinate(lat, lng)
                    );
                }
            } catch (Exception ignored) {}

            final String finalAddress = firstNonEmpty(address, formatCoordinate(lat, lng));
            mainHandler.post(() -> {
                if (pickup && pickupAddressInput != null) pickupAddressInput.setText(finalAddress);
                if (!pickup && destinationInput != null) destinationInput.setText(finalAddress);
                String compact = finalAddress.length() > 24 ? finalAddress.substring(0, 24) + "…" : finalAddress;
                if (pickup && pickupMapBtn != null) pickupMapBtn.setText("●  Jemput\n" + compact);
                if (!pickup && deliveryMapBtn != null) deliveryMapBtn.setText("●  Tujuan\n" + compact);
            });
        }, "transpickup-reverse-geocode").start();
    }

    private void drawPickupRoute() {
        if (mapView == null || !validLocation()) return;
        new Thread(() -> {
            try {
                StableRouteEngine.Result route = StableRouteEngine.fetch(
                        pickupLat, pickupLng, deliveryLat, deliveryLng
                );
                mainHandler.post(() -> {
                    if (mapView != null) mapView.drawRideRoute(route.latLngPoints);
                });
            } catch (Exception ignored) {}
        }, "transpickup-route").start();
    }

    private String formatCoordinate(double lat, double lng) {
        return String.format(Locale.US, "%.6f, %.6f", lat, lng);
    }

    private String shortCoordinate(double lat, double lng) {
        return String.format(Locale.US, "%.4f, %.4f", lat, lng);
    }

    private boolean validCoordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat != 0d && lng != 0d
                && Math.abs(lat) <= 90d && Math.abs(lng) <= 180d;
    }

    private void buildForm() {
        vehicleType = "bike";
        if (!"medium".equals(packageSize)) packageSize = "small";
        root.removeAllViews();

        buildLocationCard();
        buildItemCard();
        buildOptionCard();
        buildPaymentCard();
        buildSummaryCard();
    }

    private void buildLocationCard() {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(text("Titik Jemput & Antar", 16, "#0B3A78", true));

        pickupAddressInput = edit("Alamat penjemputan");
        pickupAddressInput.setText("Lokasi saya saat ini");
        pickupAddressInput.setFocusable(false);
        pickupAddressInput.setClickable(true);
        pickupAddressInput.setOnClickListener(v -> reopenPointSelection("pickup"));
        addInner(card, pickupAddressInput, dp(8));

        pickupCoordText = text("📍 Geser peta dan tetapkan titik jemput", 11, "#64748B", false);
        pickupCoordText.setPadding(0, dp(5), 0, dp(8));
        card.addView(pickupCoordText);

        destinationInput = edit("Alamat pengantaran / nama tempat pengantaran");
        destinationInput.setSingleLine(true);
        destinationInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        addInner(card, destinationInput, dp(6));

        deliveryCoordText = text("📍 Geser peta dan tetapkan titik antar", 11, "#64748B", false);
        deliveryCoordText.setPadding(0, dp(5), 0, dp(8));
        card.addView(deliveryCoordText);

        destinationInput.setFocusable(false);
        destinationInput.setClickable(true);
        destinationInput.setOnClickListener(v -> reopenPointSelection("delivery"));
        destinationInput.setHint("Alamat pengantaran terisi otomatis dari pin peta");

        // Lokasi sudah ditampilkan pada kartu header peta. Card lama dipertahankan
        // hanya sebagai penyimpan field internal agar proses order tetap kompatibel,
        // tetapi tidak lagi ditampilkan kepada pengguna.
        card.setVisibility(View.GONE);
        addWithMargin(card, 0, 0, 0, 0);
    }

    private void buildItemCard() {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(text("Detail Barang", 15, "#0B3A78", true));
        itemNameInput = edit("Nama barang, contoh: Dokumen / paket baju");
        addInner(card, itemNameInput, dp(6));
        itemValueInput = edit("Nilai barang, contoh: 50000");
        itemValueInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        addInner(card, itemValueInput, dp(6));
        receiverNameInput = edit("Nama penerima");
        addInner(card, receiverNameInput, dp(6));
        receiverPhoneInput = edit("Nomor HP penerima");
        receiverPhoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        addInner(card, receiverPhoneInput, dp(6));
        noteInput = edit("Catatan untuk driver");
        noteInput.setSingleLine(true); noteInput.setMinLines(1);
        addInner(card, noteInput, dp(6));
        addWithMargin(card, 0, 0, 0, dp(8));
    }

    private void buildOptionCard() {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Ukuran Paket", 15, "#0B3A78", true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView motorOnly = text("🏍 Motor saja", 12, "#0B7CFF", true);
        motorOnly.setGravity(Gravity.CENTER);
        motorOnly.setBackground(roundStroke("#EAF4FF", "#B9DAFF", dp(14), 1));
        titleRow.addView(motorOnly, new LinearLayout.LayoutParams(dp(112), dp(32)));
        card.addView(titleRow);

        LinearLayout sizeRow = row();
        smallBtn = choiceButton("📄 Kecil  < 2 kg", true);
        mediumBtn = choiceButton("📦 Sedang  2–5 kg", false);
        addChoice(sizeRow, smallBtn, mediumBtn);
        LinearLayout.LayoutParams sizeLp = new LinearLayout.LayoutParams(-1, -2);
        sizeLp.setMargins(0, dp(6), 0, 0);
        card.addView(sizeRow, sizeLp);
        smallBtn.setOnClickListener(v -> { packageSize = "small"; updateChoices(); });
        mediumBtn.setOnClickListener(v -> { packageSize = "medium"; updateChoices(); });

        LinearLayout fragileRow = row();
        fragileRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView fragileTitle = text("Mudah pecah?", 13, "#0B3A78", true);
        fragileRow.addView(fragileTitle, new LinearLayout.LayoutParams(0, dp(42), 0.7f));
        fragileNoBtn = choiceButton("Tidak", true);
        fragileYesBtn = choiceButton("Ya", false);
        fragileRow.addView(fragileNoBtn, new LinearLayout.LayoutParams(0, dp(42), 0.65f));
        LinearLayout.LayoutParams yesLp = new LinearLayout.LayoutParams(0, dp(42), 0.65f);
        yesLp.setMargins(dp(6), 0, 0, 0);
        fragileRow.addView(fragileYesBtn, yesLp);
        LinearLayout.LayoutParams fragileLp = new LinearLayout.LayoutParams(-1, -2);
        fragileLp.setMargins(0, dp(7), 0, 0);
        card.addView(fragileRow, fragileLp);
        fragileNoBtn.setOnClickListener(v -> { fragile = false; updateChoices(); });
        fragileYesBtn.setOnClickListener(v -> { fragile = true; updateChoices(); });

        // Kontrak TransSend saat ini motor-only.
        bikeBtn = null;
        vehicleType = "bike";
        addWithMargin(card, 0, 0, 0, dp(8));
    }

    private void buildPaymentCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Pembayaran", 17, "#0B3A78", true));
        LinearLayout row = row();
        cashBtn = choiceButton("Tunai", true);
        balanceBtn = choiceButton("Saldo", false);
        addChoice(row, cashBtn, balanceBtn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(10), 0, 0);
        card.addView(row, lp);
        cashBtn.setOnClickListener(v -> { paymentMethod = "cash"; updateChoices(); });
        balanceBtn.setOnClickListener(v -> { paymentMethod = "balance"; updateChoices(); });
        addWithMargin(card, 0, 0, 0, dp(8));
    }

    private void buildSummaryCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Ringkasan", 17, "#0B3A78", true));
        distanceText = text("Jarak: -", 13, "#64748B", false); distanceText.setPadding(0, dp(8), 0, 0); card.addView(distanceText);
        priceText = text("Ongkir: hitung dulu", 22, "#0B3A78", true); priceText.setPadding(0, dp(4), 0, dp(10)); card.addView(priceText);
        otpCode = "";
        LinearLayout otpRow = row();
        otpRow.setGravity(Gravity.CENTER_VERTICAL);
        otpText = text("OTP penerima dibuat aman oleh server setelah order", 12, "#0B7CFF", true);
        otpRow.addView(otpText, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button copyOtpBtn = outlineButton("Salin");
        copyOtpBtn.setOnClickListener(v -> copyOtp());
        otpRow.addView(copyOtpBtn, new LinearLayout.LayoutParams(dp(82), dp(40)));
        card.addView(otpRow);
        calculateBtn = outlineButton("Hitung Ongkir");
        orderBtn = primaryButton("Cari Driver");
        orderBtn.setEnabled(false); orderBtn.setAlpha(0.55f);
        card.addView(calculateBtn, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, dp(52)); olp.setMargins(0, dp(10), 0, 0);
        orderBtn.setVisibility(View.GONE);
        calculateBtn.setOnClickListener(v -> calculateOngkir());
        orderBtn.setOnClickListener(v -> createPickupOrder());
        addWithMargin(card, 0, 0, 0, dp(20));
    }

    private void updateChoices() {
        setChoice(smallBtn, "small".equals(packageSize)); setChoice(mediumBtn, "medium".equals(packageSize));
        setChoice(bikeBtn, true);
        setChoice(fragileNoBtn, !fragile); setChoice(fragileYesBtn, fragile);
        setChoice(cashBtn, "cash".equals(paymentMethod)); setChoice(balanceBtn, "balance".equals(paymentMethod));
        deliveryFee = 0; distanceKm = 0;
        if (priceText != null) priceText.setText("Ongkir: hitung dulu");
        if (distanceText != null) distanceText.setText("Jarak: -");
        if (orderBtn != null) { orderBtn.setEnabled(false); orderBtn.setAlpha(0.55f); }
    }

    private void setChoice(Button b, boolean active) {
        if (b == null) return;
        b.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#0B3A78"));
        b.setBackground(roundStroke(active ? "#0B7CFF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(18), 1));
    }

    private void loadPickupLocation() {
        if (checkSelfPermissionSafe(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermissionSafe(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;
            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) { showInfo("GPS", "Aktifkan GPS agar titik jemput akurat."); startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return; }
            Location best = null;
            try { best = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (best == null) try { best = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            if (best != null) setPickupLocation(best);
            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) { setPickupLocation(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());
        } catch (Exception e) { showInfo("Lokasi", "Gagal mengambil lokasi jemput."); }
    }

    private void setPickupLocation(Location loc) {
        pickupLat = loc.getLatitude();
        pickupLng = loc.getLongitude();
        mapCenterLat = pickupLat;
        mapCenterLng = pickupLng;

        if (pickupCoordText != null) {
            pickupCoordText.setText("📍 " + formatCoordinate(pickupLat, pickupLng));
        }
        if (mapView != null && mapReady) {
            mapView.setPickup(pickupLat, pickupLng, "Lokasi Jemput");
            mapView.moveTo(pickupLat, pickupLng, 17f);
        }
        try {
            new SessionManager(this).saveLastLocation(
                    String.valueOf(pickupLat),
                    String.valueOf(pickupLng)
            );
        } catch (Exception ignored) {}

        reverseGeocode(true, pickupLat, pickupLng);
        updateChoices();
    }

    private void geocodeDestination() {
        String q = destinationInput == null ? "" : destinationInput.getText().toString().trim();
        if (q.length() < 4) {
            showInfo("Pengantaran", "Masukkan alamat pengantaran lebih lengkap.");
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                Geocoder geo = new Geocoder(this, new Locale("id", "ID"));
                List<Address> list = geo.getFromLocationName(q, 1);
                if (list == null || list.isEmpty()) throw new Exception("Lokasi pengantaran tidak ditemukan.");

                Address a = list.get(0);
                deliveryLat = a.getLatitude();
                deliveryLng = a.getLongitude();
                mapCenterLat = deliveryLat;
                mapCenterLng = deliveryLng;

                String label = "📍 " + formatCoordinate(deliveryLat, deliveryLng);
                mainHandler.post(() -> {
                    setLoading(false);
                    deliveryCoordText.setText(label);
                    selectMapMode("delivery");
                    if (mapView != null) {
                        mapView.setDelivery(deliveryLat, deliveryLng, q);
                        mapView.moveTo(deliveryLat, deliveryLng, 17f);
                    }
                    updateChoices();
                    if (validLocation()) {
                        drawPickupRoute();
                        calculateOngkir();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    showInfo("Pengantaran", "Alamat pengantaran belum ditemukan. Coba tulis lebih lengkap.");
                });
            }
        }, "transpickup-geocode").start();
    }

    private void calculateOngkir() {
        if (!validLocation()) return;
        setLoading(true);
        new Thread(() -> {
            try {
                String url = BASE_URL + "server/calculatePickupOngkir.php" +
                        "?pickup_lat=" + Uri.encode(String.valueOf(pickupLat)) +
                        "&pickup_lng=" + Uri.encode(String.valueOf(pickupLng)) +
                        "&delivery_lat=" + Uri.encode(String.valueOf(deliveryLat)) +
                        "&delivery_lng=" + Uri.encode(String.valueOf(deliveryLng)) +
                        "&vehicle_type=" + Uri.encode(vehicleType) +
                        "&package_size=" + Uri.encode(packageSize) +
                        "&fragile=" + (fragile ? "1" : "0") +
                        "&_=" + System.currentTimeMillis();
                JSONObject res = getJson(url);
                if (!res.optBoolean("success", false)) throw new Exception(firstNonEmpty(res.optString("message"), "Gagal menghitung ongkir"));
                deliveryFee = res.optDouble("price", 0);
                distanceKm = res.optDouble("distance_km", 0);
                mainHandler.post(() -> {
                    setLoading(false);
                    distanceText.setText("Jarak: " + String.format(Locale.US, "%.2f", distanceKm) + " km");
                    priceText.setText("Ongkir: " + rupiah(deliveryFee));
                    orderBtn.setEnabled(true); orderBtn.setAlpha(1f);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showInfo("Ongkir", e.getMessage()); });
            }
        }).start();
    }

    private void createPickupOrder() {
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (!validLocation()) return;
        String itemName = itemNameInput.getText().toString().trim();
        String receiverName = receiverNameInput.getText().toString().trim();
        String receiverPhone = receiverPhoneInput.getText().toString().trim();
        if (itemName.length() < 2) { showInfo("Barang", "Nama barang wajib diisi."); return; }
        if (receiverName.length() < 2 || receiverPhone.length() < 6) { showInfo("Penerima", "Nama dan nomor HP penerima wajib diisi."); return; }
        if (deliveryFee <= 0) { calculateOngkir(); return; }
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("user_id", userId); p.put("username", username);
                p.put("pickup_address", pickupAddressInput.getText().toString().trim());
                p.put("delivery_address", destinationInput.getText().toString().trim());
                p.put("pickup_lat", pickupLat); p.put("pickup_lng", pickupLng); p.put("delivery_lat", deliveryLat); p.put("delivery_lng", deliveryLng);
                p.put("item_name", itemName); p.put("item_category", itemCategory); p.put("item_value", parseMoney(itemValueInput.getText().toString()));
                p.put("package_size", packageSize); p.put("vehicle_type", vehicleType); p.put("fragile", fragile ? 1 : 0);
                p.put("receiver_name", receiverName); p.put("receiver_phone", receiverPhone); p.put("note", noteInput.getText().toString().trim());
                p.put("payment_method", paymentMethod); // price, distance, dan OTP dihitung/dibuat ulang oleh server
                JSONObject res = postJson(BASE_URL + "server/create_pickup_order.php", p);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Order penjemputan berhasil dibuat" : "Gagal membuat order penjemputan");
                mainHandler.post(() -> {
                    setLoading(false);
                    if (ok) {
                        otpCode = res.optString("otp", "").trim();
                        if (otpText != null) otpText.setText("OTP penerima: " + otpCode);
                        new TransivaAlertDialogBuilder(this)
                                .setTitle("Order berhasil")
                                .setMessage(msg + "\n\nOrder ID: " + res.optString("order_id", "-") + "\nOTP penerima: " + otpCode + "\n\nBerikan OTP hanya setelah paket diterima.")
                                .setNeutralButton("Salin OTP", (d, w) -> copyOtp())
                                .setPositiveButton("Lihat Aktivitas", (d, w) -> { startActivity(new Intent(this, CustomerHistoryActivity.class)); finish(); })
                                .show();
                    } else showInfo("Gagal", msg);
                });
            } catch (Exception e) { mainHandler.post(() -> { setLoading(false); showInfo("Error", "Koneksi gagal membuat order penjemputan."); }); }
        }).start();
    }

    private boolean validLocation() {
        if (!validCoordinate(pickupLat, pickupLng)) {
            showInfo("Lokasi Jemput", "Titik jemput belum valid.");
            return false;
        }
        if (!validCoordinate(deliveryLat, deliveryLng)) {
            showInfo("Lokasi Pengantaran", "Titik tujuan belum valid. Pilih kembali pengantaran pada peta.");
            return false;
        }
        return true;
    }

    private void copyOtp() {
        if (otpCode == null || otpCode.trim().isEmpty()) {
            showInfo("OTP", "OTP tersedia setelah order berhasil dibuat.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("OTP TransSend", otpCode));
        android.widget.Toast.makeText(this, "OTP berhasil disalin", android.widget.Toast.LENGTH_SHORT).show();
    }

    private double parseMoney(String s) { try { return Double.parseDouble(firstNonEmpty(s, "0").replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } }
    private String generateOtp() { return String.valueOf(100000 + new Random().nextInt(900000)); }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        CustomerApiClient.applySecurity(this, c);
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestProperty("Accept", "application/json");
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readStream(is); c.disconnect(); return new JSONObject(body);
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        CustomerApiClient.applySecurity(this, c);
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("Accept", "application/json");
        OutputStream os = c.getOutputStream(); os.write(payload.toString().getBytes(StandardCharsets.UTF_8)); os.flush(); os.close();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readStream(is); c.disconnect(); return new JSONObject(body);
    }

    private String readStream(InputStream is) throws Exception { if (is == null) return "{}"; BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close(); return sb.toString(); }


    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStartMap();
    }

    @Override protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);
        if (mapView != null) mapView.onResumeMap();
    }

    @Override protected void onPause() {
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
        mainHandler.removeCallbacksAndMessages(null);
        if (mapView != null) {
            mapView.onDestroyMap();
            mapView = null;
        }
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQ_LOCATION) loadPickupLocation(); }
    @Override public void onBackPressed() { finish(); }

    private int checkSelfPermissionSafe(String p) { try { if (android.os.Build.VERSION.SDK_INT >= 23) return checkSelfPermission(p); return PackageManager.PERMISSION_GRANTED; } catch (Exception e) { return PackageManager.PERMISSION_DENIED; } }
    private void buildTopBar(String title, String sub, boolean back) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,0,0,dp(16)); if (back) { TextView b = text("‹", 34, "#0B3A78", true); b.setGravity(Gravity.CENTER); b.setBackground(round("#FFFFFF", dp(18))); b.setOnClickListener(v -> finish()); row.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44))); } LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(12),0,0,0); col.addView(text(title, 23, "#0B3A78", true)); col.addView(text(sub, 12, "#64748B", false)); row.addView(col, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(row, new LinearLayout.LayoutParams(-1, -2)); }
    private EditText edit(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(14); e.setSingleLine(true); e.setTextColor(Color.parseColor("#0F172A")); e.setHintTextColor(Color.parseColor("#94A3B8")); e.setPadding(dp(14),0,dp(14),0); e.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1)); return e; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private void addChoice(LinearLayout row, Button a, Button b) { row.addView(a, new LinearLayout.LayoutParams(0, dp(58), 1)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1); lp.setMargins(dp(10),0,0,0); row.addView(b, lp); }
    private void addInner(LinearLayout parent, View v, int top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50)); lp.setMargins(0, top, 0, 0); parent.addView(v, lp); }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(roundStroke("#FFFFFF", "#E2ECF8", dp(22), 1)); v.setElevation(dp(2)); return v; }
    private TextView text(String s, int sp, String color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private Button primaryButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18))); return b; }
    private Button outlineButton(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.parseColor("#0B7CFF")); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1)); return b; }
    private Button choiceButton(String s, boolean active) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); setChoice(b, active); return b; }
    private GradientDrawable round(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(radius); return g; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) { GradientDrawable g = round(color, radius); g.setStroke(dp(sw), Color.parseColor(stroke)); return g; }
    private GradientDrawable roundGradient(String c1, String c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(c1), Color.parseColor(c2)}); g.setCornerRadius(radius); return g; }
    private void addWithMargin(View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); root.addView(v, lp); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private void showInfo(String title, String msg) { try { new TransivaAlertDialogBuilder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(double v) { try { NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("id", "ID")); nf.setMaximumFractionDigits(0); return nf.format(v).replace("Rp", "Rp "); } catch (Exception e) { return "Rp " + Math.round(v); } }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
}
