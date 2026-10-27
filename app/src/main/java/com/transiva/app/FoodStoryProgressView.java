package com.transiva.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Segmented media progress. A photograph receives five visible seconds after loading. */
final class FoodStoryProgressView extends View {
    static final long PHOTO_DURATION_MS=5000L;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int count=1,index;
    private float fraction;
    FoodStoryProgressView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    static float fraction(long started,long now){return started<0?0f:Math.max(0f,Math.min(1f,(now-started)/(float)PHOTO_DURATION_MS));}
    void showProgress(int count,int index,float fraction){this.count=Math.max(1,count);this.index=index;this.fraction=Math.max(0,Math.min(1,fraction));invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float gap=3*getResources().getDisplayMetrics().density;float width=Math.max(0,(getWidth()-gap*(count-1))/count);float height=getHeight();for(int n=0;n<count;n++){float x=n*(width+gap);paint.setColor(0xFF68717C);canvas.drawRoundRect(x,0,x+width,height,height/2,height/2,paint);float fill=n<index?1:n==index?fraction:0;if(fill>0){paint.setColor(Color.WHITE);canvas.drawRoundRect(x,0,x+width*fill,height,height/2,height/2,paint);}}}
}
