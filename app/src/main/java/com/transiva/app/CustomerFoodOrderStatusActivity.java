package com.transiva.app;

import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** Read-only food outcome screen. A merchant refusal never initializes a map or driver tracking. */
public class CustomerFoodOrderStatusActivity extends CustomerAdaptiveActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final CustomerFeatureRuntimeController runtime=new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.FOOD);
    private LinearLayout root;
    private TextView countdown;
    private String orderId="",signature="";
    private boolean foreground,terminal,inFlight,routed,rendered;
    private final java.util.concurrent.atomic.AtomicBoolean heartbeatInFlight=new java.util.concurrent.atomic.AtomicBoolean();
    private long generation,remaining=8000,lastTick;
    private final Runnable poll=()->fetch();
    private final Runnable tick=new Runnable(){public void run(){
        if(!foreground||!terminal||routed)return;
        long now=SystemClock.elapsedRealtime();remaining=Math.max(0,remaining-Math.max(0,now-lastTick));lastTick=now;
        if(countdown!=null)countdown.setText("Kembali ke beranda dalam "+Math.max(1,(remaining+999)/1000)+" detik");
        if(remaining<=0){home();return;}handler.postDelayed(this,200);
    }};
    @Override protected void onCreate(Bundle state){super.onCreate(state);if(state!=null)remaining=Math.max(1000,Math.min(8000,state.getLong("remaining",8000)));readIntent();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);generation++;handler.removeCallbacksAndMessages(null);inFlight=false;routed=false;rendered=false;terminal=false;signature="";remaining=8000;readIntent();if(foreground){fetch();startTick();}}
    private void readIntent(){
        orderId=FoodOrderDecision.first(getIntent().getStringExtra("order_id"));
        CustomerWindowPolicy.status(getWindow(),CustomerAppSettings.isDarkMode(this)?0xFF071426:0xFFF7FAFF);
        CustomerWindowPolicy.navigation(getWindow(),CustomerAppSettings.isDarkMode(this)?0xFF071426:0xFFF7FAFF);
        if(orderId.isEmpty()){shell();message("Pesanan belum ditemukan","Buka Aktivitas untuk memeriksa pesananmu.");return;}
        if("merchant_rejected".equals(getIntent().getStringExtra("status"))){
            try{JSONObject o=new JSONObject().put("order_id",orderId).put("order_type","food").put("status","merchant_rejected").put("restaurant_name",FoodOrderDecision.first(getIntent().getStringExtra("restaurant_name"),"Toko" )).put("merchant_reject_reason",FoodOrderDecision.first(getIntent().getStringExtra("merchant_reject_reason")));showOutcome(new JSONObject().put("order",o),false);}catch(Exception ignored){loading();}
        }else loading();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int ink(){return CustomerAppSettings.isDarkMode(this)?0xFFEEF5FF:0xFF133152;}
    private int muted(){return CustomerAppSettings.isDarkMode(this)?0xFFA9B8CC:0xFF60758E;}
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(bold?ink():muted());v.setGravity(Gravity.CENTER);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void shell(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(20),dp(24),dp(24));
        root.setBackgroundColor(CustomerAppSettings.isDarkMode(this)?0xFF071426:0xFFF7FAFF);scroll.addView(root,new ScrollView.LayoutParams(-1,-1));setContentView(scroll);
        TextView brand=text("TRANSFOOD",12,true);brand.setTextColor(0xFF268CFF);brand.setLetterSpacing(.12f);root.addView(brand);
    }
    private void loading(){shell();root.addView(new ProgressBar(this),new LinearLayout.LayoutParams(dp(36),dp(36)));root.addView(text("Memeriksa kabar pesananmu…",16,true));button("Ke beranda",this::home,false);}
    private void message(String title,String body){root.addView(text(title,23,true));TextView detail=text(body,14,false);detail.setPadding(0,dp(12),0,dp(16));root.addView(detail);button("Coba lagi",()->fetch(),true);button("Ke beranda",this::home,false);}
    private void button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setTextColor(primary?Color.WHITE:ink());GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(16));d.setColor(primary?0xFF0B7CFF:CustomerAppSettings.isDarkMode(this)?0xFF162B43:0xFFE8F2FE);b.setBackground(d);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);root.addView(b,lp);}
    private void fetch(){
        handler.removeCallbacks(poll);if(inFlight||routed||orderId.isEmpty()||!foreground)return;inFlight=true;final long request=++generation;final String requestedOrder=orderId;
        java.util.concurrent.Future<?> future=runtime.execute(()->{try{
            JSONObject response=TransivaHttpRepository.postJson(this,ApiConfig.ROOT+"server/customer_food_order_status.php",new JSONObject().put("order_id",requestedOrder),20000);
            runtime.post(handler,()->{if(request!=generation||routed)return;inFlight=false;if(!response.optBoolean("success")){terminal=false;handler.removeCallbacks(tick);error();return;}if(!foreground)return;if(FoodOrderDecision.terminal(response)){showOutcome(response,true);return;}
                terminal=false;handler.removeCallbacks(tick);remaining=8000;
                if(CustomerOrderState.hasDriver(FoodOrderDecision.status(response))||CustomerOrderState.isFinished(FoodOrderDecision.status(response))){routed=true;Intent trip=new Intent(this,CustomerOrderState.isFinished(FoodOrderDecision.status(response))?CustomerHistoryActivity.class:CustomerTripActivity.class).putExtra("order_id",orderId).putExtra("source","orders").putExtra("food_status_resolved",true);startActivity(trip);finish();return;}
                showWaiting(response);if(foreground)handler.postDelayed(poll,4000);
            });
            // Preserve the existing redispatch heartbeat only for a still-active, unassigned food order.
            if(response.optBoolean("success")&&!FoodOrderDecision.terminal(response)&&!CustomerOrderState.hasDriver(FoodOrderDecision.status(response))&&!CustomerOrderState.isFinished(FoodOrderDecision.status(response))&&heartbeatInFlight.compareAndSet(false,true)){
                try{TransivaHttpRepository.postJson(this,ApiConfig.ROOT+"server/check_order_status.php",new JSONObject().put("order_id",requestedOrder),12000);}catch(Exception ignored){}finally{heartbeatInFlight.set(false);}
            }

        }catch(Exception e){runtime.post(handler,()->{if(request==generation&&!routed){inFlight=false;error();}});}},()->runtime.post(handler,()->{if(request==generation&&!routed){inFlight=false;error();}}));
        if(future==null){inFlight=false;error();}
    }
    private void error(){if(terminal){if(foreground)startTick();return;}signature="";shell();message("Kabar pesanan belum bisa dimuat","Koneksi belum berhasil. Coba lagi atau periksa pesanan melalui Aktivitas.");}
    private void showOutcome(JSONObject res,boolean verified){
        JSONObject order=FoodOrderDecision.order(res);terminal=true;handler.removeCallbacks(poll);
        if(verified)clearMatchingOrder();
        String next=res.toString()+verified;if(next.equals(signature)){startTick();return;}signature=next;
        shell();String category=FoodOrderDecision.rejected(res)?FoodOrderDecision.category(FoodOrderDecision.reason(res)):"unavailable";
        FoodOrderDecisionView art=new FoodOrderDecisionView(this,category);root.addView(art,new LinearLayout.LayoutParams(-1,dp(190)));
        TextView title=text(FoodOrderDecision.title(res),26,true);root.addView(title);
        String merchant=FoodOrderDecision.first(FoodOrderDecision.value(order,"restaurant_name"),getIntent().getStringExtra("restaurant_name"),"Toko");
        TextView body=text(FoodOrderDecision.rejected(res)?merchant+" belum bisa menerima pesananmu.":"Pesanan ini sudah dibatalkan dan tidak dilanjutkan.",15,false);body.setPadding(0,dp(10),0,dp(12));root.addView(body);
        String reason=FoodOrderDecision.reason(res);
        if(!reason.isEmpty()){
            LinearLayout reasonBox=new LinearLayout(this);reasonBox.setOrientation(LinearLayout.VERTICAL);reasonBox.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(CustomerAppSettings.isDarkMode(this)?0xFF172B43:0xFFE8F2FE);bg.setCornerRadius(dp(16));reasonBox.setBackground(bg);reasonBox.addView(text("Alasan dari toko",11,false));TextView exact=text(reason,15,true);exact.setPadding(0,dp(5),0,0);reasonBox.addView(exact);root.addView(reasonBox,new LinearLayout.LayoutParams(-1,-2));
        }
        TextView help=text("Pesanan ini tidak dilanjutkan ke pengantaran. Kamu bisa memilih toko lain atau melihat rinciannya di Aktivitas.",13,false);help.setPadding(0,dp(12),0,dp(6));root.addView(help);
        String payment=verified?FoodOrderDecision.paymentMessage(res):"";if(!payment.isEmpty()){TextView pay=text(payment,12,false);pay.setPadding(0,dp(6),0,dp(8));root.addView(pay);}
        TextView reference=text(orderId,10,false);reference.setPadding(0,dp(8),0,dp(4));root.addView(reference);
        button("Kembali ke beranda",this::home,true);countdown=text("",11,false);countdown.setPadding(0,dp(12),0,0);root.addView(countdown);
        if(!rendered){root.setAlpha(0);root.setTranslationY(dp(12));root.animate().alpha(1).translationY(0).setDuration(250).start();rendered=true;}startTick();
    }
    private void showWaiting(JSONObject res){
        String merchantStatus=FoodOrderDecision.value(FoodOrderDecision.order(res),"merchant_status");boolean preparing="preparing".equals(merchantStatus)||"ready".equals(merchantStatus)||"merchant_accepted".equals(FoodOrderDecision.status(res));
        String next="waiting:"+preparing+":"+merchantStatus;if(next.equals(signature))return;signature=next;shell();root.addView(new FoodOrderDecisionView(this,"waiting"),new LinearLayout.LayoutParams(-1,dp(190)));
        root.addView(text(preparing?"Pesananmu sedang diproses":"Menunggu konfirmasi toko",24,true));TextView copy=text(preparing?"Toko sudah menerima pesananmu. Informasi driver akan muncul setelah ada driver yang menerima pesanan.":"Toko sedang memeriksa pesananmu. Kabar terbaru akan muncul di sini dan melalui notifikasi.",15,false);copy.setPadding(0,dp(12),0,dp(16));root.addView(copy);button("Ke beranda",this::home,true);
    }
    private void clearMatchingOrder(){
        SharedPreferences prefs=getSharedPreferences("transiva",MODE_PRIVATE);if(!orderId.equals(prefs.getString("active_order_id","")))return;
        prefs.edit().remove("active_order_id").remove("active_order_status").remove("active_order_type").remove("active_service_name").remove("active_order_price").remove("active_order_source").remove("pickup_lat").remove("pickup_lng").remove("delivery_lat").remove("delivery_lng").remove("active_driver_type").apply();
        CustomerRedispatchService.stop(this);try{stopService(new Intent(this,TripGuardianService.class));stopService(new Intent(this,AudioProtectService.class));}catch(Exception ignored){}
    }
    private void startTick(){handler.removeCallbacks(tick);if(foreground&&terminal){lastTick=SystemClock.elapsedRealtime();handler.post(tick);}}
    private void home(){if(routed||!foreground)return;routed=true;handler.removeCallbacksAndMessages(null);Intent home=new Intent(this,CustomerDashboardActivity.class);home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(home);finish();}
    @Override public void onBackPressed(){home();}
    @Override protected void onResume(){super.onResume();foreground=true;runtime.onResume();fetch();startTick();}
    @Override protected void onPause(){foreground=false;handler.removeCallbacks(tick);handler.removeCallbacks(poll);runtime.onPause();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putLong("remaining",remaining);}
    @Override protected void onDestroy(){generation++;runtime.destroy();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
