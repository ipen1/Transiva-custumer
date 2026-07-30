package com.transiva.app;

import android.annotation.SuppressLint;
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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import java.util.Locale;

/**
 * Live Driver customer screen rendered with MapLibre GL JS.
 * This screen does not initialize Google Maps, reducing Google Maps requests.
 * Driver location still comes from the existing Transiva status endpoint.
 */
public final class CustomerLiveDriverActivity extends Activity {
    private static final String STATUS_URL =
            "https://transiva.my.id/server/check_order_status.php";
    private static final int TIMEOUT_MS = 25000;
    private static final long POLL_MS = 2000L;
    private static final long ROUTE_REFRESH_MS = 12000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RemoteLocationSmoother smoother = new RemoteLocationSmoother();

    private WebView mapWebView;
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
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readInput();
        buildUi();

        if (orderId.isEmpty()) {
            statusText.setText("Order tidak ditemukan.");
            loading.setVisibility(View.GONE);
            return;
        }

        fetchStatus();
        handler.postDelayed(pollTask, POLL_MS);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void buildUi() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#071426"));

        mapWebView = new WebView(this);
        mapWebView.setBackgroundColor(Color.parseColor("#071426"));
        WebSettings settings = mapWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        mapWebView.setWebChromeClient(new WebChromeClient());
        mapWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                mapReady = true;
                loading.setVisibility(View.GONE);
                renderMap();
            }
        });

        page.addView(mapWebView, new FrameLayout.LayoutParams(-1, -1));
        mapWebView.loadDataWithBaseURL(
                "https://transiva.my.id/",
                createMapHtml(),
                "text/html",
                "UTF-8",
                null
        );

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(Color.WHITE);
        back.setAllCaps(false);
        back.setPadding(0, 0, 0, dp(3));
        back.setBackground(round("#CC071426", dp(24)));
        FrameLayout.LayoutParams backLp =
                new FrameLayout.LayoutParams(dp(48), dp(48));
        backLp.gravity = Gravity.TOP | Gravity.START;
        backLp.setMargins(dp(16), dp(18), 0, 0);
        page.addView(back, backLp);
        back.setOnClickListener(v -> finish());

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14), dp(10), dp(14), dp(10));
        top.setBackground(round("#E60B2038", dp(18)));

        FrameLayout.LayoutParams topLp =
                new FrameLayout.LayoutParams(-1, -2);
        topLp.gravity = Gravity.TOP;
        topLp.setMargins(dp(76), dp(18), dp(16), 0);
        page.addView(top, topLp);

        driverText = text("Live Driver • MapLibre", 15, "#FFFFFF", true);
        statusText = text(
                "Menghubungkan lokasi driver...",
                12,
                "#D7E8FF",
                false
        );
        top.addView(driverText);
        top.addView(statusText);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(14), dp(16), dp(16));
        bottom.setBackground(round("#F20B2038", dp(24)));

        FrameLayout.LayoutParams bottomLp =
                new FrameLayout.LayoutParams(-1, -2);
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
        metrics.addView(
                speedBox,
                new LinearLayout.LayoutParams(dp(126), dp(82))
        );

        speedText = text("0", 34, "#FFFFFF", true);
        speedText.setGravity(Gravity.CENTER);

        TextView kmh = text("KM/JAM", 10, "#8FC5FF", true);
        kmh.setGravity(Gravity.CENTER);

        speedBox.addView(speedText, new LinearLayout.LayoutParams(-1, -2));
        speedBox.addView(kmh, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout routeBox = new LinearLayout(this);
        routeBox.setOrientation(LinearLayout.VERTICAL);
        routeBox.setPadding(dp(14), dp(6), 0, dp(6));
        metrics.addView(
                routeBox,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        TextView routeLabel = text(
                "RUTE AKTIF",
                10,
                "#8FC5FF",
                true
        );
        distanceText = text(
                "Menunggu posisi terbaru",
                15,
                "#FFFFFF",
                true
        );

        routeBox.addView(routeLabel);
        routeBox.addView(distanceText);

        TextView note = text(
                "MapLibre aktif • gerakan kendaraan dianimasikan halus.",
                10,
                "#AFC8E5",
                false
        );
        note.setPadding(0, dp(10), 0, 0);
        bottom.addView(note);

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams loadLp =
                new FrameLayout.LayoutParams(dp(48), dp(48));
        loadLp.gravity = Gravity.CENTER;
        page.addView(loading, loadLp);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private String createMapHtml() {
        boolean dark = CustomerAppSettings.isDarkMode(this);
        String styleUrl = dark
                ? "https://tiles.openfreemap.org/styles/dark"
                : "https://tiles.openfreemap.org/styles/liberty";

        return "<!doctype html><html><head>"
                + "<meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
                + "<link href='https://unpkg.com/maplibre-gl@5.6.1/dist/maplibre-gl.css' rel='stylesheet'>"
                + "<script src='https://unpkg.com/maplibre-gl@5.6.1/dist/maplibre-gl.js'></script>"
                + "<style>"
                + "html,body,#map{width:100%;height:100%;margin:0;padding:0;overflow:hidden;background:#071426}"
                + ".maplibregl-ctrl-bottom-left,.maplibregl-ctrl-bottom-right,"
                + ".maplibregl-ctrl-top-left,.maplibregl-ctrl-top-right{display:none!important}"
                + ".vehicle{width:42px;height:42px;display:flex;align-items:center;justify-content:center;"
                + "border-radius:50%;background:#fff;border:3px solid #1476ff;"
                + "box-shadow:0 4px 16px rgba(0,0,0,.38);font-size:23px;"
                + "will-change:transform;transform-origin:center center}"
                + ".target{width:20px;height:20px;border-radius:50%;background:#fff;"
                + "border:5px solid #1476ff;box-shadow:0 3px 10px rgba(0,0,0,.32)}"
                + "</style></head><body><div id='map'></div>"
                + "<script>"
                + "const map=new maplibregl.Map({container:'map',style:'" + styleUrl + "',"
                + "center:[120.0,-0.9],zoom:15.8,pitch:44,bearing:0,"
                + "attributionControl:false,dragRotate:true,pitchWithRotate:true,"
                + "maxPitch:60,fadeDuration:0});"
                + "let driverMarker=null,pickupMarker=null,deliveryMarker=null;"
                + "let currentLng=0,currentLat=0,targetLng=0,targetLat=0;"
                + "let currentBearing=0,targetBearing=0,animStart=0,animDuration=1800;"
                + "let follow=true,vehicleType='motor';"
                + "function angleDelta(a,b){return ((b-a+540)%360)-180}"
                + "function vehicleEl(type){const e=document.createElement('div');"
                + "e.className='vehicle';e.textContent=type==='car'?'🚘':'🏍️';return e}"
                + "function targetEl(){const e=document.createElement('div');e.className='target';return e}"
                + "function ensureDriver(type){"
                + "if(driverMarker&&vehicleType===type)return;"
                + "vehicleType=type;"
                + "if(driverMarker)driverMarker.remove();"
                + "driverMarker=new maplibregl.Marker({element:vehicleEl(type),anchor:'center',rotationAlignment:'map'});"
                + "if(currentLng&&currentLat)driverMarker.setLngLat([currentLng,currentLat]).addTo(map)}"
                + "function setTarget(kind,lng,lat){"
                + "if(!lng||!lat)return;"
                + "let m=kind==='pickup'?pickupMarker:deliveryMarker;"
                + "if(!m){m=new maplibregl.Marker({element:targetEl(),anchor:'center'}).setLngLat([lng,lat]).addTo(map);"
                + "if(kind==='pickup')pickupMarker=m;else deliveryMarker=m}else m.setLngLat([lng,lat])}"
                + "function updateDriver(lng,lat,bearing,type){"
                + "if(!lng||!lat)return;"
                + "ensureDriver(type);"
                + "if(!currentLng||!currentLat){currentLng=lng;currentLat=lat;targetLng=lng;targetLat=lat;"
                + "currentBearing=bearing||0;targetBearing=currentBearing;"
                + "driverMarker.setLngLat([lng,lat]).addTo(map);"
                + "map.jumpTo({center:[lng,lat],zoom:17.1,pitch:46,bearing:currentBearing});return}"
                + "targetLng=lng;targetLat=lat;targetBearing=bearing||targetBearing;"
                + "animStart=performance.now();requestAnimationFrame(tick)}"
                + "let ticking=false;"
                + "function tick(now){if(ticking)return;ticking=true;"
                + "requestAnimationFrame(function frame(t){"
                + "let p=Math.min(1,(t-animStart)/animDuration);"
                + "let eased=1-Math.pow(1-p,3);"
                + "currentLng=currentLng+(targetLng-currentLng)*Math.min(.16+.34*eased,.46);"
                + "currentLat=currentLat+(targetLat-currentLat)*Math.min(.16+.34*eased,.46);"
                + "currentBearing=(currentBearing+angleDelta(currentBearing,targetBearing)*.13+360)%360;"
                + "if(driverMarker){driverMarker.setLngLat([currentLng,currentLat]);"
                + "driverMarker.setRotation(currentBearing)}"
                + "if(follow){map.easeTo({center:[currentLng,currentLat],bearing:currentBearing,"
                + "zoom:17.1,pitch:46,duration:90,easing:x=>x})}"
                + "if(p<1||Math.abs(targetLng-currentLng)>.000001||Math.abs(targetLat-currentLat)>.000001)"
                + "requestAnimationFrame(frame);else ticking=false;"
                + "});}"
                + "function drawRoute(points){"
                + "if(!Array.isArray(points)||points.length<2)return;"
                + "const coords=points.map(p=>[Number(p[1]),Number(p[0])]);"
                + "const data={type:'Feature',properties:{},geometry:{type:'LineString',coordinates:coords}};"
                + "if(map.getSource('route'))map.getSource('route').setData(data);"
                + "else{map.addSource('route',{type:'geojson',data:data});"
                + "map.addLayer({id:'route-shadow',type:'line',source:'route',layout:{'line-join':'round','line-cap':'round'},"
                + "paint:{'line-color':'#071426','line-width':11,'line-opacity':.58}});"
                + "map.addLayer({id:'route',type:'line',source:'route',layout:{'line-join':'round','line-cap':'round'},"
                + "paint:{'line-color':'#1476ff','line-width':7,'line-opacity':.96}})}}"
                + "map.on('dragstart',()=>follow=false);"
                + "map.on('zoomstart',()=>follow=false);"
                + "map.on('load',()=>{follow=true;});"
                + "window.TransivaLive={updateDriver,setTarget,drawRoute,setFollow:(v)=>{follow=!!v}};"
                + "</script></body></html>";
    }

    private void fetchStatus() {
        if (requestInFlight || orderId.isEmpty()) return;
        requestInFlight = true;

        new Thread(() -> {
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
        }, "transiva-live-driver-maplibre").start();
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
                        + " • MapLibre Live"
        );

        statusText.setText(statusLabel());
        speedText.setText(String.valueOf(
                Math.max(0, (int) Math.round(speedKmh))
        ));

        updateDistanceText();
        renderMap();
    }

    private void renderMap() {
        if (!mapReady || mapWebView == null) return;

        if (valid(pickupLat, pickupLng)) {
            evaluate("window.TransivaLive&&TransivaLive.setTarget('pickup',"
                    + pickupLng + "," + pickupLat + ");");
        }

        if (valid(deliveryLat, deliveryLng)) {
            evaluate("window.TransivaLive&&TransivaLive.setTarget('delivery',"
                    + deliveryLng + "," + deliveryLat + ");");
        }

        if (valid(driverLat, driverLng)) {
            evaluate("window.TransivaLive&&TransivaLive.updateDriver("
                    + driverLng + ","
                    + driverLat + ","
                    + bearing + ",'"
                    + ("car".equals(driverType) ? "car" : "motor")
                    + "');");

            requestRouteIfNeeded();
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

        new Thread(() -> {
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
                    evaluate("window.TransivaLive&&TransivaLive.drawRoute("
                            + result.pointsJson()
                            + ");");

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
        }, "transiva-live-route-maplibre").start();
    }

    private void evaluate(String script) {
        if (mapWebView == null || !mapReady) return;
        mapWebView.evaluateJavascript(script, null);
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
            return driverName + " sudah tiba di lokasi pickup";
        }
        if ("on_delivery".equals(status)) {
            return driverName + " sedang menuju lokasi tujuan";
        }
        if ("arrived_delivery".equals(status)) {
            return driverName + " sudah tiba di lokasi tujuan";
        }
        if (ended(status)) {
            return "Perjalanan telah selesai";
        }
        return driverName + " sedang menuju lokasi pickup";
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

    private JSONObject postJson(
            String urlText,
            JSONObject payload
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)
                    new URL(urlText).openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            OutputStream os = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            os,
                            StandardCharsets.UTF_8
                    )
            );

            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = connection.getResponseCode();

            InputStream stream =
                    code >= 200 && code < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String body = read(stream).trim();

            return body.isEmpty()
                    ? new JSONObject()
                    : new JSONObject(body);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "";

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8
                )
        );

        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();
        return result.toString();
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

    @Override protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);

        if (mapWebView != null) {
            mapWebView.onResume();
        }

        handler.removeCallbacks(pollTask);

        if (!orderId.isEmpty()) {
            handler.postDelayed(pollTask, 300L);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(pollTask);

        if (mapWebView != null) {
            mapWebView.onPause();
        }

        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (mapWebView != null) {
            mapWebView.stopLoading();
            mapWebView.loadUrl("about:blank");
            mapWebView.clearHistory();
            mapWebView.removeAllViews();
            mapWebView.destroy();
            mapWebView = null;
        }

        super.onDestroy();
    }
}
