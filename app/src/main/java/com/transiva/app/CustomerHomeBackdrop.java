package com.transiva.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Painted surfaces for the Home hero and the nearby weather tile. */
final class CustomerHomeBackdrop extends Drawable {
    private final boolean weather;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path wave = new Path();
    private Shader base;
    private Shader accent;

    CustomerHomeBackdrop(boolean weather, float radius) {
        this.weather = weather;
        this.radius = radius;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float w = bounds.width();
        float h = bounds.height();
        outline.reset();
        wave.reset();
        if (w <= 0 || h <= 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        float top = weather ? r : 0;
        outline.addRoundRect(new RectF(0, 0, w, h),
                new float[]{top, top, top, top, r, r, r, r}, Path.Direction.CW);
        if (weather) {
            base = new LinearGradient(0, h, w, 0, 0xFF0646BD, 0xFF098BF2, Shader.TileMode.CLAMP);
            accent = new LinearGradient(0, h, w, h * .7f, 0x0800B8FF, 0xFF13AEEC, Shader.TileMode.CLAMP);
            wave.moveTo(0, h * .86f);
            wave.cubicTo(w * .4f, h * 1.04f, w * .68f, h * .63f, w, h * .82f);
            wave.lineTo(w, h);
            wave.lineTo(0, h);
            wave.close();
        } else {
            base = new LinearGradient(0, 0, w * .9f, h,
                    new int[]{0xFF0638C5, 0xFF061C72, 0xFF064DDF}, null, Shader.TileMode.CLAMP);
            accent = new RadialGradient(w * .88f, h * .02f, w * .46f,
                    new int[]{0xD900CFFC, 0x450089F0, 0x000089F0},
                    new float[]{0, .44f, 1}, Shader.TileMode.CLAMP);
        }
    }

    @Override public void draw(Canvas canvas) {
        if (outline.isEmpty()) return;
        Rect bounds = getBounds();
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.clipPath(outline);
        paint.setShader(base);
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), paint);
        paint.setShader(accent);
        if (weather) canvas.drawPath(wave, paint);
        else canvas.drawRect(0, 0, bounds.width(), bounds.height(), paint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
