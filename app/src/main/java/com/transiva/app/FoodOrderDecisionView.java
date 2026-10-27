package com.transiva.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Calm food-specific illustration: empty plate, closing storefront, or a busy kitchen. */
final class FoodOrderDecisionView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String category;
    private final long anchor=SystemClock.uptimeMillis();
    FoodOrderDecisionView(Context context,String category){super(context);this.category=category;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void stroke(Canvas c,float x1,float y1,float x2,float y2,int color,float width){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float scale=Math.min(getWidth()/260f,getHeight()/200f);c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(scale,scale);
        float phase=(SystemClock.uptimeMillis()-anchor)/1000f,breath=(float)Math.sin(phase*1.6f);
        p.setStyle(Paint.Style.FILL);p.setColor(0x1649A6FF);c.drawCircle(0,0,80+breath*3,p);p.setColor(0x182D8BFF);c.drawCircle(0,0,64,p);
        if("closed".equals(category)){
            p.setColor(0xFF253D58);c.drawRoundRect(-51,-29,51,46,9,9,p);p.setColor(0xFF68B5FF);c.drawRoundRect(-57,-42,57,-21,6,6,p);
            p.setColor(0xFF122235);c.drawRoundRect(-29,-12,29,43,4,4,p);
            float shutter=15+(float)Math.sin(Math.min(phase,1.5f)/1.5f*Math.PI/2)*23;p.setColor(0xFF7E9BB8);c.drawRoundRect(-29,-12,29,shutter,3,3,p);
            for(int y=-6;y<shutter;y+=9)stroke(c,-26,y,26,y,0xFF456582,2);
        }else if("busy".equals(category)||"waiting".equals(category)){
            p.setColor(0xFF18334F);c.drawOval(-60,33,60,46,p);p.setColor(0xFF80C2FF);c.drawArc(-52,-35,52,56,180,180,true,p);stroke(c,-61,12,61,12,0xFFE1F1FF,6);stroke(c,0,-40,0,-30,0xFFE1F1FF,6);
            for(int i=0;i<3;i++){float y=-54-(phase*12+i*8)%25;p.setColor(0x8874BAFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);Path steam=new Path();steam.moveTo(-20+i*20,y);steam.cubicTo(-29+i*20,y-8,-11+i*20,y-12,-20+i*20,y-18);c.drawPath(steam,p);p.setStyle(Paint.Style.FILL);}
        }else{
            p.setColor(0xFF77B7F3);c.drawOval(-46,-37,46,37,p);p.setColor(0xFF18334F);c.drawOval(-35,-27,35,27,p);p.setColor(0xFF9DBEDC);c.drawOval(-25,-18,25,18,p);
            stroke(c,-62,-32,-62,36,0xFFC6DCEF,5);for(int i=0;i<3;i++)stroke(c,-68+i*6,-34,-68+i*6,-14,0xFFC6DCEF,3);stroke(c,62,-32,62,36,0xFFC6DCEF,5);
        }
        if(!"waiting".equals(category)){
            p.setColor(0xFFFFBE72);c.drawCircle(53,43,21,p);stroke(c,47,37,59,49,0xFF593C24,4);stroke(c,59,37,47,49,0xFF593C24,4);
        }
        c.restore();if(isAttachedToWindow()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(32);
    }
}
