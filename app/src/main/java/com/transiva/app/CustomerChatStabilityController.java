package com.transiva.app;

import android.app.Activity;
import android.os.Handler;
import android.view.WindowManager;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns chat foreground/polling state so callbacks do not outlive the screen. */
public final class CustomerChatStabilityController {
    private final Activity activity;
    private final Handler handler;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    public CustomerChatStabilityController(Activity activity, Handler handler) { this.activity=activity; this.handler=handler; }
    public void onCreate() {
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        CustomerResponsiveUi.apply(activity);
    }
    public void onResume() { if(!destroyed.get()) active.set(true); }
    public void onPause() { active.set(false); }
    public boolean canDeliverUi() { return active.get() && !destroyed.get() && !activity.isFinishing(); }
    public boolean post(Runnable r) { if(r==null || destroyed.get()) return false; return handler.post(() -> { if(!destroyed.get() && !activity.isFinishing()) r.run(); }); }
    public boolean postDelayed(Runnable r,long delay) { if(r==null || destroyed.get()) return false; return handler.postDelayed(() -> { if(!destroyed.get() && !activity.isFinishing()) r.run(); }, delay); }
    public void destroy() { destroyed.set(true); active.set(false); handler.removeCallbacksAndMessages(null); }
}