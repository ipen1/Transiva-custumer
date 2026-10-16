package com.transiva.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** One inset owner per activity window. No Activity or Window is kept in a static collection. */
public final class CustomerWindowPolicy {
    private CustomerWindowPolicy() {}
    // Retain compatibility with the few older screen appearance expressions during migration.
    public static final int LIGHT_STATUS = 8192, LIGHT_NAVIGATION = 16;

    private static final class State {
        int topColor = 0xff0b7cff, bottomColor = 0xff071426;
        Insets bars = Insets.NONE;
        boolean installed, pip;
        int left, top, right, bottom;
        final BarProtection protection = new BarProtection(this);
    }
    private static State state(Window window) {
        View decor = window.getDecorView();
        Object old = decor.getTag(R.id.transiva_window_policy);
        if (old instanceof State) return (State) old;
        State state = new State();
        decor.setTag(R.id.transiva_window_policy, state);
        return state;
    }
    public static void install(Activity activity) {
        install(activity.getWindow(), Build.VERSION.SDK_INT >= 26 && activity.isInPictureInPictureMode());
    }
    public static void install(Window window) { install(window, false); }
    private static void install(Window window, boolean pip) {
        State state = state(window);
        state.pip = pip;
        View decor = window.getDecorView();
        View content = window.findViewById(android.R.id.content);
        if (content == null) return;
        if (!state.installed) {
            state.installed = true;
            // targetSdk 36: Android 15+ enforces edge-to-edge for non-floating
            // windows. Older platforms and floating fullscreen dialogs still
            // need the compatibility bridge (including its legacy color APIs).
            if (Build.VERSION.SDK_INT < 35 || window.isFloating())
                WindowCompat.enableEdgeToEdge(window);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            state.left = content.getPaddingLeft(); state.top = content.getPaddingTop();
            state.right = content.getPaddingRight(); state.bottom = content.getPaddingBottom();
            decor.getOverlay().add(state.protection);
            decor.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                state.protection.setBounds(0, 0, r-l, b-t);
                if (r-l != or-ol || b-t != ob-ot) ViewCompat.requestApplyInsets(content);
            });
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                state.bars = state.pip ? Insets.NONE : bars;
                int l = state.pip ? 0 : state.left + bars.left;
                int t = state.pip ? 0 : state.top + bars.top;
                int r = state.pip ? 0 : state.right + bars.right;
                int b = state.pip ? 0 : state.bottom + Math.max(bars.bottom, ime.bottom);
                if (v.getPaddingLeft()!=l || v.getPaddingTop()!=t || v.getPaddingRight()!=r || v.getPaddingBottom()!=b)
                    v.setPadding(l,t,r,b);
                state.protection.setBounds(0,0,decor.getWidth(),decor.getHeight());
                state.protection.invalidateSelf();
                // Descendants receive zero consumed insets: no duplicate padding in old views.
                return WindowInsetsCompat.CONSUMED;
            });
        }
        applyIcons(window, state);
        ViewCompat.requestApplyInsets(content);
    }
    public static void status(Window window, int color) {
        State state = state(window); state.topColor = color;
        state.protection.invalidateSelf(); applyIcons(window,state);
    }
    public static void navigation(Window window, int color) {
        State state = state(window); state.bottomColor = color;
        state.protection.invalidateSelf(); applyIcons(window,state);
    }
    private static void applyIcons(Window window, State state) {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(window,window.getDecorView());
        c.setAppearanceLightStatusBars(ColorUtils.calculateLuminance(state.topColor) > .5);
        c.setAppearanceLightNavigationBars(ColorUtils.calculateLuminance(state.bottomColor) > .5);
    }
    public static int appearance(Window window) {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(window,window.getDecorView());
        return (c.isAppearanceLightStatusBars()?LIGHT_STATUS:0)
                | (c.isAppearanceLightNavigationBars()?LIGHT_NAVIGATION:0);
    }
    public static void appearance(Window window, int flags) {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(window,window.getDecorView());
        c.setAppearanceLightStatusBars((flags & LIGHT_STATUS)!=0);
        c.setAppearanceLightNavigationBars((flags & LIGHT_NAVIGATION)!=0);
    }
    /** Draws only behind the system bars; does not add views or intercept any touches. */
    private static final class BarProtection extends Drawable {
        final State state; final Paint paint = new Paint();
        BarProtection(State state) { this.state = state; }
        @Override public void draw(Canvas canvas) {
            if (state.pip) return;
            int w=getBounds().width(), h=getBounds().height(); Insets b=state.bars;
            paint.setColor(state.topColor); canvas.drawRect(0,0,w,b.top,paint);
            paint.setColor(state.bottomColor);
            canvas.drawRect(0,h-b.bottom,w,h,paint);
            canvas.drawRect(0,b.top,b.left,h-b.bottom,paint);
            canvas.drawRect(w-b.right,b.top,w,h-b.bottom,paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
