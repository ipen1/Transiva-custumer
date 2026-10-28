package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerReferralActivity extends CustomerAdaptiveActivity {
    private static final String URL=ApiConfig.ROOT+"server/customer_referral.php";
    private static final String SLOGAN="Undang teman ke Transiva, dapatkan saldo promo Rp1.000 setelah temanmu top up minimal Rp10.000 dan menyelesaikan pesanan pertamanya.";
    private final CustomerFeatureRuntimeController runtime=new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.IDLE);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,content;private ProgressBar loading;private boolean applying;private int generation;
    @Override protected void onCreate(Bundle state){super.onCreate(state);CustomerWindowPolicy.status(getWindow(),Color.parseColor("#075ED1"));setContentView(base());CustomerAppSettings.apply(this);}
    @Override protected void onResume(){super.onResume();runtime.onResume();CustomerAppSettings.apply(this);load();}
    @Override protected void onPause(){++generation;runtime.cancel("referral-read");runtime.onPause();super.onPause();}
    @Override protected void onDestroy(){runtime.destroy();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private View base(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.parseColor("#F4F7FC"));root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(36));scroll.addView(root);
        TextView title=tx("‹   Referral Customer",24,"#0B3A78",true);title.setOnClickListener(v->finish());root.addView(title);root.addView(tx(SLOGAN,14,"#64748B",false));
        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(loading,new LinearLayout.LayoutParams(-1,dp(4)));content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);return scroll;
    }
    private void load(){
        final int request=++generation;loading.setVisibility(View.VISIBLE);
        runtime.executeLatest("referral-read",CustomerLoadingCoordinator.CONTENT,()->{
            try{JSONObject response=TransivaHttpRepository.getJsonFresh(this,URL,15000);runtime.post(handler,()->{if(request==generation)render(response);});}
            catch(Exception e){runtime.post(handler,()->{if(request==generation)error("Gagal memuat referral. Coba lagi.");});}
        },()->runtime.post(handler,()->{if(request==generation)error("Antrean sedang penuh. Coba lagi.");}));
    }
    private void render(JSONObject response){
        loading.setVisibility(View.GONE);if(!response.optBoolean("success")){error(response.optString("message","Referral belum tersedia."));return;}content.removeAllViews();
        String code=response.optString("referral_code","-");
        LinearLayout hero=card("#0878F9",24);hero.addView(tx("KODE REFERRAL ANDA",11,"#DDF2FF",true));TextView label=tx(code,30,"#FFFFFF",true);label.setTextIsSelectable(true);hero.addView(label);hero.addView(tx("Hadiah masuk ke saldo Transiva Pay pengundang.",12,"#EAF4FF",false));
        Button share=button("Bagikan & Ajak Teman");share.setTextColor(Color.parseColor("#075ED1"));share.setBackground(round("#FFFFFF",16));share.setOnClickListener(v->{Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,SLOGAN+"\n\nKode referral saya: "+code+"\nMasukkan kode sebelum top up atau pesanan pertama.\nhttps://transiva.my.id/");startActivity(Intent.createChooser(intent,"Bagikan kode referral"));});hero.addView(share,margin(12));content.addView(hero,margin(16));
        LinearLayout stats=card("#FFFFFF",20);stats.addView(tx("Saldo promo diperoleh: "+money(response.optLong("earned_promo_amount")),18,"#0B3A78",true));stats.addView(tx(response.optInt("rewarded_count")+" referral berhasil • "+response.optInt("invited_count")+" teman memakai kode",13,"#64748B",false));content.addView(stats,margin(12));
        LinearLayout rules=card("#FFFFFF",20);rules.addView(tx("Cara mendapat saldo promo",16,"#0B3A78",true));rules.addView(tx("1. Teman baru memasukkan kode sebelum top up/order pertama.\n2. Satu top up minimal Rp10.000 berhasil dikonfirmasi dan masuk ke saldo teman.\n3. Pesanan pertama teman selesai terkonfirmasi.\n4. Pengundang mendapat saldo promo Rp1.000 satu kali per teman.",13,"#475569",false));rules.addView(tx("Transfer masuk, bonus, refund, dan bukti deposit yang masih menunggu verifikasi tidak dihitung sebagai top up.",12,"#64748B",false));content.addView(rules,margin(12));
        JSONObject used=response.optJSONObject("used_referral");
        if(used!=null){LinearLayout status=card("#ECFDF5",20);status.addView(tx("Kode yang digunakan: "+used.optString("referral_code"),15,"#065F46",true));status.addView(tx((used.optBoolean("topup_qualified")?"✓":"○")+" Top up minimal Rp10.000 terkonfirmasi\n"+(used.optBoolean("first_order_confirmed")?"✓":"○")+" Pesanan pertama selesai terkonfirmasi\n"+("rewarded".equals(used.optString("status"))?"✓ Hadiah Rp1.000 sudah masuk ke pengundang":"○ Hadiah menunggu syarat lengkap"),13,"#475569",false));content.addView(status,margin(12));}
        else if(response.optBoolean("can_apply_referral",false)){
            LinearLayout form=card("#FFFFFF",20);form.addView(tx("Punya kode referral?",16,"#0B3A78",true));EditText input=new EditText(this);input.setHint("Contoh: TRV...");input.setSingleLine(true);form.addView(input);Button apply=button("Gunakan kode");apply.setEnabled(!applying);apply.setOnClickListener(v->apply(input.getText().toString(),apply));form.addView(apply,margin(8));content.addView(form,margin(12));
        }else content.addView(tx("Pemasangan kode hanya tersedia untuk customer baru sebelum top up atau order pertama.",12,"#64748B",false),margin(12));
        Button refresh=button("Perbarui status");refresh.setOnClickListener(v->load());content.addView(refresh,margin(16));CustomerAppSettings.apply(this);
    }
    private void apply(String raw,Button button){
        if(applying)return;String code=raw==null?"":raw.trim();if(code.isEmpty()){error("Masukkan kode referral.");return;}
        applying=true;button.setEnabled(false);loading.setVisibility(View.VISIBLE);
        Runnable failed=()->runtime.post(handler,()->{applying=false;button.setEnabled(true);error("Belum dapat memasang kode. Perbarui status sebelum mencoba lagi.");});
        runtime.execute(()->{try{JSONObject body=new JSONObject();body.put("referral_code",code);JSONObject response=TransivaHttpRepository.postJson(this,URL,body,15000);runtime.post(handler,()->{applying=false;button.setEnabled(true);loading.setVisibility(View.GONE);Toast.makeText(this,response.optString("message"),Toast.LENGTH_LONG).show();load();});}catch(Exception e){failed.run();}},failed);
    }
    private void error(String message){loading.setVisibility(View.GONE);Toast.makeText(this,message,Toast.LENGTH_LONG).show();if(content.getChildCount()==0){Button retry=button("Coba lagi");retry.setOnClickListener(v->{content.removeView(retry);load();});content.addView(retry,margin(16));}}
    private String money(long amount){return "Rp"+NumberFormat.getIntegerInstance(new Locale("id","ID")).format(amount);}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setMinHeight(dp(48));b.setBackground(round("#0878F9",16));return b;}
    private LinearLayout card(String color,int radius){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(18),dp(18),dp(18));box.setBackground(round(color,radius));return box;}
    private GradientDrawable round(String color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,0);return p;}
    private TextView tx(String value,int size,String color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
