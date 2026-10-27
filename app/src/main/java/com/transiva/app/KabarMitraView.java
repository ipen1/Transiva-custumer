package com.transiva.app;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Read work belongs to the Transfood lifecycle; writes use the same session guard. */
final class KabarMitraView extends LinearLayout {
 interface OpenStore {void open(JSONObject store);}
 final Activity a; final KabarMitraUi ui; final CustomerFeatureRuntimeController runtime;
 final Handler handler; final OpenStore opener; final String api=ApiConfig.ROOT+"server/customer_kabar_mitra.php";
 JSONObject data; boolean loading=false;long clock,anchor,nextRefresh;String error="";
 final Runnable clockTick=new Runnable(){public void run(){if(!isAttachedToWindow()||getWindowVisibility()!=VISIBLE)return;if(data!=null){long second=now();JSONArray arr=data.optJSONArray("stories");boolean expired=false;if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject s=arr.optJSONObject(i);if(s!=null&&s.optLong("expires_at")>lastDrawSecond&&s.optLong("expires_at")<=second)expired=true;}lastDrawSecond=second;if(expired)render();}if(!loading&&SystemClock.elapsedRealtime()>=nextRefresh)load();handler.postDelayed(this,1000);}};
 KabarMitraView(Activity a,CustomerFeatureRuntimeController r,Handler h,OpenStore o){super(a);this.a=a;ui=new KabarMitraUi(a);runtime=r;handler=h;opener=o;setOrientation(VERTICAL);load();}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,1000);}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(handler!=null){handler.removeCallbacks(clockTick);if(visibility==VISIBLE&&isAttachedToWindow())handler.post(clockTick);}}
 @Override protected void onDetachedFromWindow(){handler.removeCallbacks(clockTick);super.onDetachedFromWindow();}
 long now(){return clock+(SystemClock.elapsedRealtime()-anchor)/1000;}
 void load(){if(loading)return;nextRefresh=SystemClock.elapsedRealtime()+30000;loading=true;error="";render();java.util.concurrent.Future<?> pending=runtime.execute(()->{try{JSONObject res=TransivaHttpRepository.getJson(a,api,20000);runtime.post(handler,()->{loading=false;if(res.optBoolean("success")){data=res;clock=res.optLong("server_time");anchor=SystemClock.elapsedRealtime();}else error=res.optString("message","Kabar Mitra belum dapat dimuat.");render();});}catch(Exception e){runtime.post(handler,()->{loading=false;error="Koneksi gagal. Tekan Muat ulang untuk mencoba lagi.";render();});}});if(pending==null){loading=false;error="Sistem sedang sibuk. Tekan Muat ulang sebentar lagi.";render();}}
 private long lastDrawSecond=-1;
 void render(){
  long second=now();lastDrawSecond=data==null?-1:second;removeAllViews();LinearLayout hero=ui.box();hero.addView(ui.text("Kabar Mitra",23,true));hero.addView(ui.text("Lebih dekat dengan toko pilihanmu",13,false));addView(hero);
  if(!error.isEmpty())addView(ui.text(error,14,false));Button refresh=ui.button(loading?"Memuat…":"Muat ulang");refresh.setEnabled(!loading);refresh.setOnClickListener(v->{lastDrawSecond=-1;load();});addView(refresh);
  if(data==null)return;JSONArray stories=data.optJSONArray("stories");LinearLayout bar=ui.box();ui.storyBar(bar,stories==null?new JSONArray():stories,second);addView(bar);
  JSONArray followed=data.optJSONArray("followed");addView(ui.text("Toko Diikuti · "+(followed==null?0:followed.length()),18,true));
  if(followed==null||followed.length()==0)addView(ui.text("Ikuti toko untuk melihat story dan kabar terbaru mereka di sini.",14,false));else for(int i=0;i<followed.length();i++)store(followed.optJSONObject(i),true);
  addView(ui.text("Temukan Mitra",18,true));JSONArray discover=data.optJSONArray("discover");if(discover!=null)for(int i=0;i<discover.length();i++)store(discover.optJSONObject(i),false);
 }
 void store(JSONObject s,boolean followed){if(s==null)return;LinearLayout box=ui.box();LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);ImageView img=new ImageView(a);img.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(img,new LinearLayout.LayoutParams(ui.dp(64),ui.dp(64)));ui.image(img,s.optString("banner"));TextView name=ui.text(s.optString("name","Mitra"),16,true);name.setPadding(ui.dp(12),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));box.addView(row);row.setOnClickListener(v->opener.open(s));box.addView(ui.text(s.optInt("is_open")==1?"Menerima pesanan":"Toko sedang tutup",12,false));Button follow=ui.button(followed?"✓ Mengikuti":"+ Ikuti Toko");follow.setOnClickListener(v->changeFollow(a,runtime,handler,s.optInt("id"),!followed,follow,()->{lastDrawSecond=-1;load();}));box.addView(follow);addView(box);}
 static void bindFollow(Activity a,CustomerFeatureRuntimeController r,Handler h,int rid,Button b){b.setEnabled(false);r.execute(()->{try{JSONObject res=TransivaHttpRepository.getJson(a,ApiConfig.ROOT+"server/customer_kabar_mitra.php?restaurant_id="+rid,20000);r.post(h,()->{if(res.optBoolean("success")){boolean f=res.optBoolean("following");b.setText(f?"✓ Mengikuti":"+ Ikuti Toko");b.setContentDescription(f?"Berhenti mengikuti toko":"Ikuti toko");b.setEnabled(true);b.setOnClickListener(v->changeFollow(a,r,h,rid,!f,b,()->bindFollow(a,r,h,rid,b)));}else{b.setText("Coba lagi");b.setEnabled(true);b.setOnClickListener(v->bindFollow(a,r,h,rid,b));}});}catch(Exception e){r.post(h,()->{b.setText("Coba lagi");b.setEnabled(true);b.setOnClickListener(v->bindFollow(a,r,h,rid,b));});}});}
 static void changeFollow(Activity a,CustomerFeatureRuntimeController r,Handler h,int rid,boolean follow,Button b,Runnable done){b.setEnabled(false);r.execute(()->{try{JSONObject p=new JSONObject();p.put("action","follow");p.put("restaurant_id",rid);p.put("following",follow);JSONObject res=TransivaHttpRepository.postJson(a,ApiConfig.ROOT+"server/customer_kabar_mitra.php",p,20000);r.post(h,()->{b.setEnabled(true);if(res.optBoolean("success"))done.run();else b.setText(res.optString("message","Gagal. Tekan untuk mencoba lagi."));});}catch(Exception e){r.post(h,()->{b.setEnabled(true);b.setText("Koneksi gagal · Coba lagi");});}});}
}
