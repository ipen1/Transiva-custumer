package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class RecommendationSectionController {

    private static final String API =
            "https://transiva.my.id/server/customer_get_recommendations.php";

    private final Activity activity;
    private final LinearLayout root;
    private final LinearLayout row;
    private final TextView empty;
    private final ProgressBar progress;
    private boolean loading;

    public RecommendationSectionController(Activity activity) {
        this.activity = activity;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("Rekomendasi untukmu", 16, "#0B3A78", true);
        root.addView(title);

        progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp =
                new LinearLayout.LayoutParams(dp(32), dp(32));
        progressLp.gravity = Gravity.CENTER;
        progressLp.setMargins(0, dp(8), 0, dp(8));
        root.addView(progress, progressLp);

        empty = text(
                "Rekomendasi sedang disiapkan untukmu",
                11,
                "#7B8DA3",
                false
        );
        empty.setGravity(Gravity.CENTER_VERTICAL);
        empty.setPadding(dp(13), dp(11), dp(13), dp(11));
        empty.setBackground(roundStroke("#FFFFFF", "#E3ECF7", 14));
        LinearLayout.LayoutParams emptyLp =
                new LinearLayout.LayoutParams(-1, dp(46));
        emptyLp.setMargins(0, dp(7), 0, 0);
        root.addView(empty, emptyLp);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));

        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(-1, dp(132));
        scrollLp.setMargins(0, dp(7), 0, dp(4));
        root.addView(scroll, scrollLp);

        refresh();
    }

    public View buildView() {
        return root;
    }

    public void refresh() {
        if (loading) return;
        loading = true;
        progress.setVisibility(View.VISIBLE);

        new Thread(() -> {
            JSONArray items = new JSONArray();
            try {
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(
                                API + "?role=customer&_=" + System.currentTimeMillis()
                        ).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                InputStream stream = status >= 400
                        ? connection.getErrorStream()
                        : connection.getInputStream();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, "UTF-8")
                );
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
                reader.close();
                connection.disconnect();

                JSONObject response = new JSONObject(raw.toString());
                JSONArray received = response.optJSONArray("recommendations");
                if (response.optBoolean("success", false) && received != null) {
                    items = received;
                }
            } catch (Exception ignored) {
            }

            JSONArray finalItems = items;
            activity.runOnUiThread(() -> {
                loading = false;
                progress.setVisibility(View.GONE);
                render(finalItems);
            });
        }).start();
    }

    private void render(JSONArray items) {
        row.removeAllViews();

        if (items == null || items.length() == 0) {
            empty.setVisibility(View.VISIBLE);
            ((View) row.getParent()).setVisibility(View.GONE);
            return;
        }

        empty.setVisibility(View.GONE);
        ((View) row.getParent()).setVisibility(View.VISIBLE);

        int count = Math.min(items.length(), 6);
        for (int i = 0; i < count; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) row.addView(createCard(item));
        }
    }

    private View createCard(JSONObject item) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundStroke("#FFFFFF", "#DCE8F6", 16));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        RemoteImageLoader.loadCenterCrop(
                image,
                item.optString("image_url", ""),
                "tour".equalsIgnoreCase(item.optString("item_type"))
                        ? drawable("ic_service_tour")
                        : drawable("ic_service_food")
        );
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.addView(body);

        TextView name = text(
                item.optString("title", "Rekomendasi"),
                12,
                "#0B3A78",
                true
        );
        name.setMaxLines(2);
        body.addView(name);

        String type = item.optString("item_type", "food");
        double rating = item.optDouble("rating", 0);
        String meta = ("tour".equalsIgnoreCase(type) ? "Wisata" : "Makanan")
                + (rating > 0 ? " • ⭐ " + String.format(java.util.Locale.US, "%.1f", rating) : "");
        TextView info = text(meta, 9, "#64748B", false);
        info.setPadding(0, dp(4), 0, 0);
        body.addView(info);

        card.setOnClickListener(v -> open(item));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(148), dp(124));
        lp.setMargins(0, 0, dp(9), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void open(JSONObject item) {
        Intent intent = new Intent(activity, RecommendationDetailActivity.class);
        intent.putExtra("recommendation_json", item.toString());
        activity.startActivity(intent);
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundStroke(String fill, String stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }

    private int drawable(String name) {
        return activity.getResources().getIdentifier(
                name,
                "drawable",
                activity.getPackageName()
        );
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
