package com.transiva.app;
import org.json.JSONObject;
import java.util.Locale;
/** Shared authoritative proposal state for activity, detail and tracking. */
final class CustomerOrderChangeState {
    static JSONObject extension(JSONObject o) { return o.optJSONObject("destination_extension"); }
    static boolean pending(JSONObject o) { JSONObject e=extension(o); return "pending".equalsIgnoreCase(o.optString("price_change_status")) || (e!=null && "pending".equals(e.optString("status"))); }
    static boolean travelling(JSONObject o) { JSONObject e=extension(o); return e!=null && "approved".equals(e.optString("status")) && e.optInt("needs_arrival")==1; }
    static boolean canReceive(JSONObject o) { return !pending(o) && !travelling(o) && "arrived_delivery".equalsIgnoreCase(o.optString("status")) && o.optInt("customer_received")==0; }
    static JSONObject receiptPayload(JSONObject order) throws org.json.JSONException {
        return new JSONObject()
                .put("order_id", order.optString("order_id").isEmpty()?order.optString("id"):order.optString("order_id"))
                .put("source", order.optString("source").contains("pickup") ? "pickup_orders" : "orders")
                .put("action", "confirm_received");
    }
    static void confirmReceipt(android.app.Activity activity, Runnable proceed) {
        new TransivaAlertDialogBuilder(activity)
                .setTitle("Terima pesanan ini?")
                .setMessage("Pastikan pesanan sudah Anda terima dengan baik sebelum melakukan konfirmasi.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Ya, sudah diterima", (dialog, which) -> proceed.run())
                .show();
    }
    static String money(double n) { return "Rp"+java.text.NumberFormat.getNumberInstance(new Locale("id","ID")).format(Math.round(n)); }
    private static final java.util.regex.Pattern LOCATION = java.util.regex.Pattern.compile("^\\[Lokasi #(\\d+)\\]\\s*");

    static long locationRequest(JSONObject order) {
        java.util.regex.Matcher match=LOCATION.matcher(order.optString("price_change_reason"));
        return pending(order) && match.find()?Long.parseLong(match.group(1)):0;
    }

    static String summary(JSONObject o) {
        JSONObject e=extension(o);
        if(travelling(o)) return "Menuju tujuan tambahan: "+e.optString("address")+"\nKonfirmasi diterima tersedia setelah driver tiba.";
        if(!pending(o)) return "";
        double total=o.optDouble("price_change_requested",o.optDouble("price"));
        long location=locationRequest(o);
        if (location>0) {
            String point=LOCATION.matcher(o.optString("price_change_reason")).replaceFirst("");
            return (point.startsWith("Singgah:")?"Driver mengusulkan titik singgah.":"Lokasi belum tepat. Driver membantumu menyesuaikan. Apakah sudah benar?")
                    +"\n"+point+"\nSebelumnya "+money(o.optDouble("price"))+" · Total baru "+money(total);
        }
        String s="Pengajuan driver • menunggu keputusan Anda";
        if(e!=null && "pending".equals(e.optString("status"))) {
            s+="\nTujuan: "+e.optString("address");
            JSONObject q=e.optJSONObject("pricing_summary");
            if(q!=null) s+=String.format(new Locale("id","ID"),"\nJarak %.2f + %.2f = %.2f km",q.optDouble("previous_distance_km"),q.optDouble("additional_distance_km"),q.optDouble("total_distance_km"));
            else s+=String.format(new Locale("id","ID"),"\nTambahan jarak %.2f km",e.optDouble("distance_km"));
            s+="\nTarif minimum dihitung sekali.";
        } else if(!o.optString("price_change_reason").isEmpty()) s+="\n"+o.optString("price_change_reason");
        return s+"\nTambahan "+money(Math.max(0,total-o.optDouble("price")))+" • Total "+money(total);
    }
    static void payload(JSONObject o,JSONObject p,String action) throws org.json.JSONException {
        long location=locationRequest(o);
        if(location>0)p.put("location_request_id",location);
        JSONObject e=extension(o);
        if(e!=null && "pending".equals(e.optString("status"))) p.put("destination_request_id",e.optLong("id"));
        if("approve_price".equals(action)) p.put("expected_price",o.optDouble("price_change_requested"));
    }
    static String error(Exception e) { return e instanceof TransivaHttpRepository.ServerResponseException ? e.getMessage() : "Koneksi server bermasalah. Silakan coba kembali."; }
}
