package com.transiva.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** Pull refresh shares the existing vertical scroller; horizontal story gestures pass through. */
final class MitraPullScrollView extends ScrollView {
    interface Allowed { boolean canRefresh(); }
    Allowed allowed;
    Runnable refresh;
    android.view.View indicator;
    private float downX, downY;
    private boolean dragging, refreshing, eligible;
    private final int slop;
    private final float threshold;
    MitraPullScrollView(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        threshold = 72 * context.getResources().getDisplayMetrics().density;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX=e.getX(); downY=e.getY(); dragging=false;
            eligible=!refreshing && getScrollY()==0 && allowed!=null && allowed.canRefresh();
        } else if (e.getActionMasked()==MotionEvent.ACTION_MOVE && eligible && !refreshing) {
            float dy=e.getY()-downY;
            if (dy>slop*2 && dy>Math.abs(e.getX()-downX)*1.5f && getScrollY()==0) {
                dragging=true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return super.onTouchEvent(e);
        float dy=Math.max(0,e.getY()-downY);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
            if(getChildCount()>0)getChildAt(0).setTranslationY(Math.min(threshold,dy*.35f));
            if(indicator!=null){indicator.setVisibility(VISIBLE);indicator.setAlpha(Math.min(1f,dy/threshold));}
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP || e.getActionMasked()==MotionEvent.ACTION_CANCEL) {
            boolean run=e.getActionMasked()==MotionEvent.ACTION_UP && dy>=threshold && allowed!=null && allowed.canRefresh();
            dragging=false; eligible=false;
            if(getChildCount()>0)getChildAt(0).animate().translationY(0f).setDuration(150).start();
            if(run && refresh!=null) {refreshing=true;if(indicator!=null)indicator.setAlpha(1f);refresh.run();}
            else finishRefresh();
            return true;
        }
        return true;
    }
    void finishRefresh() {
        refreshing=false;
        if(indicator!=null)indicator.setVisibility(GONE);
    }
}
