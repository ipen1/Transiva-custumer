package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/** Small global connectivity banner attached to the current Activity decor. */
public final class NetworkStatusBanner implements TransivaNetworkMonitor.Listener {
    private static final NetworkStatusBanner INSTANCE = new NetworkStatusBanner();
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static TextView banner;
    private NetworkStatusBanner() { }

    public static void install() { TransivaNetworkMonitor.addListener(INSTANCE); }

    public static void attach(Activity a) {
        if (a == null || a.isFinishing()) return;
        activity = new WeakReference<>(a);
        View root = a.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup vg=(ViewGroup)root;
        TextView v=new TextView(a); banner=v;
        v.setTextSize(12); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,10),dp(a,7),dp(a,10),dp(a,7)); v.setElevation(dp(a,8));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2);lp.gravity=Gravity.TOP;
        if (vg instanceof FrameLayout) ((FrameLayout)vg).addView(v,lp); else vg.addView(v,0,new ViewGroup.LayoutParams(-1,-2));
        render(TransivaNetworkMonitor.state());
    }

    public static void detach(Activity a) {
        if (activity.get()!=a) return;
        try { if (banner!=null && banner.getParent() instanceof ViewGroup) ((ViewGroup)banner.getParent()).removeView(banner); } catch(Throwable ignored){}
        banner=null; activity=new WeakReference<>(null);
    }

    @Override public void onNetworkChanged(TransivaNetworkMonitor.State state) {
        Activity a=activity.get(); if(a==null||a.isFinishing())return; a.runOnUiThread(()->render(state));
    }

    private static void render(TransivaNetworkMonitor.State s){
        TextView v=banner;if(v==null)return;
        if(s==TransivaNetworkMonitor.State.VALIDATED){v.setVisibility(View.GONE);return;}
        v.setVisibility(View.VISIBLE);
        if(s==TransivaNetworkMonitor.State.OFFLINE){v.setText("Tidak ada koneksi • data terakhir tetap tersedia");v.setTextColor(Color.WHITE);v.setBackgroundColor(Color.parseColor("#B42318"));}
        else{v.setText("Jaringan belum stabil • menyambungkan kembali…");v.setTextColor(Color.parseColor("#7A4B00"));v.setBackgroundColor(Color.parseColor("#FFF1C2"));}
    }
    private static int dp(Activity a,int v){return(int)(v*a.getResources().getDisplayMetrics().density+.5f);}
}
