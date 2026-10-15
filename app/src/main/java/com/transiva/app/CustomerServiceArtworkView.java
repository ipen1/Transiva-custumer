package com.transiva.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** One measured tile owns both its frame and artwork; no rounded parent can crop the image. */
final class CustomerServiceArtworkView extends View {
    private final Drawable artwork;
    private final int preferred;
    private final float density;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outer=new RectF();
    CustomerServiceArtworkView(Context context,int resource,int preferredPixels){
        super(context);preferred=preferredPixels;density=getResources().getDisplayMetrics().density;
        artwork=resource==0?null:context.getDrawable(resource).mutate();
        CustomerAppSettings.preserveArtwork(this);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int side=preferred;
        if(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED)side=Math.min(side,MeasureSpec.getSize(widthSpec));
        if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)side=Math.min(side,MeasureSpec.getSize(heightSpec));
        side=Math.max(0,side);setMeasuredDimension(resolveSize(side,widthSpec),resolveSize(side,heightSpec));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float side=Math.min(getWidth(),getHeight());if(side<=0)return;
        float stroke=Math.max(1,density),radius=Math.min(6*density,side/8f);
        outer.set(stroke/2,stroke/2,getWidth()-stroke/2,getHeight()-stroke/2);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xfff8fbff);canvas.drawRoundRect(outer,radius,radius,paint);
        if(artwork!=null){
            // At least 0.3 * radius keeps even the square bitmap corners inside the rounded frame.
            int inset=Math.max(1,Math.round(Math.max(2*density,stroke+radius*.3f)));
            int room=Math.max(0,Math.min(getWidth(),getHeight())-2*inset);
            int w=artwork.getIntrinsicWidth(),h=artwork.getIntrinsicHeight();
            if(w<=0||h<=0){w=room;h=room;}
            float scale=w>0&&h>0?Math.min(room/(float)w,room/(float)h):0;
            int width=Math.round(w*scale),height=Math.round(h*scale);
            int left=(getWidth()-width)/2,top=(getHeight()-height)/2;
            artwork.setBounds(left,top,left+width,top+height);artwork.draw(canvas);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(0xffc9d9ec);
        canvas.drawRoundRect(outer,radius,radius,paint);
    }
}
