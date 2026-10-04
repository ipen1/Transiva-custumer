package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Compact TransShop choices; existing delivery gate and shopping routes are retained. */
public class TransShopActivity extends Activity {
    private final int blue = Color.rgb(12, 108, 234);
    private final int ink = Color.rgb(20, 52, 89);
    private TextView deliveryLabel;
    private View deliveryCard;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void updateDelivery() {
        if (deliveryLabel == null) return;
        String address = DeliveryAddressGate.address(this);
        deliveryLabel.setText(address);
        deliveryCard.setContentDescription("Antar ke: " + address + ". Ketuk untuk mengubah alamat.");
    }

    @Override protected void onResume() {
        super.onResume();
        if (DeliveryAddressGate.valid(this) && deliveryLabel == null) {
            recreate();
            return;
        }
        updateDelivery();
    }

    private void choice(LinearLayout root, String title, String description, String action,
                        int imageRes, int accent, Class<?> target, boolean stacked) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(bg(Color.WHITE, 16));
        card.setClipToOutline(true);
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(15), dp(8), dp(10));
        add(content, text(title, 18, true, ink), 0);
        TextView detail = text(description, 13, false, Color.rgb(78, 99, 121));
        detail.setLineSpacing(dp(2), 1f);
        add(content, detail, 7);
        TextView cta = text(action + "  →", 13, true, accent);
        cta.setGravity(Gravity.CENTER_VERTICAL);
        cta.setMinHeight(dp(48));
        add(content, cta, 4);
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        FrameLayout art = new FrameLayout(this);
        art.setBackgroundColor(Color.rgb(232, 242, 252));
        ImageView photo = new ImageView(this);
        photo.setImageResource(imageRes);
        // Show the supplied illustration in full instead of cropping out its subject.
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(stacked
                ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT}));
        art.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        if (stacked) {
            // Narrow windows and enlarged fonts use a shallow image plus unrestricted text.
            card.addView(art, new LinearLayout.LayoutParams(-1, dp(96)));
            card.addView(content, new LinearLayout.LayoutParams(-1, -2));
        } else {
            card.setMinimumHeight(dp(164));
            card.addView(content, new LinearLayout.LayoutParams(0, -2, 0.62f));
            card.addView(art, new LinearLayout.LayoutParams(0, -1, 0.38f));
        }
        card.setForeground(new RippleDrawable(ColorStateList.valueOf(Color.argb(28, 12, 108, 234)),
                null, bg(Color.WHITE, 16)));
        card.setContentDescription(title + ". " + description + ". " + action);
        card.setOnClickListener(view -> open(target));
        add(root, card, 12);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(blue);
        getWindow().setNavigationBarColor(Color.rgb(5, 16, 29));
        if (!DeliveryAddressGate.valid(this)) {
            DeliveryAddressGate.showUnavailable(this, "TransShop");
            DeliveryAddressGate.require(this, "TransShop");
            return;
        }
        boolean stacked = getResources().getConfiguration().screenWidthDp < 340
                || getResources().getConfiguration().fontScale >= 1.35f;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 249, 255));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(20));
        // Keep the page centered and comfortably readable on tablets and wide windows.
        FrameLayout container = new FrameLayout(this) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) root.getLayoutParams();
                int width = Math.min(w, dp(640));
                if (params.width != width) {
                    params.width = width;
                    root.setLayoutParams(params);
                }
            }
        };
        container.addView(root, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(container);
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, true, ink);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Kembali");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(text("TransShop", 23, true, ink), new LinearLayout.LayoutParams(0, -2, 1));
        add(root, header, 0);

        LinearLayout address = new LinearLayout(this);
        address.setGravity(Gravity.CENTER_VERTICAL);
        address.setPadding(dp(12), dp(10), dp(12), dp(10));
        address.setMinimumHeight(dp(64));
        address.setBackground(bg(Color.WHITE, 12));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        add(labels, text("Antar ke", 11, true, blue), 0);
        deliveryLabel = text("", 13, false, ink);
        deliveryLabel.setMaxLines(2);
        deliveryLabel.setEllipsize(TextUtils.TruncateAt.END);
        add(labels, deliveryLabel, 4);
        address.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = text("›", 25, true, blue);
        edit.setGravity(Gravity.CENTER);
        address.addView(edit, new LinearLayout.LayoutParams(dp(28), -2));
        address.setClickable(true);
        address.setFocusable(true);
        address.setForeground(new RippleDrawable(ColorStateList.valueOf(Color.argb(28, 12, 108, 234)),
                null, bg(Color.WHITE, 12)));
        address.setOnClickListener(view -> DeliveryAddressGate.edit(this, "TransShop"));
        deliveryCard = address;
        updateDelivery();
        add(root, address, 8);

        add(root, text("Belanja dengan caramu", 16, true, blue), 16);
        add(root, text("Pilih toko atau titip belanja dari lokasi pilihanmu.", 13, false,
                Color.rgb(83, 103, 124)), 5);
        choice(root, "Toko Terdaftar", "Pilih produk dari toko sekitar, lalu checkout dengan mudah.",
                "Jelajahi toko", R.drawable.transshop_registered_background,
                blue, TransShopCatalogActivity.class, stacked);
        choice(root, "Belanja Bebas", "Tentukan lokasi di peta, driver bantu belanja dan antar.",
                "Buka peta belanja", R.drawable.transshop_free_background,
                Color.rgb(15, 125, 139), TransShopLegacyActivity.class, stacked);
        TextView note = text("Dua cara belanja, satu kemudahan.", 11, false, Color.rgb(112, 130, 149));
        note.setGravity(Gravity.CENTER);
        add(root, note, 16);
    }
}
