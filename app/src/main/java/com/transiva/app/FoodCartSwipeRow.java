package com.transiva.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Swipe a whole cart line left without intercepting vertical scroll or checkbox taps. */
final class FoodCartSwipeRow extends FrameLayout {
    private final Runnable remove;
    private final int slop;
    private final float threshold;
    private float startX, startY;
    private boolean swiping;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    FoodCartSwipeRow(Context context, Runnable remove) {
        super(context);
        this.remove = remove;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        threshold = 68 * getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        paint.setTextSize(12 * getResources().getDisplayMetrics().density);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!swiping || getChildCount() == 0) return;
        float revealed = -getChildAt(0).getTranslationX();
        if (revealed <= 0) return;
        int width = getWidth();
        paint.setColor(0xFFB92548);
        canvas.drawRect(width - revealed, 0, width, getHeight(), paint);
        if (revealed > threshold * .65f) {
            paint.setColor(0xFFFFFFFF);
            canvas.drawText("Hapus", width - revealed / 2f,
                    (getHeight() - paint.ascent() - paint.descent()) / 2f, paint);
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) begin(event);
        return event.getActionMasked() == MotionEvent.ACTION_MOVE && detectSwipe(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (swiping || detectSwipe(event)) {
                    getChildAt(0).setTranslationX(Math.max(-threshold, Math.min(0, event.getX() - startX)));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean delete = swiping && startX - event.getX() >= threshold;
                reset();
                if (delete) remove.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                reset();
                return true;
            default:
                return true;
        }
    }

    private void begin(MotionEvent event) {
        swiping = false;
        startX = event.getX();
        startY = event.getY();
    }

    private boolean detectSwipe(MotionEvent event) {
        float left = startX - event.getX();
        if (left <= slop || left <= Math.abs(event.getY() - startY) * 1.35f) return false;
        swiping = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    private void reset() {
        swiping = false;
        getChildAt(0).setTranslationX(0);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }
}
