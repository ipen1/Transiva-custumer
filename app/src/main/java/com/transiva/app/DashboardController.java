package com.transiva.app;
import android.app.Activity;
import android.content.Intent;
/** Extracted dashboard cross-cutting lifecycle + order-center responsibilities. */
public final class DashboardController {
    private final Activity a;
    public DashboardController(Activity a){this.a=a;}
    public void onCreate(){CustomerAnalytics.funnel(a,"dashboard_open",null);}
    public void onResume(){CustomerFcmTokenSync.syncIfNeeded(a);UnifiedLiveOrderCenter.refresh(a,null);}
    public void openLiveOrderCenter(){a.startActivity(new Intent(a,LiveOrderCenterActivity.class));}
    public void onDestroy(){}
}
