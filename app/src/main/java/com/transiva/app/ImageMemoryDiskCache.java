package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.LruCache;
import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** Bounded render cache. No bitmap is recycled while a view may still own it. */
public final class ImageMemoryDiskCache {
    private static final int MEMORY_KB = (int) Math.max(4096L,
            Math.min(24576L, Runtime.getRuntime().maxMemory() / 1024L / 16L));
    private static final long DISK_BYTES = 48L * 1024L * 1024L;
    private static final long MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(MEMORY_KB) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return Math.max(1, bitmap.getByteCount() / 1024); }
    };
    private ImageMemoryDiskCache() { }
    public static Bitmap getMemory(String key) {
        if (key == null) return null;
        Bitmap b = MEMORY.get(key);
        return b != null && !b.isRecycled() ? b : null;
    }
    public static void clearMemory() { MEMORY.evictAll(); }
    public static void trimMemory() { MEMORY.trimToSize(MEMORY_KB / 2); }
    public static Bitmap getDisk(Context context, String key) {
        if (context == null || key == null) return null;
        Bitmap memory = getMemory(key); if (memory != null) return memory;
        File f = file(context, key);
        if (!f.isFile()) return null;
        long age = System.currentTimeMillis() - f.lastModified();
        if (age < 0L || age > MAX_AGE_MS || f.length() > 20L * 1024L * 1024L) { f.delete(); return null; }
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), options);
            options.inSampleSize = NetworkSafetyRules.bitmapSample(options.outWidth, options.outHeight, 2048, 4194304L);
            options.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
            if (b != null) MEMORY.put(key, b); else f.delete();
            return b;
        } catch (OutOfMemoryError lowMemory) { clearMemory(); return null; }
    }
    public static Bitmap get(Context c, String key) { Bitmap b = getMemory(key); return b != null ? b : getDisk(c, key); }
    public static void put(Context context, String key, Bitmap bitmap) {
        if (context == null || key == null || bitmap == null || bitmap.isRecycled()) return;
        MEMORY.put(key, bitmap);
        Context app = context.getApplicationContext();
        try {
            TransivaImageExecutor.execute(() -> {
                File destination = file(app, key); File temporary = null;
                try {
                    long age = System.currentTimeMillis() - destination.lastModified();
                    if (destination.isFile() && age >= 0 && age <= MAX_AGE_MS) return;
                    temporary = File.createTempFile("write-", ".tmp", destination.getParentFile());
                    boolean written;
                    try (FileOutputStream out = new FileOutputStream(temporary)) {
                        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
                        written = bitmap.compress(format, 86, out);
                    }
                    if (written) temporary.renameTo(destination);
                    trim(app);
                } catch (Exception ignored) {
                } finally { if (temporary != null) temporary.delete(); }
            });
        } catch (java.util.concurrent.RejectedExecutionException busy) { /* Memory cache remains usable. */ }
    }
    private static File file(Context c, String key) {
        File directory = new File(c.getCacheDir(), "img_v3");
        if (!directory.isDirectory()) directory.mkdirs();
        return new File(directory, sha(key) + ".webp");
    }
    private static synchronized void trim(Context context) {
        File[] files = new File(context.getCacheDir(), "img_v3").listFiles((d, n) -> n.endsWith(".webp"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long total = 0L; for (File f : files) total += f.length();
        long now = System.currentTimeMillis(); int count = files.length;
        for (File f : files) {
            if (total <= DISK_BYTES && count <= 120 && now - f.lastModified() <= MAX_AGE_MS) continue;
            long bytes = f.length(); if (f.delete()) { total -= bytes; count--; }
        }
    }
    private static String sha(String s) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder value = new StringBuilder();
            for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return value.toString();
        } catch (Exception ignored) { return Integer.toHexString(s.hashCode()); }
    }
}
