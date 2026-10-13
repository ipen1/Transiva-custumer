package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import org.json.JSONObject;
import java.util.UUID;

/** One unresolved send per conversation. Stable ID survives a timeout and screen/process recreation. */
final class CustomerChatSendJournal {
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    CustomerChatSendJournal(Context app, CustomerSessionGuard session, String conversation) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Chat storage requires background execution");
        if(!session.matches(app)||session.account.isEmpty())throw new IllegalStateException("Sesi chat berubah");
        String key=hash(session.account+":"+conversation);
        prefs=app.getSharedPreferences("transiva_chat_send_"+key,Context.MODE_PRIVATE);
    }
    private static String hash(String value) throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();for(byte b:digest)result.append(String.format(java.util.Locale.US,"%02x",b&255));return result.toString();
    }
    JSONObject pending() throws Exception { synchronized(LOCK) {
        String raw=prefs.getString("pending","");return raw.isEmpty()?null:new JSONObject(raw);
    } }
    JSONObject begin(JSONObject payload) throws Exception { synchronized(LOCK) {
        JSONObject previous=pending();
        if(previous!=null) {
            JSONObject old=new JSONObject(previous.toString());old.remove("client_message_id");
            JSONObject next=new JSONObject(payload.toString());next.remove("client_message_id");
            if(!OrderPayloadFingerprint.canonical(old).equals(OrderPayloadFingerprint.canonical(next)))
                throw new IllegalStateException("Kirim ulang pesan tertunda terlebih dahulu melalui tombol di atas percakapan.");
            if(!prefs.edit().putString("pending",previous.toString()).commit())
                throw new IllegalStateException("Pesan belum tersimpan di perangkat. Coba kembali.");
            return previous;
        }
        JSONObject operation=new JSONObject(payload.toString()).put("client_message_id","ccm-"+UUID.randomUUID());
        if(!prefs.edit().putString("pending",operation.toString()).commit())
            throw new IllegalStateException("Pesan belum tersimpan di perangkat. Coba kembali.");
        return operation;
    } }
    void resolve(String id) throws Exception { synchronized(LOCK) {
        JSONObject operation=pending();
        if(operation!=null&&id.equals(operation.optString("client_message_id"))
                &&!prefs.edit().remove("pending").commit()) {
            prefs.edit().putString("pending",operation.toString()).commit();
            throw new IllegalStateException("Pesan diterima. Periksa kembali pesan tertunda.");
        }
    } }
}
