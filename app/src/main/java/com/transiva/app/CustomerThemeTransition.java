package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Premium circular theme transition without changing existing page routing. */
public final class CustomerThemeTransition {
    private CustomerThemeTransition() {}

    public static void switchTheme(Activity activity, boolean dark) {
        if (activity == null || activity.isFinishing()) return;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        final View overlay = new View(activity);
        overlay.setBackgroundColor(Color.parseColor(dark ? "#07111F" : "#F5F8FD"));
        overlay.setClickable(true);
        decor.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.post(() -> {
            int cx = Math.max(1, overlay.getWidth() - dp(activity, 42));
            int cy = dp(activity, 88);
            float end = (float) Math.hypot(Math.max(cx, overlay.getWidth() - cx),
                    Math.max(cy, overlay.getHeight() - cy));
            try {
                android.animation.Animator reveal = ViewAnimationUtils.createCircularReveal(overlay, cx, cy, 0f, end);
                reveal.setDuration(420L);
                reveal.setInterpolator(new DecelerateInterpolator(1.8f));
                reveal.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        CustomerAppSettings.setDarkMode(activity, dark);
                        activity.recreate();
                    }
                });
                reveal.start();
            } catch (Throwable ignored) {
                CustomerAppSettings.setDarkMode(activity, dark);
                activity.recreate();
            }
        });
    }

    public static void playEnter(Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        content.setAlpha(0f);
        content.setScaleX(0.992f);
        content.setScaleY(0.992f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
