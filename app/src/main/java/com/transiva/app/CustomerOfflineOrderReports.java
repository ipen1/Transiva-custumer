package com.transiva.app;

import android.app.Activity;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

final class CustomerOfflineOrderReports {
    private static final String URL = "https://transiva.my.id/server/offline_order_case_native.php";

    private CustomerOfflineOrderReports() {}

    static void open(Activity activity) {
        request(activity, null, response -> {
            if (!response.optBoolean("enabled", true)) {
                show(activity, "Laporan Perjalanan", response.optString("message"));
                return;
            }
            JSONArray cases = response.optJSONArray("items");
            if (cases == null || cases.length() == 0) {
                show(activity, "Laporan Perjalanan", "Belum ada perjalanan yang perlu dikonfirmasi.");
                return;
            }
            String[] labels = new String[cases.length()];
            for (int i = 0; i < cases.length(); i++) {
                JSONObject item = cases.optJSONObject(i);
                labels[i] = item.optString("service_type") + " · " + item.optString("order_code")
                        + "\n" + item.optString("created_at") + " · Kasus #" + item.optLong("id");
            }
            new TransivaAlertDialogBuilder(activity).setTitle("Laporan Perjalanan")
                    .setItems(labels, (dialog, index) -> select(activity, cases.optJSONObject(index)))
                    .setNegativeButton("Tutup", null).show();
        });
    }

    private static void select(Activity activity, JSONObject item) {
        if (!"review".equals(item.optString("state"))) {
            show(activity, "Laporan Perjalanan", "Kasus ini sudah ditinjau oleh Transiva.");
            return;
        }
        if (!item.isNull("customer_answer")) {
            show(activity, "Laporan Perjalanan", "Jawaban Anda sudah dikirim.");
            return;
        }
        new TransivaAlertDialogBuilder(activity)
                .setTitle("Apakah Anda tetap diantar oleh driver yang sama setelah pesanan dibatalkan?")
                .setItems(new String[]{"Ya", "Tidak", "Tidak yakin"}, (dialog, index) -> {
                    if (index == 0) {
                        EditText note = new EditText(activity);
                        note.setHint("Ceritakan singkat apa yang terjadi (opsional)");
                        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
                        new TransivaAlertDialogBuilder(activity).setTitle("Detail perjalanan")
                                .setMessage("Apakah driver meminta pembatalan atau menerima pembayaran langsung?")
                                .setView(note).setNegativeButton("Kembali", null)
                                .setPositiveButton("Kirim", (d, w) -> answer(activity, item, "yes", note.getText().toString()))
                                .show();
                    } else {
                        answer(activity, item, index == 1 ? "no" : "unsure", "");
                    }
                }).setNegativeButton("Nanti", null).show();
    }

    private static void answer(Activity activity, JSONObject item, String answer, String note) {
        JSONObject body = new JSONObject();
        try {
            body.put("action", "answer").put("id", item.optLong("id")).put("answer", answer).put("note", note);
        } catch (org.json.JSONException error) {
            show(activity, "Laporan Perjalanan", "Tanggapan belum dapat disiapkan.");
            return;
        }
        request(activity, body, result -> Toast.makeText(activity, "Terima kasih. Tanggapan tersimpan.", Toast.LENGTH_SHORT).show());
    }

    private interface Response { void accept(JSONObject result); }

    private static void request(Activity activity, JSONObject body, Response action) {
        CustomerSessionGuard owner = CustomerSessionGuard.capture(activity);
        TransivaNetworkExecutor.execute(() -> {
            try {
                if (!owner.matches(activity)) return;
                JSONObject result = body == null ? TransivaHttpRepository.getJsonFresh(activity, URL, 15000)
                        : TransivaHttpRepository.postJson(activity, URL, body, 15000);
                activity.runOnUiThread(() -> {
                    if (!owner.matches(activity) || activity.isFinishing() || activity.isDestroyed()) return;
                    if (result.optBoolean("success")) action.accept(result);
                    else show(activity, "Laporan Perjalanan", result.optString("message", "Coba kembali."));
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (owner.matches(activity)) show(activity, "Laporan Perjalanan", "Layanan laporan belum tersedia. Coba kembali.");
                });
            }
        }, () -> show(activity, "Laporan Perjalanan", "Sistem sedang sibuk. Coba kembali."));
    }

    private static void show(Activity activity, String title, String message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new TransivaAlertDialogBuilder(activity).setTitle(title).setMessage(message).setPositiveButton("Tutup", null).show();
    }
}
