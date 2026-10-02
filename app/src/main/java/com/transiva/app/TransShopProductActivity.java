package com.transiva.app;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;
/** Standalone product detail. Ordering remains in the existing store/TransShop flow. */
public class TransShopProductActivity extends Activity {
 private int blue=Color.rgb(12,108,234); private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(24,53,87));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 private GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
 @Override public void onCreate(Bundle b){super.onCreate(b);JSONObject shop,item;try{shop=new JSONObject(getIntent().getStringExtra("store_json"));item=new JSONObject(getIntent().getStringExtra("product_json"));}catch(Exception e){finish();return;}getWindow().setStatusBarColor(blue);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(Color.rgb(246,249,255));setContentView(page);ScrollView sc=new ScrollView(this);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(16),dp(12),dp(16),dp(16));sc.addView(body);TextView back=text("‹  Detail Produk",22,true);body.addView(back);back.setOnClickListener(v->finish());ImageView image=new ImageView(this);TransShopImages.load(image,TransShopImages.product(item));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(260));ip.topMargin=dp(16);body.addView(image,ip);TextView name=text(item.optString("name","Produk"),23,true);name.setPadding(0,dp(14),0,0);body.addView(name);TextView price=text("Rp "+String.format(new Locale("id","ID"),"%,.0f",item.optDouble("price",0)),22,true);price.setTextColor(blue);price.setPadding(0,dp(7),0,dp(10));body.addView(price);TextView store=text("Toko: "+shop.optString("name"),14,true);body.addView(store);TextView desc=text(item.optString("description","Belum ada deskripsi produk."),14,false);desc.setPadding(0,dp(12),0,dp(12));body.addView(desc);Button choose=new Button(this);choose.setText("Pilih jumlah & pesan melalui TransShop");choose.setTextColor(Color.WHITE);choose.setAllCaps(false);choose.setBackground(bg(blue,13));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(54));cp.setMargins(dp(16),dp(8),dp(16),dp(16));page.addView(choose,cp);choose.setOnClickListener(v->{Intent intent=new Intent(this,TransShopStoreActivity.class);intent.putExtra("store_json",shop.toString());startActivity(intent);});}
}
