package com.transiva.app;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.Switch;

/** Native Switch gestures/accessibility with deterministic, visible geometry on every OEM theme. */
final class CustomerSwitchStyle {
    private CustomerSwitchStyle(){}
    private static final int[][] STATES={{-android.R.attr.state_enabled,android.R.attr.state_checked},
            {-android.R.attr.state_enabled},{android.R.attr.state_checked},{}};
    static ColorStateList thumb(boolean dark){return new ColorStateList(STATES,new int[]{0xffdbe4f0,0xffdbe4f0,0xffffffff,dark?0xffeef5ff:0xffffffff});}
    static ColorStateList track(boolean dark){return new ColorStateList(STATES,new int[]{dark?0xff385778:0xff7295bc,dark?0xff394b62:0xffa0afc2,dark?0xff2388ed:0xff0964d6,dark?0xff607b99:0xff6d829e});}
    static void install(Switch toggle){
        float density=toggle.getResources().getDisplayMetrics().density;
        GradientDrawable track=new GradientDrawable();track.setColor(0xff6d829e);
        track.setCornerRadius(15*density);track.setSize(Math.round(52*density),Math.round(30*density));
        GradientDrawable thumb=new GradientDrawable();thumb.setShape(GradientDrawable.OVAL);thumb.setColor(0xffffffff);
        thumb.setSize(Math.round(24*density),Math.round(24*density));
        toggle.setTrackDrawable(track);toggle.setThumbDrawable(new android.graphics.drawable.InsetDrawable(thumb,Math.round(3*density)));
        toggle.setShowText(false);toggle.setSplitTrack(false);
        toggle.setSwitchMinWidth(Math.round(52*density));toggle.setSwitchPadding(Math.round(12*density));
        toggle.setMinimumHeight(Math.round(48*density));
    }
}
