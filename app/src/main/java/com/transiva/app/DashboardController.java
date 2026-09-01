package com.transiva.app;
import android.app.Activity;
/** Extracted dashboard cross-cutting lifecycle responsibilities. */
public final class DashboardController {
    private final Activity a;
    public DashboardController(Activity a){this.a=a;}
    public void onCreate(){CustomerAnalytics.funnel(a,"dashboard_open",null);}
    public void onResume(){CustomerFcmTokenSync.syncIfNeeded(a);}
    public void onDestroy(){}
}
