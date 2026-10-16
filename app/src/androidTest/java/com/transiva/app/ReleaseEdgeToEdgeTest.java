package com.transiva.app;

import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.graphics.Insets;
import android.view.WindowInsets;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the real release activity inset listener, including repeated configuration dispatch. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=35)
public class ReleaseEdgeToEdgeTest {
    private static WindowInsets insets(int left,int top,int right,int bottom,int keyboard) {
        return new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(),Insets.of(left,top,right,bottom))
                .setInsets(WindowInsets.Type.displayCutout(),Insets.NONE)
                .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,keyboard)).build();
    }
    @Test public void barsAndKeyboardApplyOnceAndResetAfterResize() {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        RegisterActivity activity=(RegisterActivity)i.startActivitySync(new Intent(i.getTargetContext(),RegisterActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            i.runOnMainSync(()->{
                View content=activity.findViewById(android.R.id.content);
                content.dispatchApplyWindowInsets(insets(0,0,0,0,0));
                int l=content.getPaddingLeft(),t=content.getPaddingTop(),r=content.getPaddingRight(),b=content.getPaddingBottom();
                for(int repeat=0;repeat<3;repeat++) {
                    activity.onConfigurationChanged(activity.getResources().getConfiguration());
                    assertTrue(content.dispatchApplyWindowInsets(insets(11,23,17,31,240)).isConsumed());
                    assertEquals(l+11,content.getPaddingLeft());assertEquals(t+23,content.getPaddingTop());
                    assertEquals(r+17,content.getPaddingRight());assertEquals(b+240,content.getPaddingBottom());
                }
                content.dispatchApplyWindowInsets(insets(43,0,0,31,0));
                assertEquals(l+43,content.getPaddingLeft());assertEquals(t,content.getPaddingTop());
                assertEquals(r,content.getPaddingRight());assertEquals(b+31,content.getPaddingBottom());
                assertFalse("API 35+ theme still enables navigation contrast scrim",activity.getWindow().isNavigationBarContrastEnforced());
            });
        } finally {i.runOnMainSync(activity::finish);}
    }
}
