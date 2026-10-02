package com.transiva.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** Lightweight, lifecycle-aware placeholder for native network lists. */
public final class CustomerSkeleton {
    private CustomerSkeleton() {}
    public static View list(Activity activity, int rows) {
        boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int base = Color.parseColor(dark ? "#24364C" : "#E5ECF5");
        int glow = Color.parseColor(dark ? "#344D69" : "#F5F8FC");
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 12));
        for (int i = 0; i < Math.max(1, rows); i++) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(dark ? "#15263A" : "#FFFFFF"));
            bg.setCornerRadius(dp(activity, 16));
            card.setBackground(bg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(activity, 10);
            root.addView(card, cp);
            addBar(activity, card, base, glow, 65, 15);
            addBar(activity, card, base, glow, 90, 10);
            addBar(activity, card, base, glow, 42, 10);
        }
        return root;
    }
    private static void addBar(Activity a, LinearLayout parent, int base, int glow, int width, int height) {
        View bar = new View(a);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(base);
        shape.setCornerRadius(dp(a, 8));
        bar.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(a, height));
        // Use screen-relative width rather than weighted rows.
        lp.width = (a.getResources().getDisplayMetrics().widthPixels - dp(a, 60)) * width / 100;
        lp.bottomMargin = dp(a, 9);
        parent.addView(bar, lp);
        ValueAnimator animator = ValueAnimator.ofArgb(base, glow, base);
        animator.setDuration(1250);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(v -> { if (bar.isAttachedToWindow()) shape.setColor((Integer) v.getAnimatedValue()); });
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { animator.start(); }
            public void onViewDetachedFromWindow(View v) { animator.cancel(); }
        });
    }
    private static int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
}
