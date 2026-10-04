package com.transiva.app;
import android.view.View;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
final class DeliveryAddressAttention {
    static void attach(View card) {
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFF4D6); float density=card.getResources().getDisplayMetrics().density; bg.setCornerRadius(14*density); bg.setStroke(Math.max(1,Math.round(density)),0xFFE5B44C); card.setBackground(bg);
        card.setContentDescription("Periksa alamat pengantaran terlebih dahulu. Ketuk untuk mengganti alamat.");
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private AnimatorSet pulse;
            public void onViewAttachedToWindow(View v) {
                if(android.os.Build.VERSION.SDK_INT>=26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) return;
                ObjectAnimator x=ObjectAnimator.ofFloat(v,"scaleX",1f,1.015f,1f);
                ObjectAnimator y=ObjectAnimator.ofFloat(v,"scaleY",1f,1.015f,1f);
                x.setRepeatCount(2); y.setRepeatCount(2); pulse=new AnimatorSet(); pulse.playTogether(x,y); pulse.setDuration(850); pulse.start();
            }
            public void onViewDetachedFromWindow(View v) { if(pulse!=null) pulse.cancel(); v.setScaleX(1f); v.setScaleY(1f); }
        });
    }
}
