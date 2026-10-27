package com.transiva.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Native Transfood celebration: serving cloche, rising steam, and a drawn confirmation. */
final class FoodOrderSuccessView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long started;
    private boolean animate;
    FoodOrderSuccessView(Context c){super(c);setContentDescription("Pesanan makanan berhasil dicatat");}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();started=SystemClock.elapsedRealtime();animate=android.os.Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled();invalidate();}
    @Override protected void onDetachedFromWindow(){animate=false;super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float t=animate?(SystemClock.elapsedRealtime()-started)/1000f:2.5f;float unit=Math.min(getWidth()/280f,getHeight()/230f);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f+18*unit);canvas.scale(unit,unit);
        p.setStyle(Paint.Style.FILL);p.setColor(0x1629A6FF);canvas.drawCircle(0,-18,100,p);p.setColor(0x204DAAFF);canvas.drawOval(-82,41,82,55,p);
        float lift=(1-Math.min(1,t/.65f))*28;canvas.save();canvas.translate(0,lift);p.setColor(0xFF1185FF);Path lid=new Path();lid.moveTo(-72,23);lid.cubicTo(-72,-59,72,-59,72,23);lid.close();canvas.drawPath(lid,p);p.setColor(0xFF62D6F3);canvas.drawRoundRect(-81,23,81,32,6,6,p);p.setColor(0xFF0D6BD0);canvas.drawCircle(0,-44,8,p);canvas.restore();
        p.setStrokeWidth(3);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xFF6FCDF1);for(int n=0;n<3;n++){float phase=(t*.6f+n*.27f)%1f;float x=(n-1)*23;Path steam=new Path();steam.moveTo(x,-57-phase*24);steam.cubicTo(x-9,-64-phase*24,x+9,-71-phase*24,x,-80-phase*24);p.setAlpha((int)(170*(1-phase)));canvas.drawPath(steam,p);}p.setAlpha(255);
        float check=Math.max(0,Math.min(1,(t-.65f)/.6f));if(check>0){p.setStyle(Paint.Style.FILL);p.setColor(0xFF14B88A);canvas.drawCircle(61,-52,25*check,p);if(check>.2f){p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);Path mark=new Path();mark.moveTo(49,-52);mark.lineTo(58,-43);mark.lineTo(74,-62);PathMeasure measure=new PathMeasure(mark,false);Path drawn=new Path();measure.getSegment(0,measure.getLength()*check,drawn,true);canvas.drawPath(drawn,p);}}
        if(t<2.8f){p.setStyle(Paint.Style.FILL);for(int n=0;n<12;n++){double angle=n*Math.PI/6;float radius=60+Math.min(t,2.2f)*24;p.setColor(n%2==0?0xFF22BCE0:0xFF0B78FF);p.setAlpha((int)(160*Math.max(0,1-t/3f)));canvas.drawCircle((float)Math.cos(angle)*radius,(float)Math.sin(angle)*radius-18,2.5f,p);}p.setAlpha(255);}canvas.restore();if(animate&&t<3.1f)postInvalidateOnAnimation();
    }
}
