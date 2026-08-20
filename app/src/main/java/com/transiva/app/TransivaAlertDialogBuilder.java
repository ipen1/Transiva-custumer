package com.transiva.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Drop-in replacement for AlertDialog.Builder with Transiva blue styling.
 * Existing dialog code can keep using setTitle/setMessage/setPositiveButton/etc.
 */
public class TransivaAlertDialogBuilder extends AlertDialog.Builder {

    private final Context context;

    public TransivaAlertDialogBuilder(Context context) {
        super(context);
        this.context = context;
    }

    public TransivaAlertDialogBuilder(Context context, int themeResId) {
        super(context, themeResId);
        this.context = context;
    }

    @Override
    public AlertDialog create() {
        final AlertDialog dialog = super.create();
        dialog.setOnShowListener(d -> applyTransivaStyle(dialog));
        return dialog;
    }

    private void applyTransivaStyle(AlertDialog dialog) {
        try {
            Window window = dialog.getWindow();
            if (window != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.WHITE);
                bg.setCornerRadius(dp(20));
                window.setBackgroundDrawable(bg);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = 0.58f;
                window.setAttributes(lp);
                window.setLayout((int) (context.getResources().getDisplayMetrics().widthPixels * 0.90f),
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }

            int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
            TextView title = titleId != 0 ? dialog.findViewById(titleId) : null;
            if (title != null) {
                title.setTextColor(Color.rgb(11, 54, 117));
                title.setTextSize(20f);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }

            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(Color.rgb(55, 65, 81));
                message.setTextSize(16f);
                message.setLineSpacing(0f, 1.12f);
            }

            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            Button negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            Button neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);

            styleButton(positive, Color.rgb(22, 119, 255), Color.WHITE);
            styleButton(negative, Color.rgb(234, 244, 255), Color.rgb(11, 54, 117));
            styleButton(neutral, Color.rgb(242, 247, 255), Color.rgb(22, 119, 255));

            makeButtonsBalanced(positive, negative, neutral);
        } catch (Throwable ignored) {
            // Styling must never break a business-critical confirmation dialog.
        }
    }

    private void styleButton(Button button, int backgroundColor, int textColor) {
        if (button == null || button.getVisibility() != View.VISIBLE) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(backgroundColor);
        bg.setCornerRadius(dp(12));
        button.setBackground(bg);
        button.setTextColor(textColor);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        // Cegah teks tombol pecah per huruf seperti "Gun\nakan" / "Bata\nl".
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
    }

    private void makeButtonsBalanced(Button... buttons) {
        LinearLayout parent = null;
        int visible = 0;
        for (Button b : buttons) {
            if (b != null && b.getVisibility() == View.VISIBLE) {
                visible++;
                if (b.getParent() instanceof LinearLayout) {
                    parent = (LinearLayout) b.getParent();
                }
            }
        }
        if (parent == null || visible == 0) return;

        parent.setGravity(Gravity.CENTER);
        parent.setPadding(dp(14), dp(6), dp(14), dp(16));

        // Tiga tombol pada AlertDialog bawaan terlalu sempit di banyak device
        // (terutama font/display scale besar). Susun vertikal agar selalu terbaca utuh.
        final boolean stack = visible >= 3;
        parent.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        for (Button b : buttons) {
            if (b == null || b.getVisibility() != View.VISIBLE || b.getParent() != parent) continue;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    stack ? ViewGroup.LayoutParams.MATCH_PARENT : 0, dp(48));
            p.weight = stack ? 0f : 1f;
            p.setMargins(dp(6), dp(5), dp(6), 0);
            b.setLayoutParams(p);
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
