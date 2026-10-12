package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Unified native order cards. Cached content stays readable while refreshing. */
public class LiveOrderCenterActivity extends Activity implements UnifiedLiveOrderCenter.Listener {
    private LinearLayout list;
    private ProgressBar loading;
    private TextView info, count, refreshButton;
    private boolean refreshing, loaded;
    private String rendered = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        setContentView(screen()); CustomerAppSettings.apply(this);
        UnifiedLiveOrderCenter.addListener(this);
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onDestroy() {
        UnifiedLiveOrderCenter.removeListener(this); super.onDestroy();
    }
    private boolean closed() { return isFinishing() || isDestroyed(); }

    private View screen() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setClipToPadding(false); scroll.setBackgroundColor(Color.parseColor("#F3F7FD"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, "#123B65", true); back.setGravity(Gravity.CENTER);
        back.setContentDescription("Kembali"); back.setOnClickListener(v -> finish());
        nav.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Pesanan aktif", 19, "#123B65", true);
        nav.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(nav);
        count = text("Memeriksa pesanan aktif…", 12, "#64748B", false);
        root.addView(count, margin(2));
        LinearLayout sync = new LinearLayout(this); sync.setGravity(Gravity.CENTER_VERTICAL);
        loading = new ProgressBar(this); loading.setIndeterminate(true);
        sync.addView(loading, new LinearLayout.LayoutParams(dp(18), dp(18)));
        info = text("Memeriksa pesanan…", 11, "#64748B", false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1); ip.setMargins(dp(8), 0, dp(8), 0);
        sync.addView(info, ip);
        refreshButton = text("Perbarui", 12, "#1268D5", true);
        refreshButton.setGravity(Gravity.CENTER); refreshButton.setPadding(dp(8), 0, dp(8), 0);
        refreshButton.setMinHeight(dp(48)); refreshButton.setOnClickListener(v -> refresh()); sync.addView(refreshButton);
        root.addView(sync, margin(4));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        return scroll;
    }
    private void refresh() {
        if (refreshing || closed()) return;
        refreshing = true; loading.setVisibility(View.VISIBLE); refreshButton.setEnabled(false);
        info.setText("Memperbarui status…");
        UnifiedLiveOrderCenter.refresh(this, (orders, cached, error) -> {
            if (closed()) return;
            refreshing = false; loaded = true; loading.setVisibility(View.GONE); refreshButton.setEnabled(true);
            info.setText(error != null ? "Koneksi belum stabil • data terakhir" : "Status terbaru dari server");
            render(orders);
        });
    }
    @Override public void onOrdersChanged(List<UnifiedLiveOrderCenter.Order> orders) {
        if (!closed()) runOnUiThread(() -> { if (!closed()) render(orders); });
    }
    private void render(List<UnifiedLiveOrderCenter.Order> orders) {
        if (closed()) return;
        int size = orders == null ? 0 : orders.size();
        count.setText(size == 0 ? "Semua pesanan Anda dalam satu tempat" : size + " pesanan aktif • pilih untuk membuka");
        StringBuilder key = new StringBuilder(); key.append(loaded).append('|');
        if (orders != null) for (UnifiedLiveOrderCenter.Order order : orders) key.append(order.raw.toString()).append('\n');
        if (key.toString().equals(rendered)) return; rendered = key.toString();
        list.removeAllViews();
        if (size == 0) {
            PremiumOrderStatusView empty = new PremiumOrderStatusView(this, true);
            empty.bind(null, loaded ? "Belum ada pesanan aktif" : "Memeriksa pesanan aktif…", "Kembali ke beranda  ›");
            empty.setAction(v -> finish()); list.addView(empty, margin(8));
        } else for (UnifiedLiveOrderCenter.Order order : orders) {
            PremiumOrderStatusView card = new PremiumOrderStatusView(this, false);
            card.bind(order.raw, "", order.hasDriver() ? "Buka perjalanan  ›" : "Pantau pencarian driver  ›");
            card.setAction(v -> {
                UnifiedLiveOrderCenter.persistLegacyActiveOrder(this, order);
                startActivity(UnifiedLiveOrderCenter.routeIntent(this, order));
            }); list.addView(card, margin(8));
        }
        CustomerAppSettings.applyToView(this, list);
    }
    private TextView text(String value, int size, String color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size);
        v.setTextColor(Color.parseColor(color)); v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(2), 1f); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private LinearLayout.LayoutParams margin(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); return p;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
}
