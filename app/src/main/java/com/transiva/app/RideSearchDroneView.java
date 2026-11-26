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
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns the presentation camera only. Booking and assignment remain owned by the search screen. */
final class RideSearchDroneView extends FrameLayout implements CustomerAppSettings.ThemeAware {
    private static final long CAMERA_IDLE_AFTER_TOUCH_MS = 4500;
    private static final long SHOT_INTERVAL_MS = 2700;
    private static final float OVERVIEW_ZOOM = 13.2f;
    private static final float DRIVER_ZOOM = 16.7f;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable nextShot = this::showNextDriver;
    private final MapView mapView;
    private final CustomerMatchmakingView radar;
    private final TextView badgeLabel, badgeName;
    private final Map<String, LatLng> nearby = new LinkedHashMap<>();
    private final Map<String, Marker> markers = new LinkedHashMap<>();
    private final boolean car;
    private GoogleMap map;
    private BitmapDescriptor regularIcon, focusIcon, assignedIcon;
    private Marker pickupMarker, confirmedMarker;
    private String featured = "";
    private LatLng pickup, acceptedPosition;
    private boolean loaded, started, resumed, closed, accepted, stopped;
    private boolean pendingOverview = true;
    private boolean shotScheduled;
    private long manualUntil;

    RideSearchDroneView(Context context, boolean car, CustomerMatchmakingView radar) {
        super(context);
        this.car = car;
        this.radar = radar;
        setBackground(panel(0xFF091B32, 0xFF314D69, 22));
        setClipToOutline(true);
        CustomerAppSettings.preserveArtwork(this);

        mapView = new MapView(context);
        addView(mapView, new LayoutParams(-1, -1));
        radar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(radar, new LayoutParams(-1, -1));

        LinearLayout badge = new LinearLayout(context);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setPadding(dp(13), dp(8), dp(13), dp(8));
        badge.setBackground(panel(0xF4112C49, 0xFF4C779F, 16));
        badgeLabel = caption(11, 0xFFB7DEFF);
        badgeName = caption(17, 0xFFF4FAFF);
        badgeName.setVisibility(GONE);
        badge.addView(badgeLabel);
        badge.addView(badgeName);
        LayoutParams position = new LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        position.setMargins(dp(12), dp(12), dp(12), 0);
        addView(badge, position);
        badgeLabel.setText("Mencari driver sekitar");

        mapView.onCreate(null);
        mapView.getMapAsync(this::mapReady);
    }

    private void mapReady(GoogleMap value) {
        if (closed) return;
        map = value;
        applyStyle(CustomerAppSettings.isDarkMode(getContext()));
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.getUiSettings().setTiltGesturesEnabled(false);
        regularIcon = vehicleIcon(38, 0xFF88CBFF);
        focusIcon = vehicleIcon(53, 0xFF44E2FF);
        assignedIcon = vehicleIcon(63, 0xFF88FFD4);

        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE && !stopped && !accepted) {
                manualUntil = SystemClock.elapsedRealtime() + CAMERA_IDLE_AFTER_TOUCH_MS;
                schedule(CAMERA_IDLE_AFTER_TOUCH_MS);
            }
        });
        map.setOnMarkerClickListener(marker -> true);
        map.setOnMapLoadedCallback(() -> {
            if (closed) return;
            loaded = true;
            showMapIfAvailable();
            if (accepted) renderAccepted();
            else if (resumed && !stopped) schedule(250);
        });
        syncMarkers();
        if (accepted) renderAccepted();
        else overview(false);
    }

    void setOrigin(double latitude, double longitude) {
        if (accepted || !CustomerGeoMath.valid(latitude, longitude)) return;
        LatLng next = new LatLng(latitude, longitude);
        if (next.equals(pickup)) return;
        pickup = next;
        pendingOverview = true;
        syncMarkers();
        showMapIfAvailable();
        if (resumed && loaded && !stopped) schedule(0);
    }

    void setDrivers(Map<String, LatLng> points) {
        if (accepted || stopped) return;
        boolean hadDrivers = !nearby.isEmpty();
        nearby.clear();
        nearby.putAll(points);
        if (!hadDrivers && !nearby.isEmpty()) pendingOverview = true;
        syncMarkers();
        showMapIfAvailable();
        // Keep the existing camera cadence while polling updates the marker coordinates.
        if (resumed && loaded && !shotScheduled) schedule(0);
    }

    boolean showAccepted(String driverName, String assignedUsername, double lat, double lng) {
        accepted = true;
        acceptedPosition = CustomerGeoMath.valid(lat,lng) ? new LatLng(lat,lng)
                : nearby.get(assignedUsername.trim().toLowerCase(Locale.ROOT));
        stopScanning();
        badgeLabel.setText("✓ Driver menerima pesanan");
        badgeName.setText(driverName);
        badgeName.setVisibility(VISIBLE);
        badgeName.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (motionEnabled()) {
            badgeName.setAlpha(0f);
            badgeName.setTranslationY(dp(8));
            badgeName.animate().alpha(1f).translationY(0f).setDuration(330).start();
        }
        renderAccepted();
        return loaded && (acceptedPosition != null || pickup != null);
    }

    private void renderAccepted() {
        if (map == null || closed) return;
        for (Marker marker : markers.values()) marker.remove();
        markers.clear();
        if (acceptedPosition != null && confirmedMarker == null) {
            confirmedMarker = map.addMarker(new MarkerOptions().position(acceptedPosition)
                    .icon(assignedIcon).anchor(.5f,.5f).zIndex(4));
        }
        showMapIfAvailable();
        if (acceptedPosition != null && loaded && resumed) {
            CameraPosition closeUp = new CameraPosition.Builder().target(acceptedPosition)
                    .zoom(17.8f).tilt(motionEnabled() ? 44f : 0).bearing(15f).build();
            move(CameraUpdateFactory.newCameraPosition(closeUp), 1050);
        }
    }

    private void syncMarkers() {
        if (map == null) return;
        if (pickup != null) {
            if (pickupMarker == null) {
                pickupMarker = map.addMarker(new MarkerOptions().position(pickup)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .zIndex(1));
            } else pickupMarker.setPosition(pickup);
        }

        java.util.Iterator<Map.Entry<String, Marker>> old = markers.entrySet().iterator();
        while (old.hasNext()) {
            Map.Entry<String, Marker> entry = old.next();
            if (nearby.containsKey(entry.getKey())) continue;
            entry.getValue().remove();
            old.remove();
        }
        for (Map.Entry<String, LatLng> point : nearby.entrySet()) {
            Marker marker = markers.get(point.getKey());
            if (marker == null) {
                marker = map.addMarker(new MarkerOptions().position(point.getValue())
                        .icon(regularIcon).anchor(.5f,.5f).zIndex(2));
                if (marker != null) markers.put(point.getKey(), marker);
            } else if (!point.getValue().equals(marker.getPosition())) marker.setPosition(point.getValue());
        }
    }

    private void showMapIfAvailable() {
        if (!loaded || (pickup == null && acceptedPosition == null && nearby.isEmpty())) return;
        radar.setAnimatingEnabled(false);
        radar.setVisibility(GONE);
    }

    private void schedule(long delay) {
        main.removeCallbacks(nextShot);
        shotScheduled = resumed && loaded && !stopped && !closed;
        if (shotScheduled) main.postDelayed(nextShot, delay);
    }

    private void showNextDriver() {
        shotScheduled = false;
        if (map == null || stopped || !resumed || closed) return;
        long wait = manualUntil - SystemClock.elapsedRealtime();
        if (wait > 0) {
            schedule(wait);
            return;
        }
        if (pendingOverview || !motionEnabled() || nearby.isEmpty()) {
            pendingOverview = false;
            overview(true);
            if (motionEnabled() && !nearby.isEmpty()) schedule(1550);
            return;
        }

        List<String> order = new ArrayList<>(nearby.keySet());
        int index = order.indexOf(featured) + 1;
        if (index >= order.size()) {
            highlight("");
            overview(true);
            schedule(1650);
            return;
        }
        String target = order.get(index);
        highlight(target);
        LatLng position = nearby.get(target);
        float bearing = index % 2 == 0 ? 20f : 338f;
        CameraPosition crossing = new CameraPosition.Builder().target(position).zoom(14.7f)
                .tilt(29f).bearing(bearing).build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(crossing),740,new GoogleMap.CancelableCallback() {
            @Override public void onFinish() {
                if (!resumed || closed || stopped || !featured.equals(target)) return;
                LatLng latest = nearby.get(target);
                if (latest == null) return;
                CameraPosition closeUp = new CameraPosition.Builder(crossing).target(latest)
                        .zoom(DRIVER_ZOOM).tilt(51f).build();
                move(CameraUpdateFactory.newCameraPosition(closeUp),880);
            }
            @Override public void onCancel() { }
        });
        schedule(SHOT_INTERVAL_MS);
    }

    private void highlight(String key) {
        Marker previous = markers.get(featured);
        if (previous != null) {
            previous.setIcon(regularIcon);
            previous.setZIndex(2);
        }
        featured = key;
        Marker next = markers.get(key);
        if (next != null) {
            next.setIcon(focusIcon);
            next.setZIndex(3);
        }
    }

    private void overview(boolean animated) {
        LatLng center = pickup != null ? pickup : nearby.isEmpty() ? null : nearby.values().iterator().next();
        if (map == null || center == null) return;
        showMapIfAvailable();
        CameraUpdate update = CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(center).zoom(OVERVIEW_ZOOM).tilt(motionEnabled() ? 25f : 0).build());
        int size = Math.min(getWidth(),getHeight());
        if (!nearby.isEmpty() && size > dp(120)) {
            LatLngBounds.Builder bounds = LatLngBounds.builder().include(center);
            for (LatLng point : nearby.values()) bounds.include(point);
            LatLngBounds area = bounds.build();
            // For a very small cluster keep an aerial establishing shot.
            if (CustomerGeoMath.distanceKm(area.southwest.latitude, area.southwest.longitude,
                    area.northeast.latitude,area.northeast.longitude) > 1.5) {
                update = CameraUpdateFactory.newLatLngBounds(area,getWidth(),getHeight(),dp(48));
            }
        }
        if (animated) move(update, 950);
        else map.moveCamera(update);
    }

    private void move(CameraUpdate update, int duration) {
        if (motionEnabled()) map.animateCamera(update,duration,null);
        else map.moveCamera(update);
    }

    private boolean motionEnabled() {
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,1f) > 0;
    }

    void startMap() {
        if (!started && !closed) {
            started = true;
            mapView.onStart();
        }
    }

    void resumeMap() {
        if (resumed || closed) return;
        resumed = true;
        mapView.onResume();
        if (accepted) renderAccepted();
        else if (loaded && !stopped) {
            pendingOverview = true;
            schedule(0);
        }
    }

    void pauseMap() {
        if (!resumed) return;
        resumed = false;
        main.removeCallbacks(nextShot);
        shotScheduled = false;
        if (map != null) map.stopAnimation();
        mapView.onPause();
    }

    void stopMap() {
        if (!started) return;
        started = false;
        mapView.onStop();
    }

    void stopScanning() {
        stopped = true;
        main.removeCallbacks(nextShot);
        shotScheduled = false;
        if (map != null) map.stopAnimation();
    }

    void resumeScanning() {
        if (accepted || closed) return;
        stopped = false;
        pendingOverview = true;
        schedule(0);
    }

    void lowMemory() {
        if (!closed) mapView.onLowMemory();
    }

    void close() {
        if (closed) return;
        stopScanning();
        pauseMap();
        stopMap();
        closed = true;
        mapView.onDestroy();
        map = null;
    }

    @Override public void onCustomerThemeChanged(boolean dark) {
        if (map != null && (lastDark == null || lastDark != dark)) applyStyle(dark);
    }

    private Boolean lastDark;
    private void applyStyle(boolean dark) {
        map.setMapStyle(dark ? MapStyleOptions.loadRawResourceStyle(getContext(),R.raw.google_map_dark) : null);
        lastDark = dark;
    }

    private BitmapDescriptor vehicleIcon(int size, int border) {
        int pixels = dp(size);
        Bitmap art = Bitmap.createBitmap(pixels,pixels,Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(art);
        float center = pixels/2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColor((border & 0x00FFFFFF) | 0x58000000);
        canvas.drawCircle(center,center,center,paint);
        paint.setColor(border);
        canvas.drawCircle(center,center,center-dp(3),paint);
        paint.setColor(0xFF0B2B47);
        canvas.drawCircle(center,center,center-dp(5),paint);
        Bitmap vehicle = BitmapFactory.decodeResource(getResources(), car ? R.drawable.map_car_top : R.drawable.map_motor_top);
        if (vehicle != null) {
            float box = center * 1.38f;
            float factor = box / Math.max(vehicle.getWidth(), vehicle.getHeight());
            float halfWidth = vehicle.getWidth()*factor/2, halfHeight = vehicle.getHeight()*factor/2;
            canvas.drawBitmap(vehicle,null,new RectF(center-halfWidth,center-halfHeight,center+halfWidth,center+halfHeight),paint);
            vehicle.recycle();
        }
        return BitmapDescriptorFactory.fromBitmap(art);
    }

    private TextView caption(float size, int color) {
        TextView text = new TextView(getContext());
        text.setTextSize(size);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setTextColor(color);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private GradientDrawable panel(int color, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setStroke(dp(1),stroke);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private int dp(int value) {
        return CustomerUiPrimitives.dp(getContext(),value);
    }
}
