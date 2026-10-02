package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Detail toko dan pilihan barang. Checkout tetap memakai alur TransShop yang sudah ada. */
public class TransShopStoreActivity extends Activity {
 private JSONObject store; private JSONArray items; private final LinkedHashMap<Integer,Integer> quantities=new LinkedHashMap<>();
 private LinearLayout products; private TextView summary; private Button checkout; private final int blue=Color.rgb(12,108,234);
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(24,53,87));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 private void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-1,-2);l.topMargin=dp(top);p.addView(v,l);}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
 @Override public void onCreate(Bundle state){super.onCreate(state);try{store=new JSONObject(getIntent().getStringExtra("store_json"));}catch(Exception e){finish();return;}items=store.optJSONArray("items");if(items==null)items=new JSONArray();getWindow().setStatusBarColor(blue);getWindow().setNavigationBarColor(Color.rgb(5,16,29));LinearLayout page=col();page.setBackgroundColor(Color.rgb(246,249,255));setContentView(page);
 ScrollView sc=new ScrollView(this);LinearLayout root=col();pad(root,16);sc.addView(root);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView back=text("‹  Detail Toko",23,true);pad(back,5);add(root,back,0);back.setOnClickListener(v->finish());
 LinearLayout profile=col();pad(profile,17);profile.setBackground(bg(Color.WHITE,18));add(profile,text("🏬  "+store.optString("name","Toko TransShop"),22,true),0);add(profile,text("📍  "+store.optString("location","Alamat toko belum tersedia"),14,false),9);String desc=store.optString("description","");if(!desc.isEmpty())add(profile,text(desc,14,false),7);TextView verified=text("✓ Toko terdaftar di TransShop",13,true);verified.setTextColor(Color.rgb(25,132,94));add(profile,verified,10);add(root,profile,14);
 TextView heading=text("Produk toko",20,true);add(root,heading,19);products=col();add(root,products,4);for(int i=0;i<items.length();i++)renderItem(i);if(items.length()==0)add(products,text("Belum ada produk yang tersedia.",14,false),10);
 TextView wa=text("Hubungi toko via WhatsApp  ›",15,true);wa.setTextColor(blue);wa.setGravity(Gravity.CENTER);wa.setBackground(bg(Color.WHITE,14));pad(wa,15);add(root,wa,18);wa.setOnClickListener(v->whatsapp());
 LinearLayout bottom=col();pad(bottom,12);bottom.setBackground(bg(Color.WHITE,16));summary=text("Pilih produk untuk dipesan",13,false);add(bottom,summary,0);checkout=new Button(this);checkout.setTextColor(Color.WHITE);checkout.setBackground(bg(blue,13));add(bottom,checkout,6);page.addView(bottom);checkout.setOnClickListener(v->order());update();}
 private void renderItem(int index){JSONObject item=items.optJSONObject(index);if(item==null)return;LinearLayout card=col();pad(card,14);card.setBackground(bg(Color.WHITE,16));String name=item.optString("name","Produk");add(card,text(name,17,true),0);String desc=item.optString("description","");if(!desc.isEmpty())add(card,text(desc,13,false),5);add(card,text("Rp "+String.format(new Locale("id","ID"),"%,.0f",item.optDouble("price",0)),16,true),7);LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);Button minus=new Button(this);minus.setText("−");Button plus=new Button(this);plus.setText("+");TextView qty=text("0",16,true);qty.setGravity(Gravity.CENTER);controls.addView(minus,new LinearLayout.LayoutParams(dp(52),dp(46)));controls.addView(qty,new LinearLayout.LayoutParams(dp(46),dp(46)));controls.addView(plus,new LinearLayout.LayoutParams(dp(52),dp(46)));add(card,controls,7);minus.setOnClickListener(v->{int q=Math.max(0,quantities.getOrDefault(index,0)-1);quantities.put(index,q);qty.setText(String.valueOf(q));update();});plus.setOnClickListener(v->{int q=Math.min(99,quantities.getOrDefault(index,0)+1);quantities.put(index,q);qty.setText(String.valueOf(q));update();});add(products,card,10);}
 private void update(){int count=0;double total=0;for(Map.Entry<Integer,Integer> e:quantities.entrySet()){JSONObject item=items.optJSONObject(e.getKey());if(item!=null){count+=e.getValue();total+=item.optDouble("price",0)*e.getValue();}}summary.setText(count==0?"Pilih produk untuk dipesan":count+" barang • Subtotal produk Rp "+String.format(new Locale("id","ID"),"%,.0f",total)+" (belum termasuk biaya layanan/antar)");checkout.setEnabled(count>0);checkout.setAlpha(count>0?1f:.55f);checkout.setText("Lanjut ke pemesanan TransShop");}
 private void order(){StringBuilder list=new StringBuilder("Belanja di ").append(store.optString("name")).append("\nAlamat toko: ").append(store.optString("location"));double total=0;for(Map.Entry<Integer,Integer> e:quantities.entrySet()){int q=e.getValue();if(q<=0)continue;JSONObject item=items.optJSONObject(e.getKey());if(item==null)continue;list.append("\n").append(q).append("x ").append(item.optString("name")).append(" @ Rp ").append(String.format(new Locale("id","ID"),"%,.0f",item.optDouble("price",0)));total+=q*item.optDouble("price",0);}list.append("\nSubtotal referensi produk: Rp ").append(String.format(new Locale("id","ID"),"%,.0f",total));Intent intent=new Intent(this,TransShopLegacyActivity.class);intent.putExtra("transshop_catalog_list",list.toString());startActivity(intent);}
 private void whatsapp(){String p=store.optString("whatsapp","").replaceAll("[^0-9]","");if(p.startsWith("0"))p="62"+p.substring(1);if(!p.matches("[1-9][0-9]{7,14}")){Toast.makeText(this,"Nomor WhatsApp toko belum valid",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+p+"?text="+Uri.encode("Halo, saya melihat "+store.optString("name")+" di Transiva TransShop.")));try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Tidak dapat membuka WhatsApp",Toast.LENGTH_SHORT).show();}}
}
