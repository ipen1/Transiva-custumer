package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;

import java.util.Locale;

/**
 * Reusable UI helper for the active loyalty-season badge.
 * PNG files are resolved dynamically so the app still compiles before the assets are added.
 */
public final class TierBadgeUi {
    private static final String PREF = "transiva_loyalty_ui";
    private static final String KEY_ACTIVE_TIER = "active_season_tier";

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
        } else {
            image.setImageDrawable(null);
            image.setContentDescription(normalize(tier) + " member");
        }
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
