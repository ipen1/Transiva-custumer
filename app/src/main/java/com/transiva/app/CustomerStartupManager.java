package com.transiva.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.messaging.FirebaseMessaging;

/** Keeps Application.onCreate fast: critical guards first, network/noncritical startup is deferred. */
public final class CustomerStartupManager {
    private CustomerStartupManager(){}
    public static void installCritical(Application app){
        TransivaCrashReporter.initialize(app); TransivaNetworkMonitor.install(app); AdaptiveTlsPinning.install(app);
        CustomerReliabilityManager.install(app); NetworkResilienceManager.install(app); NetworkStatusBanner.install(); CustomerMessageApi.initialize(app); CustomerAnalytics.initialize(app); TransivaAnrWatchdog.install();
    }
    public static void deferNonCritical(Application app){ new Handler(Looper.getMainLooper()).postDelayed(() -> TransivaNetworkExecutor.execute(() -> {
        try{ CustomerFcmTokenSync.syncIfNeeded(app); }catch(Throwable ignored){}
        try{ CustomerResourceUpdateManager.checkInBackground(app); }catch(Throwable ignored){}
        try{ FirebaseMessaging.getInstance().subscribeToTopic("transiva_customer_security"); }catch(Throwable ignored){}
    }), 700L); }
}
