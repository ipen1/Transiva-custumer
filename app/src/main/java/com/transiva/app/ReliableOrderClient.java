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
    static final class FoodCartChangedException extends Exception {
        FoodCartChangedException() { super("Isi keranjang berubah. Periksa kembali sebelum memesan."); }
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
            if (isPickupSlot(slot) && prefs.contains(slot + ":receipt")) {
                throw new PendingAttemptException("Pesanan sebelumnya sudah berhasil. Buka kembali TransSend untuk melihat hasilnya.");
            }
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
                    saveOutcome(prefs,slot,user,original);original.put("recovered_attempt",true);original.put("recovered_previous_attempt",true);original.put("message","Hasil pesanan sebelumnya dipulihkan. Periksa Aktivitas sebelum membuat pesanan baru.");return original;
                }
                // Server acquired the same attempt lock and confirmed it has no committed receipt.
                key="";
            }
            boolean newAttempt = key.isEmpty();
            if (newAttempt) key = "ORD-" + UUID.randomUUID();
            synchronized (FoodCartStore.class) {
                if (newAttempt && slot.endsWith(":create_food_order.php") && !FoodCartStore.matchesDraft(prefs, user, body))
                    throw new FoodCartChangedException();
                if (!prefs.edit().putString(slot+":payload",canonical).putString(slot+":key",key).commit())
                    throw new PendingAttemptException("Percobaan order belum dapat disimpan di perangkat.");
            }
            if(!user.equals(new SessionManager(app).getUserId()))throw new InterruptedException("Account changed");
            body.put("id",key);body.put("user_id",user);
            try {
                JSONObject result=TransivaHttpRepository.postJsonIdempotent(app,url,body,timeout,key);
                saveOutcome(prefs,slot,user,result);return result;
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
                            saveOutcome(prefs,slot,user,receipt);receipt.put("recovered_attempt",true);return receipt;
                        }
                    } catch(Exception ignored) { /* Preserve the key until its outcome is known. */ }
                }
                throw error;
            }
        }finally{synchronized(ReliableOrderClient.class){IN_FLIGHT.remove(slot);}}
    }
    static boolean hasPendingFoodDraft(Context context, String draftId) {
        String user = new SessionManager(context).getUserId();
        String slot = "order:" + user + ":create_food_order.php";
        SharedPreferences prefs = context.getSharedPreferences("transiva_order_attempts", Context.MODE_PRIVATE);
        if (prefs.getString(slot + ":key", "").isEmpty()) return false;
        try {
            JSONObject payload = new JSONObject(prefs.getString(slot + ":payload", "{}"));
            return draftId.equals(payload.optString("_direct_food_draft"));
        } catch (org.json.JSONException invalid) {
            return true;
        }
    }

    static JSONObject recoverPendingFood(Context context,String url,int timeout) throws Exception {
        Context app=context.getApplicationContext();
        CustomerTransportSession owner=CustomerTransportSession.capture(context);
        String user=new SessionManager(app).getUserId();
        String slot="order:"+user+":create_food_order.php";
        SharedPreferences prefs=app.getSharedPreferences("transiva_order_attempts",Context.MODE_PRIVATE);
        String key;
        synchronized (ReliableOrderClient.class) {
            key=prefs.getString(slot+":key","");
            if (key.isEmpty()) return null;
            if (!IN_FLIGHT.add(slot)) throw new PendingAttemptException("Permintaan order masih diproses.");
        }
        try {
            String resolve=url.substring(0,url.lastIndexOf('/')+1)+"customer_resolve_order_attempt.php?attempt_key="
                    +java.net.URLEncoder.encode(key,"UTF-8");
            JSONObject response=TransivaHttpRepository.getJsonOnce(app,resolve,timeout,owner);
            if (!response.optBoolean("success")) throw new PendingAttemptException("Hasil pesanan sebelumnya belum dapat dipastikan.");
            if (!response.optBoolean("attempt_found")) return null;
            JSONObject order=response.optJSONObject("order_response");
            if (order==null || !order.optBoolean("success") || order.optString("order_id").isEmpty()) throw new PendingAttemptException("Hasil pesanan sebelumnya belum dapat dibaca.");
            owner.check(app);
            saveOutcome(prefs,slot,user,order);
            order.put("recovered_attempt",true);
            return order;
        } finally { synchronized (ReliableOrderClient.class) { IN_FLIGHT.remove(slot); } }
    }

    private static final String PICKUP_URL = "https://transiva.my.id/server/create_pickup_order.php";

    private static boolean isPickupSlot(String slot) {
        return slot.endsWith(":create_pickup_order.php");
    }

    private static void saveOutcome(SharedPreferences prefs, String slot, String user, JSONObject result) throws Exception {
        if (slot.endsWith(":create_food_order.php") && result.optBoolean("success")) {
            if (result.optString("order_id").isEmpty()) throw new PendingAttemptException(
                    "Bukti pesanan belum lengkap. Periksa Aktivitas sebelum mencoba lagi.");
            synchronized (FoodCartStore.class) {
                String saved = prefs.getString(slot + ":payload", "");
                SharedPreferences.Editor edit = prefs.edit().remove(slot + ":payload").remove(slot + ":key");
                if (!saved.isEmpty()) {
                    JSONObject payload = new JSONObject(saved);
                    // Older servers may omit the merchant from a recovered receipt.
                    if (!result.has("restaurant_id")) result.put("restaurant_id", payload.optInt("restaurant_id"));
                    if (payload.has("_direct_food_draft")) {
                        result.put("_direct_food_draft", payload.optString("_direct_food_draft"));
                        result.put("_direct_food_items", payload.optJSONArray("items"));
                    }
                    FoodCartStore.consume(prefs, edit, user, payload);
                    FoodCombinedCheckout.appendReceipt(prefs, edit, user, payload, result);
                }
                if (!edit.commit()) throw new PendingAttemptException(
                        "Pesanan tercatat. Periksa Aktivitas untuk melihat pesananmu.");
            }
            return;
        }
        if (!isPickupSlot(slot) || !result.optBoolean("success")) {
            clear(prefs, slot);
            return;
        }
        String id = result.optString("order_id", "").trim();
        if (id.isEmpty()) throw new PendingAttemptException("Hasil pengiriman belum lengkap. Periksa kembali TransSend.");
        // Save the result and remove the attempt in one disk commit before returning to an Activity.
        // OTP is fetched from the authenticated receipt endpoint when needed.
        JSONObject saved = new JSONObject().put("success", true).put("order_id", id);
        String key = prefs.getString(slot + ":key", "");
        boolean stored = prefs.edit().putString(slot + ":receipt", saved.toString())
                .putString(slot + ":receipt_attempt", key)
                .remove(slot + ":payload").remove(slot + ":key").commit();
        if (!stored) throw new PendingAttemptException("Hasil pengiriman belum tersimpan di perangkat. Periksa kembali TransSend.");
    }

    private static String pickupSlot(Context context) {
        SessionManager session = new SessionManager(context);
        String user = session.getUserId();
        return session.isLoggedIn() && user != null && !user.isEmpty() && !"0".equals(user)
                ? "order:" + user + ":create_pickup_order.php" : "";
    }

    public static boolean hasPickupRecovery(Context context) {
        String slot = pickupSlot(context);
        if (slot.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences("transiva_order_attempts", Context.MODE_PRIVATE);
        return prefs.contains(slot + ":receipt") || !prefs.getString(slot + ":key", "").isEmpty();
    }

    public static JSONObject recoverPickup(Context context, int timeout) throws Exception {
        Context app = context.getApplicationContext();
        CustomerTransportSession owner = CustomerTransportSession.capture(app);
        String slot = pickupSlot(app);
        if (slot.isEmpty()) throw new PendingAttemptException("Login diperlukan.");
        SharedPreferences prefs = app.getSharedPreferences("transiva_order_attempts", Context.MODE_PRIVATE);
        synchronized (ReliableOrderClient.class) {
            if (!IN_FLIGHT.add(slot)) throw new PendingAttemptException("Pengiriman sebelumnya masih diproses. Periksa lagi sebentar.");
        }
        try {
            owner.check(app);
            String saved = prefs.getString(slot + ":receipt", "");
            JSONObject receipt = saved.isEmpty() ? null : new JSONObject(saved);
            String key = prefs.getString(slot + (receipt == null ? ":key" : ":receipt_attempt"), "");
            if (key.isEmpty()) return receipt;
            JSONObject response;
            try {
                String url = PICKUP_URL.substring(0, PICKUP_URL.lastIndexOf('/') + 1)
                        + "customer_resolve_order_attempt.php?attempt_key="
                        + java.net.URLEncoder.encode(key, "UTF-8");
                response = TransivaHttpRepository.getJsonOnce(app, url, receipt == null ? timeout : Math.min(timeout, 5000));
            } catch (Exception unavailable) {
                owner.check(app);
                if (receipt != null) return receipt;
                throw unavailable;
            }
            owner.check(app);
            if (!response.optBoolean("success")) {
                if (receipt != null) return receipt;
                throw new PendingAttemptException("Hasil pengiriman belum dapat diperiksa.");
            }
            if (!response.optBoolean("attempt_found")) {
                if (receipt != null) return receipt;
                return new JSONObject().put("recovery_pending", true);
            }
            JSONObject original = response.optJSONObject("order_response");
            if (original == null || !original.optBoolean("success") || original.optString("order_id", "").isEmpty()) {
                throw new PendingAttemptException("Hasil pengiriman belum lengkap. Periksa lagi.");
            }
            if (receipt != null && !receipt.optString("order_id").equals(original.optString("order_id"))) {
                throw new PendingAttemptException("Hasil pengiriman belum cocok. Periksa Aktivitas.");
            }
            if (receipt == null) saveOutcome(prefs, slot, "", original);
            return original;
        } finally {
            synchronized (ReliableOrderClient.class) { IN_FLIGHT.remove(slot); }
        }
    }

    public static JSONObject retryPickup(Context context, int timeout) throws Exception {
        String slot = pickupSlot(context);
        SharedPreferences prefs = context.getSharedPreferences("transiva_order_attempts", Context.MODE_PRIVATE);
        if (slot.isEmpty() || prefs.contains(slot + ":receipt")) {
            throw new PendingAttemptException("Periksa hasil pengiriman sebelumnya.");
        }
        String payload = prefs.getString(slot + ":payload", "");
        if (payload.isEmpty() || prefs.getString(slot + ":key", "").isEmpty()) {
            throw new PendingAttemptException("Data pengiriman sebelumnya belum tersedia. Periksa Aktivitas.");
        }
        return create(context, PICKUP_URL, new JSONObject(payload), timeout);
    }

    public static boolean acknowledgePickup(Context context, String orderId) {
        String slot = pickupSlot(context);
        if (slot.isEmpty() || orderId == null || orderId.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences("transiva_order_attempts", Context.MODE_PRIVATE);
        try {
            JSONObject saved = new JSONObject(prefs.getString(slot + ":receipt", "{}"));
            if (!orderId.equals(saved.optString("order_id"))) return false;
            return prefs.edit().remove(slot + ":receipt").remove(slot + ":receipt_attempt").commit();
        } catch (org.json.JSONException invalid) { return false; }
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
