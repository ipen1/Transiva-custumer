package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class CustomerReferralActivity extends Activity {
    private static final String URL="https://transiva.my.id/server/customer_referral.php";
    private LinearLayout root; private ProgressBar loading;
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.parseColor("#075ED1"));setContentView(base());load();}
    private View base(){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setBackgroundColor(Color.parseColor("#F4F7FC"));root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(36));s.addView(root);TextView h=tx("‹   Referral Customer",24,"#0B3A78",true);h.setOnClickListener(v->finish());root.addView(h);root.addView(tx("Ajak customer baru. Anda mendapat 10 poin setelah order pertama mereka selesai.",13,"#64748B",false));loading=new ProgressBar(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.gravity=Gravity.CENTER;lp.setMargins(0,dp(28),0,0);root.addView(loading,lp);return s;}
    private void load(){new Thread(()->{try{JSONObject o=req(null);runOnUiThread(()->render(o));}catch(Exception e){runOnUiThread(()->err("Gagal memuat referral."));}},"referral-load").start();}
    private void render(JSONObject o){loading.setVisibility(View.GONE);while(root.getChildCount()>3)root.removeViewAt(3);if(!o.optBoolean("success")){err(o.optString("message","Gagal memuat referral"));return;}
        LinearLayout hero=card("#0878F9",24);hero.addView(tx("KODE REFERRAL ANDA",11,"#DDF2FF",true));TextView code=tx(o.optString("referral_code","-"),30,"#FFFFFF",true);code.setTextIsSelectable(true);hero.addView(code);hero.addView(tx("Bagikan kode ini ke customer baru",12,"#EAF4FF",false));root.addView(hero,margin(16));
        LinearLayout stat=card("#FFFFFF",20);stat.addView(tx("Referral berhasil: "+o.optInt("rewarded_count",0),16,"#0B3A78",true));stat.addView(tx("Total customer memakai kode: "+o.optInt("invited_count",0),13,"#64748B",false));stat.addView(tx("Bonus: 10 poin / customer setelah order pertama selesai",12,"#12834B",true));root.addView(stat,margin(12));
        JSONObject used=o.optJSONObject("used_referral");if(used==null){LinearLayout use=card("#FFFFFF",20);use.addView(tx("Punya kode referral?",16,"#0B3A78",true));EditText e=new EditText(this);e.setHint("Contoh: TRV...");e.setSingleLine(true);use.addView(e);Button b=new Button(this);b.setText("Gunakan kode");b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(round("#0878F9",16));b.setOnClickListener(v->apply(e.getText().toString()));use.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));root.addView(use,margin(12));}else{LinearLayout usedCard=card("#ECFDF5",20);usedCard.addView(tx("Kode yang Anda gunakan: "+used.optString("referral_code","-"),14,"#065F46",true));usedCard.addView(tx("Status: "+used.optString("status","pending"),12,"#475569",false));root.addView(usedCard,margin(12));}
    }
    private void apply(String code){String x=code==null?"":code.trim();if(x.isEmpty()){err("Masukkan kode referral.");return;}loading.setVisibility(View.VISIBLE);new Thread(()->{try{JSONObject p=new JSONObject();p.put("referral_code",x);JSONObject o=req(p);runOnUiThread(()->{loading.setVisibility(View.GONE);new TransivaAlertDialogBuilder(this).setTitle(o.optBoolean("success")?"Referral Aktif":"Referral Gagal").setMessage(o.optString("message")).setPositiveButton("OK",(d,w)->{if(o.optBoolean("success")){loading.setVisibility(View.VISIBLE);load();}}).show();});}catch(Exception e){runOnUiThread(()->{loading.setVisibility(View.GONE);err("Koneksi gagal menyimpan referral.");});}},"referral-apply").start();}
    private JSONObject req(JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(URL).openConnection();CustomerApiClient.applySecurity(this,c);c.setConnectTimeout(15000);c.setReadTimeout(15000);if(body==null)c.setRequestMethod("GET");else{c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}}InputStream in=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream();StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return new JSONObject(b.toString());}
    private void err(String s){loading.setVisibility(View.GONE);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private LinearLayout card(String c,int r){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(18),dp(18),dp(18));x.setBackground(round(c,r));return x;}private GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(c));g.setCornerRadius(dp(r));return g;}private LinearLayout.LayoutParams margin(int t){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(t),0,0);return p;}private TextView tx(String s,int z,String c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(c));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
