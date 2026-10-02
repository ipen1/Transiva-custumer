package com.transiva.app;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
/** Full screen image with pinch zoom, drag and double tap reset. */
public class TransShopZoomActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);ZoomImage image=new ZoomImage();root.addView(image,new FrameLayout.LayoutParams(-1,-1));TransShopImages.load(image,getIntent().getStringExtra("image_url"),true);TextView close=new TextView(this);close.setText("✕  Tutup");close.setTextColor(Color.WHITE);close.setTextSize(17);close.setPadding(22,20,22,20);root.addView(close);close.setOnClickListener(v->finish());}
 private class ZoomImage extends androidx.appcompat.widget.AppCompatImageView {
  private final Matrix matrix=new Matrix();private final ScaleGestureDetector scale;private final GestureDetector gestures;private float zoom=1,lastX,lastY;private boolean moving=false;
  ZoomImage(){super(TransShopZoomActivity.this);setScaleType(ScaleType.FIT_CENTER);scale=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float next=Math.max(1,Math.min(5,zoom*d.getScaleFactor()));float factor=next/zoom;zoom=next;matrix.postScale(factor,factor,d.getFocusX(),d.getFocusY());apply();return true;}});gestures=new GestureDetector(getContext(),new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDoubleTap(android.view.MotionEvent e){reset();return true;}});}
  void reset(){zoom=1;matrix.reset();setScaleType(ScaleType.FIT_CENTER);invalidate();}
  void apply(){if(zoom<=1.001f){reset();return;}setScaleType(ScaleType.MATRIX);setImageMatrix(matrix);}
  @Override public boolean onTouchEvent(android.view.MotionEvent e){gestures.onTouchEvent(e);scale.onTouchEvent(e);switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();moving=true;break;case MotionEvent.ACTION_MOVE:if(e.getPointerCount()==1&&moving&&zoom>1){matrix.postTranslate(e.getX()-lastX,e.getY()-lastY);apply();}lastX=e.getX();lastY=e.getY();break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:moving=false;break;}return true;}
 }
}
