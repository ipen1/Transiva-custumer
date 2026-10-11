package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Refreshes only the scrollable page. Bottom navigation, filters and existing data stay in place. */
public final class CustomerPullRefreshLayout extends FrameLayout {
    public interface Busy { boolean get(); }
    private final ScrollView scroll;
    private final Runnable action;
    private final Busy busy;
    private final LinearLayout badge;
    private final ProgressBar spinner;
    private final TextView label;
    private float startX,startY,pull;
    private boolean dragging,blocked,refreshing;
    private final int slop;
    private final Runnable reset = () -> { if(!refreshing)hide(); };
    private final Runnable timeout = () -> finish(false);
    public CustomerPullRefreshLayout(Activity activity,ScrollView child,Runnable action,Busy busy) {
        super(activity);this.scroll=child;this.action=action;this.busy=busy;
        slop=ViewConfiguration.get(activity).getScaledTouchSlop();
        addView(child,new FrameLayout.LayoutParams(-1,-1));
        badge=new LinearLayout(activity);badge.setGravity(Gravity.CENTER);badge.setPadding(dp(14),dp(10),dp(14),dp(10));
        boolean dark=CustomerAppSettings.isDarkMode(activity);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.parseColor(dark?"#17283D":"#FFFFFF"));bg.setCornerRadius(dp(24));bg.setStroke(dp(1),Color.parseColor(dark?"#365579":"#CBE4FF"));badge.setBackground(bg);badge.setElevation(dp(6));
        spinner=new ProgressBar(activity);spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#0B7CFF")));badge.addView(spinner,new LinearLayout.LayoutParams(dp(20),dp(20)));
        label=new TextView(activity);label.setTextSize(12);label.setTextColor(Color.parseColor(dark?"#E7F2FF":"#075BB8"));label.setPadding(dp(8),0,0,0);badge.addView(label);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);lp.topMargin=dp(8);addView(badge,lp);badge.setVisibility(GONE);
        badge.setContentDescription("Tarik ke bawah untuk memperbarui");
        child.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x0f000001,"Perbarui data"));
            }
            @Override public boolean performAccessibilityAction(View host,int id,android.os.Bundle args){
                if(id==0x0f000001){requestRefresh();return true;}
                return super.performAccessibilityAction(host,id,args);
            }
        });
    }
    private boolean scrollUpAt(View view,float x,float y) {
        android.graphics.Rect bounds=new android.graphics.Rect();
        if(!view.getGlobalVisibleRect(bounds)||!bounds.contains((int)x,(int)y))return false;
        if(view.canScrollVertically(-1))return true;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=group.getChildCount()-1;i>=0;i--)if(scrollUpAt(group.getChildAt(i),x,y))return true;
        }
        return false;
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void show(String text,boolean working){removeCallbacks(reset);badge.setVisibility(VISIBLE);label.setText(text);spinner.setVisibility(working?VISIBLE:GONE);}
    private void hide(){scroll.animate().translationY(0).setDuration(160).start();badge.setVisibility(GONE);pull=0;}
    public boolean isRefreshing(){return refreshing;}
    public void requestRefresh(){
        if(refreshing)return;
        if(busy.get()){show("Tunggu proses yang sedang berjalan",false);postDelayed(reset,1400);return;}
        refreshing=true;show("Memperbarui data…",true);scroll.animate().translationY(dp(56)).setDuration(140).start();
        removeCallbacks(timeout);postDelayed(timeout,45000);
        try{action.run();}catch(RuntimeException e){finish(false);}
    }
    public void finish(boolean success){
        if(!refreshing)return;
        refreshing=false;removeCallbacks(timeout);show(success?"Data sudah diperbarui":"Belum diperbarui · coba lagi",false);
        scroll.animate().translationY(0).setDuration(180).start();badge.announceForAccessibility(label.getText());postDelayed(reset,success?850:2200);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){removeCallbacks(reset);if(!refreshing){scroll.animate().cancel();scroll.setTranslationY(0);badge.setVisibility(GONE);pull=0;}startX=e.getX();startY=e.getY();dragging=false;blocked=refreshing||busy.get()||scrollUpAt(scroll,e.getRawX(),e.getRawY());return false;}
        if(e.getPointerCount()>1){blocked=true;return dragging;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!blocked){float dx=e.getX()-startX,dy=e.getY()-startY;
            if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)){blocked=true;return false;}
            if(CustomerRefreshRules.begin(dx,dy,slop,!scrollUpAt(scroll,e.getRawX(),e.getRawY()),busy.get(),e.getPointerCount())){dragging=true;return true;}
        }
        return dragging;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!dragging)return super.onTouchEvent(e);
        if(e.getPointerCount()>1){hide();dragging=false;return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){pull=Math.min(dp(100),Math.max(0,(e.getY()-startY)*0.45f));scroll.animate().cancel();scroll.setTranslationY(pull);show(pull>=dp(64)?"Lepaskan untuk memperbarui":"Tarik untuk memperbarui",false);return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){boolean ready=CustomerRefreshRules.release(pull,dp(64));dragging=false;if(ready)requestRefresh();else hide();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;hide();return true;}return true;
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(reset);removeCallbacks(timeout);scroll.animate().cancel();scroll.setTranslationY(0);refreshing=false;dragging=false;badge.setVisibility(GONE);super.onDetachedFromWindow();}
}
