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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
class CustomerTripActivityScreenCore extends CustomerAdaptiveActivity {



    protected final CustomerFeatureRuntimeController featureRuntime =
            new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.TRIP);

    protected static final String BASE_URL = "https://transiva.my.id/";
    protected static final String CHECK_STATUS_URL = BASE_URL + "server/check_order_status.php";
    protected static final String PICKUP_STATUS_URL = BASE_URL + "server/get_pickup_order_status.php";
    protected static final String CUSTOMER_ACTION_URL = BASE_URL + "server/customer_order_action.php";
    protected static final String SHARE_TRIP_URL = BASE_URL + "server/share_trip.php";
    protected static final String CREATE_SHARED_TRIP_URL = BASE_URL + "server/create_shared_trip.php";


    protected static final int TIMEOUT_MS = 25000;
    protected static final long TRACKING_MS = 3000;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected final AtomicBoolean trackingRequestInFlight = new AtomicBoolean(false);
    protected final AtomicLong trackingGeneration = new AtomicLong(0L);

    protected final Runnable trackingRunnable = new Runnable() {
        @Override public void run() {
            fetchDriverPosition();
            mainHandler.postDelayed(this, TRACKING_MS);
        }
    };

    protected TransivaGoogleMapView mapView;
    private PremiumOrderStatusView tripStatus;
    protected TextView statusText;
    protected TextView driverNameText;
    protected TextView driverTypeText;
    protected TextView driverPlateText;
    protected TextView tripInfoText;
    protected ImageView driverPhotoView;
    private ImageView vehiclePhotoView;
    private String loadedDriverPhoto="", loadedVehiclePhoto="";
    protected ProgressBar progressBar;
    protected TextView paymentInfoText;
    protected Button receivedButton, approvePriceButton, rejectPriceButton;
    private Button reportOfflineButton;
    private boolean reportOfflineInFlight;
    private boolean reportOfflineSubmitted;
    private Button shareTripButton;
    private boolean shareBusy;
    private boolean tripLayoutBuilt, foodStatusRouted;
    private boolean resumed;
    private TextView finishHint;
    private int finishSeconds = 5;
    private final Runnable finishTick = new Runnable() {
        @Override public void run() {
            if (!resumed || isFinishing() || isDestroyed()) return;
            if (finishSeconds == 0) {
                goHomeAfterFinished();
                return;
            }
            finishHint.setText("Kembali ke beranda dalam " + finishSeconds + " detik");
            finishSeconds--;
            mainHandler.postDelayed(this, 1000);
        }
    };

    protected String orderId = "";
    protected String activeDriverType = "motor";
    protected String orderSource = "orders";
    protected boolean trackingOnly = false;
    private SmartWaitingTextView waitingView;

    protected boolean mapReady = false;
    protected boolean firstFocus = true;
    protected boolean trackingStarted = false;
    protected boolean finishedCountdownStarted = false;
    protected boolean lastDataAlreadyPushed = false;


    protected double pickupLat = 0;
    protected double pickupLng = 0;
    protected double deliveryLat = 0;
    protected double deliveryLng = 0;
    protected double lastDriverLat = 0;
    protected final RemoteLocationSmoother remoteLocationSmoother = new RemoteLocationSmoother();
    protected double lastDriverLng = 0;
    protected double lastBearing = 0;

    protected String lastDriverName = "Driver";
    protected String lastStatus = "";
    protected volatile boolean routeRequestInFlight = false;
    protected long lastRouteRequestAt = 0L;
    protected double lastRouteFromLat = 0d, lastRouteFromLng = 0d;
    protected double lastRouteToLat = 0d, lastRouteToLng = 0d;
    protected String lastRouteStatus = "";
    private final AtomicLong routeGeneration = new AtomicLong();
    private String appliedRouteKey = "";
    private boolean driverCameraFocused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            CustomerWindowPolicy.status(getWindow(), Color.parseColor("#071426"));
            CustomerWindowPolicy.navigation(getWindow(), Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        readIntentAndSavedData();
        // Old PendingIntents and history links can still target Trip without status metadata.
        if(!trackingOnly&&orderId.toUpperCase(Locale.ROOT).startsWith("FOOD-")&&!getIntent().getBooleanExtra("food_status_resolved",false)){
            Intent food=new Intent(this,CustomerFoodOrderStatusActivity.class).putExtra("order_id",orderId);
            for(String key:new String[]{"status","restaurant_name","merchant_reject_reason"})food.putExtra(key,getIntent().getStringExtra(key));
            foodStatusRouted=true;startActivity(food);finish();return;
        }
        if(trackingOnly){buildLayout();tripLayoutBuilt=true;}
        else buildStatusGate();
        if(orderId.length()==0){statusText.setText("ID pesanan belum ditemukan. Kembali ke beranda untuk memeriksanya.");return;}
        startTrackingOnce();
    }

    private void buildStatusGate(){
        LinearLayout gate=new LinearLayout(this);gate.setOrientation(LinearLayout.VERTICAL);gate.setGravity(Gravity.CENTER);gate.setPadding(dp(24),dp(24),dp(24),dp(24));
        gate.setBackgroundColor(CustomerAppSettings.isDarkMode(this)?0xFF071426:0xFFF7FAFF);
        TextView brand = text("TRANSIVA", 11, "#1268D5", true);
        brand.setLetterSpacing(.12f);
        gate.addView(brand);
        TextView title = text("Perjalanan Anda", 22, "#0B3A78", true);
        title.setGravity(Gravity.CENTER);
        gate.addView(title, tripRow(6));
        statusText = text("Memeriksa status pesanan…", 14, "#536B83", false);
        statusText.setGravity(Gravity.CENTER);
        gate.addView(statusText, tripRow(8));
        Button retry = outlineButton("Periksa lagi");
        retry.setMinHeight(dp(48));
        retry.setOnClickListener(v -> fetchDriverPosition());
        gate.addView(retry, tripRow(16));
        Button home = outlineButton("Ke beranda");
        home.setMinHeight(dp(48));
        home.setOnClickListener(v -> goToHome());
        gate.addView(home, tripRow(8));
        setContentView(gate);
        CustomerAppSettings.apply(this);
    }

    private void openFoodStatus(JSONObject response){
        if(foodStatusRouted)return;foodStatusRouted=true;trackingStarted=false;
        trackingGeneration.incrementAndGet();routeGeneration.incrementAndGet();mainHandler.removeCallbacks(trackingRunnable);if(orderId.equals(getSharedPreferences("transiva",MODE_PRIVATE).getString("active_order_id","")))stopRideProtection();
        if(mapView!=null){mapView.setVisibility(View.GONE);mapReady=false;}
        JSONObject order=FoodOrderDecision.order(response);
        Intent intent=new Intent(this,CustomerFoodOrderStatusActivity.class).putExtra("order_id",orderId);
        intent.putExtra("status",FoodOrderDecision.rejected(response)?"merchant_rejected":FoodOrderDecision.status(response));
        intent.putExtra("merchant_reject_reason",FoodOrderDecision.reason(response));intent.putExtra("restaurant_name",order.optString("restaurant_name",""));
        startActivity(intent);finish();
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        String incoming=firstNonEmpty(intent.getStringExtra("order_id"),intent.getStringExtra("active_order_id"));
        if((incoming.toUpperCase(Locale.ROOT).startsWith("FOOD-")||"merchant_rejected".equals(intent.getStringExtra("status")))&&!intent.getBooleanExtra("food_status_resolved",false)){
            foodStatusRouted=true;trackingStarted=false;trackingGeneration.incrementAndGet();routeGeneration.incrementAndGet();mainHandler.removeCallbacks(trackingRunnable);
            if(mapView!=null)mapView.setVisibility(View.GONE);
            Intent food=new Intent(this,CustomerFoodOrderStatusActivity.class).putExtra("order_id",incoming);
            for(String key:new String[]{"status","restaurant_name","merchant_reject_reason"})food.putExtra(key,intent.getStringExtra(key));
            startActivity(food);finish();
        }else recreate();
    }

    protected void startRideProtectionIfEnabled() {
        if (trackingOnly || orderId == null || orderId.trim().isEmpty()) return;
        SharedPreferences sp=getSharedPreferences("transiva",MODE_PRIVATE);
        boolean guardian=sp.getBoolean("active_order_trip_monitoring",true);
        boolean audio=sp.getBoolean("active_order_audio_protect",false);
        if(guardian){Intent g=new Intent(this,TripGuardianService.class).putExtra(TripGuardianService.EXTRA_ORDER_ID,orderId); try{androidx.core.content.ContextCompat.startForegroundService(this,g);}catch(Exception ignored){}}
        if(audio && (android.os.Build.VERSION.SDK_INT<23 || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)){
            Intent a=new Intent(this,AudioProtectService.class).putExtra(AudioProtectService.EXTRA_ORDER_ID,orderId); try{androidx.core.content.ContextCompat.startForegroundService(this,a);}catch(Exception ignored){}
        }
    }
    protected void stopRideProtection(){try{stopService(new Intent(this,TripGuardianService.class));}catch(Exception ignored){} try{stopService(new Intent(this,AudioProtectService.class));}catch(Exception ignored){}}

    protected void readIntentAndSavedData() {
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

        String savedSource = orderId.equals(sp.getString("active_order_id", ""))
                ? sp.getString("active_order_source", "") : "";
        orderSource = firstNonEmpty(i.getStringExtra("order_source"), i.getStringExtra("source"),
                savedSource, "orders").toLowerCase(Locale.US);

        activeDriverType = firstNonEmpty(
                i.getStringExtra("active_driver_type"),
                sp.getString("active_driver_type", "motor"),
                "motor"
        );

        if (!"car".equals(activeDriverType)) activeDriverType = "motor";
    }

    protected double getDoubleExtraOrPref(Intent i, SharedPreferences sp, String key, double def) {
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

    protected void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView tripScroll = new android.widget.ScrollView(this);
        tripScroll.setClipToPadding(false);
        root.addView(tripScroll, new LinearLayout.LayoutParams(-1, -1));
        tripScroll.addView(card, new android.widget.ScrollView.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        Button close = smallButton("‹", "#EDF5FF", "#1268D5", "#DCEBFF");
        close.setTextSize(23);
        close.setPadding(0, 0, 0, 0);
        close.setContentDescription("Kembali ke beranda");
        head.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        close.setOnClickListener(v -> goToHome());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("TRANSIVA", 10, "#1268D5", true));
        heading.addView(text("Perjalanan Anda", 19, "#0B3A78", true));
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(0, -2, 1);
        headingLp.setMargins(dp(9), 0, dp(6), 0);
        head.addView(heading, headingLp);

        TextView liveBadge = text("●  LIVE", 10, "#0B7661", true);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(dp(8), dp(5), dp(8), dp(5));
        liveBadge.setBackground(round("#E8F8F1", dp(9)));
        head.addView(liveBadge, new LinearLayout.LayoutParams(-2, -2));

        tripStatus = new PremiumOrderStatusView(this, true);
        tripStatus.setActionVisible(false);
        tripStatus.setReferenceVisible(false);
        tripStatus.bind(null, "Menyiapkan perjalanan", "");
        card.addView(tripStatus, tripRow(8));

        LinearLayout driverBox = new LinearLayout(this);
        driverBox.setOrientation(LinearLayout.HORIZONTAL);
        driverBox.setGravity(Gravity.CENTER_VERTICAL);
        driverBox.setPadding(dp(9), dp(6), dp(9), dp(6));
        driverBox.setBackground(roundStroke("#FFFFFF", "#E2E8F0", dp(18), 1));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.setMargins(0, dp(8), 0, 0);
        card.addView(driverBox, dlp);

        driverPhotoView = new ImageView(this);
        driverPhotoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int fallbackAvatar = getResources().getIdentifier("car".equals(activeDriverType)
                ? "ic_service_car_premium" : "ic_service_ride_premium", "drawable", getPackageName());
        driverPhotoView.setImageResource(fallbackAvatar != 0 ? fallbackAvatar : android.R.drawable.ic_menu_myplaces);
        driverPhotoView.setBackground(round("#EAF4FF", dp(16)));
        driverPhotoView.setClipToOutline(true);
        driverBox.addView(driverPhotoView, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1);
        ilp.setMargins(dp(10), 0, 0, 0);
        driverBox.addView(info, ilp);

        driverNameText = text("Driver", 13, "#0B3A78", true);
        driverTypeText = text("Driver Anda", 10, "#2563EB", true);
        driverPlateText = text("Plat —", 11, "#64748B", false);
        driverPlateText.setSingleLine(true);
        driverPlateText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        driverNameText.setMaxLines(1);
        driverNameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(driverNameText);
        info.addView(driverPlateText);
        driverTypeText.setTextSize(10);
        driverTypeText.setGravity(Gravity.CENTER);
        driverTypeText.setPadding(dp(6), dp(5), dp(6), dp(5));
        driverTypeText.setMaxLines(2);
        driverTypeText.setBackground(round("#EDF5FF", dp(10)));
        driverBox.addView(driverTypeText, new LinearLayout.LayoutParams(-2, -2));
        vehiclePhotoView=new ImageView(this);vehiclePhotoView.setScaleType(ImageView.ScaleType.CENTER_CROP);vehiclePhotoView.setBackground(round("#EAF4FF",dp(12)));vehiclePhotoView.setClipToOutline(true);vehiclePhotoView.setVisibility(View.GONE);driverBox.addView(vehiclePhotoView,new LinearLayout.LayoutParams(dp(58),dp(48)));

        statusText = text("Menghubungkan lokasi driver...", 12, "#536B83", false);
        statusText.setPadding(dp(2), dp(5), dp(2), dp(5));
        card.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        waitingView = new SmartWaitingTextView(this);
        waitingView.setTripPresentation();
        card.addView(waitingView, tripRow(6));
        paymentInfoText = text("", 12, "#0B3A78", true);
        paymentInfoText.setPadding(dp(10), dp(7), dp(10), dp(7));
        paymentInfoText.setVisibility(View.GONE);
        card.addView(paymentInfoText, tripRow(6));
        receivedButton = smallButton("✅ Terima Pesanan", "#DCFCE7", "#047857", "#86EFAC"); receivedButton.setVisibility(View.GONE); receivedButton.setOnClickListener(v -> sendCustomerAction("confirm_received")); receivedButton.setMinHeight(dp(50)); LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.setMargins(0,dp(8),0,0); card.addView(receivedButton,rlp);
        reportOfflineButton = smallButton("Driver meminta saya membatalkan", "#EFF6FF", "#1D4ED8", "#BFDBFE");
        reportOfflineButton.setVisibility(View.GONE);
        card.addView(reportOfflineButton, tripRow(6));
        LinearLayout priceActions=new LinearLayout(this); priceActions.setOrientation(LinearLayout.HORIZONTAL); priceActions.setVisibility(View.GONE); priceActions.setTag("price_actions");
        approvePriceButton=smallButton("Setujui Harga", "#DBEAFE", "#1D4ED8", "#93C5FD"); approvePriceButton.setMinHeight(dp(48)); approvePriceButton.setOnClickListener(v->confirmPriceChange()); priceActions.addView(approvePriceButton,new LinearLayout.LayoutParams(0,-2,1));
        rejectPriceButton=smallButton("Tolak", "#FEE2E2", "#B91C1C", "#FCA5A5"); rejectPriceButton.setMinHeight(dp(48)); rejectPriceButton.setOnClickListener(v->sendCustomerAction("reject_price")); LinearLayout.LayoutParams rej=new LinearLayout.LayoutParams(0,-2,1); rej.setMargins(dp(8),0,0,0); priceActions.addView(rejectPriceButton,rej); LinearLayout.LayoutParams palp=new LinearLayout.LayoutParams(-1,-2); palp.setMargins(0,dp(8),0,0); card.addView(priceActions,palp);

        LinearLayout mapCard = new LinearLayout(this);
        mapCard.setOrientation(LinearLayout.VERTICAL);
        mapCard.setPadding(dp(5), dp(5), dp(5), dp(2));
        mapCard.setBackground(roundStroke("#FFFFFF", "#DCE8F6", dp(18), 1));
        card.addView(mapCard, tripRow(8));

        mapView = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.TRIP);
        mapView.setBackground(round("#EDF4FC", dp(14)));
        mapView.setClipToOutline(true);
        int mapHeight = Math.max(195, Math.min(360, (int)(getResources().getConfiguration().screenHeightDp * .40f)));
        mapCard.addView(mapView, new LinearLayout.LayoutParams(-1, dp(mapHeight)));
        mapView.initialize(null, new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat, double lng) {
                if (mapView == null || finishedCountdownStarted || isDestroyed()) return;
                mapReady = true;
                pushAllMarkersToMap();
            }
            @Override public void onCenterChanged(double lat, double lng) { }
        });

        LinearLayout mapFooter = new LinearLayout(this);
        boolean stackMapFooter = getResources().getConfiguration().screenWidthDp < 340
                || getResources().getConfiguration().fontScale > 1.2f;
        mapFooter.setOrientation(stackMapFooter ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        mapFooter.setGravity(Gravity.CENTER_VERTICAL);
        mapFooter.setPadding(dp(6), dp(3), dp(2), 0);
        mapCard.addView(mapFooter, new LinearLayout.LayoutParams(-1, -2));
        tripInfoText = text("Menyiapkan rute…", 11, "#536B83", false);
        tripInfoText.setPadding(0, dp(3), dp(5), dp(3));
        mapFooter.addView(tripInfoText, new LinearLayout.LayoutParams(stackMapFooter ? -1 : 0, -2, stackMapFooter ? 0 : 1));
        Button liveBtn = outlineButton("⌖  Pusatkan");
        liveBtn.setContentDescription("Pusatkan peta");
        liveBtn.setMinHeight(dp(48));
        liveBtn.setBackground(round("#EDF5FF", dp(12)));
        mapFooter.addView(liveBtn, new LinearLayout.LayoutParams(stackMapFooter ? -1 : -2, -2));
        liveBtn.setOnClickListener(v -> { if (mapView != null) mapView.fitAll(); });

        LinearLayout safetyRow = new LinearLayout(this);
        safetyRow.setOrientation(LinearLayout.HORIZONTAL);
        if (getResources().getConfiguration().screenWidthDp < 320
                || getResources().getConfiguration().fontScale > 1.15f) safetyRow.setOrientation(LinearLayout.VERTICAL);
        Button shareBtn = outlineButton("Bagikan trip"); shareBtn.setMinHeight(dp(48));
        shareTripButton = shareBtn;
        shareBtn.setTextColor(Color.WHITE);
        shareBtn.setBackground(roundStroke("#167CFF", "#167CFF", dp(12), 1));
        Button sosBtn = outlineButton("SOS · Bantuan"); sosBtn.setMinHeight(dp(48));
        sosBtn.setTextColor(Color.parseColor("#B54147"));
        sosBtn.setBackground(roundStroke("#FFF4F4", "#F2D8DB", dp(12), 1));
        boolean stackSafety = safetyRow.getOrientation() == LinearLayout.VERTICAL;
        safetyRow.addView(shareBtn, new LinearLayout.LayoutParams(stackSafety ? -1 : 0, -2, stackSafety ? 0 : 1));
        LinearLayout.LayoutParams sosLp = new LinearLayout.LayoutParams(stackSafety ? -1 : 0, -2, stackSafety ? 0 : 1); sosLp.setMargins(stackSafety ? 0 : dp(8),stackSafety ? dp(8) : 0,0,0);
        safetyRow.addView(sosBtn, sosLp);
        card.addView(safetyRow, tripRow(8));
        shareBtn.setOnClickListener(v -> shareTrip());
        sosBtn.setOnClickListener(v -> openSos());

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(54), dp(54));
        plp.gravity = Gravity.CENTER;
        page.addView(progressBar, plp);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private LinearLayout.LayoutParams tripRow(int top) {
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.topMargin = dp(top);
        return row;
    }

    private void loadTripPhoto(ImageView target,String raw,boolean vehicle){
        if(target==null||raw==null||raw.trim().isEmpty())return;
        final String value=raw.trim();if(value.equals(vehicle?loadedVehiclePhoto:loadedDriverPhoto))return;
        if(vehicle)loadedVehiclePhoto=value;else loadedDriverPhoto=value;
        final String url=value.startsWith("https://")||value.startsWith("http://")?value:BASE_URL+(value.startsWith("/")?value.substring(1):value);
        featureRuntime.execute(()->{java.net.HttpURLConnection connection=null;try{
            connection=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
            if(CustomerApi23Compat.contentLength(connection)>5*1024*1024)return;
            try(java.io.InputStream input=connection.getInputStream()){
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=4;
                final Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(input,null,options);
                if(bitmap!=null)featureRuntime.post(mainHandler,()->{if(!isFinishing()&&!isDestroyed()&&value.equals(vehicle?loadedVehiclePhoto:loadedDriverPhoto)){target.setScaleType(ImageView.ScaleType.CENTER_CROP);target.setImageBitmap(bitmap);target.setVisibility(View.VISIBLE);}});
            }
        }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}});
    }

    protected void startTrackingOnce() {
        if (trackingStarted || orderId.length() == 0) return;
        trackingStarted = true;
        setLoading(true);
        fetchDriverPosition();
        mainHandler.postDelayed(trackingRunnable, TRACKING_MS);
    }

    protected void fetchDriverPosition() {
        if (orderId.length() == 0 || foodStatusRouted || finishedCountdownStarted || isFinishing() || isDestroyed()) return;
        if (!trackingRequestInFlight.compareAndSet(false, true)) return;

        final long generation = trackingGeneration.incrementAndGet();
        featureRuntime.execute(() -> {
            try {
                final JSONObject res;
                if (orderSource.contains("pickup")) {
                    res = getJson(PICKUP_STATUS_URL + "?order_id=" + Uri.encode(orderId));
                } else {
                    JSONObject payload = new JSONObject().put("order_id", orderId)
                            .put("include_terminal", true);
                    res = postJson(CHECK_STATUS_URL, payload);
                }
                featureRuntime.post(mainHandler, () -> {
                    if (generation != trackingGeneration.get() || isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    if (res.optBoolean("success", false)) {
                        handleStatusResponse(res);
                    } else if (statusText != null) {
                        statusText.setVisibility(View.VISIBLE);
                        String message = res.optString("message", "");
                        statusText.setText(tripLayoutBuilt && CustomerOrderState.isTrip(lastStatus)
                                ? "Memeriksa pembaruan perjalanan…" : firstNonEmpty(message, "Memeriksa status pesanan…"));
                    }
                });
            } catch (Exception e) {
                TransivaCrashReporter.recordNetworkFailure(e,
                        orderSource.contains("pickup") ? "GET" : "POST",
                        orderSource.contains("pickup") ? PICKUP_STATUS_URL : CHECK_STATUS_URL);
                featureRuntime.post(mainHandler, () -> {
                    if (generation != trackingGeneration.get() || isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    if (statusText != null) { statusText.setVisibility(View.VISIBLE); statusText.setText("Koneksi tracking belum stabil. Mencoba lagi..."); }
                });
            } finally {
                trackingRequestInFlight.set(false);
            }
        }, () -> {
            trackingRequestInFlight.set(false);
            featureRuntime.post(mainHandler, () -> {
                if (generation == trackingGeneration.get()) setLoading(false);
            });
        });
    }



    protected void handleStatusResponse(JSONObject res) {
        if(!trackingOnly&&FoodOrderDecision.needsFoodScreen(res,orderId)){openFoodStatus(res);return;}
        JSONObject driver = res.optJSONObject("driver");
        if (driver == null) driver = new JSONObject();
        JSONObject order = res.optJSONObject("order");
        if (order == null) order = new JSONObject();

        String status = CustomerOrderState.laterOf(lastStatus, firstNonEmpty(res.optString("status", ""), order.optString("status", "")));
        if (CustomerOrderState.isEnded(status)) {
            if (!trackingOnly && CustomerOrderState.isTrip(lastStatus) && isFinishedStatus(status)) {
                RoyaltyRewardFeedback.completed(this, orderId, orderSource);
            }
            lastStatus = status;
            CustomerOrderBreadcrumbs.state(orderId, status, orderSource);
            showTripEnd(order);
            return;
        }
        if (!tripLayoutBuilt) {
            buildLayout();
            tripLayoutBuilt = true;
            mapView.onStartMap();
            mapView.onResumeMap();
            startRideProtectionIfEnabled();
        }
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
        loadTripPhoto(driverPhotoView,firstNonEmpty(driver.optString("driver_photo"),driver.optString("photo"),order.optString("driver_photo")),false);
        loadTripPhoto(vehiclePhotoView,firstNonEmpty(driver.optString("vehicle_photo"),order.optString("vehicle_photo")),true);
        lastDriverName = driverName;
        lastStatus = status;
        if (tripStatus != null) {
            try {
                JSONObject visualOrder = new JSONObject(order.toString());
                visualOrder.put("status", status); visualOrder.put("driver_name", driverName);
                if (visualOrder.optString("order_id").isEmpty()) visualOrder.put("order_id", orderId);
                if (visualOrder.optString("order_type").isEmpty()) visualOrder.put("order_type", res.optString("order_type"));
                tripStatus.bind(visualOrder, "", "");
            } catch (org.json.JSONException ignored) { }
        }
        CustomerOrderBreadcrumbs.state(orderId, status, orderSource);

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
        driverPlateText.setText("-".equals(plate) ? "Pelat belum tersedia" : "Pelat " + plate);
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
            if (!merchantLine.isEmpty()) {
                statusText.setText((statusText.getText().length() > 0 ? statusText.getText() + "\n" : "") + merchantLine);
                statusText.setVisibility(View.VISIBLE);
            }
        }

        agreedServerRoute=order.optJSONObject("server_route");
        updatePaymentControls(order, status);
        String assignedDriver = firstNonEmpty(order.optString("driver"), order.optString("driver_username"), driver.optString("username"));
        boolean canReport = !trackingOnly && !assignedDriver.isEmpty() && res.optBoolean("offline_report_available");
        reportOfflineButton.setVisibility(canReport ? View.VISIBLE : View.GONE);
        JSONObject reportOrder = order;
        reportOfflineButton.setOnClickListener(v -> confirmOfflineReport(reportOrder, assignedDriver));
        if (!trackingOnly) saveTripPrefs();

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

    private void confirmOfflineReport(JSONObject order, String assignedDriver) {
        if (reportOfflineInFlight) return;
        new TransivaAlertDialogBuilder(this).setTitle("Batalkan dan laporkan?")
                .setMessage("Pesanan ini akan dibatalkan dan ditinjau Transiva. Anda bisa memesan driver lain dari Beranda.")
                .setNegativeButton("Kembali", null)
                .setPositiveButton("Batalkan dan laporkan", (dialog, which) -> {
                    if (reportOfflineInFlight) return;
                    reportOfflineInFlight = true;
                    reportOfflineButton.setEnabled(false);
                    SessionManager session = new SessionManager(this);
                    int customerId;
                    try {
                        customerId = Integer.parseInt(session.getUserId());
                    } catch (NumberFormatException error) {
                        reportOfflineInFlight = false;
                        reportOfflineButton.setEnabled(true);
                        showInfo("Laporan belum tersimpan", "Sesi akun tidak ditemukan. Silakan masuk kembali.");
                        return;
                    }
                    CustomerActivityOrderAction.cancel(this, order, customerId, session.getUsername(), assignedDriver,
                            new CustomerActivityOrderAction.Callback() {
                                @Override public void onSuccess(String message) {
                                    reportOfflineInFlight = false;
                                    reportOfflineSubmitted = true;
                                    lastStatus = CustomerOrderState.CANCELED;
                                    showTripEnd(order);
                                }

                                @Override public void onError(String message) {
                                    reportOfflineInFlight = false;
                                    reportOfflineButton.setEnabled(true);
                                    if (!isFinishing() && !isDestroyed()) showInfo("Laporan belum tersimpan", message);
                                }
                            });
                }).show();
    }

    protected void pushAllMarkersToMap() {
        if (mapView == null || !mapReady) return;

        lastDataAlreadyPushed = true;
        String previewKey=pendingDestinationProposal==null?"":String.valueOf(pendingDestinationProposal.optLong("id"));
        if(!previewKey.equals(proposalMapKey)){
            mapView.clearPlaces();proposalMapKey=previewKey;
            if(pendingDestinationProposal!=null)mapView.addPlace(pendingDestinationProposal.optDouble("to_lat"),pendingDestinationProposal.optDouble("to_lng"),"Usulan tujuan tambahan","Menunggu persetujuan Anda",pendingDestinationProposal.optString("address"));
        }
        mapView.setWaypoints(destinationRouteStops);

        if (validCoord(pickupLat, pickupLng)) {
            if (mapView != null) mapView.setPickup(pickupLat, pickupLng, "Lokasi Penjemputan");
        }

        if (validCoord(deliveryLat, deliveryLng)) {
            if (mapView != null) mapView.setDelivery(deliveryLat, deliveryLng, additionalDestinationLabel.isEmpty()?"Tujuan":"Tujuan tambahan: "+additionalDestinationLabel);
        }

        if (validCoord(lastDriverLat, lastDriverLng)) {
            String popup = popupText(lastStatus);
            double[] target = getTarget(lastStatus);

            if (validCoord(target[0], target[1])) {
                requestStableRoute(target[0], target[1], false);
            }

            if (mapView != null) mapView.setTripDriver(lastDriverLat, lastDriverLng, lastBearing, "car".equals(activeDriverType), lastDriverName, popup);
        }

        if (!driverCameraFocused && validCoord(lastDriverLat,lastDriverLng)) {driverCameraFocused=true; firstFocus=true;}
        if (firstFocus && (validCoord(pickupLat,pickupLng) || validCoord(deliveryLat,deliveryLng) || validCoord(lastDriverLat,lastDriverLng))) {
            firstFocus = false;
            if (mapView != null) mapView.fitAll();
        }

        if (!validCoord(lastDriverLat, lastDriverLng) && (validCoord(pickupLat, pickupLng) || validCoord(deliveryLat, deliveryLng))) {
            tripInfoText.setText("Titik penjemputan dan pengantaran siap. Menunggu lokasi driver terbaru...");
        }
    }

    protected JSONObject agreedServerRoute;
    protected String additionalDestinationLabel="",proposalMapKey="";
    protected JSONObject pendingDestinationProposal;
    protected org.json.JSONArray destinationRouteStops=new org.json.JSONArray();
    protected boolean destinationRouteActive=false;
    protected long destinationRequestId=0;
    protected double destinationRequestedPrice=0;
    protected void confirmPriceChange(){
        final long shownId=destinationRequestId; final double shownPrice=destinationRequestedPrice;
        new TransivaAlertDialogBuilder(this).setTitle("Setujui tujuan dan biaya?")
            .setMessage(String.valueOf(paymentInfoText.getText())+"\n\nDengan menyetujui, tujuan tambahan dan total baru berlaku. Saldo TransPay tambahan dipotong jika menggunakan TransPay.")
            .setNegativeButton("Batal",null).setPositiveButton("Setujui",(d,w)->sendCustomerAction("approve_price",shownId,shownPrice)).show();
    }
    protected void updatePaymentControls(JSONObject order,String status){
        if (waitingView != null && !trackingOnly) waitingView.bind(order.optJSONObject("smart_waiting"), status);
        JSONObject extension=order.optJSONObject("destination_extension");
        pendingDestinationProposal=extension!=null && "pending".equals(extension.optString("status"))?extension:null;
        destinationRequestId=extension!=null && "pending".equals(extension.optString("status"))?extension.optLong("id"):0;
        boolean extensionTravelling=extension!=null && "approved".equals(extension.optString("status")) && extension.optInt("needs_arrival")==1;
        additionalDestinationLabel=extension!=null && "approved".equals(extension.optString("status"))?extension.optString("address",""):"";
        destinationRouteActive=extensionTravelling;
        JSONObject eco=order.optJSONObject("ecosystem");destinationRouteStops=eco==null?new org.json.JSONArray():eco.optJSONArray("waypoints");
        if(destinationRouteStops==null)destinationRouteStops=new org.json.JSONArray();
        if (trackingOnly) return;
        if(paymentInfoText==null||receivedButton==null) return;
        String method=firstNonEmpty(order.optString("payment_method",""),"cash").toLowerCase(Locale.US);
        boolean nonCash=method.equals("balance")||method.contains("transpay")||method.contains("transiva_pay")||method.equals("wallet")||method.equals("saldo");
        double price=order.optDouble("price",0), original=order.optDouble("original_price",price), requested=order.optDouble("price_change_requested",0);
        destinationRequestedPrice=requested;
        String change=order.optString("price_change_status","none").toLowerCase(Locale.US), reason=order.optString("price_change_reason","");
        String line = (nonCash ? "TransPay · sudah dibayar" : "Tunai · bayar ke driver")
                + (price > 0 ? "  ·  " + rupiah(price) : "");
        String proposal=CustomerOrderChangeState.summary(order);
        if(!proposal.isEmpty()) line+="\n"+proposal;
        if(extensionTravelling && statusText!=null){statusText.setText("Dalam perjalanan ke tujuan tambahan");statusText.setVisibility(View.VISIBLE);}
        if(approvePriceButton!=null)approvePriceButton.setText(destinationRequestId>0?"Setujui Tujuan & Biaya":"Setujui Harga");
        paymentInfoText.setText(line); paymentInfoText.setVisibility(View.VISIBLE); paymentInfoText.setBackground(round("#EFF6FF",dp(14)));
        receivedButton.setVisibility(CustomerOrderChangeState.canReceive(order) ? View.VISIBLE:View.GONE);
        View priceActions=null; android.view.ViewParent par=approvePriceButton==null?null:approvePriceButton.getParent(); if(par instanceof View) priceActions=(View)par;
        if(priceActions!=null) priceActions.setVisibility(CustomerOrderChangeState.pending(order)?View.VISIBLE:View.GONE);
    }
    protected void sendCustomerAction(String action){sendCustomerAction(action,destinationRequestId,destinationRequestedPrice);}
    protected void sendCustomerAction(String action,long requestId,double expectedPrice){
        if(orderId.isEmpty()) return; setLoading(true); featureRuntime.execute(()->{ try{
            JSONObject p=new JSONObject(); p.put("order_id",orderId); p.put("source",orderSource.contains("pickup")?"pickup_orders":"orders"); p.put("action",action); if(requestId>0)p.put("destination_request_id",requestId); if("approve_price".equals(action))p.put("expected_price",expectedPrice);
            JSONObject r=postJson(CUSTOMER_ACTION_URL,p); boolean ok=r.optBoolean("success",false); String m=firstNonEmpty(r.optString("message",""),ok?"Berhasil":"Gagal");
            featureRuntime.post(mainHandler, ()->{setLoading(false); showInfo(ok?"Berhasil":"Gagal",m); fetchDriverPosition();});
        }catch(Exception e){TransivaCrashReporter.recordNetworkFailure(e,"POST",CUSTOMER_ACTION_URL);featureRuntime.post(mainHandler, ()->{setLoading(false);showInfo("Gagal",CustomerOrderChangeState.error(e));});}});
    }
    protected String rupiah(double value){
        return CustomerCommonFormatters.rupiahCompactPrefix(value);
    }

    protected boolean drawAgreedServerRoute(){
        JSONObject payload=agreedServerRoute==null?null:agreedServerRoute.optJSONObject("route");JSONArray routes=payload==null?null:payload.optJSONArray("routes");JSONObject route=routes==null?null:routes.optJSONObject(0);
        JSONObject geometry=route==null?null:route.optJSONObject("geometry");JSONArray coords=geometry==null?null:geometry.optJSONArray("coordinates");
        if(mapView==null||!mapReady||coords==null||coords.length()<2)return false;
        JSONArray points = new JSONArray();
        try {
            for (int i = 0; i < coords.length(); i++) {
                JSONArray xy = coords.optJSONArray(i);
                if (xy == null || xy.length() < 2) return false;
                double latitude = xy.optDouble(1, Double.NaN);
                double longitude = xy.optDouble(0, Double.NaN);
                if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                        || Double.isNaN(longitude) || Double.isInfinite(longitude)
                        || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return false;
                points.put(new JSONArray().put(latitude).put(longitude));
            }
        } catch (org.json.JSONException invalidGeometry) {
            return false;
        }
        if (points.length() < 2) return false;
        mapView.drawOsrmRoute(points,lastStatus);
        if (tripInfoText != null) {
            tripInfoText.setText("Rute pesanan · " + formatTripDistance(route.optDouble("distance"))
                    + " · " + Math.max(1, (int)Math.ceil(route.optDouble("duration") / 60)) + " menit");
        }
        return true;
    }
    protected void requestStableRoute(double toLat,double toLng,boolean force){
        if(CustomerOrderState.targetsDelivery(lastStatus) && drawAgreedServerRoute())return;
        if(mapView==null || !mapReady || !validCoord(lastDriverLat,lastDriverLng) || !validCoord(toLat,toLng)) return;
        if(routeRequestInFlight) return;
        long now=System.currentTimeMillis();
        float movedFrom=distanceMeters(lastRouteFromLat,lastRouteFromLng,lastDriverLat,lastDriverLng);
        float movedTarget=distanceMeters(lastRouteToLat,lastRouteToLng,toLat,toLng);
        String requestKey = routeTargetKey();
        boolean sameTarget = requestKey.equals(appliedRouteKey) && movedTarget < 10f;
        if (!force && sameTarget && (now-lastRouteRequestAt<15000L || (movedFrom<25f && now-lastRouteRequestAt<30000L))) return;
        if (!force && now-lastRouteRequestAt<5000L && lastStatus.equals(lastRouteStatus) && movedTarget<10f) return;
        routeRequestInFlight=true; lastRouteRequestAt=now;
        final double fromLat=lastDriverLat,fromLng=lastDriverLng;
        final String status=lastStatus;
        final long generation = routeGeneration.get();
        final String targetKey = requestKey;
        final boolean extensionRoute=destinationRouteActive;
        final org.json.JSONArray stops=destinationRouteStops;final double finalLat=deliveryLat,finalLng=deliveryLng;
        featureRuntime.execute(() -> {
            try{
                org.json.JSONArray points=new org.json.JSONArray();double meters=0,seconds=0,a=fromLat,b=fromLng;
                if(extensionRoute){for(int i=0;i<stops.length();i++){
                    JSONObject stop=stops.optJSONObject(i);if(stop==null||"arrived".equals(stop.optString("status")))continue;
                    double x=stop.optDouble("latitude"),y=stop.optDouble("longitude");if(!validCoord(x,y))continue;
                    StableRouteEngine.Result leg=StableRouteEngine.fetch(a,b,x,y);
                    for(int n=0;n<leg.latLngPoints.length();n++)points.put(leg.latLngPoints.opt(n));
                    meters+=leg.distanceMeters;seconds+=leg.durationSeconds;a=x;b=y;
                }}
                StableRouteEngine.Result r=StableRouteEngine.fetch(a,b,extensionRoute?finalLat:toLat,extensionRoute?finalLng:toLng);
                for(int n=0;n<r.latLngPoints.length();n++)points.put(r.latLngPoints.opt(n));meters+=r.distanceMeters;seconds+=r.durationSeconds;
                final double distance = meters, duration = seconds;
                featureRuntime.post(mainHandler, () -> {
                    if (generation != routeGeneration.get() || !targetKey.equals(routeTargetKey()) || isFinishing() || isDestroyed() || points.length()<2) return;
                    lastRouteFromLat=fromLat; lastRouteFromLng=fromLng; lastRouteToLat=toLat; lastRouteToLng=toLng; lastRouteStatus=status;
                    appliedRouteKey=targetKey;
                    if (mapView != null) mapView.drawOsrmRoute(points, status);
                    if (tripInfoText != null) {
                        String eta = duration > 0 ? " · " + Math.max(1, (int)Math.ceil(duration / 60)) + " menit" : "";
                        tripInfoText.setText(CustomerOrderState.ARRIVED_PICKUP.equals(status) ? "Driver di titik jemput"
                                : "Estimasi driver · " + formatTripDistance(distance) + eta);
                    }
                });
            }catch(Exception error){ TransivaCrashReporter.record(error,"customer_route","stable_route"); } finally{ routeRequestInFlight=false; }
        }, () -> routeRequestInFlight=false);
    }

    private String formatTripDistance(double meters) {
        if (meters < 100) return Math.max(0, Math.round(meters)) + " m";
        return String.format(new Locale("id", "ID"), "%.1f km", meters / 1000);
    }

    private String routeTargetKey() {
        return lastStatus + "|" + pickupLat + "," + pickupLng + "|" + deliveryLat + "," + deliveryLng
                + "|" + destinationRouteActive + "|" + String.valueOf(destinationRouteStops) + "|" + String.valueOf(agreedServerRoute);
    }

    protected float distanceMeters(double aLat,double aLng,double bLat,double bLng){
        return CustomerGeoMath.distanceMetersOrFar(aLat, aLng, bLat, bLng);
    }

    protected String resolveDriverType(JSONObject order, JSONObject driver) {
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


    protected boolean isFinishedStatus(String status) {
        return CustomerOrderState.isFinished(status);
    }

    protected double[] getTarget(String status) {
        if (CustomerOrderState.targetsDelivery(status) && validCoord(deliveryLat, deliveryLng)) {
            return new double[]{deliveryLat, deliveryLng};
        }
        return new double[]{pickupLat, pickupLng};
    }

    protected void setStatusText(String status, String driverName, boolean hasLocation) {
        // The shared hero owns the status title and explanation. Keep tracking health separate.
        boolean waitingForLocation = !hasLocation && !CustomerOrderState.isEnded(status);
        statusText.setText(waitingForLocation ? "Lokasi driver sedang diperbarui…" : "");
        statusText.setVisibility(waitingForLocation ? View.VISIBLE : View.GONE);
    }

    protected String popupText(String status) {
        String s = firstNonEmpty(status, "").trim().toLowerCase(Locale.US);
        if ("driver_accepted".equals(s) || "accepted".equals(s) || "assigned".equals(s) || "taken".equals(s))
            return "🤝 Driver menerima pesananmu";
        if ("arrived_pickup".equals(s) || "arrived".equals(s)) return "📍 Driver sudah tiba menjemput";
        if ("picked_up".equals(s) || "on_trip".equals(s) || "on_delivery".equals(s)) return "🛵 Perjalanan sedang berlangsung";
        if ("arrived_delivery".equals(s)) return "🏁 Driver sudah tiba di tujuan";
        if (isFinishedStatus(s)) return "✨ Pesanan selesai";
        return OrderStatusPresentation.label(s, "");
    }


    private void showTripEnd(JSONObject order) {
        if (finishedCountdownStarted) return;
        finishedCountdownStarted = true;
        trackingStarted = false;
        mainHandler.removeCallbacks(trackingRunnable);
        routeGeneration.incrementAndGet();
        boolean finished = isFinishedStatus(lastStatus);
        if (clearActiveOrder()) stopRideProtection();
        if (mapView != null) {
            mapView.onPauseMap();
            mapView.onStopMap();
            mapView.onDestroyMap();
            mapView = null;
            mapReady = false;
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F3F8FF"));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(18), dp(24), dp(18), dp(24));
        scroll.addView(page, new android.widget.ScrollView.LayoutParams(-1, -2));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(16), dp(23), dp(16), dp(18));
        panel.setBackground(roundStroke("#FFFFFF", "#DCE8F6", dp(22), 1));
        page.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        TextView mark = text(finished ? "✓" : "×", 33, finished ? "#087758" : "#9E3547", true);
        mark.setGravity(Gravity.CENTER);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mark.setBackground(round(finished ? "#E5F8EE" : "#FCECEF", dp(28)));
        panel.addView(mark, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView brand = text("TRANSIVA", 11, "#1268D5", true);
        brand.setGravity(Gravity.CENTER);
        brand.setLetterSpacing(.12f);
        panel.addView(brand, tripRow(12));
        String service = order.optString("order_type", "");
        boolean food = "food".equalsIgnoreCase(service) || "transfood".equalsIgnoreCase(service)
                || orderId.toUpperCase(Locale.ROOT).startsWith("FOOD-");
        String endTitle = finished ? (food ? "Pesanan makanan selesai" : "Pesanan selesai") : "Pesanan dibatalkan";
        TextView title = text(endTitle, 23, "#0B3A78", true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, tripRow(3));
        TextView copy = text(finished ? "Terima kasih sudah menggunakan layanan Transiva. Sampai jumpa di perjalanan berikutnya!"
                : "Perjalanan ini telah berakhir. Rincian pesanan tersedia di Aktivitas.", 14, "#536B83", false);
        copy.setGravity(Gravity.CENTER);
        panel.addView(copy, tripRow(7));

        if (finished) {
            PremiumOrderStatusView progress = new PremiumOrderStatusView(this, true);
            progress.setActionVisible(false);
            progress.setReferenceVisible(false);
            progress.bind(order, "", "");
            progress.bindProgress(CustomerOrderState.FINISHED);
            panel.addView(progress, tripRow(14));
            if (!trackingOnly) addCompletionPayment(panel, order);
            finishHint = text("Kembali ke beranda dalam 5 detik", 12, "#536B83", false);
            finishHint.setGravity(Gravity.CENTER);
            panel.addView(finishHint, tripRow(12));
        }

        if (reportOfflineSubmitted) {
            Button reports = smallButton("Konfirmasi perjalanan", "#EFF6FF", "#1D4ED8", "#BFDBFE");
            reports.setOnClickListener(v -> CustomerOfflineOrderReports.open(this));
            panel.addView(reports, tripRow(10));
        }
        Button home = smallButton("Kembali ke beranda", "#1268D5", "#FFFFFF", "#1268D5");
        home.setMinHeight(dp(48));
        home.setOnClickListener(v -> goHomeAfterFinished());
        panel.addView(home, tripRow(10));
        setContentView(scroll);
        CustomerAppSettings.apply(this);
        if (finished && resumed) mainHandler.post(finishTick);
    }

    private void addCompletionPayment(LinearLayout panel, JSONObject order) {
        double total = order.optDouble("price", Double.NaN);
        if (!Double.isFinite(total) || total < 0) return;
        String method = order.optString("payment_label", "").trim();
        if (method.isEmpty()) {
            String value = order.optString("payment_method", "");
            if ("balance".equalsIgnoreCase(value)) method = "TransPay";
            else if ("cash".equalsIgnoreCase(value)) method = "Tunai";
        }
        JSONObject waiting = order.optJSONObject("smart_waiting");
        long fee = order.optLong("waiting_fee_applied", waiting == null ? 0L : waiting.optLong("applied_fee"));
        LinearLayout receipt = new LinearLayout(this);
        receipt.setOrientation(LinearLayout.VERTICAL);
        receipt.setPadding(dp(12), dp(10), dp(12), dp(10));
        receipt.setBackground(roundStroke("#F5F9FF", "#DCE8F6", dp(14), 1));
        receipt.addView(text(method.isEmpty() ? "Total akhir" : "Total akhir · " + method, 12, "#536B83", false));
        receipt.addView(text(rupiah(total), 19, "#0B3A78", true));
        if (fee > 0) {
            receipt.addView(text("Termasuk biaya tunggu " + rupiah(fee), 11, "#536B83", false));
        }
        panel.addView(receipt, tripRow(8));
    }

    protected void goHomeAfterFinished() {
        mainHandler.removeCallbacks(finishTick);
        Intent home = new Intent(this, CustomerDashboardActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }

    private void finishShareRequest() {
        shareBusy = false;
        if (shareTripButton != null) { shareTripButton.setEnabled(true); shareTripButton.setText("Bagikan trip"); }
        setLoading(false);
    }

    protected void shareTrip() {
        if (shareBusy) return;
        if (orderId == null || orderId.trim().isEmpty()) { showInfo("Bagikan Trip", "ID order belum tersedia."); return; }
        final String sharedOrderId = orderId;
        final CustomerTransportSession owner = CustomerTransportSession.capture(this);
        shareBusy = true;
        if (shareTripButton != null) { shareTripButton.setEnabled(false); shareTripButton.setText("Menyiapkan tautan…"); }
        featureRuntime.execute(() -> {
            try {
                JSONObject req = new JSONObject().put("order_id", sharedOrderId)
                        .put("source", orderSource.contains("pickup") ? "pickup_orders" : "orders");
                JSONObject res = TransivaHttpRepository.postJson(this, CREATE_SHARED_TRIP_URL, req, TIMEOUT_MS, owner);
                final String liveUrl = res.optString("share_url", "");
                final Uri uri = Uri.parse(liveUrl);
                final boolean ok = res.optBoolean("success", false) && "https".equals(uri.getScheme())
                        && "transiva.my.id".equals(uri.getHost());
                featureRuntime.post(mainHandler, () -> {
                    if (isFinishing() || isDestroyed() || !owner.matches(this)) return;
                    finishShareRequest();
                    if (!ok) { showInfo("Bagikan Trip", res.optString("message", "Gagal membuat tautan aman.")); return; }
                    try {
                        String text = "Pantau perjalanan Transiva saya.\nStatus dan lokasi driver diperbarui otomatis.\nTautan aktif maksimal 24 jam: " + liveUrl;
                        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_SUBJECT, "Perjalanan Live Transiva"); send.putExtra(Intent.EXTRA_TEXT, text);
                        startActivity(Intent.createChooser(send, "Bagikan perjalanan live"));
                    } catch (Exception error) { showInfo("Bagikan Trip", "Tidak dapat membuka menu berbagi."); }
                });
            } catch (Exception error) {
                final String message = error instanceof TransivaHttpRepository.ServerResponseException
                        ? error.getMessage() : "Tautan belum dapat dibuat. Periksa koneksi lalu coba lagi.";
                featureRuntime.post(mainHandler, () -> {
                    if (isFinishing() || isDestroyed() || !owner.matches(this)) return;
                    finishShareRequest(); showInfo("Bagikan Trip", message);
                });
            }
        }, () -> featureRuntime.post(mainHandler, () -> {
            if (isFinishing() || isDestroyed() || !owner.matches(this)) return;
            finishShareRequest(); showInfo("Bagikan Trip", "Permintaan belum dimulai. Coba kembali.");
        }));
    }

    protected void openSos() {
        new TransivaAlertDialogBuilder(this).setTitle("🆘 Bantuan Darurat")
                .setMessage("Hubungi layanan darurat hanya saat benar-benar diperlukan. Anda juga dapat membagikan perjalanan kepada keluarga.")
                .setNeutralButton("Bagikan Trip", (d,w) -> shareTrip())
                .setNegativeButton("Batal", null)
                .setPositiveButton("Telepon 112", (d,w) -> {
                    try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))); } catch (Exception ignored) { }
                }).show();
    }

    protected void openLiveDriver() {
        if (orderId == null || orderId.trim().isEmpty()) {
            showInfo("Live Driver", "ID order belum tersedia.");
            return;
        }
        if(mapView!=null && mapReady) mapView.fitAll();
    }

    protected void saveTripPrefs() {
        try {
            getSharedPreferences("transiva", MODE_PRIVATE).edit()
                    .putString("active_order_id", orderId)
                    .putString("active_order_source", orderSource)
                    .putString("active_driver_type", activeDriverType)
                    .putString("active_order_status", lastStatus == null ? "" : lastStatus)
                    .putString("pickup_lat", String.valueOf(pickupLat))
                    .putString("pickup_lng", String.valueOf(pickupLng))
                    .putString("delivery_lat", String.valueOf(deliveryLat))
                    .putString("delivery_lng", String.valueOf(deliveryLng))
                    .apply();
        } catch (Exception ignored) {}
    }



    private boolean clearActiveOrder() {
        SharedPreferences prefs = getSharedPreferences("transiva", MODE_PRIVATE);
        if (trackingOnly || !orderId.equals(prefs.getString("active_order_id", ""))) return false;
        prefs.edit()
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
                    .remove("active_order_source")
                    .apply();
        return true;
    }

    private JSONObject getJson(String urlText) throws Exception {
        return TransivaHttpRepository.getJson(this, urlText, TIMEOUT_MS);
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        return TransivaHttpRepository.postJson(this, urlText, payload, TIMEOUT_MS);
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
        return CustomerGeoMath.valid(lat, lng);
    }

    private double calcBearing(double lat1, double lng1, double lat2, double lng2) {
        return CustomerGeoMath.bearingOrFallback(lat1, lng1, lat2, lng2, lastBearing);
    }

    private double smoothBearing(double oldB, double newB) {
        return CustomerGeoMath.smoothBearing(oldB, newB);
    }

    private double normalize(double v) {
        return CustomerGeoMath.normalizeDegrees(v);
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
        return CustomerCommonFormatters.firstBasic(values);
    }

    private Button outlineButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setStateListAnimator(null);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor("#0B7CFF"));
        b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1));
        return b;
    }

    private Button smallButton(String value, String bg, String fg, String stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setStateListAnimator(null);
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


    private int dp(int v) {
        return CustomerUiPrimitives.dp(this, v);
    }

    private void showInfo(String title, String message) {
        try {
            new TransivaAlertDialogBuilder(this)
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
        if (finishedCountdownStarted) goHomeAfterFinished();
        else goToHome();
    }

    @Override protected void onPause() {
        resumed = false;
        mainHandler.removeCallbacks(finishTick);
        featureRuntime.onPause();
        if (mapView != null) mapView.onPauseMap();
        mainHandler.removeCallbacks(trackingRunnable);
        trackingGeneration.incrementAndGet(); // invalidate late network callbacks
        routeGeneration.incrementAndGet();
        super.onPause();
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStartMap();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        featureRuntime.onResume();
        if (mapView != null) mapView.onResumeMap();
        CustomerAppSettings.apply(this);
        mainHandler.removeCallbacks(trackingRunnable);
        if (finishHint != null && finishedCountdownStarted) mainHandler.post(finishTick);
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
        routeGeneration.incrementAndGet();
        featureRuntime.destroy();
        trackingGeneration.incrementAndGet();
        trackingRequestInFlight.set(false);
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
