package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Reward popups are ephemeral completion events, never a replay of order history. */
public final class RoyaltyRewardFeedback {
    private static final Set<String> busy=new HashSet<>();
    private static final java.util.Map<String, PendingReward> pending = new java.util.HashMap<>();
    private static final long POPUP_TTL_MS = 10 * 60 * 1000L;
    private static final class PendingReward {
        final String orderId, source;
        final long createdAt = android.os.SystemClock.elapsedRealtime();
        PendingReward(String orderId, String source) {
            this.orderId = orderId;
            this.source = source != null && source.contains("pickup") ? "pickup_orders" : "orders";
        }
    }
    private RoyaltyRewardFeedback() {}

    /** Call only after observing a live order finish, or successful receipt confirmation. */
    public static void completed(Activity a, String orderId, String source) {
        String uid = new SessionManager(a).getUserId();
        if (uid == null || uid.isEmpty() || orderId == null || orderId.isEmpty()) return;
        synchronized (busy) { pending.put(uid, new PendingReward(orderId, source)); }
    }
    public static void attach(Activity a,android.widget.LinearLayout box,String orderId,String source){
        android.widget.TextView note=new android.widget.TextView(a);note.setTextSize(13);note.setTextColor(android.graphics.Color.rgb(8,120,160));note.setPadding(0,8,0,16);note.setVisibility(android.view.View.GONE);box.addView(note);
        final String uid=new SessionManager(a).getUserId();
        if(uid==null||uid.isEmpty())return;
        TransivaNetworkExecutor.execute(()->{try{
            String url="https://transiva.my.id/server/customer_royalty_receipts.php?order_id="+java.net.URLEncoder.encode(orderId,"UTF-8")+"&source="+(source.contains("pickup")?"pickup_orders":"orders");
            JSONObject response=TransivaHttpRepository.getJson(a,url,15000);JSONArray receipts=response.optJSONArray("receipts");if(receipts==null||receipts.length()==0)return;JSONObject receipt=receipts.optJSONObject(0);if(receipt==null)return;
            a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()||!uid.equals(new SessionManager(a).getUserId()))return;note.setText(receipt.optString("message"));note.setVisibility(android.view.View.VISIBLE);});
        }catch(Exception ignored){}});
    }
    public static void check(Activity a) {
        final String uid = new SessionManager(a).getUserId();
        if (uid == null || uid.isEmpty()) return;
        final PendingReward reward;
        synchronized (busy) {
            // No event after login/reinstall/process restart: never fetch historical rewards.
            if (busy.contains(uid)) return;
            reward = pending.remove(uid);
            if (reward == null || android.os.SystemClock.elapsedRealtime() - reward.createdAt > POPUP_TTL_MS) return;
            busy.add(uid);
        }
        final String id = reward.source + ":" + reward.orderId;
        final SharedPreferences prefs = a.getSharedPreferences("royalty_receipts_" + uid, 0);
        if (prefs.getBoolean(id, false)) {
            synchronized (busy) { busy.remove(uid); }
            return;
        }
        // A reward notification must never block ordering or become a retry popup queue.
        prefs.edit().putBoolean(id, true).apply();
        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("action", "claim_popup")
                        .put("order_id", reward.orderId).put("source", reward.source);
                JSONObject response = TransivaHttpRepository.postJson(a,
                        "https://transiva.my.id/server/customer_royalty_receipts.php", body, 15000);
                JSONArray rows = response.optJSONArray("receipts");
                a.runOnUiThread(() -> {
                    synchronized (busy) { busy.remove(uid); }
                    if (a.isFinishing() || a.isDestroyed() || !a.hasWindowFocus()
                            || !uid.equals(new SessionManager(a).getUserId())
                            || !response.optBoolean("success") || rows == null || rows.length() == 0) return;
                    JSONObject receipt = rows.optJSONObject(0);
                    if (receipt == null || !id.equals(receipt.optString("receipt_id"))) return;
                    new TransivaAlertDialogBuilder(a).setTitle("Pesanan selesai, hadiah bertambah ✦")
                            .setMessage(receipt.optString("message"))
                            .setPositiveButton("Lihat misi", (d, w) -> a.startActivity(new Intent(a, CustomerLoyaltyActivity.class)))
                            .setNegativeButton("Lanjut", null).show();
                });
            } catch (Exception ignored) {
                synchronized (busy) { busy.remove(uid); }
            }
        }, () -> { synchronized (busy) { busy.remove(uid); } });
    }
}
