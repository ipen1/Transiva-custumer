package com.transiva.app;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Stable feed: keep existing rows while refreshing; repaint only changed content. */
final class KabarMitraView extends LinearLayout {
    interface OpenStore { void open(JSONObject store); }
    interface FeedListener { void loaded(JSONObject feed); }
    final Activity a;
    final KabarMitraUi ui;
    final CustomerFeatureRuntimeController runtime;
    final Handler handler;
    final OpenStore opener;
    final FeedListener listener;
    final String api=ApiConfig.ROOT+"server/customer_kabar_mitra.php";
    final LinearLayout content;
    final TextView status;
    JSONObject data;
    boolean loading;
    long clock, anchor, nextRefresh;
    String error="", signature="";
    Runnable refreshDone;
    final Runnable clockTick=new Runnable(){public void run(){
        if(!isAttachedToWindow() || getWindowVisibility()!=VISIBLE)return;
        if(data!=null)render();
        if(!loading && SystemClock.elapsedRealtime()>=nextRefresh)load();
        handler.postDelayed(this,1000);
    }};
    KabarMitraView(Activity a,CustomerFeatureRuntimeController runtime,Handler handler,
                   OpenStore opener,FeedListener listener,JSONObject cached){
        super(a);this.a=a;this.ui=new KabarMitraUi(a);this.runtime=runtime;
        this.handler=handler;this.opener=opener;this.listener=listener;
        setOrientation(VERTICAL);
        addView(ui.text("Kabar Mitra",19,true));
        status=ui.text("Tarik ke bawah untuk memperbarui",11,false);
        status.setTextColor(ui.muted);addView(status);
        content=new LinearLayout(a);content.setOrientation(VERTICAL);addView(content);
        if(cached!=null){data=cached;clock=cached.optLong("server_time");anchor=SystemClock.elapsedRealtime();render();}
        load();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,1000);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(handler!=null){handler.removeCallbacks(clockTick);if(visibility==VISIBLE&&isAttachedToWindow())handler.post(clockTick);}}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(clockTick);if(refreshDone!=null){refreshDone.run();refreshDone=null;}super.onDetachedFromWindow();}
    long now(){return clock+(SystemClock.elapsedRealtime()-anchor)/1000;}
    void refresh(Runnable done){refreshDone=done;load();}
    void complete(){if(refreshDone!=null){refreshDone.run();refreshDone=null;}}
    void load(){
        if(loading)return;
        loading=true;error="";nextRefresh=SystemClock.elapsedRealtime()+30000;render();
        java.util.concurrent.Future<?> task=runtime.execute(()->{
            try{
                JSONObject response=TransivaHttpRepository.getJson(a,api,20000);
                runtime.post(handler,()->{
                    loading=false;
                    if(response.optBoolean("success")){data=response;clock=response.optLong("server_time");anchor=SystemClock.elapsedRealtime();listener.loaded(response);}
                    else error=response.optString("message","Kabar Mitra belum tersedia.");
                    render();complete();
                });
            }catch(Exception e){runtime.post(handler,()->{loading=false;error="Koneksi gagal. Tarik ke bawah untuk mencoba lagi.";render();complete();});}
        });
        if(task==null){loading=false;error="Sistem sedang sibuk. Tarik ke bawah untuk mencoba lagi.";render();complete();}
    }
    void render(){
        String label=!error.isEmpty()?error:loading?"Memperbarui kabar…":"Tarik ke bawah untuk memperbarui";
        if(!status.getText().toString().equals(label))status.setText(label);
        if(data==null)return;
        JSONArray live=new JSONArray(),stories=data.optJSONArray("stories");
        if(stories!=null)for(int i=0;i<stories.length();i++){JSONObject story=stories.optJSONObject(i);if(story!=null&&story.optLong("expires_at")>now())live.put(story);}
        JSONArray followed=data.optJSONArray("followed"),discover=data.optJSONArray("discover");
        StringBuilder borders=new StringBuilder();
        for(JSONArray stores:new JSONArray[]{followed,discover})if(stores!=null)for(int i=0;i<stores.length();i++){
            JSONObject store=stores.optJSONObject(i);if(store!=null)borders.append(store.optInt("id")).append(KabarMitraUi.hasStory(store,live,now()));
        }
        String next=live.toString()+String.valueOf(followed)+String.valueOf(discover)+borders;
        if(signature.equals(next))return;
        signature=next;content.removeAllViews();
        LinearLayout bar=ui.box();ui.storyBar(bar,live,now());content.addView(bar);
        content.addView(ui.text("Toko Diikuti · "+(followed==null?0:followed.length()),15,true));
        if(followed==null||followed.length()==0)content.addView(ui.text("Ikuti toko pilihanmu untuk melihat kabarnya.",12,false));
        else for(int i=0;i<followed.length();i++)store(followed.optJSONObject(i),true,live);
        if(discover!=null&&discover.length()>0){content.addView(ui.text("Temukan Mitra",15,true));for(int i=0;i<discover.length();i++)store(discover.optJSONObject(i),false,live);}
    }
    void store(JSONObject store,boolean followed,JSONArray live){
        if(store==null)return;
        LinearLayout box=ui.box();box.setOrientation(HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);
        int rid=store.optInt("id");
        KabarMitraUi.Avatar avatar=ui.avatar(store.optString("banner"),48,KabarMitraUi.hasStory(store,live,now()));
        box.addView(avatar);
        avatar.setOnClickListener(v->{if(KabarMitraUi.hasStory(store,data.optJSONArray("stories"),now()))ui.openStories(runtime,handler,rid,data.optJSONArray("stories"),now(),avatar);else opener.open(store);});
        LinearLayout words=new LinearLayout(a);words.setOrientation(VERTICAL);words.setPadding(ui.dp(10),0,ui.dp(5),0);
        TextView name=ui.text(store.optString("name","Mitra"),14,true);name.setMaxLines(2);words.addView(name);
        TextView availability=ui.text(store.optInt("is_open")==1?"Menerima pesanan":"Sedang tutup",11,false);availability.setTextColor(ui.muted);words.addView(availability);
        words.setOnClickListener(v->opener.open(store));box.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Button follow=ui.button(followed?"✓ Mengikuti":"+ Ikuti");follow.setTextSize(11);follow.setPadding(0,0,0,0);follow.setMinWidth(0);follow.setMinimumWidth(0);
        box.addView(follow,new LinearLayout.LayoutParams(ui.dp(88),ui.dp(40)));
        follow.setContentDescription(followed?"Berhenti mengikuti toko":"Ikuti toko");
        follow.setOnClickListener(v->changeFollow(a,runtime,handler,rid,!followed,follow,this::load));content.addView(box);
    }
    static void bindFollow(Activity a,CustomerFeatureRuntimeController r,Handler h,int rid,Button b,JSONObject store,Runnable changed){
        if(store.has("is_following")){bindKnown(a,r,h,rid,b,store,changed);return;}
        b.setEnabled(false);
        java.util.concurrent.Future<?> task=r.execute(()->{try{
            JSONObject res=TransivaHttpRepository.getJson(a,ApiConfig.ROOT+"server/customer_kabar_mitra.php?restaurant_id="+rid,20000);
            r.post(h,()->{if(res.optBoolean("success")){try{store.put("is_following",res.optBoolean("following"));}catch(Exception ignored){}bindKnown(a,r,h,rid,b,store,changed);}else retry(a,r,h,rid,b,store,changed);});
        }catch(Exception e){r.post(h,()->retry(a,r,h,rid,b,store,changed));}});
        if(task==null)retry(a,r,h,rid,b,store,changed);
    }
    private static void retry(Activity a,CustomerFeatureRuntimeController r,Handler h,int rid,Button b,JSONObject store,Runnable changed){b.setText("Coba lagi");b.setEnabled(true);b.setOnClickListener(v->bindFollow(a,r,h,rid,b,store,changed));}
    private static void bindKnown(Activity a,CustomerFeatureRuntimeController r,Handler h,int rid,Button b,JSONObject store,Runnable changed){
        boolean following=store.optBoolean("is_following")||store.optInt("is_following")==1;
        b.setText(following?"✓ Mengikuti":"+ Ikuti Toko");b.setEnabled(true);b.setContentDescription(following?"Berhenti mengikuti toko":"Ikuti toko");
        b.setOnClickListener(v->changeFollow(a,r,h,rid,!following,b,()->{try{store.put("is_following",!following);}catch(Exception ignored){}bindKnown(a,r,h,rid,b,store,changed);changed.run();}));
    }
    static void changeFollow(Activity a,CustomerFeatureRuntimeController r,Handler h,int rid,boolean following,Button b,Runnable done){
        b.setEnabled(false);
        java.util.concurrent.Future<?> task=r.execute(()->{try{
            JSONObject p=new JSONObject();p.put("action","follow");p.put("restaurant_id",rid);p.put("following",following);
            JSONObject res=TransivaHttpRepository.postJson(a,ApiConfig.ROOT+"server/customer_kabar_mitra.php",p,20000);
            r.post(h,()->{b.setEnabled(true);if(res.optBoolean("success"))done.run();else android.widget.Toast.makeText(a,res.optString("message","Gagal memperbarui toko."),Toast.LENGTH_SHORT).show();});
        }catch(Exception e){r.post(h,()->{b.setEnabled(true);Toast.makeText(a,"Koneksi gagal. Coba lagi.",Toast.LENGTH_SHORT).show();});}});
        if(task==null)b.setEnabled(true);
    }
}
