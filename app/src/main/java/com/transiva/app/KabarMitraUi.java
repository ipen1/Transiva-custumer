package com.transiva.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Explicit paired foreground/background colors, also inside the story window. */
final class KabarMitraUi {
 final Activity a; final int bg,ink,muted,blue=Color.rgb(11,120,255); final boolean dark;
 KabarMitraUi(Activity a){this.a=a;dark=CustomerAppSettings.isDarkMode(a);bg=Color.parseColor(dark?"#122235":"#FFFFFF");ink=Color.parseColor(dark?"#EEF5FF":"#133152");muted=Color.parseColor(dark?"#A9B8CC":"#60758E");}
 int dp(int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 LinearLayout box(){LinearLayout v=new LinearLayout(a);v.setOrientation(1);v.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(16));d.setStroke(dp(1),Color.parseColor(dark?"#2D435C":"#DAE8F8"));v.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(6));v.setLayoutParams(p);return v;}
 TextView text(String s,int size,boolean bold){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(ink);t.setPadding(0,dp(4),0,dp(4));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 Button button(String s){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14);GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{blue,Color.rgb(25,157,234)});d.setCornerRadius(dp(14));b.setBackground(d);b.setMinHeight(dp(44));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(4));b.setLayoutParams(p);return b;}
 void image(ImageView v,String path){RemoteImageLoader.loadPreserveScale(v,imageUrl(path),0);}
 String imageUrl(String path){if(path.startsWith("https://"))return path;if(path.startsWith("http://"))return path.replaceFirst("http://","https://");return "https://transiva.my.id/"+(path.startsWith("uploads/mitra_stories/")?"server/":"")+path.replaceFirst("^/","");}
 static final class Avatar extends FrameLayout {
  final ImageView photo; final KabarMitraUi ui; boolean active;
  Avatar(KabarMitraUi ui,String path,int size,boolean story){super(ui.a);this.ui=ui;setPadding(ui.dp(3),ui.dp(3),ui.dp(3),ui.dp(3));photo=new ImageView(ui.a);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);GradientDrawable shape=new GradientDrawable();shape.setColor(ui.bg);shape.setShape(GradientDrawable.OVAL);photo.setBackground(shape);photo.setClipToOutline(true);addView(photo,new FrameLayout.LayoutParams(-1,-1));setLayoutParams(new LinearLayout.LayoutParams(ui.dp(size),ui.dp(size)));active=!story;setStory(story);ui.image(photo,path);}
  void setStory(boolean story){if(active==story)return;active=story;GradientDrawable ring=new GradientDrawable();ring.setShape(GradientDrawable.OVAL);ring.setColor(ui.bg);ring.setStroke(ui.dp(story?3:1),story?ui.blue:Color.parseColor(ui.dark?"#2D435C":"#D9E5F4"));setBackground(ring);setContentDescription(story?"Profil toko · story tersedia":"Profil toko");}
 }
 Avatar avatar(String path,int size,boolean story){return new Avatar(this,path,size,story);}
 static boolean hasStory(JSONArray stories,int rid,long now){if(stories==null)return false;for(int i=0;i<stories.length();i++){JSONObject s=stories.optJSONObject(i);if(s!=null&&s.optInt("restaurant_id")==rid&&s.optLong("expires_at")>now)return true;}return false;}
 static boolean hasStory(JSONObject store,JSONArray stories,long now){return hasStory(stories,store.optInt("id"),now)||store.optLong("story_expires_at")>now;}
 void openStories(CustomerFeatureRuntimeController runtime,Handler handler,int rid,JSONArray known,long now,View source){
  if(hasStory(known,rid,now)){viewer(known,rid,now);return;}
  source.setEnabled(false);
  java.util.concurrent.Future<?> task=runtime.execute(()->{try{
   JSONObject res=TransivaHttpRepository.getJson(a,ApiConfig.ROOT+"server/customer_kabar_mitra.php?restaurant_id="+rid,20000);
   runtime.post(handler,()->{source.setEnabled(true);JSONArray stories=res.optJSONArray("stories");long clock=res.optLong("server_time");if(res.optBoolean("success")&&hasStory(stories,rid,clock))viewer(stories,rid,clock);else Toast.makeText(a,"Story aktif tidak tersedia.",Toast.LENGTH_SHORT).show();});
  }catch(Exception e){runtime.post(handler,()->{source.setEnabled(true);Toast.makeText(a,"Koneksi gagal membuka story.",Toast.LENGTH_SHORT).show();});}});
  if(task==null)source.setEnabled(true);
 }
 void storyBar(LinearLayout parent,JSONArray stories,long serverTime){
  parent.addView(text("Story Mitra",15,true));
  HorizontalScrollView sc=new HorizontalScrollView(a);sc.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(a);sc.addView(row);Set<Integer> seen=new HashSet<>();long builtAt=SystemClock.elapsedRealtime();
  for(int i=0;i<stories.length();i++){JSONObject s=stories.optJSONObject(i);if(s==null||s.optLong("expires_at")<=serverTime||!seen.add(s.optInt("restaurant_id")))continue;
   int rid=s.optInt("restaurant_id");LinearLayout tile=new LinearLayout(a);tile.setOrientation(1);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(5),dp(3),dp(2));
   String photo=s.optString("banner");if(photo.isEmpty())photo=s.optString("image");
   tile.addView(avatar(photo,56,true));TextView name=text(s.optString("name","Mitra"),11,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER);tile.addView(name,new LinearLayout.LayoutParams(dp(70),-2));tile.setOnClickListener(v->viewer(stories,rid,serverTime+(SystemClock.elapsedRealtime()-builtAt)/1000));row.addView(tile);
  }
  if(seen.isEmpty())parent.addView(text("Belum ada story aktif. Ikuti toko pilihanmu.",12,false));else parent.addView(sc,new LinearLayout.LayoutParams(-1,-2));
 }
 void viewer(JSONArray all,int rid,long serverTime){new FoodStoryViewer(this,all,rid,serverTime).open();}
}
