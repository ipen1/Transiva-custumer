package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

public final class TransAssistantKnowledge {
    public static final class Entry {
        final String intent,title,answer,action,actionLabel; final List<String> keywords;
        Entry(String i,String t,String a,String ac,String al,String...k){intent=i;title=t;answer=a;action=ac;actionLabel=al;keywords=Arrays.asList(k);}
    }
    private static final String PREF="trans_assistant_kb";
    private static final String KEY_JSON="knowledge_json";
    private TransAssistantKnowledge(){}
    public static List<Entry> load(Context c){
        String raw=c.getSharedPreferences(PREF,0).getString(KEY_JSON,"");
        if(!raw.isEmpty()) try { List<Entry> remote=parse(raw); if(!remote.isEmpty()) return remote; } catch(Exception ignored){}
        return defaults();
    }
    public static void saveRemote(Context c,String raw){ try{ if(!parse(raw).isEmpty()) c.getSharedPreferences(PREF,0).edit().putString(KEY_JSON,raw).apply(); }catch(Exception ignored){} }
    private static List<Entry> parse(String raw)throws Exception{ JSONArray arr=new JSONObject(raw).optJSONArray("entries"); List<Entry> out=new ArrayList<>(); if(arr==null)return out; for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);JSONArray k=o.optJSONArray("keywords");List<String> ks=new ArrayList<>();if(k!=null)for(int j=0;j<k.length();j++)ks.add(k.optString(j));out.add(new Entry(o.optString("intent"),o.optString("title"),o.optString("answer"),o.optString("action"),o.optString("action_label"),ks.toArray(new String[0])));} return out; }
    private static List<Entry> defaults(){ List<Entry> x=new ArrayList<>();
        x.add(new Entry("ORDER_RIDE","TransRide","Untuk memesan motor: buka TransRide, tentukan titik jemput dan tujuan, periksa estimasi, lalu buat pesanan.","OPEN_RIDE","Buka TransRide","pesan motor","transride","ojek","motor","cara pesan ride"));
        x.add(new Entry("ORDER_FOOD","TransFood","Untuk memesan makanan: buka TransFood, pilih merchant dan menu, periksa alamat serta total pembayaran, lalu konfirmasi pesanan.","OPEN_FOOD","Buka TransFood","pesan makanan","transfood","makanan","food"));
        x.add(new Entry("ORDER_SHOP","TransShop","Untuk belanja kebutuhan, buka TransShop, pilih barang atau toko, isi tujuan pengantaran, lalu konfirmasi pesanan.","OPEN_SHOP","Buka TransShop","belanja","transshop","shop","pesan barang"));
        x.add(new Entry("PICKUP","TransPickup","Gunakan TransPickup untuk pengambilan atau pengantaran barang. Isi lokasi pengambilan, tujuan, detail barang, lalu buat pesanan.","OPEN_PICKUP","Buka TransPickup","antar barang","pickup","kirim barang","transpickup"));
        x.add(new Entry("CALL","Panggilan","Panggilan membutuhkan mikrofon dan, agar layar panggilan lebih mudah muncul saat aplikasi di latar belakang, izin tampil di atas aplikasi lain.","OPEN_OVERLAY","Periksa izin overlay","panggilan","telepon driver","call","tidak muncul panggilan","atas aplikasi"));
        x.add(new Entry("CHAT","Chat","Gunakan menu Chat atau tombol chat pada pesanan aktif untuk menghubungi driver atau pihak terkait.","OPEN_CHAT","Buka Chat","chat","pesan driver","hubungi driver"));
        x.add(new Entry("HISTORY","Riwayat","Riwayat pesanan menampilkan pesanan sebelumnya dan detail statusnya.","OPEN_HISTORY","Buka Riwayat","riwayat","pesanan lama","order sebelumnya"));
        x.add(new Entry("ACCOUNT","Akun","Pengaturan akun, profil, keamanan, dan preferensi aplikasi tersedia pada menu Akun/Pengaturan.","OPEN_PROFILE","Buka Akun","akun","profil","ubah nama","pengaturan akun"));
        x.add(new Entry("PAYMENT","Pembayaran","Periksa metode pembayaran dan saldo sebelum membuat pesanan. Untuk isi saldo, gunakan menu Top Up yang tersedia di aplikasi.","OPEN_TOPUP","Buka Top Up","bayar","pembayaran","saldo","top up","isi saldo"));
        x.add(new Entry("ORDER_STATUS","Status pesanan","Status pesanan aktif dapat dilihat dari Dashboard atau Riwayat. Trans Assistant tidak akan menebak status real-time bila datanya belum tersedia.","OPEN_HISTORY","Lihat pesanan","status pesanan","driver dimana","pesanan saya","order saya"));
        return x; }
}
