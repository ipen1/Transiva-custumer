package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Satu-satunya sumber bottom navigation untuk seluruh halaman utama customer.
 * Visual dan animasi mengikuti CustomerDashboardActivity sebagai patokan.
 */
public final class CustomerBottomNavigation {

    private static final String ACTIVE_BG = "#EAF4FF";
    private static final String ACTIVE_COLOR = "#0B7CFF";
    private static final String INACTIVE_COLOR = "#64748B";

    private CustomerBottomNavigation() {
    }

    public static View build(Activity activity, int activeIndex) {
        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 5), dp(activity, 4));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(activity, 8));

        add(nav, item(activity, "Beranda", "ic_nav_home", CustomerDashboardActivity.class,
                CustomerPageTransition.HOME, activeIndex));
        add(nav, item(activity, "Aktivitas", "ic_nav_activity", CustomerHistoryActivity.class,
                CustomerPageTransition.ACTIVITY, activeIndex));
        add(nav, item(activity, "Pesan", "ic_nav_chat", CustomerChatActivity.class,
                CustomerPageTransition.CHAT, activeIndex));
        add(nav, item(activity, "Transaksi", "ic_nav_wallet", CustomerBalanceHistoryActivity.class,
                CustomerPageTransition.WALLET, activeIndex));
        add(nav, item(activity, "Akun", "ic_nav_profile", ProfileActivity.class,
                CustomerPageTransition.PROFILE, activeIndex));

        nav.setAlpha(0f);
        nav.setTranslationY(dp(activity, 10));
        nav.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        return nav;
    }

    private static void add(LinearLayout nav, View item) {
        nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private static View item(
            Activity activity,
            String label,
            String iconName,
            Class<?> target,
            int index,
            int activeIndex
    ) {
        boolean active = index == activeIndex;

        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
        item.setClickable(!active);
        item.setFocusable(!active);

        if (active) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor(ACTIVE_BG));
            bg.setCornerRadius(dp(activity, 18));
            item.setBackground(bg);
            item.setScaleX(1.02f);
            item.setScaleY(1.02f);
        }

        ImageView icon = new ImageView(activity);
        int drawableId = activity.getResources().getIdentifier(
                iconName, "drawable", activity.getPackageName());
        if (drawableId != 0) {
            icon.setImageResource(drawableId);
        }
        icon.setAlpha(active ? 1f : 0.62f);
        item.addView(icon, new LinearLayout.LayoutParams(dp(activity, 22), dp(activity, 22)));

        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(9f);
        title.setTextColor(Color.parseColor(active ? ACTIVE_COLOR : INACTIVE_COLOR));
        title.setGravity(Gravity.CENTER);
        if (active) {
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        }

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(activity, 2), 0, 0);
        item.addView(title, titleLp);

        if (!active) {
            item.setOnClickListener(view -> {
                view.animate()
                        .scaleX(0.90f)
                        .scaleY(0.90f)
                        .setDuration(70L)
                        .withEndAction(() -> view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(110L)
                                .setInterpolator(new DecelerateInterpolator(1.7f))
                                .withEndAction(() -> CustomerPageTransition.open(
                                        activity, target, activeIndex, index))
                                .start())
                        .start();
            });
        }

        return item;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
