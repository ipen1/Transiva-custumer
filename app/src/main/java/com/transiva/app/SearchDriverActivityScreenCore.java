package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.os.CountDownTimer;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import java.util.concurrent.atomic.AtomicBoolean;
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
class SearchDriverActivityScreenCore extends CustomerAdaptiveActivity {



    protected final CustomerFeatureRuntimeController featureRuntime =
            new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.SEARCH);

    protected static final String BASE_URL = "https://transiva.my.id/";
    protected static final int TIMEOUT_MS = 20000;
    protected static final int REQ_LOCATION = 2201;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper());

    protected FrameLayout pageRoot;
    protected CustomerMatchmakingView matchingView;
    protected TextView countdownText;
    protected TextView countdownLabel;
    protected CountDownTimer matchCountdown;
    protected long countdownRemainingMs = 30000L;
    protected int searchPhase = 1;
    protected String offeredDriverUsername = "";
    protected TextView titleText;
    protected TextView subtitleText;
    protected TextView driverNameText;
    protected TextView driverDistanceText;
    protected TextView driverAvatarText;
    protected TextView driverPlateText;
    protected TextView driverRatingText;
    protected TextView acceptedBadgeText;
    protected ImageView driverPhotoView;
    protected LinearLayout driverCard;
    protected WebView miniMap;
    protected Button cancelBtn;
    protected ProgressBar progressBar;

    protected boolean isCanceling = false;
    protected boolean driverFound = false;
    protected boolean destroyed = false;

    protected String activeOrderId = "";
    protected double userLat = 0;
    protected double userLng = 0;
    protected boolean hasUserLocation = false;
    protected volatile String cachedDriverType = "bike";
    protected final AtomicBoolean matchRequestInFlight = new AtomicBoolean(false);
    protected final AtomicBoolean statusRequestInFlight = new AtomicBoolean(false);

    protected final Runnable driverMatchRunnable = new Runnable() {
        @Override public void run() {
            if (!destroyed && !isCanceling && !driverFound) {
                loadMatchingDrivers();
                mainHandler.postDelayed(this, 8000);
            }
        }
    };

    protected final Runnable checkOrderRunnable = new Runnable() {
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
            CustomerWindowPolicy.status(getWindow(), Color.parseColor("#071426"));
            CustomerWindowPolicy.navigation(getWindow(), Color.parseColor("#071426"));
        } catch (Exception ignored) {}

        activeOrderId = firstNonEmpty(
                getIntent().getStringExtra("order_id"),
                getStringPref("active_order_id"),
                getStringPref("order_id")
        );
        cachedDriverType = normalizeDriverType(firstNonEmpty(
                getIntent().getStringExtra("driver_type"),
                getIntent().getStringExtra("active_driver_type"),
                getStringPref("active_driver_type"),
                "bike"
        ));

        buildLayout();
        getUserLocationThenStart();
    }

    private PremiumOrderStatusView searchStatus;

    protected void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        pageRoot = page;
        page.setBackground(roundGradient("#020817", "#071A36", 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView appTitle = text("TRANSIVA • MATCHMAKING", 13, "#53B7FF", true);
        appTitle.setGravity(Gravity.CENTER);
        root.addView(appTitle, new LinearLayout.LayoutParams(-1, -2));

        titleText = text("Temukan driver yang pas", 22, "#F0F7FF", true);
        titleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(10), 0, dp(6));
        root.addView(titleText, titleLp);

        subtitleText = text("Mencocokkan Anda dengan driver terdekat", 14, "#D5E8FF", false);
        subtitleText.setGravity(Gravity.CENTER);
        root.addView(subtitleText, new LinearLayout.LayoutParams(-1, -2));

        searchStatus = new PremiumOrderStatusView(this, true);
        searchStatus.bind(null, "Mencari driver terdekat", "Pesanan sedang diproses");
        LinearLayout.LayoutParams searchStatusLp = new LinearLayout.LayoutParams(-1, -2);
        searchStatusLp.topMargin = dp(16); root.addView(searchStatus, searchStatusLp);
        matchingView = new CustomerMatchmakingView(this, "car".equals(cachedDriverType));
        LinearLayout.LayoutParams matchingLp = new LinearLayout.LayoutParams(-1, dp(192));
        matchingLp.setMargins(0, dp(12), 0, dp(14));
        root.addView(matchingView, matchingLp);

        countdownLabel = text("MENCARI DI SEKITAR JEMPUT", 10, "#8FCBFF", true);
        countdownLabel.setGravity(Gravity.CENTER);
        root.addView(countdownLabel, new LinearLayout.LayoutParams(-1, -2));

        countdownText = text("00:30", 24, "#E2F2FF", true);
        countdownText.setGravity(Gravity.CENTER);
        countdownText.setPadding(dp(18), dp(7), dp(18), dp(7));
        countdownText.setBackground(roundStroke("#102B49", "#285677", dp(14), 1));
        LinearLayout.LayoutParams countdownLp = new LinearLayout.LayoutParams(dp(124), -2);
        countdownLp.gravity = Gravity.CENTER_HORIZONTAL;
        countdownLp.setMargins(0, dp(6), 0, dp(10));
        root.addView(countdownText, countdownLp);

        TextView secureLine = text("🛡 Pesanan aman • Driver terverifikasi", 12, "#B8D7F7", false);
        secureLine.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams secureLp = new LinearLayout.LayoutParams(-1, -2);
        secureLp.setMargins(0, 0, 0, dp(16));
        root.addView(secureLine, secureLp);

        cancelBtn = new Button(this);
        cancelBtn.setAllCaps(false);
        cancelBtn.setText("Batalkan pesanan");
        cancelBtn.setTextSize(13);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setTextColor(Color.parseColor("#F3B9BD"));
        cancelBtn.setBackground(roundStroke("#182B43", "#5C3E51", dp(14), 1));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, dp(46));
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

        miniMap = new CustomerThemedWebView(this);
        miniMap.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = miniMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDomStorageEnabled(true);
        miniMap.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view,String url) { if(view instanceof CustomerThemedWebView)((CustomerThemedWebView)view).refreshCustomerTheme(); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
        });
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(-1, dp(230));
        mapLp.setMargins(0, dp(16), 0, 0);
        driverCard.addView(miniMap, mapLp);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pLp.gravity = Gravity.CENTER;
        page.addView(progressBar, pLp);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    protected void getUserLocationThenStart() {
        if (activeOrderId.length() == 0) {
            showInfo("Order Tidak Ada", "Order aktif tidak ditemukan.");
            goHomeDelayed(800);
            return;
        }

        saveStringPref("active_order_id", activeOrderId);
        CustomerRedispatchService.start(this, activeOrderId);

        if (checkSelfPermissionSafe(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermissionSafe(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            startLoops();
            return;
        }

        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) {
                setSubtitle("Lokasi belum tersedia, pencarian tetap berjalan");
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
                setSubtitle("GPS belum aktif, pencarian tetap berjalan");
            }
        } catch (Exception e) {
            setSubtitle("Lokasi belum terbaca, pencarian tetap berjalan");
        }

        startLoops();
    }

    protected void updateUserLocation(Location location) {
        if (location == null) return;
        userLat = location.getLatitude();
        userLng = location.getLongitude();
        hasUserLocation = true;
        setSubtitle("Mencari driver aktif di sekitar Anda");
    }

    protected void startLoops() {
        startMatchCountdown();
        mainHandler.removeCallbacks(driverMatchRunnable);
        mainHandler.removeCallbacks(checkOrderRunnable);
        loadMatchingDrivers();
        checkOrderStatus();
        mainHandler.postDelayed(driverMatchRunnable, CustomerPerformanceManager.pollingCritical(this, 8000));
        mainHandler.postDelayed(checkOrderRunnable, CustomerPerformanceManager.pollingCritical(this, 3000));
    }

    protected void startMatchCountdown() {
        stopMatchCountdown();
        countdownRemainingMs = 90000L;
        if (countdownText != null) countdownText.setText("00:30");
        if (countdownLabel != null) countdownLabel.setText("MENCARI DI SEKITAR JEMPUT");
        setSearchPhase(1);
        matchCountdown = new CountDownTimer(90000L, 1000L) {
            @Override public void onTick(long millisUntilFinished) {
                countdownRemainingMs = millisUntilFinished;
                long elapsed = 90000L - millisUntilFinished;
                int phase = elapsed < 30000L ? 1 : (elapsed < 60000L ? 2 : 3);
                setSearchPhase(phase);
                long nextBoundary = phase == 1 ? 30000L : (phase == 2 ? 60000L : 90000L);
                int seconds = Math.max(0, (int) Math.ceil((nextBoundary - elapsed) / 1000.0));
                if (countdownText != null) {
                    countdownText.setText(String.format(Locale.US, "00:%02d", seconds));
                    float pulse = seconds <= 5 ? 1.06f : 1.0f;
                    countdownText.animate().scaleX(pulse).scaleY(pulse).setDuration(160).withEndAction(() ->
                            countdownText.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
                }
            }

            @Override public void onFinish() {
                if (destroyed || isCanceling || driverFound) return;
                setSearchPhase(3);
                if (countdownText != null) countdownText.setText("LIVE");
            }
        }.start();
    }

    protected void setSearchPhase(int phase) {
        int safe = Math.max(1, Math.min(3, phase));
        if (searchPhase == safe && matchingView != null) {
            matchingView.setSearchPhase(safe);
            return;
        }
        searchPhase = safe;
        if (matchingView != null) matchingView.setSearchPhase(safe);
        if (countdownLabel != null) {
            countdownLabel.setText(safe == 1 ? "MENCARI DI SEKITAR JEMPUT" : (safe == 2 ? "MEMPERLUAS PENCARIAN" : "MENCARI DRIVER ONLINE"));
        }
        if (safe == 1) setSubtitle("Memprioritaskan driver di sekitar titik jemput");
        else if (safe == 2) setSubtitle("Mencari kecocokan di area yang lebih luas");
        else setSubtitle("Pencarian diperluas ke seluruh driver online");
        loadMatchingDrivers();
    }

    protected void stopMatchCountdown() {
        if (matchCountdown != null) {
            matchCountdown.cancel();
            matchCountdown = null;
        }
    }

    protected void playDriverAcceptedEffect() {
        if (pageRoot == null || destroyed) return;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setBackgroundColor(Color.argb(238, 1, 8, 24));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(26), dp(30), dp(26), dp(30));
        box.setBackground(roundStroke("#071B3C", "#F4C85A", dp(28), 2));

        TextView icon = text("✓", 48, "#FFFFFF", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundGradient("#10B981", "#0B7CFF", dp(48)));
        box.addView(icon, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView found = text("MATCH FOUND", 28, "#FFF1A6", true);
        found.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams foundLp = new LinearLayout.LayoutParams(-1, -2);
        foundLp.setMargins(0, dp(18), 0, dp(6));
        box.addView(found, foundLp);

        TextView desc = text("Driver menerima pesananmu • perjalanan siap dimulai", 15, "#D7E9FF", false);
        desc.setGravity(Gravity.CENTER);
        box.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(-1, -2);
        boxLp.gravity = Gravity.CENTER;
        boxLp.setMargins(dp(28), 0, dp(28), 0);
        overlay.addView(box, boxLp);
        pageRoot.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        overlay.setAlpha(0f);
        box.setScaleX(.55f); box.setScaleY(.55f);
        icon.setScaleX(.2f); icon.setScaleY(.2f);

        AnimatorSet enter = new AnimatorSet();
        ObjectAnimator fade = ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(box, View.SCALE_X, .55f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(box, View.SCALE_Y, .55f, 1f);
        enter.playTogether(fade, sx, sy);
        enter.setDuration(480L);
        enter.setInterpolator(new OvershootInterpolator(1.15f));
        enter.start();

        icon.animate().scaleX(1f).scaleY(1f).setStartDelay(180L).setDuration(420L)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();

        ValueAnimator glow = ValueAnimator.ofFloat(1f, 1.12f, 1f);
        glow.setDuration(720L);
        glow.setRepeatCount(1);
        glow.setInterpolator(new AccelerateDecelerateInterpolator());
        glow.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            icon.setScaleX(v); icon.setScaleY(v);
        });
        glow.start();

        mainHandler.postDelayed(() -> overlay.animate().alpha(0f).setDuration(380L).withEndAction(() -> {
            try { pageRoot.removeView(overlay); } catch (Exception ignored) {}
        }).start(), 1500L);
    }



    protected void loadMatchingDrivers() {
        if (!matchRequestInFlight.compareAndSet(false, true)) return;
        featureRuntime.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", normalizeDriverType(cachedDriverType));
                payload.put("order_id", activeOrderId);
                payload.put("search_phase", searchPhase);
                if (hasUserLocation) {
                    payload.put("latitude", userLat);
                    payload.put("longitude", userLng);
                    payload.put("max_km", searchPhase == 1 ? 12 : (searchPhase == 2 ? 24 : 40));
                    payload.put("limit", searchPhase == 1 ? 16 : (searchPhase == 2 ? 24 : 32));
                }

                JSONObject res = postJson(BASE_URL + "server/get_idle_drivers.php", payload);
                if (!res.optBoolean("success", false)) {
                    featureRuntime.post(mainHandler, () -> setSubtitle(firstNonEmpty(res.optString("message"), "Gagal mengambil driver")));
                    return;
                }

                JSONArray arr = res.optJSONArray("drivers");
                List<MatchDriver> drivers = parseDrivers(arr);
                int serverPhase = Math.max(1, Math.min(3, res.optInt("search_phase", searchPhase)));
                String activeOffer = firstNonEmpty(res.optString("offered_driver", ""), offeredDriverUsername);
                featureRuntime.post(mainHandler, () -> {
                    if (destroyed || driverFound || isCanceling) return;
                    searchPhase = serverPhase;
                    offeredDriverUsername = activeOffer;
                    matchingView.setSearchPhase(serverPhase);
                    matchingView.setOfferedDriver(activeOffer);
                    matchingView.setDriverCount(drivers.size());
                    if (drivers.size() > 0 && hasUserLocation) {
                        double nearest = nearestDistance(drivers);
                        setSubtitle(nearest < 0.1 ? "Driver tersedia di dekat titik jemput"
                                : "Driver terdekat sekitar " + String.format(Locale.US, "%.1f", nearest) + " km");
                    } else if (drivers.size() > 0) {
                        setSubtitle("Ada " + drivers.size() + " driver aktif, menunggu GPS akurat");
                    } else {
                        setSubtitle("Belum ada driver aktif di sekitar Anda");
                    }
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> setSubtitle("Koneksi gagal mengambil driver"));
            } finally {
                matchRequestInFlight.set(false);
            }
        });
    }

    protected List<MatchDriver> parseDrivers(JSONArray arr) {
        List<MatchDriver> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject d = arr.optJSONObject(i);
            if (d == null) continue;
            double lat = safeDouble(firstNonEmpty(d.optString("lat"), d.optString("latitude")));
            double lng = safeDouble(firstNonEmpty(d.optString("lng"), d.optString("longitude")));
            if (lat == 0 || lng == 0) continue;
            MatchDriver rd = new MatchDriver();
            rd.name = firstNonEmpty(d.optString("name"), d.optString("username"), "Driver");
            rd.username = firstNonEmpty(d.optString("username"), rd.name);
            rd.clusterName = d.optString("cluster_name", "");
            rd.lat = lat;
            rd.lng = lng;
            list.add(rd);
        }
        return list;
    }

    protected double nearestDistance(List<MatchDriver> drivers) {
        double best = 999999;
        for (MatchDriver d : drivers) {
            best = Math.min(best, distanceKm(userLat, userLng, d.lat, d.lng));
        }
        return best == 999999 ? 0 : best;
    }

    protected void checkOrderStatus() {
        if (!statusRequestInFlight.compareAndSet(false, true)) return;
        featureRuntime.execute(() -> {
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
                CustomerOrderBreadcrumbs.state(activeOrderId, status, "search_driver");

                String currentOffer = firstNonEmpty(
                        order.optString("offered_driver", ""),
                        res.optString("offered_driver", "")
                ).trim();
                int phaseFromServer = Math.max(1, Math.min(3, Math.max(
                        order.optInt("search_phase", 0), res.optInt("search_phase", 0))));
                if (phaseFromServer <= 0) phaseFromServer = searchPhase;
                final int finalPhaseFromServer = phaseFromServer;
                final String finalCurrentOffer = currentOffer;
                featureRuntime.post(mainHandler, () -> {
                    offeredDriverUsername = finalCurrentOffer;
                    setSearchPhase(finalPhaseFromServer);
                    if (matchingView != null) matchingView.setOfferedDriver(finalCurrentOffer);
                });

                final JSONObject statusOrder = new JSONObject(order.toString());
                statusOrder.put("status", status);
                if (statusOrder.optString("order_id").isEmpty()) statusOrder.put("order_id", activeOrderId);
                if (statusOrder.optString("order_type").isEmpty()) statusOrder.put("order_type", res.optString("order_type"));
                featureRuntime.post(mainHandler, () -> {
                    if (!isFinishing() && !isDestroyed() && searchStatus != null)
                        searchStatus.bind(statusOrder, "", "Pesanan sedang diproses");
                });
                String orderType = firstNonEmpty(order.optString("order_type", ""), res.optString("order_type", "")).trim().toLowerCase(Locale.US);
                cachedDriverType = normalizeDriverType(firstNonEmpty(
                        order.optString("driver_type", ""),
                        res.optString("driver_type", ""),
                        orderType, cachedDriverType));
                String merchantStatus = firstNonEmpty(order.optString("merchant_status", ""), res.optString("merchant_status", "")).trim().toLowerCase(Locale.US);
                int cookMinutes = Math.max(order.optInt("cook_minutes", 0), res.optInt("cook_minutes", 0));
                if ("food".equals(orderType) && !merchantStatus.isEmpty() && !isDriverAcceptedStatus(status)) {
                    String merchantLine;
                    if ("ready".equals(merchantStatus)) merchantLine = "Pesanan sudah siap diambil • menunggu driver";
                    else if ("preparing".equals(merchantStatus)) merchantLine = "Merchant sedang menyiapkan pesanan" + (cookMinutes > 0 ? (" • ±" + cookMinutes + " menit") : "");
                    else if ("merchant_accepted".equals(merchantStatus)) merchantLine = "Merchant menerima pesanan" + (cookMinutes > 0 ? (" • estimasi " + cookMinutes + " menit") : "");
                    else merchantLine = "";
                    if (!merchantLine.isEmpty()) {
                        String finalMerchantLine = merchantLine;
                        featureRuntime.post(mainHandler, () -> setSubtitle(finalMerchantLine));
                    }
                }

                if (status.equals("canceled") || status.equals("cancelled")) {
                    CustomerRedispatchService.stop(this);
                    clearOrderPrefs();
                    featureRuntime.post(mainHandler, () -> {
                        destroyLoops();
                        openCustomerDashboard();
                    });
                    return;
                }

                // FIX MATCHING:
                // Backend lama/fitur tertentu tidak selalu memakai literal "driver_accepted".
                // Pickup/TransSend bisa memakai "taken", sedangkan endpoint kompatibilitas
                // dapat mengembalikan "accepted"/"assigned". Flag is_taken/driver_found
                // berasal dari order.driver yang sudah benar-benar terisi, BUKAN offered_driver,
                // sehingga aman dipakai sebagai sinyal bahwa driver sudah memenangkan order.
                boolean serverTaken = res.optBoolean("is_taken", false);
                boolean serverDriverFound = res.optBoolean("driver_found", false);
                String assignedDriver = firstNonEmpty(
                        order.optString("driver", ""),
                        order.optString("driver_username", ""),
                        res.optString("driver_username", "")
                ).trim();

                boolean trulyAccepted = isDriverAcceptedStatus(status)
                        || serverTaken
                        || (serverDriverFound && !assignedDriver.isEmpty());

                if (trulyAccepted) {
                    featureRuntime.post(mainHandler, () -> showDriver(res));
                }
            } catch (Exception ignored) {
            } finally {
                statusRequestInFlight.set(false);
            }
        });
    }

    protected boolean isDriverAcceptedStatus(String rawStatus) {
        return CustomerOrderState.hasDriver(rawStatus);
    }

    protected void showDriver(JSONObject data) {
        CustomerRedispatchService.stop(this);
        if (isCanceling || driverFound || destroyed) return;
        driverFound = true;
        stopMatchCountdown();
        destroyLoopsKeepScreen();
        matchingView.stopMatching();
        matchingView.setVisibility(View.GONE);
        countdownLabel.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        playDriverAcceptedEffect();

        titleText.setText("DRIVER SUDAH TERHUBUNG");
        setSubtitle("Driver menerima pesananmu • membuka perjalanan sekarang");
        cancelBtn.setVisibility(View.GONE);
        driverCard.setVisibility(View.VISIBLE);
        driverCard.setAlpha(0f);
        driverCard.setScaleX(.88f);
        driverCard.setScaleY(.88f);
        driverCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(850L).setDuration(520L)
                .setInterpolator(new OvershootInterpolator(1.08f)).start();

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

        // Begitu server menyatakan driver benar-benar menerima order, layar pencarian
        // harus berhenti. Beri jeda sangat singkat hanya untuk feedback visual,
        // lalu langsung pindah ke CustomerTripActivity.
        mainHandler.postDelayed(() -> openNativeTrip(driverType, pickupLat, pickupLng, deliveryLat, deliveryLng), 450);
    }

    protected void openNativeTrip(String driverType, double pickupLat, double pickupLng, double deliveryLat, double deliveryLng) {
        if (isCanceling || destroyed) return;
        try {
            Intent i = new Intent(SearchDriverActivityScreenCore.this, CustomerTripActivity.class);
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
            // HARD CLOSE MATCHMAKING TASK:
            // Beberapa perangkat (terutama yang memiliki mode floating/freeform window)
            // bisa mempertahankan SearchDriverActivity sebagai jendela kecil walaupun finish()
            // dipanggil setelah CustomerTripActivity dibuka. Jadikan CustomerTrip sebagai root
            // task baru agar layar/radar pencarian benar-benar dihancurkan.
            destroyLoops();
            if (matchingView != null) {
                matchingView.stopMatching();
                matchingView.setVisibility(View.GONE);
            }

            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        } catch (Exception e) {
            showInfo("Trip Native", "CustomerTripActivity belum ditemukan. Pastikan file Java dan AndroidManifest sudah ditambahkan.");
        }
    }

    protected double firstPositiveDouble(double... values) {
        if (values == null) return 0;
        for (double value : values) if (value > 0) return value;
        return 0;
    }

    protected void loadDriverPhoto(String rawUrl) {
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

        featureRuntime.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = CustomerApiClient.open(this, photoUrl);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(true);
                conn.connect();
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        featureRuntime.post(mainHandler, () -> {
                            if (destroyed || driverPhotoView == null) return;
                            driverPhotoView.setImageBitmap(bitmap);
                            driverPhotoView.setVisibility(View.VISIBLE);
                            driverAvatarText.setVisibility(View.GONE);
                        });
                    }
                }
            } catch (Exception ignored) {
                featureRuntime.post(mainHandler, () -> {
                    if (driverPhotoView != null) driverPhotoView.setVisibility(View.GONE);
                    if (driverAvatarText != null) driverAvatarText.setVisibility(View.VISIBLE);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    protected void loadDriverMap(String driverName, double driverLat, double driverLng, double pickupLat, double pickupLng) {
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
                "window.transivaThemeTiles=L.tileLayer('" + tileUrl + "').addTo(map);" +
                "var d=[" + driverLat + "," + driverLng + "], p=[" + pickupLat + "," + pickupLng + "];" +
                "L.marker(d).addTo(map).bindPopup('" + jsSafe(driverName) + "').openPopup();" +
                "L.marker(p).addTo(map).bindPopup('Lokasi Jemput');" +
                "var line=L.polyline([d,p],{color:'#0B7CFF',weight:5,opacity:.9}).addTo(map);" +
                "map.fitBounds(line.getBounds(),{padding:[30,30]});" +
                "</script></body></html>";

        miniMap.loadDataWithBaseURL("https://transiva.my.id/", html, "text/html", "UTF-8", null);
    }

    protected void saveTripPrefs(String orderId, String driverType, double pickupLat, double pickupLng, double deliveryLat, double deliveryLng) {
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

    protected String normalizeDriverType(String raw) {
        String type = firstNonEmpty(raw, "bike").toLowerCase(Locale.US).trim();
        if (type.equals("transcar") || type.equals("car") || type.equals("mobil")) return "car";
        return "motor";
    }

    protected double getJsonDouble(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return 0;
        for (String key : keys) {
            if (key == null || key.length() == 0) continue;
            double value = safeDouble(obj.optString(key, ""));
            if (value != 0) return value;
        }
        return 0;
    }

    protected double getDoublePref(String key) {
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

    protected double firstValidCoordValue(double... values) {
        if (values == null) return 0;
        for (double v : values) {
            if (v != 0 && Double.isFinite(v)) return v;
        }
        return 0;
    }

    protected void confirmCancelOrder() {
        new TransivaAlertDialogBuilder(this)
                .setTitle("Batalkan Order")
                .setMessage("Yakin ingin membatalkan pencarian driver?")
                .setNegativeButton("Tidak", null)
                .setPositiveButton("Ya", (d, w) -> cancelOrder())
                .show();
    }



    private void cancelOrder() {
        CustomerRedispatchService.stop(this);
        if (isCanceling) return;
        isCanceling = true;
        destroyLoopsKeepScreen();
        cancelBtn.setEnabled(false);
        cancelBtn.setText("Membatalkan...");
        titleText.setText("Membatalkan Order...");
        setSubtitle("Mohon tunggu sebentar");
        progressBar.setVisibility(View.VISIBLE);

        featureRuntime.execute(() -> {
            try {
                String token = new SessionManager(this).getToken();
                if (token == null || token.trim().isEmpty()) {
                    throw new IllegalStateException(
                            "Sesi login tidak ditemukan. Silakan login kembali."
                    );
                }

                JSONObject payload = new JSONObject();
                payload.put("order_id", activeOrderId);
                JSONObject res = postJson(
                        BASE_URL + "server/cancel_order.php",
                        payload,
                        token.trim()
                );
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Order dibatalkan" : "Order gagal dibatalkan");
                featureRuntime.post(mainHandler, () -> {
                    progressBar.setVisibility(View.GONE);
                    if (ok) {
                        clearOrderPrefs();
                        openCustomerDashboard();
                    } else {
                        isCanceling = false;
                        cancelBtn.setEnabled(true);
                        cancelBtn.setText("Batalkan pesanan");
                        titleText.setText("Mencari Driver...");
                        showInfo("Gagal", msg);
                        startLoops();
                    }
                });
            } catch (Exception e) {
                featureRuntime.post(mainHandler, () -> {
                    progressBar.setVisibility(View.GONE);
                    isCanceling = false;
                    cancelBtn.setEnabled(true);
                    cancelBtn.setText("Batalkan pesanan");
                    titleText.setText("Mencari Driver...");
                    showInfo("Koneksi Gagal", "Koneksi gagal saat membatalkan order");
                    startLoops();
                });
            }
        });
    }

    private JSONObject postJson(
            String urlText,
            JSONObject payload
    ) throws Exception {
        return postJson(urlText, payload, "");
    }

    private JSONObject postJson(
            String urlText,
            JSONObject payload,
            String bearerToken
    ) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = CustomerApiClient.open(this, urlText);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            // Semua endpoint customer Transiva memakai p0_require_customer().
            // SearchDriverActivity sebelumnya melewati CustomerApiClient sehingga
            // check_order_status.php dipanggil TANPA Bearer token. Server lalu
            // menolak request dan radar tidak pernah melihat driver_accepted.

            // Token eksplisit (mis. cancel order) tetap boleh meng-overwrite token sesi.
            if (bearerToken != null && !bearerToken.trim().isEmpty()) {
                conn.setRequestProperty(
                        "Authorization",
                        "Bearer " + bearerToken.trim()
                );
            }
            conn.setRequestProperty(
                    "X-Device-UUID",
                    DeviceIdentityManager.getInstallationUuid(this)
            );

            conn.setRequestProperty("X-App-Scope", "customer");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload == null ? "{}" : payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            CustomerApiClient.handleSessionResponse(this, code, body);
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
        stopMatchCountdown();
        destroyed = true;
        mainHandler.removeCallbacks(driverMatchRunnable);
        mainHandler.removeCallbacks(checkOrderRunnable);
    }

    private void destroyLoopsKeepScreen() {
        stopMatchCountdown();
        mainHandler.removeCallbacks(driverMatchRunnable);
        mainHandler.removeCallbacks(checkOrderRunnable);
    }


    @Override
    protected void onResume() {
        super.onResume();
        featureRuntime.onResume();
        if (matchingView != null) matchingView.setAnimatingEnabled(!driverFound && !isCanceling);
    }

    @Override
    protected void onPause() {
        if (matchingView != null) matchingView.setAnimatingEnabled(false);
        featureRuntime.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        featureRuntime.destroy();
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
            else setSubtitle("Izin lokasi belum aktif, pencarian tetap berjalan");
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
            new TransivaAlertDialogBuilder(this)
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
        return CustomerCommonFormatters.firstBasic(values);
    }

    private double safeDouble(String v) {
        try { return Double.parseDouble(v == null ? "" : v.trim()); } catch (Exception e) { return 0; }
    }

    private String jsSafe(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        return CustomerGeoMath.distanceKm(lat1, lng1, lat2, lng2);
    }

    private int dp(int v) {
        return CustomerUiPrimitives.dp(this, v);
    }

    private static class MatchDriver {
        String name = "Driver";
        String username = "";
        String clusterName = "";
        double lat;
        double lng;
    }

}
