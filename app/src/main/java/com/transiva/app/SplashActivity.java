package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class SplashActivity extends Activity {
    private boolean routed;

    private static final int SPLASH_DELAY = 1600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.parseColor("#020617"));

        ImageView splash = new ImageView(this);
        int splashRes = getDrawableId("splash_screen");
        if (splashRes == 0) splashRes = getDrawableId("transiva_logo");
        if (splashRes == 0) splashRes = getDrawableId("logo_transiva");
        if (splashRes == 0) splashRes = getApplicationInfo().icon;

        splash.setImageResource(splashRes);
        splash.setScaleType(ScaleType.CENTER_CROP);

        layout.addView(
                splash,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(layout);

        // Never block first launch with the special overlay permission.
        // Incoming calls still use Android full-screen call notifications; users can
        // optionally enable overlay from Pengaturan Aplikasi.
        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, SPLASH_DELAY);
    }

    private void routeNext() {
        if (routed) return;
        routed = true;
        SessionManager session = new SessionManager(this);
        Intent intent;

        if (!session.isLoggedIn()) {
            intent = new Intent(this, LoginActivity.class);
        } else {
            intent = new Intent(this, PinActivity.class);
            intent.putExtra("native_role", "customer");
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private int getDrawableId(String name) {
        try {
            return getResources().getIdentifier(name, "drawable", getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }
}
