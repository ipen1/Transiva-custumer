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
        // Do not consume OnShowListener: forms use it to validate without dismissing.
        // Attach occurs before native AlertController has finished its first layout.
        // Run once just before drawing, after caller OnShowListener customizations.
        Window themedWindow=dialog.getWindow();
        if(themedWindow!=null)themedWindow.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){
                view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                    @Override public boolean onPreDraw(){
                        if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(this);
                        if(dialog.isShowing()){applyTransivaStyle(dialog);return false;}
                        return true;
                    }
                });
            }
            @Override public void onViewDetachedFromWindow(View view){}
        });
        return dialog;
    }

    private void applyTransivaStyle(AlertDialog dialog) {
        try {
            Window window = dialog.getWindow();
            if (window != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.TRANSPARENT);
                bg.setCornerRadius(dp(20));
                window.setBackgroundDrawable(bg);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = 0.58f;
                window.setAttributes(lp);
                // Use the containing window, including split-screen, rather than physical display size.
                android.graphics.Rect available=new android.graphics.Rect();
                window.getDecorView().getWindowVisibleDisplayFrame(available);
                int width=available.width()>0?available.width():context.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(Math.min(dp(560),Math.max(1,width-dp(32))),
                        WindowManager.LayoutParams.WRAP_CONTENT);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
                if (CustomerErrorMessages.containsTechnicalDetails(message.getText().toString()))
                    message.setText(CustomerErrorMessages.humanize(message.getText().toString(), "Layanan sedang mengalami kendala. Silakan coba lagi sebentar."));
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
            // Theme only dialog content. Replacing DecorView backgrounds can disturb native windows.
            View content=dialog.findViewById(android.R.id.content);
            if(content!=null){
                GradientDrawable panel=new GradientDrawable();panel.setColor(Color.WHITE);panel.setCornerRadius(dp(20));
                content.setBackground(panel);
                CustomerAppSettings.applyToView(context,content);
            }
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
        button.setSingleLine(false);
        button.setMaxLines(2);
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
        final boolean stack = visible >= 3
                || context.getResources().getConfiguration().fontScale > 1.15f
                || context.getResources().getDisplayMetrics().widthPixels < dp(360);
        parent.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        for (Button b : buttons) {
            if (b == null || b.getVisibility() != View.VISIBLE || b.getParent() != parent) continue;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    stack ? ViewGroup.LayoutParams.MATCH_PARENT : 0, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.weight = stack ? 0f : 1f;
            p.setMargins(dp(6), dp(5), dp(6), 0);
            b.setLayoutParams(p);
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
