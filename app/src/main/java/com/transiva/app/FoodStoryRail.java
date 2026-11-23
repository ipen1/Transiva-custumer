package com.transiva.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.HashSet;

/** Favorite/header remain attached; only changed/expired story tiles are reconciled. */
final class FoodStoryRail extends LinearLayout {
    private final Activity activity;private final KabarMitraUi ui;
    private final LinearLayout favorite, row;
    private final ImageView heart;
    private final TextView favoriteLabel, hint;
    private final View divider;
    private final HorizontalScrollView scroll;
    private String signature = "";

    FoodStoryRail(Activity activity, Runnable onFavorite) {
        super(activity);
        this.activity = activity;
        ui = new KabarMitraUi(activity);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(ui.dp(44));
        setPadding(ui.dp(10), 0, ui.dp(10), 0);

        GradientDrawable band = new GradientDrawable();
        band.setColor(ui.dark ? 0xff223b58 : 0xffe0effe);
        band.setCornerRadius(ui.dp(15));
        setBackground(band);
        CustomerAppSettings.preserveArtwork(this);

        favorite = new LinearLayout(activity);
        favorite.setGravity(Gravity.CENTER_VERTICAL);
        favorite.setMinimumHeight(ui.dp(44));
        favorite.setPadding(0, 0, ui.dp(8), 0);
        heart = new ImageView(activity);
        favorite.addView(heart, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        favoriteLabel = ui.text("Favorit", 12, true);
        favoriteLabel.setPadding(ui.dp(7), 0, 0, 0);
        favorite.addView(favoriteLabel);
        favorite.setFocusable(true);
        favorite.setOnClickListener(v -> onFavorite.run());
        addView(favorite, new LinearLayout.LayoutParams(-2, -2));

        divider = new View(activity);
        divider.setBackgroundColor(ui.dark ? 0xff536b87 : 0xffa5bed8);
        addView(divider, new LinearLayout.LayoutParams(ui.dp(1), ui.dp(16)));
        hint = ui.text("Menyiapkan cerita mitra…", 11, false);
        hint.setTextColor(ui.muted);
        hint.setMaxLines(2);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hint.setPadding(ui.dp(8), 0, 0, 0);
        addView(hint, new LinearLayout.LayoutParams(0, -2, 1));

        scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(activity);
        scroll.addView(row);
        addView(scroll, new LinearLayout.LayoutParams(0, -2, 1));
        scroll.setVisibility(GONE);
    }
    void update(JSONObject feed,long now,boolean selected,boolean hasMore,boolean loading,Runnable loadMore){
        JSONArray all=feed==null?null:feed.optJSONArray("stories"),live=new JSONArray();
        if(all!=null)for(int i=0;i<all.length();i++){JSONObject story=all.optJSONObject(i);if(story!=null&&story.optLong("expires_at")>now)live.put(story);}
        String next = selected + ":" + live + ":" + hasMore + ":" + loading + ":" + (feed == null);
        if (next.equals(signature)) return;
        signature = next;
        heart.setImageResource(selected ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
        heart.setColorFilter(ui.dark ? 0xff92c9ff : 0xff0756d8);
        favoriteLabel.setTextColor(selected ? (ui.dark ? 0xff92c9ff : 0xff0756d8) : ui.ink);
        favorite.setContentDescription(selected ? "Favorit aktif. Tekan untuk melihat semua toko" : "Lihat toko dan menu favorit");
        boolean empty = live.length() == 0 && !hasMore;
        divider.setVisibility(empty ? VISIBLE : GONE);
        hint.setVisibility(empty ? VISIBLE : GONE);
        scroll.setVisibility(empty ? GONE : VISIBLE);
        if (empty) {
            hint.setText(feed == null ? (loading ? "Memuat cerita mitra…" : "Cerita belum dimuat") : "Belum ada cerita baru");
            return;
        }
        int scrollX=scroll.getScrollX();LinearLayout plan=new LinearLayout(activity);HashSet<Integer> ids=new HashSet<>();IncrementalLinearRenderer.begin(row);
        try{
            for(int i=0;i<live.length();i++){
                JSONObject story=live.optJSONObject(i);int rid=story.optInt("restaurant_id");if(!ids.add(rid))continue;
                String photo=story.optString("banner");if(photo.isEmpty())photo=story.optString("image");String name=story.optString("name","Mitra"),key="story:"+rid,stamp=photo+":"+name;
                if(IncrementalLinearRenderer.unchanged(row,key,stamp)){View placeholder=new View(activity);IncrementalLinearRenderer.mark(placeholder,key,stamp);plan.addView(placeholder);continue;}
                LinearLayout tile=new LinearLayout(activity);tile.setOrientation(VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(ui.dp(3),ui.dp(3),ui.dp(3),0);tile.addView(ui.avatar(photo,54,true));
                TextView label=ui.text(name,11,true);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);tile.addView(label,new LinearLayout.LayoutParams(ui.dp(67),-2));
                tile.setOnClickListener(v->{if(activity instanceof TransFoodActivityScreenCore)((TransFoodActivityScreenCore)activity).openCompleteStory(rid,tile);});IncrementalLinearRenderer.mark(tile,key,stamp);plan.addView(tile);
            }
            if(hasMore){TextView more=ui.text(loading?"Memuat…":"Cerita lainnya ›",12,true);more.setGravity(Gravity.CENTER);more.setPadding(ui.dp(10),ui.dp(16),ui.dp(10),ui.dp(16));more.setEnabled(!loading);more.setOnClickListener(v->loadMore.run());plan.addView(more);}
            IncrementalLinearRenderer.apply(row,plan,0);
        }finally{IncrementalLinearRenderer.end(row);}
        scroll.post(()->scroll.scrollTo(scrollX,0));
    }
}
