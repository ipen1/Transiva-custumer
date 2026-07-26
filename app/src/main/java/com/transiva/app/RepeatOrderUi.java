package com.transiva.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.TextView;

public final class RepeatOrderUi {

    private RepeatOrderUi() {
    }

    public static TextView text(
            Context context,
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    public static Button primary(
            Context context,
            String value,
            int radius
    ) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(
                gradient(context, "#086BFF", "#2EA2FF", radius)
        );

        return button;
    }

    public static Button outline(
            Context context,
            String value,
            int radius
    ) {
        Button button = new Button(context);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.parseColor("#0B7CFF"));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(
                roundStroke(
                        context,
                        "#FFFFFF",
                        "#B9DBFF",
                        radius,
                        1
                )
        );

        return button;
    }

    public static GradientDrawable round(
            Context context,
            String fill,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(context, radius));

        return drawable;
    }

    public static GradientDrawable roundStroke(
            Context context,
            String fill,
            String stroke,
            int radius,
            int strokeWidth
    ) {
        GradientDrawable drawable =
                round(context, fill, radius);

        drawable.setStroke(
                dp(context, strokeWidth),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    public static GradientDrawable gradient(
            Context context,
            String start,
            String end,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(dp(context, radius));

        return drawable;
    }

    public static int dp(
            Context context,
            int value
    ) {
        return Math.round(
                value
                        * context.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
