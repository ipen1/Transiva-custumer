package com.transiva.app;
import android.app.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;
public class SmartReorderActivity extends CustomerAdaptiveActivity{LinearLayout root,list;ProgressBar p;TextView state;final SmartReorderRepository repo=new SmartReorderRepository();

 private static final int LOAD_BUDGET_MS = 6500;
 private final Handler main = new Handler(Looper.getMainLooper());
 private final CustomerLifecycleNetworkScope requests = new CustomerLifecycleNetworkScope();
 private boolean visible;
 private boolean loading;
 private boolean retryOnNetwork;
 private int attempt;
 private long retryAt;
 private Throwable lastError;
 private java.util.concurrent.Future<?> work;
 private Runnable deadline;
 private final TransivaNetworkMonitor.Listener connection = network -> main.post(() -> {
     if (visible && retryOnNetwork && network == TransivaNetworkMonitor.State.VALIDATED) load();
 });

 protected void onCreate(Bundle b){super.onCreate(b);CustomerWindowPolicy.status(getWindow(), Color.parseColor("#0B7CFF"));setContentView(ui());CustomerAppSettings.apply(this);load();}
 View ui(){ScrollView s=new ScrollView(this);s.setBackgroundColor(Color.parseColor("#F6F9FE"));root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(30));s.addView(root);TextView h=t("‹  Smart Reorder 2.0",24,"#0B3A78",true);h.setOnClickListener(v->finish());root.addView(h);root.addView(t("Pilihan dari pesanan Anda yang sering atau baru digunakan. Harga diperiksa kembali.",13,"#64748B",false));p=new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(36),dp(36));pp.gravity=Gravity.CENTER;pp.setMargins(0,dp(18),0,dp(6));root.addView(p,pp);state=t("Menyusun rekomendasi…",12,"#64748B",false);state.setGravity(Gravity.CENTER);root.addView(state);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);return s;}
 @Override protected void onResume() {
     super.onResume();
     visible = true;
     TransivaNetworkMonitor.addListener(connection);
 }

 @Override protected void onPause() {
     visible = false;
     TransivaNetworkMonitor.removeListener(connection);
     super.onPause();
 }

 @Override protected void onDestroy() {
     requests.destroy();
     main.removeCallbacksAndMessages(null);
     super.onDestroy();
 }

 void load() {
     if (loading || isFinishing() || isDestroyed()) return;
     long now = SystemClock.elapsedRealtime();
     if (now < retryAt) {
         showFailure(lastError, retryAt - now);
         return;
     }
     if (!TransivaNetworkMonitor.isOnline()) {
         retryOnNetwork = true;
         showFailure(new java.net.UnknownHostException("Offline"), 0L);
         return;
     }

     final int token = ++attempt;
     long endAt = now + LOAD_BUDGET_MS;
     loading = true;
     retryOnNetwork = false;
     p.setVisibility(View.VISIBLE);
     state.setOnClickListener(null);
     state.setText("Menyusun rekomendasi…");
     deadline = () -> {
         if (work != null) work.cancel(true);
         complete(token, null, new java.util.concurrent.TimeoutException());
     };
     main.postDelayed(deadline, LOAD_BUDGET_MS);
     work = requests.execute(() -> {
         try {
             int remaining = (int) (endAt - SystemClock.elapsedRealtime());
             if (remaining <= 0) throw new java.util.concurrent.TimeoutException();
             JSONObject orders = repo.load(getApplicationContext(), remaining);
             requests.post(main, () -> complete(token, orders, null));
         } catch (Exception error) {
             requests.post(main, () -> complete(token, null, error));
         }
     }, () -> requests.post(main, () -> complete(token, null,
             new java.util.concurrent.RejectedExecutionException())));
 }

 private void complete(int token, JSONObject result, Throwable error) {
     if (!loading || token != attempt || isDestroyed() || isFinishing()) return;
     loading = false;
     main.removeCallbacks(deadline);
     work = null;
     p.setVisibility(View.GONE);
     if (error == null) {
         retryAt = 0L;
         lastError = null;
         state.setOnClickListener(null);
         render(result);
         return;
     }
     lastError = error;
     long delay = CustomerReadError.retryDelay(error);
     retryAt = SystemClock.elapsedRealtime() + delay;
     retryOnNetwork = CustomerReadError.canRetryOnReconnect(error) && !TransivaNetworkMonitor.isValidated();
     showFailure(error, delay);
 }

 private void showFailure(Throwable error, long delay) {
     p.setVisibility(View.GONE);
     state.setText(CustomerReadError.message(error, delay, "Rekomendasi belum dapat dimuat.")
             + " Ketuk untuk coba lagi.");
     state.setOnClickListener(v -> load());
 }
 void render(JSONObject o){list.removeAllViews();JSONArray a=o.optJSONArray("orders");if(!o.optBoolean("success")||a==null||a.length()==0){state.setText("Belum ada pola pesanan yang cukup untuk rekomendasi.");return;}state.setText(a.length()+" rekomendasi paling relevan");for(int i=0;i<a.length();i++){JSONObject q=a.optJSONObject(i);if(q==null)continue;String type=q.optString("order_type","ride").toLowerCase();String title=type.contains("food")?q.optString("restaurant_name","TransFood favorit"):(type.contains("car")?"TransCar favorit":"TransRide favorit");LinearLayout c=box();c.addView(t(title,17,"#0B3A78",true));c.addView(t(q.optString("summary","Pesanan sebelumnya"),13,"#64748B",false));c.addView(t("Sering dipesan "+q.optInt("frequency",1)+"×",12,"#0B7CFF",true));Button b=new Button(this);b.setText("Pesan Lagi");b.setAllCaps(false);b.setOnClickListener(v->{Intent in=new Intent(this,type.contains("food")?RepeatFoodOrderActivity.class:RepeatRideOrderActivity.class);in.putExtra("repeat_order_json",q.toString());startActivity(in);});c.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));list.addView(c,lp());}}
 LinearLayout box(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(16),dp(16),dp(16),dp(16));x.setBackground(round("#FFFFFF",20));x.setElevation(dp(2));return x;}LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(14),0,0);return p;}TextView t(String s,int z,String c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(c));if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(c));g.setCornerRadius(dp(r));return g;}int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}}
