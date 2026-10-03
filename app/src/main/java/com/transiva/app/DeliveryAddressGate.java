package com.transiva.app;
import android.app.Activity;
import android.content.Intent;
import org.json.JSONObject;
/** Address check is service-scoped, never an application startup gate. */
public final class DeliveryAddressGate {
 private DeliveryAddressGate(){}
 public static String address(Activity a) {
  JSONObject u=new SessionManager(a).getSessionJson();
  return u.optString("delivery_address","").trim();
 }
 public static boolean valid(Activity a) {
  JSONObject u=new SessionManager(a).getSessionJson();
  double lat=u.optDouble("delivery_lat",0),lng=u.optDouble("delivery_lng",0);
  return !address(a).isEmpty() && Double.isFinite(lat) && Double.isFinite(lng)
   && lat>=-90 && lat<=90 && lng>=-180 && lng<=180
   && (Math.abs(lat)>0.000001 || Math.abs(lng)>0.000001);
 }
 public static boolean require(Activity a,String service) {
  if(valid(a))return true;
  Intent i=new Intent(a,ProfileActivity.class);
  i.putExtra("edit_delivery_address",true);
  i.putExtra("delivery_return_service",service);
  a.startActivity(i);
  return false;
 }
 public static void edit(Activity a,String service) {
  Intent i=new Intent(a,ProfileActivity.class);
  i.putExtra("edit_delivery_address",true);
  i.putExtra("delivery_return_service",service);
  a.startActivity(i);
 }
}
