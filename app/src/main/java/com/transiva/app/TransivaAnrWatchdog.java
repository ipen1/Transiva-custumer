package com.transiva.app;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight main-thread stall detector. Reports long stalls to Crashlytics, never kills the app. */
public final class TransivaAnrWatchdog {
    private static final long TICK_MS=2000L, ANR_MS=6000L;
    private static final AtomicLong lastBeat=new AtomicLong(SystemClock.uptimeMillis());
    private static volatile boolean started;
    private TransivaAnrWatchdog(){}
    public static synchronized void install(){ if(started)return; started=true; Handler main=new Handler(Looper.getMainLooper());
        Thread t=new Thread(() -> { while(started){ try{ Thread.sleep(TICK_MS); long sent=SystemClock.uptimeMillis(); main.post(() -> lastBeat.set(SystemClock.uptimeMillis())); Thread.sleep(ANR_MS); long beat=lastBeat.get(); if(beat<sent){ RuntimeException e=new RuntimeException("Main thread stalled >"+ANR_MS+"ms"); try{ com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e); }catch(Throwable ignored){} } }catch(InterruptedException e){ return; }catch(Throwable ignored){} } },"transiva-anr-watchdog"); t.setDaemon(true); t.start(); }
}
