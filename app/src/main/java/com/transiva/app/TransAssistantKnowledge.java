package com.transiva.app;

import android.content.Context;
import org.json.*;
import java.util.*;

public final class TransAssistantKnowledge {
    public static final class Entry {
        final String id,intent,title,answer,action,actionLabel;
        final List<String> keywords;
        final int priority;
        Entry(String id,String i,String t,String a,String ac,String al,int priority,String...k){
            this.id=id;intent=i;title=t;answer=a;action=ac;actionLabel=al;this.priority=priority;keywords=Arrays.asList(k);
        }
    }
    private static final String PREF="trans_assistant_kb";
    private static final String KEY_JSON="knowledge_json";
    private TransAssistantKnowledge(){}

    public static List<Entry> load(Context c){
        String raw=c.getSharedPreferences(PREF,0).getString(KEY_JSON,"");
        if(!raw.isEmpty()) try { List<Entry> remote=parse(raw); if(!remote.isEmpty()) return remote; } catch(Exception ignored){}
        return defaults();
    }

    public static void saveRemote(Context c,String raw){
        try{ if(!parse(raw).isEmpty()) c.getSharedPreferences(PREF,0).edit().putString(KEY_JSON,raw).apply(); }catch(Exception ignored){}
    }

    private static List<Entry> parse(String raw)throws Exception{
        JSONArray arr=new JSONObject(raw).optJSONArray("entries");
        List<Entry> out=new ArrayList<>(); if(arr==null)return out;
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.getJSONObject(i);
            if(o.has("active") && !o.optBoolean("active",true)) continue;
            JSONArray k=o.optJSONArray("keywords"); List<String> ks=new ArrayList<>();
            if(k!=null)for(int j=0;j<k.length();j++){String v=k.optString(j,"").trim();if(!v.isEmpty())ks.add(v);}
            JSONArray qs=o.optJSONArray("questions");
            if(qs!=null)for(int j=0;j<qs.length();j++){String v=qs.optString(j,"").trim();if(!v.isEmpty()&&!ks.contains(v))ks.add(v);}
            out.add(new Entry(o.optString("id","kb_"+i),o.optString("intent","CUSTOM"),o.optString("title","Panduan"),o.optString("answer"),o.optString("action"),o.optString("action_label"),o.optInt("priority",50),ks.toArray(new String[0])));
        }
        return out;
    }

    private static List<Entry> defaults(){ List<Entry> x=new ArrayList<>();
        x.add(new Entry("ride","ORDER_RIDE","TransRide","Untuk memesan ojek motor, buka TransRide, tentukan titik jemput dan tujuan, periksa estimasi, lalu buat pesanan.","OPEN_RIDE","Buka TransRide",80,"pesan motor","pesan ojek","ojek motor","transride","motor","cara pesan ride","pulang kantor","ke kantor","berangkat kerja"));
        x.add(new Entry("food","ORDER_FOOD","TransFood","Kalau ingin pesan makanan, buka TransFood, pilih merchant dan menu, cek alamat serta total pembayaran, lalu konfirmasi.","OPEN_FOOD","Buka TransFood",80,"pesan makanan","transfood","makanan","food","lapar","mau makan","makan siang","makan malam"));
        x.add(new Entry("shop","ORDER_SHOP","TransShop","Untuk membeli atau menitip belanja barang, buka TransShop, pilih toko/barang, tentukan tujuan pengantaran, lalu konfirmasi.","OPEN_SHOP","Buka TransShop",70,"belanja","transshop","shop","pesan barang","mau pesan barang","titip belanja"));
        x.add(new Entry("pickup","PICKUP","TransPickup","Kalau barangnya sudah ada dan hanya perlu diambil atau dikirim, gunakan TransPickup. Isi lokasi pengambilan, tujuan, dan detail barang.","OPEN_PICKUP","Buka TransPickup",80,"antar barang","pickup","kirim barang","kirim paket","antar paket","ambil barang"));
        x.add(new Entry("call","CALL","Panggilan","Panggilan membutuhkan izin mikrofon. Agar layar panggilan muncul saat aplikasi di latar belakang, aktifkan juga izin Tampil di atas aplikasi lain.","OPEN_OVERLAY","Periksa izin overlay",80,"panggilan","telepon driver","call","tidak muncul panggilan","atas aplikasi","overlay"));
        x.add(new Entry("chat","CHAT","Chat","Gunakan menu Pesan atau tombol chat pada pesanan aktif untuk menghubungi driver atau pihak terkait.","OPEN_CHAT","Buka Pesan",60,"chat","pesan driver","hubungi driver"));
        x.add(new Entry("history","HISTORY","Riwayat","Riwayat pesanan menampilkan pesanan sebelumnya beserta detail statusnya.","OPEN_HISTORY","Buka Riwayat",60,"riwayat","pesanan lama","order sebelumnya"));
        x.add(new Entry("account","ACCOUNT","Akun","Pengaturan profil, keamanan, dan preferensi aplikasi tersedia pada menu Akun.","OPEN_PROFILE","Buka Akun",50,"akun","profil","ubah nama","pengaturan akun"));
        x.add(new Entry("payment","PAYMENT","Pembayaran","Periksa metode pembayaran dan saldo sebelum membuat pesanan. Untuk isi saldo, gunakan menu Top Up.","OPEN_TOPUP","Buka Top Up",60,"bayar","pembayaran","saldo","top up","isi saldo"));
        x.add(new Entry("place","PLACE_SEARCH","Cari tempat","Untuk mencari merchant, layanan, atau tempat yang tersedia di Transiva, buka pencarian lalu ketik nama tempat atau kebutuhan Anda.","OPEN_SEARCH","Buka pencarian",65,"cari tempat","cari lokasi","cari toko","cari merchant","tempat terdekat"));
        x.add(new Entry("status","ORDER_STATUS","Status pesanan","Untuk status terbaru, buka Pusat Pesanan Aktif. Bila ada order aktif, Trans Asisten juga akan membaca status terakhir yang tersimpan di aplikasi.","OPEN_ACTIVE_ORDER","Lihat pesanan aktif",90,"status pesanan","driver dimana","pesanan saya","order saya","cek pesanan"));
        return x;
    }
}
