package com.transiva.app;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
/** One report per main-thread stall, with the actual main-thread stack. Never kills the app. */
public final class TransivaAnrWatchdog {
    private static final long TICK_MS=2000L,ANR_MS=6000L;
    private static volatile boolean started;
    private TransivaAnrWatchdog() { }
    public static synchronized void install() {
        if(started)return;started=true;
        Handler main=new Handler(Looper.getMainLooper());CustomerStallTracker tracker=new CustomerStallTracker();
        Thread watcher=new Thread(() -> {
            while(started)try {
                Thread.sleep(TICK_MS);
                if(!tracker.waiting()) {
                    final long episode=tracker.begin(SystemClock.uptimeMillis());
                    main.post(() -> tracker.recover(episode));
                }
                if(tracker.reportIfStalled(SystemClock.uptimeMillis(),ANR_MS)) {
                    RuntimeException error=new RuntimeException("Main thread stalled >="+ANR_MS+"ms");
                    error.setStackTrace(Looper.getMainLooper().getThread().getStackTrace());
                    try{com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(error);}catch(Exception ignored){}
                }
            }catch(InterruptedException stopped){Thread.currentThread().interrupt();return;}catch(Exception ignored){}
        },"transiva-anr-watchdog");watcher.setDaemon(true);watcher.start();
    }
}
