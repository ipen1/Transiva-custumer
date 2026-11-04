package com.transiva.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/** Decorative matching animation. Availability and offer state come only from server responses. */
public final class CustomerMatchmakingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Drawable vehicle;
    private final boolean shop;
    private final Drawable shopPin, homePin, scooter;
    private final Path shopRoute = new Path();
    private final PathMeasure routeMeasure;
    private final float[] glintPosition = new float[2];
    private final Shader shopLight;
    private long shopStartedAt = -1;
    private boolean attached, enabled, stopped;
    private int count, phase = 1;
    private String offer = "";
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!canAnimate()) return;
            invalidate();
            postDelayed(this, Math.max(33, CustomerPerformanceManager.animationFrame(getContext())));
        }
    };

    public CustomerMatchmakingView(Context context, boolean car) {
        this(context, car, false);
    }

    public CustomerMatchmakingView(Context context, boolean car, boolean shop) {
        super(context);
        this.shop = shop;
        shopPin = shop ? context.getDrawable(R.drawable.map_shop_pickup_pin) : null;
        homePin = shop ? context.getDrawable(R.drawable.map_shop_delivery_pin) : null;
        scooter = shop ? context.getDrawable(R.drawable.map_motor_top) : null;
        shopRoute.moveTo(58, 96);
        shopRoute.cubicTo(97, 96, 103, 70, 160, 70);
        shopRoute.cubicTo(217, 70, 223, 96, 262, 96);
        routeMeasure = new PathMeasure(shopRoute, false);
        shopLight = new RadialGradient(160, 71, 131,
                new int[]{0xFF422B77, 0xFF191D43, 0xFF11172F}, null, Shader.TileMode.CLAMP);
        int id = getResources().getIdentifier(car ? "ic_service_car_premium" : "ic_service_ride_premium",
                "drawable", context.getPackageName());
        vehicle = id == 0 ? null : context.getDrawable(id);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateDescription();
    }
    public void setSearchPhase(int value) { phase = Math.max(1, Math.min(3, value)); updateDescription(); invalidate(); }
    public void setOfferedDriver(String value) { offer = value == null ? "" : value.trim(); updateDescription(); invalidate(); }
    public void setDriverCount(int value) { count = Math.max(0, value); updateDescription(); invalidate(); }
    private void updateDescription() {
        setContentDescription(offer.isEmpty() ? (shop ? "Mencari driver untuk belanja. " : "Mencocokkan perjalanan. ") + count + " driver tersedia di area pencarian."
                : "Penawaran dikirim. Menunggu konfirmasi driver.");
    }
    public void setAnimatingEnabled(boolean value) {
        if (shop && value && shopStartedAt < 0) shopStartedAt = SystemClock.uptimeMillis();
        enabled = value;
        reschedule();
    }
    public void stopMatching() { stopped = true; reschedule(); }
    private boolean canAnimate() { return attached && enabled && !stopped && getWindowVisibility() == VISIBLE && isShown()
            && (!shop || android.os.Build.VERSION.SDK_INT < 26
                || android.animation.ValueAnimator.areAnimatorsEnabled()); }
    private void reschedule() { if (frame == null) return; removeCallbacks(frame); if (canAnimate()) post(frame); invalidate(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; reschedule(); }
    @Override protected void onDetachedFromWindow() { attached = false; removeCallbacks(frame); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); reschedule(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        float density = getResources().getDisplayMetrics().density;
        box(canvas, 0, 0, getWidth(), getHeight(), 22 * density, shop ? "#11172F" : "#0C2443");
        canvas.save();
        float scale = Math.min(getWidth() / 320f, getHeight() / 192f);
        canvas.translate((getWidth() - 320 * scale) / 2f, (getHeight() - 192 * scale) / 2f);
        canvas.scale(scale, scale);
        if (shop) {
            drawShop(canvas);
            canvas.restore();
            return;
        }
        float t = canAnimate() ? (SystemClock.uptimeMillis() % 3600L) / 3600f : 0f;
        float floatY = (float)Math.sin(t * Math.PI * 2) * 3;
        box(canvas, 0, 0, 320, 192, 22, "#0C2443");
        // A connecting path and traveling glints replace the rotating radar entirely.
        paint.setStrokeWidth(2); paint.setColor(Color.parseColor("#264B70"));
        canvas.drawLine(92, 84, 228, 84, paint);
        for (int i = 0; i < 3; i++) {
            float x = 105 + ((t + i / 3f) % 1f) * 109;
            box(canvas, x, 81, x + 9, 87, 3, offer.isEmpty() ? "#42C9F5" : "#50E2B1");
        }
        box(canvas, 24, 38 + floatY, 104, 126 + floatY, 18, "#164073");
        box(canvas, 216, 38 - floatY, 296, 126 - floatY, 18, "#113F4E");
        box(canvas, 42, 50 + floatY, 86, 94 + floatY, 14, "#167CFF");
        label(canvas, "A", 64, 79 + floatY, 22, "#FFFFFF");
        label(canvas, "ANDA", 64, 113 + floatY, 10, "#CEE7FF");
        if (vehicle != null) {
            canvas.save(); canvas.translate(0, -floatY);
            vehicle.setBounds(231, 48, 281, 98); vehicle.draw(canvas); canvas.restore();
        } else label(canvas, "T", 256, 80 - floatY, 24, "#66E4C2");
        label(canvas, "DRIVER", 256, 113 - floatY, 10, "#B9F4E3");
        box(canvas, 137, 64, 183, 104, 14, offer.isEmpty() ? "#167CFF" : "#12A97F");
        label(canvas, offer.isEmpty() ? "⇄" : "✓", 160, 91, 25, "#FFFFFF");
        String caption = !offer.isEmpty() ? "Menunggu konfirmasi driver"
                : count > 0 ? "Mencocokkan perjalanan Anda" : "Menghubungkan perjalanan Anda";
        label(canvas, caption, 160, 154, 12, "#E2F2FF");
        for (int i = 0; i < 3; i++)
            box(canvas, 129 + i * 23, 168, 146 + i * 23, 172, 2,
                    i < phase ? "#45C8F5" : "#244362");
        canvas.restore();
    }
    private void drawShop(Canvas canvas) {
        boolean animated = canAnimate();
        long elapsed = animated ? SystemClock.uptimeMillis() - shopStartedAt : 1500L;
        float intro = Math.min(1f, elapsed / 850f);
        float reveal = 1f - (1f - intro) * (1f - intro);
        float cycle = animated ? (elapsed % 3000L) / 3000f : .4f;
        boolean offered = !offer.isEmpty();
        int accent = offered ? 0xFF67EAC8 : 0xFFC49BFF;

        bounds.set(0, 0, 320, 192);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(shopLight);
        canvas.drawRoundRect(bounds, 22, 22, paint);
        paint.setShader(null);
        paint.setColor(0xFF514571);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        bounds.set(.5f, .5f, 319.5f, 191.5f);
        canvas.drawRoundRect(bounds, 22, 22, paint);

        paint.setColor(0xFF645481);
        paint.setStrokeWidth(2f);
        canvas.drawPath(shopRoute, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float position = (cycle + i / 3f) % 1f;
            routeMeasure.getPosTan(position * routeMeasure.getLength(), glintPosition, null);
            paint.setColor(accent);
            paint.setAlpha((int) (220 * Math.sin(Math.PI * position) * reveal));
            canvas.drawCircle(glintPosition[0], glintPosition[1], 2.7f, paint);
        }

        pin(canvas, shopPin, 58, 96, 40, 52);
        pin(canvas, homePin, 262, 96, 40, 52);
        label(canvas, "TOKO", 58, 117, 10, "#E0C8FF");
        label(canvas, "ANTAR", 262, 117, 10, "#BCD8FF");

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f);
        paint.setColor(accent);
        paint.setAlpha(animated ? (int) (145 * (1f - cycle)) : 100);
        canvas.drawCircle(160, 70, 35 + cycle * 16, paint);
        paint.setStyle(Paint.Style.FILL);
        box(canvas, 125, 35, 195, 105, 35, "#302457");
        canvas.save();
        canvas.scale(.62f + .38f * reveal, .62f + .38f * reveal, 160, 70);
        pin(canvas, scooter, 160, 92, 43, 43);
        canvas.restore();
        label(canvas, offered ? "PENAWARAN TERKIRIM" : "MENCARI DRIVER", 160, 122, 9,
                offered ? "#89F4D5" : "#DCC7FF");

        String caption = offered ? "Menunggu konfirmasi driver"
                : count > 0 ? "Mencocokkan driver untuk belanja"
                : "Menelusuri driver yang tersedia";
        label(canvas, caption, 160, 151, 12, "#F0E9FF");
        for (int i = 0; i < 3; i++) {
            box(canvas, 125 + i * 25, 168, 145 + i * 25, 172, 2,
                    i < phase ? (offered ? "#67EAC8" : "#C49BFF") : "#3F395D");
        }
    }

    private void pin(Canvas canvas, Drawable image, int x, int bottom, int width, int height) {
        if (image == null) return;
        image.setBounds(x - width / 2, bottom - height, x + width / 2, bottom);
        image.draw(canvas);
    }

    private void box(Canvas c, float l, float t, float r, float b, float radius, String color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor(color));
        bounds.set(l, t, r, b); c.drawRoundRect(bounds, radius, radius, paint);
    }
    private void label(Canvas c, String value, float x, float y, float size, String color) {
        paint.setColor(Color.parseColor(color)); paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value, x, y, paint);
    }
}
