package com.transiva.app;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Device regression: rotating an open registration form must keep its text and instance. */
@RunWith(AndroidJUnit4.class)
public class ReleaseWindowResizeTest {
    private static EditText firstInput(View view) {
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            EditText result=firstInput(((ViewGroup)view).getChildAt(i)); if(result!=null)return result;
        }
        return null;
    }
    @Test public void registrationSurvivesRotationWithoutRestart() {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        UiAutomation automation=instrumentation.getUiAutomation();
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0);
        RegisterActivity activity=null;
        try {
            Intent intent=new Intent(instrumentation.getTargetContext(),RegisterActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(RegisterActivity)instrumentation.startActivitySync(intent);
            final RegisterActivity screen=activity;
            AtomicReference<EditText> input=new AtomicReference<>();
            instrumentation.runOnMainSync(()-> {
                input.set(firstInput(screen.findViewById(android.R.id.content)));
                assertNotNull(input.get()); input.get().setText("rotation_smoke_270");
            });
            automation.setRotation(UiAutomation.ROTATION_FREEZE_90);
            long deadline=SystemClock.uptimeMillis()+10000;
            while(screen.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_LANDSCAPE
                    &&SystemClock.uptimeMillis()<deadline) SystemClock.sleep(100);
            instrumentation.runOnMainSync(()-> {
                assertEquals(Configuration.ORIENTATION_LANDSCAPE,screen.getResources().getConfiguration().orientation);
                assertFalse("Rotation destroyed form",screen.isDestroyed());
                assertEquals("rotation_smoke_270",input.get().getText().toString());
                assertSame(input.get(),firstInput(screen.findViewById(android.R.id.content)));
            });
        } finally {
            if(activity!=null){final RegisterActivity screen=activity;instrumentation.runOnMainSync(screen::finish);}
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE);
        }
    }
}
