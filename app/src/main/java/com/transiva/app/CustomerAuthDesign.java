package com.transiva.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native auth styling; width follows the actual window, including split screen. */
final class CustomerAuthDesign {
    private CustomerAuthDesign() { }
    static int dp(Context c, int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable backdrop() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(5,23,50),Color.rgb(14,66,139),Color.rgb(30,124,223)});
    }
    static GradientDrawable surface(Context c) {
        GradientDrawable d=new GradientDrawable(); d.setColor(Color.WHITE);
        d.setCornerRadius(dp(c,24));d.setStroke(dp(c,1),Color.rgb(215,231,253));return d;
    }
    static GradientDrawable primary(Context c) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(10,88,231),Color.rgb(27,149,246)});
        d.setCornerRadius(dp(c,14));return d;
    }
    static StateListDrawable field(Context c) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},input(c,"#F5FAFF","#1879F3",2));
        states.addState(new int[]{-android.R.attr.state_enabled},input(c,"#F1F4F8","#E1E8F1",1));
        states.addState(new int[]{},input(c,"#F6F9FE","#DFE8F4",1));return states;
    }
    private static GradientDrawable input(Context c,String fill,String border,int width) {
        GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(fill));
        d.setCornerRadius(dp(c,13));d.setStroke(dp(c,width),Color.parseColor(border));return d;
    }
    static LinearLayout brand(Context c,String title,String subtitle) {
        LinearLayout header=new LinearLayout(c);header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,18));
        LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=label(c,"T",24,true);mark.setGravity(Gravity.CENTER);
        GradientDrawable emblem=primary(c);emblem.setCornerRadius(dp(c,15));emblem.setStroke(dp(c,1),0x80FFFFFF);
        mark.setBackground(emblem);top.addView(mark,new LinearLayout.LayoutParams(dp(c,44),dp(c,44)));
        TextView brand=label(c,"TRANSIVA",15,true);brand.setLetterSpacing(.16f);
        brand.setPadding(dp(c,12),0,0,0);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView detail=label(c,"SELAMAT DATANG",9,true);detail.setTextColor(Color.rgb(218,232,254));
        detail.setMaxLines(2);detail.setGravity(Gravity.END);top.addView(detail,new LinearLayout.LayoutParams(dp(c,92),-2));
        header.addView(top,new LinearLayout.LayoutParams(-1,-2));
        TextView heading=label(c,title,23,true);heading.setPadding(0,dp(c,14),0,0);header.addView(heading);
        TextView description=label(c,subtitle,12,false);description.setTextColor(Color.rgb(217,234,255));
        description.setPadding(0,dp(c,5),0,0);header.addView(description);
        return header;
    }
    private static TextView label(Context c,String s,int size,boolean bold) {
        TextView t=new TextView(c);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;
    }
    static final class Column extends LinearLayout {
        Column(Context c){super(c);}
        @Override protected void onMeasure(int widthSpec,int heightSpec) {
            int size=View.MeasureSpec.getSize(widthSpec);
            if(View.MeasureSpec.getMode(widthSpec)!=View.MeasureSpec.UNSPECIFIED)
                widthSpec=View.MeasureSpec.makeMeasureSpec(Math.min(size,dp(getContext(),512)),View.MeasureSpec.EXACTLY);
            super.onMeasure(widthSpec,heightSpec);
        }
    }
}
