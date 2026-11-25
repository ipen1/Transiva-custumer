package com.transiva.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Pull refresh draws its indicator above the scrollable page. */
public final class CustomerPullRefreshLayout extends FrameLayout implements CustomerAppSettings.ThemeAware {
    public interface Busy { boolean get(); }

    private static final int REFRESH_ACTION = 0x0f000001;
    private final ScrollView scroll;
    private final Runnable action;
    private final Busy busy;
    private final FrameLayout badge;
    private final ImageView arrow;
    private final ProgressBar spinner;
    private final TextView result;
    private final GradientDrawable badgeBackground = new GradientDrawable();
    private final int slop;
    private final int threshold;
    private float startX, startY, pull;
    private boolean dragging, blocked, refreshing;
    private Boolean darkTheme;
    private final Runnable reset = () -> hide(true);
    private Runnable timeoutAction = () -> { };
    private final Runnable timeout = () -> {
        try { timeoutAction.run(); }
        finally { finish(false); }
    };

    public CustomerPullRefreshLayout(Activity activity, ScrollView child, Runnable action, Busy busy) {
        super(activity);
        scroll = child;
        this.action = action;
        this.busy = busy;
        slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        threshold = dp(64);
        addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        badge = new FrameLayout(activity);
        badgeBackground.setShape(GradientDrawable.OVAL);
        badge.setBackground(badgeBackground);
        badge.setElevation(dp(7));
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        CustomerAppSettings.preservePalette(badge);

        arrow = new ImageView(activity);
        arrow.setImageResource(R.drawable.ic_customer_pull_refresh);
        badge.addView(arrow, new LayoutParams(dp(22), dp(22), Gravity.CENTER));

        spinner = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        badge.addView(spinner, new LayoutParams(dp(24), dp(24), Gravity.CENTER));

        result = new TextView(activity);
        result.setGravity(Gravity.CENTER);
        result.setTextSize(22);
        result.setTypeface(Typeface.DEFAULT_BOLD);
        CustomerAppSettings.preservePalette(result);
        badge.addView(result, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(badge, new LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        onCustomerThemeChanged(CustomerAppSettings.isDarkMode(activity));
        hide(false);

        child.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(REFRESH_ACTION, "Perbarui data"));
            }

            @Override public boolean performAccessibilityAction(View host, int id, Bundle args) {
                if (id == REFRESH_ACTION) {
                    requestRefresh();
                    return true;
                }
                return super.performAccessibilityAction(host, id, args);
            }
        });
    }

    @Override public void onCustomerThemeChanged(boolean dark) {
        if (darkTheme != null && darkTheme == dark) return;
        darkTheme = dark;
        badgeBackground.setColor(dark ? 0xff17283d : 0xffffffff);
        badgeBackground.setStroke(dp(1), dark ? 0xff365579 : 0xffcbe4ff);
        ColorStateList accent = ColorStateList.valueOf(dark ? 0xff8cc5ff : 0xff0b65d8);
        arrow.setImageTintList(accent);
        spinner.setIndeterminateTintList(accent);
    }

    public void setTimeoutAction(Runnable action) {
        timeoutAction = action == null ? () -> { } : action;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean scrollUpAt(View view, float x, float y) {
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains((int) x, (int) y)) return false;
        if (view.canScrollVertically(-1)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--)
                if (scrollUpAt(group.getChildAt(i), x, y)) return true;
        }
        return false;
    }

    private void drawPull(float fingerY) {
        pull = Math.min(dp(100), Math.max(0, (fingerY - startY) * .45f));
        float progress = Math.min(1f, pull / threshold);
        badge.animate().cancel();
        arrow.setVisibility(VISIBLE);
        spinner.setVisibility(GONE);
        result.setVisibility(GONE);
        badge.setVisibility(VISIBLE);
        badge.setTranslationY(-dp(44) + dp(62) * progress + Math.max(0f, pull - threshold) * .35f);
        badge.setAlpha(progress);
        badge.setScaleX(.78f + .22f * progress);
        badge.setScaleY(.78f + .22f * progress);
        arrow.setRotation(270f * progress);
    }

    private void hide(boolean animated) {
        badge.animate().cancel();
        pull = 0;
        if (animated && badge.getVisibility() == VISIBLE) {
            badge.animate().translationY(-dp(44)).alpha(0f).setDuration(170)
                    .withEndAction(() -> badge.setVisibility(INVISIBLE)).start();
        } else {
            badge.setVisibility(INVISIBLE);
            badge.setTranslationY(-dp(44));
            badge.setAlpha(0f);
        }
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    public void requestRefresh() {
        if (refreshing) return;
        if (busy.get()) {
            hide(true);
            Toast.makeText(getContext(), "Tunggu proses yang sedang berjalan", Toast.LENGTH_SHORT).show();
            return;
        }
        refreshing = true;
        removeCallbacks(reset);
        badge.animate().cancel();
        arrow.setVisibility(GONE);
        result.setVisibility(GONE);
        spinner.setVisibility(VISIBLE);
        badge.setVisibility(VISIBLE);
        badge.animate().translationY(dp(18)).alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start();
        announceForAccessibility("Memperbarui data");
        removeCallbacks(timeout);
        postDelayed(timeout, 45000);
        try { action.run(); }
        catch (RuntimeException error) { finish(false); }
    }

    public void finish(boolean success) {
        if (!refreshing) return;
        refreshing = false;
        removeCallbacks(timeout);
        spinner.setVisibility(GONE);
        result.setText(success ? "✓" : "!");
        result.setTextColor(success
                ? (Boolean.TRUE.equals(darkTheme) ? 0xff6ee7b7 : 0xff08744d)
                : (Boolean.TRUE.equals(darkTheme) ? 0xfffecaca : 0xffb91c1c));
        result.setVisibility(VISIBLE);
        announceForAccessibility(success ? "Data sudah diperbarui" : "Belum diperbarui. Coba lagi.");
        postDelayed(reset, success ? 500 : 1600);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(reset);
                if (!refreshing) hide(false);
                startX = event.getX();
                startY = event.getY();
                dragging = false;
                blocked = refreshing || busy.get() || scroll.canScrollVertically(-1)
                        || scrollUpAt(scroll, event.getRawX(), event.getRawY());
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                blocked = true;
                return dragging;
            case MotionEvent.ACTION_MOVE:
                if (!blocked) {
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                        blocked = true;
                    } else if (CustomerRefreshRules.begin(dx, dy, slop * .5f,
                            !scroll.canScrollVertically(-1), busy.get(), event.getPointerCount())) {
                        dragging = true;
                        drawPull(event.getY());
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return super.onTouchEvent(event);
        if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            hide(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            drawPull(event.getY());
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            drawPull(event.getY());
            dragging = false;
            if (CustomerRefreshRules.release(pull, threshold)) requestRefresh();
            else hide(true);
        }
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(reset);
        removeCallbacks(timeout);
        refreshing = false;
        dragging = false;
        hide(false);
        spinner.setVisibility(GONE);
        super.onDetachedFromWindow();
    }
}
