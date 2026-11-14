package com.transiva.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/** Draws roadside scenery in the same depth space as the collectibles and lane markings. */
final class AntarKilatVillage {
    private static final float ROW_SPACING = .16f;
    private static final float FAR_EDGE = AntarKilatPerspective.END_DEPTH;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path shape = new Path();
    private final RectF bounds = new RectF();
    private final Bitmap[] props;
    private final Shader lampHalo = new RadialGradient(0, 0, 1,
            new int[]{0xafffe6a6, 0x56ffb558, 0x00ff8b32}, null, Shader.TileMode.CLAMP);
    private final Shader sky = new LinearGradient(0, 0, 0, 1, 0xff081d48, 0xff7395cf, Shader.TileMode.CLAMP);
    private Shader asphalt;
    private Shader roadside;
    private float roadHeight;
    private float width;
    private float height;
    private float horizon;

    AntarKilatVillage(Resources resources) {
        props = new Bitmap[]{
                BitmapFactory.decodeResource(resources, R.drawable.antar_kilat_warung),
                BitmapFactory.decodeResource(resources, R.drawable.antar_kilat_house),
                BitmapFactory.decodeResource(resources, R.drawable.antar_kilat_cafe),
                BitmapFactory.decodeResource(resources, R.drawable.antar_kilat_palm),
                BitmapFactory.decodeResource(resources, R.drawable.antar_kilat_plant),
                BitmapFactory.decodeResource(resources, R.drawable.antar_kilat_cart)
        };
    }

    void draw(Canvas canvas, float width, float height, float distance) {
        this.width = width;
        this.height = height;
        horizon = height * AntarKilatPerspective.HORIZON;
        if (asphalt == null || height != roadHeight) {
            roadHeight = height;
            asphalt = new LinearGradient(0, horizon, 0, height,
                    0xff58647c, 0xff30394d, Shader.TileMode.CLAMP);
            roadside = new LinearGradient(0, horizon, 0, height,
                    0xff405869, 0xff263d3a, Shader.TileMode.CLAMP);
        }
        drawLandscape(canvas);
        paint.setColor(Color.WHITE);
        paint.setShader(roadside);
        canvas.drawRect(0, horizon, width, height, paint);
        paint.setShader(asphalt);
        band(canvas, -1.51f, 1.51f, 0, FAR_EDGE);
        paint.setShader(null);

        paint.setColor(0xff68767d);
        band(canvas, -1.79f, -1.50f, 0, FAR_EDGE);
        band(canvas, 1.50f, 1.79f, 0, FAR_EDGE);

        int first = (int) Math.floor(-distance / ROW_SPACING);
        for (int row = first; ; row++) {
            float depth = row * ROW_SPACING + distance;
            if (depth > FAR_EDGE) break;
            drawCurb(canvas, depth, row);
        }
        for (int row = first; ; row++) {
            float depth = row * ROW_SPACING + distance;
            if (depth > FAR_EDGE) break;
            if (depth < .025f) continue;
            drawRoadside(canvas, depth, row, -1, distance);
            drawRoadside(canvas, depth, row, 1, distance);
        }
    }

    private void drawLandscape(Canvas canvas) {
        canvas.save();
        canvas.scale(width, horizon);
        paint.setColor(Color.WHITE);
        paint.setShader(sky);
        canvas.drawRect(0, 0, 1, 1, paint);
        paint.setShader(null);
        paint.setColor(0x257bade5);
        canvas.drawOval(-.05f, .23f, .43f, .265f, paint);
        canvas.drawOval(.59f, .42f, .96f, .45f, paint);
        paint.setColor(0xff4a6695);
        shape.reset();
        shape.moveTo(-.08f, 1);
        shape.lineTo(-.02f, .75f);
        shape.lineTo(.17f, .64f);
        shape.lineTo(.28f, .73f);
        shape.lineTo(.41f, .62f);
        shape.lineTo(.55f, .83f);
        shape.lineTo(.76f, .66f);
        shape.lineTo(.91f, .75f);
        shape.lineTo(1.09f, .61f);
        shape.lineTo(1.09f, 1);
        shape.close();
        canvas.drawPath(shape, paint);
        paint.setColor(0xff314a68);
        shape.reset();
        shape.moveTo(-.08f, 1);
        shape.lineTo(-.08f, .85f);
        shape.quadTo(.22f, .69f, .49f, .99f);
        shape.quadTo(.74f, .75f, 1.09f, .85f);
        shape.lineTo(1.09f, 1);
        shape.close();
        canvas.drawPath(shape, paint);
        canvas.restore();
    }

    private void drawCurb(Canvas canvas, float depth, int row) {
        float end = Math.min(FAR_EDGE, depth + ROW_SPACING);
        float start = Math.max(0, depth);
        if (end <= start) return;
        paint.setColor((row & 1) == 0 ? 0xff89898b : 0xff707783);
        band(canvas, -1.60f, -1.50f, start, end);
        band(canvas, 1.50f, 1.60f, start, end);
    }

    private void drawRoadside(Canvas canvas, float depth, int row, int side, float distance) {
        int slot = ((row + (side == 1 ? 3 : 0)) % 8 + 8) % 8;
        float base = x(side * 1.67f, depth);
        float groundY = y(depth);
        float scale = AntarKilatPerspective.scale(depth) * width;
        switch (slot) {
            case 0:
            case 4:
                drawProp(canvas, props[0], base, groundY, scale * .48f, side);
                break;
            case 2:
                drawProp(canvas, props[1], base, groundY, scale * .46f, side);
                break;
            case 6:
                drawProp(canvas, props[2], base, groundY, scale * .46f, side);
                break;
            case 1:
            case 5:
                drawProp(canvas, props[3], base, groundY, scale * .37f, side);
                drawLamp(canvas, x(side * 1.58f, depth), groundY, scale, side, distance * 1.6f + row);
                break;
            case 3:
                drawProp(canvas, props[5], base, groundY, scale * .25f, side);
                break;
            default:
                drawProp(canvas, props[4], base, groundY, scale * .17f, side);
                drawLamp(canvas, x(side * 1.58f, depth), groundY, scale, side, distance * 1.6f + row);
                break;
        }
    }

    private void drawProp(Canvas canvas, Bitmap sprite, float x, float y,
                          float spriteWidth, int side) {
        if (spriteWidth < 2) return;
        float spriteHeight = spriteWidth * sprite.getHeight() / sprite.getWidth();
        float anchor = x + side * spriteWidth * .46f;
        paint.setColor(0x66101827);
        canvas.drawOval(anchor - spriteWidth * .44f, y - spriteWidth * .07f,
                anchor + spriteWidth * .44f, y + spriteWidth * .05f, paint);
        canvas.save();
        canvas.translate(anchor, y);
        if (side == 1) canvas.scale(-1, 1);
        bounds.set(-spriteWidth / 2, -spriteHeight, spriteWidth / 2, 0);
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(sprite, null, bounds, paint);
        canvas.restore();
    }

    private void drawLamp(Canvas canvas, float x, float y, float scale, int side, float phase) {
        if (scale < 4) return;
        float top = y - scale * .34f;
        float bulb = x - side * scale * .055f;
        paint.setColor(0xff222a3d);
        paint.setStrokeWidth(Math.max(1, scale * .009f));
        canvas.drawLine(x, y, x, top, paint);
        canvas.drawLine(x, top, bulb, top, paint);
        canvas.save();
        canvas.translate(bulb, top);
        float radius = scale * (.075f + .003f * (float) Math.sin(phase));
        canvas.scale(radius, radius);
        paint.setColor(Color.WHITE);
        paint.setShader(lampHalo);
        canvas.drawCircle(0, 0, 1, paint);
        paint.setShader(null);
        paint.setColor(0xffffe3a2);
        canvas.drawOval(-.22f, -.105f, .22f, .095f, paint);
        canvas.restore();
    }

    private float x(float lane, float depth) {
        return AntarKilatPerspective.x(lane, depth, width);
    }

    private float y(float depth) {
        return AntarKilatPerspective.y(depth, height);
    }

    private void band(Canvas canvas, float left, float right, float start, float end) {
        shape.reset();
        shape.moveTo(x(left, start), y(start));
        shape.lineTo(x(right, start), y(start));
        shape.lineTo(x(right, end), y(end));
        shape.lineTo(x(left, end), y(end));
        shape.close();
        canvas.drawPath(shape, paint);
    }
}
