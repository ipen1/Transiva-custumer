package com.transiva.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
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
    private final LinearLayout searchHeader, searchControls, bubble;
    private final TextView driverName;
    private final View pointer;
    private final boolean car;

    private GoogleMap map;
    private Marker pickupMarker, driverMarker;
    private LatLng pickup, acceptedPosition;
    private boolean loaded, started, resumed, closed, accepted, stopped;
    private boolean flyingToDriver;
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

        bubble = column();
        bubble.setClipChildren(false);
        bubble.setClipToPadding(false);
        LinearLayout copy = column();
        copy.setPadding(dp(15),dp(9),dp(15),dp(10));
        copy.setBackground(panel(0xFF103652,0xFF79F2CA,16));
        driverName = caption(17,0xFFF3FFFF,true);
        TextView driverReady = caption(12,0xFFB8F7E3,false);
        driverReady.setText("siap menjalankan orderan");
        copy.addView(driverName);
        copy.addView(driverReady);
        pointer = new View(context);
        pointer.setBackgroundColor(0xFF79F2CA);
        pointer.setRotation(45f);
        LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(10),dp(10));
        arrow.topMargin = -dp(5);
        bubble.addView(copy);
        bubble.addView(pointer,arrow);
        bubble.setElevation(dp(5));
        bubble.setVisibility(INVISIBLE);
        bubble.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        bubble.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionBubble());
        addView(bubble,new LayoutParams(-2,-2,Gravity.TOP | Gravity.LEFT));

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
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.getUiSettings().setTiltGesturesEnabled(false);
        map.setOnMarkerClickListener(marker->true);
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason==GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE && !accepted) scheduleOrbit(4300);
        });
        map.setOnCameraMoveListener(() -> { if (accepted) positionBubble(); });
        map.setOnCameraIdleListener(this::positionBubble);
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
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
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
        float bearing=(map.getCameraPosition().bearing+72f)%360f;
        CameraPosition shot=new CameraPosition.Builder().target(pickup).zoom(15.6f)
                .tilt(43f).bearing(bearing).build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(shot),2200,null);
        scheduleOrbit(2240);
    }

    boolean showAccepted(String name,double lat,double lng) {
        accepted=true;
        acceptedPosition=CustomerGeoMath.valid(lat,lng)?new LatLng(lat,lng):null;
        stopScanning();
        searchHeader.setVisibility(GONE);
        searchControls.setVisibility(GONE);
        driverName.setText(name);
        bubble.setContentDescription(name+" siap menjalankan orderan");
        bubble.setAlpha(1f);
        if (motionEnabled()) {
            scrim.animate().alpha(0f).setDuration(350).withEndAction(()->scrim.setVisibility(GONE)).start();
        } else scrim.setVisibility(GONE);
        renderAccepted();
        return acceptedPosition!=null;
    }

    private void renderAccepted() {
        updateFallback();
        if (map==null || closed || acceptedPosition==null || !loaded || !resumed) {
            flyingToDriver=false;
            positionBubble();
            return;
        }
        if (pickupMarker!=null) pickupMarker.setVisible(false);
        if (driverMarker==null) {
            driverMarker=map.addMarker(new MarkerOptions().position(acceptedPosition)
                    .icon(vehicleIcon()).anchor(.5f,.5f).zIndex(4));
        }
        CameraPosition closeUp=new CameraPosition.Builder().target(acceptedPosition).zoom(17.8f)
                .tilt(motionEnabled()?45f:0f).bearing(18f).build();
        if (!motionEnabled()) {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(closeUp));
            positionBubble();
            return;
        }
        flyingToDriver=true;
        bubble.setVisibility(INVISIBLE);
        map.animateCamera(CameraUpdateFactory.newCameraPosition(closeUp),1150,new GoogleMap.CancelableCallback() {
            @Override public void onFinish() { revealBubble(); }
            @Override public void onCancel() {
                flyingToDriver=false;
                positionBubble();
            }
        });
    }

    private void revealBubble() {
        flyingToDriver=false;
        if (!resumed || closed) return;
        positionBubble();
        if (bubble.getVisibility()==VISIBLE) {
            bubble.setAlpha(0f);
            bubble.animate().alpha(1f).setDuration(260).start();
        }
    }

    private void positionBubble() {
        if (!accepted || closed || flyingToDriver || bubble.getWidth()==0 || getWidth()==0) return;
        int inset=dp(10);
        boolean anchored=map!=null && loaded && acceptedPosition!=null && driverMarker!=null;
        Point anchor=anchored?map.getProjection().toScreenLocation(acceptedPosition):null;
        if (anchor!=null && (anchor.x<0 || anchor.x>getWidth() || anchor.y<0 || anchor.y>getHeight())) {
            bubble.setVisibility(INVISIBLE);
            return;
        }
        pointer.setVisibility(anchored?VISIBLE:INVISIBLE);
        float center=anchor==null?getWidth()/2f:anchor.x;
        float left=Math.max(inset,Math.min(getWidth()-bubble.getWidth()-inset,center-bubble.getWidth()/2f));
        bubble.setTranslationX(left);
        bubble.setTranslationY(anchor==null?dp(18):Math.max(dp(5),anchor.y-bubble.getHeight()-dp(34)));
        pointer.setTranslationX(anchor==null?0f:
                Math.max(-bubble.getWidth()/2f+dp(13),
                        Math.min(bubble.getWidth()/2f-dp(13),center-left-bubble.getWidth()/2f)));
        bubble.setVisibility(VISIBLE);
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

    private BitmapDescriptor vehicleIcon() {
        int pixels=dp(66);
        Bitmap art=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(art);
        float center=pixels/2f;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColor(0x6988FFD4);
        canvas.drawCircle(center,center,center,paint);
        paint.setColor(0xFF88FFD4);
        canvas.drawCircle(center,center,center-dp(3),paint);
        paint.setColor(0xFF0B2B47);
        canvas.drawCircle(center,center,center-dp(5),paint);
        Bitmap vehicle=BitmapFactory.decodeResource(getResources(),car?R.drawable.map_car_top:R.drawable.map_motor_top);
        if (vehicle!=null) {
            float factor=center*1.38f/Math.max(vehicle.getWidth(),vehicle.getHeight());
            float halfWidth=vehicle.getWidth()*factor/2,halfHeight=vehicle.getHeight()*factor/2;
            canvas.drawBitmap(vehicle,null,new RectF(center-halfWidth,center-halfHeight,center+halfWidth,center+halfHeight),paint);
            vehicle.recycle();
        }
        return BitmapDescriptorFactory.fromBitmap(art);
    }

    private LinearLayout column() {
        LinearLayout layout=new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        return layout;
    }

    private TextView caption(float size,int color,boolean bold) {
        TextView view=new TextView(getContext());
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setMaxLines(2);
        view.setMaxWidth(dp(244));
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
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
