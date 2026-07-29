package com.transiva.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Native Google Maps renderer shared by TransRide, TransCar and CustomerTrip.
 * Routing remains on Transiva's existing OSRM engine (StableRouteEngine).
 */
public final class TransivaGoogleMapView extends FrameLayout implements OnMapReadyCallback {

    public interface Listener {
        void onReady(double lat, double lng);
        void onCenterChanged(double lat, double lng);
    }

    public enum Mode { PICKER, TRIP }

    private final MapView nativeMapView;
    private final Mode mode;
    private final List<Marker> placeMarkers = new ArrayList<>();
    private final List<Marker> driverMarkers = new ArrayList<>();
    private final ImageView centerPin;

    private GoogleMap googleMap;
    private Listener listener;
    private Marker pickupMarker;
    private Marker deliveryMarker;
    private Marker tripDriverMarker;
    private Polyline routePolyline;
    private boolean ready;
    private String selectionMode = "pickup";
    private ValueAnimator driverAnimator;
    private final Map<String, BitmapDescriptor> iconCache = new HashMap<>();

    private static final LatLng DEFAULT_CENTER = new LatLng(-0.018137, 120.087380);

    public TransivaGoogleMapView(Context context, Mode mode) {
        super(context);
        this.mode = mode;
        setClipToOutline(true);

        try { MapsInitializer.initialize(context.getApplicationContext()); } catch (Exception ignored) {}

        nativeMapView = new MapView(context);
        addView(nativeMapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (mode == Mode.PICKER) {
            centerPin = new ImageView(context);
            centerPin.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            centerPin.setImageResource(R.drawable.map_pickup_pin);
            int w = dp(42), h = dp(54);
            LayoutParams lp = new LayoutParams(w, h);
            lp.gravity = Gravity.CENTER;
            // Pin tip points to the exact camera center.
            lp.bottomMargin = h / 2;
            addView(centerPin, lp);
        } else {
            centerPin = null;
        }
    }

    public void initialize(Bundle state, Listener listener) {
        this.listener = listener;
        nativeMapView.onCreate(state);
        nativeMapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        ready = true;

        try {
            map.getUiSettings().setMapToolbarEnabled(false);
            map.getUiSettings().setCompassEnabled(false);
            map.getUiSettings().setRotateGesturesEnabled(false);
            map.getUiSettings().setTiltGesturesEnabled(false);
            map.getUiSettings().setZoomControlsEnabled(false);
            map.setBuildingsEnabled(true);
            map.setIndoorEnabled(false);
            map.setTrafficEnabled(false);
        } catch (Exception ignored) {}

        try {
            if (CustomerAppSettings.isDarkMode(getContext())) {
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.google_map_dark));
            }
        } catch (Exception ignored) {}

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, mode == Mode.PICKER ? 17f : 15f));

        if (mode == Mode.PICKER) {
            map.setOnCameraIdleListener(() -> {
                if (googleMap == null) return;
                LatLng c = googleMap.getCameraPosition().target;
                if (listener != null && valid(c.latitude, c.longitude)) {
                    listener.onCenterChanged(c.latitude, c.longitude);
                }
            });
        }

        LatLng c = map.getCameraPosition().target;
        if (listener != null) listener.onReady(c.latitude, c.longitude);
    }

    public boolean isReady() { return ready && googleMap != null; }

    public void setSelectionMode(String mode) {
        selectionMode = "delivery".equals(mode) ? "delivery" : "pickup";
        if (centerPin != null) {
            centerPin.setImageResource("delivery".equals(selectionMode)
                    ? R.drawable.map_destination_pin : R.drawable.map_pickup_pin);
        }
    }

    public LatLng getCenter() {
        if (googleMap == null) return DEFAULT_CENTER;
        return googleMap.getCameraPosition().target;
    }

    public void moveTo(double lat, double lng, float zoom) {
        if (!isReady() || !valid(lat, lng)) return;
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom));
    }

    public void setPickup(double lat, double lng, String label) {
        if (!isReady() || !valid(lat, lng)) return;
        LatLng p = new LatLng(lat, lng);
        if (pickupMarker == null) {
            pickupMarker = googleMap.addMarker(new MarkerOptions()
                    .position(p)
                    .anchor(0.5f, 1f)
                    .icon(icon(R.drawable.map_pickup_pin, 42, 54))
                    .title("Lokasi Jemput"));
        } else pickupMarker.setPosition(p);
        if (pickupMarker != null) pickupMarker.setSnippet(label == null ? "" : label);
    }

    public void setDelivery(double lat, double lng, String label) {
        if (!isReady() || !valid(lat, lng)) return;
        LatLng p = new LatLng(lat, lng);
        if (deliveryMarker == null) {
            deliveryMarker = googleMap.addMarker(new MarkerOptions()
                    .position(p)
                    .anchor(0.5f, 1f)
                    .icon(icon(R.drawable.map_destination_pin, 42, 54))
                    .title("Lokasi Tujuan"));
        } else deliveryMarker.setPosition(p);
        if (deliveryMarker != null) deliveryMarker.setSnippet(label == null ? "" : label);
    }

    public void clearPlaces() {
        for (Marker m : placeMarkers) try { m.remove(); } catch (Exception ignored) {}
        placeMarkers.clear();
    }

    public void addPlace(double lat, double lng, String name, String type, String address) {
        if (!isReady() || !valid(lat, lng)) return;
        Marker m = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .anchor(0.5f, 1f)
                .icon(icon(R.drawable.ic_location_pin, 34, 34))
                .title(empty(name) ? "Lokasi" : name)
                .snippet(join(type, address)));
        if (m != null) placeMarkers.add(m);
    }

    public void clearOnlineDrivers() {
        for (Marker m : driverMarkers) try { m.remove(); } catch (Exception ignored) {}
        driverMarkers.clear();
    }

    public void addOnlineDriver(double lat, double lng, String name, boolean car) {
        if (!isReady() || !valid(lat, lng)) return;
        Marker m = googleMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .anchor(0.5f, 0.5f)
                .flat(true)
                .icon(icon(car ? R.drawable.map_car_top : R.drawable.map_motor_top, 46, 46))
                .title(empty(name) ? "Driver" : name)
                .snippet("Driver online"));
        if (m != null) driverMarkers.add(m);
    }

    public void setTripDriver(double lat, double lng, double bearing, boolean car, String name, String status) {
        if (!isReady() || !valid(lat, lng)) return;
        LatLng target = new LatLng(lat, lng);
        if (tripDriverMarker == null) {
            tripDriverMarker = googleMap.addMarker(new MarkerOptions()
                    .position(target)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation((float) normalize(bearing))
                    .icon(icon(car ? R.drawable.map_car_top : R.drawable.map_motor_top, 46, 46))
                    .title(empty(name) ? "Driver" : name)
                    .snippet(status == null ? "" : status));
            return;
        }
        tripDriverMarker.setIcon(icon(car ? R.drawable.map_car_top : R.drawable.map_motor_top, 46, 46));
        tripDriverMarker.setTitle(empty(name) ? "Driver" : name);
        tripDriverMarker.setSnippet(status == null ? "" : status);
        animateDriver(tripDriverMarker.getPosition(), target, bearing);
    }

    private void animateDriver(LatLng start, LatLng end, double bearing) {
        if (tripDriverMarker == null) return;
        if (driverAnimator != null) driverAnimator.cancel();
        driverAnimator = ValueAnimator.ofFloat(0f, 1f);
        driverAnimator.setDuration(900L);
        driverAnimator.addUpdateListener(a -> {
            if (tripDriverMarker == null) return;
            float f = (float) a.getAnimatedValue();
            double lat = start.latitude + (end.latitude - start.latitude) * f;
            double lng = start.longitude + (end.longitude - start.longitude) * f;
            tripDriverMarker.setPosition(new LatLng(lat, lng));
            tripDriverMarker.setRotation((float) normalize(bearing));
        });
        driverAnimator.start();
    }

    public void drawOsrmRoute(JSONArray latLngPoints, String status) {
        if (!isReady() || latLngPoints == null || latLngPoints.length() < 2) return;
        List<LatLng> points = new ArrayList<>();
        for (int i = 0; i < latLngPoints.length(); i++) {
            JSONArray p = latLngPoints.optJSONArray(i);
            if (p == null || p.length() < 2) continue;
            double lat = p.optDouble(0, Double.NaN);
            double lng = p.optDouble(1, Double.NaN);
            if (valid(lat, lng)) points.add(new LatLng(lat, lng));
        }
        if (points.size() < 2) return;
        if (routePolyline != null) routePolyline.remove();
        int color = "on_delivery".equals(status) ? Color.rgb(22, 163, 74)
                : ("arrived_pickup".equals(status) ? Color.rgb(245, 158, 11) : Color.rgb(37, 99, 235));
        routePolyline = googleMap.addPolyline(new PolylineOptions()
                .addAll(points)
                .width(dpF(5.5f))
                .color(color)
                .geodesic(false)
                .zIndex(2f));
    }

    public void drawRideRoute(JSONArray latLngPoints) {
        drawOsrmRoute(latLngPoints, "driver_accepted");
        fitPickupDelivery();
    }

    public void fitAll() {
        if (!isReady()) return;
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        int n = 0;
        if (pickupMarker != null) { b.include(pickupMarker.getPosition()); n++; }
        if (deliveryMarker != null) { b.include(deliveryMarker.getPosition()); n++; }
        if (tripDriverMarker != null) { b.include(tripDriverMarker.getPosition()); n++; }
        if (n == 0) return;
        if (n == 1) {
            LatLng p = tripDriverMarker != null ? tripDriverMarker.getPosition()
                    : pickupMarker != null ? pickupMarker.getPosition() : deliveryMarker.getPosition();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 16f));
            return;
        }
        try { googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(58))); }
        catch (Exception ignored) {}
    }

    public void fitPickupDelivery() {
        if (!isReady() || pickupMarker == null || deliveryMarker == null) return;
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(pickupMarker.getPosition())
                .include(deliveryMarker.getPosition())
                .build();
        try { googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(64))); }
        catch (Exception ignored) {}
    }

    public void onStartMap() { try { nativeMapView.onStart(); } catch (Exception ignored) {} }
    public void onResumeMap() { try { nativeMapView.onResume(); } catch (Exception ignored) {} }
    public void onPauseMap() { try { nativeMapView.onPause(); } catch (Exception ignored) {} }
    public void onStopMap() { try { nativeMapView.onStop(); } catch (Exception ignored) {} }
    public void onDestroyMap() {
        try { if (driverAnimator != null) driverAnimator.cancel(); } catch (Exception ignored) {}
        try { nativeMapView.onDestroy(); } catch (Exception ignored) {}
        googleMap = null;
        ready = false;
    }
    public void onLowMemoryMap() { try { nativeMapView.onLowMemory(); } catch (Exception ignored) {} }
    public void onSaveInstanceStateMap(Bundle out) { try { nativeMapView.onSaveInstanceState(out); } catch (Exception ignored) {} }

    private BitmapDescriptor icon(int resId, int widthDp, int heightDp) {
        String key = resId + ":" + widthDp + "x" + heightDp;
        BitmapDescriptor cached = iconCache.get(key);
        if (cached != null) return cached;
        try {
            Bitmap raw = BitmapFactory.decodeResource(getResources(), resId);
            if (raw == null) return BitmapDescriptorFactory.defaultMarker();
            Bitmap scaled = Bitmap.createScaledBitmap(raw, dp(widthDp), dp(heightDp), true);
            BitmapDescriptor descriptor = BitmapDescriptorFactory.fromBitmap(scaled);
            iconCache.put(key, descriptor);
            if (scaled != raw) try { raw.recycle(); } catch (Exception ignored) {}
            return descriptor;
        } catch (Exception e) {
            return BitmapDescriptorFactory.defaultMarker();
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private float dpF(float v) { return v * getResources().getDisplayMetrics().density; }
    private static boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 && !(lat == 0 && lng == 0);
    }
    private static double normalize(double b) { b %= 360d; return b < 0 ? b + 360d : b; }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
    private static String join(String a, String b) {
        if (empty(a)) return empty(b) ? "" : b;
        if (empty(b)) return a;
        return a + " • " + b;
    }
}
