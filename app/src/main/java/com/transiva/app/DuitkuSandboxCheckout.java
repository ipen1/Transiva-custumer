package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

final class DuitkuSandboxCheckout {
    static final String METHOD = "qris";
    static final int REQUEST_PAYMENT = 6931;
    private static final String STATUS_PATH = "duitku_sandbox_status.php";

    private DuitkuSandboxCheckout() {}

    static String label(String method) {
        if (METHOD.equals(method)) return "QRIS";
        return "balance".equals(method) ? "Transiva Pay" : "Tunai";
    }

    interface PaymentSelection {
        void select(String method);
    }

    static void chooseMethod(Activity activity, String checkoutUrl, String service, String current,
                             boolean supportsSandbox, String title, int timeout, PaymentSelection selection) {
        CustomerTransportSession owner = CustomerTransportSession.capture(activity);
        ArrayAdapter<String> methods = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_single_choice);
        methods.add("Tunai");
        methods.add("Transiva Pay");
        int selected = METHOD.equals(current) ? -1 : "balance".equals(current) ? 1 : 0;
        AlertDialog dialog = new TransivaAlertDialogBuilder(activity)
                .setTitle(title)
                .setSingleChoiceItems(methods, selected, (choice, index) -> {
                    choice.dismiss();
                    if (owner.matches(activity)) selection.select(index == 2 ? METHOD : index == 1 ? "balance" : "cash");
                })
                .setNegativeButton("Batal", null)
                .setNeutralButton("Riwayat QRIS", (choice, button) -> {
                    if (owner.matches(activity)) showHistory(activity, checkoutUrl, service, timeout);
                })
                .show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
        if (!supportsSandbox) return;
        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject request = new JSONObject().put("action", "capabilities");
                JSONObject result = TransivaHttpRepository.postJson(activity, endpoint(checkoutUrl, STATUS_PATH),
                        request, Math.min(timeout, 3500), owner);
                if (!result.optBoolean("success") || !result.optBoolean("sandbox_qris_available")) return;
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed() || !owner.matches(activity)) return;
                    methods.add(label(METHOD));
                    if (METHOD.equals(current)) dialog.getListView().setItemChecked(2, true);
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);
                });
            } catch (Exception ignored) {
                // Cash and TransPay remain selectable when the sandbox is unavailable.
            }
        });
    }

    static JSONObject create(Activity activity, String checkoutUrl, String service, JSONObject payload, int timeout) throws Exception {
        String url = endpoint(checkoutUrl, "duitku_sandbox_checkout.php");
        JSONObject body = new JSONObject(payload.toString()).put("sandbox_service", service)
                .put("id", "TEST-" + UUID.randomUUID());
        JSONObject result = TransivaHttpRepository.postJson(activity, url, body, timeout);
        if (result.optBoolean("success") && result.optJSONObject("sandbox_payment") == null) {
            throw new IllegalStateException("Server belum mengirim rincian QRIS.");
        }
        return result;
    }

    static boolean showResult(Activity activity, String checkoutUrl, JSONObject result, int timeout) {
        JSONObject payment = result == null ? null : result.optJSONObject("sandbox_payment");
        if (payment == null) return false;
        DuitkuSandboxPaymentActivity.open(activity, payment, true);
        return true;
    }

    private static void showHistory(Activity activity, String checkoutUrl, String service, int timeout) {
        CustomerTransportSession owner = CustomerTransportSession.capture(activity);
        String url = endpoint(checkoutUrl, STATUS_PATH);
        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject request = new JSONObject().put("action", "history").put("service", service);
                JSONObject response = TransivaHttpRepository.postJson(activity, url, request, timeout, owner);
                JSONArray rows = response.optJSONArray("sandbox_payments");
                if (rows == null) {
                    JSONObject latest = response.optJSONObject("sandbox_payment");
                    if (latest == null) throw new IllegalStateException(response.optString("message", "Riwayat QRIS belum tersedia."));
                    rows = new JSONArray().put(latest);
                }
                ArrayList<JSONObject> payments = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                SimpleDateFormat format = new SimpleDateFormat("dd MMM · HH.mm", new Locale("id", "ID"));
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject payment = rows.optJSONObject(i);
                    if (payment == null || payment.optString("payment_id").isEmpty()) continue;
                    long created = payment.optLong("created_epoch");
                    String time = created > 0 ? "\n" + format.format(new Date(created * 1000L)) : "";
                    labels.add(payment.optString("service") + " · " + rupiah(payment)
                            + " · " + historyStatus(payment.optString("status")) + time);
                    payments.add(payment);
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !owner.matches(activity)) return;
                    AlertDialog.Builder dialog = new TransivaAlertDialogBuilder(activity)
                            .setTitle("Riwayat QRIS").setNegativeButton("Tutup", null);
                    if (payments.isEmpty()) {
                        dialog.setMessage("Belum ada tagihan QRIS untuk akun ini.");
                    } else {
                        dialog.setItems(labels.toArray(new String[0]), (choice, index) -> {
                            if (owner.matches(activity)) DuitkuSandboxPaymentActivity.open(activity, payments.get(index), false);
                        });
                    }
                    dialog.show();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed() && owner.matches(activity)) {
                        Toast.makeText(activity, "Riwayat QRIS belum bisa dibaca. Coba lagi.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    static String historyStatus(String status) {
        switch (status) {
            case "paid": return "Lunas";
            case "refunded": return "Dikembalikan ke TransPay";
            case "cancelled": return "Dibatalkan · Refund belum tercatat";
            case "expired": return "Kedaluwarsa";
            case "closed": return "Ditutup";
            case "creating": return "Sedang dibuat";
            default: return "Menunggu pembayaran";
        }
    }

    static String rupiah(JSONObject payment) {
        return "Rp" + NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(payment.optLong("amount"));
    }

    private static String endpoint(String checkoutUrl, String path) {
        return checkoutUrl.substring(0, checkoutUrl.lastIndexOf('/') + 1) + path;
    }

    static void acknowledgeFood(Activity activity, JSONObject receipt) throws Exception {
        if (!"food".equals(receipt.optString("order_type"))) return;
        String user = new SessionManager(activity).getUserId();
        String id = receipt.optString("order_id");
        if (user == null || user.isEmpty() || !id.startsWith("FOOD-QR-")) return;
        android.content.SharedPreferences prefs = activity.getSharedPreferences("transiva_order_attempts", Activity.MODE_PRIVATE);
        String key = "qris-confirmed:" + user + ":" + id;
        synchronized (FoodCartStore.class) {
            if (prefs.getBoolean(key, false)) return;
            android.content.SharedPreferences.Editor edit = prefs.edit().putBoolean(key, true);
            FoodCartStore.consume(prefs, edit, user, receipt);
            if (!edit.commit()) throw new IllegalStateException("Keranjang belum dapat diperbarui.");
        }
    }

    interface HistoryLoaded {
        void loaded(JSONArray rows);
    }

    static void loadHistory(Activity activity, HistoryLoaded callback) {
        CustomerTransportSession owner = CustomerTransportSession.capture(activity);
        TransivaNetworkExecutor.execute(() -> {
            try {
                String url = ApiConfig.server(STATUS_PATH);
                JSONObject probe = TransivaHttpRepository.postJson(activity, url,
                        new JSONObject().put("action", "capabilities"), 3500, owner);
                if (!probe.optBoolean("sandbox_qris_available")) return;
                JSONObject response = TransivaHttpRepository.postJson(activity, url,
                        new JSONObject().put("action", "history"), 6000, owner);
                JSONArray rows = response.optJSONArray("sandbox_payments");
                if (rows == null) return;
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed() && !activity.isFinishing() && owner.matches(activity)) callback.loaded(rows);
                });
            } catch (Exception ignored) { /* QRIS records are supplementary. */ }
        });
    }

}
