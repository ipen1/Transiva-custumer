package com.transiva.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerOrderDetailActivity extends Activity {
    private final CustomerLifecycleNetworkScope networkScope =
            new CustomerLifecycleNetworkScope();

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String ACTION_URL = BASE_URL + "server/customer_order_action.php";
    private static final String REVIEW_URL = BASE_URL + "server/save_driver_review.php";
    private static final String TIP_URL = BASE_URL + "server/customer_tip_driver.php";

    private final Handler main = new Handler(Looper.getMainLooper());
    private JSONObject order = new JSONObject();
    private LinearLayout body;
    private ProgressBar progress;
    private int selectedRating = 0;
    private TextView[] starViews;
    private EditText reviewInput;
    private Button submitReviewButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { order = new JSONObject(getIntent().getStringExtra("order_json")); } catch (Exception ignored) {}
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        // Menangani perubahan tema saat halaman detail masih berada di back stack.
        CustomerAppSettings.apply(this);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F8FD"));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(10), dp(18), dp(10));
        top.setBackground(gradient("#087AF5", "#27A8F8", 0));

        TextView back = text("‹", 40, Color.WHITE, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Detail Pesanan", 23, Color.WHITE, true);
        TextView subtitle = text("Informasi perjalanan dan pembayaran", 12, Color.parseColor("#DDF2FF"), false);
        heading.addView(title);
        heading.addView(subtitle);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, -2, 1);
        hlp.setMargins(dp(8), 0, 0, 0);
        top.addView(heading, hlp);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(34));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        setContentView(root);
        CustomerAppSettings.apply(this);
        render();
    }

    private void render() {
        body.removeAllViews();
        String service = first(order.optString("service_name"), order.optString("order_type"), order.optString("service"), "Pesanan Transiva");
        String statusRaw = order.optString("status", "").toLowerCase(Locale.US);
        String status = OrderStatusPresentation.label(statusRaw, service);

        LinearLayout hero = card(24);
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView serviceText = text(service, 25, Color.parseColor("#0B477D"), true);
        heroTop.addView(serviceText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView icon = text(serviceIcon(service), 30, Color.parseColor("#167DF5"), false);
        heroTop.addView(icon);
        hero.addView(heroTop);
        TextView orderId = text("Order #" + first(order.optString("order_id"), order.optString("id"), "-"), 12, Color.parseColor("#7B8CA2"), false);
        LinearLayout.LayoutParams oidLp = new LinearLayout.LayoutParams(-1, -2); oidLp.setMargins(0, dp(4), 0, 0); hero.addView(orderId, oidLp);
        TextView badge = text(status, 13, statusTextColor(statusRaw), true);
        badge.setPadding(dp(13), dp(7), dp(13), dp(7));
        badge.setBackground(round(statusBackground(statusRaw), 99));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2); blp.setMargins(0, dp(12), 0, 0); hero.addView(badge, blp);
        addCard(hero);

        LinearLayout timeline = card(24);
        timeline.addView(sectionHeader("Order Timeline", "Tahapan perjalanan pesanan secara ringkas"));
        timeline.addView(buildTimeline(statusRaw));
        addCard(timeline);

        LinearLayout driverCard = card(24);
        driverCard.addView(sectionHeader("Driver & Kendaraan", "Profil mitra yang menangani pesanan"));

        LinearLayout photos = new LinearLayout(this);
        photos.setOrientation(LinearLayout.HORIZONTAL);
        photos.addView(photoWithLabel("Foto Driver", first(order.optString("driver_photo"), order.optString("photo_driver"))), new LinearLayout.LayoutParams(0, dp(178), 1));
        View spacer = new View(this); photos.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));
        photos.addView(photoWithLabel("Kendaraan", first(order.optString("vehicle_photo"), order.optString("photo_vehicle"))), new LinearLayout.LayoutParams(0, dp(178), 1));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2); plp.setMargins(0, dp(14), 0, dp(12)); driverCard.addView(photos, plp);

        LinearLayout driverInfo = infoPanel();
        driverInfo.addView(infoRow("Nama driver", first(order.optString("driver"), order.optString("driver_name"), order.optString("driver_username"), "Belum ada driver"), "👤"));
        driverInfo.addView(divider());
        driverInfo.addView(infoRow("Kendaraan", vehicleLabel(first(order.optString("driver_type"), order.optString("vehicle_type"), "-")), "🚘"));
        driverInfo.addView(divider());
        driverInfo.addView(infoRow("Nomor polisi", first(order.optString("driver_plate"), order.optString("plate"), "-"), "🔖"));
        double driverRating = order.optDouble("driver_rating", 0);
        driverInfo.addView(divider());
        driverInfo.addView(infoRow("Rating driver", driverRating > 0 ? "★ " + String.format(Locale.US, "%.1f", driverRating) : "Belum ada rating", "⭐"));
        driverCard.addView(driverInfo);
        addCard(driverCard);

        LinearLayout route = card(24);
        route.addView(sectionHeader("Rincian Perjalanan", "Lokasi dan informasi pembayaran"));
        LinearLayout routePanel = infoPanel();
        routePanel.addView(infoRow("Penjemputan", first(order.optString("pickup_address"), order.optString("from_address"), order.optString("restaurant_name"), "-"), "📍"));
        routePanel.addView(divider());
        routePanel.addView(infoRow("Pengantaran", first(order.optString("delivery_address"), order.optString("to_address"), order.optString("destination"), "-"), "🏁"));
        routePanel.addView(divider());
        routePanel.addView(infoRow("Pembayaran", paymentLabel(order.optString("payment_method")), "💳"));

        String deliveryOtp = order.optString("delivery_otp", "").trim();
        if (isPickupOrder() && !isFinishedStatus(statusRaw) && !deliveryOtp.isEmpty()) {
            routePanel.addView(divider());
            routePanel.addView(infoRow("Kode OTP TransSend", deliveryOtp, "🔐"));

            TextView otpHint = text(
                    "Berikan kode OTP kepada driver hanya setelah paket benar-benar Anda terima.",
                    12,
                    Color.parseColor("#52667D"),
                    false
            );
            otpHint.setPadding(dp(14), dp(10), dp(14), 0);
            routePanel.addView(otpHint);
        }
        route.addView(routePanel);

        LinearLayout totalBox = new LinearLayout(this);
        totalBox.setGravity(Gravity.CENTER_VERTICAL);
        totalBox.setPadding(dp(16), dp(15), dp(16), dp(15));
        totalBox.setBackground(gradient("#0D7BF5", "#28A7F8", 18));
        LinearLayout totalLabel = new LinearLayout(this); totalLabel.setOrientation(LinearLayout.VERTICAL);
        totalLabel.addView(text("Total pembayaran", 13, Color.parseColor("#DDF2FF"), false));
        totalLabel.addView(text(paymentLabel(order.optString("payment_method")), 12, Color.WHITE, false));
        totalBox.addView(totalLabel, new LinearLayout.LayoutParams(0, -2, 1));
        totalBox.addView(text(rupiah(order.optDouble("price", 0)), 22, Color.WHITE, true));
        LinearLayout.LayoutParams tbp = new LinearLayout.LayoutParams(-1, -2); tbp.setMargins(0, dp(14), 0, 0); route.addView(totalBox, tbp);
        addCard(route);

        java.util.List<CustomerOrderRichFormatter.Line> serviceDetails = CustomerOrderRichFormatter.detailLines(order);
        if (!serviceDetails.isEmpty()) {
            LinearLayout detailCard = card(24);
            String detailTitle = service.toLowerCase(Locale.US).contains("food") ? "Detail Makanan & Harga" : "Detail Layanan";
            detailCard.addView(sectionHeader(detailTitle, "Rincian lengkap item, layanan, dan komponen pesanan"));
            LinearLayout detailPanel = infoPanel();
            for (int i = 0; i < serviceDetails.size(); i++) {
                CustomerOrderRichFormatter.Line line = serviceDetails.get(i);
                if (i > 0) detailPanel.addView(divider());
                detailPanel.addView(infoRow(line.label, line.value, line.icon));
            }
            detailCard.addView(detailPanel);
            addCard(detailCard);
        }

        double price = order.optDouble("price", 0);
        double original = order.optDouble("original_price", price);
        double requested = order.optDouble("price_change_requested", 0);
        String pcs = order.optString("price_change_status", "none").toLowerCase(Locale.US);
        String reason = order.optString("price_change_reason", "").trim();
        if (Math.abs(original - price) > 0.5 || "pending".equals(pcs) || !reason.isEmpty()) {
            LinearLayout change = card(24);
            change.addView(sectionHeader("Perubahan Harga", "Riwayat penyesuaian total oleh driver"));
            LinearLayout changePanel = infoPanel();
            changePanel.addView(infoRow("Harga awal", rupiah(original), "🏷️"));
            changePanel.addView(divider());
            changePanel.addView(infoRow("Harga " + ("pending".equals(pcs) ? "diajukan" : "akhir"), rupiah("pending".equals(pcs) ? requested : price), "💰"));
            changePanel.addView(divider());
            changePanel.addView(infoRow("Status", priceStatus(pcs), "ℹ️"));
            if (!reason.isEmpty()) { changePanel.addView(divider()); changePanel.addView(infoRow("Catatan driver", reason, "📝")); }
            change.addView(changePanel);
            addCard(change);
        }

        LinearLayout actionBox = card(24);
        actionBox.addView(sectionHeader("Tindakan Pesanan", "Konfirmasi yang diperlukan untuk melanjutkan"));
        boolean received = order.optInt("customer_received", 0) == 1;
        int actionCount = 0;
        if ("arrived_delivery".equals(statusRaw) && !received) {
            Button receive = primary("✓ Terima Pesanan");
            receive.setOnClickListener(v -> confirmAction("confirm_received", "Terima pesanan ini?", "Pastikan pesanan sudah Anda terima dengan baik."));
            actionBox.addView(receive, buttonLp()); actionCount++;
        }
        if ("pending".equals(pcs)) {
            Button approve = primary("Setujui Harga " + rupiah(requested));
            approve.setOnClickListener(v -> confirmAction("approve_price", "Setujui perubahan harga?", "Total pesanan akan berubah menjadi " + rupiah(requested) + "."));
            actionBox.addView(approve, buttonLp());
            Button reject = outline("Tolak Perubahan Harga");
            reject.setOnClickListener(v -> confirmAction("reject_price", "Tolak perubahan harga?", "Harga pesanan tidak akan dinaikkan."));
            actionBox.addView(reject, buttonLp()); actionCount += 2;
        }
        if (actionCount == 0) {
            TextView empty = text(received ? "✓ Pesanan sudah dikonfirmasi diterima." : "Tidak ada konfirmasi yang diperlukan saat ini.", 14, Color.parseColor("#64748B"), false);
            empty.setPadding(0, dp(5), 0, 0); actionBox.addView(empty);
        }
        addCard(actionBox);

        if (isFinishedStatus(statusRaw)) {
            addReviewCard();
            addTipCard();
        }

        // Card driver, foto, panel informasi, dan review dibuat ulang secara dinamis.
        // Terapkan kembali tema customer agar semua view baru langsung mengikuti
        // pilihan Mode Gelap tanpa perlu menutup halaman.
        CustomerAppSettings.applyToView(this, body);
    }

    private View buildTimeline(String currentStatus) {
        LinearLayout box = infoPanel();
        String type = first(order.optString("order_type"), order.optString("service_type"), "").toLowerCase(Locale.US);
        String[] keys; String[] labels;
        if (type.contains("food")) {
            keys = new String[]{"pending","merchant_confirmed","driver_accepted","arrived_pickup","on_delivery","arrived_delivery","finished"};
            labels = new String[]{"Pesanan dibuat","Merchant menerima","Driver menerima","Driver tiba di merchant","Pesanan diantar","Driver tiba di tujuan","Pesanan selesai"};
        } else {
            keys = new String[]{"pending","driver_accepted","arrived_pickup","on_delivery","arrived_delivery","finished"};
            labels = new String[]{"Pesanan dibuat","Driver menerima","Driver tiba di titik jemput","Perjalanan dimulai","Driver tiba di tujuan","Pesanan selesai"};
        }
        int current = timelineIndex(currentStatus, keys);
        for (int i=0;i<labels.length;i++) {
            boolean done = i < current || isFinishedStatus(currentStatus);
            boolean active = i == current && !isFinishedStatus(currentStatus);
            String bullet = done ? "✓" : (active ? "●" : "○");
            String suffix = i == 0 ? "  •  " + first(order.optString("created_at"), "Waktu tidak tersedia") : (active ? "  •  Status saat ini" : "");
            TextView row = text(bullet + "  " + labels[i] + suffix, 14, Color.parseColor(done ? "#047857" : active ? "#0B7CFF" : "#94A3B8"), done || active);
            row.setPadding(0, dp(9), 0, dp(9)); box.addView(row);
            if (i < labels.length-1) box.addView(divider());
        }
        return box;
    }

    private int timelineIndex(String status, String[] keys) {
        status = first(status, "pending").toLowerCase(Locale.US);
        if (status.contains("cancel") || status.contains("reject")) return 0;
        for (int i=0;i<keys.length;i++) if (status.equals(keys[i])) return i;
        if (status.equals("taken")) return Math.min(2, keys.length-1);
        if (status.equals("completed") || status.equals("done") || status.equals("delivered")) return keys.length-1;
        if (status.contains("merchant")) return Math.min(1, keys.length-1);
        return 0;
    }

    private void addReviewCard() {
        int savedRating = Math.max(order.optInt("rating", 0), order.optInt("customer_rating", 0));
        LinearLayout reviewCard = card(24);
        reviewCard.setBackground(gradient("#FFFFFF", "#F7FBFF", 24));
        reviewCard.addView(sectionHeader("Nilai Pelayanan Driver", savedRating > 0 ? "Terima kasih, rating Anda sudah tersimpan" : "Ketuk bintang untuk memberi penilaian"));

        if (savedRating > 0) {
            LinearLayout savedStars = new LinearLayout(this);
            savedStars.setGravity(Gravity.CENTER);
            for (int i = 1; i <= 5; i++) {
                TextView star = text(i <= savedRating ? "★" : "☆", 39, i <= savedRating ? Color.parseColor("#FFB000") : Color.parseColor("#CBD5E1"), true);
                star.setGravity(Gravity.CENTER); savedStars.addView(star, new LinearLayout.LayoutParams(0, dp(56), 1));
            }
            reviewCard.addView(savedStars, new LinearLayout.LayoutParams(-1, -2));
            String savedReview = first(order.optString("review"), order.optString("customer_review"));
            if (!savedReview.isEmpty()) {
                TextView quote = text("“" + savedReview + "”", 14, Color.parseColor("#475569"), false);
                quote.setGravity(Gravity.CENTER); quote.setPadding(dp(12), dp(8), dp(12), 0); reviewCard.addView(quote);
            }
            addCard(reviewCard); return;
        }

        TextView prompt = text("Bagaimana pengalaman Anda bersama " + first(order.optString("driver_name"), order.optString("driver"), "driver") + "?", 15, Color.parseColor("#334155"), false);
        prompt.setGravity(Gravity.CENTER); prompt.setPadding(0, dp(4), 0, dp(8)); reviewCard.addView(prompt);

        starViews = new TextView[5];
        LinearLayout stars = new LinearLayout(this);
        stars.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            TextView star = text("☆", 42, Color.parseColor("#CBD5E1"), true);
            star.setGravity(Gravity.CENTER);
            star.setContentDescription(value + " bintang");
            star.setOnClickListener(v -> selectRating(value, true));
            starViews[i] = star;
            stars.addView(star, new LinearLayout.LayoutParams(0, dp(64), 1));
        }
        reviewCard.addView(stars, new LinearLayout.LayoutParams(-1, -2));

        reviewInput = new EditText(this);
        reviewInput.setHint("Tulis ulasan singkat (opsional)");
        reviewInput.setTextSize(14); reviewInput.setTextColor(Color.parseColor("#243447")); reviewInput.setHintTextColor(Color.parseColor("#94A3B8"));
        reviewInput.setMinLines(2); reviewInput.setMaxLines(4); reviewInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        reviewInput.setBackground(roundStroke("#F8FAFC", "#D5E4F2", 14));
        LinearLayout.LayoutParams rip = new LinearLayout.LayoutParams(-1, -2); rip.setMargins(0, dp(8), 0, 0); reviewCard.addView(reviewInput, rip);

        submitReviewButton = primary("Kirim Rating");
        submitReviewButton.setEnabled(false); submitReviewButton.setAlpha(0.55f);
        submitReviewButton.setOnClickListener(v -> submitReview());
        reviewCard.addView(submitReviewButton, buttonLp());
        addCard(reviewCard);
    }


    private void addTipCard() {
        LinearLayout tipCard = card(24);
        tipCard.setBackground(gradient("#FFF9ED", "#FFFFFF", 24));
        int sentTip = order.optInt("driver_tip_amount", 0);
        tipCard.addView(sectionHeader("Kasih Tip untuk Driver", sentTip > 0 ? "Terima kasih. Tip Anda sudah diterima driver." : "Apresiasi setelah perjalanan selesai • tip masuk penuh ke saldo driver"));

        if (sentTip > 0) {
            TextView done = text("🎉 Tip " + rupiah(sentTip) + " sudah terkirim", 18, Color.parseColor("#9A6700"), true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(dp(12), dp(16), dp(12), dp(16));
            done.setBackground(roundStroke("#FFF8E7", "#FFD37A", 16));
            tipCard.addView(done);
            TextView note = text("Tip tidak dipotong komisi Transiva dan tidak memengaruhi ongkir/order yang sudah selesai.", 12, Color.parseColor("#64748B"), false);
            note.setGravity(Gravity.CENTER); note.setPadding(dp(8), dp(10), dp(8), 0); tipCard.addView(note);
            addCard(tipCard);
            return;
        }

        TextView info = text("Tip dibayar dari saldo Transiva Pay Anda. Driver menerima 100% nominal tip.", 13, Color.parseColor("#5B6472"), false);
        info.setPadding(0, 0, 0, dp(12));
        tipCard.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] amounts = new int[]{2000, 5000, 10000};
        for (int amount : amounts) {
            Button b = outline(rupiah(amount));
            b.setOnClickListener(v -> confirmTip(amount));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(b, lp);
        }
        tipCard.addView(row);

        Button more = primary("Tip Rp20.000");
        more.setOnClickListener(v -> confirmTip(20000));
        tipCard.addView(more, buttonLp());
        addCard(tipCard);
    }

    private void confirmTip(int amount) {
        String driver = first(order.optString("driver_name"), order.optString("driver"), order.optString("driver_username"), "driver");
        new TransivaAlertDialogBuilder(this)
                .setTitle("Kirim tip " + rupiah(amount) + "?")
                .setMessage("Tip akan dipotong dari saldo Transiva Pay Anda dan masuk penuh ke saldo " + driver + ". Tip hanya dapat diberikan satu kali untuk order ini.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Kirim Tip", (d, w) -> sendTip(amount))
                .show();
    }

    private void sendTip(int amount) {
        progress.setVisibility(View.VISIBLE);
        networkScope.newThread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("order_id", first(order.optString("order_id"), order.optString("id")));
                p.put("source", order.optString("source", "").contains("pickup") ? "pickup_orders" : "orders");
                p.put("amount", amount);
                p.put("action", "send");
                JSONObject r = post(TIP_URL, p);
                boolean ok = r.optBoolean("success", false);
                String msg = first(r.optString("message"), ok ? "Tip berhasil dikirim" : "Tip gagal dikirim");
                if (ok) order.put("driver_tip_amount", r.optInt("tip_amount", amount));
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    new TransivaAlertDialogBuilder(this)
                            .setTitle(ok ? "Tip Terkirim 🎉" : "Tip Gagal")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                    if (ok) render();
                });
            } catch (Exception e) {
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    new TransivaAlertDialogBuilder(this).setTitle("Tip Gagal").setMessage("Koneksi server bermasalah. Tip belum diproses.").setPositiveButton("OK", null).show();
                });
            }
        }, "detail-driver-tip").start();
    }

    private void selectRating(int rating, boolean animate) {
        selectedRating = rating;
        for (int i = 0; i < starViews.length; i++) {
            boolean active = i < rating;
            starViews[i].setText(active ? "★" : "☆");
            starViews[i].setTextColor(active ? Color.parseColor("#FFB000") : Color.parseColor("#CBD5E1"));
            if (animate && active) animateStar(starViews[i], i * 55L);
        }
        if (starViews[rating - 1] != null) starViews[rating - 1].performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        submitReviewButton.setEnabled(true); submitReviewButton.setAlpha(1f);
        submitReviewButton.setText(ratingLabel(rating));
    }

    private void animateStar(View star, long delay) {
        star.setScaleX(0.6f); star.setScaleY(0.6f); star.setRotation(-18f); star.setAlpha(0.35f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(star, View.SCALE_X, 0.6f, 1.28f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(star, View.SCALE_Y, 0.6f, 1.28f, 1f);
        ObjectAnimator rot = ObjectAnimator.ofFloat(star, View.ROTATION, -18f, 12f, 0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(star, View.ALPHA, 0.35f, 1f);
        AnimatorSet set = new AnimatorSet(); set.playTogether(sx, sy, rot, alpha); set.setDuration(430); set.setStartDelay(delay); set.setInterpolator(new OvershootInterpolator(1.8f)); set.start();
    }

    private void submitReview() {
        if (selectedRating < 1) { Toast.makeText(this, "Pilih minimal 1 bintang", Toast.LENGTH_SHORT).show(); return; }
        progress.setVisibility(View.VISIBLE); submitReviewButton.setEnabled(false); submitReviewButton.setText("Mengirim...");
        final String review = reviewInput == null ? "" : reviewInput.getText().toString().trim();
        networkScope.newThread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("order_id", first(order.optString("order_id"), order.optString("id")));
                payload.put("source", order.optString("source", "").contains("pickup") ? "pickup_orders" : "orders");
                payload.put("rating", selectedRating);
                payload.put("review", review);
                JSONObject response = post(REVIEW_URL, payload);
                boolean ok = response.optBoolean("success", false);
                String message = first(response.optString("message"), ok ? "Terima kasih atas penilaian Anda." : "Rating gagal disimpan.");
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    if (ok) {
                        try { order.put("rating", selectedRating); order.put("customer_rating", selectedRating); order.put("review", review); } catch (Exception ignored) {}
                        playSubmittedStars(() -> { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); render(); });
                    } else {
                        submitReviewButton.setEnabled(true); submitReviewButton.setText(ratingLabel(selectedRating));
                        new TransivaAlertDialogBuilder(this).setTitle("Gagal").setMessage(message).setPositiveButton("OK", null).show();
                    }
                });
            } catch (Exception e) {
                main.post(() -> { progress.setVisibility(View.GONE); submitReviewButton.setEnabled(true); submitReviewButton.setText(ratingLabel(selectedRating)); new TransivaAlertDialogBuilder(this).setTitle("Gagal").setMessage("Koneksi server bermasalah.").setPositiveButton("OK", null).show(); });
            }
        }, "detail-save-review").start();
    }

    private void playSubmittedStars(Runnable done) {
        for (int i = 0; i < starViews.length; i++) {
            if (i < selectedRating) animateStar(starViews[i], i * 100L);
        }
        main.postDelayed(done, 700 + selectedRating * 80L);
    }

    private void confirmAction(String action, String title, String message) {
        new TransivaAlertDialogBuilder(this).setTitle(title).setMessage(message).setNegativeButton("Batal", null).setPositiveButton("Ya", (d, w) -> sendAction(action)).show();
    }

    private void sendAction(String action) {
        progress.setVisibility(View.VISIBLE);
        networkScope.newThread(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("order_id", first(order.optString("order_id"), order.optString("id")));
                p.put("source", order.optString("source", "").contains("pickup") ? "pickup_orders" : "orders");
                p.put("action", action);
                JSONObject r = post(ACTION_URL, p);
                boolean ok = r.optBoolean("success", false);
                String msg = first(r.optString("message"), ok ? "Berhasil" : "Gagal");
                if (ok) {
                    if ("confirm_received".equals(action)) order.put("customer_received", 1);
                    else if ("approve_price".equals(action)) { order.put("price", order.optDouble("price_change_requested", order.optDouble("price", 0))); order.put("price_change_status", "approved"); }
                    else if ("reject_price".equals(action)) order.put("price_change_status", "rejected");
                }
                main.post(() -> { progress.setVisibility(View.GONE); new TransivaAlertDialogBuilder(this).setTitle(ok ? "Berhasil" : "Gagal").setMessage(msg).setPositiveButton("OK", null).show(); if (ok) render(); });
            } catch (Exception e) {
                main.post(() -> { progress.setVisibility(View.GONE); new TransivaAlertDialogBuilder(this).setTitle("Gagal").setMessage("Koneksi server bermasalah.").setPositiveButton("OK", null).show(); });
            }
        }, "detail-action").start();
    }

    private View photoWithLabel(String label, String raw) {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setBackground(round("#EDF5FD", 18));
        ImageView image = photoBox(raw); wrap.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView caption = text(label, 12, Color.parseColor("#45627E"), true); caption.setGravity(Gravity.CENTER); caption.setPadding(dp(5), dp(7), dp(5), dp(8)); wrap.addView(caption);
        return wrap;
    }

    private ImageView photoBox(String raw) {
        ImageView v = new ImageView(this); v.setScaleType(ImageView.ScaleType.CENTER_CROP); v.setImageResource(android.R.drawable.ic_menu_gallery); v.setBackground(round("#EAF2FB", 18));
        String u = absoluteUrl(raw); if (!u.isEmpty()) loadImage(v, u); return v;
    }

    private void loadImage(ImageView v, String u) {
        RemoteImageLoader.loadCenterCrop(v, u, android.R.drawable.ic_menu_gallery);
    }

    private String absoluteUrl(String p) {
        if (p == null || p.trim().isEmpty()) return "";
        p = p.trim(); if (p.startsWith("http://") || p.startsWith("https://")) return p;
        while (p.startsWith("/")) p = p.substring(1);
        if (p.startsWith("uploads/")) return BASE_URL + "server/" + p;
        if (p.startsWith("server/")) return BASE_URL + p;
        return BASE_URL + p;
    }

    private JSONObject post(String url, JSONObject payload) throws Exception {
        return TransivaHttpRepository.postJson(this, url, payload, 20000);
    }

    private LinearLayout card(int radius) { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); x.setPadding(dp(18), dp(18), dp(18), dp(18)); x.setBackground(round("#FFFFFF", radius)); x.setElevation(dp(2)); return x; }
    private LinearLayout infoPanel() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); x.setPadding(dp(14), dp(4), dp(14), dp(4)); x.setBackground(roundStroke("#F8FBFF", "#E1ECF7", 16)); return x; }
    private View divider() { View v = new View(this); v.setBackgroundColor(Color.parseColor("#E7EEF6")); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.setMargins(dp(34), 0, 0, 0); v.setLayoutParams(lp); return v; }
    private void addCard(View v) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(14)); body.addView(v, lp); }
    private View sectionHeader(String title, String subtitle) { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, 0, 0, dp(12)); box.addView(text(title, 19, Color.parseColor("#0B477D"), true)); box.addView(text(subtitle, 12, Color.parseColor("#7B8CA2"), false)); return box; }
    private View infoRow(String key, String value, String iconText) { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.TOP); row.setPadding(0, dp(10), 0, dp(10)); TextView icon = text(iconText, 18, Color.parseColor("#167DF5"), false); icon.setGravity(Gravity.CENTER); row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(36))); LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.addView(text(key, 12, Color.parseColor("#7B8CA2"), false)); TextView val = text(first(value, "-"), 15, Color.parseColor("#263648"), true); val.setLineSpacing(0, 1.05f); txt.addView(val); row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1)); return row; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private Button primary(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setBackground(gradient("#087AF5", "#25A6F8", 16)); return b; }
    private Button outline(String s) { Button b = new Button(this); b.setText(s); b.setTextSize(15); b.setTextColor(Color.parseColor("#1269BE")); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setBackground(roundStroke("#FFFFFF", "#8CC7F7", 16)); return b; }
    private LinearLayout.LayoutParams buttonLp() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54)); lp.setMargins(0, dp(10), 0, 0); return lp; }
    private GradientDrawable round(String c, int r) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(c)); g.setCornerRadius(dp(r)); return g; }
    private GradientDrawable roundStroke(String c, String s, int r) { GradientDrawable g = round(c, r); g.setStroke(dp(1), Color.parseColor(s)); return g; }
    private GradientDrawable gradient(String start, String end, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)}); g.setCornerRadius(dp(radius)); return g; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private String rupiah(double n) { return "Rp" + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(Math.round(n)); }
    private String first(String... values) { for (String s : values) if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) return s.trim(); return ""; }
    private String paymentLabel(String s) { s = first(s, "cash").toLowerCase(Locale.US); return s.contains("balance") || s.contains("transpay") || s.contains("wallet") || s.contains("saldo") ? "TransPay (Non-tunai)" : "Tunai"; }
    private String vehicleLabel(String s) { s = first(s, "-").toLowerCase(Locale.US); return s.equals("car") || s.equals("mobil") ? "Mobil / Car" : s.equals("bike") || s.equals("motor") ? "Motor / Bike" : s; }
    private String priceStatus(String s) { if ("pending".equals(s)) return "Menunggu konfirmasi Anda"; if ("approved".equals(s)) return "Disetujui"; if ("rejected".equals(s)) return "Ditolak"; return "Tidak ada pengajuan"; }
    private String prettyStatus(String s) { s = first(s, "-").replace('_', ' '); StringBuilder b = new StringBuilder(); for (String p : s.split(" ")) { if (p.isEmpty()) continue; if (b.length() > 0) b.append(' '); b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); } return b.toString(); }
    private boolean isFinishedStatus(String s) { return "completed".equals(s) || "finished".equals(s) || "done".equals(s) || "delivered".equals(s); }
    private boolean isPickupOrder() {
        String service = first(
                order.optString("service_name"),
                order.optString("service_type"),
                order.optString("order_type"),
                order.optString("service"),
                ""
        ).toLowerCase(Locale.US);
        String source = order.optString("source", "").toLowerCase(Locale.US);
        String table = order.optString("_transiva_table", "").toLowerCase(Locale.US);
        return service.contains("pickup")
                || service.contains("send")
                || source.contains("pickup_orders")
                || table.contains("pickup_orders");
    }

    private String serviceIcon(String s) { String v = s.toLowerCase(Locale.US); if (v.contains("food")) return "🍱"; if (v.contains("shop") || v.contains("mart")) return "🛍️"; if (v.contains("car")) return "🚘"; if (v.contains("ride") || v.contains("bike")) return "🏍️"; if (v.contains("send") || v.contains("pickup")) return "📦"; return "🧭"; }
    private int statusTextColor(String s) { if (isFinishedStatus(s)) return Color.parseColor("#047857"); if (s.contains("cancel")) return Color.parseColor("#B91C1C"); if (s.contains("arrived")) return Color.parseColor("#075985"); return Color.parseColor("#9A6700"); }
    private String statusBackground(String s) { if (isFinishedStatus(s)) return "#D1FAE5"; if (s.contains("cancel")) return "#FEE2E2"; if (s.contains("arrived")) return "#E0F2FE"; return "#FFF4D6"; }
    private String ratingLabel(int rating) { if (rating <= 1) return "Kirim • Perlu Diperbaiki"; if (rating == 2) return "Kirim • Kurang"; if (rating == 3) return "Kirim • Cukup"; if (rating == 4) return "Kirim • Bagus"; return "Kirim • Sangat Memuaskan"; }

    @Override
    protected void onDestroy() {
        networkScope.destroy();
        super.onDestroy();
    }
}
