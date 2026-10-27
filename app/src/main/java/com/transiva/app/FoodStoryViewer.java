package com.transiva.app;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One window for the entire story session, with bounded image loading and gesture playback. */
final class FoodStoryViewer {
    final KabarMitraUi ui;
    final Activity a;
    final ArrayList<JSONObject> items=new ArrayList<>();
    final Handler handler=new Handler(Looper.getMainLooper());
    final FoodStoryPlayback playback=new FoodStoryPlayback();
    final long serverTime,anchor=SystemClock.elapsedRealtime();
    Dialog dialog;
    FrameLayout root,media;
    ImageView photo,backdrop;
    KabarMitraUi.Avatar avatar;
    TextView name,time,caption,hint,previous,next;
    FoodStoryProgressView progress;
    ScrollView captionArea;
    int at,groupStart,groupCount;
    long loadDeadline,downAt;
    float downX,downY;
    boolean closing,holding,background,dragging;
    Runnable afterClose;

    FoodStoryViewer(KabarMitraUi ui,JSONArray all,int rid,long serverTime){
        this.ui=ui;this.a=ui.a;this.serverTime=serverTime;
        items.addAll(playlist(all,rid,serverTime));
    }
    static List<JSONObject> playlist(JSONArray all,int rid,long now){
        LinkedHashMap<Integer,ArrayList<JSONObject>> groups=new LinkedHashMap<>();
        if(all!=null)for(int i=all.length()-1;i>=0;i--){JSONObject s=all.optJSONObject(i);if(s!=null&&s.optLong("expires_at")>now){int id=s.optInt("restaurant_id");if(!groups.containsKey(id))groups.put(id,new ArrayList<>());groups.get(id).add(s);}}
        ArrayList<JSONObject> result=new ArrayList<>();
        if(!groups.containsKey(rid))return result;
        result.addAll(groups.remove(rid));for(ArrayList<JSONObject> group:groups.values())result.addAll(group);return result;
    }
    int dp(int n){return ui.dp(n);}
    TextView label(String s,int sp,boolean bold){TextView v=ui.text(s,sp,bold);v.setTextColor(Color.WHITE);return v;}
    TextView control(String s,String description){TextView v=label(s,20,true);v.setGravity(Gravity.CENTER);v.setContentDescription(description);return v;}
    void open(){
        if(items.isEmpty()||a.isFinishing()||a.isDestroyed())return;
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root=new FrameLayout(a);root.setBackgroundColor(0xFF071221);root.setFitsSystemWindows(false);CustomerAppSettings.preserveArtwork(root);
        media=new FrameLayout(a);root.addView(media,new FrameLayout.LayoutParams(-1,-1));
        backdrop=new ImageView(a);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);backdrop.setAlpha(.22f);media.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        photo=new ImageView(a);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);media.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        photo.setContentDescription("Story: ketuk kiri atau kanan untuk berpindah, tahan untuk menjeda, geser turun untuk menutup");
        photo.setOnTouchListener((v,event)->touch(event));

        LinearLayout top=new LinearLayout(a);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(12),dp(12),dp(10),dp(20));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xDC071221,0x00071221}));
        progress=new FoodStoryProgressView(a);top.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        LinearLayout toolbar=new LinearLayout(a);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(0,dp(8),0,0);
        avatar=ui.avatar("",36,false);toolbar.addView(avatar,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout words=new LinearLayout(a);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(9),0,dp(4),0);
        name=label("",15,true);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);
        time=label("",11,false);time.setTextColor(0xFFD0DCEB);words.addView(time);toolbar.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=control("×","Tutup story");close.setOnClickListener(v->close(null));toolbar.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));top.addView(toolbar);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        LinearLayout bottom=new LinearLayout(a);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(16),dp(32),dp(16),dp(14));
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00071221,0xEB071221}));
        caption=label("",15,false);caption.setMaxLines(3);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setOnClickListener(v->{if(caption.getMaxLines()==3){caption.setMaxLines(Integer.MAX_VALUE);holding=true;}else{caption.setMaxLines(3);holding=false;}pause();});
        ScrollView captionScroll=new ScrollView(a);captionArea=captionScroll;captionScroll.setFillViewport(false);captionScroll.addView(caption);bottom.addView(captionScroll,new LinearLayout.LayoutParams(-1,dp(72)));
        captionScroll.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){holding=true;pause();}else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){holding=caption.getMaxLines()!=3;pause();}return false;});
        LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);
        previous=control("‹","Story sebelumnya");previous.setOnClickListener(v->move(-1));actions.addView(previous,new LinearLayout.LayoutParams(dp(44),dp(44)));
        hint=label("Tahan untuk jeda",11,false);hint.setTextColor(0xFFD0DCEB);actions.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=label("Lihat menu  ›",13,true);menu.setGravity(Gravity.CENTER);GradientDrawable pill=new GradientDrawable();pill.setColor(0xDD0B7CFF);pill.setCornerRadius(dp(22));menu.setBackground(pill);menu.setPadding(dp(14),0,dp(14),0);
        menu.setOnClickListener(v->{if(closing||at>=items.size())return;JSONObject story=items.get(at);int id=story.optInt("restaurant_id");String title=story.optString("name","Merchant");close(()->openMenu(id,title));});
        actions.addView(menu,new LinearLayout.LayoutParams(-2,dp(44)));
        next=control("›","Story berikutnya");next.setOnClickListener(v->move(1));actions.addView(next,new LinearLayout.LayoutParams(dp(44),dp(44)));bottom.addView(actions);
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        dialog.setContentView(root);
        dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)close(null);return true;}return false;});
        Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity x,Bundle b){}public void onActivityStarted(Activity x){}public void onActivitySaveInstanceState(Activity x,Bundle b){}
            public void onActivityResumed(Activity x){if(x==a){background=false;pause();}}
            public void onActivityPaused(Activity x){if(x==a){holding=caption.getMaxLines()!=3;background=true;pause();}}
            public void onActivityStopped(Activity x){if(x==a)dialog.dismiss();}public void onActivityDestroyed(Activity x){if(x==a)dialog.dismiss();}
        };
        a.getApplication().registerActivityLifecycleCallbacks(lifecycle);
        dialog.setOnDismissListener(d->{closing=true;handler.removeCallbacksAndMessages(null);root.animate().cancel();media.animate().cancel();photo.setTag(new Object());avatar.photo.setTag(new Object());a.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);Runnable action=afterClose;afterClose=null;if(action!=null&&!a.isFinishing()&&!a.isDestroyed())action.run();});
        dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setWindowAnimations(0);w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);CustomerWindowPolicy.install(w);CustomerWindowPolicy.status(w,0xFF071221);CustomerWindowPolicy.navigation(w,0xFF071221);CustomerWindowPolicy.appearance(w,0);}
        root.setAlpha(0f);root.setScaleX(.96f);root.setScaleY(.96f);root.animate().alpha(1).scaleX(1).scaleY(1).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        show();handler.post(tick);
    }
    void show(){
        if(closing)return;
        long now=now();while(at<items.size()&&items.get(at).optLong("expires_at")<=now)at++;
        if(at>=items.size()){close(null);return;}
        JSONObject story=items.get(at);int rid=story.optInt("restaurant_id");groupStart=at;while(groupStart>0&&items.get(groupStart-1).optInt("restaurant_id")==rid)groupStart--;
        int end=at+1;while(end<items.size()&&items.get(end).optInt("restaurant_id")==rid)end++;groupCount=end-groupStart;
        name.setText(story.optString("name","Kabar Mitra"));long published=story.optLong("published_at",story.optLong("expires_at")-86400);
        time.setText(ageLabel(published,now));caption.setText(story.optString("caption"));caption.setMaxLines(3);captionArea.setVisibility(story.optString("caption").isEmpty()?View.GONE:View.VISIBLE);
        previous.setAlpha(at==0?.35f:1f);previous.setEnabled(at>0);next.setContentDescription(at==items.size()-1?"Selesai melihat story":"Story berikutnya");next.setText(at==items.size()-1?"✓":"›");
        ui.image(avatar.photo,story.optString("banner"));
        playback.reset();holding=false;dragging=false;pause();hint.setText("Memuat foto…");loadDeadline=SystemClock.elapsedRealtime()+15000;
        progress.showProgress(groupCount,at-groupStart,0);backdrop.setImageDrawable(null);photo.setAlpha(0f);ui.image(photo,story.optString("image"));
    }
    static String ageLabel(long published,long now){long age=Math.max(0,now-published);if(age<60)return "Baru saja";if(age<3600)return age/60+" menit lalu";return age/3600+" jam lalu";}
    long now(){return serverTime+(SystemClock.elapsedRealtime()-anchor)/1000;}
    void pause(){playback.pause(holding||background,SystemClock.elapsedRealtime());if(hint!=null&&playback.loaded())hint.setText(holding||background?"Dijeda":"Tahan untuk jeda");}
    void move(int direction){if(closing)return;if(direction<0&&at==0)return;photo.animate().cancel();at+=direction;show();}
    void close(Runnable action){if(closing)return;closing=true;afterClose=action;handler.removeCallbacksAndMessages(null);root.animate().cancel();root.animate().alpha(0).scaleX(.97f).scaleY(.97f).setDuration(150).withEndAction(dialog::dismiss).start();}
    void openMenu(int rid,String title){
        if(a instanceof TransFoodActivityScreenCore){((TransFoodActivityScreenCore)a).openStoryMerchant(rid,title);return;}
        Intent intent=new Intent(a,TransFoodActivity.class);intent.putExtra("story_restaurant_id",rid);intent.putExtra("story_restaurant_name",title);a.startActivity(intent);
    }
    boolean touch(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();downAt=SystemClock.elapsedRealtime();holding=true;pause();return true;}
        if(action==MotionEvent.ACTION_MOVE){float dx=e.getX()-downX,dy=e.getY()-downY;if(dy>dp(16)&&dy>Math.abs(dx)*1.3f){dragging=true;media.setTranslationY(dy);media.setScaleX(Math.max(.85f,1-dy/root.getHeight()*.2f));media.setScaleY(media.getScaleX());}return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            float dy=e.getY()-downY;boolean swiped=dragging&&dy>dp(80);holding=caption.getMaxLines()!=3;pause();
            if(action==MotionEvent.ACTION_UP&&swiped){close(null);return true;}
            media.animate().translationY(0).scaleX(1).scaleY(1).setDuration(160).start();
            if(action==MotionEvent.ACTION_UP&&!dragging&&SystemClock.elapsedRealtime()-downAt<240&&Math.abs(e.getX()-downX)<dp(24)&&Math.abs(dy)<dp(24))move(e.getX()<photo.getWidth()*.35f?-1:1);
            dragging=false;return true;
        }return true;
    }
    final Runnable tick=new Runnable(){public void run(){
        if(closing||!dialog.isShowing()||at>=items.size())return;
        long elapsed=SystemClock.elapsedRealtime();
        if(items.get(at).optLong("expires_at")<=now())move(1);
        else if(!playback.loaded()){
            if(photo.getDrawable()!=null){
                playback.start(elapsed);photo.animate().alpha(1).setDuration(160).start();
                // Reuse the loaded bitmap, never request the same photo again for the backdrop.
                if(photo.getDrawable() instanceof BitmapDrawable){Bitmap bitmap=((BitmapDrawable)photo.getDrawable()).getBitmap();if(bitmap!=null&&!bitmap.isRecycled()){int width=24,height=Math.max(1,Math.min(96,Math.round(24f*bitmap.getHeight()/bitmap.getWidth())));backdrop.setImageBitmap(Bitmap.createScaledBitmap(bitmap,width,height,true));}}
                hint.setText(holding||background?"Dijeda":"Tahan untuk jeda");
            }else if(elapsed>=loadDeadline){Toast.makeText(a,"Foto belum bisa dimuat. Kita lanjut, ya.",Toast.LENGTH_SHORT).show();move(1);}
        }else{float value=playback.fraction(elapsed);progress.showProgress(groupCount,at-groupStart,value);if(value>=1)move(1);}
        if(!closing)handler.postDelayed(this,16);
    }};
}
