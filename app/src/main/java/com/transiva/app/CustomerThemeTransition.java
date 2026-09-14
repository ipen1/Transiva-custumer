package com.transiva.app;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.WeakHashMap;

/**
 * Smooth theme hand-off for the programmatic Customer UI.
 *
 * The previous circular reveal painted a full-screen target-colour layer and then recreated
 * the Activity. On some devices this produced a visible flash / double transition while the
 * view tree was also being recoloured by CustomerAppSettings. This implementation performs
 * one short fade-out, recreates once, then lets playEnter() fade the newly themed content in.
 */
public final class CustomerThemeTransition {
    private static final long EXIT_MS = 135L;
    private static final long ENTER_MS = 210L;
    private static final WeakHashMap<Activity, Boolean> SWITCHING = new WeakHashMap<>();

    private CustomerThemeTransition() {}

    public static void switchTheme(Activity activity, boolean dark) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        if (CustomerAppSettings.isDarkMode(activity) == dark) return;

        synchronized (SWITCHING) {
            if (Boolean.TRUE.equals(SWITCHING.get(activity))) return;
            SWITCHING.put(activity, true);
        }

        final View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            commitAndRecreate(activity, dark);
            return;
        }

        content.animate().cancel();
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        content.animate()
                .alpha(0.78f)
                .translationY(dp(activity, 2))
                .setDuration(EXIT_MS)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .withEndAction(() -> {
                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                    commitAndRecreate(activity, dark);
                })
                .start();
    }

    private static void commitAndRecreate(Activity activity, boolean dark) {
        if (activity == null || activity.isFinishing()) return;
        CustomerAppSettings.setDarkMode(activity, dark);
        try {
            activity.recreate();
            // Keep the system window hand-off subtle. The content itself is animated by playEnter().
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Throwable ignored) {
            synchronized (SWITCHING) { SWITCHING.remove(activity); }
        }
    }


    /** Smoothly refresh an Activity that was already in the back stack when theme changed. */
    public static void refreshAppliedTheme(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            activity.recreate();
            return;
        }
        content.animate().cancel();
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        content.animate().alpha(0.82f).setDuration(95L)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (!activity.isFinishing()) {
                        activity.recreate();
                        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }).start();
    }

    public static void playEnter(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        content.animate().cancel();
        content.setAlpha(0.80f);
        content.setTranslationY(dp(activity, 2));
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ENTER_MS)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .withEndAction(() -> {
                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                    synchronized (SWITCHING) { SWITCHING.remove(activity); }
                })
                .start();
    }

    private static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
