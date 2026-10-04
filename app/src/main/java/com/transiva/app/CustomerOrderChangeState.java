package com.transiva.app;
import org.json.JSONObject;
import java.util.Locale;
/** Shared authoritative proposal state for activity, detail and tracking. */
final class CustomerOrderChangeState {
    static JSONObject extension(JSONObject o) { return o.optJSONObject("destination_extension"); }
    static boolean pending(JSONObject o) { JSONObject e=extension(o); return "pending".equalsIgnoreCase(o.optString("price_change_status")) || (e!=null && "pending".equals(e.optString("status"))); }
    static boolean travelling(JSONObject o) { JSONObject e=extension(o); return e!=null && "approved".equals(e.optString("status")) && e.optInt("needs_arrival")==1; }
    static boolean canReceive(JSONObject o) { return !pending(o) && !travelling(o) && "arrived_delivery".equalsIgnoreCase(o.optString("status")) && o.optInt("customer_received")==0; }
    static String money(double n) { return "Rp"+java.text.NumberFormat.getNumberInstance(new Locale("id","ID")).format(Math.round(n)); }
    static String summary(JSONObject o) {
        JSONObject e=extension(o);
        if(travelling(o)) return "Menuju tujuan tambahan: "+e.optString("address")+"\nKonfirmasi diterima tersedia setelah driver tiba.";
        if(!pending(o)) return "";
        double total=o.optDouble("price_change_requested",o.optDouble("price"));
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
        JSONObject e=extension(o);
        if(e!=null && "pending".equals(e.optString("status"))) p.put("destination_request_id",e.optLong("id"));
        if("approve_price".equals(action)) p.put("expected_price",o.optDouble("price_change_requested"));
    }
    static String error(Exception e) { return e instanceof TransivaHttpRepository.ServerResponseException ? e.getMessage() : "Koneksi server bermasalah. Silakan coba kembali."; }
}
