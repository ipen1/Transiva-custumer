package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Shows actual order ledger rewards once per customer/order on this device. */
public final class RoyaltyRewardFeedback {
    private static final Set<String> busy=new HashSet<>();
    private RoyaltyRewardFeedback() {}
    public static void attach(Activity a,android.widget.LinearLayout box,String orderId,String source){
        android.widget.TextView note=new android.widget.TextView(a);note.setTextSize(13);note.setTextColor(android.graphics.Color.rgb(8,120,160));note.setPadding(0,8,0,16);note.setVisibility(android.view.View.GONE);box.addView(note);
        final String uid=new SessionManager(a).getUserId();
        TransivaNetworkExecutor.execute(()->{try{
            String url="https://transiva.my.id/server/customer_royalty_receipts.php?order_id="+java.net.URLEncoder.encode(orderId,"UTF-8")+"&source="+(source.contains("pickup")?"pickup_orders":"orders");
            JSONObject response=TransivaHttpRepository.getJson(a,url,15000);JSONArray receipts=response.optJSONArray("receipts");if(receipts==null||receipts.length()==0)return;JSONObject receipt=receipts.optJSONObject(0);if(receipt==null)return;
            a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()||!uid.equals(new SessionManager(a).getUserId()))return;note.setText(receipt.optString("message"));note.setVisibility(android.view.View.VISIBLE);if(note.getWindowToken()!=null)a.getSharedPreferences("royalty_receipts_"+uid,0).edit().putBoolean(receipt.optString("receipt_id"),true).apply();});
        }catch(Exception ignored){}});
    }
    public static void check(Activity a){
        String uid=new SessionManager(a).getUserId();if(uid==null||uid.isEmpty())return;
        synchronized(busy){if(!busy.add(uid))return;}
        TransivaNetworkExecutor.execute(()->{try{
            JSONObject response=TransivaHttpRepository.getJson(a,"https://transiva.my.id/server/customer_royalty_receipts.php",15000);
            JSONArray rows=response.optJSONArray("receipts");
            a.runOnUiThread(()->{synchronized(busy){busy.remove(uid);}if(a.isFinishing()||a.isDestroyed()||!uid.equals(new SessionManager(a).getUserId())||rows==null)return;
                SharedPreferences prefs=a.getSharedPreferences("royalty_receipts_"+uid,0);
                for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;String id=r.optString("receipt_id");if(id.isEmpty()||prefs.getBoolean(id,false))continue;
                    new TransivaAlertDialogBuilder(a).setTitle("Pesanan selesai, hadiah bertambah ✦").setMessage(r.optString("message"))
                        .setPositiveButton("Lihat misi",(d,w)->a.startActivity(new Intent(a,CustomerLoyaltyActivity.class))).setNegativeButton("Lanjut",null).show();
                    prefs.edit().putBoolean(id,true).apply();break;
                }
            });
        }catch(Exception ignored){synchronized(busy){busy.remove(uid);}}},()->{synchronized(busy){busy.remove(uid);}});
    }
}
