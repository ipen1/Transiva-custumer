package com.transiva.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public final class DuitkuSandboxPaymentActivity extends Activity {
    private static final int STORAGE_REQUEST = 6932;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = () -> load(false);
    private final Runnable tick = this::updateTimer;
    private final Runnable continueOrder = this::openOrders;
    private CustomerTransportSession owner;
    private String service;
    private String paymentId;
    private String status = "";
    private boolean autoContinue;
    private boolean visible;
    private boolean loading;
    private boolean saving;
    private boolean orderPending;
    private boolean expiryCheckSent;
    private boolean dark;
    private long expiresAt;
    private JSONObject readyReceipt;
    private TextView title;
    private TextView successMark;
    private TextView badge;
    private TextView amount;
    private TextView detail;
    private TextView timer;
    private TextView check;
    private TextView download;
    private TextView activityButton;
    private FrameLayout code;
    private WebView web;
    private Bitmap qrBitmap;
    private String presentation = "";

    static void open(Activity source, JSONObject payment, boolean autoContinue) {
        Intent intent = new Intent(source, DuitkuSandboxPaymentActivity.class)
                .putExtra("id", payment.optString("payment_id"))
                .putExtra("service", payment.optString("service"))
                .putExtra("auto", autoContinue);
        if (autoContinue) source.startActivityForResult(intent, DuitkuSandboxCheckout.REQUEST_PAYMENT);
        else source.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        owner = CustomerTransportSession.capture(this);
        service = getIntent().getStringExtra("service");
        paymentId = getIntent().getStringExtra("id");
        autoContinue = getIntent().getBooleanExtra("auto", false);
        if (paymentId == null || !paymentId.matches("TXS-[a-f0-9]{28}")
                || !("TransRide".equals(service) || "TransCar".equals(service) || "TransFood".equals(service))) {
            finish();
            return;
        }

        dark = CustomerAppSettings.isDarkMode(this);
        CustomerWindowPolicy.status(getWindow(), color(dark ? "#101F35" : "#0C2E61"));
        CustomerWindowPolicy.navigation(getWindow(), color("#071426"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(dark ? "#0B1424" : "#F1F6FC"));
        LinearLayout page = column();
        page.setPadding(dp(16), dp(10), dp(16), dp(18));
        scroll.addView(page);

        TextView back = action("‹ Kembali", false);
        add(page, back, 0);
        back.setOnClickListener(v -> finish());

        LinearLayout summary = column();
        summary.setGravity(Gravity.CENTER_HORIZONTAL);
        summary.setPadding(dp(14), dp(16), dp(14), dp(16));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color("#102A52"), color("#064EB9")});
        gradient.setCornerRadius(dp(22));
        summary.setBackground(gradient);
        add(page, summary, 10);

        successMark = text("✓", 26, "#9EF5CF", true);
        successMark.setGravity(Gravity.CENTER);
        successMark.setBackground(shape("#214E50", "#4BB891", 23));
        successMark.setVisibility(View.GONE);
        summary.addView(successMark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        title = text("Pembayaran QRIS", 20, "#FFFFFF", true);
        title.setGravity(Gravity.CENTER);
        add(summary, title, 4);
        TextView serviceName = text(service, 13, "#D1E5FF", false);
        add(summary, serviceName, 4);
        serviceName.setGravity(Gravity.CENTER);
        amount = text("—", 32, "#FFFFFF", true);
        add(summary, amount, 8);
        amount.setGravity(Gravity.CENTER);
        badge = text("Memuat tagihan…", 13, "#E4F0FF", true);
        add(summary, badge, 5);
        badge.setGravity(Gravity.CENTER);
        badge.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        LinearLayout panel = column();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(shape(dark ? "#15243B" : "#FFFFFF", dark ? "#2D415E" : "#D7E4F3", 20));
        add(page, panel, 12);
        timer = text("", 14, dark ? "#9FC8FF" : "#1456A8", true);
        timer.setGravity(Gravity.CENTER);
        timer.setVisibility(View.GONE);
        panel.addView(timer, new LinearLayout.LayoutParams(-1, -2));
        code = new FrameLayout(this);
        code.setVisibility(View.GONE);
        add(panel, code, 8);

        download = action("Simpan QRIS ke galeri", true);
        download.setVisibility(View.GONE);
        download.setOnClickListener(v -> requestDownload());
        add(panel, download, 8);
        detail = text("", 12, dark ? "#B8CAE0" : "#506782", false);
        detail.setTextIsSelectable(true);
        detail.setLineSpacing(dp(3), 1f);
        add(panel, detail, 8);

        check = action("Cek Status", false);
        check.setOnClickListener(v -> load(true));
        add(page, check, 8);
        activityButton = action("Buka Aktivitas", true);
        activityButton.setVisibility(View.GONE);
        activityButton.setOnClickListener(v -> openOrders());
        add(page, activityButton, 8);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (check == null) return;
        visible = true;
        if (readyReceipt != null) scheduleContinuation();
        else {
            updateTimer();
            load(false);
        }
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        disposeWeb();
        super.onDestroy();
    }

    private void load(boolean checkProvider) {
        handler.removeCallbacks(poll);
        if (!visible || isFinishing() || isDestroyed() || loading) return;
        if (!owner.matches(this)) {
            finish();
            return;
        }
        loading = true;
        check.setEnabled(false);
        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject request = new JSONObject().put("service", service).put("payment_id", paymentId)
                        .put("check_provider", checkProvider);
                JSONObject response = TransivaHttpRepository.postJson(this, ApiConfig.server("duitku_sandbox_status.php"),
                        request, 12000, owner);
                JSONObject payment = response.optJSONObject("sandbox_payment");
                runOnUiThread(() -> finishLoad(response.optBoolean("success") ? payment : null,
                        response.optBoolean("provider_check_pending"), checkProvider));
            } catch (Exception error) {
                runOnUiThread(() -> finishLoad(null, true, checkProvider));
            }
        }, () -> finishLoad(null, true, checkProvider));
    }

    private void finishLoad(JSONObject payment, boolean unavailable, boolean manual) {
        if (isFinishing() || isDestroyed()) return;
        loading = false;
        check.setEnabled(true);
        if (!owner.matches(this)) {
            finish();
            return;
        }
        if (payment != null) render(payment, unavailable);
        else if (manual || status.isEmpty()) {
            Toast.makeText(this, "Status belum bisa diperbarui. Coba lagi.", Toast.LENGTH_SHORT).show();
        }
        if (visible && (status.isEmpty() || "pending".equals(status) || "creating".equals(status)
                || "expired".equals(status) && autoContinue || orderPending)) {
            handler.postDelayed(poll, "expired".equals(status) ? 15000 : 5000);
        }
    }

    private void render(JSONObject payment, boolean unavailable) {
        status = payment.optString("status");
        orderPending = payment.optBoolean("order_pending");
        JSONObject receipt = payment.optJSONObject("order_receipt");
        String orderId = receipt == null ? "" : receipt.optString("order_id");
        boolean paid = "paid".equals(status);
        title.setText(paid ? "Pembayaran berhasil" : "Pembayaran QRIS");
        if (paid && successMark.getVisibility() != View.VISIBLE) {
            successMark.setVisibility(View.VISIBLE);
            successMark.setAlpha(0f);
            successMark.setScaleX(0.8f);
            successMark.setScaleY(0.8f);
            successMark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();
        }
        badge.setText(orderPending ? "Menyiapkan pesanan…" : DuitkuSandboxCheckout.historyStatus(status));
        badge.setTextColor(color(paid ? "#9EF5CF" : "#E4F0FF"));
        amount.setText(DuitkuSandboxCheckout.rupiah(payment));
        String channel = "NQ".equals(payment.optString("channel")) ? "Nobu" : "ShopeePay";
        StringBuilder info = new StringBuilder("QRIS ").append(channel).append("\nTagihan  ").append(paymentId);
        String provider = value(payment, "provider_reference");
        if (!provider.isEmpty()) info.append("\nReferensi Duitku  ").append(provider);
        if (!orderId.isEmpty()) info.append("\nPesanan  ").append(orderId);
        String paidAt = value(payment, "paid_at");
        if (!paidAt.isEmpty()) info.append("\nWaktu pembayaran  ").append(paidAt);
        if (unavailable) info.append("\nStatus dari Duitku belum diperbarui.");
        detail.setText(info.toString());

        long expiry = payment.optLong("expires_epoch");
        long serverTime = payment.optLong("server_epoch", System.currentTimeMillis() / 1000);
        expiresAt = expiry > 0 ? SystemClock.elapsedRealtime() + (expiry - serverTime) * 1000L : 0;
        renderCode(payment);
        updateTimer();
        check.setVisibility(paid && !orderPending ? View.GONE : View.VISIBLE);
        activityButton.setVisibility(paid && !orderId.isEmpty() ? View.VISIBLE : View.GONE);
        if (paid && !orderId.isEmpty() && readyReceipt == null) {
            readyReceipt = receipt;
            try { DuitkuSandboxCheckout.acknowledgeFood(this, receipt); }
            catch (Exception ignored) { /* Activity history still contains the order. */ }
            scheduleContinuation();
        }
    }

    private void updateTimer() {
        handler.removeCallbacks(tick);
        if ((!isAwaitingPayment() && !"expired".equals(status)) || expiresAt == 0) {
            timer.setVisibility(View.GONE);
            return;
        }
        long seconds = Math.max(0, (expiresAt - SystemClock.elapsedRealtime() + 999L) / 1000L);
        if ("expired".equals(status)) seconds = 0;
        timer.setVisibility(View.VISIBLE);
        timer.setText(seconds == 0 ? "Masa pembayaran berakhir"
                : String.format(Locale.ROOT, "Berlaku %02d:%02d", seconds / 60, seconds % 60));
        if (seconds == 0) {
            code.setVisibility(View.GONE);
            download.setVisibility(View.GONE);
            disposeWeb();
            if (visible && !loading && !expiryCheckSent) {
                expiryCheckSent = true;
                handler.post(() -> load(true));
            }
        } else if (visible) {
            handler.postDelayed(tick, 1000);
        }
    }

    private boolean isAwaitingPayment() {
        return "pending".equals(status) || "creating".equals(status);
    }

    private void scheduleContinuation() {
        handler.removeCallbacks(continueOrder);
        if (visible && autoContinue) handler.postDelayed(continueOrder, 1700);
    }

    private void renderCode(JSONObject payment) {
        String qr = value(payment, "qr_text");
        String url = value(payment, "payment_url");
        String next = "pending".equals(status) ? qr + "\n" + url : "";
        if (next.equals(presentation)) return;
        presentation = next;
        disposeWeb();
        code.removeAllViews();
        qrBitmap = null;
        code.setVisibility(View.GONE);
        download.setVisibility(View.GONE);
        if (!"pending".equals(status)) return;
        if (qr.startsWith("000201") && qr.length() <= 1500) {
            try {
                renderNativeQr(qr);
                return;
            } catch (Exception ignored) { /* Use the provider checkout for unavailable native QR. */ }
        }
        if (trusted(Uri.parse(url))) showHostedQr(url);
    }

    private void renderNativeQr(String qr) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, 720, 720);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        qrBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        ImageView image = new ImageView(this);
        image.setContentDescription("Kode QRIS untuk pembayaran");
        image.setImageBitmap(qrBitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = Math.min(dp(232), Math.max(dp(1), getResources().getDisplayMetrics().widthPixels - dp(64)));
        code.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        code.setVisibility(View.VISIBLE);
        download.setVisibility(View.VISIBLE);
    }

    private void requestDownload() {
        if (qrBitmap == null || !isAwaitingPayment() || saving
                || expiresAt != 0 && expiresAt <= SystemClock.elapsedRealtime()) return;
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST);
            return;
        }
        Bitmap image = qrBitmap;
        saving = true;
        download.setEnabled(false);
        TransivaNetworkExecutor.execute(() -> {
            try {
                saveQr(image);
                runOnUiThread(() -> finishSave("QRIS tersimpan di galeri."));
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> finishSave("QRIS belum bisa disimpan. Coba lagi."));
            }
        }, () -> finishSave("QRIS belum bisa disimpan. Coba lagi."));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) requestDownload();
            else Toast.makeText(this, "Izin penyimpanan dibutuhkan untuk menyimpan QRIS.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveQr(Bitmap image) throws IOException {
        String filename = "Transiva-QRIS-" + paymentId.substring(4, 12) + "-" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues photo = new ContentValues();
            photo.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            photo.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            photo.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Transiva");
            photo.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo);
            if (uri == null) throw new IOException("MediaStore insert failed");
            boolean published = false;
            try {
                try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                    writeQr(image, stream);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                published = getContentResolver().update(uri, ready, null, null) > 0;
                if (!published) throw new IOException("MediaStore publish failed");
            } finally {
                if (!published) getContentResolver().delete(uri, null, null);
            }
            return;
        }
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Transiva");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create gallery folder");
        File file = new File(folder, filename);
        try (OutputStream stream = new FileOutputStream(file)) {
            writeQr(image, stream);
        } catch (IOException | RuntimeException error) {
            file.delete();
            throw error;
        }
        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
    }

    private static void writeQr(Bitmap image, OutputStream stream) throws IOException {
        if (stream == null || !image.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
            throw new IOException("Cannot write QR image");
        }
    }

    private void finishSave(String message) {
        if (isFinishing() || isDestroyed()) return;
        saving = false;
        download.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showHostedQr(String url) {
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !trusted(request.getUrl());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String destination) {
                return !trusted(Uri.parse(destination));
            }
        });
        code.addView(web, new FrameLayout.LayoutParams(-1, dp(420)));
        code.setVisibility(View.VISIBLE);
        web.loadUrl(url);
    }

    private static boolean trusted(Uri uri) {
        String host = uri.getHost();
        return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                && ("sandbox.duitku.com".equals(host) || "app-sandbox.duitku.com".equals(host) || "api-sandbox.duitku.com".equals(host));
    }

    private void disposeWeb() {
        if (web == null) return;
        web.stopLoading();
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        web = null;
    }

    private void openOrders() {
        if (readyReceipt == null || isFinishing() || isDestroyed()) return;
        if (!owner.matches(this)) {
            finish();
            return;
        }
        handler.removeCallbacks(continueOrder);
        if (autoContinue) {
            setResult(RESULT_OK, new Intent().putExtra("order_receipt", readyReceipt.toString()));
        } else {
            startActivity(new Intent(this, CustomerHistoryActivity.class));
        }
        finish();
    }

    private static String value(JSONObject source, String key) {
        return source.isNull(key) ? "" : source.optString(key, "");
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        parent.addView(child, params);
    }

    private TextView text(String value, int size, String tint, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(tint));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView action(String label, boolean primary) {
        Button control = new Button(this);
        control.setAllCaps(false);
        control.setStateListAnimator(null);
        control.setText(label);
        control.setTextSize(14);
        control.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        control.setGravity(Gravity.CENTER);
        control.setTextColor(color(primary ? "#FFFFFF" : dark ? "#AAD1FF" : "#1557A6"));
        control.setMinHeight(dp(46));
        control.setPadding(dp(10), dp(8), dp(10), dp(8));
        String fill = primary ? "#0964DD" : dark ? "#172A46" : "#FFFFFF";
        String border = primary ? "#0964DD" : dark ? "#334D70" : "#D5E3F4";
        control.setBackground(new RippleDrawable(ColorStateList.valueOf(0x257CB5FF), shape(fill, border, 13), null));
        return control;
    }

    private GradientDrawable shape(String fill, String stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setStroke(dp(1), color(stroke));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private static int color(String hex) {
        return Color.parseColor(hex);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
