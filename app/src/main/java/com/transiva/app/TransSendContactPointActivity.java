package com.transiva.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;
import android.widget.LinearLayout;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;
import java.util.Locale;

public final class TransSendContactPointActivity extends CustomerAdaptiveActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CustomerLifecycleNetworkScope network = new CustomerLifecycleNetworkScope();
    private TransivaGoogleMapView map;
    private Button usePoint;
    private boolean moved;
    private boolean resolving;
    private boolean locating;
    private static final int LOCATION_PERMISSION=724;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(CustomerUiPrimitives.text(this, getIntent().getStringExtra("title"),
                20, "#103769", true));
        map = new TransivaGoogleMapView(this, TransivaGoogleMapView.Mode.PICKER);
        page.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));
        usePoint = new Button(this);
        usePoint.setAllCaps(false);
        usePoint.setText("Pakai Titik Ini");
        usePoint.setEnabled(false);
        usePoint.setOnClickListener(v -> confirm());
        if (getIntent().getBooleanExtra("tiva_location",false)) {
            Button current=new Button(this);
            current.setAllCaps(false);
            current.setText("Ambil lokasi");
            current.setOnClickListener(v->useCurrentPosition(true));
            page.addView(current);
        }
        page.addView(usePoint);
        setContentView(page);
        CustomerAppSettings.apply(this);

        Intent input = getIntent();
        double lat = saved == null ? input.getDoubleExtra("lat", 0) : saved.getDouble("lat");
        double lng = saved == null ? input.getDoubleExtra("lng", 0) : saved.getDouble("lng");
        if (valid(lat, lng)) map.centerOnInitialLocation(lat, lng, 17f);
        map.setGestureListener(new TransivaGoogleMapView.GestureListener() {
            public void onGestureStart() { moved = true; usePoint.setEnabled(false); }
            public void onGestureEnd() { usePoint.setEnabled(!resolving); }
        });
        map.initialize(null, new TransivaGoogleMapView.Listener() {
            public void onReady(double mapLat, double mapLng) {
                String kind=input.getStringExtra("point_kind");
                if (kind==null && "Atur Titik Antar".equals(input.getStringExtra("title"))) kind="delivery";
                if ("stop".equals(kind)) map.setWaypointSelectionMode(1);
                else map.setSelectionMode(kind);
                map.showCenterPin(true);
                map.hideCenterAction();
                usePoint.setEnabled(true);
                if (!valid(lat, lng)) useCurrentPosition(false);
            }
            public void onCenterChanged(double mapLat, double mapLng) {}
        });
    }

    private void useCurrentPosition(boolean explicit) {
        if (resolving || locating) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (explicit) requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            return;
        }
        locating=true;
        if (explicit) moved=false;
        try {
            TransivaFreshLocation.request(this, new TransivaFreshLocation.Callback() {
                public void onLocation(android.location.Location location, boolean fresh) {
                    locating=false;
                    if (!moved && !resolving && !isFinishing() && !isDestroyed())
                        map.centerOnInitialLocation(location.getLatitude(), location.getLongitude(), 17f);
                }
                public void onFailure(String message) {
                    locating=false;
                    if (explicit && !isFinishing() && !isDestroyed())
                        Toast.makeText(TransSendContactPointActivity.this,"Lokasi belum ditemukan. Geser peta untuk memilih titik.",Toast.LENGTH_SHORT).show();
                }
            });
        } catch (SecurityException ignored) { locating=false; }
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request==LOCATION_PERMISSION && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)
            useCurrentPosition(true);
    }

    private void confirm() {
        if (resolving) return;
        LatLng point = map.getCenter();
        if (!valid(point.latitude, point.longitude)) return;
        moved = true;
        resolving = true;
        usePoint.setEnabled(false);
        usePoint.setText("Menentukan Alamat…");
        main.postDelayed(() -> complete(point, String.format(Locale.US, "%.6f, %.6f",
                point.latitude, point.longitude)), 3000L);
        network.execute(() -> {
            String address = "";
            try {
                List<Address> found = new Geocoder(this, new Locale("id", "ID"))
                        .getFromLocation(point.latitude, point.longitude, 1);
                if (found != null && !found.isEmpty()) address = found.get(0).getAddressLine(0);
            } catch (Exception ignored) { }
            String label = CallPeerIdentity.first(address,
                    String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude));
            network.post(main, () -> complete(point, label));
        }, () -> network.post(main, () -> complete(point,
                String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude))));
    }

    private void complete(LatLng point, String label) {
        if (isFinishing()) return;
        setResult(RESULT_OK, new Intent().putExtra("lat", point.latitude)
                .putExtra("lng", point.longitude).putExtra("address", label));
        finish();
    }

    private static boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90
                && Math.abs(lng) <= 180 && lat != 0 && lng != 0;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (map.isReady()) {
            LatLng point = map.getCenter();
            out.putDouble("lat", point.latitude);
            out.putDouble("lng", point.longitude);
        }
    }
    @Override protected void onStart() { super.onStart(); map.onStartMap(); }
    @Override protected void onResume() { super.onResume(); map.onResumeMap(); }
    @Override protected void onPause() { map.onPauseMap(); super.onPause(); }
    @Override protected void onStop() { map.onStopMap(); super.onStop(); }
    @Override protected void onDestroy() { network.destroy(); main.removeCallbacksAndMessages(null); map.onDestroyMap(); super.onDestroy(); }
}
