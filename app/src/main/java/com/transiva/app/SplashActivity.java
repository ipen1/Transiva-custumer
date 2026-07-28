package com.transiva.app;

import android.app.Activity;
import android.provider.Settings;
import android.os.Build;
import android.net.Uri;
import android.app.AlertDialog;
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
    private boolean overlaySettingsOpened;
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

        new Handler(Looper.getMainLooper()).postDelayed(this::checkOverlayThenRoute, SPLASH_DELAY);
    }

    private void checkOverlayThenRoute() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            routeNext();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Izinkan tampil di atas aplikasi lain")
                .setMessage("Izin ini membantu layar panggilan Transiva langsung muncul saat ada telepon masuk, termasuk ketika Anda sedang membuka aplikasi lain. Aktifkan 'Tampil di atas aplikasi lain' untuk Transiva.")
                .setCancelable(false)
                .setPositiveButton("Buka pengaturan", (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        overlaySettingsOpened = true;
                        startActivity(i);
                    } catch (Throwable ignored) {
                        routeNext();
                    }
                })
                .setNegativeButton("Nanti", (d, w) -> routeNext())
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (overlaySettingsOpened && !routed) {
            overlaySettingsOpened = false;
            new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, 250L);
        }
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
