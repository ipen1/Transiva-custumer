package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

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
import android.util.Base64;

public class CustomerTripActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String CHECK_STATUS_URL = BASE_URL + "server/check_order_status.php";

    // Pakai file Leaflet lokal/server sendiri, bukan CDN, agar stabil di WebView.
    private static final String LEAFLET_CSS = BASE_URL + "js/leaflet.css";
    private static final String LEAFLET_JS  = BASE_URL + "js/leaflet.js";

    private static final int TIMEOUT_MS = 25000;
    private static final long TRACKING_MS = 3000;
    private static final long MAP_FALLBACK_READY_MS = 2500;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable trackingRunnable = new Runnable() {
        @Override public void run() {
            fetchDriverPosition();
            mainHandler.postDelayed(this, TRACKING_MS);
        }
    };

    private WebView mapView;
    private TextView statusText;
    private TextView driverNameText;
    private TextView driverTypeText;
    private TextView driverPlateText;
    private TextView tripInfoText;
    private ImageView driverPhotoView;
    private ProgressBar progressBar;

    private String orderId = "";
    private String activeDriverType = "motor";

    private boolean mapReady = false;
    private boolean firstFocus = true;
    private boolean trackingStarted = false;
    private boolean finishedCountdownStarted = false;
    private boolean lastDataAlreadyPushed = false;

    private int finishSeconds = 5;

    private double pickupLat = 0;
    private double pickupLng = 0;
    private double deliveryLat = 0;
    private double deliveryLng = 0;
    private double lastDriverLat = 0;
    private final RemoteLocationSmoother remoteLocationSmoother = new RemoteLocationSmoother();
    private double lastDriverLng = 0;
    private double lastBearing = 0;

    private String lastDriverName = "Driver";
    private String lastStatus = "";
    private volatile boolean routeRequestInFlight = false;
    private long lastRouteRequestAt = 0L;
    private double lastRouteFromLat = 0d, lastRouteFromLng = 0d;
    private double lastRouteToLat = 0d, lastRouteToLng = 0d;
    private String lastRouteStatus = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(Color.parseColor("#071426"));
            getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readIntentAndSavedData();
        buildLayout();

        if (orderId.length() == 0) {
            showInfo("Order tidak ditemukan", "ID order tidak ditemukan. Silakan ulangi order.");
            return;
        }

        if (!validCoord(pickupLat, pickupLng)) {
            statusText.setText("Mengambil data lokasi order...");
        }

        // FIX PENTING:
        // Tracking langsung jalan, tidak menunggu mapReady.
        // Kalau Leaflet lambat/gagal, data driver tetap diambil dari server.
        startTrackingOnce();

        // Fallback agar WebView tetap mencoba render marker walaupun callback JS lambat.
        mainHandler.postDelayed(() -> {
            if (!mapReady && mapView != null) {
                mapReady = true;
                pushAllMarkersToMap();
            }
        }, MAP_FALLBACK_READY_MS);
    }

    private void readIntentAndSavedData() {
        SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
        Intent i = getIntent();

        orderId = firstNonEmpty(
                i.getStringExtra("order_id"),
                i.getStringExtra("active_order_id"),
                sp.getString("active_order_id", "")
        );

        pickupLat = getDoubleExtraOrPref(i, sp, "pickup_lat", 0);
        pickupLng = getDoubleExtraOrPref(i, sp, "pickup_lng", 0);
        deliveryLat = getDoubleExtraOrPref(i, sp, "delivery_lat", 0);
        deliveryLng = getDoubleExtraOrPref(i, sp, "delivery_lng", 0);

        activeDriverType = firstNonEmpty(
                i.getStringExtra("active_driver_type"),
                sp.getString("active_driver_type", "motor"),
                "motor"
        );

        if (!"car".equals(activeDriverType)) activeDriverType = "motor";
    }

    private double getDoubleExtraOrPref(Intent i, SharedPreferences sp, String key, double def) {
        try {
            if (i != null && i.hasExtra(key)) {
                Object raw = i.getExtras() != null ? i.getExtras().get(key) : null;
                if (raw instanceof Number) return ((Number) raw).doubleValue();
                if (raw != null) {
                    String v = String.valueOf(raw).trim();
                    if (v.length() > 0 && !"null".equalsIgnoreCase(v)) return Double.parseDouble(v);
                }
            }
        } catch (Exception ignored) {}

        try {
            String v = sp.getString(key, "");
            if (v != null && v.trim().length() > 0 && !"null".equalsIgnoreCase(v.trim())) {
                return Double.parseDouble(v.trim());
            }
        } catch (Exception ignored) {}

        return def;
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(12));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundStroke("#FAFCFF", "#D7E6F8", dp(24), 1));
        card.setElevation(dp(7));
        root.addView(card, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Driver Ditemukan", 20, "#0B3A78", true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button close = smallButton("×", "#FEE2E2", "#DC2626", "#FECACA");
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        close.setOnClickListener(v -> finish());

        LinearLayout driverBox = new LinearLayout(this);
        driverBox.setOrientation(LinearLayout.HORIZONTAL);
        driverBox.setGravity(Gravity.CENTER_VERTICAL);
        driverBox.setPadding(dp(10), dp(10), dp(10), dp(10));
        driverBox.setBackground(roundStroke("#FFFFFF", "#E2E8F0", dp(18), 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(0, dp(12), 0, 0);
        card.addView(driverBox, dlp);

        driverPhotoView = new ImageView(this);
        driverPhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        driverPhotoView.setImageResource(android.R.drawable.ic_menu_myplaces);
        driverPhotoView.setBackground(round("#EAF4FF", dp(26)));
        driverBox.addView(driverPhotoView, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1);
        ilp.setMargins(dp(10), 0, 0, 0);
        driverBox.addView(info, ilp);

        driverNameText = text("Driver", 15, "#0B3A78", true);
        driverTypeText = text("🏍️ Motor / Bike", 12, "#2563EB", true);
        driverPlateText = text("🔢 Plat: -", 12, "#64748B", false);
        info.addView(driverNameText);
        info.addView(driverTypeText);
        info.addView(driverPlateText);

        statusText = text("Menghubungkan lokasi driver...", 13, "#334155", true);
        statusText.setPadding(dp(4), dp(10), dp(4), dp(8));
        card.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        mapView = new WebView(this);
        mapView.setBackgroundColor(Color.parseColor("#EAF4FF"));
        mapView.setVerticalScrollBarEnabled(false);
        mapView.setHorizontalScrollBarEnabled(false);
        mapView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = mapView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        mapView.setWebChromeClient(new WebChromeClient());
        mapView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                mainHandler.postDelayed(() -> {
                    if (!mapReady) {
                        mapReady = true;
                        pushAllMarkersToMap();
                    }
                }, 1200);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // Jangan hentikan tracking. Map bisa fallback saat JS siap.
            }
        });

        mapView.addJavascriptInterface(new TripBridge(), "AndroidTrip");

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1);
        mlp.setMargins(0, dp(4), 0, dp(10));
        card.addView(mapView, mlp);
        mapView.loadDataWithBaseURL(BASE_URL, mapHtml(), "text/html", "UTF-8", null);

        tripInfoText = text("Menyiapkan rute perjalanan...", 12, "#64748B", false);
        tripInfoText.setPadding(dp(4), 0, dp(4), dp(8));
        card.addView(tripInfoText, new LinearLayout.LayoutParams(-1, -2));

        Button backBtn = outlineButton("Kembali");
        card.addView(backBtn, new LinearLayout.LayoutParams(-1, dp(48)));
        backBtn.setOnClickListener(v -> finish());

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(54), dp(54));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private String mapHtml() {
        String carIcon = drawableDataUri("map_car_top", "ic_car_top", "car_top", "ic_transcar", "transcar", "car", "transcar_marker");
        String bikeIcon = drawableDataUri("map_motor_top", "ic_motor_top", "motor_top", "ic_transbike", "transbike", "motor", "bike_marker");

        return "<!DOCTYPE html><html><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='" + LEAFLET_CSS + "?v=route_snap_1'>" +
                "<script src='" + LEAFLET_JS + "?v=route_snap_1'></script>" +
                "<style>" +
                "html,body,#map{height:100%;width:100%;margin:0;padding:0;background:#eaf4ff;overflow:hidden;}" +
                ".leaflet-container{height:100%;width:100%;font-family:Arial,sans-serif;background:#eaf4ff;border-radius:18px;}" +
                ".leaflet-control-attribution,.leaflet-control-zoom{display:none!important;}" +
                ".leaflet-tile-pane{filter:saturate(.92) contrast(.98) brightness(1.03);}" +
                ".pin{width:40px;height:40px;border-radius:20px;display:flex;align-items:center;justify-content:center;font-size:21px;background:#fff;box-shadow:0 5px 14px rgba(15,23,42,.24);border:3px solid #fff;}" +
                ".pickup{background:#16a34a;color:#fff;}" +
                ".delivery{background:#ef4444;color:#fff;}" +
                ".vehicle{width:48px;height:48px;object-fit:contain;transition:transform .30s linear;filter:drop-shadow(0 5px 6px rgba(0,0,0,.38));}" +
                ".vehicleFallback{width:46px;height:46px;border-radius:23px;background:#fff;display:flex;align-items:center;justify-content:center;font-size:28px;transition:transform .30s linear;filter:drop-shadow(0 5px 6px rgba(0,0,0,.38));}" +
                ".popup{font-weight:700;color:#0B3A78;min-width:130px;line-height:1.35;}" +
                "</style></head><body><div id='map'></div><script>" +

                "var map=null,pickup=null,delivery=null,driver=null,line=null;" +
                "var lastRouteKey='',drawing=false,lastRouteTime=0,routePts=[],routeProgress=0,driverAnim=null,currentVehicleType='';" +
                "var carIconData='" + carIcon + "',bikeIconData='" + bikeIcon + "',driverRaw=null;" +
                "function ready(){try{AndroidTrip.onMapReady();}catch(e){}}" +
                "function esc(s){return String(s||'').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/'/g,'&#39;');}" +
                "function valid(a,b){a=+a;b=+b;return isFinite(a)&&isFinite(b)&&a!==0&&b!==0;}" +
                "function init(){" +
                " if(typeof L==='undefined'){setTimeout(init,300);return;}" +
                " if(map){ready();return;}" +
                " map=L.map('map',{zoomControl:false,attributionControl:false,preferCanvas:true}).setView([-0.018137,120.087380],15);" +
                " L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:20,crossOrigin:true,attribution:''}).addTo(map);" +
                " setTimeout(function(){try{map.invalidateSize(true);}catch(e){} ready();},600);" +
                "}" +
                "function iconPin(cls,txt){return L.divIcon({html:'<div class=\"pin '+cls+'\">'+txt+'</div>',className:'',iconSize:[42,42],iconAnchor:[21,21],popupAnchor:[0,-22]});}" +
                "function vehicleIcon(type){var data=(type==='car')?carIconData:bikeIconData;var html='';if(data&&data.length>20){html='<img class=vehicle src='+data+'>';}else{html='<div class=vehicleFallback>'+((type==='car')?'🚘':'🏍️')+'</div>';}return L.divIcon({html:html,className:'',iconSize:[50,50],iconAnchor:[25,25],popupAnchor:[0,-30]});}" +
                "function setPickup(lat,lng,label){if(!map||!valid(lat,lng))return;var p=[+lat,+lng];if(pickup){pickup.setLatLng(p);}else{pickup=L.marker(p,{icon:iconPin('pickup','👤'),zIndexOffset:600}).addTo(map);}pickup.bindPopup('<div class=popup>'+esc(label||'Lokasi Pickup')+'</div>');}" +
                "function setDelivery(lat,lng,label){if(!map||!valid(lat,lng))return;var p=[+lat,+lng];if(delivery){delivery.setLatLng(p);}else{delivery=L.marker(p,{icon:iconPin('delivery','⌂'),zIndexOffset:600}).addTo(map);}delivery.bindPopup('<div class=popup>'+esc(label||'Lokasi Delivery')+'</div>');}" +
                "function bearingOf(a,b){try{var lat1=a[0]*Math.PI/180,lat2=b[0]*Math.PI/180;var dLng=(b[1]-a[1])*Math.PI/180;var y=Math.sin(dLng)*Math.cos(lat2);var x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLng);var br=(Math.atan2(y,x)*180/Math.PI)%360;return br<0?br+360:br;}catch(e){return null;}}" +
                "function snapToRoute(lat,lng){lat=+lat;lng=+lng;if(!routePts||routePts.length<2)return{lat:lat,lng:lng,bearing:null};var cos=Math.cos(lat*Math.PI/180);if(!isFinite(cos)||Math.abs(cos)<0.000001)cos=1;var px=lng*cos,py=lat,bestD=999999999,bx=lng,by=lat,bi=routeProgress;var start=Math.max(0,routeProgress-2),end=Math.min(routePts.length-2,routeProgress+80);for(var i=start;i<=end;i++){var a=routePts[i],b=routePts[i+1];var ax=a[1]*cos,ay=a[0],cx=b[1]*cos,cy=b[0];var vx=cx-ax,vy=cy-ay,wx=px-ax,wy=py-ay;var len=vx*vx+vy*vy,t=len?((wx*vx+wy*vy)/len):0;if(t<0)t=0;if(t>1)t=1;var qx=ax+vx*t,qy=ay+vy*t,dx=px-qx,dy=py-qy,dd=dx*dx+dy*dy;if(dd<bestD){bestD=dd;bx=qx/cos;by=qy;bi=i;}}var meters=Math.sqrt(bestD)*111320;if(meters>80)return{lat:lat,lng:lng,bearing:null};if(bi>=routeProgress)routeProgress=bi;var br=bearingOf(routePts[bi],routePts[Math.min(bi+1,routePts.length-1)]);return{lat:by,lng:bx,bearing:br};}" +
                "function rotateVehicle(bearing){if(!driver)return;var el=driver.getElement();if(!el)return;var img=el.querySelector('.vehicle')||el.querySelector('.vehicleFallback');if(img){img.style.transform='rotate('+(+bearing||0)+'deg)';}}" +
                "function animateDriverTo(target,bearing){if(!driver)return;if(driverAnim)clearInterval(driverAnim);var start=driver.getLatLng();var steps=24,i=0;driverAnim=setInterval(function(){i++;var f=i/steps;var ilat=start.lat+(target.lat-start.lat)*f;var ilng=start.lng+(target.lng-start.lng)*f;var s=snapToRoute(ilat,ilng);driver.setLatLng([s.lat,s.lng]);rotateVehicle((s.bearing!==null&&s.bearing!==undefined)?s.bearing:bearing);if(i>=steps){clearInterval(driverAnim);driverAnim=null;driver.setLatLng(target);rotateVehicle(bearing);}},45);}" +
                "function setDriver(lat,lng,bearing,type,name,status){if(!map||!valid(lat,lng))return;type=(type==='car')?'car':'motor';lat=+lat;lng=+lng;bearing=+bearing||0;driverRaw={lat:lat,lng:lng,bearing:bearing,type:type,name:name,status:status};var s=snapToRoute(lat,lng);var useBearing=(s.bearing!==null&&s.bearing!==undefined)?s.bearing:bearing;var target=L.latLng(s.lat,s.lng);if(!driver){driver=L.marker(target,{icon:vehicleIcon(type),zIndexOffset:9999}).addTo(map);currentVehicleType=type;rotateVehicle(useBearing);}else{if(currentVehicleType!==type){driver.setIcon(vehicleIcon(type));currentVehicleType=type;}animateDriverTo(target,useBearing);}driver.bindPopup('<div class=popup><b>'+esc(name||'Driver')+'</b><br>'+esc(status||'Dalam perjalanan')+'</div>');}" +
                "function clearLine(){if(line&&map){map.removeLayer(line);line=null;}routePts=[];}" +
                "function fitAll(){if(!map)return;var p=[];if(driver)p.push(driver.getLatLng());if(pickup)p.push(pickup.getLatLng());if(delivery)p.push(delivery.getLatLng());if(p.length===1)map.setView(p[0],16,{animate:true});else if(p.length>1)map.fitBounds(L.latLngBounds(p),{padding:[55,55],maxZoom:16,animate:true});setTimeout(function(){try{map.invalidateSize(true);}catch(e){}},250);}" +
                "function fitTrip(){fitAll();}" +
                "function drawStraight(a,b,color){if(!map)return;clearLine();routePts=[a,b];routeProgress=0;line=L.polyline(routePts,{color:color||'#2563eb',weight:5,opacity:.8,dashArray:'8,8',lineCap:'round'}).addTo(map);try{line.bringToBack();}catch(e){}if(driverRaw){setDriver(driverRaw.lat,driverRaw.lng,driverRaw.bearing,driverRaw.type,driverRaw.name,driverRaw.status);}}" +
                "function applyNativeRoute(pts,km,sec,status){try{if(typeof pts==='string')pts=JSON.parse(pts);if(!pts||pts.length<2)return;var color=status==='on_delivery'?'#16a34a':(status==='arrived_pickup'?'#f59e0b':'#2563eb');clearLine();routePts=pts;routeProgress=0;line=L.polyline(routePts,{color:color,weight:5,opacity:.92,lineCap:'round',lineJoin:'round'}).addTo(map);try{line.bringToBack();}catch(e){}if(driverRaw)setDriver(driverRaw.lat,driverRaw.lng,driverRaw.bearing,driverRaw.type,driverRaw.name,driverRaw.status);try{AndroidTrip.onRoute((+km||0),(+sec||0));}catch(e){}fitAll();}catch(e){}}"+
                "window.applyNativeRoute=applyNativeRoute;"+
                "function drawRoute(dLat,dLng,tLat,tLng,status){if(!line&&map&&valid(dLat,dLng)&&valid(tLat,tLng))drawStraight([+dLat,+dLng],[+tLat,+tLng],status==='on_delivery'?'#16a34a':'#2563eb');}"+
                "init();setTimeout(init,1000);" +
                "</script></body></html>";
    }

    public class TripBridge {
        @JavascriptInterface public void onMapReady() {
            mainHandler.post(() -> {
                mapReady = true;
                pushAllMarkersToMap();
            });
        }

        @JavascriptInterface public void onRoute(double km, double seconds) {
            mainHandler.post(() -> {
                if (km > 0 && seconds > 0) {
                    tripInfoText.setText("Estimasi rute driver: " + String.format(Locale.US, "%.1f", km) + " KM • " + Math.max(1, (int)Math.ceil(seconds / 60.0)) + " menit");
                }
            });
        }
    }

    private void startTrackingOnce() {
        if (trackingStarted || orderId.length() == 0) return;
        trackingStarted = true;
        setLoading(true);
        fetchDriverPosition();
        mainHandler.postDelayed(trackingRunnable, TRACKING_MS);
    }

    private void fetchDriverPosition() {
        if (orderId.length() == 0 || finishedCountdownStarted) return;

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject().put("order_id", orderId);
                JSONObject res = postJson(CHECK_STATUS_URL, payload);
                mainHandler.post(() -> {
                    setLoading(false);
                    if (res.optBoolean("success", false)) {
                        handleStatusResponse(res);
                    } else {
                        statusText.setText(firstNonEmpty(res.optString("message", ""), "Menunggu status order..."));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    statusText.setText("Koneksi tracking belum stabil. Mencoba lagi...");
                });
            }
        }).start();
    }

    private void handleStatusResponse(JSONObject res) {
        JSONObject driver = res.optJSONObject("driver");
        if (driver == null) driver = new JSONObject();
        JSONObject order = res.optJSONObject("order");
        if (order == null) order = new JSONObject();

        String status = firstNonEmpty(res.optString("status", ""), order.optString("status", "")).toLowerCase(Locale.US).trim();
        String driverName = firstNonEmpty(
                driver.optString("name", ""),
                driver.optString("username", ""),
                order.optString("driver", ""),
                order.optString("driver_username", ""),
                res.optString("driver", ""),
                res.optString("driver_username", ""),
                "Driver"
        );
        String plate = firstNonEmpty(
                driver.optString("plate", ""),
                driver.optString("vehicle_plate", ""),
                driver.optString("plat", ""),
                order.optString("driver_plate", ""),
                "-"
        );

        activeDriverType = resolveDriverType(order, driver);
        lastDriverName = driverName;
        lastStatus = status;

        double pLat = firstValidCoordPart(
                getJsonDouble(order, "pickup_lat", "pickupLatitude", "pickup_latitude", "user_lat", "customer_lat"),
                getJsonDouble(res, "pickup_lat", "pickupLatitude", "pickup_latitude", "user_lat", "customer_lat")
        );
        double pLng = firstValidCoordPart(
                getJsonDouble(order, "pickup_lng", "pickupLongitude", "pickup_longitude", "user_lng", "customer_lng"),
                getJsonDouble(res, "pickup_lng", "pickupLongitude", "pickup_longitude", "user_lng", "customer_lng")
        );
        double dLat = firstValidCoordPart(
                getJsonDouble(order, "delivery_lat", "deliveryLatitude", "delivery_latitude", "destination_lat"),
                getJsonDouble(res, "delivery_lat", "deliveryLatitude", "delivery_latitude", "destination_lat")
        );
        double dLng = firstValidCoordPart(
                getJsonDouble(order, "delivery_lng", "deliveryLongitude", "delivery_longitude", "destination_lng"),
                getJsonDouble(res, "delivery_lng", "deliveryLongitude", "delivery_longitude", "destination_lng")
        );

        if (validCoord(pLat, pLng)) { pickupLat = pLat; pickupLng = pLng; }
        if (validCoord(dLat, dLng)) { deliveryLat = dLat; deliveryLng = dLng; }

        double lat = firstValidCoordPart(
                getJsonDouble(driver, "driver_lat", "latitude", "lat"),
                getJsonDouble(order, "driver_lat", "latitude", "lat"),
                getJsonDouble(res, "driver_lat", "latitude", "lat")
        );
        double lng = firstValidCoordPart(
                getJsonDouble(driver, "driver_lng", "driver_lon", "longitude", "lng", "lon"),
                getJsonDouble(order, "driver_lng", "driver_lon", "longitude", "lng", "lon"),
                getJsonDouble(res, "driver_lng", "driver_lon", "longitude", "lng", "lon")
        );

        boolean hasDriverLocation = validCoord(lat, lng);

        driverNameText.setText(driverName);
        driverTypeText.setText("car".equals(activeDriverType) ? "🚘 Mobil / Car" : "🏍️ Motor / Bike");
        driverPlateText.setText("🔢 Plat: " + plate);
        setStatusText(status, driverName, hasDriverLocation);

        saveTripPrefs();

        if (isFinishedStatus(status)) {
            startFinishCountdown();
            return;
        }

        if (hasDriverLocation) {
            double[] target = getTarget(status);
            double bearing = calcBearing(lat, lng, target[0], target[1]);

            if (validCoord(lastDriverLat, lastDriverLng)) {
                double moveBearing = calcBearing(lastDriverLat, lastDriverLng, lat, lng);
                if (distanceMeters(lastDriverLat, lastDriverLng, lat, lng) > 2) bearing = moveBearing;
            }

            RemoteLocationSmoother.Point stable = remoteLocationSmoother.offer(lat, lng, bearing);
            if(stable != null){
                lastBearing = smoothBearing(lastBearing, stable.bearing);
                lastDriverLat = stable.lat;
                lastDriverLng = stable.lng;
            }
        }

        pushAllMarkersToMap();
    }

    private void pushAllMarkersToMap() {
        if (mapView == null || !mapReady) return;

        lastDataAlreadyPushed = true;

        if (validCoord(pickupLat, pickupLng)) {
            eval("setPickup(" + pickupLat + "," + pickupLng + ",'Lokasi Pickup')");
        }

        if (validCoord(deliveryLat, deliveryLng)) {
            eval("setDelivery(" + deliveryLat + "," + deliveryLng + ",'Lokasi Delivery')");
        }

        if (validCoord(lastDriverLat, lastDriverLng)) {
            String popup = popupText(lastStatus);
            double[] target = getTarget(lastStatus);

            if (validCoord(target[0], target[1])) {
                requestStableRoute(target[0], target[1], false);
            }

            eval("setDriver(" + lastDriverLat + "," + lastDriverLng + "," + lastBearing + ",'" + activeDriverType + "','" + esc(lastDriverName) + "','" + esc(popup) + "')");
        }

        if (firstFocus) {
            firstFocus = false;
            eval("fitTrip()");
        } else {
            eval("fitAll()");
        }

        if (!validCoord(lastDriverLat, lastDriverLng) && (validCoord(pickupLat, pickupLng) || validCoord(deliveryLat, deliveryLng))) {
            tripInfoText.setText("Titik pickup dan delivery siap. Menunggu lokasi driver terbaru...");
        }
    }

    private void requestStableRoute(double toLat,double toLng,boolean force){
        if(mapView==null || !mapReady || !validCoord(lastDriverLat,lastDriverLng) || !validCoord(toLat,toLng)) return;
        if(routeRequestInFlight) return;
        long now=System.currentTimeMillis();
        float movedFrom=distanceMeters(lastRouteFromLat,lastRouteFromLng,lastDriverLat,lastDriverLng);
        float movedTarget=distanceMeters(lastRouteToLat,lastRouteToLng,toLat,toLng);
        if(!force && lastStatus.equals(lastRouteStatus) && movedFrom<25f && movedTarget<10f && now-lastRouteRequestAt<15000L) return;
        routeRequestInFlight=true; lastRouteRequestAt=now;
        final double fromLat=lastDriverLat,fromLng=lastDriverLng;
        final String status=lastStatus;
        new Thread(() -> {
            try{
                StableRouteEngine.Result r=StableRouteEngine.fetch(fromLat,fromLng,toLat,toLng);
                lastRouteFromLat=fromLat; lastRouteFromLng=fromLng; lastRouteToLat=toLat; lastRouteToLng=toLng; lastRouteStatus=status;
                final String pts=r.pointsJson(); final double km=r.distanceMeters/1000d,sec=r.durationSeconds;
                mainHandler.post(() -> eval("if(window.applyNativeRoute)applyNativeRoute("+JSONObject.quote(pts)+","+km+","+sec+",'"+esc(status)+"')"));
            }catch(Exception ignored){} finally{ routeRequestInFlight=false; }
        },"transiva-route-customer").start();
    }

    private float distanceMeters(double aLat,double aLng,double bLat,double bLng){
        if(!validCoord(aLat,aLng) || !validCoord(bLat,bLng)) return 999999f;
        try{ float[] r=new float[1]; android.location.Location.distanceBetween(aLat,aLng,bLat,bLng,r); return r[0]; }catch(Exception e){ return 999999f; }
    }

    private String resolveDriverType(JSONObject order, JSONObject driver) {
        String type = firstNonEmpty(
                order.optString("driver_type", ""),
                order.optString("price_mode", ""),
                driver.optString("driver_type", ""),
                driver.optString("vehicle_type", ""),
                activeDriverType,
                "motor"
        ).toLowerCase(Locale.US);
        return "car".equals(type) || "mobil".equals(type) ? "car" : "motor";
    }


    private boolean isFinishedStatus(String status) {
        String s = firstNonEmpty(status, "").toLowerCase(Locale.US).trim();
        return "finished".equals(s)
                || "finish".equals(s)
                || "completed".equals(s)
                || "complete".equals(s)
                || "done".equals(s)
                || "selesai".equals(s);
    }

    private double[] getTarget(String status) {
        if (("on_delivery".equals(status) || "arrived_delivery".equals(status) || isFinishedStatus(status)) && validCoord(deliveryLat, deliveryLng)) {
            return new double[]{deliveryLat, deliveryLng};
        }
        return new double[]{pickupLat, pickupLng};
    }

    private void setStatusText(String status, String driverName, boolean hasLocation) {
        if (!hasLocation) {
            statusText.setText("🛰️ Menunggu lokasi terbaru dari " + driverName);
            return;
        }
        if ("taken".equals(status)) statusText.setText("🛵 " + driverName + " sedang menuju lokasi pickup");
        else if ("arrived_pickup".equals(status)) statusText.setText("✅ " + driverName + " sudah tiba di lokasi pickup");
        else if ("on_delivery".equals(status)) statusText.setText("🛵 " + driverName + " sedang menuju lokasi delivery");
        else if ("arrived_delivery".equals(status)) statusText.setText("🏁 " + driverName + " sudah tiba di lokasi delivery");
        else if (isFinishedStatus(status)) statusText.setText("✅ Order selesai");
        else statusText.setText(driverName + " sedang dalam perjalanan");
    }

    private String popupText(String status) {
        if ("arrived_pickup".equals(status)) return "✅ Sudah tiba di pickup";
        if ("on_delivery".equals(status)) return "🛵 Menuju lokasi delivery";
        if ("arrived_delivery".equals(status)) return "🏁 Sudah tiba di delivery";
        if (isFinishedStatus(status)) return "✅ Order selesai";
        return "Sedang menuju pickup";
    }

    private void startFinishCountdown() {
        if (finishedCountdownStarted) return;
        finishedCountdownStarted = true;
        mainHandler.removeCallbacks(trackingRunnable);
        finishSeconds = 5;

        Runnable r = new Runnable() {
            @Override public void run() {
                if (finishSeconds <= 0) {
                    goHomeAfterFinished();
                    return;
                }
                statusText.setText("✅ Order selesai\nKembali ke dashboard dalam " + finishSeconds + " detik...");
                finishSeconds--;
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(r);
    }

    private void goHomeAfterFinished() {
        clearActiveOrder();
        try {
            Intent i = new Intent(CustomerTripActivity.this, CustomerDashboardActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {}
        finish();
    }

    private void saveTripPrefs() {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .putString("active_order_id", orderId)
                    .putString("active_driver_type", activeDriverType)
                    .putString("pickup_lat", String.valueOf(pickupLat))
                    .putString("pickup_lng", String.valueOf(pickupLng))
                    .putString("delivery_lat", String.valueOf(deliveryLat))
                    .putString("delivery_lng", String.valueOf(deliveryLng))
                    .apply();
        } catch (Exception ignored) {}
    }

    private void clearActiveOrder() {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .remove("active_order_id")
                    .remove("pickup_lat")
                    .remove("pickup_lng")
                    .remove("delivery_lat")
                    .remove("delivery_lng")
                    .remove("active_driver_type")
                    .remove("active_order_type")
                    .remove("active_service_name")
                    .remove("active_order_price")
                    .apply();
        } catch (Exception ignored) {}
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
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.isEmpty()) return new JSONObject();
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

    private double firstValidCoordPart(double... values) {
        if (values == null) return 0;
        for (double v : values) {
            if (Double.isFinite(v) && v != 0) return v;
        }
        return 0;
    }

    private double getJsonDouble(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return 0;
        for (String k : keys) {
            try {
                if (obj.has(k) && !obj.isNull(k)) {
                    Object v = obj.opt(k);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                    String s = String.valueOf(v).trim();
                    if (s.length() > 0 && !"null".equalsIgnoreCase(s)) return Double.parseDouble(s);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private boolean validCoord(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat != 0 && lng != 0;
    }

    private double calcBearing(double lat1, double lng1, double lat2, double lng2) {
        if (!validCoord(lat1, lng1) || !validCoord(lat2, lng2)) return lastBearing;
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng);
        return normalize(Math.toDegrees(Math.atan2(y, x)));
    }

    private double smoothBearing(double oldB, double newB) {
        oldB = normalize(oldB);
        newB = normalize(newB);
        double diff = newB - oldB;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        return normalize(oldB + diff * 0.35);
    }

    private double normalize(double v) {
        v = v % 360;
        return v < 0 ? v + 360 : v;
    }


    private void eval(String js) {
        if (mapView == null || !mapReady) return;
        try {
            mapView.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    private void setLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String esc(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0 && !v.trim().equalsIgnoreCase("null")) return v.trim();
        }
        return "";
    }

    private Button outlineButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1));
        return b;
    }

    private Button smallButton(String value, String bg, String fg, String stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(roundStroke(bg, stroke, dp(16), 1));
        return b;
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


    private String drawableDataUri(String... names) {
        try {
            for (String name : names) {
                int id = getResources().getIdentifier(name, "drawable", getPackageName());
                if (id <= 0) continue;

                Bitmap bm = BitmapFactory.decodeResource(getResources(), id);
                if (bm == null) continue;

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
                String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                try { bm.recycle(); } catch (Exception ignored) {}
                return "data:image/png;base64," + b64;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
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

    @Override protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(trackingRunnable);
    }

    @Override protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);
        if (trackingStarted && !finishedCountdownStarted) {
            fetchDriverPosition();
            mainHandler.postDelayed(trackingRunnable, TRACKING_MS);
        }
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        try {
            if (mapView != null) {
                mapView.stopLoading();
                mapView.loadUrl("about:blank");
                mapView.destroy();
                mapView = null;
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}