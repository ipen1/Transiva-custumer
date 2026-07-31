package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stable mock-location protection.
 *
 * Checks two independent signals:
 * 1. An installed application currently has Android's MOCK_LOCATION app-op.
 *    This catches Fake GPS even before the fake-location app is started.
 * 2. A fresh Location object is explicitly marked as mock by Android.
 *
 * No blocked state is persisted. Every resume/check reads Android again, so a
 * previously selected fake-location app does not remain "stuck" after it is
 * disabled in Developer options.
 */
public final class MockLocationGuard {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean CHECK_RUNNING = new AtomicBoolean(false);
    private static final long LISTEN_TIMEOUT_MS = 6_000L;
    private static final long MAX_LAST_LOCATION_AGE_MS = 12_000L;

    private static WeakReference<AlertDialog> activeDialog = new WeakReference<>(null);
    private static WeakReference<Activity> activeActivity = new WeakReference<>(null);

    private MockLocationGuard() { }

    public static boolean protect(Activity activity) {
        if (!isUsable(activity)) return false;

        // Never trust an old static flag. Re-read the real Android state.
        Detection result = detectNow(activity);
        if (result.blocked) {
            showBlockingDialog(activity, result.message);
            return true;
        }

        dismissBlockingDialog();
        listenForFreshMockLocation(activity);
        return false;
    }

    /** Runs a fresh check after returning from Developer options. */
    public static void recheck(Activity activity) {
        if (!isUsable(activity) || !CHECK_RUNNING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            try {
                Detection result = detectNow(activity);
                if (result.blocked) {
                    showBlockingDialog(activity, result.message);
                } else {
                    dismissBlockingDialog();
                }
            } finally {
                CHECK_RUNNING.set(false);
            }
        });
    }

    public static boolean isMock(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock();
        }
        return location.isFromMockProvider();
    }

    private static Detection detectNow(Context context) {
        String packageName = findEnabledMockLocationApp(context);
        if (packageName != null) {
            String label = appLabel(context, packageName);
            return Detection.blocked(
                    "Aplikasi lokasi palsu masih dipilih di Opsi Developer"
                            + (label.isEmpty() ? "." : ": " + label + ".")
                            + "\n\nNonaktifkan pilihan aplikasi lokasi palsu agar Transiva dapat digunakan."
            );
        }

        // Legacy Android only. Modern Android is handled with AppOps above.
        if (legacyMockSettingEnabled(context)) {
            return Detection.blocked("Fitur lokasi palsu masih aktif pada perangkat.");
        }

        // Only inspect recent cached positions. Old mock cache is deliberately
        // ignored to prevent a false lock after Fake GPS has been disabled.
        if (hasRecentMockLastKnownLocation(context)) {
            return Detection.blocked(
                    "Perangkat baru saja mengirim lokasi palsu. Tunggu GPS memperoleh lokasi asli, lalu tekan Periksa Lagi."
            );
        }

        return Detection.allowed();
    }

    /**
     * Returns the package currently granted OPSTR_MOCK_LOCATION, or null.
     * QUERY_ALL_PACKAGES in the manifest is used so Android 11+ does not hide
     * the selected third-party application from this check.
     */
    private static String findEnabledMockLocationApp(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;

        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        PackageManager pm = context.getPackageManager();
        if (appOps == null || pm == null) return null;

        try {
            List<ApplicationInfo> apps;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
            } else {
                //noinspection deprecation
                apps = pm.getInstalledApplications(0);
            }

            for (ApplicationInfo info : apps) {
                if (info == null || context.getPackageName().equals(info.packageName)) continue;

                int mode;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mode = appOps.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_MOCK_LOCATION,
                            info.uid,
                            info.packageName
                    );
                } else {
                    mode = appOps.checkOpNoThrow(
                            AppOpsManager.OPSTR_MOCK_LOCATION,
                            info.uid,
                            info.packageName
                    );
                }

                if (mode == AppOpsManager.MODE_ALLOWED) {
                    return info.packageName;
                }
            }
        } catch (SecurityException ignored) {
            // Some OEM ROMs restrict AppOps inspection. The location-object
            // check below remains active as the fallback.
        } catch (Throwable ignored) {
            // Do not crash the app on vendor-specific PackageManager behavior.
        }
        return null;
    }

    private static String appLabel(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0));
            } else {
                //noinspection deprecation
                info = pm.getApplicationInfo(packageName, 0);
            }
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? "" : label.toString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean legacyMockSettingEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return false;
        try {
            String value = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ALLOW_MOCK_LOCATION
            );
            return "1".equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasRecentMockLastKnownLocation(Context context) {
        if (!hasLocationPermission(context)) return false;
        try {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return false;
            List<String> providers = manager.getAllProviders();
            if (providers == null) return false;

            long now = System.currentTimeMillis();
            for (String provider : providers) {
                try {
                    Location location = manager.getLastKnownLocation(provider);
                    if (location == null || !isMock(location)) continue;
                    long age = Math.abs(now - location.getTime());
                    if (location.getTime() > 0 && age <= MAX_LAST_LOCATION_AGE_MS) return true;
                } catch (SecurityException ignored) {
                    return false;
                } catch (Throwable ignored) {
                    // Continue checking other providers.
                }
            }
        } catch (Throwable ignored) {
            // Fail open if vendor ROM does not expose provider state.
        }
        return false;
    }

    private static void listenForFreshMockLocation(Activity activity) {
        if (!hasLocationPermission(activity)) return;
        final LocationManager manager =
                (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return;

        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isMock(location)) {
                    removeSafely(manager, this);
                    showBlockingDialog(activity,
                            "Android mendeteksi lokasi yang dikirim sebagai lokasi palsu. Matikan Fake GPS dan hapus pilihan aplikasi lokasi palsu di Opsi Developer.");
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
        } catch (Throwable ignored) { }
        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
                registered = true;
            }
        } catch (Throwable ignored) { }
        try {
            manager.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
            registered = true;
        } catch (Throwable ignored) { }

        if (registered) {
            MAIN.postDelayed(() -> removeSafely(manager, listener), LISTEN_TIMEOUT_MS);
        }
    }

    private static void showBlockingDialog(Activity activity, String reason) {
        if (!isUsable(activity)) return;

        AlertDialog existing = activeDialog.get();
        Activity owner = activeActivity.get();
        if (existing != null && existing.isShowing() && owner == activity) return;
        dismissBlockingDialog();

        activity.runOnUiThread(() -> {
            if (!isUsable(activity)) return;
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("Lokasi palsu terdeteksi")
                        .setMessage(reason)
                        .setCancelable(false)
                        .setPositiveButton("Buka Opsi Developer", null)
                        .setNegativeButton("Tutup aplikasi", (d, which) -> activity.finishAffinity())
                        .setNeutralButton("Periksa Lagi", null)
                        .create();

                dialog.setOnShowListener(d -> {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v ->
                            openDeveloperSettings(activity));
                    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v ->
                            recheck(activity));
                });
                dialog.setOnDismissListener(d -> {
                    if (activeDialog.get() == dialog) {
                        activeDialog.clear();
                        activeActivity.clear();
                    }
                });
                activeDialog = new WeakReference<>(dialog);
                activeActivity = new WeakReference<>(activity);
                dialog.show();
            } catch (Throwable ignored) {
                activity.finishAffinity();
            }
        });
    }

    private static void openDeveloperSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            activity.startActivity(intent);
        } catch (Throwable first) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                activity.startActivity(intent);
            } catch (Throwable ignored) { }
        }
    }

    private static void dismissBlockingDialog() {
        MAIN.post(() -> {
            AlertDialog dialog = activeDialog.get();
            if (dialog != null && dialog.isShowing()) {
                try { dialog.dismiss(); } catch (Throwable ignored) { }
            }
            activeDialog.clear();
            activeActivity.clear();
        });
    }

    private static boolean hasLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    private static void removeSafely(LocationManager manager, LocationListener listener) {
        try { manager.removeUpdates(listener); } catch (Throwable ignored) { }
    }

    private static final class Detection {
        final boolean blocked;
        final String message;

        private Detection(boolean blocked, String message) {
            this.blocked = blocked;
            this.message = message;
        }

        static Detection blocked(String message) { return new Detection(true, message); }
        static Detection allowed() { return new Detection(false, ""); }
    }
}
