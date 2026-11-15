package com.transiva.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

public final class DuitkuSandboxPaymentActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = () -> load(false);
    private CustomerTransportSession owner;
    private String service;
    private String paymentId;
    private boolean autoContinue;
    private boolean visible;
    private boolean loading;
    private boolean pending = true;
    private TextView amount;
    private TextView detail;
    private Button check;
    private Button activityButton;
    private FrameLayout code;
    private WebView web;
    private String presentation = "";
    private int requestNumber;

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
        autoContinue = state == null ? getIntent().getBooleanExtra("auto", false) : state.getBoolean("auto");
        if (paymentId == null || !paymentId.matches("TXS-[a-f0-9]{28}")
                || !("TransRide".equals(service) || "TransCar".equals(service) || "TransFood".equals(service))) {
            finish();
            return;
        }

        CustomerWindowPolicy.status(getWindow(), Color.parseColor("#0B7CFF"));
        CustomerWindowPolicy.navigation(getWindow(), Color.parseColor("#071426"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(8), dp(16), dp(12));
        Button back = new Button(this);
        back.setAllCaps(false);
        back.setText("‹ Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-2, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(text("QRIS · " + service, 18));
        amount = text("", 24);
        body.addView(amount);
        detail = text("Memuat tagihan…", 14);
        detail.setTextIsSelectable(true);
        body.addView(detail);
        code = new FrameLayout(this);
        body.addView(code, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        check = new Button(this);
        check.setAllCaps(false);
        check.setText("Cek Status");
        check.setOnClickListener(v -> load(true));
        root.addView(check);
        activityButton = new Button(this);
        activityButton.setAllCaps(false);
        activityButton.setText("Buka Aktivitas");
        activityButton.setVisibility(View.GONE);
        activityButton.setOnClickListener(v -> openOrders());
        root.addView(activityButton);
        setContentView(root);
        CustomerResponsiveUi.apply(this);
        CustomerAppSettings.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (check == null) return;
        visible = true;
        load(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("auto", autoContinue);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poll);
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
        int generation = ++requestNumber;
        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject request = new JSONObject().put("service", service).put("payment_id", paymentId)
                        .put("check_provider", checkProvider);
                JSONObject response = TransivaHttpRepository.postJson(this, ApiConfig.server("duitku_sandbox_status.php"),
                        request, 12000, owner);
                JSONObject payment = response.optJSONObject("sandbox_payment");
                if (!response.optBoolean("success") || payment == null) throw new IllegalStateException("Status tagihan belum tersedia.");
                runOnUiThread(() -> finishLoad(generation, payment, response.optBoolean("provider_check_pending"), checkProvider));
            } catch (Exception error) {
                runOnUiThread(() -> finishLoad(generation, null, true, checkProvider));
            }
        }, () -> finishLoad(generation, null, true, checkProvider));
    }

    private void finishLoad(int generation, JSONObject payment, boolean unavailable, boolean manual) {
        if (generation != requestNumber || isFinishing() || isDestroyed()) return;
        loading = false;
        check.setEnabled(true);
        if (!owner.matches(this)) {
            finish();
            return;
        }
        if (payment == null) {
            if (manual || amount.getText().length() == 0) detail.setText("Status belum dapat diperbarui. Tekan Cek Status untuk mencoba lagi.");
        } else {
            render(payment, unavailable);
        }
        if (visible && pending) handler.postDelayed(poll, 8000);
    }

    private void render(JSONObject payment, boolean unavailable) {
        String status = payment.optString("status");
        pending = "pending".equals(status) || "creating".equals(status) || payment.optBoolean("order_pending");
        JSONObject receipt = payment.optJSONObject("order_receipt");
        String orderId = receipt == null ? "" : receipt.optString("order_id");
        amount.setText(DuitkuSandboxCheckout.rupiah(payment));
        StringBuilder info = new StringBuilder(DuitkuSandboxCheckout.historyStatus(status));
        info.append("\nMetode: QRIS ").append("NQ".equals(payment.optString("channel")) ? "Nobu" : "ShopeePay");
        info.append("\nTagihan: ").append(paymentId);
        String provider = value(payment, "provider_reference");
        if (!provider.isEmpty()) info.append("\nReferensi Duitku: ").append(provider);
        if (!orderId.isEmpty()) info.append("\nPesanan: ").append(orderId);
        if (payment.optBoolean("order_pending")) info.append("\nPembayaran diterima. Pesanan sedang diproses.");
        String paidAt = value(payment, "paid_at");
        if (!paidAt.isEmpty()) info.append("\nWaktu lunas (server): ").append(paidAt);
        if (unavailable) info.append("\nPengecekan terbaru ke Duitku belum berhasil.");
        detail.setText(info.toString());
        renderCode(payment, status);
        activityButton.setVisibility(orderId.isEmpty() ? View.GONE : View.VISIBLE);
        if (receipt != null && "paid".equals(status) && !orderId.isEmpty()) {
            try { DuitkuSandboxCheckout.acknowledgeFood(this, receipt); }
            catch (Exception ignored) { /* Order remains recoverable from Aktivitas. */ }
            if (visible && autoContinue) {
                autoContinue = false;
                setResult(RESULT_OK, new Intent().putExtra("order_receipt", receipt.toString()));
                finish();
            }
        }
    }

    private void renderCode(JSONObject payment, String status) {
        String qr = value(payment, "qr_text");
        String url = value(payment, "payment_url");
        String next = "pending".equals(status) ? qr + "\n" + url : "";
        if (next.equals(presentation)) return;
        presentation = next;
        disposeWeb();
        code.removeAllViews();
        if (!"pending".equals(status)) return;
        if (qr.startsWith("000201") && qr.length() <= 1500) {
            try {
                int size = 480;
                renderNativeQr(qr, size);
                return;
            } catch (Exception ignored) { /* Hosted QR checkout is available for channels without a native payload. */ }
        }
        if (trusted(Uri.parse(url))) showHostedQr(url);
    }

    private void renderNativeQr(String qr, int size) throws Exception {
        com.google.zxing.common.BitMatrix matrix = new QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, size, size);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        ImageView view = new ImageView(this);
        view.setContentDescription("Kode QRIS untuk pembayaran");
        view.setImageBitmap(bitmap);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        code.addView(view, new FrameLayout.LayoutParams(-1, dp(310), Gravity.CENTER));
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
        code.addView(web, new FrameLayout.LayoutParams(-1, dp(450)));
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
        startActivity(new Intent(this, CustomerHistoryActivity.class));
        finish();
    }

    private static String value(JSONObject source, String key) {
        return source.isNull(key) ? "" : source.optString(key, "");
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
