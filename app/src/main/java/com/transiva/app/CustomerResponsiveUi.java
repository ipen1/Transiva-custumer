package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;

/** Central responsive-device policy for customer screens. */
public final class CustomerResponsiveUi {
    public enum Profile { COMPACT, SMALL, NORMAL, LARGE, TABLET }
    private CustomerResponsiveUi() {}

    public static Profile profile(Context context) {
        int sw = context.getResources().getConfiguration().screenWidthDp;
        if (sw <= 0) sw = Math.round(context.getResources().getDisplayMetrics().widthPixels / context.getResources().getDisplayMetrics().density);
        if (sw <= 320) return Profile.COMPACT;
        if (sw <= 359) return Profile.SMALL;
        if (sw <= 411) return Profile.NORMAL;
        if (sw <= 599) return Profile.LARGE;
        return Profile.TABLET;
    }

    public static int dp(Context c, float dp) { return Math.round(dp * c.getResources().getDisplayMetrics().density); }
    public static float scale(Context c) {
        switch (profile(c)) {
            case COMPACT: return .86f;
            case SMALL: return .92f;
            case LARGE: return 1.06f;
            case TABLET: return 1.16f;
            default: return 1f;
        }
    }
    public static int adaptiveDp(Context c, int normalDp) { return Math.max(1, Math.round(normalDp * scale(c))); }

    public static void apply(Activity activity) {
        if (activity == null) return;
        CustomerWindowPolicy.install(activity);
        View root = activity.findViewById(android.R.id.content);
        if (root != null) root.post(() -> tuneTree(activity, root));
    }

    private static void tuneTree(Context c, View v) {
        if (v instanceof TextView) {
            TextView t=(TextView)v;
            float fs=c.getResources().getConfiguration().fontScale;
            if (fs > 1.30f) t.setMaxLines(Math.max(t.getMaxLines(), 2));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) tuneTree(c,g.getChildAt(i));
        }
    }
}