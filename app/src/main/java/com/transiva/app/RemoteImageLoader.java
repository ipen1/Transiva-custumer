package com.transiva.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.content.Context;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** Bounded image download/decode, isolated from transactional API workers. */
public final class RemoteImageLoader {
    private static final long MAX_DOWNLOAD = 20L * 1024L * 1024L;
    private RemoteImageLoader() { }
    public static void loadCenterCrop(ImageView view, String url, int fallback) { load(view, url, fallback, true); }
    public static void loadPreserveScale(ImageView view, String url, int fallback) { load(view, url, fallback, false); }
    private static void load(ImageView view, String url, int fallback, boolean crop) {
        if (view == null) return;
        String clean = url == null ? "" : url.trim();
        Object binding = new Object();
        view.setTag(binding); // Also invalidate a previous request when the new URL is empty.
        if (crop) view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (fallback != 0) view.setImageResource(fallback); else view.setImageDrawable(null);
        if (clean.isEmpty()) return;
        boolean smallHeap = Runtime.getRuntime().maxMemory() <= 128L * 1024L * 1024L;
        int edge = crop ? (smallHeap ? 512 : 1024) : (smallHeap ? 1024 : 2048);
        String cacheKey = clean + "#sample=" + edge;
        Bitmap memory = ImageMemoryDiskCache.getMemory(cacheKey);
        if (memory != null) { view.setImageBitmap(memory); return; }
        Context app = view.getContext().getApplicationContext();
        WeakReference<ImageView> target = new WeakReference<>(view);
        try {
            TransivaImageExecutor.execute(() -> {
                if (target.get() == null) return;
                HttpURLConnection c = null; File temporary = null;
                try {
                    Bitmap cached = ImageMemoryDiskCache.getDisk(app, cacheKey);
                    if (cached != null) { post(target, binding, cached); return; }
                    URL endpoint = new URL(clean);
                    if (!"https".equalsIgnoreCase(endpoint.getProtocol())) return;
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                    int code = 0;
                    for (int hop = 0; hop < 4; hop++) {
                        if (!"https".equalsIgnoreCase(endpoint.getProtocol()) || endpoint.getUserInfo() != null) return;
                        long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                        if (left <= 0L || Thread.currentThread().isInterrupted()) return;
                        c = (HttpURLConnection) endpoint.openConnection();
                        c.setInstanceFollowRedirects(false);
                        // Re-evaluate the origin for every redirect, never forward credentials to a CDN.
                        CustomerApiClient.applySecurity(app, c);
                        c.setConnectTimeout((int)Math.min(7000L,left));
                        c.setReadTimeout((int)Math.min(8000L,left));
                        c.setUseCaches(true); c.setRequestProperty("Accept", "image/*");
                        code = c.getResponseCode();
                        if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) break;
                        String location = c.getHeaderField("Location");
                        if (location == null || hop == 3) return;
                        URL next = new URL(endpoint, location);
                        c.disconnect(); c = null; endpoint = next;
                    }
                    if (c == null || code < 200 || code >= 300 || c.getContentLength() > MAX_DOWNLOAD) return;
                    temporary = File.createTempFile("transiva-image-", ".download", app.getCacheDir());
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(temporary)) {
                        byte[] buffer = new byte[8192]; long total = 0; int n;
                        while ((n = in.read(buffer)) != -1) {
                            total += n;
                            if (total > MAX_DOWNLOAD || Thread.currentThread().isInterrupted()
                                    || System.nanoTime() > deadline || target.get() == null) return;
                            out.write(buffer, 0, n);
                        }
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(temporary.getAbsolutePath(), options);
                    if (options.outWidth <= 0 || options.outHeight <= 0) return;
                    options.inSampleSize = NetworkSafetyRules.bitmapSample(options.outWidth, options.outHeight,
                            edge, (long) edge * edge);
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeFile(temporary.getAbsolutePath(), options);
                    if (bitmap == null) return;
                    ImageMemoryDiskCache.put(app, cacheKey, bitmap);
                    post(target, binding, bitmap);
                } catch (Exception ignored) {
                } catch (OutOfMemoryError lowMemory) {
                    ImageMemoryDiskCache.clearMemory();
                } finally {
                    if (c != null) c.disconnect();
                    if (temporary != null) temporary.delete();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException busy) { /* Keep placeholder. */ }
    }
    private static void post(WeakReference<ImageView> target, Object binding, Bitmap bitmap) {
        ImageView view = target.get();
        if (view == null) return;
        view.post(() -> {
            ImageView current = target.get();
            if (current != null && current.getTag() == binding && !bitmap.isRecycled()) current.setImageBitmap(bitmap);
        });
    }
}
