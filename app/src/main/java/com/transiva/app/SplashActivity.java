package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Splash Customer: security check + update gate + pemulihan order aktif. */
public class SplashActivity extends Activity {
    private boolean routed;
    private boolean securityCheckStarted;
    private boolean updateChecking;
    private static final int SPLASH_DELAY = 80;
    private TextView statusText;

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
        splash.setScaleType(ImageView.ScaleType.CENTER_CROP);
        layout.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = new TextView(this);
        statusText.setText("Memeriksa keamanan aplikasi...");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(13f);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(24, 12, 24, 12);
        statusText.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(28, 0, 28, 36);
        layout.addView(statusText, lp);

        setContentView(layout);
        new Handler(Looper.getMainLooper()).postDelayed(this::startSecurityCheck, SPLASH_DELAY);
    }

    private void startSecurityCheck() {
        if (routed || securityCheckStarted || updateChecking || isFinishing()) return;
        securityCheckStarted = true;
        statusText.setText("Memeriksa keamanan perangkat...");
        CustomerStartupSecurityGate.check(this, () -> {
            securityCheckStarted = false;
            checkAppUpdate();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Saat kembali dari pengaturan/installer, jalankan pemeriksaan segar.
        if (!routed && !securityCheckStarted && !updateChecking) {
            new Handler(Looper.getMainLooper()).postDelayed(this::startSecurityCheck, 300L);
        }
    }

    private void checkAppUpdate() {
        CustomerResourceUpdateManager.checkInBackground(this);
        if (!BuildConfig.SELF_UPDATE_APK) {
            statusText.setText("Aplikasi siap digunakan");
            routeNext();
            return;
        }
        if (routed || updateChecking || isFinishing()) return;
        updateChecking = true;
        statusText.setText("Memeriksa versi Transiva Customer...");

        AppUpdateInfo cached = AppUpdateStore.cachedInfo(this);
        int current = currentVersion();
        if (cached != null && cached.isForceRequired(current)) {
            updateChecking = false;
            openForcedUpdate();
            return;
        }

        AppUpdateClient.check(this, new AppUpdateClient.Callback() {
            @Override public void onResult(AppUpdateInfo info, boolean available) {
                runOnUiThread(() -> {
                    updateChecking = false;
                    if (isFinishing() || routed) return;
                    int installed = currentVersion();
                    if (info.isForceRequired(installed)) {
                        openForcedUpdate();
                        return;
                    }
                    if (available) {
                        try { AppUpdateDownloadManager.ensureDownload(SplashActivity.this, info); }
                        catch (Exception ignored) { }
                    }
                    statusText.setText("Aplikasi siap digunakan");
                    routeNext();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    updateChecking = false;
                    if (isFinishing() || routed) return;
                    AppUpdateInfo old = AppUpdateStore.cachedInfo(SplashActivity.this);
                    if (old != null && old.isForceRequired(currentVersion())) {
                        openForcedUpdate();
                    } else {
                        // Fail-open hanya jika tidak ada force-update yang sudah tercache.
                        statusText.setText("Membuka aplikasi...");
                        routeNext();
                    }
                });
            }
        });
    }

    private void openForcedUpdate() {
        if (routed) return;
        routed = true;
        Intent i = new Intent(this, UpdateDownloadActivity.class);
        i.putExtra(UpdateDownloadActivity.EXTRA_ROLE, "customer");
        i.putExtra(UpdateDownloadActivity.EXTRA_FORCE, true);
        i.putExtra(UpdateDownloadActivity.EXTRA_AUTO_START, true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private int currentVersion() {
        try { return AppUpdateClient.installedVersionCode(this); }
        catch (Exception ignored) { return 0; }
    }

    private void routeNext() {
        if (routed || isFinishing()) return;
        routed = true;
        if (!TransAssistantTourActivity.isDone(this)) {
            openRoot(new Intent(this, TransAssistantTourActivity.class));
            return;
        }

        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            openRoot(new Intent(this, LoginActivity.class));
            return;
        }

        // Tetap pertahankan recovery order aktif milik Customer.
        ActiveOrderRecovery.route(this, session, routedToTrip -> {
            if (!routedToTrip && !isFinishing()) {
                openRoot(new Intent(this, CustomerDashboardActivity.class));
            }
        });
    }

    private void openRoot(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int getDrawableId(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); }
        catch (Exception e) { return 0; }
    }
}
