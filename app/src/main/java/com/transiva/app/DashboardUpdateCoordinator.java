package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-blocking update check owned by Dashboard, never by startup/splash. */
final class DashboardUpdateCoordinator {
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private DashboardUpdateCoordinator() {}
    static void check(Activity activity) {
        CustomerResourceUpdateManager.checkInBackground(activity);
        if (!BuildConfig.SELF_UPDATE_APK || activity == null || activity.isFinishing()) return;
        if (!CHECKING.compareAndSet(false, true)) return;
        AppUpdateClient.check(activity, new AppUpdateClient.Callback() {
            @Override public void onResult(AppUpdateInfo info, boolean available) {
                CHECKING.set(false);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    int installed = current(activity);
                    if (info != null && info.isForceRequired(installed)) { open(activity, true); return; }
                    if (available) {
                        try { AppUpdateDownloadManager.ensureDownload(activity, info); } catch (Exception ignored) {}
                    }
                });
            }
            @Override public void onError(String message) { CHECKING.set(false); }
        });
    }
    private static int current(Activity a) { try { return AppUpdateClient.installedVersionCode(a); } catch (Exception e) { return 0; } }
    private static void open(Activity a, boolean force) {
        Intent i = new Intent(a, UpdateDownloadActivity.class);
        i.putExtra(UpdateDownloadActivity.EXTRA_ROLE, "customer");
        i.putExtra(UpdateDownloadActivity.EXTRA_FORCE, force);
        i.putExtra(UpdateDownloadActivity.EXTRA_AUTO_START, true);
        a.startActivity(i);
    }
}
