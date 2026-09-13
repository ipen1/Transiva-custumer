package com.transiva.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Premium responsive bottom navigation untuk seluruh halaman utama customer.
 * Tidak mengubah destination/menu behavior lama, hanya visual, ukuran, dan touch feedback.
 */
public final class CustomerBottomNavigation {

    private static final String BLUE = "#0B7CFF";
    private static final String CYAN = "#22A4FF";

    private CustomerBottomNavigation() {}

    public static View build(Activity activity, int activeIndex) {
        boolean dark = isDark(activity);

        LinearLayout nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(activity, 7), dp(activity, 5), dp(activity, 7), dp(activity, 5));
        nav.setElevation(dp(activity, 14));
        nav.setBackground(navBackground(activity, dark));

        add(nav, item(activity, "Beranda", "ic_nav2_home", CustomerDashboardActivity.class,
                CustomerPageTransition.HOME, activeIndex, false, dark));
        add(nav, item(activity, "Aktivitas", "ic_nav2_activity", CustomerHistoryActivity.class,
                CustomerPageTransition.ACTIVITY, activeIndex, false, dark));
        add(nav, item(activity, "Pesan", "ic_nav2_chat", CustomerChatActivity.class,
                CustomerPageTransition.CHAT, activeIndex, true, dark));
        add(nav, item(activity, "Transaksi", "ic_nav2_wallet", CustomerBalanceHistoryActivity.class,
                CustomerPageTransition.WALLET, activeIndex, false, dark));
        add(nav, item(activity, "Akun", "ic_nav2_profile", ProfileActivity.class,
                CustomerPageTransition.PROFILE, activeIndex, false, dark));

        nav.setAlpha(0f);
        nav.setTranslationY(dp(activity, 12));
        nav.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(230L)
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
            int activeIndex,
            boolean center,
            boolean dark
    ) {
        boolean active = index == activeIndex;
        int inactiveText = Color.parseColor(dark ? "#D8E6F7" : "#60738A");

        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 1));
        item.setClickable(!active);
        item.setFocusable(!active);
        item.setContentDescription(label);

        if (active && !center) {
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Color.parseColor(dark ? "#253B69FF" : "#EAF4FF"));
            pill.setStroke(dp(activity, 1), Color.parseColor(dark ? "#5A7DCBFF" : "#B7D8FF"));
            pill.setCornerRadius(dp(activity, 18));
            item.setBackground(pill);
        }

        FrameLayout iconWrap = new FrameLayout(activity);
        int wrap = center ? dp(activity, 39) : dp(activity, 31);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(wrap, wrap);
        item.addView(iconWrap, wrapLp);

        if (center) {
            GradientDrawable centerBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor(BLUE), Color.parseColor(CYAN)}
            );
            centerBg.setShape(GradientDrawable.OVAL);
            centerBg.setStroke(dp(activity, active ? 2 : 1),
                    Color.parseColor(active ? "#A9D7FF" : "#65B8FF"));
            iconWrap.setBackground(centerBg);
            iconWrap.setElevation(dp(activity, active ? 8 : 5));
        } else if (active) {
            GradientDrawable activeCircle = new GradientDrawable();
            activeCircle.setShape(GradientDrawable.OVAL);
            activeCircle.setColor(Color.parseColor(dark ? "#1D5DAFFF" : "#D9ECFF"));
            iconWrap.setBackground(activeCircle);
        }

        ImageView icon = new ImageView(activity);
        int drawableId = activity.getResources().getIdentifier(iconName, "drawable", activity.getPackageName());
        if (drawableId != 0) icon.setImageResource(drawableId);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = center ? dp(activity, 9) : dp(activity, 6);
        icon.setPadding(pad, pad, pad, pad);
        if (center) {
            icon.setColorFilter(Color.WHITE);
        } else {
            icon.setColorFilter(Color.parseColor(active ? BLUE : (dark ? "#BFD5EC" : "#70859B")));
        }
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        iconWrap.addView(icon, iconLp);

        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(center ? 9.5f : 9f);
        title.setTextColor(center ? Color.parseColor(active ? "#39A8FF" : (dark ? "#F4F9FF" : "#26384B"))
                : Color.parseColor(active ? BLUE : (dark ? "#D8E6F7" : "#60738A")));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        if (active || center) title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(activity, 1), 0, 0);
        item.addView(title, titleLp);

        if (!active) {
            item.setOnClickListener(view -> {
                view.animate()
                        .scaleX(0.91f)
                        .scaleY(0.91f)
                        .setDuration(65L)
                        .withEndAction(() -> view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120L)
                                .setInterpolator(new DecelerateInterpolator(1.8f))
                                .withEndAction(() -> CustomerPageTransition.open(
                                        activity, target, activeIndex, index))
                                .start())
                        .start();
            });
        }

        return item;
    }

    private static GradientDrawable navBackground(Activity activity, boolean dark) {
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor(dark ? "#F2142940" : "#FFFFFFFF"),
                        Color.parseColor(dark ? "#F208182B" : "#F7FBFF")
                }
        );
        bg.setStroke(dp(activity, 1), Color.parseColor(dark ? "#355A83A8" : "#D7E8F8"));
        bg.setCornerRadii(new float[]{
                dp(activity, 22), dp(activity, 22),
                dp(activity, 22), dp(activity, 22),
                0, 0, 0, 0
        });
        return bg;
    }

    private static boolean isDark(Activity activity) {
        int mode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
