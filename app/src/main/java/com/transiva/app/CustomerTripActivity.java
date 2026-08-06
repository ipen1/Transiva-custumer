package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
import android.widget.EditText;
import android.widget.RatingBar;
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
    private static final String PICKUP_STATUS_URL = BASE_URL + "server/get_pickup_order_status.php";
    private static final String CUSTOMER_ACTION_URL = BASE_URL + "server/customer_order_action.php";
    private static final String SAVE_REVIEW_URL = BASE_URL + "server/save_driver_review.php";
    private static final String SHARE_TRIP_URL = BASE_URL + "share_trip.php";

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

    private TransivaGoogleMapView mapView;
    private TextView statusText;
    private TextView driverNameText;
    private TextView driverTypeText;
    private TextView driverPlateText;
    private TextView tripInfoText;
    private ImageView driverPhotoView;
    private ProgressBar progressBar;
    private TextView paymentInfoText;
    private Button receivedButton, approvePriceButton, rejectPriceButton;

    private String orderId = "";
    private String activeDriverType = "motor";
    private String orderSource = "orders";
    private boolean trackingOnly = false;

    private boolean mapReady = false;
    private boolean firstFocus = true;
    private boolean trackingStarted = false;
    private boolean finishedCountdownStarted = false;
    private boolean reviewDialogShown = false;
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
        if (trackingOnly) {
            if (driverNameText != null) driverNameText.setVisibility(View.GONE);
            if (driverTypeText != null) driverTypeText.setVisibility(View.GONE);
            if (driverPlateText != null) driverPlateText.setVisibility(View.GONE);
            if (driverPhotoView != null) driverPhotoView.setVisibility(View.GONE);
            if (paymentInfoText != null) paymentInfoText.setVisibility(View.GONE);
            if (receivedButton != null) receivedButton.setVisibility(View.GONE);
            if (approvePriceButton != null) approvePriceButton.setVisibility(View.GONE);
            if (rejectPriceButton != null) rejectPriceButton.setVisibility(View.GONE);
        }

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

        trackingOnly = i.getBooleanExtra("tracking_only", false);

        String deepLinkOrderId = "";
        try {
            Uri data = i.getData();
            if (data != null) {
                deepLinkOrderId = firstNonEmpty(
                        data.getQueryParameter("order_id"),
                        data.getQueryParameter("order"),
                        data.getLastPathSegment()
                );
                // Link yang dibagikan dibuka sebagai penonton, bukan sebagai pemilik order.
                trackingOnly = true;
            }
        } catch (Exception ignored) { }

        orderId = firstNonEmpty(
                deepLinkOrderId,
                i.getStringExtra("order_id"),
                i.getStringExtra("active_order_id"),
                sp.getString("active_order_id", "")
        );

        pickupLat = getDoubleExtraOrPref(i, sp, "pickup_lat", 0);
        pickupLng = getDoubleExtraOrPref(i, sp, "pickup_lng", 0);
        deliveryLat = getDoubleExtraOrPref(i, sp, "delivery_lat", 0);
        deliveryLng = getDoubleExtraOrPref(i, sp, "delivery_lng", 0);

        orderSource = firstNonEmpty(
                i.getStringExtra("order_source"),
                i.getStringExtra("source"),
                sp.getString("active_order_source", "orders"),
                "orders"
        ).toLowerCase(Locale.US);

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
        close.setOnClickListener(v -> goToHome());

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

        paymentInfoText = text("", 13, "#0B3A78", true); paymentInfoText.setPadding(dp(12),dp(9),dp(12),dp(9)); paymentInfoText.setVisibility(View.GONE); card.addView(paymentInfoText,new LinearLayout.LayoutParams(-1,-2));
        receivedButton = smallButton("✅ Terima Pesanan", "#DCFCE7", "#047857", "#86EFAC"); receivedButton.setVisibility(View.GONE); receivedButton.setOnClickListener(v -> sendCustomerAction("confirm_received")); LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,dp(50)); rlp.setMargins(0,dp(8),0,0); card.addView(receivedButton,rlp);
        LinearLayout priceActions=new LinearLayout(this); priceActions.setOrientation(LinearLayout.HORIZONTAL); priceActions.setVisibility(View.GONE); priceActions.setTag("price_actions");
        approvePriceButton=smallButton("Setujui Harga", "#DBEAFE", "#1D4ED8", "#93C5FD"); approvePriceButton.setOnClickListener(v->sendCustomerAction("approve_price")); priceActions.addView(approvePriceButton,new LinearLayout.LayoutParams(0,dp(48),1));
        rejectPriceButton=smallButton("Tolak", "#FEE2E2", "#B91C1C", "#FCA5A5"); rejectPriceButton.setOnClickListener(v->sendCustomerAction("reject_price")); LinearLayout.LayoutParams rej=new LinearLayout.LayoutParams(0,dp(48),1); rej.setMargins(dp(8),0,0,0); priceActions.addView(rejectPriceButton,rej); LinearLayout.LayoutParams palp=new LinearLayout.LayoutParams(-1,-2); palp.setMargins(0,dp(8),0,0); card.addView(priceActions,palp);

        mapView = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.TRIP);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1);
        mlp.setMargins(0, dp(4), 0, dp(10));
        card.addView(mapView, mlp);
        mapView.initialize(null, new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat, double lng) {
                mapReady = true;
                pushAllMarkersToMap();
            }
            @Override public void onCenterChanged(double lat, double lng) { }
        });

        tripInfoText = text("Menyiapkan rute perjalanan...", 12, "#64748B", false);
        tripInfoText.setPadding(dp(4), 0, dp(4), dp(8));
        card.addView(tripInfoText, new LinearLayout.LayoutParams(-1, -2));

        Button liveBtn = outlineButton("📍 Lihat Live Driver");
        liveBtn.setTextColor(Color.parseColor("#FFFFFF"));
        liveBtn.setBackground(roundStroke("#0B7CFF", "#0B7CFF", dp(18), 1));
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(-1, dp(50));
        liveLp.setMargins(0, 0, 0, dp(8));
        card.addView(liveBtn, liveLp);
        liveBtn.setOnClickListener(v -> openLiveDriver());

        LinearLayout safetyRow = new LinearLayout(this);
        safetyRow.setOrientation(LinearLayout.HORIZONTAL);
        Button shareBtn = outlineButton("🔗 Bagikan Trip");
        Button sosBtn = outlineButton("🆘 SOS");
        safetyRow.addView(shareBtn, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams sosLp = new LinearLayout.LayoutParams(0, dp(48), 1); sosLp.setMargins(dp(8),0,0,0);
        safetyRow.addView(sosBtn, sosLp);
        LinearLayout.LayoutParams safetyLp = new LinearLayout.LayoutParams(-1, -2); safetyLp.setMargins(0,0,0,dp(8)); card.addView(safetyRow, safetyLp);
        shareBtn.setOnClickListener(v -> shareTrip());
        sosBtn.setOnClickListener(v -> openSos());

        Button backBtn = outlineButton("Kembali");
        card.addView(backBtn, new LinearLayout.LayoutParams(-1, dp(48)));
        backBtn.setOnClickListener(v -> goToHome());

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
                "function setPickup(lat,lng,label){if(!map||!valid(lat,lng))return;var p=[+lat,+lng];if(pickup){pickup.setLatLng(p);}else{pickup=L.marker(p,{icon:iconPin('pickup','👤'),zIndexOffset:600}).addTo(map);}pickup.bindPopup('<div class=popup>'+esc(label||'Lokasi Penjemputan')+'</div>');}" +
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
                JSONObject res;
                if (orderSource.contains("pickup")) {
                    res = getJson(PICKUP_STATUS_URL + "?order_id=" + Uri.encode(orderId));
                } else {
                    JSONObject payload = new JSONObject().put("order_id", orderId);
                    res = postJson(CHECK_STATUS_URL, payload);
                }
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
        // Data driver dan tombol aksi berubah dari polling server; pastikan view baru/
        // yang diperbarui tetap menggunakan tema customer yang sedang aktif.
        CustomerAppSettings.applyToView(this, driverNameText.getRootView());

        String orderType = firstNonEmpty(order.optString("order_type", ""), res.optString("order_type", "")).trim().toLowerCase(Locale.US);
        String merchantStatus = firstNonEmpty(order.optString("merchant_status", ""), res.optString("merchant_status", "")).trim().toLowerCase(Locale.US);
        int cookMinutes = Math.max(order.optInt("cook_minutes", 0), res.optInt("cook_minutes", 0));
        if ("food".equals(orderType) && !merchantStatus.isEmpty()) {
            String merchantLine = "";
            if ("ready".equals(merchantStatus)) merchantLine = "🍽️ Pesanan sudah siap diambil dari merchant";
            else if ("preparing".equals(merchantStatus)) merchantLine = "🍳 Merchant sedang menyiapkan pesanan" + (cookMinutes > 0 ? (" • estimasi " + cookMinutes + " menit") : "");
            else if ("merchant_accepted".equals(merchantStatus)) merchantLine = "✅ Merchant menerima pesanan" + (cookMinutes > 0 ? (" • estimasi " + cookMinutes + " menit") : "");
            if (!merchantLine.isEmpty()) statusText.setText(statusText.getText() + "\n" + merchantLine);
        }

        updatePaymentControls(order, status);
        saveTripPrefs();

        if (isFinishedStatus(status)) {
            showDriverReviewDialog(order);
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
            if (mapView != null) mapView.setPickup(pickupLat, pickupLng, "Lokasi Penjemputan");
        }

        if (validCoord(deliveryLat, deliveryLng)) {
            if (mapView != null) mapView.setDelivery(deliveryLat, deliveryLng, "Lokasi Delivery");
        }

        if (validCoord(lastDriverLat, lastDriverLng)) {
            String popup = popupText(lastStatus);
            double[] target = getTarget(lastStatus);

            if (validCoord(target[0], target[1])) {
                requestStableRoute(target[0], target[1], false);
            }

            if (mapView != null) mapView.setTripDriver(lastDriverLat, lastDriverLng, lastBearing, "car".equals(activeDriverType), lastDriverName, popup);
        }

        if (firstFocus) {
            firstFocus = false;
            if (mapView != null) mapView.fitAll();
        } else {
            if (mapView != null) mapView.fitAll();
        }

        if (!validCoord(lastDriverLat, lastDriverLng) && (validCoord(pickupLat, pickupLng) || validCoord(deliveryLat, deliveryLng))) {
            tripInfoText.setText("Titik penjemputan dan pengantaran siap. Menunggu lokasi driver terbaru...");
        }
    }

    private void updatePaymentControls(JSONObject order,String status){
        if (trackingOnly) return;
        if(paymentInfoText==null||receivedButton==null) return;
        String method=firstNonEmpty(order.optString("payment_method",""),"cash").toLowerCase(Locale.US);
        boolean nonCash=method.equals("balance")||method.contains("transpay")||method.contains("transiva_pay")||method.equals("wallet")||method.equals("saldo");
        double price=order.optDouble("price",0), original=order.optDouble("original_price",price), requested=order.optDouble("price_change_requested",0);
        String change=order.optString("price_change_status","none").toLowerCase(Locale.US), reason=order.optString("price_change_reason","");
        String line=(nonCash?"💳 TransPay • sudah dibayar":"💵 Tunai • bayar ke driver")+(price>0?" • "+rupiah(price):"");
        if(original>0 && Math.abs(original-price)>0.5) line += "\nHarga berubah dari " + rupiah(original) + " menjadi " + rupiah(price) + (reason.isEmpty() ? "" : " • " + reason);
        if(change.equals("pending") && requested > 0) line += "\nDriver mengajukan " + rupiah(requested) + (reason.isEmpty() ? "" : " • " + reason);
        paymentInfoText.setText(line); paymentInfoText.setVisibility(View.VISIBLE); paymentInfoText.setBackground(round("#EFF6FF",dp(14)));
        receivedButton.setVisibility("arrived_delivery".equals(status) && order.optInt("customer_received",0)!=1 ? View.VISIBLE:View.GONE);
        View priceActions=null; android.view.ViewParent par=approvePriceButton==null?null:approvePriceButton.getParent(); if(par instanceof View) priceActions=(View)par;
        if(priceActions!=null) priceActions.setVisibility(change.equals("pending")?View.VISIBLE:View.GONE);
    }
    private void sendCustomerAction(String action){
        if(orderId.isEmpty()) return; setLoading(true); new Thread(()->{ try{
            JSONObject p=new JSONObject(); p.put("order_id",orderId); p.put("source",orderSource.contains("pickup")?"pickup_orders":"orders"); p.put("action",action);
            JSONObject r=postJson(CUSTOMER_ACTION_URL,p); boolean ok=r.optBoolean("success",false); String m=firstNonEmpty(r.optString("message",""),ok?"Berhasil":"Gagal");
            mainHandler.post(()->{setLoading(false); showInfo(ok?"Berhasil":"Gagal",m); if(ok) fetchDriverPosition();});
        }catch(Exception e){mainHandler.post(()->{setLoading(false);showInfo("Gagal","Koneksi server bermasalah.");});}},"customer-action").start();
    }
    private String rupiah(double value){ return "Rp"+java.text.NumberFormat.getNumberInstance(new Locale("id","ID")).format(Math.round(value)); }

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
                mainHandler.post(() -> { if (mapView != null) { mapView.drawOsrmRoute(r.latLngPoints, status); mapView.fitAll(); } if (tripInfoText != null && km > 0 && sec > 0) tripInfoText.setText("Estimasi rute driver: " + String.format(Locale.US, "%.1f", km) + " KM • " + Math.max(1, (int)Math.ceil(sec / 60.0)) + " menit"); });
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
        if ("taken".equals(status)) statusText.setText("🛵 " + driverName + " sedang menuju titik penjemputan");
        else if ("arrived_pickup".equals(status)) statusText.setText("✅ " + driverName + " sudah tiba di titik penjemputan");
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
        return "Sedang menuju penjemputan";
    }


    private void showDriverReviewDialog(JSONObject order) {
        if (reviewDialogShown || isFinishing()) return;
        if (order.optInt("rating", 0) > 0) { goHomeAfterFinished(); return; }
        reviewDialogShown = true;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        TextView prompt = new TextView(this);
        prompt.setText("Bagaimana pelayanan " + firstNonEmpty(order.optString("driver_name"), order.optString("driver"), "driver") + "?");
        prompt.setTextSize(16);
        prompt.setTextColor(Color.parseColor("#0B3A78"));
        box.addView(prompt);
        RatingBar stars = new RatingBar(this, null, android.R.attr.ratingBarStyle);
        stars.setNumStars(5); stars.setStepSize(1f); stars.setRating(5f);
        box.addView(stars, new LinearLayout.LayoutParams(-2,-2));
        EditText review = new EditText(this);
        review.setHint("Tulis ulasan (opsional)"); review.setMinLines(2); review.setMaxLines(4);
        box.addView(review, new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Beri Rating Driver")
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("Lewati", (d,w) -> goHomeAfterFinished())
                .setPositiveButton("Kirim", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int rating = Math.max(1, Math.round(stars.getRating()));
            submitDriverReview(rating, review.getText().toString().trim(), dialog);
        }));
        dialog.show();
    }

    private void submitDriverReview(int rating, String review, AlertDialog dialog) {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("order_id", orderId);
                body.put("source", orderSource.contains("pickup") ? "pickup_orders" : "orders");
                body.put("rating", rating);
                body.put("review", review);
                JSONObject res = postJson(SAVE_REVIEW_URL, body);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Terima kasih atas ulasannya." : "Rating gagal disimpan.");
                mainHandler.post(() -> {
                    setLoading(false);
                    if (ok) { dialog.dismiss(); showInfo("Terima kasih", msg); mainHandler.postDelayed(this::goHomeAfterFinished, 700); }
                    else showInfo("Gagal", msg);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showInfo("Gagal", "Koneksi server bermasalah."); });
            }
        }, "save-driver-review").start();
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


    private void shareTrip() {
        try {
            String liveUrl = SHARE_TRIP_URL + "?order_id=" + Uri.encode(orderId);
            String text = "Pantau perjalanan Transiva saya secara real-time."
                    + "\nOrder #" + orderId
                    + "\nBuka perjalanan live: " + liveUrl;

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Perjalanan Live Transiva");
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Bagikan perjalanan live"));
        } catch (Exception e) {
            showInfo("Bagikan Trip", "Tidak dapat membuka menu berbagi.");
        }
    }

    private void openSos() {
        new AlertDialog.Builder(this).setTitle("🆘 Bantuan Darurat")
                .setMessage("Hubungi layanan darurat hanya saat benar-benar diperlukan. Anda juga dapat membagikan perjalanan kepada keluarga.")
                .setNeutralButton("Bagikan Trip", (d,w) -> shareTrip())
                .setNegativeButton("Batal", null)
                .setPositiveButton("Telepon 112", (d,w) -> {
                    try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))); } catch (Exception ignored) { }
                }).show();
    }

    private void openLiveDriver() {
        if (orderId == null || orderId.trim().isEmpty()) {
            showInfo("Live Driver", "ID order belum tersedia.");
            return;
        }
        Intent i = new Intent(this, CustomerLiveDriverActivity.class);
        i.putExtra("order_id", orderId);
        i.putExtra("pickup_lat", pickupLat);
        i.putExtra("pickup_lng", pickupLng);
        i.putExtra("delivery_lat", deliveryLat);
        i.putExtra("delivery_lng", deliveryLng);
        i.putExtra("active_driver_type", activeDriverType);
        startActivity(i);
    }

    private void saveTripPrefs() {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .putString("active_order_id", orderId)
                    .putString("active_driver_type", activeDriverType)
                    .putString("active_order_status", lastStatus == null ? "" : lastStatus)
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
                    .remove("active_order_status")
                    .remove("active_order_type")
                    .remove("active_service_name")
                    .remove("active_order_price")
                    .apply();
        } catch (Exception ignored) {}
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            return body.isEmpty() ? new JSONObject() : new JSONObject(body);
        } finally {
            if (conn != null) conn.disconnect();
        }
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

    private void goToHome() {
        // Buat ulang task dashboard secara eksplisit. Ini tetap bekerja ketika layar trip
        // menjadi root activity setelah aplikasi dipulihkan atau dibuka dari deep link.
        try {
            Intent home = new Intent(this, CustomerDashboardActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(home);
            overridePendingTransition(0, 0);
        } catch (Exception firstError) {
            try {
                Intent splash = new Intent(this, SplashActivity.class);
                splash.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(splash);
            } catch (Exception ignored) { }
        }
        finishAffinity();
    }

    @Override
    public void onBackPressed() {
        goToHome();
    }

    @Override protected void onPause() {
        if (mapView != null) mapView.onPauseMap();
        super.onPause();
        mainHandler.removeCallbacks(trackingRunnable);
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStartMap();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResumeMap();
        CustomerAppSettings.apply(this);
        if (trackingStarted && !finishedCountdownStarted) {
            fetchDriverPosition();
            mainHandler.postDelayed(trackingRunnable, TRACKING_MS);
        }
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
        try {
            if (mapView != null) {
                mapView.onDestroyMap();
                mapView = null;
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
