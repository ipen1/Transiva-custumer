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
            try {
                JSONObject result=TransivaHttpRepository.postJsonIdempotent(app,url,body,timeout,key);
                clear(prefs,slot);return result;
            } catch (Exception error) {
                if (definitelyRejected(error)) { clear(prefs,slot); throw error; }
                // A missing HTTP acknowledgement may still have committed: resolve the SAME key.
                if (!Thread.currentThread().isInterrupted() && (error instanceof java.io.IOException
                        || error instanceof org.json.JSONException
                        || (error instanceof TransivaHttpRepository.ServerResponseException
                            && ((TransivaHttpRepository.ServerResponseException)error).status>=500))) {
                    try {
                        String recoveryUrl=url.substring(0,url.lastIndexOf('/')+1)+"customer_resolve_order_attempt.php?attempt_key="+java.net.URLEncoder.encode(key,"UTF-8");
                        JSONObject found=TransivaHttpRepository.getJsonOnce(app,recoveryUrl,Math.min(timeout,6000));
                        JSONObject receipt=found.optJSONObject("order_response");
                        if(found.optBoolean("success") && found.optBoolean("attempt_found") && receipt!=null
                                && user.equals(new SessionManager(app).getUserId())) {
                            clear(prefs,slot);receipt.put("recovered_attempt",true);return receipt;
                        }
                    } catch(Exception ignored) { /* Preserve the key until its outcome is known. */ }
                }
                throw error;
            }
        }finally{synchronized(ReliableOrderClient.class){IN_FLIGHT.remove(slot);}}
    }
    private static void clear(SharedPreferences prefs,String slot){prefs.edit().remove(slot+":payload").remove(slot+":key").commit();}
    static boolean definitelyRejected(Exception error) {
        if(!(error instanceof TransivaHttpRepository.ServerResponseException))return false;
        JSONObject response=((TransivaHttpRepository.ServerResponseException)error).response;
        return response!=null && Boolean.FALSE.equals(response.opt("order_created"))
                && !response.optBoolean("success") && !"ORDER_OUTCOME_UNKNOWN".equals(response.optString("code"));
    }
    public static String failureMessage(Exception error){
        if(error instanceof PendingAttemptException)return error.getMessage();
        if(error instanceof TransivaHttpRepository.ServerResponseException) {
            String message=error.getMessage();
            if(message!=null&&!message.trim().isEmpty())return definitelyRejected(error)?message:message+"\nPeriksa Aktivitas sebelum membuat pesanan lain.";
        }
        return "Hasil order belum dapat dipastikan. Periksa Aktivitas atau coba ulang pesanan yang sama.";
    }
}
