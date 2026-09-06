package com.transiva.app;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Versioned knowledge base for Trans Asisten 3.0. Backward compatible with 2.x server JSON. */
public final class TransAssistantKnowledge {
    public static final class Entry {
        final String id,intent,title,answer,action,actionLabel;
        final List<String> keywords,strongKeywords,weakKeywords,negativeKeywords,questions;
        final int priority;
        final double minConfidence;
        Entry(String id,String i,String t,String a,String ac,String al,int priority,double minConfidence,
              List<String> strong,List<String> weak,List<String> negative,List<String> questions){
            this.id=id;intent=i;title=t;answer=a;action=ac;actionLabel=al;this.priority=priority;this.minConfidence=minConfidence;
            this.strongKeywords=strong;this.weakKeywords=weak;this.negativeKeywords=negative;this.questions=questions;
            ArrayList<String> all=new ArrayList<>(); all.addAll(strong); all.addAll(weak); all.addAll(questions); this.keywords=all;
        }
    }
    private static final String PREF="trans_assistant_kb", KEY_JSON="knowledge_json", KEY_VERSION="knowledge_version", KEY_SYNC="last_sync";
    private TransAssistantKnowledge(){}

    public static List<Entry> load(Context c){
        String raw=c.getSharedPreferences(PREF,0).getString(KEY_JSON,"");
        if(!raw.isEmpty())try{List<Entry> remote=parse(raw);if(!remote.isEmpty())return remote;}catch(Exception ignored){}
        return defaults();
    }
    public static void saveRemote(Context c,String raw){
        try{
            List<Entry> parsed=parse(raw); if(parsed.isEmpty())return;
            JSONObject root=new JSONObject(raw);
            String version=root.optString("version",root.optString("updated_at",String.valueOf(System.currentTimeMillis())));
            c.getSharedPreferences(PREF,0).edit().putString(KEY_JSON,raw).putString(KEY_VERSION,version).putLong(KEY_SYNC,System.currentTimeMillis()).apply();
        }catch(Exception ignored){}
    }
    public static long lastSync(Context c){return c.getSharedPreferences(PREF,0).getLong(KEY_SYNC,0L);}
    public static String version(Context c){return c.getSharedPreferences(PREF,0).getString(KEY_VERSION,"local");}

    private static List<Entry> parse(String raw)throws Exception{
        JSONArray arr=new JSONObject(raw).optJSONArray("entries"); List<Entry> out=new ArrayList<>(); if(arr==null)return out;
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.getJSONObject(i); if(o.has("active")&&!o.optBoolean("active",true))continue;
            List<String> strong=list(o,"strong_keywords"); List<String> weak=list(o,"weak_keywords"); List<String> negative=list(o,"negative_keywords"); List<String> questions=list(o,"questions");
            if(strong.isEmpty()&&weak.isEmpty()){ List<String> legacy=list(o,"keywords"); for(String s:legacy){if(s.trim().contains(" "))strong.add(s);else weak.add(s);} }
            String answer=o.optString("answer",""); JSONArray answers=o.optJSONArray("answers"); if(answer.isEmpty()&&answers!=null&&answers.length()>0)answer=answers.optString(0,"");
            out.add(new Entry(o.optString("id","kb_"+i),o.optString("intent","CUSTOM"),o.optString("title","Panduan"),answer,
                    o.optString("action",""),o.optString("action_label",""),o.optInt("priority",50),o.optDouble("min_confidence",0.58),strong,weak,negative,questions));
        }
        return out;
    }
    private static List<String> list(JSONObject o,String key){ArrayList<String>x=new ArrayList<>();JSONArray a=o.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i,"").trim();if(!s.isEmpty())x.add(s);}return x;}
    private static Entry e(String id,String intent,String title,String answer,String action,String label,int p,String[] strong,String[] weak,String[] negative,String[] questions){
        return new Entry(id,intent,title,answer,action,label,p,.58,new ArrayList<>(Arrays.asList(strong)),new ArrayList<>(Arrays.asList(weak)),new ArrayList<>(Arrays.asList(negative)),new ArrayList<>(Arrays.asList(questions)));
    }
    private static List<Entry> defaults(){ArrayList<Entry>x=new ArrayList<>();
        x.add(e("ride","ORDER_RIDE","TransRide","Untuk perjalanan motor, buka TransRide. Tentukan titik jemput dan tujuan, cek estimasi, lalu konfirmasi pesanan.","OPEN_RIDE","Buka TransRide",90,new String[]{"pesan ojek","pesan motor","ojek motor","mau naik motor","pulang kantor","berangkat kerja"},new String[]{"transride","ride","ojek","motor"},new String[]{"beli motor","jual motor","saldo driver"},new String[]{"bagaimana cara pesan ojek","saya mau pulang kantor"}));
        x.add(e("food","ORDER_FOOD","TransFood","Kalau Anda lapar, buka TransFood untuk memilih merchant dan menu. Cek alamat, total pembayaran, lalu konfirmasi.","OPEN_FOOD","Cari makanan",90,new String[]{"pesan makanan","mau makan","cari makanan","makan siang","makan malam"},new String[]{"transfood","makanan","lapar","food"},new String[]{"antar makanan merchant"},new String[]{"saya lapar","cara pesan makanan"}));
        x.add(e("shop","ORDER_SHOP","TransShop","Untuk membeli atau menitip belanja barang, gunakan TransShop. Pilih barang/toko, tentukan tujuan, lalu konfirmasi.","OPEN_SHOP","Buka TransShop",80,new String[]{"pesan barang","mau belanja","titip belanja","beli barang"},new String[]{"transshop","belanja","shop"},new String[]{"kirim barang yang sudah ada"},new String[]{"mau pesan barang"}));
        x.add(e("pickup","PICKUP","TransPickup","Jika barangnya sudah ada dan hanya perlu diambil atau dikirim, gunakan TransPickup. Isi lokasi pengambilan, tujuan, dan detail barang.","OPEN_PICKUP","Kirim barang",85,new String[]{"kirim barang","kirim paket","antar paket","ambil barang"},new String[]{"pickup","paket"},new String[]{"beli barang","belanja barang"},new String[]{"cara kirim paket"}));
        x.add(e("payment","PAYMENT","Pembayaran","Untuk isi saldo gunakan Top Up. Sebelum membuat order, periksa saldo dan metode pembayaran agar transaksi berjalan lancar.","OPEN_TOPUP","Buka Top Up",75,new String[]{"isi saldo","top up saldo","saldo kurang","tambah saldo"},new String[]{"saldo","bayar","pembayaran","topup"},new String[]{"saldo driver","saldo merchant"},new String[]{"cara top up","saldo saya kurang"}));
        x.add(e("status","ORDER_STATUS","Status pesanan","Buka Pusat Pesanan Aktif untuk melihat status terbaru. Jika ada order aktif, saya juga bisa membaca status terakhir yang tersimpan di aplikasi.","OPEN_ACTIVE_ORDER","Lihat pesanan aktif",95,new String[]{"status pesanan","status order","cek pesanan","driver di mana","pesanan saya di mana"},new String[]{"status","order","pesanan"},new String[]{"cara pesan","buat order"},new String[]{"driver saya di mana"}));
        x.add(e("chat","CHAT","Chat","Gunakan menu Pesan atau tombol chat pada pesanan aktif untuk menghubungi driver atau pihak terkait.","OPEN_CHAT","Buka Pesan",70,new String[]{"chat driver","hubungi driver","kirim pesan driver"},new String[]{"chat","pesan"},new String[]{},new String[]{"cara chat driver"}));
        x.add(e("account","ACCOUNT","Akun","Pengaturan profil, keamanan, dan preferensi aplikasi tersedia di menu Akun.","OPEN_PROFILE","Buka Akun",65,new String[]{"ubah profil","ubah nama","pengaturan akun"},new String[]{"akun","profil"},new String[]{},new String[]{"cara ubah nama"}));
        x.add(e("place","PLACE_SEARCH","Cari tempat","Gunakan pencarian Transiva untuk mencari merchant, layanan, atau tempat yang tersedia.","OPEN_SEARCH","Buka pencarian",70,new String[]{"cari tempat","cari merchant","tempat terdekat","cari toko"},new String[]{"lokasi","tempat","merchant"},new String[]{"lokasi saya mati"},new String[]{"cari tempat dekat saya"}));
        return x;
    }
}
