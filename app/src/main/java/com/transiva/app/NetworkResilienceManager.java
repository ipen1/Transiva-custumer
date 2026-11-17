package com.transiva.app;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

/** Refresh live orders after reconnect when a screen is not already managing the same read. */
public final class NetworkResilienceManager implements TransivaNetworkMonitor.Listener {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final NetworkResilienceManager INSTANCE = new NetworkResilienceManager();
    private TransivaNetworkMonitor.State previous;

    private NetworkResilienceManager() {}

    public static void install(Context context) {
        if (INSTALLED.compareAndSet(false, true)) TransivaNetworkMonitor.addListener(INSTANCE);
    }

    @Override public synchronized void onNetworkChanged(TransivaNetworkMonitor.State state) {
        TransivaNetworkMonitor.State old = previous;
        previous = state;
        if (old == null || old == state || state == TransivaNetworkMonitor.State.OFFLINE
                || TransivaCustomerApplication.foregroundOwnsActiveOrderRead()) return;

        Context app = TransivaCustomerApplication.appContext();
        if (app != null) UnifiedLiveOrderCenter.refresh(app, null);
    }
}
