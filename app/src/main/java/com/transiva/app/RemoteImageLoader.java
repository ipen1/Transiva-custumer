package com.transiva.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Shared non-blocking remote image loader backed by the app network pool and memory/disk cache. */
public final class RemoteImageLoader {
    private RemoteImageLoader() {}

    public static void loadCenterCrop(ImageView view, String imageUrl, int fallbackDrawable) {
        load(view, imageUrl, fallbackDrawable, true);
    }

    /** Loads using the ImageView's existing ScaleType (useful for chat/photo viewers). */
    public static void loadPreserveScale(ImageView view, String imageUrl, int fallbackDrawable) {
        load(view, imageUrl, fallbackDrawable, false);
    }

    private static void load(ImageView view, String imageUrl, int fallbackDrawable, boolean centerCrop) {
        if (view == null) return;
        String clean = imageUrl == null ? "" : imageUrl.trim();
        if (centerCrop) view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (fallbackDrawable != 0) view.setImageResource(fallbackDrawable);
        if (clean.isEmpty()) return;

        view.setTag(clean);
        Bitmap memory = ImageMemoryDiskCache.getMemory(clean);
        if (memory != null) { view.setImageBitmap(memory); return; }

        final android.content.Context app = view.getContext().getApplicationContext();
        TransivaImageExecutor.execute(() -> {
            // P2: disk decode is background-only; never block the UI thread.
            Bitmap cached = ImageMemoryDiskCache.getDisk(app, clean);
            if (cached != null) { postIfStillBound(view, clean, cached); return; }

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(clean).openConnection();
                CustomerApiClient.applySecurity(app, connection);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(true);
                connection.setRequestProperty("Accept", "image/*");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) return;
                Bitmap bitmap;
                try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
                    bitmap = BitmapFactory.decodeStream(input);
                }
                if (bitmap == null) return;
                ImageMemoryDiskCache.put(app, clean, bitmap);
                // Do not recycle here: the same Bitmap is owned by the shared memory cache.
                postIfStillBound(view, clean, bitmap);
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static void postIfStillBound(ImageView view, String key, Bitmap bitmap) {
        view.post(() -> {
            Object tag = view.getTag();
            if (tag != null && key.equals(String.valueOf(tag)) && bitmap != null && !bitmap.isRecycled()) {
                view.setImageBitmap(bitmap);
            }
        });
    }
}
