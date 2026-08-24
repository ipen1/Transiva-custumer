package com.transiva.app;

import org.json.JSONObject;
import java.util.Locale;

/**
 * Satu sumber bahasa status order untuk sisi customer.
 * Nilai status database tidak diubah; class ini hanya mengubah cara status
 * tersebut ditampilkan agar lebih natural dan konsisten di seluruh UI.
 */
public final class OrderStatusPresentation {
    private OrderStatusPresentation() {}

    public static String label(String rawStatus, String rawType) {
        String s = n(rawStatus), t = n(rawType);
        switch (s) {
            case "pending":
                return isFood(t) ? "Menunggu Konfirmasi Merchant" : "Mencari Driver Terdekat";
            case "merchant_accepted":
                return "Merchant Menyiapkan Pesananmu";
            case "merchant_rejected":
                return "Merchant Belum Bisa Menerima";
            case "driver_accepted":
            case "accepted":
            case "assigned":
            case "driver_assigned":
            case "taken":
            case "taken_by_driver":
                return isFood(t) ? "Driver Mengambil Pesananmu" : "Driver Menerima Pesananmu";
            case "arrived_pickup":
            case "arrived":
                return isFood(t) ? "Driver Sudah Tiba di Merchant" : "Driver Sudah Tiba Menjemput";
            case "picked_up":
            case "on_trip":
            case "on_delivery":
            case "in_progress":
            case "ongoing":
            case "started":
                return isFood(t) ? "Pesananmu Sedang Diantar" : "Perjalanan Sedang Berlangsung";
            case "arrived_delivery":
                return isFood(t) ? "Pesanan Sudah Tiba" : "Driver Sudah Tiba di Tujuan";
            case "finished":
            case "finish":
            case "completed":
            case "complete":
            case "done":
                return "Pesanan Selesai 🎉";
            case "canceled":
            case "cancelled":
            case "cancel":
                return "Pesanan Dibatalkan";
            case "scheduled":
                return "Pesanan Terjadwal";
            default:
                return readable(s);
        }
    }

    public static String description(JSONObject o) {
        if (o == null) return "";
        String s = n(o.optString("status"));
        String t = n(first(o.optString("order_type"), o.optString("service_type"),
                o.optString("service"), o.optString("service_name")));
        String d = first(o.optString("driver_name"), o.optString("driver"),
                o.optString("driver_username"), "Driver");
        switch (s) {
            case "pending":
                return isFood(t)
                        ? "Merchant sedang kami hubungi untuk mengonfirmasi pesananmu"
                        : "Kami sedang mencarikan driver terdekat untukmu";
            case "merchant_accepted":
                return "Merchant sudah menerima dan sedang menyiapkan pesananmu";
            case "merchant_rejected":
                return "Merchant belum bisa menerima pesanan ini";
            case "driver_accepted":
            case "accepted":
            case "assigned":
            case "driver_assigned":
            case "taken":
            case "taken_by_driver":
                return isFood(t)
                        ? d + " menerima order dan sedang menuju merchant"
                        : d + " menerima pesananmu dan sedang menuju titik jemput";
            case "arrived_pickup":
            case "arrived":
                return isFood(t)
                        ? d + " sudah tiba di merchant untuk mengambil pesanan"
                        : d + " sudah tiba di titik penjemputan";
            case "picked_up":
            case "on_trip":
            case "on_delivery":
            case "in_progress":
            case "ongoing":
            case "started":
                return isFood(t)
                        ? d + " sedang mengantar pesanan ke lokasimu"
                        : d + " sedang mengantarmu menuju tujuan";
            case "arrived_delivery":
                return isFood(t)
                        ? d + " sudah tiba di lokasi pengantaran"
                        : d + " sudah tiba di tujuan";
            case "finished":
            case "finish":
            case "completed":
            case "complete":
            case "done":
                return "Perjalanan selesai. Terima kasih sudah Transivin bareng kami ✨";
            case "canceled":
            case "cancelled":
            case "cancel":
                return "Pesanan ini telah dibatalkan";
            case "scheduled":
                return "Pesananmu sudah dijadwalkan dan akan diproses sesuai waktu pilihanmu";
            default:
                return label(s, t);
        }
    }

    public static String textColor(String s){s=n(s); if(doneOrArrived(s))return "#07864B"; if(cancel(s))return "#C23636"; if(waiting(s))return "#B66A00"; return "#0B7CFF";}
    public static String backgroundColor(String s){s=n(s); if(doneOrArrived(s))return "#EAFBF2"; if(cancel(s))return "#FFF0F0"; if(waiting(s))return "#FFF7E5"; return "#EAF4FF";}
    public static String dotColor(String s){s=n(s); if(doneOrArrived(s))return "#14A867"; if(cancel(s))return "#E35353"; if(waiting(s))return "#F0A51A"; return "#0B7CFF";}

    private static boolean waiting(String s){return s.equals("pending")||s.equals("merchant_accepted")||s.equals("scheduled");}
    private static boolean cancel(String s){return s.equals("merchant_rejected")||s.equals("canceled")||s.equals("cancelled")||s.equals("cancel");}
    private static boolean doneOrArrived(String s){return s.equals("finished")||s.equals("finish")||s.equals("completed")||s.equals("complete")||s.equals("done")||s.equals("arrived_pickup")||s.equals("arrived_delivery");}
    private static boolean isFood(String t){return t.contains("food");}
    private static String n(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
    private static String first(String...v){for(String x:v)if(x!=null&&!x.trim().isEmpty()&&!"null".equalsIgnoreCase(x.trim()))return x.trim();return "";}
    private static String readable(String v){if(v.isEmpty())return "Status Sedang Diperbarui";StringBuilder b=new StringBuilder();for(String w:v.replace('_',' ').split("\\s+")){if(w.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(w.charAt(0)));if(w.length()>1)b.append(w.substring(1));}return b.toString();}
}
