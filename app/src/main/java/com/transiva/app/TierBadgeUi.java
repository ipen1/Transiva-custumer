package com.transiva.app;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Animatable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Reusable UI helper for the active loyalty-season badge.
 * PNG files are resolved dynamically so the app still compiles before the assets are added.
 */
public final class TierBadgeUi {
    private static final String PREF = "transiva_loyalty_ui";
    private static final String KEY_ACTIVE_TIER = "active_season_tier";
    private static final WeakHashMap<ImageView, Animator> SHINE_ANIMATORS = new WeakHashMap<>();

    private TierBadgeUi() {}

    public static String normalize(String tier) {
        String value = tier == null ? "" : tier.trim().toUpperCase(Locale.US);
        if ("SILVER".equals(value) || "GOLD".equals(value) || "DIAMOND".equals(value) || "PLATINUM".equals(value)) {
            return value;
        }
        return "BRONZE";
    }

    public static void saveActiveTier(Context context, String tier) {
        if (context == null) return;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_ACTIVE_TIER, normalize(tier)).apply();
    }

    public static String getCachedActiveTier(Context context) {
        if (context == null) return "BRONZE";
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return normalize(sp.getString(KEY_ACTIVE_TIER, "BRONZE"));
    }

    public static int drawableId(Context context, String tier) {
        if (context == null) return 0;
        String name = "tier_" + normalize(tier).toLowerCase(Locale.US);
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    public static ImageView image(Context context, String tier, int contentDescriptionResIgnored) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setAdjustViewBounds(true);
        applyToImage(image, tier);
        return image;
    }

    public static void applyToImage(ImageView image, String tier) {
        if (image == null) return;
        int id = drawableId(image.getContext(), tier);
        if (id != 0) {
            image.setImageResource(id);
            image.setContentDescription(normalize(tier) + " member badge");
            enableShine(image, tier);
        } else {
            image.setImageDrawable(null);
            image.setContentDescription(normalize(tier) + " member");
        }
    }

    /** Premium light pulse behind a tier PNG. Glow color follows Bronze/Silver/Gold/Diamond/Platinum. */
    public static void enableShine(ImageView image, String tier) {
        if (image == null) return;
        String normalized = normalize(tier);
        int glow = glowColor(normalized);
        try {
            GradientDrawable halo = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{withAlpha(glow, 20), withAlpha(glow, 135), withAlpha(glow, 12)}
            );
            halo.setShape(GradientDrawable.OVAL);
            image.setBackground(halo);
            image.setPadding(dp(image.getContext(), 2), dp(image.getContext(), 2), dp(image.getContext(), 2), dp(image.getContext(), 2));

            synchronized (SHINE_ANIMATORS) {
                Animator old = SHINE_ANIMATORS.remove(image);
                if (old != null) old.cancel();
                ObjectAnimator sx = ObjectAnimator.ofFloat(image, "scaleX", 1f, 1.055f, 1f);
                ObjectAnimator sy = ObjectAnimator.ofFloat(image, "scaleY", 1f, 1.055f, 1f);
                ObjectAnimator fade = ObjectAnimator.ofFloat(image, "alpha", 1f, .86f, 1f);
                sx.setRepeatCount(ObjectAnimator.INFINITE); sy.setRepeatCount(ObjectAnimator.INFINITE); fade.setRepeatCount(ObjectAnimator.INFINITE);
                sx.setDuration(1800L); sy.setDuration(1800L); fade.setDuration(1800L);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(sx, sy, fade);
                set.setStartDelay(180L);
                set.start();
                SHINE_ANIMATORS.put(image, set);
            }
        } catch (Throwable ignored) {
            // Decorative only: never let animation affect loyalty functionality.
        }
    }

    private static int glowColor(String tier) {
        switch (normalize(tier)) {
            case "SILVER": return Color.parseColor("#DCE7F2");
            case "GOLD": return Color.parseColor("#FFD54A");
            case "DIAMOND": return Color.parseColor("#66E0FF");
            case "PLATINUM": return Color.parseColor("#8BD3FF");
            default: return Color.parseColor("#E8A36F");
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Show only a compact indeterminate spinner inside the button while Hemat status is checked. */
    public static void showSpinner(Button button, int sizePx) {
        if (button == null) return;
        try {
            ProgressBar progress = new ProgressBar(button.getContext(), null, android.R.attr.progressBarStyleSmall);
            Drawable d = progress.getIndeterminateDrawable();
            if (d != null) {
                d = d.mutate();
                d.setBounds(0, 0, sizePx, sizePx);
                d.setCallback(button);
                button.setText("");
                button.setCompoundDrawables(d, null, null, null);
                button.setCompoundDrawablePadding(0);
                button.setGravity(Gravity.CENTER);
                if (d instanceof Animatable) ((Animatable) d).start();
                return;
            }
        } catch (Exception ignored) {}
        // Fail-safe: keep the button clean even on devices whose progress drawable cannot be reused.
        button.setText("");
        button.setCompoundDrawables(null, null, null, null);
        button.setGravity(Gravity.CENTER);
    }

    /** Restore the normal Hemat label and the cached/current tier badge. */
    public static void restoreHematButton(Button button, String tier, int sizePx, int gapPx) {
        if (button == null) return;
        button.setText("Hemat");
        applyToButton(button, tier, sizePx, gapPx);
    }

    /** Add the tier PNG to the left side of a Hemat button. */
    public static void applyToButton(Button button, String tier, int sizePx, int gapPx) {
        if (button == null) return;
        int id = drawableId(button.getContext(), tier);
        if (id == 0) {
            button.setCompoundDrawables(null, null, null, null);
            return;
        }
        Drawable d;
        try {
            d = button.getContext().getResources().getDrawable(id, button.getContext().getTheme());
        } catch (Exception e) {
            d = button.getContext().getResources().getDrawable(id);
        }
        d.setBounds(0, 0, sizePx, sizePx);
        button.setCompoundDrawables(d, null, null, null);
        button.setCompoundDrawablePadding(gapPx);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Transiva Hemat • " + normalize(tier) + " member");
    }
}
