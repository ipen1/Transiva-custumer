package com.transiva.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** Resize the current view tree without restarting requests or discarding in-memory forms. */
public class CustomerAdaptiveActivity extends Activity {
    private OnBackInvokedCallback customBackCallback;

    /** Routes system Back to the same handler used on older Android versions. */
    protected final void registerCustomBackNavigation() {
        if (Build.VERSION.SDK_INT >= 33 && customBackCallback == null) {
            customBackCallback = this::onBackPressed;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, customBackCallback);
        }
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && customBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(customBackCallback);
            customBackCallback = null;
        }
        super.onDestroy();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CustomerWindowPolicy.install(this);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        CustomerWindowPolicy.install(this);
        CustomerAppSettings.apply(this);
        View content = findViewById(android.R.id.content);
        if (content != null) { content.requestLayout(); content.invalidate(); }
    }
}
