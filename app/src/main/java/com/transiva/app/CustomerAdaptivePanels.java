package com.transiva.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/** Reuses the existing map/form views; landscape gives the form a scrollable side panel. */
final class CustomerAdaptivePanels {
    private CustomerAdaptivePanels() {}
    static void install(LinearLayout root, View map) {
        List<View> order = new ArrayList<>(); List<ViewGroup.LayoutParams> params = new ArrayList<>();
        for(int i=0;i<root.getChildCount();i++) {
            order.add(root.getChildAt(i)); params.add(root.getChildAt(i).getLayoutParams());
        }
        ScrollView scroll = new ScrollView(root.getContext()); scroll.setFillViewport(false);
        LinearLayout form = new LinearLayout(root.getContext()); form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form,new ScrollView.LayoutParams(-1,-2));
        boolean[] split = {false};
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)-> {
            float density=root.getResources().getDisplayMetrics().density;
            boolean wide=r-l>=520*density && b-t<560*density && r-l>b-t;
            if(wide==split[0])return; split[0]=wide;
            root.removeAllViews(); form.removeAllViews();
            if(wide) {
                root.setOrientation(LinearLayout.HORIZONTAL);
                for(View child:order) if(child!=map)form.addView(child,new LinearLayout.LayoutParams(-1,-2));
                root.addView(scroll,new LinearLayout.LayoutParams(0,-1,.44f));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,.56f);
                lp.leftMargin=Math.round(8*density); root.addView(map,lp);
            } else {
                root.setOrientation(LinearLayout.VERTICAL);
                for(int i=0;i<order.size();i++)root.addView(order.get(i),params.get(i));
            }
        });
    }
}
