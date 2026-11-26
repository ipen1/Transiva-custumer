package com.transiva.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/** The only positions rendered are the pickup and the driver confirmed by the order endpoint. */
final class RideSearchDroneView extends FrameLayout implements CustomerAppSettings.ThemeAware {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable orbitStep = this::orbitPickup;
    private final MapView mapView;
    private final View mapFallback;
    private final View scrim;
    private final LinearLayout searchHeader, searchControls;
    private ValueAnimator orbitAnimation;
    private float pickupBearing;
    private final boolean car;

    private GoogleMap map;
    private Marker pickupMarker, driverMarker;
    private LatLng pickup, acceptedPosition;
    private boolean loaded, started, resumed, closed, accepted, stopped;
    private Boolean lastDark;

    RideSearchDroneView(Context context, boolean car) {
        super(context);
        this.car = car;
        setBackground(panel(0xFF091B32, 0xFF314D69, 24));
        setClipToOutline(true);
        CustomerAppSettings.preserveArtwork(this);

        mapView = new MapView(context);
        addView(mapView, new LayoutParams(-1,-1));
        mapFallback=new View(context);
        mapFallback.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF173753,0xFF09192D}));
        addView(mapFallback,new LayoutParams(-1,-1));
        scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xC809172A,0x3509172A,0xCB09172A}));
        LayoutParams scrimParams=new LayoutParams(-1,-1);
        scrimParams.bottomMargin=dp(26);
        addView(scrim,scrimParams);

        // Keep map touches away from Google Maps while leaving the controls above it interactive.
        View mapTouchShield = new View(context);
        mapTouchShield.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mapTouchShield.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return true;
        });
        addView(mapTouchShield, new LayoutParams(-1, -1));

        searchHeader = column();
        searchHeader.setPadding(dp(14),dp(12),dp(14),dp(12));
        searchHeader.setBackground(panel(0xDD10253F,0x8A729DBF,19));
        LayoutParams headerParams = new LayoutParams(-1,-2,Gravity.TOP);
        headerParams.setMargins(dp(14),dp(16),dp(14),0);
        addView(searchHeader,headerParams);

        searchControls = column();
        searchControls.setPadding(dp(14),dp(10),dp(14),dp(10));
        searchControls.setBackground(panel(0xEB10253F,0x8A729DBF,21));
        LayoutParams controlsParams = new LayoutParams(-1,-2,Gravity.BOTTOM);
        controlsParams.setMargins(dp(14),0,dp(14),dp(32));
        addView(searchControls,controlsParams);

        mapView.onCreate(null);
        mapView.getMapAsync(this::mapReady);
    }

    void installSearchControls(TextView title,TextView subtitle,TextView phase,TextView countdown,View cancel) {
        searchHeader.addView(title,new LinearLayout.LayoutParams(-1,-2));
        subtitle.setPadding(0,dp(4),0,0);
        searchHeader.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));
        searchControls.addView(phase,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams clock = new LinearLayout.LayoutParams(-2,-2);
        clock.topMargin=dp(4);
        clock.bottomMargin=dp(8);
        searchControls.addView(countdown,clock);
        searchControls.addView(cancel,new LinearLayout.LayoutParams(-1,-2));
    }

    private void mapReady(GoogleMap value) {
        if (closed) return;
        map=value;
        applyStyle(CustomerAppSettings.isDarkMode(getContext()));
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setAllGesturesEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.setOnMapLoadedCallback(() -> {
            if (closed) return;
            loaded=true;
            updateFallback();
            if (accepted) renderAccepted();
            else scheduleOrbit(350);
        });
        if (accepted) renderAccepted();
        else placePickup();
    }

    void setOrigin(double latitude,double longitude) {
        if (accepted || !CustomerGeoMath.valid(latitude,longitude)) return;
        LatLng position=new LatLng(latitude,longitude);
        if (position.equals(pickup)) return;
        pickup=position;
        placePickup();
        updateFallback();
        scheduleOrbit(350);
    }

    private void placePickup() {
        if (map==null || pickup==null) return;
        if (pickupMarker==null) {
            pickupMarker=map.addMarker(new MarkerOptions().position(pickup)
                    .anchor(.5f,1f).icon(image(R.drawable.map_pickup_pin,42,54)));
        } else pickupMarker.setPosition(pickup);
        map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(pickup).zoom(15.6f).tilt(motionEnabled()?43f:0f).build()));
    }

    private void scheduleOrbit(long delay) {
        main.removeCallbacks(orbitStep);
        if (resumed && loaded && !accepted && !stopped && !closed && pickup!=null && motionEnabled()) {
            main.postDelayed(orbitStep,delay);
        }
    }

    private void orbitPickup() {
        if (map==null || pickup==null || !resumed || accepted || stopped || closed || !motionEnabled()) return;
        stopOrbitAnimation();
        pickupBearing=map.getCameraPosition().bearing;
        orbitAnimation=ValueAnimator.ofFloat(0f,1f);
        orbitAnimation.setDuration(13000L);
        orbitAnimation.setRepeatCount(ValueAnimator.INFINITE);
        orbitAnimation.setInterpolator(new android.view.animation.LinearInterpolator());
        orbitAnimation.addUpdateListener(frame -> {
            float portion=(float)frame.getAnimatedValue();
            float bearing=(pickupBearing+360f*portion)%360f;
            float hop=(float)Math.pow(Math.max(0,Math.sin(portion*Math.PI*12)),2);
            pickupMarker.setAnchor(.5f,1f+hop*.16f);
            map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                    .target(pickup).zoom(15.6f).tilt(43f).bearing(bearing).build()));
        });
        orbitAnimation.start();
    }

    private void stopOrbitAnimation() {
        if (orbitAnimation!=null) {
            orbitAnimation.cancel();
            orbitAnimation=null;
        }
        if (pickupMarker!=null) pickupMarker.setAnchor(.5f,1f);
    }

    boolean showAccepted(double lat,double lng) {
        accepted=true;
        acceptedPosition=CustomerGeoMath.valid(lat,lng)?new LatLng(lat,lng):null;
        stopScanning();
        searchHeader.setVisibility(GONE);
        searchControls.setVisibility(GONE);
        if (motionEnabled()) {
            scrim.animate().alpha(0f).setDuration(350).withEndAction(()->scrim.setVisibility(GONE)).start();
        } else scrim.setVisibility(GONE);
        renderAccepted();
        return acceptedPosition!=null;
    }

    private void renderAccepted() {
        updateFallback();
        if (map==null || closed || acceptedPosition==null || !loaded || !resumed) return;
        if (pickupMarker!=null) pickupMarker.setVisible(false);
        if (driverMarker==null) {
            driverMarker=map.addMarker(new MarkerOptions().position(acceptedPosition)
                    .icon(image(car?R.drawable.map_car_top:R.drawable.map_motor_top,62,62))
                    .anchor(.5f,.5f).zIndex(4));
        }
        CameraPosition closeUp=new CameraPosition.Builder().target(acceptedPosition).zoom(17.8f)
                .tilt(motionEnabled()?45f:0f).bearing(18f).build();
        if (motionEnabled()) map.animateCamera(CameraUpdateFactory.newCameraPosition(closeUp),1050,null);
        else map.moveCamera(CameraUpdateFactory.newCameraPosition(closeUp));
    }

    private void updateFallback() {
        if (!loaded || (pickup==null && acceptedPosition==null)) return;
        mapFallback.setVisibility(GONE);
    }

    private boolean motionEnabled() {
        if (Build.VERSION.SDK_INT>=26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0;
    }

    void startMap() {
        if (!started && !closed) {
            started=true;
            mapView.onStart();
        }
    }

    void resumeMap() {
        if (resumed || closed) return;
        resumed=true;
        mapView.onResume();
        if (accepted) renderAccepted();
        else scheduleOrbit(0);
    }

    void pauseMap() {
        if (!resumed) return;
        resumed=false;
        main.removeCallbacks(orbitStep);
        stopOrbitAnimation();
        if (map!=null) map.stopAnimation();
        mapView.onPause();
    }

    void stopMap() {
        if (!started) return;
        started=false;
        mapView.onStop();
    }

    void stopScanning() {
        stopped=true;
        main.removeCallbacks(orbitStep);
        stopOrbitAnimation();
        if (map!=null) map.stopAnimation();
    }

    void resumeScanning() {
        if (accepted || closed) return;
        stopped=false;
        scheduleOrbit(0);
    }

    void lowMemory() {
        if (!closed) mapView.onLowMemory();
    }

    void close() {
        if (closed) return;
        closed=true;
        stopScanning();
        pauseMap();
        stopMap();
        mapView.onDestroy();
        map=null;
    }

    @Override public void onCustomerThemeChanged(boolean dark) {
        if (map!=null && (lastDark==null || lastDark!=dark)) applyStyle(dark);
    }

    private void applyStyle(boolean dark) {
        map.setMapStyle(dark?MapStyleOptions.loadRawResourceStyle(getContext(),R.raw.google_map_dark):null);
        lastDark=dark;
    }

    private BitmapDescriptor image(int resource,int width,int height) {
        int w=dp(width),h=dp(height);
        Bitmap source=BitmapFactory.decodeResource(getResources(),resource);
        if (source==null) return BitmapDescriptorFactory.defaultMarker();
        Bitmap artwork=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        float scale=Math.min((float)w/source.getWidth(),(float)h/source.getHeight());
        float drawWidth=scale*source.getWidth(), drawHeight=scale*source.getHeight();
        new Canvas(artwork).drawBitmap(source,null,new RectF((w-drawWidth)/2,(h-drawHeight)/2,
                (w+drawWidth)/2,(h+drawHeight)/2),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        source.recycle();
        return BitmapDescriptorFactory.fromBitmap(artwork);
    }

    private LinearLayout column() {
        LinearLayout layout=new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        return layout;
    }

    private GradientDrawable panel(int color,int stroke,int radius) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);
        shape.setStroke(dp(1),stroke);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) {
        return CustomerUiPrimitives.dp(getContext(),value);
    }
}
