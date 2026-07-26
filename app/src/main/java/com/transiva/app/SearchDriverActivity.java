package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchDriverActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final int TIMEOUT_MS = 20000;
    private static final int REQ_LOCATION = 2201;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private RadarView radarView;
    private TextView titleText;
    private TextView subtitleText;
    private TextView driverNameText;
    private TextView driverDistanceText;
    private TextView driverAvatarText;
    private TextView driverPlateText;
    private TextView driverRatingText;
    private TextView acceptedBadgeText;
    private ImageView driverPhotoView;
    private LinearLayout driverCard;
    private WebView miniMap;
    private Button cancelBtn;
    private ProgressBar progressBar;

    private boolean isCanceling = false;
    private boolean driverFound = false;
    private boolean destroyed = false;

    private String activeOrderId = "";
    private double userLat = 0;
    private double userLng = 0;
    private boolean hasUserLocation = false;

    private final Runnable driverRadarRunnable = new Runnable() {
        @Override public void run() {
            if (!destroyed && !isCanceling && !driverFound) {
                loadIdleDriversToRadar();
                mainHandler.postDelayed(this, 5000);
            }
        }
    };

    private final Runnable checkOrderRunnable = new Runnable() {
        @Override public void run() {
            if (!destroyed && !isCanceling && !driverFound) {
                checkOrderStatus();
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        activeOrderId = firstNonEmpty(
                getIntent().getStringExtra("order_id"),
                getStringPref("active_order_id"),
                getStringPref("order_id")
        );

        buildLayout();
        getUserLocationThenStart();
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView appTitle = text("Transiva", 14, "#0B7CFF", true);
        appTitle.setGravity(Gravity.CENTER);
        root.addView(appTitle, new LinearLayout.LayoutParams(-1, -2));

        titleText = text("Mencari Driver...", 26, "#0B3A78", true);
        titleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(12), 0, dp(6));
        root.addView(titleText, titleLp);

        subtitleText = text("Mengambil lokasi & mencari driver terdekat", 14, "#64748B", false);
        subtitleText.setGravity(Gravity.CENTER);
        root.addView(subtitleText, new LinearLayout.LayoutParams(-1, -2));

        radarView = new RadarView(this);
        LinearLayout.LayoutParams radarLp = new LinearLayout.LayoutParams(dp(290), dp(290));
        radarLp.setMargins(0, dp(22), 0, dp(18));
        root.addView(radarView, radarLp);

        cancelBtn = new Button(this);
        cancelBtn.setAllCaps(false);
        cancelBtn.setText("Batalkan Order");
        cancelBtn.setTextSize(15);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setBackground(roundGradient("#EF4444", "#DC2626", dp(18)));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, dp(52));
        root.addView(cancelBtn, cancelLp);
        cancelBtn.setOnClickListener(v -> confirmCancelOrder());

        driverCard = new LinearLayout(this);
        driverCard.setOrientation(LinearLayout.VERTICAL);
        driverCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        driverCard.setBackground(roundStroke("#FFFFFF", "#CFE1F7", dp(26), 1));
        driverCard.setElevation(dp(8));
        driverCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams driverCardLp = new LinearLayout.LayoutParams(-1, -2);
        driverCardLp.setMargins(0, dp(18), 0, 0);
        root.addView(driverCard, driverCardLp);

        acceptedBadgeText = text("✓  DRIVER MENERIMA PESANAN", 11, "#08783E", true);
        acceptedBadgeText.setGravity(Gravity.CENTER);
        acceptedBadgeText.setPadding(dp(12), dp(7), dp(12), dp(7));
        acceptedBadgeText.setBackground(round("#E9FFF3", dp(18)));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        badgeLp.setMargins(0, 0, 0, dp(16));
        driverCard.addView(acceptedBadgeText, badgeLp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        driverCard.addView(row, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout avatarFrame = new FrameLayout(this);
        avatarFrame.setBackground(roundStroke("#EAF3FF", "#BFD8F6", dp(22), 1));
        LinearLayout.LayoutParams avFrameLp = new LinearLayout.LayoutParams(dp(78), dp(78));
        avFrameLp.setMargins(0, 0, dp(14), 0);
        row.addView(avatarFrame, avFrameLp);

        driverAvatarText = text("D", 24, "#FFFFFF", true);
        driverAvatarText.setGravity(Gravity.CENTER);
        driverAvatarText.setBackground(round("#0B7CFF", dp(22)));
        avatarFrame.addView(driverAvatarText, new FrameLayout.LayoutParams(-1, -1));

        driverPhotoView = new ImageView(this);
        driverPhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        driverPhotoView.setBackground(round("#EAF3FF", dp(22)));
        driverPhotoView.setClipToOutline(true);
        driverPhotoView.setVisibility(View.GONE);
        avatarFrame.addView(driverPhotoView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1));

        driverNameText = text("Driver", 19, "#0B3A78", true);
        infoCol.addView(driverNameText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
        metaLp.setMargins(0, dp(6), 0, 0);
        infoCol.addView(metaRow, metaLp);

        driverPlateText = text("Plat • -", 12, "#475569", true);
        driverPlateText.setPadding(dp(9), dp(5), dp(9), dp(5));
        driverPlateText.setBackground(round("#F1F5F9", dp(12)));
        metaRow.addView(driverPlateText, new LinearLayout.LayoutParams(-2, -2));

        driverRatingText = text("⭐ 5.0", 12, "#8A5A00", true);
        driverRatingText.setPadding(dp(9), dp(5), dp(9), dp(5));
        driverRatingText.setBackground(round("#FFF8E1", dp(12)));
        LinearLayout.LayoutParams ratingLp = new LinearLayout.LayoutParams(-2, -2);
        ratingLp.setMargins(dp(8), 0, 0, 0);
        metaRow.addView(driverRatingText, ratingLp);

        driverDistanceText = text("Driver sedang menuju lokasi jemput", 13, "#64748B", false);
        LinearLayout.LayoutParams distLp = new LinearLayout.LayoutParams(-1, -2);
        distLp.setMargins(0, dp(9), 0, 0);
        infoCol.addView(driverDistanceText, distLp);

        miniMap = new WebView(this);
        miniMap.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = miniMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, dp(230));
        mapLp.setMargins(0, dp(16), 0, 0);
        driverCard.addView(miniMap, mapLp);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private void getUserLocationThenStart() {
        if (activeOrderId.length() == 0) {
            showInfo("Order Tidak Ada", "Order aktif tidak ditemukan.");
            goHomeDelayed(800);
            return;
        }

        saveStringPref("active_order_id", activeOrderId);

        if (checkSelfPermissionSafe(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermissionSafe(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            startLoops();
            return;
        }

        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) {
                setSubtitle("GPS tidak tersedia, radar tetap berjalan");
                startLoops();
                return;
            }

            boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            Location best = null;
            if (gps) {
                try { best = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            }
            if (best == null && network) {
                try { best = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            }
            if (best != null) updateUserLocation(best);

            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (gps || network) {
                lm.requestSingleUpdate(provider, new LocationListener() {
                    @Override public void onLocationChanged(Location location) { updateUserLocation(location); }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                }, Looper.getMainLooper());
            } else {
                setSubtitle("GPS belum aktif, radar tetap berjalan");
            }
        } catch (Exception e) {
            setSubtitle("GPS gagal diakses, radar tetap berjalan");
        }

        startLoops();
    }

    private void updateUserLocation(Location location) {
        if (location == null) return;
        userLat = location.getLatitude();
        userLng = location.getLongitude();
        hasUserLocation = true;
        setSubtitle("Mencari driver aktif di sekitar Anda");
    }

    private void startLoops() {
        mainHandler.removeCallbacks(driverRadarRunnable);
        mainHandler.removeCallbacks(checkOrderRunnable);
        loadIdleDriversToRadar();
        checkOrderStatus();
        mainHandler.postDelayed(driverRadarRunnable, 5000);
        mainHandler.postDelayed(checkOrderRunnable, 3000);
    }

    private void loadIdleDriversToRadar() {
        new Thread(() -> {
            try {
                JSONObject orderPayload = new JSONObject();
                orderPayload.put("order_id", activeOrderId);
                JSONObject orderRes = postJson(BASE_URL + "server/check_order_status.php", orderPayload);

                if (!orderRes.optBoolean("success", false)) {
                    mainHandler.post(() -> setSubtitle(firstNonEmpty(orderRes.optString("message"), "Gagal membaca tipe order")));
                    return;
                }

                String type = firstNonEmpty(
                        orderRes.optString("driver_type", ""),
                        orderRes.optString("order_type", ""),
                        orderRes.optJSONObject("order") != null ? orderRes.optJSONObject("order").optString("driver_type", "") : "",
                        orderRes.optJSONObject("order") != null ? orderRes.optJSONObject("order").optString("order_type", "") : ""
                ).toLowerCase(Locale.US).trim();

                if (type.equals("transbike") || type.equals("bike") || type.equals("motor") || type.equals("kurir")) {
                    type = "bike";
                } else if (type.equals("transcar") || type.equals("car") || type.equals("mobil")) {
                    type = "car";
                } else {
                    type = "bike";
                }

                JSONObject payload = new JSONObject();
                payload.put("type", type);
                JSONObject res = postJson(BASE_URL + "server/get_idle_drivers.php", payload);

                if (!res.optBoolean("success", false)) {
                    mainHandler.post(() -> setSubtitle(firstNonEmpty(res.optString("message"), "Gagal mengambil driver")));
                    return;
                }

                JSONArray arr = res.optJSONArray("drivers");
                List<RadarDriver> drivers = parseDrivers(arr);

                mainHandler.post(() -> {
                    radarView.setDrivers(drivers, hasUserLocation, userLat, userLng);
                    if (drivers.size() > 0 && hasUserLocation) {
                        double nearest = nearestDistance(drivers);
                        setSubtitle("Driver terdekat sekitar " + String.format(Locale.US, "%.1f", nearest) + " km");
                    } else if (drivers.size() > 0) {
                        setSubtitle("Ada " + drivers.size() + " driver aktif, menunggu GPS akurat");
                    } else {
                        setSubtitle("Belum ada driver aktif di sekitar Anda");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> setSubtitle("Koneksi gagal mengambil driver"));
            }
        }).start();
    }

    private List<RadarDriver> parseDrivers(JSONArray arr) {
        List<RadarDriver> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject d = arr.optJSONObject(i);
            if (d == null) continue;
            double lat = safeDouble(firstNonEmpty(d.optString("lat"), d.optString("latitude")));
            double lng = safeDouble(firstNonEmpty(d.optString("lng"), d.optString("longitude")));
            if (lat == 0 || lng == 0) continue;
            RadarDriver rd = new RadarDriver();
            rd.name = firstNonEmpty(d.optString("name"), d.optString("username"), "Driver");
            rd.lat = lat;
            rd.lng = lng;
            list.add(rd);
        }
        return list;
    }

    private double nearestDistance(List<RadarDriver> drivers) {
        double best = 999999;
        for (RadarDriver d : drivers) {
            best = Math.min(best, distanceKm(userLat, userLng, d.lat, d.lng));
        }
        return best == 999999 ? 0 : best;
    }

    private void checkOrderStatus() {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("order_id", activeOrderId);
                JSONObject res = postJson(BASE_URL + "server/check_order_status.php", payload);
                if (!res.optBoolean("success", false)) return;

                JSONObject order = res.optJSONObject("order");
                if (order == null) order = new JSONObject();

                String status = firstNonEmpty(
                        order.optString("status", ""),
                        res.optString("status", "")
                ).trim().toLowerCase(Locale.US);

                if (status.equals("canceled") || status.equals("cancelled")) {
                    clearOrderPrefs();
                    mainHandler.post(() -> {
                        destroyLoops();
                        openCustomerDashboard();
                    });
                    return;
                }

                // PENTING: keberadaan driver online / offered_driver / driver_found
                // BUKAN berarti order sudah diterima. Customer tetap di layar pencarian
                // selama status masih pending. UI driver hanya boleh muncul setelah
                // backend benar-benar mengubah status menjadi driver_accepted.
                if (isDriverAcceptedStatus(status)) {
                    mainHandler.post(() -> showDriver(res));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private boolean isDriverAcceptedStatus(String rawStatus) {
        String status = firstNonEmpty(rawStatus, "").trim().toLowerCase(Locale.US);
        return status.equals("driver_accepted")
                || status.equals("arrived_pickup")
                || status.equals("picked_up")
                || status.equals("on_trip")
                || status.equals("on_delivery")
                || status.equals("arrived_delivery");
    }

    private void showDriver(JSONObject data) {
        if (isCanceling || driverFound || destroyed) return;
        driverFound = true;
        destroyLoopsKeepScreen();
        radarView.stopRadar();

        titleText.setText("Driver Menerima Pesanan");
        setSubtitle("Yeay! Driver Anda sudah terhubung dan segera menuju pickup");
        cancelBtn.setVisibility(View.GONE);
        driverCard.setVisibility(View.VISIBLE);

        JSONObject driver = data.optJSONObject("driver");
        if (driver == null) driver = new JSONObject();

        JSONObject order = data.optJSONObject("order");
        if (order == null) order = new JSONObject();

        String driverName = firstNonEmpty(
                driver.optString("name", ""),
                driver.optString("username", ""),
                order.optString("driver_username", ""),
                data.optString("driver_username", ""),
                "Driver"
        );

        String driverPlate = firstNonEmpty(
                driver.optString("plate", ""),
                driver.optString("vehicle_plate", ""),
                driver.optString("plat", ""),
                order.optString("driver_plate", ""),
                data.optString("driver_plate", ""),
                "-"
        );
        double rating = firstPositiveDouble(
                driver.optDouble("rating", 0),
                order.optDouble("driver_rating", 0),
                data.optDouble("driver_rating", 0),
                data.optDouble("rating", 0)
        );
        String driverPhoto = firstNonEmpty(
                driver.optString("driver_photo", ""),
                driver.optString("photo", ""),
                data.optString("driver_photo", "")
        );

        driverNameText.setText(driverName);
        driverPlateText.setText("Plat • " + driverPlate);
        driverRatingText.setText(rating > 0
                ? "⭐ " + String.format(Locale.US, "%.1f", rating)
                : "⭐ Driver baru");
        driverDistanceText.setText("Driver sedang menuju lokasi jemput");
        driverAvatarText.setText(driverName.substring(0, 1).toUpperCase(Locale.US));
        loadDriverPhoto(driverPhoto);

        String driverType = normalizeDriverType(firstNonEmpty(
                order.optString("driver_type", ""),
                order.optString("price_mode", ""),
                data.optString("driver_type", ""),
                data.optString("order_type", ""),
                driver.optString("driver_type", ""),
                getStringPref("active_driver_type"),
                "bike"
        ));

        double driverLat = getJsonDouble(driver, "driver_lat", "lat", "latitude");
        double driverLng = getJsonDouble(driver, "driver_lng", "lng", "longitude");

        double pickupLat = firstValidCoordValue(
                getJsonDouble(order, "pickup_lat", "pickupLatitude", "pickup_latitude"),
                getJsonDouble(data, "pickup_lat", "pickupLatitude", "pickup_latitude"),
                getDoublePref("pickup_lat")
        );
        double pickupLng = firstValidCoordValue(
                getJsonDouble(order, "pickup_lng", "pickupLongitude", "pickup_longitude"),
                getJsonDouble(data, "pickup_lng", "pickupLongitude", "pickup_longitude"),
                getDoublePref("pickup_lng")
        );
        double deliveryLat = firstValidCoordValue(
                getJsonDouble(order, "delivery_lat", "deliveryLatitude", "delivery_latitude"),
                getJsonDouble(data, "delivery_lat", "deliveryLatitude", "delivery_latitude"),
                getDoublePref("delivery_lat")
        );
        double deliveryLng = firstValidCoordValue(
                getJsonDouble(order, "delivery_lng", "deliveryLongitude", "delivery_longitude"),
                getJsonDouble(data, "delivery_lng", "deliveryLongitude", "delivery_longitude"),
                getDoublePref("delivery_lng")
        );

        saveTripPrefs(activeOrderId, driverType, pickupLat, pickupLng, deliveryLat, deliveryLng);
        loadDriverMap(driverName, driverLat, driverLng, pickupLat, pickupLng);

        mainHandler.postDelayed(() -> openNativeTrip(driverType, pickupLat, pickupLng, deliveryLat, deliveryLng), 6000);
    }

    private void openNativeTrip(String driverType, double pickupLat, double pickupLng, double deliveryLat, double deliveryLng) {
        if (isCanceling || destroyed) return;
        try {
            Intent i = new Intent(SearchDriverActivity.this, CustomerTripActivity.class);
            i.putExtra("order_id", activeOrderId);
            i.putExtra("active_order_id", activeOrderId);
            i.putExtra("active_driver_type", driverType);
            if (pickupLat != 0 && pickupLng != 0) {
                i.putExtra("pickup_lat", pickupLat);
                i.putExtra("pickup_lng", pickupLng);
            }
            if (deliveryLat != 0 && deliveryLng != 0) {
                i.putExtra("delivery_lat", deliveryLat);
                i.putExtra("delivery_lng", deliveryLng);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        } catch (Exception e) {
            showInfo("Trip Native", "CustomerTripActivity belum ditemukan. Pastikan file Java dan AndroidManifest sudah ditambahkan.");
        }
    }

    private double firstPositiveDouble(double... values) {
        if (values == null) return 0;
        for (double value : values) if (value > 0) return value;
        return 0;
    }

    private void loadDriverPhoto(String rawUrl) {
        if (driverPhotoView == null) return;
        String value = firstNonEmpty(rawUrl, "").trim();
        if (value.length() == 0) {
            driverPhotoView.setVisibility(View.GONE);
            driverAvatarText.setVisibility(View.VISIBLE);
            return;
        }
        final String photoUrl = value.startsWith("http://") || value.startsWith("https://")
                ? value
                : BASE_URL + (value.startsWith("/") ? value.substring(1) : value);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(photoUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(true);
                conn.connect();
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        mainHandler.post(() -> {
                            if (destroyed || driverPhotoView == null) return;
                            driverPhotoView.setImageBitmap(bitmap);
                            driverPhotoView.setVisibility(View.VISIBLE);
                            driverAvatarText.setVisibility(View.GONE);
                        });
                    }
                }
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    if (driverPhotoView != null) driverPhotoView.setVisibility(View.GONE);
                    if (driverAvatarText != null) driverAvatarText.setVisibility(View.VISIBLE);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void loadDriverMap(String driverName, double driverLat, double driverLng, double pickupLat, double pickupLng) {
        if (driverLat == 0 || driverLng == 0 || pickupLat == 0 || pickupLng == 0) {
            miniMap.loadData("<html><body style='font-family:sans-serif;text-align:center;padding:30px;color:#64748B'>Koordinat driver belum lengkap</body></html>", "text/html", "UTF-8");
            return;
        }

        String tileUrl = CustomerAppSettings.isDarkMode(this)
                ? "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                : "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png";

        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<style>html,body,#map{height:100%;margin:0} .leaflet-control-attribution{display:none}</style></head>" +
                "<body><div id='map'></div><script>" +
                "var map=L.map('map',{zoomControl:false,attributionControl:false});" +
                "L.tileLayer('" + tileUrl + "').addTo(map);" +
                "var d=[" + driverLat + "," + driverLng + "], p=[" + pickupLat + "," + pickupLng + "];" +
                "L.marker(d).addTo(map).bindPopup('" + jsSafe(driverName) + "').openPopup();" +
                "L.marker(p).addTo(map).bindPopup('Lokasi Jemput');" +
                "var line=L.polyline([d,p],{color:'#0B7CFF',weight:5,opacity:.9}).addTo(map);" +
                "map.fitBounds(line.getBounds(),{padding:[30,30]});" +
                "</script></body></html>";

        miniMap.loadDataWithBaseURL("https://transiva.my.id/", html, "text/html", "UTF-8", null);
    }

    private void saveTripPrefs(String orderId, String driverType, double pickupLat, double pickupLng, double deliveryLat, double deliveryLng) {
        try {
            SharedPreferences.Editor e = getSharedPreferences("transiva", MODE_PRIVATE).edit();
            e.putString("active_order_id", firstNonEmpty(orderId, activeOrderId));
            e.putString("active_driver_type", normalizeDriverType(driverType));
            if (pickupLat != 0 && pickupLng != 0) {
                e.putLong("pickup_lat", Double.doubleToLongBits(pickupLat));
                e.putLong("pickup_lng", Double.doubleToLongBits(pickupLng));
                e.putString("pickup_lat_text", String.valueOf(pickupLat));
                e.putString("pickup_lng_text", String.valueOf(pickupLng));
            }
            if (deliveryLat != 0 && deliveryLng != 0) {
                e.putLong("delivery_lat", Double.doubleToLongBits(deliveryLat));
                e.putLong("delivery_lng", Double.doubleToLongBits(deliveryLng));
                e.putString("delivery_lat_text", String.valueOf(deliveryLat));
                e.putString("delivery_lng_text", String.valueOf(deliveryLng));
            }
            e.apply();
        } catch (Exception ignored) {}
    }

    private String normalizeDriverType(String raw) {
        String type = firstNonEmpty(raw, "bike").toLowerCase(Locale.US).trim();
        if (type.equals("transcar") || type.equals("car") || type.equals("mobil")) return "car";
        return "motor";
    }

    private double getJsonDouble(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return 0;
        for (String key : keys) {
            if (key == null || key.length() == 0) continue;
            double value = safeDouble(obj.optString(key, ""));
            if (value != 0) return value;
        }
        return 0;
    }

    private double getDoublePref(String key) {
        try {
            SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
            if (sp.contains(key)) {
                try { return Double.longBitsToDouble(sp.getLong(key, Double.doubleToLongBits(0))); } catch (Exception ignored) {}
                try { return safeDouble(sp.getString(key, "0")); } catch (Exception ignored) {}
            }
            return safeDouble(sp.getString(key + "_text", "0"));
        } catch (Exception ignored) {}
        return 0;
    }

    private double firstValidCoordValue(double... values) {
        if (values == null) return 0;
        for (double v : values) {
            if (v != 0 && Double.isFinite(v)) return v;
        }
        return 0;
    }

    private void confirmCancelOrder() {
        new AlertDialog.Builder(this)
                .setTitle("Batalkan Order")
                .setMessage("Yakin ingin membatalkan pencarian driver?")
                .setNegativeButton("Tidak", null)
                .setPositiveButton("Ya", (d, w) -> cancelOrder())
                .show();
    }

    private void cancelOrder() {
        if (isCanceling) return;
        isCanceling = true;
        destroyLoopsKeepScreen();
        cancelBtn.setEnabled(false);
        cancelBtn.setText("Membatalkan...");
        titleText.setText("Membatalkan Order...");
        setSubtitle("Mohon tunggu sebentar");
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("order_id", activeOrderId);
                JSONObject res = postJson(BASE_URL + "server/cancel_order.php", payload);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Order dibatalkan" : "Order gagal dibatalkan");
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (ok) {
                        clearOrderPrefs();
                        openCustomerDashboard();
                    } else {
                        isCanceling = false;
                        cancelBtn.setEnabled(true);
                        cancelBtn.setText("Batalkan Order");
                        titleText.setText("Mencari Driver...");
                        showInfo("Gagal", msg);
                        startLoops();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isCanceling = false;
                    cancelBtn.setEnabled(true);
                    cancelBtn.setText("Batalkan Order");
                    titleText.setText("Mencari Driver...");
                    showInfo("Koneksi Gagal", "Koneksi gagal saat membatalkan order");
                    startLoops();
                });
            }
        }).start();
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload == null ? "{}" : payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.length() == 0) return new JSONObject();
            return new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private void destroyLoops() {
        destroyed = true;
        mainHandler.removeCallbacks(driverRadarRunnable);
        mainHandler.removeCallbacks(checkOrderRunnable);
    }

    private void destroyLoopsKeepScreen() {
        mainHandler.removeCallbacks(driverRadarRunnable);
        mainHandler.removeCallbacks(checkOrderRunnable);
    }

    @Override protected void onDestroy() {
        destroyLoops();
        try { if (miniMap != null) miniMap.destroy(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean ok = false;
            if (grantResults != null) {
                for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) ok = true;
            }
            if (ok) getUserLocationThenStart();
            else setSubtitle("Izin lokasi ditolak, radar tetap berjalan");
        }
    }

    private int checkSelfPermissionSafe(String permission) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) return checkSelfPermission(permission);
            return PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return PackageManager.PERMISSION_DENIED;
        }
    }

    private void openCustomerDashboard() {
        Intent intent = new Intent(this, CustomerDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void goHomeDelayed(long delay) {
        mainHandler.postDelayed(this::openCustomerDashboard, delay);
    }

    private void clearOrderPrefs() {
        SharedPreferences.Editor e = getSharedPreferences("transiva", MODE_PRIVATE).edit();
        e.remove("active_order_id");
        e.remove("active_order");
        e.remove("order_status");
        e.apply();
    }

    private void saveStringPref(String key, String value) {
        getSharedPreferences("transiva", MODE_PRIVATE).edit().putString(key, value == null ? "" : value).apply();
    }

    private String getStringPref(String key) {
        return getSharedPreferences("transiva", MODE_PRIVATE).getString(key, "");
    }

    private void setSubtitle(String s) {
        subtitleText.setText(s == null ? "" : s);
    }

    private void showInfo(String title, String message) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception ignored) {}
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(radius);
        return gd;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) {
        GradientDrawable gd = round(color, radius);
        gd.setStroke(dp(width), Color.parseColor(stroke));
        return gd;
    }

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
        gd.setCornerRadius(radius);
        return gd;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        }
        return "";
    }

    private double safeDouble(String v) {
        try { return Double.parseDouble(v == null ? "" : v.trim()); } catch (Exception e) { return 0; }
    }

    private String jsSafe(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class RadarDriver {
        String name;
        double lat;
        double lng;
    }

    private class RadarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<RadarDriver> drivers = new ArrayList<>();
        private boolean gpsOk = false;
        private double centerLat = 0;
        private double centerLng = 0;
        private float sweep = 0;
        private boolean running = true;

        public RadarView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            post(tick);
        }

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (running) {
                    sweep += 4;
                    if (sweep >= 360) sweep = 0;
                    invalidate();
                    postDelayed(this, 33);
                }
            }
        };

        public void stopRadar() {
            running = false;
            invalidate();
        }

        public void setDrivers(List<RadarDriver> newDrivers, boolean gpsOk, double lat, double lng) {
            drivers.clear();
            if (newDrivers != null) drivers.addAll(newDrivers);
            this.gpsOk = gpsOk;
            centerLat = lat;
            centerLng = lng;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) / 2f - dp(12);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setShadowLayer(dp(16), 0, dp(8), Color.argb(35, 15, 23, 42));
            canvas.drawCircle(cx, cy, r, paint);
            paint.clearShadowLayer();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.parseColor("#D7E6F8"));
            canvas.drawCircle(cx, cy, r * .35f, paint);
            canvas.drawCircle(cx, cy, r * .62f, paint);
            canvas.drawCircle(cx, cy, r * .88f, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(45, 11, 124, 255));
            RectF arc = new RectF(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(arc, sweep, 34, true, paint);

            paint.setColor(Color.parseColor("#0B7CFF"));
            canvas.drawCircle(cx, cy, dp(32), paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(15));
            canvas.drawText("YOU", cx, cy + dp(5), paint);

            for (int i = 0; i < drivers.size() && i < 8; i++) {
                RadarDriver d = drivers.get(i);
                double dist = gpsOk ? distanceKm(centerLat, centerLng, d.lat, d.lng) : (i + 1);
                double safe = Math.min(dist, 10.0);
                float radius = (float) ((safe / 10.0) * r * .78f);
                if (radius < dp(44)) radius = dp(44);
                double angle = Math.toRadians((i * 67 + dist * 31) % 360);
                float x = (float) (cx + Math.cos(angle) * radius);
                float y = (float) (cy + Math.sin(angle) * radius);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#22C55E"));
                canvas.drawCircle(x, y, dp(8), paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dp(4), paint);

                paint.setColor(Color.parseColor("#0B3A78"));
                paint.setTextSize(dp(10));
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                String label = d.name.length() > 8 ? d.name.substring(0, 8) : d.name;
                canvas.drawText(label, x, y - dp(13), paint);
            }
        }
    }
}