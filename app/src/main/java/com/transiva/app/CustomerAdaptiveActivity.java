package com.transiva.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

/** Resize the current view tree without restarting requests or discarding in-memory forms. */
public class CustomerAdaptiveActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CustomerWindowPolicy.install(this);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        CustomerWindowPolicy.install(this);
        View content = findViewById(android.R.id.content);
        if (content != null) { content.requestLayout(); content.invalidate(); }
    }
}
