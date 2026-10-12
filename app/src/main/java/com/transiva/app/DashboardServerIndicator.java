package com.transiva.app;

import android.content.Context;
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

/** Dashboard-only server reachability. Internet availability alone never means green. */
public final class DashboardServerIndicator extends FrameLayout {
    private static final String URL_TEXT = "https://transiva.my.id/server/customer_connection_status.php";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final View square;
    private volatile boolean active;
    private volatile long generation;
    private HttpURLConnection connection;
    private Future<?> request;
    private boolean busy;
    private String message = "Menghubungkan ke server Transiva";
    private TransivaNetworkMonitor.State networkState;
    private final Runnable poll = this::check;
    private final Runnable deadline = () -> {
        if (!active || !busy) return;
        cancelRequest(); show("#EF4444", "Koneksi server terputus • pemeriksaan timeout");
        main.postDelayed(poll, 15000L);
    };
    private final TransivaNetworkMonitor.Listener networkListener = state -> main.post(() -> {
        if (!active) return;
        TransivaNetworkMonitor.State previous = networkState; networkState = state;
        if (state == TransivaNetworkMonitor.State.OFFLINE) {
            cancelRequest(); main.removeCallbacks(poll);
            show("#EF4444", "Terputus • perangkat tidak memiliki koneksi internet");
            main.postDelayed(poll, 15000L);
        } else if (previous == TransivaNetworkMonitor.State.OFFLINE || previous == null) {
            cancelRequest(); check();
        }
    });

    public DashboardServerIndicator(Context context) {
        super(context);
        GradientDrawable surface = new GradientDrawable(); surface.setColor(0x24FFFFFF);
        surface.setCornerRadius(dp(7)); surface.setStroke(dp(1), 0x50FFFFFF); setBackground(surface);
        square = new View(context);
        LayoutParams dot = new LayoutParams(dp(12), dp(12), Gravity.CENTER); addView(square, dot);
        square.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setFocusable(true); show("#FBBF24", message);
        setOnClickListener(v -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            if (active && !busy) check();
        });
    }

    public void start() {
        if (active) return;
        active = true; networkState = null;
        TransivaNetworkMonitor.install(getContext());
        networkState = TransivaNetworkMonitor.state();
        TransivaNetworkMonitor.addListener(networkListener);
        check();
    }
    public void stop() {
        active = false; TransivaNetworkMonitor.removeListener(networkListener);
        main.removeCallbacksAndMessages(null); cancelRequest(); networkState = null;
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    private void check() {
        if (!active || busy) return;
        main.removeCallbacks(poll);
        if (!TransivaNetworkMonitor.isOnline()) {
            show("#EF4444", "Terputus • perangkat tidak memiliki koneksi internet");
            main.postDelayed(poll, 15000L); return;
        }
        busy = true; final long token = ++generation;
        show("#FBBF24", "Menghubungkan ke server Transiva");
        main.postDelayed(deadline, 12000L);
        request = TransivaNetworkExecutor.execute(() -> {
            boolean healthy = false; HttpURLConnection candidate = null;
            try {
                candidate = (HttpURLConnection) new URL(URL_TEXT).openConnection();
                candidate.setConnectTimeout(5000); candidate.setReadTimeout(5000);
                candidate.setInstanceFollowRedirects(false); candidate.setUseCaches(false);
                candidate.setRequestProperty("Accept", "application/json");
                candidate.setRequestProperty("Cache-Control", "no-cache, no-store");
                synchronized (lock) {
                    if (!active || token != generation) return;
                    connection = candidate;
                }
                int code = candidate.getResponseCode();
                if (code == 200) {
                    JSONObject response = new JSONObject(BoundedResponseReader.read(candidate.getInputStream(), 4096, 5000));
                    healthy = response.optBoolean("success", false)
                            && "transiva-customer-connection".equals(response.optString("service"));
                }
            } catch (Exception ignored) {
                // An unreachable health probe is a connection state, not an application crash.
            } finally {
                if (candidate != null) candidate.disconnect();
                synchronized (lock) { if (connection == candidate) connection = null; }
            }
            final boolean result = healthy;
            main.post(() -> complete(token, result));
        }, () -> main.post(() -> complete(token, false)));
    }
    private void complete(long token, boolean healthy) {
        if (!active || token != generation || !busy) return;
        busy = false; request = null; main.removeCallbacks(deadline);
        show(healthy ? "#22C55E" : "#EF4444", healthy ? "Terhubung ke server Transiva" : "Terputus • server belum dapat dihubungi");
        main.postDelayed(poll, healthy ? 30000L : 15000L);
    }
    private void cancelRequest() {
        ++generation; busy = false; main.removeCallbacks(deadline);
        Future<?> pending = request; request = null; if (pending != null) pending.cancel(true);
        synchronized (lock) { if (connection != null) { connection.disconnect(); connection = null; } }
    }
    private void show(String color, String nextMessage) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(Color.parseColor(color));
        shape.setCornerRadius(dp(2)); shape.setStroke(dp(1), 0xD9FFFFFF); square.setBackground(shape);
        message = nextMessage; setContentDescription("Status koneksi: " + message + ". Ketuk untuk memeriksa kembali.");
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
