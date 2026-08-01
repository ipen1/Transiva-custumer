package com.transiva.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.concurrent.CopyOnWriteArrayList;

/** Global connectivity observer used to pause/retry customer realtime screens safely. */
public final class CustomerReliabilityManager implements Application.ActivityLifecycleCallbacks {
    public interface Listener { void onNetworkChanged(boolean online); }
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean online = true;
    private static volatile Activity current;
    private static boolean installed;

    private CustomerReliabilityManager() { }

    public static synchronized void install(Application app) {
        if (installed) return;
        installed = true;
        app.registerActivityLifecycleCallbacks(new CustomerReliabilityManager());
        ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        online = isOnline(cm);
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { update(true); }
                @Override public void onLost(Network network) { update(isOnline(cm)); }
                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                    update(c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                }
            });
        } catch (Exception ignored) { }
    }

    public static boolean isOnline() { return online; }
    public static void addListener(Listener l) { if (l != null) LISTENERS.addIfAbsent(l); }
    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    private static boolean isOnline(ConnectivityManager cm) {
        try {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) { return false; }
    }

    private static void update(boolean value) {
        boolean changed = online != value;
        online = value;
        if (!changed) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity a = current;
            if (a != null && !a.isFinishing()) {
                Toast.makeText(a, value ? "Internet kembali terhubung. Menyinkronkan data…" :
                        "Koneksi terputus. Data akan disinkronkan otomatis.", Toast.LENGTH_SHORT).show();
            }
            for (Listener l : LISTENERS) try { l.onNetworkChanged(value); } catch (Exception ignored) { }
        });
    }

    @Override public void onActivityResumed(Activity activity) { current = activity; }
    @Override public void onActivityPaused(Activity activity) { if (current == activity) current = null; }
    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityStarted(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}
