package com.transiva.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Lightweight process-wide network health monitor. No location or personal data is collected. */
public final class TransivaNetworkMonitor {
    public interface Listener { void onNetworkChanged(State state); }
    public enum State { OFFLINE, CONNECTED, VALIDATED }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile State state = State.OFFLINE;
    private static volatile boolean installed;
    private static ConnectivityManager.NetworkCallback callback;

    private TransivaNetworkMonitor() { }

    public static void install(Context context) {
        if (context == null || installed) return;
        synchronized (TransivaNetworkMonitor.class) {
            if (installed) return;
            Context app = context.getApplicationContext();
            ConnectivityManager cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            state = detect(cm);
            callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { update(cm); }
                @Override public void onLost(Network network) { update(cm); }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { update(cm); }
            };
            try {
                if (Build.VERSION.SDK_INT >= 24) {
                    cm.registerDefaultNetworkCallback(callback);
                } else {
                    NetworkRequest request = new NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build();
                    cm.registerNetworkCallback(request, callback);
                }
                installed = true;
            } catch (Throwable ignored) { state = detect(cm); }
        }
    }

    public static State state() { return state; }
    public static boolean isOnline() { return state != State.OFFLINE; }
    public static boolean isValidated() { return state == State.VALIDATED; }

    public static void addListener(Listener listener) {
        if (listener != null) { LISTENERS.add(listener); listener.onNetworkChanged(state); }
    }
    public static void removeListener(Listener listener) { if (listener != null) LISTENERS.remove(listener); }

    private static void update(ConnectivityManager cm) {
        State next = detect(cm);
        if (next == state) return;
        state = next;
        TransivaCrashReporter.networkStateChanged(next.name().toLowerCase());
        for (Listener listener : LISTENERS) {
            try { listener.onNetworkChanged(next); } catch (Throwable ignored) { }
        }
    }

    private static State detect(ConnectivityManager cm) {
        try {
            Network network = cm.getActiveNetwork();
            if (network == null) return State.OFFLINE;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return State.OFFLINE;
            if (Build.VERSION.SDK_INT >= 23 && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return State.VALIDATED;
            return State.CONNECTED;
        } catch (Throwable ignored) { return State.OFFLINE; }
    }
}
