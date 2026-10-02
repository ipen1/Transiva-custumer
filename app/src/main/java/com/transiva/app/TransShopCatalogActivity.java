package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class TransShopCatalogActivity extends Activity {
 private static final String ENDPOINT="https://transiva.my.id/server/transshop_catalog.php";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayList<JSONObject> stores=new ArrayList<>();
 private LinearLayout results; private EditText search; private TextView status; private boolean loading=false; private boolean closed=false;
 private int blue=Color.rgb(12,108,234);
 private int d(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));return g;}
 private TextView text(String t,int sp,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(Color.rgb(20,52,89));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void pad(View v,int p){v.setPadding(d(p),d(p),d(p),d(p));}
 private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=d(top);parent.addView(child,lp);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(blue);getWindow().setNavigationBarColor(Color.rgb(5,16,29));
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(246,249,255));LinearLayout root=column();pad(root,16);scroll.addView(root);setContentView(scroll);
 LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",34,true);back.setGravity(Gravity.CENTER);head.addView(back,new LinearLayout.LayoutParams(d(44),d(48)));back.setOnClickListener(v->finish());TextView title=text("TransShop",25,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView cart=text("Toko",14,true);cart.setTextColor(blue);head.addView(cart);add(root,head,0);
 TextView banner=text("Belanja mudah dari toko sekitar\nPilih produk favoritmu.",16,true);banner.setTextColor(Color.WHITE);banner.setBackground(bg(blue,20));pad(banner,14);add(root,banner,12);
 search=new EditText(this);search.setSingleLine(true);search.setTextSize(15);search.setHint("Cari toko atau produk...");search.setBackground(bg(Color.WHITE,15));pad(search,14);add(root,search,14);
 TextView section=text("Pilihan toko",20,true);add(root,section,18);status=text("Memuat toko...",14,false);add(root,status,8);results=column();add(root,results,6);
 TextView manual=text("Butuh belanja dari lokasi lain? Buka TransShop Maps  ›",14,true);manual.setTextColor(blue);manual.setGravity(Gravity.CENTER);manual.setBackground(bg(Color.WHITE,15));pad(manual,16);add(root,manual,20);manual.setOnClickListener(v->startActivity(new Intent(this,TransShopLegacyActivity.class)));
 search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(Editable e){}});load(); }
 private void load(){if(loading)return;loading=true;status.setText("Memuat toko...");new Thread(()->{ArrayList<JSONObject> fresh=new ArrayList<>();String error=null;HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(ENDPOINT).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);JSONObject data=new JSONObject(out.toString("UTF-8"));if(!data.optBoolean("success"))throw new IOException("Daftar toko belum tersedia");JSONArray a=data.optJSONArray("stores");if(a!=null)for(int i=0;i<a.length();i++)fresh.add(a.getJSONObject(i));}}catch(Exception e){error="Tidak dapat memuat toko. Periksa koneksi dan coba lagi.";}finally{if(c!=null)c.disconnect();}final String err=error;handler.post(()->{if(closed)return;loading=false;if(err==null){stores.clear();stores.addAll(fresh);}status.setText(err==null?"":err);render();if(err!=null){status.setOnClickListener(v->load());status.setText(err+"  •  Ketuk untuk coba lagi");}});}).start();}
 private void render(){if(results==null)return;results.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;for(JSONObject shop:stores){String name=shop.optString("name"),loc=shop.optString("location"),desc=shop.optString("description");JSONArray items=shop.optJSONArray("items");StringBuilder terms=new StringBuilder(name+" "+loc+" "+desc);if(items!=null)for(int i=0;i<items.length();i++){JSONObject it=items.optJSONObject(i);if(it!=null)terms.append(' ').append(it.optString("name"));}if(!terms.toString().toLowerCase(Locale.ROOT).contains(q))continue;shown++;LinearLayout card=column();pad(card,12);card.setBackground(bg(Color.WHITE,16));LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);TransShopImages.load(logo,TransShopImages.shop(shop));head.addView(logo,new LinearLayout.LayoutParams(d(58),d(58)));LinearLayout info=column();LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-2,1);ilp.leftMargin=d(10);head.addView(info,ilp);add(info,text(name,17,true),0);TextView address=text("📍 "+loc,12,false);address.setMaxLines(1);address.setEllipsize(android.text.TextUtils.TruncateAt.END);add(info,address,3);TextView count=text((items==null?0:items.length())+" produk • Lihat toko ›",12,true);count.setTextColor(blue);add(info,count,4);add(card,head,0);card.setOnClickListener(v->openStore(shop));if(items!=null&&items.length()>0){TextView label=text("Produk pilihan",13,true);add(card,label,9);android.widget.HorizontalScrollView horizontal=new android.widget.HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);LinearLayout strip=new LinearLayout(this);horizontal.addView(strip);for(int i=0;i<Math.min(items.length(),8);i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;LinearLayout tile=column();pad(tile,7);tile.setBackground(bg(Color.rgb(246,249,255),11));LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(d(116),-2);tlp.rightMargin=d(8);strip.addView(tile,tlp);ImageView photo=new ImageView(this);TransShopImages.load(photo,TransShopImages.product(item));tile.addView(photo,new LinearLayout.LayoutParams(-1,d(88)));TextView itemName=text(item.optString("name","Produk"),12,true);itemName.setMaxLines(1);itemName.setEllipsize(android.text.TextUtils.TruncateAt.END);add(tile,itemName,5);TextView price=text("Rp "+String.format(new Locale("id","ID"),"%,.0f",item.optDouble("price",0)),12,true);price.setTextColor(blue);add(tile,price,3);tile.setOnClickListener(v->openProduct(shop,item));}add(card,horizontal,7);}add(results,card,9);}if(shown==0&&status.getText().length()==0)status.setText(stores.isEmpty()?"Belum ada toko aktif. Silakan cek kembali nanti.":"Tidak ada toko atau produk yang cocok.");else if(shown>0)status.setText("");}
 private void openStore(JSONObject shop){Intent intent=new Intent(this,TransShopStoreActivity.class);intent.putExtra("store_json",shop.toString());startActivity(intent);}
 private void openProduct(JSONObject shop,JSONObject item){Intent intent=new Intent(this,TransShopProductActivity.class);intent.putExtra("store_json",shop.toString());intent.putExtra("product_json",item.toString());startActivity(intent);}
 private void detail(JSONObject shop){ScrollView sc=new ScrollView(this);LinearLayout body=column();pad(body,16);sc.addView(body);add(body,text(shop.optString("name"),22,true),0);add(body,text(shop.optString("location"),14,false),8);add(body,text(shop.optString("description"),14,false),6);JSONArray a=shop.optJSONArray("items");if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);if(item==null)continue;LinearLayout row=column();pad(row,12);row.setBackground(bg(Color.rgb(244,248,255),12));add(row,text(item.optString("name"),16,true),0);add(row,text(item.optString("description"),13,false),3);add(row,text("Rp "+String.format(new Locale("id","ID"),"%,.0f",item.optDouble("price",0)),15,true),5);add(body,row,9);}Button wa=new Button(this);wa.setText("Hubungi toko via WhatsApp");wa.setTextColor(Color.WHITE);wa.setBackground(bg(blue,13));add(body,wa,15);wa.setOnClickListener(v->{String phone=shop.optString("whatsapp").replaceAll("[^0-9]","");if(phone.startsWith("0"))phone="62"+phone.substring(1);if(!phone.matches("[1-9][0-9]{7,14}")){Toast.makeText(this,"Nomor WhatsApp toko belum valid",Toast.LENGTH_SHORT).show();return;}String msg="Halo, saya melihat toko "+shop.optString("name")+" di Transiva TransShop. Saya ingin bertanya tentang produk.";Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+phone+"?text="+Uri.encode(msg)));try{startActivity(intent);}catch(Exception e){Toast.makeText(this,"Tidak dapat membuka WhatsApp",Toast.LENGTH_SHORT).show();}});new AlertDialog.Builder(this).setView(sc).setNegativeButton("Tutup",null).show();}
 @Override protected void onDestroy(){closed=true;super.onDestroy();}
}
