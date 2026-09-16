package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** TransPay Split Bill 2.0: invitation, 60s confirmation, status and percentage distribution. */
public final class SplitBillManager {
    public interface Listener { void onChanged(String sessionKey, int groupSize, boolean ready); }
    private final Activity a; private final Handler h=new Handler(Looper.getMainLooper()); private final Listener listener;
    private static final String SPLIT_URL=ApiConfig.SERVER+"split_bill.php";
    private String sessionKey=""; private int groupSize=1; private boolean ready=false; private AlertDialog statusDialog; private Runnable poller;
    private final SharedPreferences prefs;
    private final String prefPrefix;
    public SplitBillManager(Activity a, Listener l){
        this.a=a;this.listener=l;
        this.prefs=a.getSharedPreferences("transiva_split_bill_v3", Activity.MODE_PRIVATE);
        this.prefPrefix=a.getClass().getName()+".";
        this.sessionKey=prefs.getString(prefPrefix+"session_key","");
        this.groupSize=Math.max(1,Math.min(4,prefs.getInt(prefPrefix+"group_size",1)));
        this.ready=prefs.getBoolean(prefPrefix+"ready",false);
        if(!sessionKey.isEmpty()) startBackgroundPoll();
    }
    public String sessionKey(){return sessionKey;} public int groupSize(){return groupSize;} public boolean ready(){return ready;}
    public void clear(){sessionKey="";groupSize=1;ready=false;stopPoll();persist();changed();}
    public void start(int size,int expectedTotal,String context){
        size=Math.max(2,Math.min(4,size)); final int n=size;
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(40,16,40,8);
        ArrayList<EditText> fields=new ArrayList<>();
        TextView help=new TextView(a);help.setText("Masukkan username, nomor HP, atau email customer. Setelah dikirim, FCM berlaku 60 detik.");help.setPadding(0,0,0,14);box.addView(help);
        for(int i=1;i<n;i++){
            EditText e=new EditText(a);
            e.setHint("Username / nomor HP / email Customer "+(i+1));
            e.setSingleLine(true);
            e.setTextColor(Color.rgb(20,38,64));
            e.setHintTextColor(Color.rgb(110,120,135));
            e.setTextSize(17);
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setPadding(18,10,18,10);
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMargins(0,6,0,8);
            box.addView(e,ep);fields.add(e);
        }
        new AlertDialog.Builder(a).setTitle("Split Bill • "+n+" orang").setView(box).setNegativeButton("Batal",null).setPositiveButton("Kirim Undangan",(d,w)->{
            JSONArray targets=new JSONArray();for(EditText e:fields){String t=e.getText().toString().trim();if(t.isEmpty()){toast("Semua customer undangan harus diisi");return;}targets.put(t);}create(n,expectedTotal,context,targets);
        }).show();
    }
    private void create(int n,int total,String context,JSONArray targets){new Thread(()->{try{JSONObject p=new JSONObject().put("action","create").put("group_size",n).put("expected_total",Math.max(0,total)).put("context",context).put("targets",targets);JSONObject r=TransivaHttpRepository.postJson(a,SPLIT_URL,p,15000);h.post(()->{if(!r.optBoolean("success")){toast(r.optString("message","Gagal mengirim undangan"));return;}sessionKey=r.optString("session_key","");groupSize=n;ready=false;persist();changed(); Intent si=new Intent(a,SplitBillSessionActivity.class);si.putExtra("session_key",sessionKey);a.startActivity(si); startBackgroundPoll();});}catch(Exception e){h.post(()->toast("Gagal mengirim undangan: "+e.getMessage()));}}).start();}
    public void showStatus(){if(sessionKey.isEmpty()){toast("Belum ada sesi Split Bill");return;}fetch(true);}
    private void fetch(boolean show){new Thread(()->{try{JSONObject p=new JSONObject().put("action","status").put("session_key",sessionKey);JSONObject r=TransivaHttpRepository.postJson(a,SPLIT_URL,p,12000);h.post(()->renderStatus(r,show));}catch(Exception e){h.post(()->{if(show)toast("Status Split Bill gagal dimuat");});}}).start();}
    private void renderStatus(JSONObject r,boolean show){if(!r.optBoolean("success")){if(show)toast(r.optString("message","Sesi Split Bill gagal"));return;}JSONObject s=r.optJSONObject("session");JSONArray ps=r.optJSONArray("participants");int remain=s==null?0:s.optInt("expires_in",0);boolean all=true;int accepted=0;if(ps!=null)for(int i=0;i<ps.length();i++){String st=ps.optJSONObject(i).optString("status");if("accepted".equals(st))accepted++;else all=false;}ready=all&&ps!=null&&ps.length()==groupSize;persist();changed();
        if(!show && statusDialog==null)return;LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(36,10,36,10);TextView timer=new TextView(a);timer.setText(remain>0?"⏳ Undangan aktif "+remain+" detik":"⏱ Undangan berakhir");timer.setTextSize(15);box.addView(timer);
        if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);TextView row=new TextView(a);String st=p.optString("status");String mark="accepted".equals(st)?"✅":"declined".equals(st)?"❌":"⏳";row.setText(mark+" "+p.optString("username","Customer")+" • "+String.format(Locale.US,"%.0f%%",p.optDouble("percentage",0)));row.setTextSize(16);row.setPadding(0,12,0,8);box.addView(row);}
        if(ready){TextView tip=new TextView(a);tip.setText("Semua peserta sudah menerima. Anda dapat mengatur persentase pembagian.");tip.setPadding(0,14,0,6);box.addView(tip);Button b=new Button(a);b.setText("Atur Persentase");b.setOnClickListener(v->showDistribution(ps));box.addView(b);}
        if(statusDialog!=null)statusDialog.dismiss();statusDialog=new AlertDialog.Builder(a).setTitle("Split Bill • "+accepted+"/"+groupSize+" siap").setView(box).setNegativeButton("Tutup",null).create();statusDialog.setOnDismissListener(x->{statusDialog=null;stopPoll();});statusDialog.show();if(remain>0&&!ready)startPoll();}
    private void startPoll(){stopPoll();poller=new Runnable(){public void run(){if(statusDialog==null)return;fetch(false);h.postDelayed(this,2000);}};h.postDelayed(poller,2000);}
    private void startBackgroundPoll(){stopPoll();poller=new Runnable(){public void run(){if(sessionKey.isEmpty()||ready)return;fetch(false);h.postDelayed(this,1000);}};h.postDelayed(poller,1000);}
    private void stopPoll(){if(poller!=null)h.removeCallbacks(poller);poller=null;}
    private void showDistribution(JSONArray ps){if(ps==null)return;LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,8,32,8);ArrayList<SeekBar> bars=new ArrayList<>();ArrayList<Integer> ids=new ArrayList<>();ArrayList<TextView> labels=new ArrayList<>();
      for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);TextView l=new TextView(a);l.setText(p.optString("username")+" • "+Math.round(p.optDouble("percentage"))+"%");box.addView(l);SeekBar b=new SeekBar(a);b.setMax(96);b.setProgress(Math.max(0,(int)Math.round(p.optDouble("percentage"))-1));final TextView fl=l;final String name=p.optString("username");b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar x,int v,boolean f){fl.setText(name+" • "+(v+1)+"%");}public void onStartTrackingTouch(SeekBar x){}public void onStopTrackingTouch(SeekBar x){}});box.addView(b);bars.add(b);ids.add(p.optInt("user_id"));labels.add(l);}
      new AlertDialog.Builder(a).setTitle("Atur Pembagian • total 100%").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->{JSONArray sh=new JSONArray();int sum=0;try{for(int i=0;i<bars.size();i++){int pct=bars.get(i).getProgress()+1;sum+=pct;sh.put(new JSONObject().put("user_id",ids.get(i)).put("percentage",pct));}}catch(Exception ignored){}if(sum!=100){toast("Total harus tepat 100%. Saat ini "+sum+"%.");return;}saveDistribution(sh);}).show();}
    private void saveDistribution(JSONArray shares){new Thread(()->{try{JSONObject p=new JSONObject().put("action","distribution").put("session_key",sessionKey).put("shares",shares);JSONObject r=TransivaHttpRepository.postJson(a,SPLIT_URL,p,12000);h.post(()->{toast(r.optString("message",r.optBoolean("success")?"Pembagian disimpan":"Gagal"));if(r.optBoolean("success"))showStatus();});}catch(Exception e){h.post(()->toast("Gagal menyimpan pembagian"));}}).start();}
    private void persist(){prefs.edit().putString(prefPrefix+"session_key",sessionKey).putInt(prefPrefix+"group_size",groupSize).putBoolean(prefPrefix+"ready",ready).apply();}
    private void changed(){if(listener!=null)listener.onChanged(sessionKey,groupSize,ready);}
    private void toast(String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
}
