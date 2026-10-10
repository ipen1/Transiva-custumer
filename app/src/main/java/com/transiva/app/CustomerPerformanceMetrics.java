package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, non-PII measurements. First normal-screen pre-draw is not TTID/TTFD. */
public final class CustomerPerformanceMetrics {
    private static long processCreated;
    private static boolean firstScreen;
    private static final AtomicInteger loads = new AtomicInteger();
    private CustomerPerformanceMetrics() { }
    public static synchronized void install() { processCreated = SystemClock.elapsedRealtime(); }
    public static void resumed(Activity activity) {
        if (activity instanceof SplashActivity || activity instanceof UpdateDownloadActivity) return;
        synchronized (CustomerPerformanceMetrics.class) {
            if (firstScreen) return;
            firstScreen = true;
        }
        final View view = activity.getWindow().getDecorView();
        final long started = processCreated;
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
                Bundle b = new Bundle();
                b.putLong("application_to_screen_ms", Math.max(0, SystemClock.elapsedRealtime() - started));
                b.putLong("java_heap_kb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);
                b.putLong("native_heap_kb", Debug.getNativeHeapAllocatedSize() / 1024);
                CustomerAnalytics.event(activity.getApplicationContext(), "customer_startup_sample", b);
                return true;
            }
        });
    }
    public static String readCategory(String url) {
        // Only fixed category labels are emitted. Query strings and identifiers are discarded.
        try {
            String path = java.net.URI.create(url).getPath();
            if (path.endsWith("/get_customer_profile.php")) return "profile";
            if (path.endsWith("/get_food_restaurants.php") || path.endsWith("/get_food_menus.php")) return "food";
            if (path.endsWith("/getWisataPlaces.php")) return "tour";
        } catch (RuntimeException ignored) { }
        return "other_read";
    }
    public static void load(Context context, String category, long started, boolean success) {
        // Maximum 20 read samples per process; URLs, user IDs and payloads are never included.
        if (loads.incrementAndGet() > 20) return;
        Bundle b = new Bundle();
        b.putLong("duration_ms", Math.max(0, SystemClock.elapsedRealtime() - started));
        b.putString("category", category);
        b.putString("result", success ? "success" : "failed");
        CustomerAnalytics.event(context, "customer_json_load", b);
    }
}
