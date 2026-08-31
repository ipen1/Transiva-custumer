package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Handles destructive account deletion outside ProfileActivity to keep account UI maintainable. */
public final class CustomerAccountDeletionController {
    private static final String DELETE_URL = "https://transiva.my.id/server/customer_delete_account.php";

    public interface Listener {
        void onBusyChanged(boolean busy);
        void onError(String message);
    }

    private CustomerAccountDeletionController() {}

    public static void show(Activity activity, Listener listener) {
        if (activity == null || activity.isFinishing()) return;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        box.setPadding(pad, 0, pad, 0);

        EditText phrase = new EditText(activity);
        phrase.setHint("Ketik: HAPUS AKUN");
        phrase.setSingleLine(true);
        box.addView(phrase, new LinearLayout.LayoutParams(-1, -2));

        EditText password = new EditText(activity);
        password.setHint("Kata sandi akun");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSingleLine(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dp(activity, 8);
        box.addView(password, pp);

        new TransivaAlertDialogBuilder(activity)
                .setTitle("Hapus Akun Permanen")
                .setMessage("Akun akan dinonaktifkan permanen dan data pribadi dihapus. Riwayat transaksi tertentu dapat dipertahankan tanpa identitas pribadi untuk kewajiban transaksi/hukum. Pastikan saldo Rp0 dan tidak ada order aktif.")
                .setView(box)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus Permanen", (dialog, which) -> {
                    String confirm = phrase.getText() == null ? "" : phrase.getText().toString().trim();
                    String pass = password.getText() == null ? "" : password.getText().toString();
                    if (!"HAPUS AKUN".equalsIgnoreCase(confirm)) {
                        if (listener != null) listener.onError("Ketik HAPUS AKUN untuk mengonfirmasi penghapusan.");
                        return;
                    }
                    if (pass.trim().isEmpty()) {
                        if (listener != null) listener.onError("Kata sandi wajib diisi.");
                        return;
                    }
                    delete(activity, pass, listener);
                })
                .show();
    }

    private static void delete(Activity activity, String password, Listener listener) {
        if (listener != null) listener.onBusyChanged(true);
        new Thread(() -> {
            HttpURLConnection c = null;
            String message = "Penghapusan akun gagal diproses.";
            boolean success = false;
            try {
                c = CustomerApiClient.open(activity, DELETE_URL);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject();
                body.put("password", password);
                body.put("confirm", "HAPUS AKUN");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = c.getOutputStream()) { out.write(payload); }
                int code = c.getResponseCode();
                JSONObject result = new JSONObject(read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream()));
                success = result.optBoolean("success", false);
                message = result.optString("message", message);
            } catch (Throwable e) {
                TransivaCrashReporter.record(e, "customer_delete_account", "profile");
            } finally {
                if (c != null) c.disconnect();
            }

            final boolean ok = success;
            final String msg = message;
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onBusyChanged(false);
                if (!ok) {
                    if (listener != null) listener.onError(msg);
                    return;
                }
                try { new SessionManager(activity).markLoggedOut("account_deleted"); } catch (Throwable ignored) {}
                try { SecureTokenStore.clear(activity); } catch (Throwable ignored) {}
                Intent intent = new Intent(activity, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("account_deleted", true);
                activity.startActivity(intent);
                activity.finish();
            });
        }, "customer-account-delete").start();
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
