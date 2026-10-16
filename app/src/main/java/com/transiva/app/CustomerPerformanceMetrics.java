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
    private static long lastResourceSample;
    private static int resourceSamples;
    private CustomerPerformanceMetrics() { }
    public static synchronized void install() { processCreated = SystemClock.elapsedRealtime(); }
    public static void resumed(Activity activity) {
        resourceSample(activity.getApplicationContext());
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
    /** At most 12 passive samples/process, never more than once a minute; no new permission. */
    private static synchronized void resourceSample(Context context){
        long now=SystemClock.elapsedRealtime();
        if(resourceSamples>=12||(lastResourceSample>0&&now-lastResourceSample<60000))return;
        if(TransivaNetworkExecutor.queuedTasks()>16)return; // Passive sampling yields to customer work.
        lastResourceSample=now;++resourceSamples;
        TransivaNetworkExecutor.execute(()->{
            try{
                Bundle sample=new Bundle();
                sample.putLong("java_heap_kb",(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024);
                sample.putLong("native_heap_kb",Debug.getNativeHeapAllocatedSize()/1024);
                sample.putLong("process_pss_kb",Debug.getPss());
                android.os.BatteryManager battery=(android.os.BatteryManager)context.getSystemService(Context.BATTERY_SERVICE);
                if(battery!=null){int percent=battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);if(percent>=0&&percent<=100)sample.putLong("device_battery_percent",percent);}
                sample.putLong("network_queue",TransivaNetworkExecutor.queuedTasks());
                sample.putLong("network_rejected",TransivaNetworkExecutor.rejectedTasks());
                if(android.os.Build.VERSION.SDK_INT>=30){
                    android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                    int crashes=0,anrs=0;
                    if(manager!=null)for(android.app.ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,10)){
                        if(info.getReason()==android.app.ApplicationExitInfo.REASON_CRASH||info.getReason()==android.app.ApplicationExitInfo.REASON_CRASH_NATIVE)++crashes;
                        if(info.getReason()==android.app.ApplicationExitInfo.REASON_ANR)++anrs;
                    }
                    // Counts among the last ten retained exits, NOT a crash-free rate or session denominator.
                    sample.putLong("recent_crash_exits",crashes);sample.putLong("recent_anr_exits",anrs);
                }
                CustomerAnalytics.event(context,"customer_resource_sample",sample);
            }catch(Exception ignored){}
        },()->{});
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
