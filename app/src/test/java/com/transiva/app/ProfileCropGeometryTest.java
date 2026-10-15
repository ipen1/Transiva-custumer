package com.transiva.app;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class ProfileCropGeometryTest {
    private void covered(ProfileCropGeometry crop) {
        assertTrue(crop.x <= .01f); assertTrue(crop.y <= .01f);
        assertTrue(crop.x + crop.width * crop.scale >= crop.side - .01f);
        assertTrue(crop.y + crop.height * crop.scale >= crop.side - .01f);
        assertTrue(crop.scale >= crop.minimum - .001f);
        assertTrue(crop.scale <= crop.minimum * 5f + .001f);
    }
    @Test public void repeatedDragAndPinchNeverExposeEmptyPixels() {
        Random random = new Random(263);
        for (float[] size : new float[][]{{1600,300},{300,1600},{720,720},{33,1600},{1600,33}}) {
            ProfileCropGeometry crop = new ProfileCropGeometry(); crop.fit(320, size[0], size[1]); covered(crop);
            for (int i=0; i<2000; i++) {
                if (i%2 == 0) crop.drag(random.nextFloat()*2000-1000, random.nextFloat()*2000-1000);
                else crop.zoom(random.nextFloat()*4, random.nextFloat()*320, random.nextFloat()*320);
                covered(crop);
            }
        }
    }
    @Test public void centeredPinchPreservesFocusAndResetRestoresFit() {
        ProfileCropGeometry crop = new ProfileCropGeometry(); crop.fit(320,720,720);
        crop.zoom(2,160,160); assertEquals(-160,crop.x,.01f); assertEquals(-160,crop.y,.01f);
        crop.fit(320,720,720); assertEquals(0,crop.x,.01f); assertEquals(0,crop.y,.01f);
        assertEquals(crop.minimum,crop.scale,.001f); covered(crop);
    }
    @Test public void invalidZoomFactorsCannotCorruptTransform() {
        ProfileCropGeometry crop = new ProfileCropGeometry(); crop.fit(320,1600,300);
        float scale=crop.scale, x=crop.x, y=crop.y;
        for (float factor : new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,-1}) crop.zoom(factor,160,160);
        assertEquals(scale,crop.scale,0); assertEquals(x,crop.x,0); assertEquals(y,crop.y,0); covered(crop);
    }
}
