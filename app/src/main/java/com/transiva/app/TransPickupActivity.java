package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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
    private ProgressBar progressBar;

    private EditText pickupAddressInput, destinationInput, itemNameInput, itemValueInput, receiverNameInput, receiverPhoneInput, noteInput;
    private TextView pickupCoordText, deliveryCoordText, priceText, distanceText, otpText;
    private Button bikeBtn, carBtn, smallBtn, mediumBtn, bigBtn, cargoBtn, fragileYesBtn, fragileNoBtn, cashBtn, balanceBtn, calculateBtn, orderBtn;

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
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (android.os.Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Exception ignored) {}
        loadSession();
        buildBase();
        buildForm();
        loadPickupLocation();
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
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(52), dp(52));
        p.gravity = Gravity.CENTER;
        page.addView(progressBar, p);
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private void buildForm() {
        root.removeAllViews();
        buildTopBar("TransPickup", "Kirim paket instan dengan driver Transiva", true);
        LinearLayout hero = card();
        hero.setPadding(dp(16), dp(14), dp(16), dp(14));
        hero.setBackground(roundGradient("#FFFFFF", "#EAF4FF", dp(24)));
        hero.addView(text("📦 Titip Kirim Barang", 21, "#0B3A78", true));
        TextView sub = text("Ongkir dihitung aman di server. Sertakan OTP penerima untuk menyelesaikan pengiriman.", 13, "#64748B", false);
        sub.setPadding(0, dp(6), 0, 0);
        hero.addView(sub);
        addWithMargin(hero, 0, 0, 0, dp(14));

        buildLocationCard();
        buildItemCard();
        buildOptionCard();
        buildPaymentCard();
        buildSummaryCard();
    }

    private void buildLocationCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Lokasi", 17, "#0B3A78", true));

        pickupAddressInput = edit("Alamat penjemputan");
        pickupAddressInput.setText("Lokasi saya saat ini");
        addInner(card, pickupAddressInput, dp(10));
        pickupCoordText = text("📍 Mengambil titik jemput...", 12, "#64748B", false);
        pickupCoordText.setPadding(0, dp(6), 0, dp(8));
        card.addView(pickupCoordText);
        Button current = outlineButton("Gunakan lokasi saya");
        current.setOnClickListener(v -> loadPickupLocation());
        card.addView(current, new LinearLayout.LayoutParams(-1, dp(48)));

        destinationInput = edit("Alamat tujuan / nama tempat tujuan");
        destinationInput.setSingleLine(false);
        destinationInput.setMinLines(1);
        destinationInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        addInner(card, destinationInput, dp(12));
        deliveryCoordText = text("📍 Tujuan belum dipilih", 12, "#64748B", false);
        deliveryCoordText.setPadding(0, dp(6), 0, dp(8));
        card.addView(deliveryCoordText);
        Button find = outlineButton("Cari titik tujuan dari alamat");
        find.setOnClickListener(v -> geocodeDestination());
        card.addView(find, new LinearLayout.LayoutParams(-1, dp(48)));
        destinationInput.setOnEditorActionListener((v, actionId, event) -> { geocodeDestination(); return true; });

        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void buildItemCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Detail Barang", 17, "#0B3A78", true));
        itemNameInput = edit("Nama barang, contoh: Dokumen / paket baju");
        addInner(card, itemNameInput, dp(10));
        itemValueInput = edit("Nilai barang, contoh: 50000");
        itemValueInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        addInner(card, itemValueInput, dp(10));
        receiverNameInput = edit("Nama penerima");
        addInner(card, receiverNameInput, dp(10));
        receiverPhoneInput = edit("Nomor HP penerima");
        receiverPhoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        addInner(card, receiverPhoneInput, dp(10));
        noteInput = edit("Catatan untuk driver");
        noteInput.setSingleLine(false); noteInput.setMinLines(2);
        addInner(card, noteInput, dp(10));
        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void buildOptionCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Ukuran Paket", 17, "#0B3A78", true));
        LinearLayout row1 = row();
        smallBtn = choiceButton("📄 Kecil\n< 2 kg", true);
        mediumBtn = choiceButton("📦 Sedang\n2-5 kg", false);
        addChoice(row1, smallBtn, mediumBtn);
        card.addView(row1);
        LinearLayout row2 = row();
        bigBtn = choiceButton("🧳 Besar\n5-20 kg", false);
        cargoBtn = choiceButton("🚚 Cargo\n>20 kg", false);
        addChoice(row2, bigBtn, cargoBtn);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(-1, -2); r2lp.setMargins(0, dp(10), 0, 0);
        card.addView(row2, r2lp);
        smallBtn.setOnClickListener(v -> { packageSize = "small"; updateChoices(); });
        mediumBtn.setOnClickListener(v -> { packageSize = "medium"; updateChoices(); });
        bigBtn.setOnClickListener(v -> { packageSize = "big"; updateChoices(); });
        cargoBtn.setOnClickListener(v -> { packageSize = "cargo"; updateChoices(); });

        TextView vehicleTitle = text("Kendaraan", 17, "#0B3A78", true); vehicleTitle.setPadding(0, dp(16), 0, 0); card.addView(vehicleTitle);
        LinearLayout vrow = row();
        bikeBtn = choiceButton("🏍️ Motor\nCepat", true);
        carBtn = choiceButton("🚗 Mobil\nBesar", false);
        addChoice(vrow, bikeBtn, carBtn);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2); vlp.setMargins(0, dp(10), 0, 0);
        card.addView(vrow, vlp);
        bikeBtn.setOnClickListener(v -> { vehicleType = "bike"; updateChoices(); });
        carBtn.setOnClickListener(v -> { vehicleType = "car"; updateChoices(); });

        TextView fragileTitle = text("Barang mudah pecah?", 15, "#0B3A78", true); fragileTitle.setPadding(0, dp(16), 0, 0); card.addView(fragileTitle);
        LinearLayout frow = row();
        fragileNoBtn = choiceButton("Tidak", true);
        fragileYesBtn = choiceButton("Ya", false);
        addChoice(frow, fragileNoBtn, fragileYesBtn);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2); flp.setMargins(0, dp(10), 0, 0);
        card.addView(frow, flp);
        fragileNoBtn.setOnClickListener(v -> { fragile = false; updateChoices(); });
        fragileYesBtn.setOnClickListener(v -> { fragile = true; updateChoices(); });

        addWithMargin(card, 0, 0, 0, dp(14));
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
        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void buildSummaryCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Ringkasan", 17, "#0B3A78", true));
        distanceText = text("Jarak: -", 13, "#64748B", false); distanceText.setPadding(0, dp(8), 0, 0); card.addView(distanceText);
        priceText = text("Ongkir: hitung dulu", 22, "#0B3A78", true); priceText.setPadding(0, dp(4), 0, dp(10)); card.addView(priceText);
        otpCode = generateOtp();
        otpText = text("OTP penerima: " + otpCode, 13, "#0B7CFF", true); otpText.setPadding(0, 0, 0, dp(12)); card.addView(otpText);
        calculateBtn = outlineButton("Hitung Ongkir");
        orderBtn = primaryButton("Cari Driver");
        orderBtn.setEnabled(false); orderBtn.setAlpha(0.55f);
        card.addView(calculateBtn, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, dp(52)); olp.setMargins(0, dp(10), 0, 0);
        card.addView(orderBtn, olp);
        calculateBtn.setOnClickListener(v -> calculateOngkir());
        orderBtn.setOnClickListener(v -> createPickupOrder());
        addWithMargin(card, 0, 0, 0, dp(20));
    }

    private void updateChoices() {
        setChoice(smallBtn, "small".equals(packageSize)); setChoice(mediumBtn, "medium".equals(packageSize)); setChoice(bigBtn, "big".equals(packageSize)); setChoice(cargoBtn, "cargo".equals(packageSize));
        setChoice(bikeBtn, "bike".equals(vehicleType)); setChoice(carBtn, "car".equals(vehicleType));
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
        pickupLat = loc.getLatitude(); pickupLng = loc.getLongitude();
        String text = "📍 " + String.format(Locale.US, "%.6f, %.6f", pickupLat, pickupLng);
        if (pickupCoordText != null) pickupCoordText.setText(text);
        try { new SessionManager(this).saveLastLocation(String.valueOf(pickupLat), String.valueOf(pickupLng)); } catch (Exception ignored) {}
        updateChoices();
    }

    private void geocodeDestination() {
        String q = destinationInput == null ? "" : destinationInput.getText().toString().trim();
        if (q.length() < 4) { showInfo("Tujuan", "Masukkan alamat tujuan lebih lengkap."); return; }
        setLoading(true);
        new Thread(() -> {
            try {
                Geocoder geo = new Geocoder(this, new Locale("id", "ID"));
                List<Address> list = geo.getFromLocationName(q, 1);
                if (list == null || list.isEmpty()) throw new Exception("Tujuan tidak ditemukan.");
                Address a = list.get(0);
                deliveryLat = a.getLatitude(); deliveryLng = a.getLongitude();
                String label = "📍 " + String.format(Locale.US, "%.6f, %.6f", deliveryLat, deliveryLng);
                mainHandler.post(() -> { setLoading(false); deliveryCoordText.setText(label); updateChoices(); calculateOngkir(); });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showInfo("Tujuan", "Alamat tujuan belum ditemukan. Coba tulis lebih lengkap, misalnya nama kecamatan/kabupaten."); });
            }
        }).start();
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
                p.put("payment_method", paymentMethod); p.put("price", deliveryFee); p.put("distance_km", distanceKm); p.put("delivery_otp", otpCode);
                JSONObject res = postJson(BASE_URL + "server/create_pickup_order.php", p);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Order pickup berhasil dibuat" : "Gagal membuat order pickup");
                mainHandler.post(() -> {
                    setLoading(false);
                    if (ok) new AlertDialog.Builder(this).setTitle("Berhasil").setMessage(msg + "\n\nOrder ID: " + res.optString("order_id", "-") + "\nOTP penerima: " + otpCode).setPositiveButton("OK", (d, w) -> finish()).show();
                    else showInfo("Gagal", msg);
                });
            } catch (Exception e) { mainHandler.post(() -> { setLoading(false); showInfo("Error", "Koneksi gagal membuat order pickup."); }); }
        }).start();
    }

    private boolean validLocation() {
        if (pickupLat == 0 || pickupLng == 0) { showInfo("Lokasi Jemput", "Titik jemput belum valid."); return false; }
        if (deliveryLat == 0 || deliveryLng == 0) { showInfo("Lokasi Tujuan", "Cari titik tujuan terlebih dahulu."); return false; }
        return true;
    }

    private double parseMoney(String s) { try { return Double.parseDouble(firstNonEmpty(s, "0").replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } }
    private String generateOtp() { return String.valueOf(100000 + new Random().nextInt(900000)); }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestProperty("Accept", "application/json");
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readStream(is); c.disconnect(); return new JSONObject(body);
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("Accept", "application/json");
        OutputStream os = c.getOutputStream(); os.write(payload.toString().getBytes(StandardCharsets.UTF_8)); os.flush(); os.close();
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = readStream(is); c.disconnect(); return new JSONObject(body);
    }

    private String readStream(InputStream is) throws Exception { if (is == null) return "{}"; BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close(); return sb.toString(); }

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
    private void showInfo(String title, String msg) { try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(double v) { try { NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("id", "ID")); nf.setMaximumFractionDigits(0); return nf.format(v).replace("Rp", "Rp "); } catch (Exception e) { return "Rp " + Math.round(v); } }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
}
