package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.HashSet;

/** Favorite/header remain attached; only changed/expired story tiles are reconciled. */
final class FoodStoryRail extends LinearLayout {
    private final Activity activity;private final KabarMitraUi ui;
    private final LinearLayout favorite,row;private final FrameLayout circle;private final ImageView heart;private final TextView favoriteLabel,hint;
    private final HorizontalScrollView scroll;private String signature="";
    FoodStoryRail(Activity activity,Runnable onFavorite){
        super(activity);this.activity=activity;ui=new KabarMitraUi(activity);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(0,ui.dp(5),0,ui.dp(7));
        favorite=new LinearLayout(activity);favorite.setOrientation(VERTICAL);favorite.setGravity(Gravity.CENTER);favorite.setPadding(0,ui.dp(3),ui.dp(8),0);
        circle=new FrameLayout(activity);heart=new ImageView(activity);heart.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));circle.addView(heart,new FrameLayout.LayoutParams(-1,-1));favorite.addView(circle,new LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));
        favoriteLabel=ui.text("Favorit",11,true);favoriteLabel.setGravity(Gravity.CENTER);favorite.addView(favoriteLabel);favorite.setOnClickListener(v->onFavorite.run());addView(favorite,new LinearLayout.LayoutParams(ui.dp(70),-2));
        hint=ui.text("Menyiapkan cerita mitra…",12,false);hint.setTextColor(ui.muted);hint.setMaxLines(3);hint.setPadding(ui.dp(6),0,ui.dp(3),0);addView(hint,new LinearLayout.LayoutParams(0,-2,1));
        scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);row=new LinearLayout(activity);scroll.addView(row);addView(scroll,new LinearLayout.LayoutParams(0,-2,1));scroll.setVisibility(GONE);
    }
    void update(JSONObject feed,long now,boolean selected,boolean hasMore,boolean loading,Runnable loadMore){
        JSONArray all=feed==null?null:feed.optJSONArray("stories"),live=new JSONArray();
        if(all!=null)for(int i=0;i<all.length();i++){JSONObject story=all.optJSONObject(i);if(story!=null&&story.optLong("expires_at")>now)live.put(story);}
        JSONArray followed=feed==null?null:feed.optJSONArray("followed");boolean hasFollowing=followed!=null&&followed.length()>0;
        String next=selected+":"+hasFollowing+":"+live+":"+hasMore+":"+loading+":"+(feed==null);
        if(next.equals(signature))return;signature=next;
        GradientDrawable shape=new GradientDrawable();shape.setShape(GradientDrawable.OVAL);shape.setColor(selected?ui.blue:ui.bg);shape.setStroke(ui.dp(1),ui.blue);circle.setBackground(shape);
        heart.setImageResource(selected?R.drawable.ic_favorite_filled:R.drawable.ic_favorite_outline);heart.setColorFilter(selected?Color.WHITE:ui.blue);favoriteLabel.setTextColor(selected?ui.blue:ui.ink);
        favorite.setContentDescription(selected?"Favorit aktif. Tekan untuk melihat semua toko":"Lihat toko dan menu favorit");
        boolean empty=live.length()==0&&!hasMore;hint.setVisibility(empty?VISIBLE:GONE);scroll.setVisibility(empty?GONE:VISIBLE);
        if(empty){hint.setText(feed==null?(loading?"Memuat cerita mitra…":"Cerita belum dimuat. Tarik untuk mencoba lagi."):hasFollowing?"Belum ada cerita baru dari toko yang kamu ikuti. Mampir lagi nanti, ya!":"Ikuti toko yang kamu suka. Cerita menu harian dan promo mereka bakal muncul di sini.");return;}
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
