package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;

/**
 * Navigasi premium untuk lima halaman utama customer.
 *
 * Index:
 * 0 Beranda
 * 1 Aktivitas
 * 2 Pesan
 * 3 Transaksi
 * 4 Akun
 */
public final class CustomerPageTransition {

    public static final int HOME = 0;
    public static final int ACTIVITY = 1;
    public static final int CHAT = 2;
    public static final int WALLET = 3;
    public static final int PROFILE = 4;

    private static final long CLICK_GUARD_MS = 450L;
    private static long lastNavigationAt;

    private CustomerPageTransition() {
    }

    public static void open(
            Activity activity,
            Class<?> target,
            int fromIndex,
            int toIndex
    ) {
        if (
                activity == null
                        || target == null
                        || fromIndex == toIndex
        ) {
            return;
        }

        long now = SystemClock.elapsedRealtime();

        if (now - lastNavigationAt < CLICK_GUARD_MS) {
            return;
        }

        lastNavigationAt = now;

        Intent intent = new Intent(activity, target);

        /*
         * Menggunakan kembali activity yang sudah ada agar perpindahan
         * bottom navigation tidak menumpuk banyak halaman.
         */
        intent.addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        activity.startActivity(intent);

        boolean moveRight = toIndex > fromIndex;

        activity.overridePendingTransition(
                moveRight
                        ? R.anim.transiva_page_enter_right
                        : R.anim.transiva_page_enter_left,
                moveRight
                        ? R.anim.transiva_page_exit_left
                        : R.anim.transiva_page_exit_right
        );
    }

    /**
     * Animasi halus ketika activity yang sudah ada kembali ke depan.
     */
    public static void animateResume(
            Activity activity,
            View root
    ) {
        if (activity == null || root == null) {
            return;
        }

        root.setAlpha(0.96f);
        root.setTranslationY(dp(activity, 5));

        root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(
                        new android.view.animation.DecelerateInterpolator(
                                1.6f
                        )
                )
                .start();
    }

    public static void finishWithBackAnimation(
            Activity activity,
            int fromIndex,
            int toIndex
    ) {
        if (activity == null) {
            return;
        }

        activity.finish();

        boolean moveRight = toIndex > fromIndex;

        activity.overridePendingTransition(
                moveRight
                        ? R.anim.transiva_page_enter_right
                        : R.anim.transiva_page_enter_left,
                moveRight
                        ? R.anim.transiva_page_exit_left
                        : R.anim.transiva_page_exit_right
        );
    }

    private static int dp(
            Activity activity,
            int value
    ) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
