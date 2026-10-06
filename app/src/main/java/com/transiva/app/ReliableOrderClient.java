package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Durable attempts survive a lost response; a changed payload first resolves the previous attempt. */
public final class ReliableOrderClient {
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    public static final class PendingAttemptException extends Exception {
        PendingAttemptException(String message) { super(message); }
    }
    private ReliableOrderClient() {}

    public static JSONObject create(Context context, String url, JSONObject input, int timeout) throws Exception {
        Context app=context.getApplicationContext();
        String user=new SessionManager(app).getUserId();
        if(user==null||user.isEmpty()||"0".equals(user))throw new PendingAttemptException("Login diperlukan.");
        String slot="order:"+user+":"+url.substring(url.lastIndexOf('/')+1);
        SharedPreferences prefs=app.getSharedPreferences("transiva_order_attempts",Context.MODE_PRIVATE);
        JSONObject body=new JSONObject(input.toString());body.remove("id");
        String canonical=OrderPayloadFingerprint.canonical(body);String key;boolean changed;
        synchronized(ReliableOrderClient.class){
            if(IN_FLIGHT.contains(slot))throw new PendingAttemptException("Permintaan order masih diproses.");
            String saved=prefs.getString(slot+":payload","");key=prefs.getString(slot+":key","");
            changed=!saved.isEmpty()&&!saved.equals(canonical)&&!key.isEmpty();IN_FLIGHT.add(slot);
        }
        try{
            if(!user.equals(new SessionManager(app).getUserId()))throw new InterruptedException("Account changed");
            if(changed){
                JSONObject resolution;
                try{
                    String resolveUrl=url.substring(0,url.lastIndexOf('/')+1)+"customer_resolve_order_attempt.php?attempt_key="+java.net.URLEncoder.encode(key,"UTF-8");
                    resolution=TransivaHttpRepository.getJson(app,resolveUrl,timeout);
                }catch(Exception error){throw new PendingAttemptException("Hasil pesanan sebelumnya belum diketahui. Periksa Aktivitas atau ulangi pesanan yang sama dahulu.");}
                if(!resolution.optBoolean("success"))throw new PendingAttemptException("Hasil pesanan sebelumnya belum dapat dipastikan.");
                if(resolution.optBoolean("attempt_found")){
                    JSONObject original=resolution.optJSONObject("order_response");
                    if(original==null)throw new PendingAttemptException("Hasil pesanan sebelumnya belum dapat dibaca.");
                    clear(prefs,slot);original.put("recovered_attempt",true);original.put("message","Hasil pesanan sebelumnya dipulihkan. Periksa Aktivitas sebelum membuat pesanan baru.");return original;
                }
                // Server acquired the same attempt lock and confirmed it has no committed receipt.
                key="";
            }
            if(key.isEmpty())key="ORD-"+UUID.randomUUID();
            if(!prefs.edit().putString(slot+":payload",canonical).putString(slot+":key",key).commit())
                throw new PendingAttemptException("Percobaan order belum dapat disimpan di perangkat.");
            if(!user.equals(new SessionManager(app).getUserId()))throw new InterruptedException("Account changed");
            body.put("id",key);body.put("user_id",user);
            JSONObject result=TransivaHttpRepository.postJsonIdempotent(app,url,body,timeout,key);
            clear(prefs,slot);return result;
        }finally{synchronized(ReliableOrderClient.class){IN_FLIGHT.remove(slot);}}
    }
    private static void clear(SharedPreferences prefs,String slot){prefs.edit().remove(slot+":payload").remove(slot+":key").commit();}
    public static String failureMessage(Exception error){
        if(error instanceof PendingAttemptException)return error.getMessage();
        return "Hasil order belum dapat dipastikan. Periksa Aktivitas atau coba ulang pesanan yang sama.";
    }
}
