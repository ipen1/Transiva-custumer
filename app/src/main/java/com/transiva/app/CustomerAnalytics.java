package com.transiva.app;

import android.content.Context;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;

/** Privacy-minimal product funnel events. Do not send names, phone numbers, addresses or chat text. */
public final class CustomerAnalytics {
    private static volatile FirebaseAnalytics analytics;
    private CustomerAnalytics() {}
    public static void initialize(Context c){ if(c!=null && analytics==null) analytics=FirebaseAnalytics.getInstance(c.getApplicationContext()); }
    public static void event(Context c,String name){ event(c,name,null); }
    public static void event(Context c,String name,Bundle params){ try{ initialize(c); if(analytics!=null) analytics.logEvent(clean(name), params); }catch(Throwable ignored){} }
    public static void screen(Context c,String screen){ Bundle b=new Bundle(); b.putString(FirebaseAnalytics.Param.SCREEN_NAME, clean(screen)); b.putString(FirebaseAnalytics.Param.SCREEN_CLASS, clean(screen)); event(c,FirebaseAnalytics.Event.SCREEN_VIEW,b); }
    public static void funnel(Context c,String stage,String service){ Bundle b=new Bundle(); b.putString("stage",clean(stage)); if(service!=null)b.putString("service",clean(service)); event(c,"customer_funnel",b); }
    private static String clean(String s){ if(s==null)return "unknown"; s=s.trim().toLowerCase().replaceAll("[^a-z0-9_]","_"); return s.length()>40?s.substring(0,40):s; }
}
