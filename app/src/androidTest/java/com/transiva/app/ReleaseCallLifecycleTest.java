package com.transiva.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.*;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Fresh install, no account/order/call ID: exercises real permission UI without contacting call backend. */
@RunWith(AndroidJUnit4.class)
public class ReleaseCallLifecycleTest {
    static Object field(Object target,String name)throws Exception{Field f=TransivaCallUiActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static void shell(Instrumentation i,String command)throws Exception{try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(i.getUiAutomation().executeShellCommand(command))){byte[] data=new byte[4096];while(in.read(data)!=-1){}}}
    private void denial(boolean locked)throws Exception{
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        Context c=i.getTargetContext();
        assertNotEquals("Run on a fresh install before granting call permissions",PackageManager.PERMISSION_GRANTED,c.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        WebRtcCallActivity screen=null;
        try{
            shell(i,"input keyevent 224");shell(i,"wm dismiss-keyguard");
            if(locked){shell(i,"input keyevent 223");}
            screen=(WebRtcCallActivity)i.startActivitySync(new Intent(c,WebRtcCallActivity.class).putExtra("call_type","video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final WebRtcCallActivity activity=screen;
            i.runOnMainSync(()->{try{Method m=TransivaCallUiActivity.class.getDeclaredMethod("requestStart");m.setAccessible(true);m.invoke(activity);}catch(Exception e){throw new AssertionError(e);}});
            long end=SystemClock.uptimeMillis()+15000;boolean clicked=false;
            while(SystemClock.uptimeMillis()<end){
                AccessibilityNodeInfo root=i.getUiAutomation().getRootInActiveWindow();
                if(root!=null){List<AccessibilityNodeInfo> deny=new java.util.ArrayList<>();
                    for(String controller:new String[]{"com.android.permissioncontroller","com.google.android.permissioncontroller"})
                        for(String id:new String[]{"permission_deny_button","permission_deny_and_dont_ask_again_button"})
                            deny.addAll(root.findAccessibilityNodeInfosByViewId(controller+":id/"+id));
                    for(AccessibilityNodeInfo button:deny)if(button.isClickable()){clicked|=button.performAction(AccessibilityNodeInfo.ACTION_CLICK);} }
                java.util.concurrent.atomic.AtomicBoolean inflight=new java.util.concurrent.atomic.AtomicBoolean(true);
                i.runOnMainSync(()->{try{inflight.set((Boolean)field(activity,"permissionsInFlight"));}catch(Exception e){throw new AssertionError(e);}});
                if(clicked&&!inflight.get())break;
                SystemClock.sleep(150);
            }
            assertTrue("Android Deny button was not clickable",clicked);
            i.runOnMainSync(()->{try{
                assertFalse((Boolean)field(activity,"permissionsInFlight"));
                assertTrue(((Button)field(activity,"accept")).isEnabled());
                assertNull("Denied permission started foreground call service",WebRtcCallForegroundService.current());
            }catch(Exception e){throw new AssertionError(e);}});
            assertNotEquals(PackageManager.PERMISSION_GRANTED,c.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        }finally{if(screen!=null){WebRtcCallActivity activity=screen;i.runOnMainSync(activity::finish);}shell(i,"input keyevent 224");shell(i,"wm dismiss-keyguard");}
    }
    @Test public void deniedPermissionsRemainRetryable()throws Exception{denial(false);}
    @Test public void lockedScreenDoesNotBypassPermission()throws Exception{denial(true);}
}
