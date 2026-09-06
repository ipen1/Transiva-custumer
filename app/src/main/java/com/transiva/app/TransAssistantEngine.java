package com.transiva.app;

import android.content.Context;
import java.text.Normalizer;
import java.util.*;

/** Trans Asisten 2.0: local intent + fuzzy matching + short context + live app/device awareness. */
public final class TransAssistantEngine {
    public static final class Reply {
        public final String text;
        public final String action;
        public final String actionLabel;
        Reply(String text, String action, String actionLabel) { this.text=text; this.action=action; this.actionLabel=actionLabel; }
    }
    private static final String PREF="trans_assistant_ctx";
    private static final String KEY_LAST="last_intent";
    private final Context context;
    private final List<TransAssistantKnowledge.Entry> entries;
    public TransAssistantEngine(Context c){ context=c.getApplicationContext(); entries=TransAssistantKnowledge.load(context); }

    public Reply answer(String raw){
        String q=norm(raw);
        if(q.isEmpty()) return new Reply("Tanyakan kebutuhan Anda, misalnya mau pesan barang, pulang kantor, lapar, atau cek pesanan.","","");

        Reply state=answerFromRuntime(q, TransAssistantRuntimeContext.read(context));
        if(state!=null) return state;

        TransAssistantKnowledge.Entry best=null; double bestScore=0;
        String previous=context.getSharedPreferences(PREF,0).getString(KEY_LAST,"");
        for(TransAssistantKnowledge.Entry e: entries){
            double s=score(q,e);
            if(!previous.isEmpty() && e.intent.equals(previous) && isFollowUp(q)) s+=0.22;
            if(s>bestScore){bestScore=s;best=e;}
        }
        if(best==null || bestScore<0.31){
            Reply lifestyle=lifestyleIntent(q);
            if(lifestyle!=null) return lifestyle;
            return new Reply("Saya belum yakin maksudnya. Coba tulis seperti ‘lapar’, ‘mau pulang kantor’, ‘pesan barang’, ‘cek pesanan’, atau ‘panggilan tidak muncul’.","OPEN_HELP","Lihat bantuan");
        }
        context.getSharedPreferences(PREF,0).edit().putString(KEY_LAST,best.intent).apply();
        return new Reply(best.answer,best.action,best.actionLabel);
    }

    private Reply answerFromRuntime(String q, TransAssistantRuntimeContext s){
        if((q.contains("internet")||q.contains("koneksi")||q.contains("offline")||q.contains("jaringan")) && !s.online)
            return new Reply("Perangkat sedang tidak terhubung ke internet. Trans Asisten lokal tetap bisa membantu panduan, tetapi pencarian merchant dan data pesanan terbaru memerlukan koneksi.","","");
        if((q.contains("gps")||q.contains("lokasi")||q.contains("titik jemput")) && !s.locationEnabled)
            return new Reply("Lokasi/GPS perangkat sedang tidak aktif. Aktifkan lokasi agar titik jemput dan pencarian driver bekerja lebih akurat.","OPEN_LOCATION_SETTINGS","Aktifkan lokasi");
        if((q.contains("panggilan")||q.contains("telepon")||q.contains("call")||q.contains("atas aplikasi")||q.contains("overlay")) && !s.overlayAllowed)
            return new Reply("Izin ‘Tampil di atas aplikasi lain’ belum aktif. Izin ini membantu layar panggilan Transiva muncul ketika aplikasi berada di latar belakang.","OPEN_OVERLAY","Aktifkan izin");
        if((q.contains("pesanan")||q.contains("order")||q.contains("driver")||q.contains("dimana")||q.contains("mana")) && s.hasActiveOrder){
            String service=s.activeService.isEmpty()?"pesanan Transiva":s.activeService;
            String label=s.activeOrderStatus.isEmpty()?"sedang aktif":OrderStatusPresentation.label(s.activeOrderStatus, service);
            return new Reply("Anda memiliki "+service+" yang masih aktif. Status terakhir di aplikasi: "+label+". Buka pusat pesanan untuk melihat posisi/status terbaru.","OPEN_ACTIVE_ORDER","Lihat pesanan aktif");
        }
        return null;
    }

    private Reply lifestyleIntent(String q){
        if(hasAny(q,"lapar","mau makan","makan siang","makan malam","cari makanan"))
            return new Reply("Kalau sedang lapar, TransFood paling cocok. Pilih merchant dan menu lalu tentukan alamat pengantaran.","OPEN_FOOD","Cari makanan");
        if(hasAny(q,"pulang","pulang kantor","ke kantor","berangkat kerja","mau pergi","butuh kendaraan"))
            return new Reply("Untuk perjalanan, saya bisa arahkan ke TransRide untuk motor atau TransCar untuk mobil. Untuk kebutuhan cepat seperti pulang kantor, TransRide bisa jadi pilihan awal.","OPEN_RIDE","Pesan perjalanan");
        if(hasAny(q,"pesan barang","belanja barang","butuh barang","mau belanja","titip belanja"))
            return new Reply("Untuk membeli atau menitip belanja barang, gunakan TransShop. Kalau barangnya sudah ada dan hanya perlu dikirim, gunakan TransSend/TransPickup.","OPEN_SHOP","Buka TransShop");
        if(hasAny(q,"kirim barang","antar paket","kirim paket","ambil barang"))
            return new Reply("Untuk mengambil atau mengirim barang, gunakan TransSend/TransPickup dan isi lokasi pengambilan serta tujuan.","OPEN_PICKUP","Kirim barang");
        return null;
    }

    private boolean isFollowUp(String q){ return q.matches(".*\\b(lalu|setelah itu|terus|selanjutnya|bagaimana lagi|gimana lagi|itu)\\b.*") || q.length()<22; }
    private double score(String q, TransAssistantKnowledge.Entry e){ double total=0; for(String k:e.keywords){String nk=norm(k);if(q.contains(nk))total+=nk.contains(" ")?0.34:0.18;else total+=tokenSimilarity(q,nk)*0.12;}if(q.contains(norm(e.title)))total+=0.35;return Math.min(1.0,total); }
    private static boolean hasAny(String q,String... terms){for(String t:terms)if(q.contains(norm(t)))return true;return false;}
    private static double tokenSimilarity(String a,String b){String[] aa=a.split("\\s+"),bb=b.split("\\s+");double best=0;for(String x:aa)for(String y:bb)best=Math.max(best,similarity(x,y));return best;}
    private static double similarity(String a,String b){int d=lev(a,b),m=Math.max(a.length(),b.length());return m==0?1:1.0-((double)d/m);}
    private static int lev(String a,String b){int[] p=new int[b.length()+1];for(int j=0;j<p.length;j++)p[j]=j;for(int i=1;i<=a.length();i++){int[] n=new int[b.length()+1];n[0]=i;for(int j=1;j<=b.length();j++)n[j]=Math.min(Math.min(n[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));p=n;}return p[b.length()];}
    static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s.toLowerCase(new Locale("id","ID")),Normalizer.Form.NFD).replaceAll("\\p{M}","");return n.replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}
}
