package com.transiva.app;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** No optical insets: native Switch cannot subtract inset padding from its thumb travel twice. */
final class CustomerSwitchThumbDrawable extends Drawable {
    private final float density;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ColorStateList tint=ColorStateList.valueOf(0xffffffff);
    private int alpha=255;
    CustomerSwitchThumbDrawable(float density){this.density=density;}
    @Override public int getIntrinsicWidth(){return Math.round(28*density);}
    @Override public int getIntrinsicHeight(){return Math.round(32*density);}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();float radius=Math.max(0,Math.min(b.width(),b.height())/2f-2*density);
        paint.setColor(tint.getColorForState(getState(),tint.getDefaultColor()));paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,paint);
    }
    @Override public boolean isStateful(){return tint.isStateful();}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    @Override public void setTintList(ColorStateList colors){tint=colors==null?ColorStateList.valueOf(0xffffffff):colors;invalidateSelf();}
    @Override public void setTint(int color){setTintList(ColorStateList.valueOf(color));}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
