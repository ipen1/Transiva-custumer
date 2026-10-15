package com.transiva.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** In-app drag/pinch crop. No external crop intent, storage permission, or new dependency. */
final class ProfilePhotoCropDialog extends Dialog {
    interface Result { void chosen(Bitmap bitmap); }
    private Bitmap source;
    private final Result result;
    private final Runnable cancelled;
    private boolean accepted;
    private CropView crop;
    ProfilePhotoCropDialog(Activity activity, Bitmap source, Result result, Runnable cancelled) {
        super(activity, android.R.style.Theme_Material_Light_NoActionBar); this.source = source; this.result = result; this.cancelled = cancelled;
    }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(getContext()); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(20)); root.setBackgroundColor(Color.rgb(11, 24, 43));
        TextView title = new TextView(getContext()); title.setText("Sesuaikan foto profil");
        title.setTextColor(Color.WHITE); title.setTextSize(21); title.setTypeface(null, 1); root.addView(title);
        TextView hint = new TextView(getContext()); hint.setText("Geser foto • Cubit untuk memperbesar");
        hint.setTextColor(Color.rgb(194, 215, 239)); hint.setTextSize(13);
        hint.setPadding(0, dp(8), 0, dp(12)); root.addView(hint);
        crop = new CropView(); root.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(getContext()); actions.setGravity(Gravity.CENTER);
        Button back = button("Batal"); back.setOnClickListener(v -> dismiss());
        Button reset = button("Atur ulang"); reset.setOnClickListener(v -> crop.reset());
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(48), 1)); root.addView(actions);
        Button save = button("Gunakan & simpan foto"); save.setTextColor(Color.WHITE);
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(11, 124, 255)));
        save.setOnClickListener(v -> {
            if (accepted || crop.geometry.side <= 0) return;
            try {
                Bitmap output = crop.exportBitmap(); accepted = true;
                dismiss(); result.chosen(output);
            } catch (OutOfMemoryError | RuntimeException error) {
                android.widget.Toast.makeText(getContext(), "Foto belum dapat diproses. Coba gunakan foto lain atau tutup aplikasi lain dahulu.", android.widget.Toast.LENGTH_LONG).show();
            }
        });
        root.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        // This is a full-screen media editor, independent of floating AlertDialog sizing/theme.
        CustomerAppSettings.preserveArtwork(root);
        setContentView(root); setCanceledOnTouchOutside(false);
        setOnDismissListener(d -> {
            if (source != null && !source.isRecycled()) source.recycle(); source = null;
            if (!accepted) cancelled.run();
        });
    }
    private Button button(String title) {
        Button b = new Button(getContext()); b.setText(title); b.setTextSize(13); b.setAllCaps(false);
        return b;
    }
    @Override protected void onStart() {
        super.onStart(); Window window = getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(11, 24, 43)));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(Color.rgb(11, 24, 43)); window.setNavigationBarColor(Color.rgb(11, 24, 43));
        }
    }
    private final class CropView extends View {
        final ProfileCropGeometry geometry = new ProfileCropGeometry();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float left, top, lastX, lastY; boolean dragging;
        final ScaleGestureDetector pinch;
        CropView() {
            super(ProfilePhotoCropDialog.this.getContext());
            setContentDescription("Area potong foto profil. Geser dengan satu jari, perbesar dengan dua jari.");
            pinch = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    geometry.zoom(detector.getScaleFactor(), detector.getFocusX() - left, detector.getFocusY() - top);
                    invalidate(); return true;
                }
            });
        }
        void reset() {
            if (source == null || getWidth() <= 0 || getHeight() <= 0) return;
            float side = Math.max(1, Math.min(getWidth(), getHeight()) - dp(12));
            left = (getWidth() - side) / 2f; top = (getHeight() - side) / 2f;
            geometry.fit(side, source.getWidth(), source.getHeight()); invalidate();
        }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { reset(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if (source == null || geometry.side <= 0) return;
            canvas.save(); canvas.translate(left + geometry.x, top + geometry.y);
            canvas.scale(geometry.scale, geometry.scale); canvas.drawBitmap(source, 0, 0, paint); canvas.restore();
            Path mask = new Path(); mask.setFillType(Path.FillType.EVEN_ODD);
            mask.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            mask.addCircle(left + geometry.side/2, top + geometry.side/2, geometry.side/2, Path.Direction.CW);
            paint.setColor(0xB30B182B); paint.setStyle(Paint.Style.FILL); canvas.drawPath(mask, paint);
            paint.setColor(Color.WHITE); paint.setStrokeWidth(dp(2)); paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(left + geometry.side/2, top + geometry.side/2, geometry.side/2, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            pinch.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = event.getX(); lastY = event.getY(); dragging = true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (dragging && !pinch.isInProgress() && event.getPointerCount() == 1)
                    geometry.drag(event.getX()-lastX, event.getY()-lastY);
                lastX = event.getX(); lastY = event.getY(); invalidate();
            } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                dragging = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                dragging = false; if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        Bitmap exportBitmap() {
            Bitmap output = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output); canvas.scale(720f / geometry.side, 720f / geometry.side);
            canvas.translate(geometry.x, geometry.y); canvas.scale(geometry.scale, geometry.scale);
            canvas.drawBitmap(source, 0, 0, paint); return output;
        }
    }
}
