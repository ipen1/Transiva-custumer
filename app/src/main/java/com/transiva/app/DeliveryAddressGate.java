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
 private static String user(Context a) {
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
 private static final Handler main=new Handler(Looper.getMainLooper());
 private interface Result { void done(JSONObject profile); }
 static void storeProfile(Context context,String id,JSONObject profile) {
  if(!id.equals(user(context)) || profile==null)return;
  context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
    .putString("user",id).putString("address",profile.optString("delivery_address","").trim())
    .putLong("lat",Double.doubleToRawLongBits(profile.optDouble("delivery_lat",0)))
    .putLong("lng",Double.doubleToRawLongBits(profile.optDouble("delivery_lng",0))).apply();
 }
 private static void fetch(Context context,String id,Result listener) {
  CustomerProfileCache.fetch(context,false,profile->{
   if(profile!=null)storeProfile(context,id,profile);
   if(listener!=null)listener.done(profile);
  });
 }
 public static void prefetch(Activity a) {
  CustomerProfileCache.warm(a.getApplicationContext());
 }
 public static boolean require(Activity a,String service) {
  if(valid(a))return true;
  String id=user(a);
  if(id.isEmpty()){explainMissing(a,service);return false;}
  if(a.isFinishing()||a.isDestroyed())return false;
  String key=id+":gate";
  if(!pending.add(key))return false;
  final ProgressDialog progress=new ProgressDialog(a);
  final java.lang.ref.WeakReference<Activity> target=new java.lang.ref.WeakReference<>(a);
  final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean(false);
  progress.setMessage("Menyiapkan alamat delivery Anda…");
  progress.setIndeterminate(true);progress.setCancelable(true);
  progress.setOnCancelListener(dialog->{cancelled.set(true);pending.remove(key);});
  try{progress.show();}catch(RuntimeException unavailable){pending.remove(key);return false;}
  fetch(a.getApplicationContext(),id,profile->{
   if(!cancelled.get())pending.remove(key);
   try{if(progress.isShowing())progress.dismiss();}catch(RuntimeException ignored){}
   Activity current=target.get();
   if(cancelled.get()||current==null||current.isFinishing()||current.isDestroyed()||!id.equals(user(current)))return;
   if(profile!=null){
    String saved=profile.optString("delivery_address","").trim();
    if(!saved.isEmpty()){
     Intent intent=new Intent(current,"TransFood".equalsIgnoreCase(service)?TransFoodActivity.class:TransShopActivity.class);
     if(("TransFood".equalsIgnoreCase(service)&&current instanceof TransFoodActivity)
       ||("TransShop".equalsIgnoreCase(service)&&current instanceof TransShopActivity))current.recreate();
     else current.startActivity(intent);
    }else explainMissing(current,service);
   }else new TransivaAlertDialogBuilder(current).setTitle("Alamat belum dapat diperiksa")
     .setMessage("Koneksi ke server belum berhasil. Coba lagi atau simpan alamat delivery melalui profil.")
     .setPositiveButton("Coba lagi",(dialog,which)->require(current,service))
     .setNeutralButton("Simpan alamat",(dialog,which)->profile(current,service))
     .setNegativeButton("Tutup",null).show();
  });
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
