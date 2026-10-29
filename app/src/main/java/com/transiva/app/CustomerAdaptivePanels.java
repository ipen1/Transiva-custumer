package com.transiva.app;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/** Preserve the existing map and form instances; let larger text and options scroll on any phone. */
final class CustomerAdaptivePanels {
    private CustomerAdaptivePanels() {}
    static void install(LinearLayout root, View map) {
        List<View> order=new ArrayList<>();
        for(int i=0;i<root.getChildCount();i++)order.add(root.getChildAt(i));
        ScrollView scroll=new ScrollView(root.getContext()) {
            private boolean mapGesture;
            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {
                    Rect rect=new Rect(); mapGesture=map.getGlobalVisibleRect(rect)
                            && rect.contains((int)event.getRawX(),(int)event.getRawY());
                }
                // A gesture that starts on the map belongs to the map, including vertical pans.
                if(mapGesture)return false;
                return super.onInterceptTouchEvent(event);
            }
        };
        scroll.setFillViewport(false); scroll.setClipToPadding(false);
        LinearLayout form=new LinearLayout(root.getContext());form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form,new ScrollView.LayoutParams(-1,-2));
        int[] layout={-1,-1};
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            float density=root.getResources().getDisplayMetrics().density;
            int width=r-l,height=b-t;if(width<=0||height<=0)return;
            boolean wide=width>=520*density && height<560*density && width>height;
            int mapHeight=Math.round(Math.max(220,Math.min(340,height/density*.40f))*density);
            int mode=wide?1:0;
            if(layout[0]==mode && layout[1]==mapHeight)return;
            layout[0]=mode;layout[1]=mapHeight;
            // Detach before reparenting. No screen, request, or map renderer is recreated.
            root.removeAllViews();form.removeAllViews();
            for(View child:order) {
                if(child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);
            }
            if(wide) {
                root.setOrientation(LinearLayout.HORIZONTAL);
                for(View child:order)if(child!=map)form.addView(child,new LinearLayout.LayoutParams(-1,-2));
                root.addView(scroll,new LinearLayout.LayoutParams(0,-1,.48f));
                LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(0,-1,.52f);
                mlp.leftMargin=Math.round(8*density);root.addView(map,mlp);
            } else {
                root.setOrientation(LinearLayout.VERTICAL);
                for(View child:order) {
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,child==map?mapHeight:-2);
                    if(child==map){lp.topMargin=Math.round(8*density);lp.bottomMargin=Math.round(8*density);}
                    form.addView(child,lp);
                }
                root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
            }
        });
    }
}
