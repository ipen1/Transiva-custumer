package com.transiva.app;

import android.app.Instrumentation;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
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
        return insets(left,top,right,bottom,keyboard,0);
    }
    private static WindowInsets insets(int left,int top,int right,int bottom,int keyboard,int cutoutLeft) {
        return new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(),Insets.of(left,top,right,bottom))
                .setInsets(WindowInsets.Type.displayCutout(),Insets.of(cutoutLeft,0,0,0))
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
                // A landscape cutout is combined by maximum, never added twice.
                content.dispatchApplyWindowInsets(insets(11,23,17,31,0,67));
                assertEquals(l+67,content.getPaddingLeft());
                assertEquals(t+23,content.getPaddingTop());
                content.dispatchApplyWindowInsets(insets(0,23,0,31,240));
                assertEquals(b+240,content.getPaddingBottom());
                content.dispatchApplyWindowInsets(insets(0,23,0,31,0));
                assertEquals(b+31,content.getPaddingBottom());
                assertFalse("API 35+ theme still enables navigation contrast scrim",activity.getWindow().isNavigationBarContrastEnforced());
            });
        } finally {i.runOnMainSync(activity::finish);}
    }

    @Test public void registrationContentFitsRealSystemBars() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        RegisterActivity activity = (RegisterActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), RegisterActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                ViewGroup content = activity.findViewById(android.R.id.content);
                View decor = activity.getWindow().getDecorView();
                WindowInsets insets = decor.getRootWindowInsets();
                assertNotNull("Expected insets from the attached window", insets);
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
                assertTrue("Expected visible system bars", safe.top + safe.bottom > 0);

                View form = content.getChildAt(0);
                assertNotNull(form);
                int[] position = new int[2];
                form.getLocationInWindow(position);
                assertEquals(safe.left, position[0]);
                assertEquals(safe.top, position[1]);
                assertEquals(decor.getWidth() - safe.right, position[0] + form.getWidth());
                assertEquals(decor.getHeight() - Math.max(safe.bottom, keyboard),
                        position[1] + form.getHeight());
            });
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test public void releaseDoesNotRegisterUnusedPlacesAutocompleteActivity() {
        android.content.Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ComponentName widget = new ComponentName(target,
                "com.google.android.libraries.places.widget.AutocompleteActivity");
        try {
            target.getPackageManager().getActivityInfo(widget, 0);
            fail("The Places widget should be absent from the merged manifest");
        } catch (PackageManager.NameNotFoundException expected) {
            // Transiva hosts its own PlacesClient search UI.
        }
    }

    @Test public void floatingMediaDialogPreservesKeyboardStateAndAppliesInsetsOnce() {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        RegisterActivity activity=(RegisterActivity)i.startActivitySync(new Intent(i.getTargetContext(),RegisterActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            i.runOnMainSync(()->{
                Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_Light_Dialog_NoActionBar);
                dialog.setContentView(new FrameLayout(activity));
                dialog.show();
                try {
                    android.view.Window window=dialog.getWindow();
                    assertNotNull(window);
                    window.setLayout(-1,-1);
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                    View content=window.findViewById(android.R.id.content);
                    int l=content.getPaddingLeft(),t=content.getPaddingTop();
                    int r=content.getPaddingRight(),b=content.getPaddingBottom();
                    for(int repeat=0;repeat<3;repeat++) {
                        CustomerWindowPolicy.install(window);
                        int mode=window.getAttributes().softInputMode;
                        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
                                mode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE);
                        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                                mode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
                        content.dispatchApplyWindowInsets(insets(7,23,11,31,240));
                        assertEquals(l+7,content.getPaddingLeft());
                        assertEquals(t+23,content.getPaddingTop());
                        assertEquals(r+11,content.getPaddingRight());
                        assertEquals(b+240,content.getPaddingBottom());
                    }
                    content.dispatchApplyWindowInsets(insets(0,23,0,31,0));
                    assertEquals(b+31,content.getPaddingBottom());
                } finally { dialog.dismiss(); }
            });
        } finally {i.runOnMainSync(activity::finish);}
    }
    @Test public void compactTransSimpelPanelLeavesSpaceForComposerAfterResize() {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        RegisterActivity activity=(RegisterActivity)instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(),RegisterActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(()->{
                TransSimpelRoutePanel panel=new TransSimpelRoutePanel(activity,"PUD000000000000000000000000");
                try {
                    android.widget.LinearLayout column=panel.createLayout();
                    View route=panel.view();
                    route.setVisibility(View.VISIBLE);
                    column.addView(route,new android.widget.LinearLayout.LayoutParams(-1,-2));
                    View messages=new View(activity);
                    column.addView(messages,new android.widget.LinearLayout.LayoutParams(-1,0,1));
                    View composer=new View(activity);
                    float density=activity.getResources().getDisplayMetrics().density;
                    int composerHeight=Math.round(56*density);
                    column.addView(composer,new android.widget.LinearLayout.LayoutParams(-1,composerHeight));
                    for(int heightDp:new int[]{720,300,580,240,720}) {
                        int width=Math.round(320*density);
                        int height=Math.round(heightDp*density);
                        column.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                        column.layout(0,0,width,height);
                        int limit=Math.round(height*.42f);
                        assertTrue("Route height "+route.getHeight()+" exceeds "+limit+" at "+heightDp+"dp",
                                route.getHeight()>0 && route.getHeight()<=limit);
                        assertTrue("Messages have no space at "+heightDp+"dp",messages.getHeight()>0);
                        assertEquals("Composer height at "+heightDp+"dp",composerHeight,composer.getHeight());
                        assertEquals("Composer bottom at "+heightDp+"dp",height,composer.getBottom());
                    }
                } finally {
                    panel.close();
                }
            });
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
