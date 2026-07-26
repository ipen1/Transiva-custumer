package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public class RecommendationDetailActivity extends Activity {

    private JSONObject item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));

        try {
            item = new JSONObject(
                    getIntent().getStringExtra("recommendation_json")
            );
        } catch (Exception ignored) {
            item = new JSONObject();
        }

        setContentView(buildScreen());
    }

    private ScrollView buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(Color.parseColor("#F7FAFF"));
        scroll.addView(root);

        TextView back = text("‹  Kembali", 16, "#0B7CFF", true);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.parseColor("#EAF4FF"));
        RemoteImageLoader.loadCenterCrop(
                image,
                item.optString("image_url", ""),
                "tour".equalsIgnoreCase(item.optString("item_type"))
                        ? drawable("ic_service_tour")
                        : drawable("ic_service_food")
        );
        LinearLayout.LayoutParams imageLp =
                new LinearLayout.LayoutParams(-1, dp(190));
        imageLp.setMargins(0, dp(14), 0, dp(14));
        root.addView(image, imageLp);

        root.addView(text(item.optString("title", "Rekomendasi"), 23, "#0B3A78", true));

        String owner = item.optString("owner_name", "");
        if (!owner.isEmpty()) {
            TextView ownerView = text(owner, 13, "#64748B", false);
            ownerView.setPadding(0, dp(5), 0, 0);
            root.addView(ownerView);
        }

        double rating = item.optDouble("rating", 0);
        int reviews = item.optInt("review_count", 0);
        if (rating > 0) {
            TextView ratingView = text(
                    "⭐ " + String.format(Locale.US, "%.1f", rating)
                            + (reviews > 0 ? " • " + reviews + " ulasan" : ""),
                    13,
                    "#64748B",
                    false
            );
            ratingView.setPadding(0, dp(7), 0, 0);
            root.addView(ratingView);
        }

        String subtitle = item.optString("subtitle", "");
        if (!subtitle.isEmpty()) {
            TextView description = text(subtitle, 14, "#52647A", false);
            description.setPadding(0, dp(12), 0, dp(12));
            root.addView(description);
        }

        double price = item.optDouble("price", 0);
        if (price > 0) {
            root.addView(text("Mulai " + rupiah(price), 17, "#0B7CFF", true));
        }

        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText(
                "tour".equalsIgnoreCase(item.optString("item_type"))
                        ? "Buka Transtour"
                        : "Buka Restoran / Menu"
        );
        open.setTextColor(Color.WHITE);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setBackground(round("#0B7CFF", 17));
        open.setOnClickListener(v -> openService());

        LinearLayout.LayoutParams buttonLp =
                new LinearLayout.LayoutParams(-1, dp(52));
        buttonLp.setMargins(0, dp(20), 0, 0);
        root.addView(open, buttonLp);

        return scroll;
    }

    private void openService() {
        String type = item.optString("item_type", "food");
        int targetId = item.optInt("target_id", 0);
        int parentId = item.optInt("parent_id", 0);

        Intent intent;
        if ("tour".equalsIgnoreCase(type)) {
            intent = new Intent(this, TranstourActivity.class);
            intent.putExtra("recommended_wisata_id", targetId);
        } else {
            intent = new Intent(this, TransFoodActivity.class);
            intent.putExtra(
                    "recommended_restaurant_id",
                    parentId > 0 ? parentId : targetId
            );
            intent.putExtra("recommended_menu_id", targetId);
            intent.putExtra("recommended_title", item.optString("title", ""));
        }

        startActivity(intent);
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int drawable(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private String rupiah(double value) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID"))
                .format((long) value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
