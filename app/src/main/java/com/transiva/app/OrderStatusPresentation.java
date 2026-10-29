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
        String s = CustomerOrderState.normalize(rawStatus), t = n(rawType);
        if (isGoods(t)) {
            switch(s) {
                case "driver_accepted": return "Driver Menuju Lokasi Pengambilan";
                case "arrived_pickup": return "Driver Tiba di Lokasi Pengambilan";
                case "on_delivery": return "Barang Sedang Diantar";
                case "arrived_delivery": return "Barang Tiba di Tujuan";
                default: break;
            }
        }
        switch (s) {
            case "pending":
                return isFood(t) ? "Menunggu Konfirmasi Penjual" : "Sedang Mencari Driver";
            case "merchant_accepted":
                return "Penjual Menyiapkan Pesanan Anda";
            case "merchant_rejected":
                return "Penjual Belum Bisa Menerima";
            case "driver_accepted":
            case "accepted":
            case "assigned":
            case "driver_assigned":
            case "taken":
            case "taken_by_driver":
                return isFood(t) ? "Driver Mengambil Pesanan Anda" : "Driver Menuju Lokasi Jemput";
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

    public static String serviceType(JSONObject order) {
        if(order==null)return "";
        String key=CustomerHistoryPolicy.serviceKey(order);
        return "other".equals(key)?first(order.optString("order_type"),order.optString("service_type"),order.optString("service_name")):key;
    }

    public static String description(JSONObject o) {
        if (o == null) return "";
        String s = CustomerOrderState.normalize(o.optString("status"));
        String t = n(serviceType(o));
        String d = first(o.optString("driver_name"), o.optString("driver"),
                o.optString("driver_username"), "Driver");
        if(isGoods(t)) {
            switch(s) {
                case "driver_accepted": return d+" menuju lokasi pengambilan. Siapkan barang dan pastikan alamat benar.";
                case "arrived_pickup": return d+" sudah tiba. Siapkan penyerahan barang dan cocokkan identitas driver.";
                case "on_delivery": return d+" sedang mengantar barang. Pastikan penerima bisa dihubungi.";
                case "arrived_delivery": return d+" sudah tiba di tujuan. Minta penerima memeriksa barang dan tagihan pesanan.";
                default: break;
            }
        }
        switch (s) {
            case "pending":
                return isFood(t)
                        ? "Penjual sedang dihubungi. Tunggu konfirmasi dan pantau halaman pesanan Anda."
                        : "Sedang mencari driver di sekitar Anda. Pantau halaman ini; jika butuh bantuan, hubungi admin.";
            case "merchant_accepted":
                return "Penjual sedang menyiapkan pesanan. Pastikan alamat antar dan nomor Anda bisa dihubungi.";
            case "merchant_rejected":
                return "Penjual belum bisa menerima pesanan. Periksa rincian atau pilih penjual lain.";
            case "driver_accepted":
            case "accepted":
            case "assigned":
            case "driver_assigned":
            case "taken":
            case "taken_by_driver":
                return isFood(t)
                        ? d + " sedang menuju penjual. Pastikan alamat antar Anda benar."
                        : d + " sedang menuju titik jemput. Bersiaplah di lokasi; hubungi driver bila perlu.";
            case "arrived_pickup":
            case "arrived":
                return isFood(t)
                        ? d + " sudah tiba di penjual untuk mengambil pesanan. Anda dapat menunggu di alamat antar."
                        : d + " sudah tiba. Silakan menuju titik jemput dan cocokkan identitas driver.";
            case "picked_up":
            case "on_trip":
            case "on_delivery":
            case "in_progress":
            case "ongoing":
            case "started":
                return isFood(t)
                        ? d + " sedang mengantar pesanan. Bersiaplah menerima di alamat antar."
                        : d + " sedang mengantar Anda. Pantau tujuan perjalanan di peta.";
            case "arrived_delivery":
                return isFood(t)
                        ? d + " sudah tiba di alamat antar. Silakan terima dan periksa pesanan Anda."
                        : d + " sudah tiba di tujuan. Periksa barang bawaan dan tagihan pembayaran.";
            case "finished":
            case "finish":
            case "completed":
            case "complete":
            case "done":
                return "Pesanan selesai. Periksa ringkasan pembayaran di Pesanan; hubungi bantuan jika ada kendala.";
            case "canceled":
            case "cancelled":
            case "cancel":
                return "Pesanan dibatalkan. Periksa rincian pembayaran; Anda dapat memesan kembali atau menghubungi bantuan.";
            case "scheduled":
                return "Pesanan dijadwalkan. Periksa waktu, lokasi, dan perkembangan pesanan di halaman ini.";
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
    private static boolean isGoods(String t){return t.contains("shop")||t.contains("send")||t.contains("pickup")||t.contains("courier");}
    private static boolean isFood(String t){return t.contains("food");}
    private static String n(String v){return v==null?"":v.trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
    private static String first(String...v){for(String x:v)if(x!=null&&!x.trim().isEmpty()&&!"null".equalsIgnoreCase(x.trim()))return x.trim();return "";}
    private static String readable(String v){if(v.isEmpty())return "Status Sedang Diperbarui";StringBuilder b=new StringBuilder();for(String w:v.replace('_',' ').split("\\s+")){if(w.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(w.charAt(0)));if(w.length()>1)b.append(w.substring(1));}return b.toString();}
}
