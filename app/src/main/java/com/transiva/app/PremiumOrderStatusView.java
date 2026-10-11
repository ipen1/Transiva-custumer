package com.transiva.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Lightweight native presentation. No timers, requests, or changes to order state. */
public final class PremiumOrderStatusView extends LinearLayout {
    private final TextView service, reference, status, description, stage, action;
    private final LinearLayout segments, route;
    private final android.widget.FrameLayout emblemFrame;
    private final android.widget.ImageView serviceIcon;
    private final TextView emblem;
    private final boolean compact;
    private String fingerprint = "";
    private String progressStatus = null;

    public PremiumOrderStatusView(Context context, boolean compact) {
        super(context); this.compact = compact;
        setOrientation(VERTICAL); setPadding(dp(16), dp(16), dp(16), dp(14));
        setBackground(surface("#FFFFFF", "#DCE8F6", 22)); setElevation(dp(2));
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        emblemFrame = new android.widget.FrameLayout(context);
        emblem = label("T", 18, "#FFFFFF", true); emblem.setGravity(Gravity.CENTER);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#075DDF"), Color.parseColor("#27AEFA")});
        gradient.setCornerRadius(dp(14)); emblemFrame.setBackground(gradient);
        emblemFrame.addView(emblem, new android.widget.FrameLayout.LayoutParams(-1, -1));
        serviceIcon = new android.widget.ImageView(context);
        serviceIcon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        serviceIcon.setPadding(dp(3), dp(3), dp(3), dp(3)); serviceIcon.setVisibility(GONE);
        serviceIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        emblemFrame.addView(serviceIcon, new android.widget.FrameLayout.LayoutParams(-1, -1));
        emblem.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(emblemFrame, new LayoutParams(dp(46), dp(46)));
        LinearLayout headings = new LinearLayout(context); headings.setOrientation(VERTICAL);
        service = label("Status pesanan", 16, "#123B65", true);
        reference = label("Semua perjalanan dalam satu tempat", 11, "#64748B", false);
        headings.addView(service); headings.addView(reference);
        LayoutParams hlp = new LayoutParams(0, -2, 1); hlp.setMargins(dp(10), 0, 0, 0);
        header.addView(headings, hlp); addView(header);
        status = label("Belum ada pesanan aktif", 16, "#1268D5", true);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        addView(status, spaced(12));
        description = label("", 12, "#536B83", false); addView(description, spaced(8));
        segments = new LinearLayout(context); segments.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(segments, spaced(12));
        stage = label("", 11, "#64748B", false); addView(stage, spaced(6));
        route = new LinearLayout(context); route.setOrientation(VERTICAL);
        route.setPadding(dp(12), dp(10), dp(12), dp(10)); route.setBackground(surface("#F5F9FF", "#EAF0F8", 14));
        addView(route, spaced(12));
        action = label("Lihat pesanan  ›", 13, "#1268D5", true);
        action.setGravity(Gravity.CENTER_VERTICAL); action.setMinHeight(dp(48));
        action.setPadding(dp(12), dp(8), dp(12), dp(8));
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#D1E5FF")),
                surface("#EDF5FF", "#DCEBFF", 14), null));
        addView(action, spaced(12)); bind(null, "", "Mulai pesan  ›");
    }

    public void bind(JSONObject order, String fallback, String actionText) {
        String next = (order == null ? "" : order.toString()) + "|" + fallback + "|" + actionText;
        if (next.equals(fingerprint)) return; fingerprint = next;
        boolean empty = order == null;
        String raw = empty ? "" : order.optString("status");
        String type = empty ? "" : first(order.optString("order_type"), order.optString("service_type"), order.optString("service_name"));
        String name = empty ? "Status pesanan" : new UnifiedLiveOrderCenter.Order(order).title();
        service.setText(name);
        String iconType = (type + " " + name).toLowerCase(java.util.Locale.ROOT);
        String resource = iconType.contains("food") ? "ic_service_food_premium" : iconType.contains("shop") ? "ic_service_shop_premium"
                : iconType.contains("car") ? "ic_service_car_premium" : iconType.contains("send") || iconType.contains("pickup") ? "ic_service_send_premium"
                : iconType.contains("ride") || iconType.contains("motor") ? "ic_service_ride_premium" : "";
        int iconId = resource.isEmpty() ? 0 : getResources().getIdentifier(resource, "drawable", getContext().getPackageName());
        serviceIcon.setVisibility(iconId == 0 ? GONE : VISIBLE); emblem.setVisibility(iconId == 0 ? VISIBLE : GONE);
        if (iconId != 0) serviceIcon.setImageResource(iconId);
        reference.setText(empty ? "Pantau pesanan Anda di sini" : "Order #" + first(order.optString("order_id"), order.optString("id_order"), order.optString("id"), "—"));
        status.setText(empty ? first(fallback, "Belum ada pesanan aktif") : OrderStatusPresentation.label(raw, type));
        status.setTextColor(Color.parseColor(empty ? "#123B65" : OrderStatusPresentation.textColor(raw)));
        status.setBackground(surface(empty ? "#F1F6FC" : OrderStatusPresentation.backgroundColor(raw), "", 14));
        description.setText(empty ? "Pesan layanan favorit Anda, lalu pantau perkembangannya di sini."
                : OrderStatusPresentation.description(order));
        description.setVisibility(compact && empty ? GONE : VISIBLE);
        bindProgress(raw);
        segments.setVisibility(empty ? GONE : segments.getVisibility()); stage.setVisibility(empty ? GONE : stage.getVisibility());
        route.removeAllViews(); route.setVisibility(empty || compact ? GONE : VISIBLE);
        if (!empty && !compact) {
            addRoute(type.toLowerCase(java.util.Locale.ROOT).contains("food") ? "MERCHANT / JEMPUT" : "TITIK JEMPUT", first(order.optString("pickup_address"), order.optString("pickup_location"), "Lokasi jemput belum tersedia"), "#0B7CFF");
            addRoute("TUJUAN", first(order.optString("delivery_address"), order.optString("destination_address"), "Tujuan belum tersedia"), "#18A578");
        }
        action.setText(actionText);
        status.setContentDescription("Status pesanan: " + status.getText());
        CustomerAppSettings.applyToView(getContext(), this);
    }

    /** Updates only visual progress; caller retains its existing polling and lifecycle. */
    public void bindProgress(String raw) {
        String normalized = CustomerOrderState.normalize(raw);
        if (normalized.equals(progressStatus)) return;
        progressStatus = normalized;
        segments.removeAllViews(); int current = step(raw);
        segments.setVisibility(current < 0 ? GONE : VISIBLE);
        stage.setVisibility(current < 0 ? GONE : VISIBLE);
        if (current < 0) return;
        String[] captions = {"Menunggu konfirmasi / driver", "Driver menuju titik jemput", "Driver tiba di titik jemput", "Menuju lokasi tujuan", "Sudah tiba di tujuan", "Pesanan selesai"};
        for (int i = 0; i < 6; i++) {
            View segment = new View(getContext());
            segment.setBackground(surface(i <= current ? OrderStatusPresentation.dotColor(raw) : "#E6EEF8", "", 4));
            LayoutParams lp = new LayoutParams(0, dp(4), 1); if (i > 0) lp.setMargins(dp(4), 0, 0, 0);
            segments.addView(segment, lp);
        }
        stage.setText(captions[current]);
    }

    public void setAction(View.OnClickListener listener) { action.setOnClickListener(listener); setOnClickListener(listener); }
    public static int step(String raw) {
        switch (CustomerOrderState.normalize(raw)) {
            case "pending": case "merchant_accepted": return 0;
            case "driver_accepted": return 1;
            case "arrived_pickup": return 2;
            case "on_delivery": return 3;
            case "arrived_delivery": return 4;
            case "finished": return 5;
            default: return -1; // Unknown, scheduled or canceled: do not invent progress.
        }
    }
    private void addRoute(String title, String address, String color) {
        LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.TOP);
        TextView dot = label("●", 12, color, true); row.addView(dot, new LayoutParams(dp(20), -2));
        LinearLayout copy = new LinearLayout(getContext()); copy.setOrientation(VERTICAL);
        copy.addView(label(title, 10, "#6C8198", true)); copy.addView(label(address, 12, "#294661", false));
        row.addView(copy, new LayoutParams(0, -2, 1)); route.addView(row, spaced(route.getChildCount() == 0 ? 0 : 10));
    }
    private TextView label(String value, int size, String color, boolean bold) {
        TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(size);
        v.setTextColor(Color.parseColor(color)); v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(2), 1f); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private LayoutParams spaced(int top) { LayoutParams p = new LayoutParams(-1, -2); p.topMargin = dp(top); return p; }
    private GradientDrawable surface(String color, String border, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(Color.parseColor(color)); d.setCornerRadius(dp(radius));
        if (!border.isEmpty()) d.setStroke(dp(1), Color.parseColor(border)); return d;
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private static String first(String... values) { for (String v : values) if (v != null && !v.trim().isEmpty() && !"null".equalsIgnoreCase(v.trim())) return v.trim(); return ""; }
}
