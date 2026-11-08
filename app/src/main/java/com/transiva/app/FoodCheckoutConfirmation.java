package com.transiva.app;

import android.app.Activity;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

final class FoodCheckoutConfirmation {
    interface OnConfirmed {
        void confirm(String fingerprint);
    }

    private FoodCheckoutConfirmation() {}

    static JSONObject quote(Activity activity, String url, JSONObject payload, int timeout) throws Exception {
        JSONObject request = new JSONObject(payload.toString()).put("quote_only",true);
        JSONObject response = TransivaHttpRepository.postJson(activity,url,request,timeout);
        JSONObject quote = response.optJSONObject("quote");
        if (!response.optBoolean("success") || quote == null || quote.optString("fingerprint").isEmpty()) {
            throw new IllegalStateException(response.optString("message","Harga pesanan belum tersedia."));
        }
        return quote;
    }

    static void show(Activity activity, JSONObject quote, boolean changed, OnConfirmed confirmed, Runnable cancel) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        StringBuilder summary = new StringBuilder();
        JSONArray items=quote.optJSONArray("items");
        if (items!=null) for (int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);
            if (item==null) continue;
            String option=item.optString("options");
            summary.append(item.optInt("qty")).append(" × ").append(item.optString("name"));
            if (!option.isEmpty()) summary.append(" · ").append(option);
            summary.append("  ").append(rupiah(item.optLong("subtotal"))).append('\n');
        }
        summary.append("\nSubtotal  ").append(rupiah(quote.optLong("food_total")))
                .append("\nOngkir  ").append(rupiah(quote.optLong("delivery_fee")));
        if (quote.optLong("voucher_discount")>0) summary.append("\nVoucher  −").append(rupiah(quote.optLong("voucher_discount")));
        if (quote.optLong("point_discount")>0) summary.append("\nPoin Transiva  −").append(rupiah(quote.optLong("point_discount")));
        long total=quote.optLong("total");
        summary.append("\n\nTotal ").append("balance".equals(quote.optString("payment_method"))?"Transiva Pay":"tunai")
                .append("  ").append(rupiah(total));
        ScrollView scroll=new ScrollView(activity);
        TextView content=new TextView(activity);
        int pad=(int)(20*activity.getResources().getDisplayMetrics().density);
        content.setPadding(pad,pad,pad,pad);
        content.setText(summary);
        scroll.addView(content);
        new TransivaAlertDialogBuilder(activity)
                .setTitle(changed?"Harga pesanan berubah":"Konfirmasi harga pesanan")
                .setView(scroll)
                .setNegativeButton("Kembali",(d,w)->cancel.run())
                .setOnCancelListener(d->cancel.run())
                .setPositiveButton("Pesan · "+rupiah(total),(d,w)->confirmed.confirm(quote.optString("fingerprint")))
                .show();
    }

    private static String rupiah(long value) {
        return "Rp"+NumberFormat.getIntegerInstance(new Locale("id","ID")).format(value);
    }
}
