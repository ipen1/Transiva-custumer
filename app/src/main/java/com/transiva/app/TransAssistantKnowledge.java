package com.transiva.app;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Versioned knowledge base for Trans Asisten 3.1. Remote knowledge augments, never weakens, built-in knowledge. */
public final class TransAssistantKnowledge {
    public static final class Entry {
        final String id,intent,title,action,actionLabel;
        final List<String> answers,keywords,strongKeywords,weakKeywords,negativeKeywords,questions;
        final int priority;
        final double minConfidence;
        Entry(String id,String i,String t,List<String> answers,String ac,String al,int priority,double minConfidence,
              List<String> strong,List<String> weak,List<String> negative,List<String> questions){
            this.id=safe(id);intent=safe(i);title=safe(t);action=safe(ac);actionLabel=safe(al);this.priority=priority;this.minConfidence=clamp(minConfidence,.45,.90);
            this.answers=clean(answers);this.strongKeywords=clean(strong);this.weakKeywords=clean(weak);this.negativeKeywords=clean(negative);this.questions=clean(questions);
            ArrayList<String> all=new ArrayList<>();all.addAll(this.strongKeywords);all.addAll(this.weakKeywords);all.addAll(this.questions);this.keywords=Collections.unmodifiableList(all);
        }
        String primaryAnswer(){return answers.isEmpty()?"":answers.get(0);}
        String answerFor(String query){
            if(answers.isEmpty())return "";
            if(answers.size()==1)return answers.get(0);
            String q=query==null?"":query;
            int idx=(q.hashCode()&0x7fffffff)%answers.size();
            return answers.get(idx);
        }
    }
    private static final String PREF="trans_assistant_kb",KEY_JSON="knowledge_json",KEY_VERSION="knowledge_version",KEY_SYNC="last_sync";
    private TransAssistantKnowledge(){}

    public static List<Entry> load(Context c){
        List<Entry> built=defaults();
        String raw=c.getSharedPreferences(PREF,0).getString(KEY_JSON,"");
        if(raw.isEmpty())return built;
        try{
            List<Entry> remote=parse(raw);
            if(remote.isEmpty())return built;
            LinkedHashMap<String,Entry> merged=new LinkedHashMap<>();
            for(Entry e:built)merged.put(key(e),e);
            for(Entry e:remote)merged.put(key(e),e); // admin may deliberately override same id/intent+title
            return new ArrayList<>(merged.values());
        }catch(Throwable ignored){return built;}
    }
    private static String key(Entry e){return !e.id.isEmpty()?"id:"+e.id:"it:"+e.intent+"|"+e.title.toLowerCase(Locale.ROOT);}

    public static boolean saveRemote(Context c,String raw){
        try{
            List<Entry> parsed=parse(raw);if(parsed.isEmpty())return false;
            JSONObject root=new JSONObject(raw);
            String version=root.optString("version",root.optString("updated_at",String.valueOf(System.currentTimeMillis())));
            c.getSharedPreferences(PREF,0).edit().putString(KEY_JSON,raw).putString(KEY_VERSION,version).putLong(KEY_SYNC,System.currentTimeMillis()).apply();
            return true;
        }catch(Throwable ignored){return false;}
    }
    public static long lastSync(Context c){return c.getSharedPreferences(PREF,0).getLong(KEY_SYNC,0L);}
    public static String version(Context c){return c.getSharedPreferences(PREF,0).getString(KEY_VERSION,"local-3.1");}

    private static List<Entry> parse(String raw)throws Exception{
        JSONObject root=new JSONObject(raw);JSONArray arr=root.optJSONArray("entries");ArrayList<Entry> out=new ArrayList<>();if(arr==null)return out;
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i);if(o==null)continue;if(o.has("active")&&!o.optBoolean("active",true))continue;
            List<String> strong=list(o,"strong_keywords"),weak=list(o,"weak_keywords"),negative=list(o,"negative_keywords"),questions=list(o,"questions");
            if(strong.isEmpty()&&weak.isEmpty()){for(String s:list(o,"keywords")){if(s.contains(" "))strong.add(s);else weak.add(s);}}
            List<String> answers=list(o,"answers");String answer=o.optString("answer","").trim();if(!answer.isEmpty()&&!answers.contains(answer))answers.add(0,answer);
            if(answers.isEmpty())continue;
            out.add(new Entry(o.optString("id","kb_"+i),o.optString("intent","CUSTOM"),o.optString("title","Panduan"),answers,
                    o.optString("action",""),o.optString("action_label",""),o.optInt("priority",50),o.optDouble("min_confidence",.58),strong,weak,negative,questions));
        }
        return out;
    }
    private static List<String> list(JSONObject o,String key){ArrayList<String>x=new ArrayList<>();JSONArray a=o.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i,"").trim();if(!s.isEmpty()&&!x.contains(s))x.add(s);}return x;}
    private static Entry e(String id,String intent,String title,String[] answers,String action,String label,int p,String[] strong,String[] weak,String[] negative,String[] questions){
        return new Entry(id,intent,title,new ArrayList<>(Arrays.asList(answers)),action,label,p,.58,new ArrayList<>(Arrays.asList(strong)),new ArrayList<>(Arrays.asList(weak)),new ArrayList<>(Arrays.asList(negative)),new ArrayList<>(Arrays.asList(questions)));
    }
    private static List<Entry> defaults(){ArrayList<Entry>x=new ArrayList<>();
        x.add(e("ride","ORDER_RIDE","TransRide",new String[]{"Untuk perjalanan motor, buka TransRide. Tentukan titik jemput dan tujuan, cek estimasi, lalu konfirmasi pesanan.","Siap. Untuk naik ojek motor, gunakan TransRide lalu isi titik jemput dan tujuan Anda."},"OPEN_RIDE","Buka TransRide",90,new String[]{"pesan ojek","pesan motor","ojek motor","mau naik motor","pulang kantor","berangkat kerja"},new String[]{"transride","ride","ojek","motor"},new String[]{"beli motor","jual motor","saldo driver"},new String[]{"bagaimana cara pesan ojek","saya mau pulang kantor"}));
        x.add(e("food","ORDER_FOOD","TransFood",new String[]{"Kalau Anda lapar, buka TransFood untuk memilih merchant dan menu. Cek alamat, total pembayaran, lalu konfirmasi.","Untuk pesan makanan, buka TransFood lalu pilih merchant dan menu yang tersedia."},"OPEN_FOOD","Cari makanan",90,new String[]{"pesan makanan","mau makan","ingin makan","cari makanan","makan siang","makan malam"},new String[]{"transfood","makanan","lapar","food"},new String[]{"antar makanan merchant"},new String[]{"saya lapar","cara pesan makanan"}));
        x.add(e("shop","ORDER_SHOP","TransShop",new String[]{"Untuk membeli atau menitip belanja barang, gunakan TransShop. Pilih barang atau toko, tentukan tujuan, lalu konfirmasi."},"OPEN_SHOP","Buka TransShop",80,new String[]{"pesan barang","mau belanja","titip belanja","beli barang"},new String[]{"transshop","belanja","shop"},new String[]{"kirim barang yang sudah ada"},new String[]{"mau pesan barang"}));
        x.add(e("pickup","PICKUP","TransPickup",new String[]{"Jika barangnya sudah ada dan hanya perlu diambil atau dikirim, gunakan TransPickup. Isi lokasi pengambilan, tujuan, dan detail barang."},"OPEN_PICKUP","Kirim barang",85,new String[]{"kirim barang","kirim paket","antar paket","ambil barang"},new String[]{"pickup","paket"},new String[]{"beli barang","belanja barang"},new String[]{"cara kirim paket"}));
        x.add(e("payment","PAYMENT","Pembayaran",new String[]{"Untuk isi saldo gunakan Top Up. Sebelum membuat order, periksa saldo dan metode pembayaran agar transaksi berjalan lancar."},"OPEN_TOPUP","Buka Top Up",75,new String[]{"isi saldo","top up saldo","saldo kurang","tambah saldo"},new String[]{"saldo","bayar","pembayaran","topup"},new String[]{"saldo driver","saldo merchant"},new String[]{"cara top up","saldo saya kurang"}));
        x.add(e("status","ORDER_STATUS","Status pesanan",new String[]{"Buka Pusat Pesanan Aktif untuk melihat status terbaru. Jika ada order aktif, saya juga bisa membaca status terakhir yang tersimpan di aplikasi."},"OPEN_ACTIVE_ORDER","Lihat pesanan aktif",95,new String[]{"status pesanan","status order","cek pesanan","cek order","driver di mana","pesanan saya di mana","order saya di mana"},new String[]{"status","order","pesanan"},new String[]{"cara pesan","buat order"},new String[]{"driver saya di mana"}));
        x.add(e("chat","CHAT","Chat",new String[]{"Gunakan menu Pesan atau tombol chat pada pesanan aktif untuk menghubungi driver atau pihak terkait."},"OPEN_CHAT","Buka Pesan",70,new String[]{"chat driver","hubungi driver","kirim pesan driver"},new String[]{"chat","pesan"},new String[]{},new String[]{"cara chat driver"}));
        x.add(e("account","ACCOUNT","Akun",new String[]{"Pengaturan profil, keamanan, dan preferensi aplikasi tersedia di menu Akun."},"OPEN_PROFILE","Buka Akun",65,new String[]{"ubah profil","ubah nama","pengaturan akun"},new String[]{"akun","profil"},new String[]{},new String[]{"cara ubah nama"}));
        x.add(e("place","PLACE_SEARCH","Cari tempat",new String[]{"Gunakan pencarian Transiva untuk mencari merchant, layanan, atau tempat yang tersedia."},"OPEN_SEARCH","Buka pencarian",70,new String[]{"cari tempat","cari merchant","tempat terdekat","cari toko"},new String[]{"lokasi","tempat","merchant"},new String[]{"lokasi saya mati"},new String[]{"cari tempat dekat saya"}));
        return x;
    }
    private static List<String> clean(List<String> in){ArrayList<String>x=new ArrayList<>();if(in!=null)for(String s:in){s=safe(s);if(!s.isEmpty()&&!x.contains(s))x.add(s);}return Collections.unmodifiableList(x);}
    private static String safe(String s){return s==null?"":s.trim();}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
}
