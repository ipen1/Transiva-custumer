package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

final class AntarKilatRoadView extends View {
    private final Bitmap street;
    private final Bitmap courier;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final Path shape = new Path();
    private final float swipeThreshold;
    private AntarKilatRound round;
    private boolean steeringEnabled;
    private float touchStart;
    private boolean swiped;

    AntarKilatRoadView(Context context) {
        super(context);
        street = BitmapFactory.decodeResource(getResources(), R.drawable.antar_kilat_street);
        courier = BitmapFactory.decodeResource(getResources(), R.drawable.antar_kilat_courier);
        swipeThreshold = 22 * getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void render(AntarKilatRound round, boolean steeringEnabled) {
        this.round = round;
        this.steeringEnabled = steeringEnabled;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (round == null || getWidth() == 0 || getHeight() == 0) return;
        float width = Math.min(getWidth(), getHeight() * .95f);
        float height = getHeight();
        canvas.drawColor(0xff081833);
        canvas.save();
        canvas.translate((getWidth() - width) / 2, 0);
        canvas.scale(width, height);
        bounds.set(0, 0, 1, 1);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(street, null, bounds, paint);

        float motion = round.elapsed * .77f % .19f;
        paint.setColor(0xffe0e6f2);
        for (float depth = motion; depth < 1.1f; depth += .19f) {
            float end = Math.min(1.1f, depth + .095f);
            drawStripe(canvas, -.5f, depth, end);
            drawStripe(canvas, .5f, depth, end);
        }
        for (int i = round.objects.size() - 1; i >= 0; i--) {
            drawObject(canvas, round.objects.get(i), width, height);
        }

        float riderX = roadX(round.riderLane - 1, 1);
        float riderHeight = Math.min(.38f, .38f * width / height);
        float riderWidth = riderHeight * height / width * courier.getWidth() / courier.getHeight();
        paint.setColor(0x72041226);
        canvas.drawOval(riderX - riderWidth * .44f, .878f, riderX + riderWidth * .44f, .918f, paint);
        bounds.set(riderX - riderWidth / 2, .91f - riderHeight, riderX + riderWidth / 2, .91f);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(courier, null, bounds, paint);
        if (round.impact > 0) {
            paint.setColor(0x7cff5443);
            canvas.drawRect(0, .988f, 1, 1, paint);
        }
        canvas.restore();
    }

    private void drawStripe(Canvas canvas, float lane, float start, float end) {
        shape.reset();
        shape.moveTo(roadX(lane - .015f, start), roadY(start));
        shape.lineTo(roadX(lane + .015f, start), roadY(start));
        shape.lineTo(roadX(lane + .015f, end), roadY(end));
        shape.lineTo(roadX(lane - .015f, end), roadY(end));
        shape.close();
        canvas.drawPath(shape, paint);
    }

    private static float roadX(float lane, float depth) {
        return .5f + lane * (.009f + .27f * depth * depth);
    }

    private static float roadY(float depth) {
        return .347f + .563f * depth * depth;
    }

    private void drawObject(Canvas canvas, AntarKilatRound.ObjectOnRoad object, float width, float height) {
        float x = roadX(object.lane - 1, object.depth);
        float y = roadY(object.depth);
        float size = .005f + .08f * object.depth * object.depth;
        float rise = Math.min(.125f, size * width / height);
        if (object.kind == AntarKilatRound.Kind.PARCEL) {
            paint.setColor(0x65020f22);
            canvas.drawOval(x - size, y - rise * .14f, x + size, y + rise * .14f, paint);
            y -= rise * .3f;
            paint.setColor(0xffe78b13);
            canvas.drawRoundRect(x - size * .63f, y - rise, x + size * .63f, y, size * .09f, rise * .09f, paint);
            paint.setColor(0xffffce5a);
            canvas.drawRect(x - size * .56f, y - rise, x + size * .56f, y - rise * .74f, paint);
            paint.setColor(0xffffedb1);
            canvas.drawRect(x - size * .14f, y - rise, x + size * .14f, y - rise * .26f, paint);
        } else {
            boolean puddle = object.kind == AntarKilatRound.Kind.PUDDLE;
            paint.setColor(puddle ? 0xff388fcd : 0xff74717a);
            canvas.drawOval(x - size, y - rise * .32f, x + size, y + rise * .32f, paint);
            paint.setColor(puddle ? 0xff185887 : 0xff1b1521);
            canvas.drawOval(x - size * .81f, y - rise * .23f, x + size * .81f, y + rise * .23f, paint);
            if (puddle) {
                paint.setColor(0xff76c6ea);
                canvas.drawOval(x - size * .53f, y - rise * .15f, x + size * .21f, y - rise * .07f, paint);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!steeringEnabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStart = event.getX();
                swiped = false;
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                float offset = event.getX() - touchStart;
                if (!swiped && Math.abs(offset) >= swipeThreshold) {
                    round.steer(offset < 0 ? -1 : 1);
                    swiped = true;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                swiped = false;
                return true;
            default:
                return true;
        }
    }
}
