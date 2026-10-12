package com.transiva.app;

import android.content.Context;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Future;

/** Shared bottom-nav indicator. Owner lifecycle prevents requests from hidden tabs. */
public final class DashboardServerIndicator extends FrameLayout {
    private final Activity owner;
    private boolean registered;
    private final Application.ActivityLifecycleCallbacks lifecycle;
    private final View square;
    private boolean active;
    private String message="Menghubungkan ke server Transiva";
    private final CustomerServerConnectionMonitor monitor=CustomerServerConnectionMonitor.shared();
    private final CustomerServerConnectionMonitor.Listener statusListener=this::show;

    public DashboardServerIndicator(Context context) {
        super(context);
        owner = context instanceof Activity ? (Activity) context : null;
        lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) { if (activity == owner) start(); }
            @Override public void onActivityPaused(Activity activity) { if (activity == owner) stop(); }
            @Override public void onActivityStopped(Activity activity) { if (activity == owner) stop(); }
            @Override public void onActivityDestroyed(Activity activity) {
                if (activity == owner) { stop(); unregister(); }
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        };
        GradientDrawable surface = new GradientDrawable(); surface.setColor(0x24FFFFFF);
        surface.setCornerRadius(dp(7)); surface.setStroke(dp(1), 0x50FFFFFF); setBackground(surface);
        square = new View(context);
        LayoutParams dot = new LayoutParams(dp(12), dp(12), Gravity.CENTER); addView(square, dot);
        square.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setFocusable(true); show("#FBBF24", message);
        setOnClickListener(v -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            if (active) monitor.refresh();
        });
    }

    public void start() {
        if(active)return; active=true; monitor.subscribe(getContext(),statusListener);
    }
    public void stop() {
        if(!active)return; active=false; monitor.unsubscribe(statusListener);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (owner != null && !registered) {
            owner.getApplication().registerActivityLifecycleCallbacks(lifecycle); registered = true;
        }
        start();
    }
    private void unregister() {
        if (owner != null && registered) {
            owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle); registered = false;
        }
    }
    @Override protected void onDetachedFromWindow() { stop(); unregister(); super.onDetachedFromWindow(); }

    private void show(String color, String nextMessage) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(Color.parseColor(color));
        shape.setCornerRadius(dp(2)); shape.setStroke(dp(1), 0xD9FFFFFF); square.setBackground(shape);
        message = nextMessage; setContentDescription("Status koneksi: " + message + ". Ketuk untuk memeriksa kembali.");
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
