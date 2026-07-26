package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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

public class TransLaundryActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String PREF_NAME = "transiva";
    private static final int TIMEOUT_MS = 20000;
    private static final int REQ_LOCATION = 4401;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout page;
    private LinearLayout root;
    private LinearLayout listBox;
    private ProgressBar progressBar;
    private TextView pickupText;
    private TextView selectedText;
    private EditText searchInput;
    private EditText noteInput;

    private final List<JSONObject> laundries = new ArrayList<>();
    private JSONObject selectedLaundry;
    private LocationManager locationManager;

    private int userId = 0;
    private String username = "User";
    private double pickupLat = 0;
    private double pickupLng = 0;
    private String pickupAddress = "Lokasi jemput";
    private String query = "";
    private boolean ordering = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}
        loadSession();
        buildBase();
        renderPage();
        loadActualLocation();
        loadLaundries();
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
            SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            username = firstNonEmpty(sp.getString("username", ""), sp.getString("player_username", ""), "User");
            userId = sp.getInt("id", sp.getInt("user_id", 0));
        } catch (Exception ignored) {}
    }

    private void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));
        ScrollView scroll = new ScrollView(this);
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

    private void renderPage() {
        root.removeAllViews();
        buildTopBar("TransLaundry", "Jemput pakaian ke lokasi kamu", true);

        LinearLayout pickup = card();
        pickup.setPadding(dp(16), dp(14), dp(16), dp(14));
        pickup.addView(text("Titik Jemput", 13, "#64748B", true));
        pickupText = text(pickupLat != 0 && pickupLng != 0 ? pickupAddress : "Mengambil lokasi...", 15, "#0F172A", true);
        pickupText.setPadding(0, dp(6), 0, dp(10));
        pickup.addView(pickupText);
        Button gps = outlineButton("◎ Pakai GPS Saat Ini");
        gps.setOnClickListener(v -> loadActualLocation());
        pickup.addView(gps, new LinearLayout.LayoutParams(-1, dp(46)));
        addWithMargin(pickup, 0, 0, 0, dp(14));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setText(query);
        searchInput.setTextSize(16);
        searchInput.setHint("Cari nama laundry...");
        searchInput.setHintTextColor(Color.parseColor("#94A3B8"));
        searchInput.setTextColor(Color.parseColor("#0F172A"));
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        searchInput.setSelection(searchInput.getText().length());
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                renderLaundryList();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        addWithMargin(searchInput, 0, 0, 0, dp(14), dp(56));

        selectedText = text("Pilih laundry tersedia", 14, "#64748B", false);
        selectedText.setPadding(dp(14), dp(12), dp(14), dp(12));
        selectedText.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));
        addWithMargin(selectedText, 0, 0, 0, dp(12));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox, new LinearLayout.LayoutParams(-1, -2));
        renderLaundryList();

        noteInput = new EditText(this);
        noteInput.setMinLines(2);
        noteInput.setMaxLines(4);
        noteInput.setGravity(Gravity.TOP);
        noteInput.setTextSize(14);
        noteInput.setHint("Catatan pakaian / alamat detail...");
        noteInput.setHintTextColor(Color.parseColor("#94A3B8"));
        noteInput.setTextColor(Color.parseColor("#0F172A"));
        noteInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        noteInput.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(18), 1));
        addWithMargin(noteInput, 0, dp(4), 0, dp(12));

        Button order = primaryButton("Order TransLaundry");
        order.setOnClickListener(v -> confirmOrder());
        addWithMargin(order, 0, 0, 0, dp(10), dp(54));
    }

    private void renderLaundryList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        if (laundries.isEmpty()) {
            addStatusTo(listBox, "Memuat laundry...");
            return;
        }
        String q = firstNonEmpty(query, "").toLowerCase(Locale.ROOT).trim();
        int shown = 0;
        for (JSONObject l : laundries) {
            if (q.length() == 0 || contains(l.optString("name"), q) || contains(l.optString("address"), q)) {
                addLaundryCard(l);
                shown++;
            }
        }
        if (shown == 0) addStatusTo(listBox, "Laundry tidak ditemukan untuk: " + query);
    }

    private void addLaundryCard(JSONObject l) {
        boolean active = selectedLaundry != null && selectedLaundry.optInt("id", 0) == l.optInt("id", -1);
        LinearLayout card = card();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setClickable(true);
        card.setBackground(roundStroke(active ? "#EAF4FF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(22), active ? 2 : 1));
        card.setOnClickListener(v -> selectLaundry(l));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text("🧺", 28, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, 0, 0);
        col.addView(text(firstNonEmpty(l.optString("name"), "Laundry"), 16, "#0F172A", true));
        col.addView(text("Harga mulai " + rupiah(l.optDouble("price", 0)), 13, "#0B7CFF", true));
        TextView addr = text(firstNonEmpty(l.optString("address"), "Alamat belum diisi"), 12, "#64748B", false);
        addr.setPadding(0, dp(4), 0, 0);
        col.addView(addr);
        if (pickupLat != 0 && pickupLng != 0) {
            double km = distanceKm(pickupLat, pickupLng, l.optDouble("latitude", 0), l.optDouble("longitude", 0));
            TextView dist = text(String.format(Locale.US, "± %.2f km dari kamu", km), 12, "#94A3B8", false);
            dist.setPadding(0, dp(4), 0, 0);
            col.addView(dist);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row);
        addWithMarginTo(listBox, card, 0, 0, 0, dp(10));
    }

    private void selectLaundry(JSONObject l) {
        selectedLaundry = l;
        if (selectedText != null) selectedText.setText("Dipilih: " + firstNonEmpty(l.optString("name"), "Laundry") + " • " + rupiah(l.optDouble("price", 0)));
        renderLaundryList();
    }

    private void loadLaundries() {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/get_laundries.php?v=" + System.currentTimeMillis());
                JSONArray arr = res.optJSONArray("laundries");
                if (!res.optBoolean("success", false) || arr == null) throw new Exception(firstNonEmpty(res.optString("message"), "Gagal memuat laundry"));
                laundries.clear();
                for (int i = 0; i < arr.length(); i++) laundries.add(arr.getJSONObject(i));
                mainHandler.post(() -> { setLoading(false); renderLaundryList(); });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); addStatusTo(listBox, "Gagal memuat laundry"); showInfo("Gagal", e.getMessage()); });
            }
        }).start();
    }

    private void loadActualLocation() {
        if (pickupText != null) pickupText.setText("Mengambil lokasi...");
        if (checkSelfPermissionSafe(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermissionSafe(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) { setPickup(-0.805480, 120.158949, "Lokasi default Transiva"); return; }
            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) {
                if (pickupText != null) pickupText.setText("GPS belum aktif. Ketuk untuk aktifkan GPS.");
                if (pickupText != null) pickupText.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
                return;
            }
            Location best = null;
            try { best = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (best == null) try { best = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            if (best != null) updateLocation(best);
            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            locationManager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) { updateLocation(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());
        } catch (Exception e) {
            setPickup(-0.805480, 120.158949, "Lokasi default Transiva");
        }
    }

    private void updateLocation(Location loc) {
        if (loc == null) return;
        double lat = loc.getLatitude();
        double lng = loc.getLongitude();
        try { new SessionManager(this).saveLastLocation(String.valueOf(lat), String.valueOf(lng)); } catch (Exception ignored) {}
        new Thread(() -> {
            String addr = "Lokasi jemput kamu";
            try {
                Geocoder geocoder = new Geocoder(this, new Locale("id", "ID"));
                List<Address> list = geocoder.getFromLocation(lat, lng, 1);
                if (list != null && !list.isEmpty()) addr = firstNonEmpty(list.get(0).getAddressLine(0), addr);
            } catch (Exception ignored) {}
            String finalAddr = addr;
            mainHandler.post(() -> setPickup(lat, lng, finalAddr));
        }).start();
    }

    private void setPickup(double lat, double lng, String addr) {
        pickupLat = lat; pickupLng = lng; pickupAddress = firstNonEmpty(addr, "Lokasi jemput");
        if (pickupText != null) pickupText.setText(pickupAddress + "\n" + String.format(Locale.US, "%.6f, %.6f", pickupLat, pickupLng));
        renderLaundryList();
    }

    private void confirmOrder() {
        if (ordering) return;
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (pickupLat == 0 || pickupLng == 0) { showInfo("Lokasi", "Titik jemput belum tersedia."); return; }
        if (selectedLaundry == null) { showInfo("Laundry", "Pilih laundry terlebih dahulu."); return; }
        double km = distanceKm(pickupLat, pickupLng, selectedLaundry.optDouble("latitude", 0), selectedLaundry.optDouble("longitude", 0)) * 1.25;
        double laundryPrice = selectedLaundry.optDouble("price", 0);
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi TransLaundry")
                .setMessage("Laundry: " + firstNonEmpty(selectedLaundry.optString("name"), "Laundry") +
                        "\nHarga mulai: " + rupiah(laundryPrice) +
                        "\nJarak estimasi: " + String.format(Locale.US, "%.2f km", km) +
                        "\n\nLanjut order sekarang?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Order", (d, w) -> createOrder())
                .show();
    }

    private void createOrder() {
        ordering = true;
        setLoading(true);
        new Thread(() -> {
            try {
                String orderId = "LDY-" + System.currentTimeMillis();
                String note = noteInput == null ? "" : noteInput.getText().toString().trim();
                double laundryPrice = selectedLaundry.optDouble("price", 0);
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("id", orderId);
                payload.put("order_type", "laundry");
                payload.put("driver_type", "bike");
                payload.put("service_name", "TransLaundry");
                payload.put("note", "TransLaundry: " + firstNonEmpty(selectedLaundry.optString("name"), "Laundry") + " | Harga laundry mulai " + rupiah(laundryPrice) + (note.length() > 0 ? " | Catatan: " + note : ""));
                JSONObject pickup = new JSONObject();
                pickup.put("latitude", pickupLat); pickup.put("longitude", pickupLng); pickup.put("address", pickupAddress);
                payload.put("pickup", pickup);
                JSONObject delivery = new JSONObject();
                delivery.put("latitude", selectedLaundry.optDouble("latitude", 0));
                delivery.put("longitude", selectedLaundry.optDouble("longitude", 0));
                delivery.put("address", firstNonEmpty(selectedLaundry.optString("name"), "Laundry") + " - " + firstNonEmpty(selectedLaundry.optString("address"), "Laundry"));
                payload.put("delivery", delivery);
                JSONObject userLoc = new JSONObject();
                userLoc.put("latitude", pickupLat); userLoc.put("longitude", pickupLng);
                payload.put("userLocation", userLoc);
                payload.put("status", "pending");
                JSONObject res = postJson(BASE_URL + "server/createOrder.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Order berhasil dibuat" : "Gagal membuat order");
                String finalOrderId = firstNonEmpty(res.optString("order_id"), orderId);
                mainHandler.post(() -> {
                    ordering = false; setLoading(false);
                    if (ok) {
                        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
                                .putString("active_order_id", finalOrderId)
                                .putString("active_order_type", "laundry")
                                .putString("active_driver_type", "bike")
                                .apply();
                        new AlertDialog.Builder(this)
                                .setTitle("Order Berhasil")
                                .setMessage("Order TransLaundry berhasil dibuat. Driver akan diarahkan ke titik jemput kamu.\n\nOrder ID: " + finalOrderId)
                                .setPositiveButton("OK", (d, w) -> finish())
                                .show();
                    } else showInfo("Gagal", msg);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { ordering = false; setLoading(false); showInfo("Error", "Koneksi gagal membuat order laundry."); });
            }
        }).start();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("GET"); c.setRequestProperty("Accept", "application/json");
        return new JSONObject(readStream(c));
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS); c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("Accept", "application/json"); c.setDoOutput(true);
        OutputStream os = c.getOutputStream(); os.write(payload.toString().getBytes(StandardCharsets.UTF_8)); os.flush(); os.close();
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

    private int checkSelfPermissionSafe(String p) { try { if (android.os.Build.VERSION.SDK_INT >= 23) return checkSelfPermission(p); return PackageManager.PERMISSION_GRANTED; } catch (Exception e) { return PackageManager.PERMISSION_DENIED; } }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQ_LOCATION) loadActualLocation(); }
    @Override public void onBackPressed() { finish(); }

    private boolean contains(String value, String q) { return value != null && value.toLowerCase(Locale.ROOT).contains(q); }
    private double distanceKm(double lat1, double lng1, double lat2, double lng2) { double r=6371, dLat=Math.toRadians(lat2-lat1), dLng=Math.toRadians(lng2-lng1); double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLng/2)*Math.sin(dLng/2); return r*2*Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); }

    private void buildTopBar(String title, String sub, boolean back) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,0,0,dp(16)); if (back) { TextView b=text("‹",34,"#0B3A78",true); b.setGravity(Gravity.CENTER); b.setBackground(round("#FFFFFF",dp(18))); b.setOnClickListener(v -> finish()); row.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44))); } LinearLayout col=new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(12),0,0,0); col.addView(text(title,23,"#0B3A78",true)); if(sub!=null&&sub.length()>0) col.addView(text(sub,12,"#64748B",false)); row.addView(col,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row,new LinearLayout.LayoutParams(-1,-2)); }
    private LinearLayout card() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(roundStroke("#FFFFFF","#E2ECF8",dp(22),1)); v.setElevation(dp(2)); return v; }
    private TextView text(String s,int sp,String color,boolean bold) { TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private Button primaryButton(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(round("#0B7CFF",dp(18))); return b; }
    private Button outlineButton(String s) { Button b=primaryButton(s); b.setTextColor(Color.parseColor("#0B7CFF")); b.setBackground(roundStroke("#FFFFFF","#B9DBFF",dp(18),1)); return b; }
    private GradientDrawable round(String color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(color));g.setCornerRadius(radius);return g;}
    private GradientDrawable roundStroke(String color,String stroke,int radius,int sw){GradientDrawable g=round(color,radius);g.setStroke(dp(sw),Color.parseColor(stroke));return g;}
    private void addWithMargin(View v,int l,int t,int r,int b){addWithMargin(v,l,t,r,b,-2);} private void addWithMargin(View v,int l,int t,int r,int b,int h){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,h);lp.setMargins(l,t,r,b);root.addView(v,lp);} private void addWithMarginTo(LinearLayout p,View v,int l,int t,int r,int b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(l,t,r,b);p.addView(v,lp);} private void addStatusTo(LinearLayout p,String m){if(p==null)return;TextView t=text(m,14,"#64748B",false);t.setGravity(Gravity.CENTER);t.setPadding(dp(16),dp(20),dp(16),dp(20));t.setBackground(roundStroke("#FFFFFF","#D7E6F8",dp(20),1));addWithMarginTo(p,t,0,0,0,dp(12));}
    private void setLoading(boolean b){if(progressBar!=null)progressBar.setVisibility(b?View.VISIBLE:View.GONE);} private void showInfo(String title,String msg){try{new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK",null).show();}catch(Exception ignored){}} private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);} private String rupiah(double v){return "Rp " + NumberFormat.getNumberInstance(new Locale("id","ID")).format((long)v);} private String firstNonEmpty(String... values){if(values==null)return"";for(String s:values)if(s!=null&&s.trim().length()>0&&!"null".equalsIgnoreCase(s.trim()))return s.trim();return"";}
}
