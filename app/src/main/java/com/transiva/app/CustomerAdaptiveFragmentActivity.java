package com.transiva.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.fragment.app.FragmentActivity;

/** Same window lifecycle while retaining FragmentActivity/biometric support. */
public class CustomerAdaptiveFragmentActivity extends FragmentActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CustomerWindowPolicy.install(this);
    }
    protected interface BackAction {
        boolean handleBack();
    }

    protected final void registerCustomBackNavigation(BackAction action) {
        OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
        dispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (action.handleBack()) return;
                // Let other callbacks or Android handle Back when this screen has no internal step.
                setEnabled(false);
                try {
                    dispatcher.onBackPressed();
                } finally {
                    setEnabled(true);
                }
            }
        });
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        CustomerWindowPolicy.install(this);
        View content=findViewById(android.R.id.content);
        if(content!=null){content.requestLayout();content.invalidate();}
    }
}
