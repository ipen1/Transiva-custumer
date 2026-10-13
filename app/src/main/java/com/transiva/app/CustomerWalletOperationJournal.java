package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.UUID;

/** One unresolved money intent per account. No auth token is persisted. No TTL discards it. */
final class CustomerWalletOperationJournal {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    private final String account;
    CustomerWalletOperationJournal(Context context, String account) {
        backgroundOnly();
        if (account == null || !account.matches("[0-9]+") || "0".equals(account))
            throw new IllegalStateException("Sesi wallet tidak valid");
        this.account = account;
        preferences = context.getApplicationContext().getSharedPreferences("transiva_wallet_operation_" + account, Context.MODE_PRIVATE);
    }
    private static void backgroundOnly() {
        android.os.Looper current=android.os.Looper.myLooper();
        if(current!=null&&current==android.os.Looper.getMainLooper())throw new IllegalStateException("Wallet journal requires background execution");
    }
    JSONObject pending() throws Exception { backgroundOnly(); synchronized (LOCK) {
        String raw = preferences.getString("pending", "");
        return raw == null || raw.isEmpty() ? null : new JSONObject(raw);
    } }
    JSONObject begin(String action, JSONObject payload) throws Exception { backgroundOnly(); synchronized (LOCK) {
        if (pending() != null) throw new IllegalStateException("Periksa hasil operasi sebelumnya terlebih dahulu.");
        String id = "transfer".equals(action) ? payload.optString("request_id") : "cwd-" + UUID.randomUUID();
        if (id.isEmpty()) throw new IllegalStateException("ID operasi tidak valid");
        payload.put("operation_owner_id", account);
        if ("withdraw".equals(action)) payload.put("idempotency_key", id);
        JSONObject operation = new JSONObject().put("action", action).put("id", id).put("payload", new JSONObject(payload.toString()));
        if (!preferences.edit().putString("pending", operation.toString()).commit())
            throw new IllegalStateException("Operasi belum dapat disimpan. Tidak ada dana yang dikirim.");
        return operation;
    } }
    void prepareSend(String id) throws Exception { backgroundOnly(); synchronized (LOCK) {
        JSONObject current = pending();
        if (current == null || !id.equals(current.optString("id"))
                || !preferences.edit().putString("pending", current.toString()).commit())
            throw new IllegalStateException("Operasi belum tersimpan. Periksa penyimpanan perangkat.");
    } }
    void resolve(String id) throws Exception { backgroundOnly(); synchronized (LOCK) {
        JSONObject current = pending();
        if (current != null && id.equals(current.optString("id"))
                && !preferences.edit().remove("pending").commit()) {
            // Android updates memory even when a disk commit fails; restore the unresolved intent.
            preferences.edit().putString("pending", current.toString()).commit();
            throw new IllegalStateException("Hasil diterima, tetapi penanda operasi belum tersimpan. Periksa kembali.");
        }
    } }
}
