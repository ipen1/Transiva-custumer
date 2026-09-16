package com.transiva.app;

import android.app.*;import android.os.*;import android.graphics.Color;import android.content.*;import android.view.*;import android.widget.*;import org.json.*;

/** Dedicated realtime screen for a Split Bill invitation. */
public class SplitBillInviteActivity extends Activity {
 private String key=""; private LinearLayout root; private TextView timer,detail; private Button yes,no;
 private final Handler h=new Handler(Looper.getMainLooper()); private Runnable secondTick,poll; private long deadline=0L; private boolean busy=false;
 private final BroadcastReceiver realtime=new BroadcastReceiver(){public void onReceive(Context c,Intent i){String k=i.getStringExtra("session_key");if(key.equals(k)) load(false);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);key=getIntent().getStringExtra("session_key");build();registerRt();load(true);}
 private void build(){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(28),dp(42),dp(28),dp(28));root.setBackgroundColor(Color.WHITE);setContentView(root);
  TextView title=new TextView(this);title.setText("💳 Undangan Split Bill");title.setTextSize(27);title.setTextColor(Color.rgb(11,58,120));title.setTypeface(null,1);root.addView(title);
  detail=new TextView(this);detail.setText("Memuat undangan…");detail.setTextSize(17);detail.setTextColor(Color.rgb(45,55,70));detail.setPadding(0,dp(24),0,dp(12));root.addView(detail);
  timer=new TextView(this);timer.setText("00:60");timer.setTextSize(32);timer.setTextColor(Color.rgb(0,122,92));timer.setTypeface(null,1);timer.setGravity(Gravity.CENTER);timer.setPadding(0,dp(12),0,dp(22));root.addView(timer);
  yes=new Button(this);yes.setText("✓ TERIMA SPLIT BILL");yes.setEnabled(false);yes.setOnClickListener(v->respond(true));root.addView(yes,new LinearLayout.LayoutParams(-1,dp(56)));
  no=new Button(this);no.setText("TOLAK");no.setEnabled(false);no.setOnClickListener(v->respond(false));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(52));np.topMargin=dp(10);root.addView(no,np);
 }
 private void load(boolean first){if(busy)return;busy=true;new Thread(()->{try{JSONObject q=new JSONObject().put("action","status").put("session_key",key);JSONObject r=TransivaHttpRepository.postJson(this,ApiConfig.SERVER+"split_bill.php",q,12000);h.post(()->{busy=false;render(r,first);});}catch(Exception e){h.post(()->{busy=false;if(first)detail.setText("Koneksi server bermasalah. Mencoba kembali otomatis…");});}}).start();}
 private void render(JSONObject r,boolean first){if(!r.optBoolean("success")){detail.setText(r.optString("message","Undangan tidak tersedia"));yes.setEnabled(false);no.setEnabled(false);return;}JSONObject s=r.optJSONObject("session");int sec=s==null?0:s.optInt("expires_in",0);deadline=SystemClock.elapsedRealtime()+sec*1000L;String status=s==null?"":s.optString("status");detail.setText("Anda diundang membayar bersama melalui Transiva Pay.\nSaldo diperiksa saat menerima dan divalidasi lagi saat order dibuat.");boolean active=sec>0&&"inviting".equals(status);yes.setEnabled(active);no.setEnabled(active);startClock();}
 private void startClock(){if(secondTick!=null)h.removeCallbacks(secondTick);secondTick=new Runnable(){public void run(){long ms=Math.max(0,deadline-SystemClock.elapsedRealtime());long sec=(ms+999)/1000;timer.setText(String.format(java.util.Locale.US,"00:%02d",sec));if(sec<=10)timer.setTextColor(Color.rgb(210,50,50));if(ms>0)h.postDelayed(this,250);else{yes.setEnabled(false);no.setEnabled(false);timer.setText("KEDALUWARSA");}}};h.post(secondTick);}
 private void startPoll(){stopPoll();poll=new Runnable(){public void run(){load(false);h.postDelayed(this,1000);}};h.postDelayed(poll,1000);}
 private void stopPoll(){if(poll!=null)h.removeCallbacks(poll);poll=null;}
 private void respond(boolean accept){yes.setEnabled(false);no.setEnabled(false);new Thread(()->{try{JSONObject q=new JSONObject().put("action","respond").put("session_key",key).put("accept",accept);JSONObject r=TransivaHttpRepository.postJson(this,ApiConfig.SERVER+"split_bill.php",q,12000);h.post(()->{Toast.makeText(this,r.optString("message"),Toast.LENGTH_LONG).show();if(r.optBoolean("success"))finish();else{yes.setEnabled(true);no.setEnabled(true);}});}catch(Exception e){h.post(()->{Toast.makeText(this,"Gagal memproses undangan",Toast.LENGTH_LONG).show();yes.setEnabled(true);no.setEnabled(true);});}}).start();}
 private void registerRt(){IntentFilter f=new IntentFilter("com.transiva.app.SPLIT_BILL_REALTIME");if(Build.VERSION.SDK_INT>=33)registerReceiver(realtime,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(realtime,f);startPoll();}
 private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 @Override protected void onDestroy(){stopPoll();if(secondTick!=null)h.removeCallbacks(secondTick);try{unregisterReceiver(realtime);}catch(Exception ignored){}super.onDestroy();}
}
