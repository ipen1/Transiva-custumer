package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Live Driver customer screen rendered with the shared native Google Map runtime.
 * No CDN/JavaScript/WebView is required for active-order tracking. Driver location still
 * comes from the existing Transiva status endpoint and the standard order status flow.
 */
public final class CustomerLiveDriverActivity extends Activity {
    private static final String STATUS_URL =
            "https://transiva.my.id/server/check_order_status.php";
    private static final int TIMEOUT_MS = 25000;
    private static final long POLL_MS = 5000L;
    private static final long ROUTE_REFRESH_MS = 15000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RemoteLocationSmoother smoother = new RemoteLocationSmoother();

    private TransivaGoogleMapView mapView;
    private final CustomerFeatureRuntimeController featureRuntime =
            new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.TRIP);
    private TextView speedText;
    private TextView statusText;
    private TextView distanceText;
    private TextView driverText;
    private ProgressBar loading;

    private String orderId = "";
    private String driverType = "motor";
    private String status = "";
    private String driverName = "Driver";

    private double pickupLat, pickupLng;
    private double deliveryLat, deliveryLng;
    private double driverLat, driverLng;
    private double bearing, speedKmh;
    private double previousRawLat, previousRawLng;
    private long previousRawAt;

    private boolean mapReady;
    private boolean requestInFlight;
    private boolean routeInFlight;
    private long lastRouteAt;
    private double lastRouteFromLat, lastRouteFromLng;
    private double lastRouteToLat, lastRouteToLng;

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            fetchStatus();
            handler.postDelayed(this, CustomerPerformanceManager.pollingCritical(CustomerLiveDriverActivity.this, POLL_MS));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readInput();
        buildUi(state);

        if (orderId.isEmpty()) {
            statusText.setText("Order tidak ditemukan.");
            loading.setVisibility(View.GONE);
            return;
        }

        fetchStatus();
        handler.postDelayed(pollTask, CustomerPerformanceManager.pollingCritical(this, POLL_MS));
    }

    private void readInput() {
        Intent i = getIntent();
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);

        orderId = firstNonEmpty(
                i == null ? "" : i.getStringExtra("order_id"),
                sp.getString("active_order_id", "")
        );

        driverType = firstNonEmpty(
                i == null ? "" : i.getStringExtra("active_driver_type"),
                sp.getString("active_driver_type", "motor")
        );

        if (!"car".equalsIgnoreCase(driverType)) driverType = "motor";

        pickupLat = readCoord(i, sp, "pickup_lat");
        pickupLng = readCoord(i, sp, "pickup_lng");
        deliveryLat = readCoord(i, sp, "delivery_lat");
        deliveryLng = readCoord(i, sp, "delivery_lng");
    }

    private void buildUi(Bundle state) {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#071426"));

        mapView = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.TRIP);
        mapView.initialize(state, new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat, double lng) {
                mapReady = true;
                if (loading != null) loading.setVisibility(View.GONE);
                renderMap();
            }
            @Override public void onCenterChanged(double lat, double lng) { }
        });
        page.addView(mapView, new FrameLayout.LayoutParams(-1, -1));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(Color.WHITE);
        back.setAllCaps(false);
        back.setPadding(0, 0, 0, dp(3));
        back.setBackground(round("#CC071426", dp(24)));
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        backLp.gravity = Gravity.TOP | Gravity.START;
        backLp.setMargins(dp(16), dp(18), 0, 0);
        page.addView(back, backLp);
        back.setOnClickListener(v -> finish());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(10));
        top.setBackground(round("#E60B2038", dp(18)));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2);
        topLp.gravity = Gravity.TOP;
        topLp.setMargins(dp(76), dp(18), dp(16), 0);
        page.addView(top, topLp);

        driverText = text("Live Driver • Native Map", 15, "#FFFFFF", true);
        statusText = text("Menghubungkan lokasi driver...", 12, "#D7E8FF", false);
        top.addView(driverText);
        top.addView(statusText);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(14), dp(16), dp(16));
        bottom.setBackground(round("#F20B2038", dp(24)));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2);
        bottomLp.gravity = Gravity.BOTTOM;
        bottomLp.setMargins(dp(14), 0, dp(14), dp(18));
        page.addView(bottom, bottomLp);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(metrics, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout speedBox = new LinearLayout(this);
        speedBox.setOrientation(LinearLayout.VERTICAL);
        speedBox.setGravity(Gravity.CENTER);
        speedBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        speedBox.setBackground(round("#162D4A", dp(18)));
        metrics.addView(speedBox, new LinearLayout.LayoutParams(dp(126), dp(82)));

        speedText = text("0", 34, "#FFFFFF", true);
        speedText.setGravity(Gravity.CENTER);
        TextView kmh = text("KM/JAM", 10, "#8FC5FF", true);
        kmh.setGravity(Gravity.CENTER);
        speedBox.addView(speedText, new LinearLayout.LayoutParams(-1, -2));
        speedBox.addView(kmh, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout routeBox = new LinearLayout(this);
        routeBox.setOrientation(LinearLayout.VERTICAL);
        routeBox.setPadding(dp(14), dp(6), 0, dp(6));
        metrics.addView(routeBox, new LinearLayout.LayoutParams(0, -2, 1));
        routeBox.addView(text("RUTE AKTIF", 10, "#8FC5FF", true));
        distanceText = text("Menunggu posisi terbaru", 15, "#FFFFFF", true);
        routeBox.addView(distanceText);

        TextView note = text("Posisi diperbarui otomatis. Peta native tetap digunakan saat CDN eksternal bermasalah.", 11, "#B8CEE8", false);
        note.setPadding(0, dp(10), 0, 0);
        bottom.addView(note);

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(dp(42), dp(42));
        loadingLp.gravity = Gravity.CENTER;
        page.addView(loading, loadingLp);

        setContentView(page);
    }

    private void fetchStatus() {
        if (requestInFlight || orderId.isEmpty()) return;
        requestInFlight = true;

        featureRuntime.execute(() -> {
            try {
                JSONObject result = postJson(
                        STATUS_URL,
                        new JSONObject().put("order_id", orderId)
                );
                handler.post(() -> applyStatus(result));
            } catch (Exception e) {
                handler.post(() ->
                        statusText.setText(
                                "Koneksi tracking belum stabil. Mencoba lagi..."
                        )
                );
            } finally {
                requestInFlight = false;
                handler.post(() -> {
                    if (loading != null) loading.setVisibility(View.GONE);
                });
            }
        });
    }

    private void applyStatus(JSONObject res) {
        if (!res.optBoolean("success", false)) {
            statusText.setText(firstNonEmpty(
                    res.optString("message", ""),
                    "Menunggu status order..."
            ));
            return;
        }

        JSONObject order = res.optJSONObject("order");
        JSONObject driver = res.optJSONObject("driver");
        if (order == null) order = new JSONObject();
        if (driver == null) driver = new JSONObject();

        status = firstNonEmpty(
                res.optString("status", ""),
                order.optString("status", "")
        ).toLowerCase(Locale.US).trim();

        driverName = firstNonEmpty(
                driver.optString("name", ""),
                driver.optString("username", ""),
                order.optString("driver_username", ""),
                "Driver"
        );

        driverType = resolveDriverType(order, driver);

        pickupLat = firstCoord(
                jsonDouble(order, "pickup_lat", "pickupLatitude",
                        "pickup_latitude", "customer_lat"),
                jsonDouble(res, "pickup_lat", "pickupLatitude",
                        "pickup_latitude"),
                pickupLat
        );

        pickupLng = firstCoord(
                jsonDouble(order, "pickup_lng", "pickupLongitude",
                        "pickup_longitude", "customer_lng"),
                jsonDouble(res, "pickup_lng", "pickupLongitude",
                        "pickup_longitude"),
                pickupLng
        );

        deliveryLat = firstCoord(
                jsonDouble(order, "delivery_lat", "deliveryLatitude",
                        "delivery_latitude", "destination_lat"),
                jsonDouble(res, "delivery_lat", "deliveryLatitude",
                        "delivery_latitude"),
                deliveryLat
        );

        deliveryLng = firstCoord(
                jsonDouble(order, "delivery_lng", "deliveryLongitude",
                        "delivery_longitude", "destination_lng"),
                jsonDouble(res, "delivery_lng", "deliveryLongitude",
                        "delivery_longitude"),
                deliveryLng
        );

        double rawLat = firstCoord(
                jsonDouble(driver, "driver_lat", "latitude", "lat"),
                jsonDouble(order, "driver_lat", "latitude", "lat"),
                jsonDouble(res, "driver_lat", "latitude", "lat")
        );

        double rawLng = firstCoord(
                jsonDouble(driver, "driver_lng", "driver_lon",
                        "longitude", "lng", "lon"),
                jsonDouble(order, "driver_lng", "driver_lon",
                        "longitude", "lng", "lon"),
                jsonDouble(res, "driver_lng", "driver_lon",
                        "longitude", "lng", "lon")
        );

        double serverSpeed = firstPositive(
                jsonDouble(driver, "speed_kmh", "speedKmh"),
                jsonDouble(order, "speed_kmh", "speedKmh"),
                jsonDouble(res, "speed_kmh", "speedKmh")
        );

        double speedMps = firstPositive(
                jsonDouble(driver, "speed_mps", "speed"),
                jsonDouble(order, "speed_mps", "speed"),
                jsonDouble(res, "speed_mps", "speed")
        );

        if (serverSpeed <= 0 && speedMps > 0) {
            serverSpeed = speedMps * 3.6d;
        }

        if (valid(rawLat, rawLng)) {
            long now = System.currentTimeMillis();
            double measured = 0d;

            if (valid(previousRawLat, previousRawLng)
                    && previousRawAt > 0
                    && now > previousRawAt) {

                float meters = distance(
                        previousRawLat,
                        previousRawLng,
                        rawLat,
                        rawLng
                );

                double seconds = (now - previousRawAt) / 1000d;

                if (seconds > 0.25d && meters < 250d) {
                    measured = meters / seconds * 3.6d;
                }

                if (meters > 1.5f) {
                    bearing = calcBearing(
                            previousRawLat,
                            previousRawLng,
                            rawLat,
                            rawLng
                    );
                }
            } else {
                double[] target = target();
                bearing = calcBearing(
                        rawLat,
                        rawLng,
                        target[0],
                        target[1]
                );
            }

            previousRawLat = rawLat;
            previousRawLng = rawLng;
            previousRawAt = now;

            double candidate =
                    serverSpeed > 0 ? serverSpeed : measured;

            if (candidate < 1.5d) candidate = 0d;
            candidate = Math.min(candidate, 180d);

            speedKmh = speedKmh == 0d
                    ? candidate
                    : speedKmh * 0.68d + candidate * 0.32d;

            RemoteLocationSmoother.Point point =
                    smoother.offer(rawLat, rawLng, bearing);

            if (point != null) {
                driverLat = point.lat;
                driverLng = point.lng;
                bearing = smoothAngle(
                        bearing,
                        point.bearing,
                        0.45d
                );
            }
        }

        driverText.setText(
                ("car".equals(driverType) ? "🚘 " : "🏍️ ")
                        + driverName
                        + " • Native Live"
        );

        statusText.setText(statusLabel());
        speedText.setText(String.valueOf(
                Math.max(0, (int) Math.round(speedKmh))
        ));

        updateDistanceText();
        renderMap();
    }

    private void renderMap() {
        if (!mapReady || mapView == null) return;
        if (valid(pickupLat, pickupLng)) mapView.setPickup(pickupLat, pickupLng, "Lokasi jemput");
        if (valid(deliveryLat, deliveryLng)) mapView.setDelivery(deliveryLat, deliveryLng, "Lokasi tujuan");
        if (valid(driverLat, driverLng)) {
            mapView.setTripDriver(driverLat, driverLng, bearing, "car".equals(driverType), driverName, status);
            mapView.moveTo(driverLat, driverLng, 17f);
            requestRouteIfNeeded();
        } else if (valid(pickupLat, pickupLng)) {
            mapView.moveTo(pickupLat, pickupLng, 16f);
        }
    }

    private void requestRouteIfNeeded() {
        double[] target = target();

        if (routeInFlight
                || !valid(driverLat, driverLng)
                || !valid(target[0], target[1])) {
            return;
        }

        long now = System.currentTimeMillis();

        float movedFrom = distance(
                lastRouteFromLat,
                lastRouteFromLng,
                driverLat,
                driverLng
        );

        float movedTo = distance(
                lastRouteToLat,
                lastRouteToLng,
                target[0],
                target[1]
        );

        if (now - lastRouteAt < ROUTE_REFRESH_MS
                && movedFrom < 30f
                && movedTo < 10f) {
            return;
        }

        routeInFlight = true;

        final double fromLat = driverLat;
        final double fromLng = driverLng;
        final double toLat = target[0];
        final double toLng = target[1];

        featureRuntime.execute(() -> {
            try {
                StableRouteEngine.Result result =
                        StableRouteEngine.fetch(
                                fromLat,
                                fromLng,
                                toLat,
                                toLng
                        );

                lastRouteAt = System.currentTimeMillis();
                lastRouteFromLat = fromLat;
                lastRouteFromLng = fromLng;
                lastRouteToLat = toLat;
                lastRouteToLng = toLng;

                handler.post(() -> {
                    if (mapView != null && mapReady) mapView.drawOsrmRoute(result.latLngPoints, status);
                    double km = result.distanceMeters / 1000d;
                    int min = Math.max(
                            1,
                            (int) Math.ceil(
                                    result.durationSeconds / 60d
                            )
                    );

                    distanceText.setText(String.format(
                            Locale.US,
                            "%.1f km • ±%d menit",
                            km,
                            min
                    ));
                });
            } catch (Exception ignored) {
            } finally {
                routeInFlight = false;
            }
        });
    }


    private void updateDistanceText() {
        double[] target = target();

        if (valid(driverLat, driverLng)
                && valid(target[0], target[1])) {

            float meters = distance(
                    driverLat,
                    driverLng,
                    target[0],
                    target[1]
            );

            if (meters < 1000f) {
                distanceText.setText(
                        Math.max(0, Math.round(meters))
                                + " meter menuju target"
                );
            } else {
                distanceText.setText(String.format(
                        Locale.US,
                        "%.1f km menuju target",
                        meters / 1000d
                ));
            }
        }
    }

    private double[] target() {
        if (("on_delivery".equals(status)
                || "arrived_delivery".equals(status)
                || ended(status))
                && valid(deliveryLat, deliveryLng)) {
            return new double[]{deliveryLat, deliveryLng};
        }

        return new double[]{pickupLat, pickupLng};
    }

    private String statusLabel() {
        if ("arrived_pickup".equals(status)) {
            return driverName + " sudah tiba di titik penjemputan";
        }
        if ("on_delivery".equals(status)) {
            return driverName + " sedang menuju titik pengantaran";
        }
        if ("arrived_delivery".equals(status)) {
            return driverName + " sudah tiba di titik pengantaran";
        }
        if (ended(status)) {
            return "Perjalanan telah selesai";
        }
        return driverName + " sedang menuju titik penjemputan";
    }

    private String resolveDriverType(
            JSONObject order,
            JSONObject driver
    ) {
        String value = firstNonEmpty(
                order.optString("driver_type", ""),
                order.optString("price_mode", ""),
                driver.optString("vehicle_type", ""),
                driverType,
                "motor"
        ).toLowerCase(Locale.US);

        return "car".equals(value) || "mobil".equals(value)
                ? "car"
                : "motor";
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        return TransivaHttpRepository.postJson(this, urlText, payload, TIMEOUT_MS);
    }

    private double readCoord(
            Intent intent,
            SharedPreferences preferences,
            String key
    ) {
        try {
            if (intent != null && intent.hasExtra(key)) {
                Object value = intent.getExtras() == null
                        ? null
                        : intent.getExtras().get(key);

                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }

                if (value != null) {
                    return Double.parseDouble(
                            String.valueOf(value)
                    );
                }
            }
        } catch (Exception ignored) {}

        try {
            return Double.parseDouble(
                    preferences.getString(key, "0")
            );
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private double jsonDouble(
            JSONObject obj,
            String... keys
    ) {
        if (obj == null) return 0d;

        for (String key : keys) {
            try {
                if (!obj.has(key) || obj.isNull(key)) continue;

                Object value = obj.opt(key);

                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }

                String text = String.valueOf(value).trim();

                if (!text.isEmpty()
                        && !"null".equalsIgnoreCase(text)) {
                    return Double.parseDouble(text);
                }
            } catch (Exception ignored) {}
        }

        return 0d;
    }

    private static double firstCoord(double... values) {
        for (double value : values) {
            if (Double.isFinite(value) && value != 0d) {
                return value;
            }
        }
        return 0d;
    }

    private static double firstPositive(double... values) {
        for (double value : values) {
            if (Double.isFinite(value) && value > 0d) {
                return value;
            }
        }
        return 0d;
    }

    private static boolean valid(double lat, double lng) {
        return Double.isFinite(lat)
                && Double.isFinite(lng)
                && lat != 0d
                && lng != 0d
                && Math.abs(lat) <= 90d
                && Math.abs(lng) <= 180d;
    }

    private static float distance(
            double fromLat,
            double fromLng,
            double toLat,
            double toLng
    ) {
        if (!valid(fromLat, fromLng)
                || !valid(toLat, toLng)) {
            return 999999f;
        }

        float[] result = new float[1];

        Location.distanceBetween(
                fromLat,
                fromLng,
                toLat,
                toLng,
                result
        );

        return result[0];
    }

    private static double calcBearing(
            double fromLat,
            double fromLng,
            double toLat,
            double toLng
    ) {
        if (!valid(fromLat, fromLng)
                || !valid(toLat, toLng)) {
            return 0d;
        }

        double deltaLng =
                Math.toRadians(toLng - fromLng);

        double fromLatRad =
                Math.toRadians(fromLat);

        double toLatRad =
                Math.toRadians(toLat);

        double y =
                Math.sin(deltaLng)
                        * Math.cos(toLatRad);

        double x =
                Math.cos(fromLatRad)
                        * Math.sin(toLatRad)
                        - Math.sin(fromLatRad)
                        * Math.cos(toLatRad)
                        * Math.cos(deltaLng);

        return normalize(
                Math.toDegrees(Math.atan2(y, x))
        );
    }

    private static double smoothAngle(
            double from,
            double to,
            double alpha
    ) {
        from = normalize(from);
        to = normalize(to);

        double delta =
                ((to - from + 540d) % 360d) - 180d;

        return normalize(from + delta * alpha);
    }

    private static double normalize(double value) {
        if (!Double.isFinite(value)) return 0d;

        value %= 360d;

        return value < 0d
                ? value + 360d
                : value;
    }

    private static boolean ended(String value) {
        String s = value == null
                ? ""
                : value.trim().toLowerCase(Locale.US);

        return "finished".equals(s)
                || "completed".equals(s)
                || "complete".equals(s)
                || "done".equals(s)
                || "cancelled".equals(s)
                || "canceled".equals(s);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null
                    && !value.trim().isEmpty()
                    && !"null".equalsIgnoreCase(
                            value.trim()
                    )) {
                return value.trim();
            }
        }

        return "";
    }

    private TextView text(
            String value,
            int sizeSp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(Color.parseColor(color));

        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return view;
    }

    private GradientDrawable round(
            String color,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(radius);

        return drawable;
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStartMap();
    }

    @Override protected void onStop() {
        if (mapView != null) mapView.onStopMap();
        super.onStop();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemoryMap();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (mapView != null) mapView.onSaveInstanceStateMap(outState);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);

        featureRuntime.onResume();
        if (mapView != null) mapView.onResumeMap();

        handler.removeCallbacks(pollTask);

        if (!orderId.isEmpty()) {
            handler.postDelayed(pollTask, 300L);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(pollTask);

        featureRuntime.onPause();
        if (mapView != null) mapView.onPauseMap();

        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        featureRuntime.destroy();
        if (mapView != null) {
            mapView.onDestroyMap();
            mapView = null;
        }

        super.onDestroy();
    }
}
