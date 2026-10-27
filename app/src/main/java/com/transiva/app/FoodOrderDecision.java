package com.transiva.app;

import org.json.JSONObject;
import java.util.Locale;

/** Presentation only: preserves raw merchant outcome before generic status normalization. */
final class FoodOrderDecision {
    static String value(JSONObject o,String key){if(o==null||o.isNull(key))return "";String s=o.optString(key,"").trim();return "null".equalsIgnoreCase(s)?"":s;}
    static String first(String... values){for(String s:values)if(s!=null&&!s.trim().isEmpty())return s.trim();return "";}
    static JSONObject order(JSONObject response){JSONObject o=response==null?null:response.optJSONObject("order");return o==null?new JSONObject():o;}
    static String status(JSONObject res){return first(value(order(res),"status"),value(res,"status")).toLowerCase(Locale.ROOT);}
    static String reason(JSONObject res){return first(value(order(res),"merchant_reject_reason"),value(res,"merchant_reject_reason"),value(order(res),"reject_reason"));}
    static boolean isFood(JSONObject res,String id){String type=first(value(order(res),"order_type"),value(res,"order_type")).toLowerCase(Locale.ROOT);return "food".equals(type)||"transfood".equals(type)||(id!=null&&id.toUpperCase(Locale.ROOT).startsWith("FOOD-"));}
    static boolean rejected(JSONObject res){String merchant=first(value(order(res),"merchant_status"),value(res,"merchant_status"));return "merchant_rejected".equals(status(res))||"merchant_rejected".equalsIgnoreCase(merchant)||(CustomerOrderState.isCanceled(status(res))&&!reason(res).isEmpty());}
    static boolean terminal(JSONObject res){return rejected(res)||CustomerOrderState.isCanceled(status(res));}
    static boolean needsFoodScreen(JSONObject res,String id){return isFood(res,id)&&(terminal(res)||(!CustomerOrderState.hasDriver(status(res))&&!CustomerOrderState.isFinished(status(res))));}
    static String category(String reason){
        String s=reason==null?"":reason.toLowerCase(Locale.ROOT);
        if(s.contains("tutup")||s.contains("closed")||s.contains("closing"))return "closed";
        if(s.contains("habis")||s.contains("sold out")||s.contains("stok kosong"))return "sold_out";
        if(s.contains("ramai")||s.contains("sibuk")||s.contains("penuh"))return "busy";
        return "unavailable";
    }
    static String title(JSONObject res){
        if(!rejected(res))return "Pesanan dibatalkan";
        switch(category(reason(res))){case "closed":return reason(res).toLowerCase(Locale.ROOT).contains("sudah")?"Toko sedang tutup":"Toko akan tutup";case "sold_out":return "Menu sedang habis";case "busy":return "Toko sedang ramai";default:return "Pesanan belum bisa dibuat";}
    }
    static String paymentMessage(JSONObject res){
        JSONObject order=order(res);
        // Only an authenticated server response may supply a refund confirmation.
        String state=value(order,"payment_status").toLowerCase(Locale.ROOT);
        String method=value(order,"payment_method").toLowerCase(Locale.ROOT);
        if("refunded".equals(state))return "Pembayaran sudah dikembalikan. Cek riwayat Transiva Pay untuk rinciannya.";
        if("cash".equals(method)||"tunai".equals(method))return "Kamu tidak perlu membayar pesanan ini.";
        if("balance".equals(method)||"saldo".equals(method)||"wallet".equals(method)||"transiva_pay".equals(method)||"transivapay".equals(method))return "Cek status pembayaran dan pengembalian dana di Transiva Pay.";
        return "";
    }
}
