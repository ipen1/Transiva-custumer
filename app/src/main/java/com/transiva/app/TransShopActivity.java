package com.transiva.app;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** TransShop landing: routes to existing catalog and map flows without modifying order logic. */
public class TransShopActivity extends Activity {
 private final int blue=Color.rgb(12,108,234);
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
 private TextView text(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 private void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);p.addView(v,lp);}
 private void open(Class<?> c){if(!DeliveryAddressGate.require(this,"TransShop"))return;startActivity(new Intent(this,c));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
 private void choice(LinearLayout root,String symbol,String title,String desc,String action,int accent,int fill,Class<?> target,int delay){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(19),dp(18),dp(19),dp(18));card.setBackground(bg(fill,20));card.setElevation(dp(2));card.setClickable(true);card.setFocusable(true);
  add(card,text(symbol,29,true,accent),0);add(card,text(title,21,true,Color.rgb(20,52,89)),9);
  TextView detail=text(desc,14,false,Color.rgb(78,99,121));detail.setLineSpacing(dp(3),1f);add(card,detail,6);
  add(card,text(action+"  →",14,true,accent),16);card.setContentDescription(title+". "+desc+". "+action);card.setOnClickListener(v->open(target));add(root,card,13);
  card.setAlpha(0f);card.setTranslationY(dp(14));card.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(260).start();
 }
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(blue);getWindow().setNavigationBarColor(Color.rgb(5,16,29));
  if (!DeliveryAddressGate.require(this,"TransShop")) {finish();return;}
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(246,249,255));
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(18),dp(18),dp(26));scroll.addView(root);setContentView(scroll);
  TextView back=text("‹  TransShop",27,true,Color.rgb(20,52,89));back.setGravity(Gravity.CENTER_VERTICAL);back.setMinHeight(dp(52));back.setOnClickListener(v->finish());add(root,back,0);
  TextView addr=text("📍 Antar ke: "+DeliveryAddressGate.address(this)+"   ›",13,true,blue);
  addr.setPadding(dp(12),dp(12),dp(12),dp(12));addr.setBackground(bg(Color.WHITE,12));
  addr.setOnClickListener(v->DeliveryAddressGate.edit(this,"TransShop"));add(root,addr,10);
  add(root,text("Belanja dengan caramu",17,true,blue),16);
  TextView intro=text("Temukan pilihan terbaik dari toko sekitar, atau titipkan belanjaan dari lokasi pilihanmu.",14,false,Color.rgb(83,103,124));intro.setLineSpacing(dp(3),1f);add(root,intro,6);
  choice(root,"▣","Toko Terdaftar","Jelajahi etalase merchant pilihan TransShop. Pilih produk, atur jumlah, lalu pesan dengan checkout praktis.","Jelajahi toko",blue,Color.WHITE,TransShopCatalogActivity.class,50);
  choice(root,"⌖","Belanja Bebas","Punya tempat belanja sendiri? Tentukan lokasinya lewat peta dan biarkan Transiva membantu perjalanan belanjamu.","Buka peta belanja",Color.rgb(15,125,139),Color.rgb(232,249,250),TransShopLegacyActivity.class,120);
  TextView note=text("Dua cara belanja, satu kemudahan bersama Transiva.",12,false,Color.rgb(112,130,149));note.setGravity(Gravity.CENTER);add(root,note,22);
 }
}
