package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class AntarKilatRoadView extends View {
    private static final float HORIZON = .347f;
    private static final float RIDER_GROUND = .91f;
    private final AntarKilatVillage village;
    private final Bitmap courier;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final Path shape = new Path();
    private final Shader goldGlow = new RadialGradient(0, 0, 1f,
            new int[]{0x80ffbe36, 0x34ff9f21, 0x00ff9f21}, null, Shader.TileMode.CLAMP);
    private final float swipeThreshold;
    private AntarKilatRound round;
    private boolean steeringEnabled;
    private float touchStart;
    private int pointer = -1;
    private boolean swiped;
    private float width;
    private float height;
    private final Path[] parcelFaces = new Path[5];
    private final Path[] holeFaces = new Path[4];

    AntarKilatRoadView(Context context) {
        super(context);
        village = new AntarKilatVillage(getResources());
        courier = BitmapFactory.decodeResource(getResources(), R.drawable.antar_kilat_courier);
        swipeThreshold = 18 * getResources().getDisplayMetrics().density;
        parcelFaces[0] = polygon(-.74f, -.32f, .15f, -.61f, .75f, -.28f, -.11f, .03f);
        parcelFaces[1] = polygon(-.74f, -.32f, -.11f, .03f, -.11f, .71f, -.74f, .35f);
        parcelFaces[2] = polygon(-.11f, .03f, .75f, -.28f, .75f, .39f, -.11f, .71f);
        parcelFaces[3] = polygon(-.43f, -.42f, -.25f, -.48f, .39f, -.14f, .22f, -.09f);
        parcelFaces[4] = polygon(.22f, -.09f, .39f, -.14f, .39f, .15f, .22f, .21f);
        holeFaces[0] = polygon(-1f, .04f, -.78f, -.23f, -.41f, -.28f, -.17f, -.37f,
                .38f, -.32f, .8f, -.2f, 1f, .08f, .64f, .3f, .08f, .38f, -.54f, .28f);
        holeFaces[1] = polygon(-.86f, .01f, -.57f, -.16f, -.16f, -.25f, .35f, -.23f,
                .67f, -.13f, .82f, .06f, .45f, .23f, .02f, .24f, -.5f, .18f);
        holeFaces[2] = polygon(-.78f, -.23f, -.61f, -.13f, -.86f, .01f, -1.08f, -.12f);
        holeFaces[3] = polygon(.64f, .3f, .46f, .22f, .82f, .06f, 1.08f, .17f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void render(AntarKilatRound round, boolean steeringEnabled) {
        this.round = round;
        this.steeringEnabled = steeringEnabled;
        if (!steeringEnabled) pointer = -1;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (round == null || getWidth() == 0 || getHeight() == 0) return;
        height = getHeight();
        width = Math.min(getWidth(), height * .95f);
        canvas.drawColor(0xff081833);
        canvas.save();
        canvas.translate((getWidth() - width) * .5f, 0);
        canvas.clipRect(0, 0, width, height);
        village.draw(canvas, width, height, round.elapsed / AntarKilatRound.TRAVEL_TIME,
                round.riderLane - 1, HORIZON, RIDER_GROUND);
        drawMovingRoad(canvas);
        for (int i = round.objects.size() - 1; i >= 0; i--) {
            drawObject(canvas, round.objects.get(i));
        }
        drawCourier(canvas);
        canvas.restore();
    }

    private void drawMovingRoad(Canvas canvas) {
        float distance = round.elapsed / AntarKilatRound.TRAVEL_TIME;
        float marks = distance % .18f;
        for (float depth = marks - .18f; depth < 1.08f; depth += .18f) {
            float start = Math.max(0, depth);
            float end = Math.min(1.08f, depth + .085f);
            if (end <= start) continue;
            paint.setColor(0xbfedefff);
            drawStripe(canvas, -.5f, start, end);
            drawStripe(canvas, .5f, start, end);
        }
        // Small glints in the asphalt travel with the same projection as the road objects.
        for (int i = 0; i < 45; i++) {
            float depth = (distance + i * .1439f) % 1.08f;
            float lane = ((i * 53 % 97) / 96f - .5f) * 2.95f;
            float x = roadX(lane, depth);
            float y = roadY(depth);
            float size = width * .013f * depth * depth;
            paint.setColor(i % 5 == 0 ? 0x35ffc080 : 0x237aa7c8);
            canvas.drawOval(x - size * .25f, y - size, x + size * .25f, y + size, paint);
        }
    }

    private void drawStripe(Canvas canvas, float lane, float start, float end) {
        shape.reset();
        shape.moveTo(roadX(lane - .018f, start), roadY(start));
        shape.lineTo(roadX(lane + .018f, start), roadY(start));
        shape.lineTo(roadX(lane + .018f, end), roadY(end));
        shape.lineTo(roadX(lane - .018f, end), roadY(end));
        shape.close();
        canvas.drawPath(shape, paint);
    }

    private float roadX(float lane, float depth) {
        float cameraShift = (round.riderLane - 1) * (.014f + .025f * depth);
        return width * (.5f - cameraShift + lane * (.008f + .30f * depth * depth));
    }

    private float roadY(float depth) {
        return height * (HORIZON + (RIDER_GROUND - HORIZON) * depth * depth);
    }

    private void drawObject(Canvas canvas, AntarKilatRound.ObjectOnRoad object) {
        float depth = object.depth;
        float x = roadX(object.lane - 1, depth);
        float y = roadY(depth);
        float size = width * (.005f + .088f * depth * depth);
        paint.setColor(0x5b050a18);
        canvas.drawOval(x - size, y - size * .19f, x + size, y + size * .19f, paint);
        if (object.kind == AntarKilatRound.Kind.PARCEL) {
            drawParcel(canvas, x, y, size, object.lane);
        } else if (object.kind == AntarKilatRound.Kind.PUDDLE) {
            drawPuddle(canvas, x, y, size);
        } else {
            drawPothole(canvas, x, y, size);
        }
    }

    private void drawParcel(Canvas canvas, float x, float ground, float size, int lane) {
        float y = ground - size * (.57f + .14f * (float) Math.sin(round.elapsed * 7 + lane));
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size, size);
        paint.setColor(Color.WHITE);
        paint.setShader(goldGlow);
        canvas.drawCircle(0, 0, 1.35f, paint);
        paint.setShader(null);
        fill(canvas, 0xffffd566, parcelFaces[0]);
        fill(canvas, 0xfff9ab27, parcelFaces[1]);
        fill(canvas, 0xffdc7914, parcelFaces[2]);
        fill(canvas, 0xfffff1ad, parcelFaces[3]);
        fill(canvas, 0xffffcc68, parcelFaces[4]);
        float twinkle = .10f + .06f * (float) Math.sin(round.elapsed * 10 + lane);
        paint.setColor(0xffffefad);
        canvas.drawOval(.83f - twinkle, -.62f, .83f + twinkle, -.56f, paint);
        canvas.drawOval(.80f, -.59f - twinkle, .86f, -.59f + twinkle, paint);
        canvas.restore();
    }

    private void drawPuddle(Canvas canvas, float x, float y, float size) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size, size);
        shape.reset();
        shape.moveTo(-.98f, .06f);
        shape.cubicTo(-1.13f, -.35f, -.23f, -.4f, .04f, -.27f);
        shape.cubicTo(.29f, -.17f, .55f, -.34f, .86f, -.12f);
        shape.cubicTo(1.28f, .18f, .72f, .33f, .31f, .25f);
        shape.cubicTo(-.11f, .16f, -.82f, .34f, -.98f, .06f);
        shape.close();
        paint.setColor(0xff56bce9);
        canvas.drawPath(shape, paint);
        canvas.scale(.91f, .79f);
        paint.setColor(0xff154a78);
        canvas.drawPath(shape, paint);
        paint.setColor(0xff88d8ff);
        canvas.drawOval(-.73f, -.15f, .04f, -.10f, paint);
        canvas.drawOval(.08f, .06f, .77f, .095f, paint);
        paint.setColor(0xa8ffce8b);
        canvas.drawOval(-.37f, .025f, -.12f, .066f, paint);
        canvas.restore();
    }

    private void drawPothole(Canvas canvas, float x, float y, float size) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(size, size);
        fill(canvas, 0xff68636a, holeFaces[0]);
        fill(canvas, 0xff17131c, holeFaces[1]);
        fill(canvas, 0xffaaa1a0, holeFaces[2]);
        fill(canvas, 0xff837779, holeFaces[3]);
        canvas.restore();
    }

    private void drawCourier(Canvas canvas) {
        float ground = roadY(1);
        float x = roadX(round.riderLane - 1, 1);
        float bikeHeight = Math.min(height * .40f, width * .44f);
        float bikeWidth = bikeHeight * courier.getWidth() / courier.getHeight();
        float bounce = width * (.0025f * (float) Math.sin(round.elapsed * 21)
                + .026f * (round.impact / .32f) * (float) Math.sin(round.impact * 19));
        paint.setColor(0x70030c1b);
        canvas.drawOval(x - bikeWidth * .42f, ground - bikeHeight * .071f,
                x + bikeWidth * .42f, ground + bikeHeight * .038f, paint);
        canvas.save();
        canvas.rotate(15 * round.lean, x, ground);
        bounds.set(x - bikeWidth / 2, ground - bikeHeight - bounce,
                x + bikeWidth / 2, ground - bounce);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(courier, null, bounds, paint);
        canvas.restore();

        float feedback = Math.max(round.impact / .32f, round.pickup / .55f);
        if (feedback <= 0) return;
        paint.setColor(round.impact > 0 ? 0xfffd986e : 0xffffdc72);
        paint.setAlpha((int) (feedback * 255));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(bikeHeight * .14f);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String points = round.impact > 0 ? (round.penalty == 0 ? "Tabrakan" : "−" + round.penalty) : "+10";
        canvas.drawText(points, x, ground - bikeHeight * (1.12f + .22f * (1 - feedback)), paint);
        paint.setAlpha(255);
    }

    private static Path polygon(float... points) {
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.close();
        return path;
    }

    private void fill(Canvas canvas, int color, Path path) {
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!steeringEnabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointer = event.getPointerId(0);
                touchStart = event.getX();
                swiped = false;
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                int index = event.findPointerIndex(pointer);
                if (!swiped && index >= 0) {
                    float offset = event.getX(index) - touchStart;
                    if (Math.abs(offset) >= swipeThreshold) {
                        round.steer(offset < 0 ? -1 : 1);
                        swiped = true;
                    }
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) pointer = -1;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointer) pointer = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                pointer = -1;
                return true;
            default:
                return true;
        }
    }
}
