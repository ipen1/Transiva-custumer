package com.transiva.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.fragment.app.FragmentActivity;

/** Same window lifecycle while retaining FragmentActivity/biometric support. */
public class CustomerAdaptiveFragmentActivity extends FragmentActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        CustomerWindowPolicy.install(this);
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        CustomerWindowPolicy.install(this);
        View content=findViewById(android.R.id.content);
        if(content!=null){content.requestLayout();content.invalidate();}
    }
}
