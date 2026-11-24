package com.transiva.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
class TransPickupActivityScreenCore extends CustomerAdaptiveFragmentActivity {


    protected final CustomerLifecycleNetworkScope networkScope =
            new CustomerLifecycleNetworkScope();


    protected static final String BASE_URL = "https://transiva.my.id/";
    protected static final String PREF_NAME = "transiva";
    protected static final int TIMEOUT_MS = 20000;
    protected static final int REQ_LOCATION = 4801;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected FrameLayout page;
    protected LinearLayout root;
    protected LinearLayout bottomSheet;
    private ScrollView packageScroll;
    private LinearLayout checkoutFooter;
    private org.json.JSONArray waypoints=new org.json.JSONArray();
    private int editingStop=-1;
    private Button stopMapBtn;
    private LinearLayout pointFields, stopRows, routeUtility;
    private TextView gpsStatus;
    private TransSendInlinePointSearch pointSearch;
    private double previewLat=Double.NaN,previewLng=Double.NaN;
    private String previewAddress="";
    private int maxStops=2;
    private Button detailsButton;
    private boolean detailsOpen;
    private int locationGeneration;
    private String pickupAddress = "";
    private String deliveryAddress = "";
    protected ProgressBar progressBar;
    protected TransivaGoogleMapView mapView;
    private String courierContactId = "";
    protected Button pickupMapBtn, deliveryMapBtn, confirmPointBtn, gpsMapBtn;
    protected boolean mapReady = false;
    protected String mapSelectionMode = "pickup";

    protected EditText itemNameInput, itemValueInput, receiverNameInput, receiverPhoneInput, noteInput;
    protected TextView priceText, distanceText;
    protected Button smallBtn, mediumBtn, fragileYesBtn, fragileNoBtn, cashBtn, balanceBtn, orderBtn;

    protected String username = "User";
    protected int userId = 0;
    protected double pickupLat = 0, pickupLng = 0, deliveryLat = 0, deliveryLng = 0;
    protected String vehicleType = "bike";
    protected String packageSize = "small";
    protected String itemCategory = "Paket";
    protected boolean fragile = false;
    protected String paymentMethod = "cash";
    protected double deliveryFee = 0, distanceKm = 0;
    private final CustomerFareQuoteGate pickupFareGate=new CustomerFareQuoteGate();
    private boolean pickupScreenClosed;
    private boolean submittingOrder = false;
    private boolean recoveryScreen;
    private boolean recoveryBusy;
    private String completedOrderId = "";
    private android.app.AlertDialog recoveryReceipt;
    protected LocationManager locationManager;
    private LocationListener locationListener;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerCustomBackNavigation(this::handleBack);
        try {
            CustomerWindowPolicy.status(getWindow(), Color.parseColor("#072450"));
            CustomerWindowPolicy.navigation(getWindow(), Color.parseColor("#071426"));
            if (android.os.Build.VERSION.SDK_INT >= 23) CustomerWindowPolicy.appearance(getWindow(), 0);
        } catch (Exception ignored) {}
        loadSession();
        if (ReliableOrderClient.hasPickupRecovery(this)) {
            recoveryScreen = true;
            showRecovery(false);
            return;
        }
        if (savedInstanceState != null
                && Integer.toString(userId).equals(savedInstanceState.getString("sendRecoveryOwner", ""))
                && !savedInstanceState.getString("sendCompletedOrder", "").isEmpty()) {
            startActivity(new Intent(this, CustomerHistoryActivity.class));
            finish();
            return;
        }
        buildBase();
        buildForm();
        String contactId = getIntent().getStringExtra(TransSendContactClient.EXTRA_CONTACT);
        if (contactId != null && contactId.matches("PUD[0-9A-F]{24}")) courierContactId = contactId;
        if (savedInstanceState == null && !courierContactId.isEmpty()) loadCourierPoints();
        restoreDraft(savedInstanceState);
        updateChoices();
        renderStage();
        startMap();
    }

    private void loadCourierPoints() {
        Intent pickup = getIntent().getParcelableExtra("contact_pickup");
        Intent delivery = getIntent().getParcelableExtra("contact_delivery");
        if (pickup == null || delivery == null) return;
        pickupLat = pickup.getDoubleExtra("lat", 0);
        pickupLng = pickup.getDoubleExtra("lng", 0);
        deliveryLat = delivery.getDoubleExtra("lat", 0);
        deliveryLng = delivery.getDoubleExtra("lng", 0);
        if (!hasRoutePoints()) return;
        String item=getIntent().getStringExtra("tiva_item");
        if (item!=null) itemNameInput.setText(item);
        pickupAddress = firstNonEmpty(pickup.getStringExtra("address"), formatCoordinate(pickupLat, pickupLng));
        deliveryAddress = firstNonEmpty(delivery.getStringExtra("address"), formatCoordinate(deliveryLat, deliveryLng));
        mapSelectionMode = "order";
        detailsOpen = true;
    }

    protected void loadSession() {
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

    protected void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F3F8FF"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setFocusableInTouchMode(true);
        page.addView(layout, new FrameLayout.LayoutParams(-1, -1));

        buildMapHeader(layout);
        FrameLayout viewport = new FrameLayout(this);
        layout.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        mapView = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.PICKER);
        mapView.useNumberedRideStops();
        mapView.useExternalPickerAction();
        mapView.setCenterActionListener(this::confirmMapPoint);
        mapView.setGestureListener(new TransivaGoogleMapView.GestureListener() {
            @Override public void onGestureStart() { locationGeneration++; previewAddress=""; }
            @Override public void onGestureEnd() {}
        });
        viewport.setBackground(roundStroke("#EAF4FF","#AFCFF2",dp(14),1));
        viewport.setClipToOutline(true);
        viewport.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        viewport.addOnLayoutChangeListener((v, l, top, r, b, oldL, oldTop, oldR, oldB) -> {
            if (isCheckout() && (r-l != oldR-oldL || b-top != oldB-oldTop)) fitCheckout();
        });
        gpsMapBtn = outlineButton("⌖");
        gpsMapBtn.setTextSize(20);
        FrameLayout.LayoutParams gpsLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.BOTTOM);
        gpsLp.setMargins(0,0,dp(10),dp(10));
        viewport.addView(gpsMapBtn, gpsLp);
        gpsMapBtn.setOnClickListener(v -> { if (isCheckout()) fitCheckout(); else loadPickupLocation(); });

        buildBottomSheet(layout);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams loading = new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM);
        viewport.addView(progressBar, loading);

        page.addOnLayoutChangeListener((v, l, top, r, b, oldL, oldTop, oldR, oldB) -> {
            if (b-top == oldB-oldTop) return;
            android.view.ViewGroup.LayoutParams params = packageScroll.getLayoutParams();
            int height = Math.min(dp(235), Math.max(dp(105), (b-top) / 3));
            if (params.height != height) {
                params.height = height;
                packageScroll.setLayoutParams(params);
            }
        });
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private void startMap() {
        mapView.initialize(null, new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat, double lng) {
                if (pickupScreenClosed) return;
                mapReady = true;
                renderStage();
                if (validCoordinate(pickupLat, pickupLng)) mapView.setPickup(pickupLat, pickupLng, pickupAddress);
                if (validCoordinate(deliveryLat, deliveryLng)) mapView.setDelivery(deliveryLat, deliveryLng, deliveryAddress);
                mapView.setWaypoints(waypoints);
                if (isCheckout()) {
                    fitCheckout();
                    drawPickupRoute();
                } else if ("stop".equals(mapSelectionMode) && waypoints.optJSONObject(editingStop)!=null) {
                    JSONObject stop=waypoints.optJSONObject(editingStop);
                    mapView.moveTo(stop.optDouble("latitude"),stop.optDouble("longitude"),17f);
                } else if ("delivery".equals(mapSelectionMode) && validCoordinate(deliveryLat, deliveryLng)) {
                    mapView.moveTo(deliveryLat, deliveryLng, 17f);
                } else if (validCoordinate(pickupLat, pickupLng)) {
                    mapView.moveTo(pickupLat, pickupLng, 17f);
                } else {
                    loadPickupLocation();
                }
            }
            @Override public void onCenterChanged(double lat, double lng) { }
        });

    }

    private void buildMapHeader(LinearLayout parent) {
        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Color.WHITE);
        parent.addView(header,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout titleRow=row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF072450,0xFF075FE3}));
        CustomerAppSettings.preserveArtwork(titleRow);
        header.addView(titleRow);
        Button back=outlineButton("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(27);
        back.setPadding(0,0,0,0);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription("Kembali");
        back.setOnClickListener(v->handleBack());
        titleRow.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        titleRow.addView(text("TransSend",getResources().getConfiguration().screenHeightDp<700?18:20,
                "#FFFFFF",true),new LinearLayout.LayoutParams(0,-2,1));

        ScrollView editor=new ScrollView(this){
            @Override protected void onMeasure(int width,int height) {
                int available=getResources().getConfiguration().screenHeightDp;
                int limit=dp(Math.max(100,Math.min(350,Math.round(available*.46f))));
                if (MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED) limit=Math.min(limit,MeasureSpec.getSize(height));
                super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
            }
        };
        editor.setVerticalScrollBarEnabled(false);
        header.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        pointFields=new LinearLayout(this);
        pointFields.setOrientation(LinearLayout.VERTICAL);
        pointFields.setPadding(dp(10),dp(5),dp(10),dp(4));
        editor.addView(pointFields,new ScrollView.LayoutParams(-1,-2));

        pickupMapBtn=routeButton("#FAF8F2","#EDE7DC","#116138",R.drawable.ic_booking_pickup,0);
        deliveryMapBtn=routeButton("#FFF1EC","#F3E0D8","#B8201A",R.drawable.ic_booking_destination,R.drawable.ic_smart_search);
        pointFields.addView(pickupMapBtn);
        stopRows=new LinearLayout(this);
        stopRows.setOrientation(LinearLayout.VERTICAL);
        pointFields.addView(stopRows);
        LinearLayout.LayoutParams deliveryParams=new LinearLayout.LayoutParams(-1,-2);
        deliveryParams.topMargin=dp(4);
        pointFields.addView(deliveryMapBtn,deliveryParams);
        pickupMapBtn.setOnClickListener(v->openRoutePoint("pickup",-1));
        deliveryMapBtn.setOnClickListener(v->openRoutePoint("delivery",-1));
        routeUtility=row();
        routeUtility.setGravity(Gravity.CENTER_VERTICAL);
        gpsStatus=text("GPS sedang mencari…",11,"#875217",false);
        gpsStatus.setGravity(Gravity.CENTER_VERTICAL);
        gpsStatus.setMinHeight(dp(44));
        gpsStatus.setCompoundDrawablePadding(dp(5));
        gpsStatus.setCompoundDrawablesRelative(glyph(R.drawable.ic_booking_gps,14),null,null,null);
        gpsStatus.setOnClickListener(v->{if(isCheckout()) selectMapMode("pickup");loadPickupLocation();});
        routeUtility.addView(gpsStatus,new LinearLayout.LayoutParams(0,-2,1));
        stopMapBtn=outlineButton("+ Singgah");
        stopMapBtn.setTextColor(0xff95510c);
        stopMapBtn.setBackground(roundStroke("#FFF7E8","#EAC987",dp(18),1));
        stopMapBtn.setOnClickListener(v->{
            if(waypoints.length()<maxStops) openRoutePoint("stop",waypoints.length());
        });
        routeUtility.addView(stopMapBtn,new LinearLayout.LayoutParams(-2,-2));
        pointFields.addView(routeUtility);

        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject response=TransivaHttpRepository.postJson(this,ApiConfig.server("ride_stop_config.php"),
                        new JSONObject().put("service_type","Transbike"),10000);
                JSONObject config=response.optJSONObject("stop_pricing");
                if (config==null) config=response.optJSONObject("data");
                int limit=(config==null?response:config).optInt("max_stops",2);
                runOnUiThread(()->{
                    if (pickupScreenClosed) return;
                    maxStops=Math.max(0,Math.min(2,limit));
                    updateStopButton();
                });
            } catch (Exception ignored) { }
        });
    }

    private Button routeButton(String fill,String border,String color,int startIcon,int endIcon) {
        Button button=outlineButton("");
        button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        button.setTextColor(Color.parseColor(color));
        button.setMaxLines(2);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setPaddingRelative(dp(10),dp(4),dp(8),dp(4));
        button.setCompoundDrawablePadding(dp(8));
        button.setCompoundDrawablesRelative(glyph(startIcon,17),null,glyph(endIcon,18),null);
        button.setBackground(roundStroke(fill,border,dp(12),1));
        button.setElevation(dp(1));
        return button;
    }

    private android.graphics.drawable.Drawable glyph(int resource,int size) {
        if(resource==0)return null;
        android.graphics.drawable.Drawable drawable=getDrawable(resource);
        if(drawable!=null)drawable.setBounds(0,0,dp(size),dp(size));
        return drawable;
    }

    private void updateStopButton() {
        stopMapBtn.setVisibility(waypoints.length()<maxStops && !"stop".equals(mapSelectionMode)
                ?View.VISIBLE:View.GONE);
    }

    private void renderStopRows() {
        stopRows.removeAllViews();
        int count=waypoints.length();
        if ("stop".equals(mapSelectionMode) && editingStop==count) count++;
        for (int index=0;index<count;index++) {
            final int position=index;
            JSONObject saved=waypoints.optJSONObject(index);
            LinearLayout line=row();
            line.setBackground(roundStroke("#FFF7E8","#EAC987",dp(12),1));
            LinearLayout.LayoutParams lineParams=new LinearLayout.LayoutParams(-1,-2);
            lineParams.topMargin=dp(4);
            stopRows.addView(line,lineParams);
            Button select=routeButton("#FFF7E8","#FFF7E8","#95510C",0,0);
            select.setElevation(0);
            setPointLabel(select,"●  Singgah "+(index+1),
                    saved==null?"Cari tempat / tempel link Maps":saved.optString("address"));
            line.addView(select,new LinearLayout.LayoutParams(0,-2,1));
            select.setOnClickListener(v->openRoutePoint("stop",position));
            Button remove=outlineButton("×");
            remove.setTextSize(20);
            remove.setTextColor(0xff95510c);
            remove.setBackgroundColor(Color.TRANSPARENT);
            remove.setContentDescription("Hapus Singgah "+(index+1));
            line.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));
            remove.setOnClickListener(v->{
                if (submittingOrder) return;
                closePointSearch();
                if(position<waypoints.length())waypoints.remove(position);
                editingStop=-1;
                if(!hasRoutePoints()) mapSelectionMode=validCoordinate(pickupLat,pickupLng)?"delivery":"pickup";
                routePointsChanged();
            });
        }
        updateStopButton();
    }

    private void openRoutePoint(String kind,int index) {
        if (submittingOrder || !mapReady) return;
        if (!"pickup".equals(kind) && !validCoordinate(pickupLat,pickupLng)) {
            showInfo("Titik jemput","Tetapkan titik jemput terlebih dahulu.");
            return;
        }
        if ("stop".equals(kind) && (index<0 || index>waypoints.length() || index>=maxStops)) return;
        editingStop="stop".equals(kind)?index:-1;
        selectMapMode(kind);
        String title="pickup".equals(kind)?"jemput":"stop".equals(kind)?"singgah":"tujuan";
        pointSearch=new TransSendInlinePointSearch(this,networkScope,mapView.getCenter(),
                "Cari "+title+" / tempel link Maps",this::closePointSearch,(lat,lng,address)->{
                    if (!mapReady || isCheckout() || !kind.equals(mapSelectionMode)) return;
                    previewLat=lat;previewLng=lng;previewAddress=address;
                    closePointSearch();
                    mapView.centerOnInitialLocation(lat,lng,17f);
                });
        int insert="pickup".equals(kind)?1:"stop".equals(kind)?2:pointFields.indexOfChild(routeUtility);
        pointFields.addView(pointSearch.view(),insert,new LinearLayout.LayoutParams(-1,-2));
        routeUtility.setVisibility(View.GONE);
    }

    private boolean closePointSearch() {
        if (pointSearch==null) return false;
        View view=pointSearch.view();
        pointSearch.close();
        pointFields.removeView(view);
        pointSearch=null;
        routeUtility.setVisibility(View.VISIBLE);
        return true;
    }

    private void routePointsChanged() {
        pickupFareGate.invalidate();
        if (mapReady) mapView.setWaypoints(waypoints);
        if (hasRoutePoints()) openCheckout();
        else renderStage();
    }

    private void buildBottomSheet(LinearLayout parent) {
        bottomSheet = new LinearLayout(this);
        bottomSheet.setOrientation(LinearLayout.VERTICAL);
        bottomSheet.setPadding(dp(10), dp(5), dp(10), dp(5));
        bottomSheet.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(16), 1));
        parent.addView(bottomSheet, new LinearLayout.LayoutParams(-1, -2));

        packageScroll = new ScrollView(this);
        int height = Math.min(235, Math.max(114, getResources().getConfiguration().screenHeightDp / 3));
        bottomSheet.addView(packageScroll, new LinearLayout.LayoutParams(-1, dp(height)));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(5));
        packageScroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        confirmPointBtn = primaryButton("Tetapkan titik");
        confirmPointBtn.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x44ffffff),
                roundGradient("#064FD6","#086AF2",dp(11)),null));
        confirmPointBtn.setOnClickListener(v -> mapView.confirmPickerCenter());
        bottomSheet.addView(confirmPointBtn, new LinearLayout.LayoutParams(-1, -2));

        checkoutFooter = new LinearLayout(this);
        checkoutFooter.setOrientation(LinearLayout.VERTICAL);
        bottomSheet.addView(checkoutFooter, new LinearLayout.LayoutParams(-1, -2));
        buildSummaryCard();
    }

    private boolean isCheckout() {
        return "order".equals(mapSelectionMode);
    }

    private void renderStage() {
        boolean checkout = isCheckout();
        boolean stop="stop".equals(mapSelectionMode);
        setPointLabel(pickupMapBtn,"Jemput",firstNonEmpty(pickupAddress,"Lokasi Anda"));
        setPointLabel(deliveryMapBtn,"Mau ke mana?",firstNonEmpty(deliveryAddress,"Cari tempat / tempel link Maps"));
        renderStopRows();
        String title=stop?"Pilih lokasi singgah":"Pilih lokasi ini";
        android.text.SpannableString caption=new android.text.SpannableString("◉  "+title);
        android.graphics.drawable.Drawable pin=glyph(R.drawable.ic_booking_confirm,16);
        if (pin!=null) caption.setSpan(new android.text.style.ImageSpan(pin,android.text.style.ImageSpan.ALIGN_BOTTOM),
                0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        confirmPointBtn.setText(caption);
        confirmPointBtn.setContentDescription(title);
        confirmPointBtn.setVisibility(checkout ? View.GONE : View.VISIBLE);
        checkoutFooter.setVisibility(checkout ? View.VISIBLE : View.GONE);
        packageScroll.setVisibility(checkout && detailsOpen ? View.VISIBLE : View.GONE);
        detailsButton.setText(detailsOpen ? "Tutup detail" : "Detail paket");
        gpsMapBtn.setContentDescription(checkout ? "Tampilkan seluruh rute" : "Lokasi saya");
        if (checkout) mapView.showOrderAction(false,null);
        else {
            mapView.resetPickupDeliveryAnchors();
            if (stop) mapView.setWaypointSelectionMode(editingStop+1,"Pilih lokasi singgah");
            else mapView.setSelectionMode(mapSelectionMode);
            mapView.showCenterPin(true);
            mapView.hideCenterAction();
        }
        updateCheckoutButton();
    }

    private void setPointLabel(Button button,String title,String address) {
        String label=address.replace('\n',' ');
        android.text.SpannableString styled=new android.text.SpannableString(title+"\n"+label);
        styled.setSpan(new android.text.style.ForegroundColorSpan(0xff173153),
                title.length()+1,styled.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(styled);
        button.setContentDescription(title+", "+label);
    }

    protected void selectMapMode(String mode) {
        if (submittingOrder) return;
        if (!"pickup".equals(mode) && !validCoordinate(pickupLat,pickupLng)) {
            showInfo("Titik jemput","Tetapkan titik jemput terlebih dahulu.");
            return;
        }
        closePointSearch();
        previewAddress="";
        locationGeneration++;
        pickupFareGate.invalidate();
        setLoading(false);
        mapView.clearRoute();
        mapSelectionMode=mode;
        renderStage();
        double lat="delivery".equals(mode)?deliveryLat:pickupLat;
        double lng="delivery".equals(mode)?deliveryLng:pickupLng;
        JSONObject stop="stop".equals(mode)?waypoints.optJSONObject(editingStop):null;
        if(stop!=null){lat=stop.optDouble("latitude",lat);lng=stop.optDouble("longitude",lng);}
        if(!validCoordinate(lat,lng)){lat=pickupLat;lng=pickupLng;}
        if (mapReady && validCoordinate(lat,lng)) mapView.moveTo(lat,lng,17f);
    }

    protected void confirmMapPoint() {
        if (!mapReady || isCheckout() || submittingOrder) return;
        com.google.android.gms.maps.model.LatLng point=mapView.getCenter();
        if (!validCoordinate(point.latitude,point.longitude)) return;
        closePointSearch();
        locationGeneration++;
        boolean searched=!previewAddress.isEmpty() && Math.abs(point.latitude-previewLat)<.000001
                && Math.abs(point.longitude-previewLng)<.000001;
        String address=searched?previewAddress:formatCoordinate(point.latitude,point.longitude);
        if ("stop".equals(mapSelectionMode)) {
            final JSONObject saved=new JSONObject();
            try {
                saved.put("latitude",point.latitude).put("longitude",point.longitude).put("address",address);
                waypoints.put(editingStop,saved);
            } catch (org.json.JSONException invalid) { return; }
            if(!searched) reverseStopAddress(saved,point.latitude,point.longitude);
            editingStop=-1;
        } else {
            boolean pickup="pickup".equals(mapSelectionMode);
            if (pickup) {
                pickupLat=point.latitude;pickupLng=point.longitude;pickupAddress=address;
                mapView.setPickup(pickupLat,pickupLng,address);
            } else {
                deliveryLat=point.latitude;deliveryLng=point.longitude;deliveryAddress=address;
                mapView.setDelivery(deliveryLat,deliveryLng,address);
            }
            if(!searched) reverseGeocode(pickup,point.latitude,point.longitude);
        }
        previewAddress="";
        mapView.clearRoute();
        mapView.setWaypoints(waypoints);
        if (hasRoutePoints()) openCheckout();
        else selectMapMode("delivery");
    }

    private void reverseStopAddress(JSONObject waypoint,double lat,double lng) {
        networkScope.execute(()->{
            try {
                List<Address> addresses=new Geocoder(this,new Locale("id","ID")).getFromLocation(lat,lng,1);
                if (addresses==null || addresses.isEmpty()) return;
                String label=addresses.get(0).getAddressLine(0);
                if(label==null || label.trim().isEmpty())return;
                networkScope.post(mainHandler,()->{
                    for(int i=0;i<waypoints.length();i++){
                        if(waypoints.optJSONObject(i)!=waypoint || submittingOrder)continue;
                        try{waypoint.put("address",label);}catch(org.json.JSONException ignored){return;}
                        if(pointSearch==null)renderStopRows();
                        if(mapReady)mapView.setWaypoints(waypoints);
                        if(isCheckout())calculateOngkir();
                        return;
                    }
                });
            } catch(Exception ignored) { }
        });
    }

    private void openCheckout() {
        closePointSearch();
        locationGeneration++;
        mapSelectionMode = "order";
        renderStage();
        fitCheckout();
        drawPickupRoute();
        calculateOngkir();
    }

    private void fitCheckout() {
        if (!mapReady || mapView == null || !isCheckout()) return;
        mapView.post(() -> {
            if (!pickupScreenClosed && mapView != null && isCheckout()) {
                if (waypoints.length()>0) mapView.fitAll(); else mapView.fitPickupDeliveryInViewport();
            }
        });
    }

    protected void reverseGeocode(boolean pickup, double lat, double lng) {
        networkScope.newThread(() -> {
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
            networkScope.post(mainHandler, () -> {
                if (pickupScreenClosed || (pickup ? lat != pickupLat || lng != pickupLng
                        : lat != deliveryLat || lng != deliveryLng)) return;
                if (pickup) {
                    pickupAddress = finalAddress;
                    mapView.setPickup(lat, lng, finalAddress);
                } else {
                    deliveryAddress = finalAddress;
                    mapView.setDelivery(lat, lng, finalAddress);
                }
                setPointLabel(pickupMapBtn,"Jemput",firstNonEmpty(pickupAddress,"Lokasi Anda"));
                setPointLabel(deliveryMapBtn,"Mau ke mana?",firstNonEmpty(deliveryAddress,"Cari tempat / tempel link Maps"));
            });
        }, "transpickup-reverse-geocode").start();
    }

    protected void drawPickupRoute() {
        if (!mapReady || !hasRoutePoints()) return;
        final double fromLat=pickupLat,fromLng=pickupLng,toLat=deliveryLat,toLng=deliveryLng;
        final String stopsJson=waypoints.toString();
        networkScope.execute(() -> {
            try {
                org.json.JSONArray stops=new org.json.JSONArray(stopsJson);
                org.json.JSONArray geometry=new org.json.JSONArray();
                double startLat=fromLat,startLng=fromLng;
                for (int index=0;index<=stops.length();index++) {
                    JSONObject stop=stops.optJSONObject(index);
                    double endLat=stop==null?toLat:stop.getDouble("latitude");
                    double endLng=stop==null?toLng:stop.getDouble("longitude");
                    org.json.JSONArray leg=StableRouteEngine.fetch(startLat,startLng,endLat,endLng).latLngPoints;
                    for (int point=geometry.length()==0?0:1; point<leg.length();point++) geometry.put(leg.get(point));
                    startLat=endLat;startLng=endLng;
                }
                networkScope.post(mainHandler, () -> {
                    if (mapView!=null && isCheckout() && fromLat==pickupLat && fromLng==pickupLng
                            && toLat==deliveryLat && toLng==deliveryLng && stopsJson.equals(waypoints.toString())) {
                        mapView.drawOsrmRoute(geometry,"driver_accepted");
                    }
                });
            } catch (Exception ignored) { }
        });
    }

    protected String formatCoordinate(double lat, double lng) {
        return String.format(Locale.US, "%.6f, %.6f", lat, lng);
    }

    protected boolean validCoordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat != 0d && lng != 0d
                && Math.abs(lat) <= 90d && Math.abs(lng) <= 180d;
    }

    protected void buildForm() {
        vehicleType = "bike";
        if (!"medium".equals(packageSize)) packageSize = "small";
        root.removeAllViews();

        buildItemCard();
        buildOptionCard();
        buildPaymentCard();
    }

    protected void buildItemCard() {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(text("Detail paket", 15, "#0B3A78", true));
        itemNameInput = edit("Nama barang");
        addInner(card, itemNameInput, dp(6));
        itemValueInput = edit("Nilai barang (Rp)");
        itemValueInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        addInner(card, itemValueInput, dp(6));
        receiverNameInput = edit("Nama penerima");
        addInner(card, receiverNameInput, dp(6));
        receiverPhoneInput = edit("Nomor HP penerima");
        receiverPhoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        addInner(card, receiverPhoneInput, dp(6));
        noteInput = edit("Catatan driver (opsional)");
        noteInput.setSingleLine(false); noteInput.setMinLines(2); noteInput.setMaxLines(3); noteInput.setGravity(Gravity.TOP | Gravity.START);
        addInner(card, noteInput, dp(6));
        noteInput.getLayoutParams().height = dp(62);
        addWithMargin(card, 0, 0, 0, dp(8));
    }



    private void buildOptionCard() {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Ukuran Paket", 15, "#0B3A78", true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView motorOnly = text("Khusus motor", 11, "#0B7CFF", true);
        motorOnly.setGravity(Gravity.CENTER);
        motorOnly.setBackground(roundStroke("#EAF4FF", "#B9DAFF", dp(14), 1));
        titleRow.addView(motorOnly, new LinearLayout.LayoutParams(dp(112), dp(32)));
        card.addView(titleRow);

        LinearLayout sizeRow = row();
        smallBtn = choiceButton("Kecil · < 2 kg", true);
        mediumBtn = choiceButton("Sedang · 2–5 kg", false);
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
        fragileRow.addView(fragileNoBtn, new LinearLayout.LayoutParams(0, -2, 0.65f));
        LinearLayout.LayoutParams yesLp = new LinearLayout.LayoutParams(0, -2, 0.65f);
        yesLp.setMargins(dp(6), 0, 0, 0);
        fragileRow.addView(fragileYesBtn, yesLp);
        LinearLayout.LayoutParams fragileLp = new LinearLayout.LayoutParams(-1, -2);
        fragileLp.setMargins(0, dp(7), 0, 0);
        card.addView(fragileRow, fragileLp);
        fragileNoBtn.setOnClickListener(v -> { fragile = false; updateChoices(); });
        fragileYesBtn.setOnClickListener(v -> { fragile = true; updateChoices(); });

        vehicleType = "bike";
        addWithMargin(card, 0, 0, 0, dp(8));
    }

    private void buildPaymentCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(text("Pembayaran", 17, "#0B3A78", true));
        LinearLayout row = row();
        cashBtn = choiceButton("Tunai", true);
        balanceBtn = choiceButton("TransPay", false);
        addChoice(row, cashBtn, balanceBtn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(10), 0, 0);
        card.addView(row, lp);
        cashBtn.setOnClickListener(v -> { paymentMethod = "cash"; updateChoices(); });
        balanceBtn.setOnClickListener(v -> { paymentMethod = "balance"; updateChoices(); });
        addWithMargin(card, 0, 0, 0, dp(8));
    }

    private void buildSummaryCard() {
        LinearLayout summary = row();
        summary.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout fare = new LinearLayout(this);
        fare.setOrientation(LinearLayout.VERTICAL);
        priceText = text("Ongkir · —", 16, "#0B3A78", true);
        distanceText = text("", 11, "#536B83", false);
        distanceText.setMaxLines(2);
        distanceText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        fare.addView(priceText);
        fare.addView(distanceText);
        summary.addView(fare, new LinearLayout.LayoutParams(0, -2, 1));
        detailsButton = outlineButton("Detail paket");
        detailsButton.setOnClickListener(v -> setDetailsOpen(!detailsOpen));
        summary.addView(detailsButton, new LinearLayout.LayoutParams(-2, -2));
        checkoutFooter.addView(summary);

        orderBtn = primaryButton("Kirim paket");
        orderBtn.setOnClickListener(v -> {
            String key = currentPickupQuoteKey();
            if (!pickupFareGate.hasQuote(key)) calculateOngkir();
            else if (validatePackage()) createPickupOrder();
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
        buttonLp.topMargin = dp(3);
        checkoutFooter.addView(orderBtn, buttonLp);
    }

    private void setDetailsOpen(boolean open) {
        detailsOpen = open;
        if (!open) {
            View focus = getCurrentFocus();
            if (focus != null) {
                android.view.inputmethod.InputMethodManager keyboard =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                focus.clearFocus();
            }
        }
        renderStage();
        fitCheckout();
    }

    private void updateChoices() {
        setChoice(smallBtn, "small".equals(packageSize));
        setChoice(mediumBtn, "medium".equals(packageSize));
        setChoice(fragileNoBtn, !fragile);
        setChoice(fragileYesBtn, fragile);
        setChoice(cashBtn, "cash".equals(paymentMethod));
        setChoice(balanceBtn, "balance".equals(paymentMethod));
        if (isCheckout()) calculateOngkir();
    }

    private void updateCheckoutButton() {
        String key = currentPickupQuoteKey();
        boolean pending = pickupFareGate.isPending(key);
        boolean ready = pickupFareGate.hasQuote(key);
        orderBtn.setEnabled(!submittingOrder && !pending);
        orderBtn.setAlpha(orderBtn.isEnabled() ? 1f : .58f);
        orderBtn.setText(submittingOrder ? "Memproses…" : pending ? "Menghitung ongkir…"
                : ready ? (courierContactId.isEmpty() ? "Kirim paket" : "Pesan dengan kurir ini") : "Hitung ulang ongkir");
    }

    private void setChoice(Button b, boolean active) {
        if (b == null) return;
        b.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#0B3A78"));
        b.setBackground(roundStroke(active ? "#0B7CFF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(18), 1));
    }

    private void loadPickupLocation() {
        if (pickupScreenClosed || isCheckout()) return;
        final int request = ++locationGeneration;
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
            if (best != null) centerOnLocation(best, request);
            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (locationListener != null) locationManager.removeUpdates(locationListener);
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) { centerOnLocation(location, request); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };
            locationManager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper());
        } catch (Exception e) { showInfo("Lokasi", "Gagal mengambil lokasi jemput."); }
    }

    private void centerOnLocation(Location location, int request) {
        if (!mapReady || pickupScreenClosed || request != locationGeneration || isCheckout()) return;
        double lat = location.getLatitude(), lng = location.getLongitude();
        if (validCoordinate(lat, lng)) {
            previewAddress="";
            if (gpsStatus!=null) gpsStatus.setText(location.hasAccuracy()?"GPS ±"+Math.round(location.getAccuracy())+" m":"Lokasi GPS");
            mapView.moveTo(lat, lng, 17f);
            new SessionManager(this).saveLastLocation(String.valueOf(lat), String.valueOf(lng));
        }
    }

    private String currentPickupQuoteKey() {
        return CustomerFareQuoteGate.key(pickupLat,pickupLng,deliveryLat,deliveryLng,
                vehicleType,packageSize,fragile?"1":"0",paymentMethod,String.valueOf(userId),waypoints.toString());
    }
    private boolean isPickupQuoteCurrent(long ticket,String key) {
        return !pickupScreenClosed && !submittingOrder && isCheckout() && pickupFareGate.isCurrent(ticket)
                && key.equals(currentPickupQuoteKey());
    }
    private void calculateOngkir() {
        if(pickupScreenClosed || submittingOrder)return;
        if (!isCheckout() || !hasRoutePoints()) return;
        final String quoteKey = currentPickupQuoteKey();
        if (pickupFareGate.isPending(quoteKey)) return;
        final long quoteTicket=pickupFareGate.begin(quoteKey);
        deliveryFee=0;distanceKm=0;
        distanceText.setText("");
        updateCheckoutButton();
        final double pLat=pickupLat,pLng=pickupLng,dLat=deliveryLat,dLng=deliveryLng;
        final String vehicle=vehicleType,size=packageSize;
        final boolean delicate=fragile;
        final String stops=waypoints.toString();
        setLoading(true);
        if(priceText!=null)priceText.setText("Menghitung...");
        networkScope.execute(() -> {
            try {
                String url=BASE_URL+"server/calculatePickupOngkir.php"+
                        "?pickup_lat="+Uri.encode(String.valueOf(pLat))+"&pickup_lng="+Uri.encode(String.valueOf(pLng))+
                        "&delivery_lat="+Uri.encode(String.valueOf(dLat))+"&delivery_lng="+Uri.encode(String.valueOf(dLng))+
                        "&vehicle_type="+Uri.encode(vehicle)+"&package_size="+Uri.encode(size)+
                        "&fragile="+(delicate?"1":"0")+"&waypoints="+Uri.encode(stops)+"&_="+System.currentTimeMillis();
                JSONObject res=getJson(url);
                if(!res.optBoolean("success",false))throw new Exception(firstNonEmpty(res.optString("message"),"Gagal menghitung ongkir"));
                final double quotedFee=res.optDouble("price",0),quotedDistance=res.optDouble("distance_km",0);
                if(Double.isNaN(quotedFee)||Double.isInfinite(quotedFee)||quotedFee<=0||Double.isNaN(quotedDistance)||Double.isInfinite(quotedDistance)||quotedDistance<0)
                    throw new Exception("Tarif server belum valid. Hitung ongkir kembali.");
                networkScope.post(mainHandler,()->{
                    if(!isPickupQuoteCurrent(quoteTicket,quoteKey)||!pickupFareGate.accept(quoteTicket,quoteKey))return;
                    deliveryFee=quotedFee;distanceKm=quotedDistance;setLoading(false);
                    distanceText.setText(String.format(new Locale("id", "ID"), "%.2f km", distanceKm)
                            + ("balance".equals(paymentMethod) ? " · TransPay" : " · Tunai"));
                    priceText.setText("Ongkir: "+rupiah(deliveryFee));
                    updateCheckoutButton();
                });
            }catch(Exception e){networkScope.post(mainHandler,()->{
                if(!isPickupQuoteCurrent(quoteTicket,quoteKey))return;
                pickupFareGate.reject(quoteTicket);setLoading(false);
                priceText.setText("Ongkir belum tersedia");
                distanceText.setText(firstNonEmpty(e.getMessage(), "Coba hitung ulang."));
                updateCheckoutButton();
            });}
        },()->networkScope.post(mainHandler,()->{
            if(!isPickupQuoteCurrent(quoteTicket,quoteKey))return;
            pickupFareGate.reject(quoteTicket);setLoading(false);
            priceText.setText("Ongkir belum tersedia");
            distanceText.setText("Sistem sedang sibuk. Coba lagi.");
            updateCheckoutButton();
        }));
    }

    private boolean validatePackage() {
        EditText invalid = null;
        String message = "";
        if (itemNameInput.getText().toString().trim().length() < 2) {
            invalid = itemNameInput; message = "Isi nama barang.";
        } else if (itemValueInput.getText().toString().trim().isEmpty()) {
            invalid = itemValueInput; message = "Isi nilai barang.";
        } else if (receiverNameInput.getText().toString().trim().length() < 2) {
            invalid = receiverNameInput; message = "Isi nama penerima.";
        } else if (!receiverPhoneInput.getText().toString().trim().matches("\\+?[0-9]{9,15}")) {
            invalid = receiverPhoneInput; message = "Isi nomor HP penerima yang valid.";
        }
        if (invalid == null) return true;
        setDetailsOpen(true);
        invalid.setError(message);
        invalid.requestFocus();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return false;
    }

    private void createPickupOrder() {
        if (submittingOrder) return;
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (!isCheckout() || !hasRoutePoints() || !validatePackage()) return;
        String itemName = itemNameInput.getText().toString().trim();
        String receiverName = receiverNameInput.getText().toString().trim();
        String receiverPhone = receiverPhoneInput.getText().toString().trim();
        if (deliveryFee <= 0 || !pickupFareGate.hasQuote(currentPickupQuoteKey())) { if(!pickupFareGate.isPending(currentPickupQuoteKey()))calculateOngkir(); return; }
        final double submittedPickupLat=pickupLat,submittedPickupLng=pickupLng,submittedDeliveryLat=deliveryLat,submittedDeliveryLng=deliveryLng;
        final String submittedStops=waypoints.toString();
        final String submittedPickupAddress=pickupAddress,submittedDeliveryAddress=deliveryAddress;
        final String submittedSize=packageSize,submittedVehicle=vehicleType,submittedPayment=paymentMethod,submittedCategory=itemCategory;
        final boolean submittedFragile=fragile;
        final long approvedPrice = Math.round(deliveryFee);
        final double submittedValue=parseMoney(itemValueInput.getText().toString());
        final String submittedNote=noteInput.getText().toString().trim();
        submittingOrder = true;
        pickupFareGate.invalidate();
        updateCheckoutButton();
        setLoading(true);
        networkScope.newThread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("user_id", userId); p.put("username", username);
                p.put("pickup_address", submittedPickupAddress);
                p.put("delivery_address", submittedDeliveryAddress);
                p.put("pickup_lat", submittedPickupLat); p.put("pickup_lng", submittedPickupLng); p.put("delivery_lat", submittedDeliveryLat); p.put("delivery_lng", submittedDeliveryLng);
                p.put("item_name", itemName); p.put("item_category", submittedCategory); p.put("item_value", submittedValue);
                p.put("package_size", submittedSize); p.put("vehicle_type", submittedVehicle); p.put("fragile", submittedFragile ? 1 : 0);
                p.put("receiver_name", receiverName); p.put("receiver_phone", receiverPhone); p.put("note", submittedNote);
                p.put("payment_method", submittedPayment);
                p.put("waypoints", new org.json.JSONArray(submittedStops));
                if ("balance".equals(submittedPayment) || !courierContactId.isEmpty()) p.put("approved_price", approvedPrice);
                if (!courierContactId.isEmpty()) p.put("courier_contact_id", courierContactId);
                JSONObject res = ReliableOrderClient.create(this, BASE_URL + "server/create_pickup_order.php", p, TIMEOUT_MS);
                networkScope.post(mainHandler, () -> {
                    if (pickupScreenClosed || isFinishing() || isDestroyed()) return;
                    if (res.optBoolean("success")) {
                        showPickupReceipt(res);
                    } else {
                        submittingOrder = false;
                        setLoading(false);
                        updateCheckoutButton();
                        showInfo("Pengiriman", res.optString("message", "Pengiriman belum berhasil."));
                    }
                });
            } catch (Exception e) {
                networkScope.post(mainHandler, () -> {
                    if (pickupScreenClosed || isFinishing() || isDestroyed()) return;
                    if (ReliableOrderClient.hasPickupRecovery(this)) {
                        recoveryScreen = true;
                        submittingOrder = false;
                        showRecovery(false);
                    } else {
                        submittingOrder = false;
                        updateCheckoutButton();
                        setLoading(false);
                        if (e instanceof TransivaHttpRepository.ServerResponseException) {
                            JSONObject rejection = ((TransivaHttpRepository.ServerResponseException) e).response;
                            if (rejection != null && "PICKUP_QUOTE_CHANGED".equals(rejection.optString("code"))) {
                                pickupFareGate.invalidate();
                                calculateOngkir();
                            }
                        }
                        showInfo("Pengiriman", ReliableOrderClient.failureMessage(e));
                    }
                });
            }
        }).start();
    }

    private void showRecovery(boolean retry) {
        if (recoveryBusy || pickupScreenClosed) return;
        recoveryScreen = true;
        recoveryBusy = true;
        renderRecovery("Memeriksa pengiriman sebelumnya…", false, false);
        networkScope.execute(() -> {
            try {
                JSONObject result = retry ? ReliableOrderClient.retryPickup(this, TIMEOUT_MS)
                        : ReliableOrderClient.recoverPickup(this, TIMEOUT_MS);
                networkScope.post(mainHandler, () -> {
                    if (pickupScreenClosed || isFinishing() || isDestroyed()) return;
                    recoveryBusy = false;
                    if (result != null && result.optBoolean("success")) {
                        showPickupReceipt(result);
                    } else if (result != null && result.optBoolean("recovery_pending")) {
                        renderRecovery("Pengiriman sebelumnya belum terkonfirmasi. Lanjutkan permintaan yang sama atau periksa Aktivitas.", true, false);
                    } else if (ReliableOrderClient.hasPickupRecovery(this)) {
                        renderRecovery("Pengiriman belum dapat dipastikan. Periksa lagi atau lihat Aktivitas.", false, false);
                    } else {
                        renderRecovery(result == null ? "Tidak ada pengiriman yang menunggu pemulihan."
                                : result.optString("message", "Pengiriman sebelumnya belum berhasil."), false, true);
                    }
                });
            } catch (Exception unavailable) {
                networkScope.post(mainHandler, () -> {
                    if (pickupScreenClosed || isFinishing() || isDestroyed()) return;
                    recoveryBusy = false;
                    renderRecovery("Hasil pengiriman belum dapat dimuat. Periksa koneksi dan coba lagi, atau lihat Aktivitas.", false,
                            !ReliableOrderClient.hasPickupRecovery(this));
                });
            }
        }, () -> networkScope.post(mainHandler, () -> {
            recoveryBusy = false;
            renderRecovery("Aplikasi sedang sibuk. Coba periksa lagi.", false, false);
        }));
    }

    private void renderRecovery(String message, boolean canRetry, boolean canStartNew) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(22), dp(24), dp(22), dp(24));
        content.setBackgroundColor(Color.parseColor("#F3F8FF"));
        content.addView(text("TransSend", 24, "#0B3A78", true));
        TextView copy = text(message, 15, "#536B83", false);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(0, dp(12), 0, dp(18));
        content.addView(copy);
        if (recoveryBusy) {
            content.addView(new ProgressBar(this), new LinearLayout.LayoutParams(dp(32), dp(32)));
        } else {
            Button check = primaryButton(canRetry ? "Lanjutkan pengiriman yang sama" : "Periksa lagi");
            check.setOnClickListener(v -> showRecovery(canRetry));
            content.addView(check, new LinearLayout.LayoutParams(-1, -2));
            if (canStartNew) {
                Button create = outlineButton("Kembali ke TransSend");
                create.setOnClickListener(v -> recreate());
                content.addView(create, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        Button history = outlineButton("Lihat Aktivitas");
        history.setOnClickListener(v -> {
            startActivity(new Intent(this, CustomerHistoryActivity.class));
            finish();
        });
        content.addView(history, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        CustomerAppSettings.apply(this);
    }

    private void showPickupReceipt(JSONObject response) {
        if (recoveryReceipt != null && recoveryReceipt.isShowing()) return;
        completedOrderId = response.optString("order_id", "").trim();
        if (completedOrderId.isEmpty()) {
            submittingOrder = false;
            showRecovery(false);
            return;
        }
        submittingOrder = true;
        setLoading(false);
        if (!recoveryScreen) updateCheckoutButton();
        recoveryReceipt = new TransivaAlertDialogBuilder(this)
                .setTitle("Paket siap dikirim")
                .setCancelable(false)
                .setMessage(("driver_accepted".equals(response.optString("status"))
                        ? "Pesanan diterima kurir pilihan Anda." : "Pengiriman berhasil dibuat.")
                        + "\n\nOrder ID: " + completedOrderId)
                .setPositiveButton("Lihat Aktivitas", (dialog, which) -> {
                    startActivity(new Intent(this, CustomerHistoryActivity.class).putExtra("order_id", completedOrderId));
                    ReliableOrderClient.acknowledgePickup(this, completedOrderId);
                    finish();
                })
                .create();
        recoveryReceipt.show();
    }

    private boolean hasRoutePoints() {
        return validCoordinate(pickupLat, pickupLng) && validCoordinate(deliveryLat, deliveryLng);
    }

    private void restoreDraft(Bundle state) {
        if (state == null) return;
        String draftOwner = state.getString("sendRecoveryOwner", "");
        if (!draftOwner.isEmpty() && !Integer.toString(userId).equals(draftOwner)) return;
        editingStop=state.getInt("editingStop",-1);
        try { waypoints=new org.json.JSONArray(state.getString("sendStops","[]")); }
        catch(org.json.JSONException ignored) { }
        pickupLat = state.getDouble("sendPickupLat");
        pickupLng = state.getDouble("sendPickupLng");
        deliveryLat = state.getDouble("sendDeliveryLat");
        deliveryLng = state.getDouble("sendDeliveryLng");
        pickupAddress = state.getString("sendPickupAddress", "");
        deliveryAddress = state.getString("sendDeliveryAddress", "");
        mapSelectionMode = state.getString("sendStage", "pickup");
        if ("stop".equals(mapSelectionMode) && (editingStop<0 || editingStop>waypoints.length() || editingStop>=2)) {
            mapSelectionMode=hasRoutePoints()?"order":"pickup";
        }
        detailsOpen = state.getBoolean("sendDetails");
        itemNameInput.setText(state.getString("sendItem"));
        itemValueInput.setText(state.getString("sendValue"));
        receiverNameInput.setText(state.getString("sendName"));
        receiverPhoneInput.setText(state.getString("sendPhone"));
        noteInput.setText(state.getString("sendNote"));
        packageSize = state.getString("sendSize", "small");
        paymentMethod = state.getString("sendPayment", "cash");
        fragile = state.getBoolean("sendFragile");
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("sendRecoveryOwner", Integer.toString(userId));
        out.putString("sendCompletedOrder", completedOrderId);
        if (recoveryScreen) return;
        out.putInt("editingStop",editingStop);
        out.putString("sendStops",waypoints.toString());
        out.putDouble("sendPickupLat", pickupLat);
        out.putDouble("sendPickupLng", pickupLng);
        out.putDouble("sendDeliveryLat", deliveryLat);
        out.putDouble("sendDeliveryLng", deliveryLng);
        out.putString("sendPickupAddress", pickupAddress);
        out.putString("sendDeliveryAddress", deliveryAddress);
        out.putString("sendStage", mapSelectionMode);
        out.putBoolean("sendDetails", detailsOpen);
        out.putString("sendItem", itemNameInput.getText().toString());
        out.putString("sendValue", itemValueInput.getText().toString());
        out.putString("sendName", receiverNameInput.getText().toString());
        out.putString("sendPhone", receiverPhoneInput.getText().toString());
        out.putString("sendNote", noteInput.getText().toString());
        out.putString("sendSize", packageSize);
        out.putString("sendPayment", paymentMethod);
        out.putBoolean("sendFragile", fragile);
    }



    private double parseMoney(String s) { try { return Double.parseDouble(firstNonEmpty(s, "0").replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } }
    private JSONObject getJson(String urlText) throws Exception {
        return TransivaHttpRepository.getJson(this, urlText, TIMEOUT_MS);
    }

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
        if (recoveryReceipt != null) recoveryReceipt.dismiss();
        closePointSearch();
        pickupScreenClosed=true;pickupFareGate.invalidate();
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (RuntimeException ignored) { }
        }
        networkScope.destroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (mapView != null) {
            mapView.onDestroyMap();
            mapView = null;
        }
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                loadPickupLocation();
                return;
            }
        }
    }
    private boolean handleBack() {
        if (submittingOrder || closePointSearch()) return true;
        if ("stop".equals(mapSelectionMode) && !hasRoutePoints()) {
            editingStop=-1;
            selectMapMode("delivery");
            return true;
        }
        if (recoveryScreen) {
            startActivity(new Intent(this, CustomerDashboardActivity.class));
            finish();
        } else if (isCheckout() && detailsOpen) {
            setDetailsOpen(false);
        } else if (!isCheckout() && hasRoutePoints()) {
            openCheckout();
        } else {
            finish();
        }
        return true;
    }

    private int checkSelfPermissionSafe(String p) { try { if (android.os.Build.VERSION.SDK_INT >= 23) return checkSelfPermission(p); return PackageManager.PERMISSION_GRANTED; } catch (Exception e) { return PackageManager.PERMISSION_DENIED; } }
    private EditText edit(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(14); e.setSingleLine(true); e.setTextColor(Color.parseColor("#0F172A")); e.setHintTextColor(Color.parseColor("#94A3B8")); e.setPadding(dp(14),0,dp(14),0); e.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(12), 1)); return e; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private void addChoice(LinearLayout row, Button a, Button b) { row.addView(a, new LinearLayout.LayoutParams(0, -2, 1)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(dp(10),0,0,0); row.addView(b, lp); }
    private void addInner(LinearLayout parent, View v, int top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48)); lp.setMargins(0, top, 0, 0); parent.addView(v, lp); }
    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(roundStroke("#FFFFFF", "#E2ECF8", dp(14), 1)); return v; }
    private TextView text(String s, int sp, String color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.parseColor(color)); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private Button primaryButton(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(14); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setStateListAnimator(null); b.setPadding(dp(10), dp(4), dp(10), dp(4)); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundGradient("#086BFF", "#2EA2FF", dp(18))); return b; }
    private Button outlineButton(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setStateListAnimator(null); b.setPadding(dp(10), dp(4), dp(10), dp(4)); b.setTextColor(Color.parseColor("#0B7CFF")); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(roundStroke("#FFFFFF", "#9DCAFF", dp(18), 1)); return b; }
    private Button choiceButton(String s, boolean active) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setStateListAnimator(null); b.setPadding(dp(10), dp(4), dp(10), dp(4)); b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); setChoice(b, active); return b; }
    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(sw), Color.parseColor(stroke));
        return drawable;
    }
    private GradientDrawable roundGradient(String c1, String c2, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(c1), Color.parseColor(c2)}); g.setCornerRadius(radius); return g; }
    private void addWithMargin(View v, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(l,t,r,b); root.addView(v, lp); }
    private void setLoading(boolean b) { if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE); }
    private void showInfo(String title, String msg) { try { new TransivaAlertDialogBuilder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {} }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String rupiah(double v) { try { NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("id", "ID")); nf.setMaximumFractionDigits(0); return nf.format(v).replace("Rp", "Rp "); } catch (Exception e) { return "Rp " + Math.round(v); } }
    private String firstNonEmpty(String... values) { if (values == null) return ""; for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }

}
