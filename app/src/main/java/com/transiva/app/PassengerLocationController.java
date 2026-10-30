package com.transiva.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/** Foreground-only transport startup: recent preview first, fresh pickup second. */
final class PassengerLocationController {
    interface Callback {
        void onLocation(Location location, boolean current);
        void onUnavailable(String message);
    }

    private final Context context;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LocationManager manager;
    private com.google.android.gms.location.FusedLocationProviderClient fused;
    private LocationCallback fusedCallback;
    private LocationListener nativeListener;
    private boolean running;
    private int generation;
    private Location best;
    private boolean deliveredCurrent;

    PassengerLocationController(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    void start() {
        if (running) return;
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) return;
        if (!enabled(LocationManager.GPS_PROVIDER) && !enabled(LocationManager.NETWORK_PROVIDER)) {
            callback.onUnavailable("Aktifkan lokasi HP agar titik jemput ditemukan.");
            return;
        }
        running = true;
        best = null;
        deliveredCurrent = false;
        final int ticket = ++generation;
        // System cache is synchronous: no need to wait for tiles or a new GPS fix.
        for (String provider : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try { if (manager != null) accept(manager.getLastKnownLocation(provider), ticket); }
            catch (Exception ignored) { }
        }
        nativeListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { accept(location, ticket); }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (enabled(provider)) manager.requestLocationUpdates(provider, 1000L, 0f,
                        nativeListener, Looper.getMainLooper());
            } catch (Exception ignored) { }
        }
        try {
            fused = LocationServices.getFusedLocationProviderClient(context);
            fused.getLastLocation().addOnSuccessListener(location -> accept(location, ticket));
            final LocationCallback requestCallback = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    for (Location location : result.getLocations()) accept(location, ticket);
                }
            };
            fusedCallback = requestCallback;
            LocationRequest request = new LocationRequest.Builder(
                    fine ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    1000L).setMinUpdateIntervalMillis(500L).setMaxUpdateAgeMillis(15000L)
                    .setWaitForAccurateLocation(false).setDurationMillis(12000L).build();
            fused.requestLocationUpdates(request, requestCallback, Looper.getMainLooper())
                    .addOnSuccessListener(unused -> {
                        // A pending registration may finish after the Activity has paused.
                        if (!running || generation != ticket) {
                            try { fused.removeLocationUpdates(requestCallback); } catch (Exception ignored) { }
                        }
                    });
        } catch (Exception ignored) { /* Native GPS/network listeners remain available. */ }
        handler.postDelayed(() -> {
            if (!running || ticket != generation) return;
            boolean found = deliveredCurrent;
            stop();
            if (!found) callback.onUnavailable(fine
                    ? "Lokasi belum akurat. Ketuk GPS untuk mencoba lagi atau pilih jemput di peta."
                    : "Lokasi masih perkiraan. Aktifkan lokasi presisi atau pilih jemput di peta.");
        }, 12000L);
    }

    private boolean enabled(String provider) {
        try { return manager != null && manager.isProviderEnabled(provider); }
        catch (Exception ignored) { return false; }
    }

    private void accept(Location location, int ticket) {
        if (!running || ticket != generation || location == null || location.isFromMockProvider()
                || !location.hasAccuracy() || !Float.isFinite(location.getAccuracy())
                || location.getAccuracy() < 0 || location.getAccuracy() > 300f
                || !CustomerGeoMath.valid(location.getLatitude(), location.getLongitude())) return;
        long age = Math.max(0L, (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1000000L);
        if (age > 120000L) return;
        boolean current = age <= 15000L && location.getAccuracy() <= 100f;
        if (deliveredCurrent) return; // Keep the established pickup steady.
        if (!current && best != null && location.getAccuracy() >= best.getAccuracy()
                && location.getElapsedRealtimeNanos() <= best.getElapsedRealtimeNanos()) return;
        best = new Location(location);
        deliveredCurrent = current;
        callback.onLocation(best, current);
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (manager != null && nativeListener != null) {
            try { manager.removeUpdates(nativeListener); } catch (Exception ignored) { }
        }
        if (fused != null && fusedCallback != null) {
            try { fused.removeLocationUpdates(fusedCallback); } catch (Exception ignored) { }
        }
        nativeListener = null;
        fusedCallback = null;
    }
}
