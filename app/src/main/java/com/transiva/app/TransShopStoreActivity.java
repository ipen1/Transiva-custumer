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
 private LinearLayout products; private final Map<Integer,TextView> quantityLabels=new HashMap<>(); private static final int PRODUCT_REQUEST=202; private TextView summary; private Button checkout; private final int blue=Color.rgb(12,108,234); private final int green=Color.rgb(25,132,94);
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(24,53,87));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 private void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-1,-2);l.topMargin=dp(top);p.addView(v,l);}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private int limit(JSONObject item){if(item==null)return 0; int max=99; if(item.has("stock")&&!item.isNull("stock"))max=Math.min(max,Math.max(0,item.optInt("stock",0)));return max;}
 private String money(double value){return "Rp "+String.format(new Locale("id","ID"),"%,.0f",value);}
 private TextView action(String symbol,boolean primary){TextView v=text(symbol,22,true);v.setGravity(Gravity.CENTER);v.setTextColor(primary?Color.WHITE:blue);v.setBackground(bg(primary?blue:Color.rgb(235,243,255),10));v.setContentDescription(primary?"Tambah jumlah":"Kurangi jumlah");return v;}
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);for(Map.Entry<Integer,Integer> e:quantities.entrySet())out.putInt("cart_"+e.getKey(),e.getValue());}
 private void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
 @Override public void onCreate(Bundle state){super.onCreate(state);try{store=new JSONObject(getIntent().getStringExtra("store_json"));}catch(Exception e){finish();return;}items=store.optJSONArray("items");if(items==null)items=new JSONArray();if(state!=null)for(int i=0;i<items.length();i++){int q=state.getInt("cart_"+i,0);if(q>0)quantities.put(i,Math.min(limit(items.optJSONObject(i)),q));}getWindow().setStatusBarColor(blue);getWindow().setNavigationBarColor(Color.rgb(5,16,29));LinearLayout page=col();page.setBackgroundColor(Color.rgb(246,249,255));setContentView(page);
 ScrollView sc=new ScrollView(this);LinearLayout root=col();pad(root,16);sc.addView(root);sc.setClipToPadding(false);sc.setPadding(0,0,0,dp(20));page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView back=text("‹  Detail Toko",23,true);pad(back,5);add(root,back,0);back.setOnClickListener(v->finish());
 LinearLayout profile=col();pad(profile,17);profile.setBackground(bg(Color.WHITE,18));LinearLayout shopHead=new LinearLayout(this);shopHead.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);TransShopImages.load(logo,TransShopImages.shop(store));shopHead.addView(logo,new LinearLayout.LayoutParams(dp(76),dp(76)));TextView shopName=text(store.optString("name","Toko TransShop"),22,true);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(12);shopHead.addView(shopName,titleLp);add(profile,shopHead,0);add(profile,text("📍  "+store.optString("location","Alamat toko belum tersedia"),14,false),9);String desc=store.optString("description","");if(!desc.isEmpty())add(profile,text(desc,14,false),7);boolean isVerified=store.optBoolean("verified",false)||store.optInt("verified",0)==1||"verified".equalsIgnoreCase(store.optString("status"));TextView verified=text(isVerified?"✓ Merchant Terverifikasi TransShop":"Toko terdaftar di TransShop",13,true);verified.setTextColor(isVerified?green:Color.rgb(91,108,126));add(profile,verified,10);add(root,profile,14);
 TextView heading=text("Produk toko",20,true);add(root,heading,19);products=col();add(root,products,4);for(int i=0;i<items.length();i++)renderItem(i);if(getIntent().hasExtra("selected_index")){int selected=getIntent().getIntExtra("selected_index",-1);if(selected>=0&&selected<items.length())quantities.put(selected,Math.min(limit(items.optJSONObject(selected)),Math.max(1,getIntent().getIntExtra("selected_quantity",1))));}if(items.length()==0)add(products,text("Belum ada produk yang tersedia.",14,false),10);
 TextView wa=text("Hubungi toko via WhatsApp  ›",15,true);wa.setTextColor(blue);wa.setGravity(Gravity.CENTER);wa.setBackground(bg(Color.WHITE,14));pad(wa,15);add(root,wa,18);wa.setOnClickListener(v->whatsapp());
 LinearLayout bottom=col();pad(bottom,12);bottom.setBackground(bg(Color.WHITE,16));summary=text("Pilih produk untuk dipesan",13,false);add(bottom,summary,0);checkout=new Button(this);checkout.setTextColor(Color.WHITE);checkout.setBackground(bg(blue,13));add(bottom,checkout,6);page.addView(bottom);checkout.setOnClickListener(v->order());update();}
 private void renderItem(int index){
 JSONObject item=items.optJSONObject(index);if(item==null)return;
 LinearLayout card=col();pad(card,13);card.setBackground(bg(Color.WHITE,16));
 LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
 ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);TransShopImages.load(photo,TransShopImages.product(item));
 head.addView(photo,new LinearLayout.LayoutParams(dp(88),dp(88)));
 LinearLayout info=col();LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(0,-2,1);il.leftMargin=dp(12);head.addView(info,il);
 add(info,text(item.optString("name","Produk"),17,true),0);
 String desc=item.optString("description","");if(!desc.isEmpty()){TextView d=text(desc,13,false);d.setMaxLines(2);d.setEllipsize(android.text.TextUtils.TruncateAt.END);add(info,d,4);}
 add(info,text(money(item.optDouble("price",0)),16,true),6);
 if(item.has("stock")&&!item.isNull("stock")){TextView stock=text(limit(item)==0?"Stok habis":"Stok: "+item.optInt("stock"),12,true);stock.setTextColor(limit(item)==0?Color.rgb(180,55,55):green);add(info,stock,4);}
 add(card,head,0);
 View.OnClickListener detailClick=v->{Intent detail=new Intent(this,TransShopProductActivity.class);detail.putExtra("store_json",store.toString());detail.putExtra("product_json",item.toString());detail.putExtra("product_index",index);detail.putExtra("selected_quantity",quantities.getOrDefault(index,0));startActivityForResult(detail,PRODUCT_REQUEST);};
 head.setOnClickListener(detailClick);
 LinearLayout foot=new LinearLayout(this);foot.setGravity(Gravity.CENTER_VERTICAL);
 TextView see=text("Lihat detail produk ›",12,true);see.setTextColor(blue);see.setOnClickListener(detailClick);foot.addView(see,new LinearLayout.LayoutParams(0,dp(44),1));see.setGravity(Gravity.CENTER_VERTICAL);
 TextView minus=action("−",false),plus=action("+",true),qty=text("0",16,true);qty.setGravity(Gravity.CENTER);quantityLabels.put(index,qty);
 foot.addView(minus,new LinearLayout.LayoutParams(dp(40),dp(40)));foot.addView(qty,new LinearLayout.LayoutParams(dp(34),dp(40)));foot.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(40)));
 add(card,foot,8);
 minus.setOnClickListener(v->{int q=Math.max(0,quantities.getOrDefault(index,0)-1);quantities.put(index,q);update();});
 plus.setOnClickListener(v->{int q=Math.min(limit(item),quantities.getOrDefault(index,0)+1);quantities.put(index,q);update();});
 plus.setEnabled(limit(item)>0);plus.setAlpha(limit(item)>0?1f:.4f);
 add(products,card,10);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PRODUCT_REQUEST&&result==RESULT_OK&&data!=null){int index=data.getIntExtra("product_index",-1);if(index>=0&&index<items.length()){int q=Math.max(0,Math.min(limit(items.optJSONObject(index)),data.getIntExtra("selected_quantity",0)));quantities.put(index,q);TextView label=quantityLabels.get(index);if(label!=null)label.setText(String.valueOf(q));update();}}}
 private void update(){int count=0;double total=0;for(Map.Entry<Integer,Integer> e:quantities.entrySet()){JSONObject item=items.optJSONObject(e.getKey());if(item!=null){int q=Math.max(0,Math.min(limit(item),e.getValue()));count+=q;total+=item.optDouble("price",0)*q;TextView label=quantityLabels.get(e.getKey());if(label!=null)label.setText(String.valueOf(q));}}summary.setText(count==0?"Pilih produk untuk dipesan":count+" barang  •  "+money(total)+"\\nBelum termasuk biaya layanan/antar");checkout.setEnabled(count>0);checkout.setAlpha(count>0?1f:.55f);checkout.setAllCaps(false);checkout.setText(count>0?"Lanjut pemesanan  →":"Pilih produk dahulu");}
 private void order(){StringBuilder list=new StringBuilder("Belanja di ").append(store.optString("name")).append("\nAlamat toko: ").append(store.optString("location"));double total=0;for(Map.Entry<Integer,Integer> e:quantities.entrySet()){int q=Math.min(limit(items.optJSONObject(e.getKey())),e.getValue());if(q<=0)continue;JSONObject item=items.optJSONObject(e.getKey());if(item==null)continue;list.append("\n").append(q).append("x ").append(item.optString("name")).append(" @ Rp ").append(String.format(new Locale("id","ID"),"%,.0f",item.optDouble("price",0)));total+=q*item.optDouble("price",0);}list.append("\nSubtotal referensi produk: Rp ").append(String.format(new Locale("id","ID"),"%,.0f",total));Intent intent=new Intent(this,TransShopLegacyActivity.class);intent.putExtra("transshop_catalog_list",list.toString());intent.putExtra("transshop_store_json",store.toString());JSONArray cart=new JSONArray();for(Map.Entry<Integer,Integer> e:quantities.entrySet()){if(e.getValue()<=0)continue;JSONObject product=items.optJSONObject(e.getKey());if(product==null)continue;JSONObject line=new JSONObject();try{line.put("name",product.optString("name"));line.put("quantity",Math.min(limit(product),e.getValue()));line.put("price",product.optDouble("price",0));cart.put(line);}catch(JSONException ignored){}}intent.putExtra("transshop_cart_json",cart.toString());startActivity(intent);}
 private void whatsapp(){String p=store.optString("whatsapp","").replaceAll("[^0-9]","");if(p.startsWith("0"))p="62"+p.substring(1);if(!p.matches("[1-9][0-9]{7,14}")){Toast.makeText(this,"Nomor WhatsApp toko belum valid",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+p+"?text="+Uri.encode("Halo, saya melihat "+store.optString("name")+" di Transiva TransShop.")));try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Tidak dapat membuka WhatsApp",Toast.LENGTH_SHORT).show();}}
}
