package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MLBB-style resource snapshot updater for Transiva Customer.
 *
 * Important: this updates data/assets that the APK is coded to read through this
 * manager; it does not replace compiled Java/classes/native code. APK code updates
 * for the Play flavor remain delivered by Google Play.
 */
public final class CustomerResourceUpdateManager {
    private static final String ENDPOINT =
            "https://transiva.my.id/server/customer_resource_manifest.php";
    private static final String PREF = "transiva_customer_resources";
    private static final String K_VERSION = "resource_version";
    private static final String K_LAST_CHECK = "last_check";
    private static final long CHECK_INTERVAL_MS = 15L * 60L * 1000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private CustomerResourceUpdateManager() {}

    public static void checkInBackground(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long age = System.currentTimeMillis() - sp.getLong(K_LAST_CHECK, 0L);
        if (age >= 0 && age < CHECK_INTERVAL_MS) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        new Thread(() -> {
            try { checkAndInstall(app); }
            catch (Throwable ignored) { }
            finally { RUNNING.set(false); }
        }, "customer-resource-update").start();
    }

    public static int installedVersion(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(K_VERSION, 0);
    }

    /** Return a file from the currently installed snapshot, or null if absent/unsafe. */
    public static File file(Context context, String relativePath) {
        if (context == null || relativePath == null || relativePath.trim().isEmpty()) return null;
        String rel = relativePath.replace('\\', '/');
        if (rel.startsWith("/") || rel.contains("../")) return null;
        int v = installedVersion(context);
        if (v <= 0) return null;
        try {
            File base = versionDir(context, v).getCanonicalFile();
            File target = new File(base, rel).getCanonicalFile();
            if (!target.getPath().startsWith(base.getPath() + File.separator)) return null;
            return target.isFile() ? target : null;
        } catch (Exception ignored) { return null; }
    }

    private static void checkAndInstall(Context app) throws Exception {
        SharedPreferences sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int current = sp.getInt(K_VERSION, 0);
        int appVersion;
        try { appVersion = AppUpdateClient.installedVersionCode(app); }
        catch (Exception e) { appVersion = 0; }

        HttpURLConnection c = null;
        try {
            String u = ENDPOINT + "?resource_version=" + current
                    + "&app_version_code=" + appVersion
                    + "&_=" + System.currentTimeMillis();
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return;
            String body = read(c.getInputStream());
            JSONObject root = new JSONObject(body);
            if (!root.optBoolean("success", false)) return;
            JSONObject data = root.optJSONObject("data");
            if (data == null || !data.optBoolean("update_available", false)) {
                sp.edit().putLong(K_LAST_CHECK, System.currentTimeMillis()).apply();
                return;
            }
            int version = data.optInt("version", 0);
            int minApp = data.optInt("min_app_version_code", 0);
            String url = data.optString("url", "");
            String sha256 = data.optString("sha256", "").toLowerCase(Locale.US);
            long expectedSize = data.optLong("size", 0L);
            if (version <= current || version <= 0 || url.isEmpty() || sha256.length() != 64) return;
            if (minApp > 0 && appVersion > 0 && appVersion < minApp) return;
            if (!url.toLowerCase(Locale.US).startsWith("https://transiva.my.id/")) return;

            File rootDir = rootDir(app);
            if (!rootDir.exists() && !rootDir.mkdirs()) return;
            File zip = new File(rootDir, "resource-v" + version + ".download");
            download(url, zip, expectedSize);
            if (!sha256.equals(sha256(zip))) { zip.delete(); return; }

            File staging = new File(rootDir, ".staging-v" + version);
            deleteRecursive(staging);
            if (!staging.mkdirs()) { zip.delete(); return; }
            unzipSafe(zip, staging);
            zip.delete();

            File marker = new File(staging, "snapshot.json");
            if (!marker.isFile()) { deleteRecursive(staging); return; }
            JSONObject snapshot = new JSONObject(read(new FileInputStream(marker)));
            if (snapshot.optInt("version", -1) != version) { deleteRecursive(staging); return; }
            if (!"customer".equalsIgnoreCase(snapshot.optString("app", "customer"))) {
                deleteRecursive(staging); return;
            }

            File target = versionDir(app, version);
            deleteRecursive(target);
            if (!staging.renameTo(target)) { deleteRecursive(staging); return; }

            sp.edit().putInt(K_VERSION, version)
                    .putLong(K_LAST_CHECK, System.currentTimeMillis()).commit();
            cleanupOld(rootDir, version);
        } finally {
            sp.edit().putLong(K_LAST_CHECK, System.currentTimeMillis()).apply();
            if (c != null) c.disconnect();
        }
    }

    private static void download(String url, File out, long expectedSize) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setUseCaches(false);
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) throw new Exception("download");
            long declared = c.getContentLengthLong();
            if (expectedSize > 0 && declared > 0 && expectedSize != declared) throw new Exception("size");
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[32 * 1024]; int n; long total = 0;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > 100L * 1024L * 1024L) throw new Exception("resource pack too large");
                    fos.write(buf, 0, n);
                }
                fos.getFD().sync();
                if (expectedSize > 0 && total != expectedSize) throw new Exception("size");
            }
        } finally { if (c != null) c.disconnect(); }
    }

    private static void unzipSafe(File zip, File target) throws Exception {
        String base = target.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e; byte[] buf = new byte[32 * 1024];
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName() == null || e.getName().trim().isEmpty()) continue;
                File out = new File(target, e.getName()).getCanonicalFile();
                if (!out.getPath().startsWith(base)) throw new SecurityException("zip traversal");
                if (e.isDirectory()) { if (!out.exists() && !out.mkdirs()) throw new Exception("mkdir"); }
                else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("mkdir");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n; long entryTotal = 0;
                        while ((n = zis.read(buf)) > 0) {
                            entryTotal += n;
                            if (entryTotal > 50L * 1024L * 1024L) throw new Exception("entry too large");
                            fos.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[32 * 1024]; int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte v : md.digest()) sb.append(String.format(Locale.US, "%02x", v & 0xff));
        return sb.toString();
    }

    private static String read(InputStream in) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder s = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) s.append(line);
            return s.toString();
        }
    }

    private static File rootDir(Context c) { return new File(c.getFilesDir(), "transiva_resources/customer"); }
    private static File versionDir(Context c, int v) { return new File(rootDir(c), "v" + v); }
    private static void cleanupOld(File root, int keep) {
        File[] files = root.listFiles(); if (files == null) return;
        for (File f : files) if (f.isDirectory() && f.getName().startsWith("v") && !f.getName().equals("v" + keep)) deleteRecursive(f);
    }
    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] xs = f.listFiles(); if (xs != null) for (File x : xs) deleteRecursive(x); }
        try { f.delete(); } catch (Throwable ignored) { }
    }
}
