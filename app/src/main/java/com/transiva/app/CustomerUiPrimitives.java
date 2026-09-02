package com.transiva.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import android.graphics.Typeface;

/** Small customer UI primitives shared by programmatic Activity layouts. */
public final class CustomerUiPrimitives {
    private CustomerUiPrimitives() {}

    public static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(String color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable roundStroke(Context context, String color, String stroke, int radiusPx, int widthDp) {
        GradientDrawable drawable = round(color, radiusPx);
        drawable.setStroke(dp(context, widthDp), Color.parseColor(stroke));
        return drawable;
    }

    public static GradientDrawable gradient(String start, String end, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(start), Color.parseColor(end)}
        );
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static TextView text(Context context, String value, int sp, String color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }
}
