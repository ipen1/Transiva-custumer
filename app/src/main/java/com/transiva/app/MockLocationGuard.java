package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocks the customer app only when Android provides definite evidence that a
 * location came from a mock provider. This avoids false positives on devices
 * that merely have Developer options enabled.
 */
public final class MockLocationGuard {
    private static final AtomicBoolean BLOCKING = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long LISTEN_TIMEOUT_MS = 8_000L;

    private MockLocationGuard() {
    }

    public static boolean protect(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;

        if (legacyMockSettingEnabled(activity) || hasMockLastKnownLocation(activity)) {
            block(activity);
            return true;
        }

        listenForMockLocation(activity);
        return false;
    }

    public static boolean isMock(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock();
        }
        return location.isFromMockProvider();
    }

    private static boolean legacyMockSettingEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return false;
        try {
            String value = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ALLOW_MOCK_LOCATION
            );
            return "1".equals(value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasMockLastKnownLocation(Context context) {
        if (!hasLocationPermission(context)) return false;
        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return false;

            List<String> providers = manager.getAllProviders();
            if (providers == null) return false;

            for (String provider : providers) {
                try {
                    if (isMock(manager.getLastKnownLocation(provider))) return true;
                } catch (SecurityException ignored) {
                    return false;
                } catch (Exception ignored) {
                    // Continue checking other providers.
                }
            }
        } catch (Exception ignored) {
            // Fail open when Android cannot expose location state.
        }
        return false;
    }

    private static void listenForMockLocation(Activity activity) {
        if (!hasLocationPermission(activity) || BLOCKING.get()) return;

        final LocationManager manager =
                (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return;

        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isMock(location)) {
                    removeSafely(manager, this);
                    block(activity);
                }
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };

        boolean registered = false;
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
                registered = true;
            }
        } catch (Exception ignored) { }

        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
                registered = true;
            }
        } catch (Exception ignored) { }

        try {
            manager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
            registered = true;
        } catch (Exception ignored) { }

        if (registered) {
            MAIN.postDelayed(() -> removeSafely(manager, listener), LISTEN_TIMEOUT_MS);
        }
    }

    private static boolean hasLocationPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void removeSafely(LocationManager manager, LocationListener listener) {
        try {
            manager.removeUpdates(listener);
        } catch (Exception ignored) { }
    }

    private static void block(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!BLOCKING.compareAndSet(false, true)) return;

        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                        .setTitle("Lokasi palsu terdeteksi")
                        .setMessage("Aplikasi tidak dapat digunakan karena perangkat mengirim lokasi palsu (mock location). Matikan aplikasi lokasi palsu, nonaktifkan aplikasi lokasi tiruan di Opsi Developer, lalu buka kembali Transiva.")
                        .setCancelable(false)
                        .setPositiveButton("Tutup aplikasi", (dialog, which) -> {
                            dialog.dismiss();
                            activity.finishAffinity();
                        })
                        .setOnDismissListener(dialog -> {
                            if (!activity.isFinishing()) activity.finishAffinity();
                        })
                        .show();
            } catch (Exception ignored) {
                activity.finishAffinity();
            }
        });
    }
}
