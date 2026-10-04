package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.app.ProgressDialog;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** Service-only address gate. A missing coordinate is not a missing saved address. */
public final class DeliveryAddressGate {
 private DeliveryAddressGate(){}
 private static final String PREF="delivery_address_gate_v2";
 private static final java.util.Set<String> pending=java.util.Collections.synchronizedSet(new java.util.HashSet<>());
 private static String user(Activity a) {
  SessionManager s=new SessionManager(a);
  String id=s.getId(); if(id==null||id.trim().isEmpty()) id=s.getUserId();
  return id==null?"":id.trim();
 }
 public static void update(Activity a,String address,double lat,double lng) {
  String id=user(a); if(id.isEmpty())return;
  a.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
    .putString("user",id).putString("address",address==null?"":address.trim())
    .putLong("lat",Double.doubleToRawLongBits(lat)).putLong("lng",Double.doubleToRawLongBits(lng)).apply();
 }
 public static String address(Activity a) {
  String id=user(a); if(id.isEmpty())return "";
  android.content.SharedPreferences p=a.getSharedPreferences(PREF,Context.MODE_PRIVATE);
  if(id.equals(p.getString("user",""))) {
   String cached=p.getString("address","").trim();if(!cached.isEmpty())return cached;
  }
  SessionManager session=new SessionManager(a);
  String local=id.equals(session.get("delivery_owner_id")) ? session.get("delivery_address") : "";
  return local==null?"":local.trim();
 }
 public static boolean valid(Activity a) {return !address(a).isEmpty();}
 private static void profile(Activity a,String service) {
  Intent i=new Intent(a,ProfileActivity.class);
  i.putExtra("edit_delivery_address",true);i.putExtra("delivery_return_service",service);
  a.startActivity(i);
 }
 public static void prefetch(Activity a) {
  if(valid(a))return;
  final String id=user(a);
  if(id.isEmpty() || !pending.add(id+":prefetch"))return;
  final Context context=a.getApplicationContext();
  new Thread(()->{
   HttpURLConnection c=null;
   try {
    String endpoint="https://transiva.my.id/server/get_customer_profile.php?id="+
      java.net.URLEncoder.encode(id,"UTF-8");
    c=CustomerApiClient.open(context,endpoint);
    c.setConnectTimeout(3500);c.setReadTimeout(4000);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){
      byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)b.write(buffer,0,n);
      JSONObject response=new JSONObject(b.toString("UTF-8"));
      JSONObject u=response.optJSONObject("user");
      if(response.optBoolean("success",false) && u!=null && id.equals(user(a))){
       String addr=u.optString("delivery_address","").trim();
       if(!addr.isEmpty())update(a,addr,u.optDouble("delivery_lat",0),u.optDouble("delivery_lng",0));
      }
    }
   }catch(Exception ignored){}finally{
    if(c!=null)c.disconnect();
    pending.remove(id+":prefetch");
   }
  }).start();
 }
 public static boolean require(Activity a,String service) {
  if(valid(a))return true;
  String id=user(a);
  if(id.isEmpty()){explainMissing(a,service);return false;}
  String key=id+":"+service;
  if(!pending.add(key))return false;
  final ProgressDialog progress=new ProgressDialog(a);
  progress.setMessage("Menyiapkan alamat delivery Anda…");
  progress.setIndeterminate(true);
  progress.setCancelable(false);
  progress.show();
  new Thread(()->{
   HttpURLConnection c=null;String addr="";double lat=0,lng=0;boolean loaded=false;
   try {
    URL url=new URL("https://transiva.my.id/server/get_customer_profile.php?id="+
       java.net.URLEncoder.encode(id,"UTF-8")+"&_="+System.currentTimeMillis());
    c=CustomerApiClient.open(a,url.toString());c.setConnectTimeout(4500);c.setReadTimeout(5000);
    InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);in.close();
    JSONObject result=new JSONObject(bytes.toString("UTF-8"));
    JSONObject u=result.optJSONObject("user");
    if(result.optBoolean("success",false)&&u!=null){
      addr=u.optString("delivery_address","").trim();
      lat=u.optDouble("delivery_lat",0);lng=u.optDouble("delivery_lng",0);loaded=true;
    }
   }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
   final String saved=addr;final double la=lat,lo=lng;final boolean ok=loaded;
   new Handler(Looper.getMainLooper()).post(()->{
    pending.remove(key);
    if(progress.isShowing()) progress.dismiss();
    if(a.isFinishing()||a.isDestroyed()||!id.equals(user(a)))return;
    if(ok){update(a,saved,la,lo);if(!saved.isEmpty()){
      Intent i=new Intent(a,"TransFood".equalsIgnoreCase(service)?TransFoodActivity.class:TransShopActivity.class);
      if(("TransFood".equalsIgnoreCase(service)&&a instanceof TransFoodActivity)||("TransShop".equalsIgnoreCase(service)&&a instanceof TransShopActivity))a.recreate();else a.startActivity(i);return;
    }explainMissing(a,service);
    }else new TransivaAlertDialogBuilder(a).setTitle("Alamat belum dapat diperiksa")
      .setMessage("Koneksi ke server belum berhasil. Anda dapat mencoba lagi atau menyimpan alamat delivery melalui profil.")
      .setPositiveButton("Coba lagi",(dialog,which)->require(a,service))
      .setNeutralButton("Simpan alamat",(dialog,which)->profile(a,service)).setNegativeButton("Tutup",null).show();
   });
  }).start();
  return false;
 }
 private static void explainMissing(Activity a,String service){
  if(a.isFinishing()||a.isDestroyed())return;
  new TransivaAlertDialogBuilder(a).setTitle("Simpan alamat delivery dahulu")
   .setMessage(service+" membutuhkan alamat tujuan pengantaran. Alamat delivery Anda belum tersimpan. Pilih Simpan alamat untuk mengaturnya di profil, lalu buka layanan kembali.")
   .setPositiveButton("Simpan alamat",(dialog,which)->profile(a,service)).setNegativeButton("Nanti",null).show();
 }
 public static void showUnavailable(Activity a,String service){
  android.widget.LinearLayout layout=new android.widget.LinearLayout(a);layout.setOrientation(1);layout.setPadding(32,40,32,32);
  android.widget.TextView title=new android.widget.TextView(a);title.setText(service);title.setTextSize(23);layout.addView(title);
  android.widget.TextView message=new android.widget.TextView(a);message.setText("Alamat delivery belum siap. Simpan alamat pengantaran agar toko dan restoran dapat ditampilkan sesuai lokasi Anda.");message.setTextSize(16);message.setPadding(0,24,0,24);layout.addView(message);
  android.widget.Button save=new android.widget.Button(a);save.setText("Simpan alamat delivery");save.setOnClickListener(v->profile(a,service));layout.addView(save);
  android.widget.Button retry=new android.widget.Button(a);retry.setText("Periksa alamat lagi");retry.setOnClickListener(v->require(a,service));layout.addView(retry);
  android.widget.Button back=new android.widget.Button(a);back.setText("Kembali");back.setOnClickListener(v->a.finish());layout.addView(back);
  a.setContentView(layout);CustomerAppSettings.applyToView(a,layout);
 }
 public static void edit(Activity a,String service){profile(a,service);}
}
