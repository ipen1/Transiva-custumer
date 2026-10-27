package com.transiva.app;

import android.app.Activity;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.util.HashSet;

/** One stable row: favorites stays left while followed-store stories scroll beside it. */
final class FoodStoryRail extends LinearLayout {
    private final Activity activity;
    private final KabarMitraUi ui;
    private final Runnable onFavorite;
    private String signature="";
    FoodStoryRail(Activity activity,Runnable onFavorite){super(activity);this.activity=activity;this.onFavorite=onFavorite;ui=new KabarMitraUi(activity);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(0,ui.dp(5),0,ui.dp(7));}
    void update(JSONObject feed,long serverTime,boolean selected){
        JSONArray all=feed==null?null:feed.optJSONArray("stories");
        JSONArray live=new JSONArray();
        if(all!=null)for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null&&s.optLong("expires_at")>serverTime)live.put(s);}
        JSONArray followed=feed==null?null:feed.optJSONArray("followed");
        boolean hasFollowing=followed!=null&&followed.length()>0;
        String next=selected+":"+hasFollowing+":"+live.toString();
        if(next.equals(signature))return;signature=next;removeAllViews();
        LinearLayout favorite=new LinearLayout(activity);favorite.setOrientation(VERTICAL);favorite.setGravity(Gravity.CENTER);favorite.setPadding(0,ui.dp(3),ui.dp(8),0);
        FrameLayout circle=new FrameLayout(activity);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);shape.setColor(selected?ui.blue:ui.bg);shape.setStroke(ui.dp(1),ui.blue);circle.setBackground(shape);
        ImageView heart=new ImageView(activity);heart.setImageResource(selected?R.drawable.ic_favorite_filled:R.drawable.ic_favorite_outline);heart.setColorFilter(selected?android.graphics.Color.WHITE:ui.blue);heart.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));circle.addView(heart,new FrameLayout.LayoutParams(-1,-1));favorite.addView(circle,new LinearLayout.LayoutParams(ui.dp(54),ui.dp(54)));
        TextView label=ui.text("Favorit",11,true);label.setGravity(Gravity.CENTER);if(selected)label.setTextColor(ui.blue);favorite.addView(label);favorite.setContentDescription(selected?"Favorit aktif. Tekan untuk melihat semua toko":"Lihat toko dan menu favorit");favorite.setOnClickListener(v->onFavorite.run());addView(favorite,new LinearLayout.LayoutParams(ui.dp(70),-2));
        if(live.length()==0){
            TextView hint=ui.text(hasFollowing?"Belum ada cerita baru dari toko yang kamu ikuti. Mampir lagi nanti, ya!":"Ikuti toko yang kamu suka. Cerita menu harian dan promo mereka bakal muncul di sini.",12,false);hint.setTextColor(ui.muted);hint.setMaxLines(3);hint.setPadding(ui.dp(6),0,ui.dp(3),0);addView(hint,new LinearLayout.LayoutParams(0,-2,1));return;
        }
        HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);scroll.addView(row);addView(scroll,new LinearLayout.LayoutParams(0,-2,1));
        HashSet<Integer> ids=new HashSet<>();long builtAt=SystemClock.elapsedRealtime();
        for(int i=0;i<live.length();i++){
            JSONObject s=live.optJSONObject(i);int rid=s.optInt("restaurant_id");if(!ids.add(rid))continue;
            LinearLayout tile=new LinearLayout(activity);tile.setOrientation(VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(ui.dp(3),ui.dp(3),ui.dp(3),0);
            String photo=s.optString("banner");if(photo.isEmpty())photo=s.optString("image");tile.addView(ui.avatar(photo,54,true));TextView name=ui.text(s.optString("name","Mitra"),11,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);tile.addView(name,new LinearLayout.LayoutParams(ui.dp(67),-2));tile.setOnClickListener(v->ui.viewer(live,rid,serverTime+(SystemClock.elapsedRealtime()-builtAt)/1000));row.addView(tile);
        }
    }
}
