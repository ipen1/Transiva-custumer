package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Conservative root/hooking detector. Heavy checks run outside the UI thread. */
public final class RootSecurityGuard {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static WeakReference<AlertDialog> dialogRef = new WeakReference<>(null);

    private static final String[] ROOT_PACKAGES = {
            "com.topjohnwu.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu",
            "com.rifsxd.ksunext", "me.bmax.apatch", "org.lsposed.manager",
            "de.robv.android.xposed.installer", "com.saurik.substrate", "com.devadvance.rootcloak",
            "com.thirdparty.superuser", "eu.chainfire.supersu", "com.koushikdutta.superuser"
    };

    private static final String[] ROOT_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk", "/data/adb/magisk",
            "/data/adb/ksu", "/data/adb/ap", "/data/adb/modules", "/cache/su"
    };

    private RootSecurityGuard() { }

    public static void checkBeforeContinue(Activity activity, Runnable onAllowed) {
        check(activity, onAllowed);
    }

    public static void protect(Activity activity) {
        check(activity, null);
    }

    private static void check(Activity activity, Runnable onAllowed) {
        if (!usable(activity)) return;
        if (!RUNNING.compareAndSet(false, true)) {
            if (onAllowed != null) MAIN.postDelayed(() -> check(activity, onAllowed), 300L);
            return;
        }
        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            Detection result;
            try { result = detect(app); }
            catch (Throwable ignored) { result = Detection.clean(); }
            Detection finalResult = result;
            MAIN.post(() -> {
                RUNNING.set(false);
                if (!usable(activity)) return;
                if (finalResult.blocked) showBlocked(activity, finalResult.reason);
                else {
                    dismiss();
                    if (onAllowed != null) onAllowed.run();
                }
            });
        });
    }

    private static Detection detect(Context context) {
        if (hasKnownPackage(context)) return Detection.blocked("Aplikasi root atau hooking terdeteksi.");
        for (String path : ROOT_PATHS) if (new File(path).exists()) return Detection.blocked("Komponen root terdeteksi pada perangkat.");

        String tags = Build.TAGS == null ? "" : Build.TAGS.toLowerCase(Locale.US);
        if (tags.contains("test-keys")) return Detection.blocked("Sistem perangkat memakai build tidak resmi (test-keys).");

        String props = readCommand("getprop");
        if (props.contains("[ro.debuggable]: [1]") || props.contains("[ro.secure]: [0]")) {
            return Detection.blocked("Konfigurasi sistem tidak aman terdeteksi.");
        }
        String whichSu = readCommand("sh", "-c", "command -v su 2>/dev/null").trim();
        if (!whichSu.isEmpty()) return Detection.blocked("Akses superuser terdeteksi.");

        String maps = readFile("/proc/self/maps").toLowerCase(Locale.US);
        List<String> hooks = Arrays.asList("xposed", "lsposed", "zygisk", "frida", "substrate");
        for (String hook : hooks) if (maps.contains(hook)) return Detection.blocked("Framework modifikasi aplikasi terdeteksi: " + hook + ".");
        return Detection.clean();
    }

    private static boolean hasKnownPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String name : ROOT_PACKAGES) {
            try { pm.getPackageInfo(name, 0); return true; }
            catch (PackageManager.NameNotFoundException ignored) { }
            catch (Throwable ignored) { }
        }
        return false;
    }

    private static String readCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line; int count = 0;
                while ((line = reader.readLine()) != null && count++ < 400) out.append(line).append('\n');
            }
            process.waitFor();
            return out.toString();
        } catch (Throwable ignored) { return ""; }
        finally { if (process != null) process.destroy(); }
    }

    private static String readFile(String path) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(path)))) {
            StringBuilder out = new StringBuilder(); String line; int count = 0;
            while ((line = reader.readLine()) != null && count++ < 3000) out.append(line).append('\n');
            return out.toString();
        } catch (Throwable ignored) { return ""; }
    }

    private static void showBlocked(Activity activity, String reason) {
        AlertDialog existing = dialogRef.get();
        if (existing != null && existing.isShowing()) return;
        try {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Perangkat tidak aman")
                    .setMessage(reason + "\n\nDemi keamanan akun dan transaksi, Transiva tidak dapat dijalankan pada perangkat yang di-root atau dimodifikasi.")
                    .setCancelable(false)
                    .setPositiveButton("Buka pengaturan", (d, w) -> openSettings(activity))
                    .setNegativeButton("Tutup aplikasi", (d, w) -> activity.finishAffinity())
                    .create();
            dialog.setOnDismissListener(d -> dialogRef.clear());
            dialogRef = new WeakReference<>(dialog);
            dialog.show();
        } catch (Throwable ignored) {
            Toast.makeText(activity, "Perangkat root/modifikasi terdeteksi.", Toast.LENGTH_LONG).show();
            MAIN.postDelayed(activity::finishAffinity, 1500L);
        }
    }

    private static void openSettings(Activity activity) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        } catch (Throwable ignored) { }
    }

    private static void dismiss() {
        AlertDialog d = dialogRef.get();
        if (d != null && d.isShowing()) try { d.dismiss(); } catch (Throwable ignored) { }
        dialogRef.clear();
    }

    private static boolean usable(Activity a) {
        return a != null && !a.isFinishing() && (Build.VERSION.SDK_INT < 17 || !a.isDestroyed());
    }

    private static final class Detection {
        final boolean blocked; final String reason;
        private Detection(boolean blocked, String reason) { this.blocked = blocked; this.reason = reason; }
        static Detection blocked(String reason) { return new Detection(true, reason); }
        static Detection clean() { return new Detection(false, ""); }
    }
}
