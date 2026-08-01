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
    private boolean securityCheckStarted;
    private static final int SPLASH_DELAY = 1200;

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
        layout.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(layout);
        new Handler(Looper.getMainLooper()).postDelayed(this::startSecurityCheck, SPLASH_DELAY);
    }

    private void startSecurityCheck() {
        if (routed || securityCheckStarted || isFinishing()) return;
        securityCheckStarted = true;
        RootSecurityGuard.checkBeforeContinue(this,
                () -> MockLocationGuard.checkBeforeContinue(this, this::routeNext));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // When returning from Developer options, run a fresh check.
        if (securityCheckStarted && !routed) {
            securityCheckStarted = false;
            new Handler(Looper.getMainLooper()).postDelayed(this::startSecurityCheck, 300L);
        }
    }

    private void routeNext() {
        if (routed || isFinishing()) return;
        routed = true;
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn() && ActiveOrderRecovery.route(this)) return;
        Intent intent = !session.isLoggedIn()
                ? new Intent(this, LoginActivity.class)
                : new Intent(this, PinActivity.class).putExtra("native_role", "customer");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int getDrawableId(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); }
        catch (Exception e) { return 0; }
    }
}
