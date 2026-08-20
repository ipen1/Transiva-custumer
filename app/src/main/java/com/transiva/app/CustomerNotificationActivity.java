package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CustomerNotificationActivity extends Activity {

    private LinearLayout list;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        setContentView(buildScreen());
        CustomerAppSettings.apply(this);
        renderNotifications();
        TransivaNotificationStore.markAllRead(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);
        renderNotifications();
        TransivaNotificationStore.markAllRead(this);
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F7FAFF"));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(12));
        header.setBackground(Shape.gradient("#075EF4", "#22A4FF", 0));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        TextView back = text("‹", 34, "#FFFFFF", false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Kembali");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, -2, 1);
        titlesLp.setMargins(dp(6), 0, 0, 0);
        header.addView(titles, titlesLp);
        titles.addView(text("Pemberitahuan", 19, "#FFFFFF", true));
        titles.addView(text("Informasi terbaru dari Transiva", 10, "#EAF4FF", false));

        ImageView bell = new ImageView(this);
        bell.setImageResource(getResources().getIdentifier("ic_notification_bell", "drawable", getPackageName()));
        bell.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(bell, new LinearLayout.LayoutParams(dp(38), dp(38)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        // Informasi brand ini adalah bagian tetap dari halaman Pemberitahuan.
        // Tidak disimpan di TransivaNotificationStore, sehingga "Hapus semua"
        // tidak pernah dapat menghapusnya.
        body.addView(buildKOnlinePinnedCard());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        row.addView(text("Semua pemberitahuan", 15, "#0B3A78", true), new LinearLayout.LayoutParams(0, -2, 1));

        TextView clear = text("Hapus semua", 10, "#0B7CFF", true);
        clear.setPadding(dp(8), dp(7), dp(8), dp(7));
        clear.setOnClickListener(v -> {
            TransivaNotificationStore.clear(this);
            renderNotifications();
            Toast.makeText(this, "Pemberitahuan dibersihkan", Toast.LENGTH_SHORT).show();
        });
        row.addView(clear);

        emptyText = text("Belum ada pemberitahuan dari aplikasi.", 12, "#7B8DA3", false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(16), dp(42), dp(16), dp(42));
        body.addView(emptyText, new LinearLayout.LayoutParams(-1, -2));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, -2);
        listLp.setMargins(0, dp(10), 0, 0);
        body.addView(list, listLp);

        return root;
    }

    private View buildKOnlinePinnedCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(Shape.gradient("#F3F8FF", "#FFFFFF", dp(19)));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardLp);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(brandRow, new LinearLayout.LayoutParams(-1, -2));

        TextView pin = text("PIN", 8, "#FFFFFF", true);
        pin.setGravity(Gravity.CENTER);
        pin.setBackground(Shape.round("#0B7CFF", dp(12)));
        brandRow.addView(pin, new LinearLayout.LayoutParams(dp(38), dp(24)));

        TextView permanent = text("  Informasi resmi Transiva", 10, "#58718E", true);
        brandRow.addView(permanent, new LinearLayout.LayoutParams(0, -2, 1));

        TextView lock = text("Tetap", 9, "#12834B", true);
        lock.setPadding(dp(8), dp(4), dp(8), dp(4));
        lock.setBackground(Shape.roundStroke("#ECFAF2", "#BFE8CF", dp(12), 1));
        brandRow.addView(lock);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams identityLp =
                new LinearLayout.LayoutParams(-1, -2);
        identityLp.setMargins(0, dp(12), 0, 0);
        card.addView(identity, identityLp);

        TextView kBadge = text("K", 20, "#FFFFFF", true);
        kBadge.setGravity(Gravity.CENTER);
        kBadge.setBackground(Shape.gradient("#111827", "#374151", dp(15)));
        identity.addView(kBadge, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView arrow = text("→", 17, "#7A8DA6", true);
        arrow.setGravity(Gravity.CENTER);
        identity.addView(arrow, new LinearLayout.LayoutParams(dp(38), dp(46)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier(
                "transiva_logo", "drawable", getPackageName()));
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        identity.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandCopyLp =
                new LinearLayout.LayoutParams(0, -2, 1);
        brandCopyLp.setMargins(dp(8), 0, 0, 0);
        identity.addView(brandCopy, brandCopyLp);
        brandCopy.addView(text("TRANSIVA", 15, "#0B3A78", true));
        brandCopy.addView(text("Satu ekosistem, lebih lengkap", 9, "#6C8199", false));

        TextView title = text(
                "K Online kini menjadi bagian dari Transiva",
                14, "#0B3A78", true);
        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(11), 0, 0);
        card.addView(title, titleLp);

        TextView body = text(
                "Layanan yang Anda kenal kini terhubung dalam satu aplikasi Transiva — lebih praktis, aman, dan lengkap.",
                10, "#5D728A", false);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyLp =
                new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(5), 0, 0);
        card.addView(body, bodyLp);

        TextView more = text("Selengkapnya  ›", 10, "#0B7CFF", true);
        more.setGravity(Gravity.RIGHT);
        more.setPadding(0, dp(9), 0, 0);
        card.addView(more, new LinearLayout.LayoutParams(-1, -2));

        View.OnClickListener open = v -> showKOnlineInfo();
        card.setOnClickListener(open);
        more.setOnClickListener(open);

        return card;
    }

    private void showKOnlineInfo() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("K Online × Transiva")
                    .setMessage(
                            "K Online kini menjadi bagian dari Transiva.\n\n"
                                    + "Anda tetap dapat menikmati layanan yang sudah dikenal, kini dalam ekosistem Transiva yang lebih lengkap.\n\n"
                                    + "• Satu aplikasi untuk berbagai kebutuhan\n"
                                    + "• Dukungan layanan dan keamanan Transiva\n"
                                    + "• Pengalaman pemesanan yang lebih terintegrasi\n\n"
                                    + "#KOnlineBersamaTransiva"
                    )
                    .setPositiveButton("Mengerti", null)
                    .show();
        } catch (Throwable ignored) { }
    }

    private void renderNotifications() {
        if (list == null) return;
        list.removeAllViews();
        JSONArray items = TransivaNotificationStore.getItems(this);
        emptyText.setVisibility(items.length() == 0 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            list.addView(notificationCard(item));
        }
    }

    private View notificationCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#E0EAF6", dp(18), 1));
        card.setElevation(dp(1));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardLp);

        TextView icon = text(iconFor(item.optString("type")), 18, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Shape.round("#EAF4FF", dp(18)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(10), 0, 0, 0);
        card.addView(copy, copyLp);

        copy.addView(text(item.optString("title", "Transiva"), 13, "#153B66", true));
        TextView body = text(item.optString("body", "Notifikasi baru"), 11, "#64748B", false);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.setMargins(0, dp(4), 0, dp(5));
        copy.addView(body, bodyLp);
        copy.addView(text(formatTime(item.optLong("time", 0L)), 9, "#9AA9BA", false));

        card.setOnClickListener(v -> openNotification(item));
        return card;
    }

    private void openNotification(JSONObject item) {
        String type = item.optString("type", "general").toLowerCase(Locale.ROOT);
        String orderId = item.optString("order_id", "");
        Intent intent;
        if (type.contains("chat") || type.contains("message")) {
            intent = new Intent(this, CustomerChatActivity.class);
            intent.putExtra("room_id", item.optString("room_id", ""));
        } else if (type.contains("wallet") || type.contains("saldo") || type.contains("deposit")) {
            intent = new Intent(this, CustomerTopUpActivity.class);
        } else if (type.contains("order") || type.contains("trip") || type.contains("ride")) {
            intent = new Intent(this, CustomerHistoryActivity.class);
            intent.putExtra("order_id", orderId);
        } else {
            intent = new Intent(this, CustomerDashboardActivity.class);
        }
        startActivity(intent);
    }

    private String iconFor(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (value.contains("chat")) return "💬";
        if (value.contains("wallet") || value.contains("saldo")) return "Rp";
        if (value.contains("promo")) return "%";
        if (value.contains("order") || value.contains("trip")) return "✓";
        return "!";
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "Baru saja";
        return new SimpleDateFormat("dd MMM yyyy • HH:mm", new Locale("id", "ID"))
                .format(new Date(timestamp));
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
