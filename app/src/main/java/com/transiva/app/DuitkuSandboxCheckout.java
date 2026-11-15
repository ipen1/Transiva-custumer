package com.transiva.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

final class DuitkuSandboxCheckout {
    static final String METHOD = "qris";
    private static final String STATUS_PATH = "duitku_sandbox_status.php";

    private DuitkuSandboxCheckout() {}

    static String label(String method) {
        if (METHOD.equals(method)) return "QRIS Sandbox";
        return "balance".equals(method) ? "Transiva Pay" : "Tunai";
    }

    static JSONObject create(Activity activity, String checkoutUrl, String service, JSONObject payload, int timeout) throws Exception {
        String url = endpoint(checkoutUrl, "duitku_sandbox_checkout.php");
        JSONObject body = new JSONObject(payload.toString()).put("sandbox_service", service)
                .put("id", "TEST-" + UUID.randomUUID());
        JSONObject result = TransivaHttpRepository.postJson(activity, url, body, timeout);
        if (result.optBoolean("success") && result.optJSONObject("sandbox_payment") == null) {
            throw new IllegalStateException("Server belum mengirim rincian QRIS Sandbox.");
        }
        return result;
    }

    static boolean showResult(Activity activity, String checkoutUrl, JSONObject result, int timeout) {
        JSONObject payment = result == null ? null : result.optJSONObject("sandbox_payment");
        if (payment == null) return false;
        show(activity, endpoint(checkoutUrl, STATUS_PATH), payment, timeout, false);
        return true;
    }

    static void showLatest(Activity activity, String checkoutUrl, String service, int timeout) {
        refresh(activity, endpoint(checkoutUrl, STATUS_PATH), service, "", timeout, false);
    }

    private static String endpoint(String checkoutUrl, String path) {
        return checkoutUrl.substring(0, checkoutUrl.lastIndexOf('/') + 1) + path;
    }

    private static void show(Activity activity, String statusUrl, JSONObject payment, int timeout, boolean retried) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String status = payment.optString("status");
        String service = payment.optString("service");
        String id = payment.optString("payment_id");
        String amount = NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(payment.optLong("amount"));
        String detail;
        switch (status) {
            case "paid":
                detail = "Pembayaran simulasi lunas.\nCallback diterima: "
                        + (payment.optBoolean("callback_received") ? "Ya" : "Belum");
                break;
            case "closed":
            case "expired":
                detail = "Tagihan ditutup atau kedaluwarsa. Cek status jika sudah melakukan simulasi bayar.";
                break;
            case "creating":
                detail = "Duitku belum mengirim halaman bayar. Cek status atau tunggu sampai tagihan kedaluwarsa.";
                break;
            default:
                detail = "Buka halaman bayar, selesaikan simulasi, lalu kembali dan tekan Cek Status.";
        }
        if (retried) detail += "\nPemeriksaan terbaru ke Duitku belum berhasil.";
        String url = payment.optString("payment_url", "");
        android.app.AlertDialog.Builder dialog = new TransivaAlertDialogBuilder(activity)
                .setTitle("QRIS Sandbox · " + service)
                .setMessage("Rp" + amount + "\n" + detail + "\n\nReferensi: " + id)
                .setNegativeButton("Tutup", null)
                .setNeutralButton("Cek Status", (d, w) -> refresh(activity, statusUrl, service, id, timeout, true));
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if ("pending".equals(status) && "https".equals(uri.getScheme())
                && ("sandbox.duitku.com".equals(host) || "app-sandbox.duitku.com".equals(host)
                || "api-sandbox.duitku.com".equals(host))
                && uri.getUserInfo() == null && uri.getPort() == -1) {
            dialog.setPositiveButton("Buka QRIS", (d, w) -> {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    // Keep the tagihan dialog available after the customer returns from the browser.
                    activity.getWindow().getDecorView().post(() -> show(activity, statusUrl, payment, timeout, false));
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(activity, "Browser tidak tersedia.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        dialog.show();
    }

    private static void refresh(Activity activity, String statusUrl, String service, String id, int timeout, boolean verify) {
        CustomerTransportSession owner = CustomerTransportSession.capture(activity);
        TransivaNetworkExecutor.execute(() -> {
            try {
                JSONObject request = new JSONObject()
                        .put("service", service).put("payment_id", id).put("check_provider", verify);
                JSONObject result = TransivaHttpRepository.postJson(activity, statusUrl, request, timeout, owner);
                JSONObject payment = result.optJSONObject("sandbox_payment");
                if (payment == null) throw new IllegalStateException(result.optString("message", "Status uji belum tersedia."));
                owner.check(activity);
                activity.runOnUiThread(() -> {
                    try {
                        owner.check(activity);
                        show(activity, statusUrl, payment, timeout, result.optBoolean("provider_check_pending"));
                    } catch (Exception ignored) { /* The signed-in account changed. */ }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed() && !CustomerAsyncError.isCancellation(error)) {
                        String message = error instanceof TransivaHttpRepository.ServerResponseException
                                || error instanceof IllegalStateException ? error.getMessage() : "Status uji belum bisa dibaca.";
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
