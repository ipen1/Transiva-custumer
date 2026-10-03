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
  String local=new SessionManager(a).get("delivery_address");
  return local==null?"":local.trim();
 }
 public static boolean valid(Activity a) {return !address(a).isEmpty();}
 private static void profile(Activity a,String service) {
  Intent i=new Intent(a,ProfileActivity.class);
  i.putExtra("edit_delivery_address",true);i.putExtra("delivery_return_service",service);
  a.startActivity(i);
 }
 public static boolean require(Activity a,String service) {
  if(valid(a))return true;
  String id=user(a);
  if(id.isEmpty()){profile(a,service);return false;}
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
      a.startActivity(i);return;
    }profile(a,service);
    }else Toast.makeText(a,"Alamat belum dapat diperiksa. Periksa koneksi dan coba lagi.",Toast.LENGTH_LONG).show();
   });
  }).start();
  return false;
 }
 public static void edit(Activity a,String service){profile(a,service);}
}
