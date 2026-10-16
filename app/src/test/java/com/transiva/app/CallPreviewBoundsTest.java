package com.transiva.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallPreviewBoundsTest {
    @Test public void portraitUsesRequestedSizeAndInset() {
        CallPreviewBounds b=CallPreviewBounds.fit(360,480,92,128,12);
        assertEquals(92,b.width);assertEquals(128,b.height);assertEquals(12,b.margin);
    }
    @Test public void previewAndMarginsAlwaysFitDuringResize() {
        for(int width:new int[]{1,2,10,24,40,90,180,360,800})
            for(int height:new int[]{1,2,10,24,40,90,180,360,800}) {
                CallPreviewBounds b=CallPreviewBounds.fit(width,height,92,128,12);
                assertTrue(b.width>0);assertTrue(b.height>0);assertTrue(b.margin>=0);
                assertTrue("Horizontal overflow",b.width+2*b.margin<=width);
                assertTrue("Vertical overflow",b.height+2*b.margin<=height);
            }
    }
}
