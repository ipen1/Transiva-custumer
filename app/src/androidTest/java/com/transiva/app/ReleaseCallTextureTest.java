package com.transiva.app;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.webrtc.*;
import static org.junit.Assert.*;

/** Device check: render a real EGL frame, then resize and recreate the view's surface. */
@RunWith(AndroidJUnit4.class)
public class ReleaseCallTextureTest {
    @Test public void textureDisplaysFramesAfterResizeAndReattachment() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        WebRtcNativeRuntime.initialize(instrumentation.getTargetContext());
        EglBase egl=EglBase.create();
        WebRtcCallActivity screen=null;
        AtomicReference<CallTextureRenderer> target=new AtomicReference<>();
        AtomicReference<FrameLayout> container=new AtomicReference<>();
        AtomicReference<CountDownLatch> shown=new AtomicReference<>(new CountDownLatch(1));
        JavaI420Buffer buffer=JavaI420Buffer.allocate(32,32);
        for(int i=0;i<buffer.getDataY().capacity();i++)buffer.getDataY().put(i,(byte)235);
        for(int i=0;i<buffer.getDataU().capacity();i++)buffer.getDataU().put(i,(byte)128);
        for(int i=0;i<buffer.getDataV().capacity();i++)buffer.getDataV().put(i,(byte)128);
        VideoFrame frame=new VideoFrame(buffer,0,System.nanoTime());
        try {
            screen=(WebRtcCallActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),WebRtcCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final WebRtcCallActivity activity=screen;
            instrumentation.runOnMainSync(()->{
                FrameLayout root=new FrameLayout(activity);CustomerAppSettings.preserveArtwork(root);
                activity.setContentView(root);container.set(root);
                CallTextureRenderer view=new CallTextureRenderer(activity);target.set(view);
                view.init(egl.getEglBaseContext(),()->shown.get().countDown());view.setMirror(true);
                view.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),12);}});
                view.setClipToOutline(true);root.addView(view,new FrameLayout.LayoutParams(92,128));
            });
            awaitFrame(target.get(),frame,shown.get());
            instrumentation.runOnMainSync(()->{
                Bitmap bitmap=target.get().getBitmap();assertNotNull(bitmap);
                assertTrue("Rendered buffer is still black",Color.red(bitmap.getPixel(bitmap.getWidth()/2,bitmap.getHeight()/2))>180);bitmap.recycle();
                assertTrue(target.get().getClipToOutline());
                target.get().setLayoutParams(new FrameLayout.LayoutParams(60,80));
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->container.get().removeView(target.get()));
            instrumentation.waitForIdleSync();shown.set(new CountDownLatch(1));
            instrumentation.runOnMainSync(()->container.get().addView(target.get(),new FrameLayout.LayoutParams(60,80)));
            awaitFrame(target.get(),frame,shown.get());
            instrumentation.runOnMainSync(()->{assertEquals(60,target.get().getWidth());assertEquals(80,target.get().getHeight());});
        } finally {
            frame.release();
            final WebRtcCallActivity activity=screen;
            instrumentation.runOnMainSync(()->{if(target.get()!=null)target.get().release();if(activity!=null)activity.finish();});
            egl.release();
        }
    }
    private static void awaitFrame(CallTextureRenderer target,VideoFrame frame,CountDownLatch shown) throws Exception {
        for(int i=0;i<100 && shown.getCount()>0;i++){target.onFrame(frame);shown.await(100,TimeUnit.MILLISECONDS);}
        assertEquals("Texture never displayed a video frame",0,shown.getCount());
    }
}
