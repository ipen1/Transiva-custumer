package com.transiva.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Status comes from the host Activity's existing order response. */
final class CustomerGameOrderHeader extends LinearLayout {
    private final TextView title, heading;
    private final RouteGlow glow;
    private final boolean motionEnabled;
    private String state = "", label = "";
    private boolean live = true;

    CustomerGameOrderHeader(Context context, Runnable back) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(12), dp(5), dp(8), dp(5));
        setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xff102e52, 0xff20477c, 0xff143957}));
        motionEnabled = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;

        glow = new RouteGlow(context);
        glow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(glow, new LayoutParams(dp(37), dp(40)));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(VERTICAL);
        heading = text("STATUS PESANAN", 9, 0xffb6d9ff);
        heading.setLetterSpacing(.11f);
        title = text("", 14, 0xfff4f9ff);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        copy.addView(heading);
        copy.addView(title);
        LayoutParams copyParams = new LayoutParams(0, -2, 1f);
        copyParams.setMarginStart(dp(8));
        addView(copy, copyParams);

        TextView button = text("Pesanan  ›", 12, 0xffeff9ff);
        button.setMinHeight(dp(48));
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Kembali ke pesanan");
        button.setBackground(selectableItemBackground());
        button.setOnClickListener(v -> back.run());
        addView(button, new LayoutParams(-2, -2));
    }

    void bind(String raw, String value) {
        String normalized = CustomerOrderState.normalize(raw);
        if (state.equals(normalized) && label.equals(value) && live) return;
        state = normalized;
        label = value == null || value.trim().isEmpty() ? "Memeriksa status pesanan…" : value;
        live = true;
        heading.setText("STATUS PESANAN");
        title.setText(label);
        glow.stage(PremiumOrderStatusView.step(state));
        if (motionEnabled && isShown()) {
            title.animate().cancel();
            title.setAlpha(.67f);
            title.animate().alpha(1f).setDuration(270).start();
        }
    }

    void reconnecting() {
        if (!live) return;
        live = false;
        heading.setText("MENYAMBUNG ULANG");
    }

    void setForeground(boolean value) {
        glow.setActive(value && motionEnabled);
        if (!value) {
            title.animate().cancel();
            title.setAlpha(1f);
        }
    }

    private android.graphics.drawable.Drawable selectableItemBackground() {
        android.util.TypedValue typed = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typed, true);
        return typed.resourceId == 0 ? null : getContext().getDrawable(typed.resourceId);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class RouteGlow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private ValueAnimator pulse;
        private int step = -1;
        private float phase;

        RouteGlow(Context context) { super(context); }

        void stage(int value) { step = value; invalidate(); }

        void setActive(boolean active) {
            if (!active && pulse != null) {
                pulse.cancel(); pulse = null; phase = 0; invalidate();
            } else if (active && pulse == null) {
                pulse = ValueAnimator.ofFloat(0, 1);
                pulse.setDuration(1600);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });
                pulse.start();
            }
        }

        @Override protected void onDetachedFromWindow() {
            setActive(false);
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 40f, getHeight() / 44f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff2d5d91);
            canvas.drawCircle(20f, 17f, 16f, paint);
            paint.setColor(0xff6ae5ce);
            paint.setAlpha((int) (120 * (1 - phase)));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f);
            canvas.drawCircle(20f, 17f, 13f + phase * 5f, paint);
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffa1f9e2);
            box.set(13f, 12f, 27f, 23f);
            canvas.drawRoundRect(box, 2f, 2f, paint);
            paint.setColor(0xff337693);
            box.set(19f, 12f, 21f, 22f);
            canvas.drawRect(box, paint);
            for (int i = 0; i < 6; i++) {
                float x = 2f + i * 6.1f;
                paint.setColor(i <= step ? 0xff7ce9d6 : 0xff44618b);
                box.set(x, 36f, x + 4.8f, 39f);
                canvas.drawRoundRect(box, 1.5f, 1.5f, paint);
            }
            canvas.restore();
        }
    }
}
