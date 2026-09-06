package com.transiva.app;

import android.content.Context;
import java.text.Normalizer;
import java.util.*;

/** Trans Asisten 2.1: local semantic-ish matcher + fuzzy phrase ranking + short context + runtime awareness. */
public final class TransAssistantEngine {
    public static final class Reply {
        public final String text, action, actionLabel;
        Reply(String text,String action,String actionLabel){this.text=text;this.action=action;this.actionLabel=actionLabel;}
    }
    private static final String PREF="trans_assistant_ctx", KEY_LAST="last_intent";
    private final Context context;
    public TransAssistantEngine(Context c){context=c.getApplicationContext();}

    public Reply answer(String raw){
        String q=norm(raw);
        if(q.isEmpty())return new Reply("Ceritakan kebutuhan Anda. Saya bisa membantu pemesanan, akun, pembayaran, panggilan, chat, status order, dan panduan lain yang ditambahkan admin.","","");

        Reply state=answerFromRuntime(q,TransAssistantRuntimeContext.read(context));
        if(state!=null)return state;

        List<TransAssistantKnowledge.Entry> entries=TransAssistantKnowledge.load(context); // selalu baca KB terbaru hasil sinkron admin
        TransAssistantKnowledge.Entry best=null; double bestScore=0;
        String previous=context.getSharedPreferences(PREF,0).getString(KEY_LAST,"");
        for(TransAssistantKnowledge.Entry e:entries){
            double s=score(q,e);
            if(!previous.isEmpty()&&e.intent.equals(previous)&&isFollowUp(q))s+=0.10;
            if(s>bestScore){bestScore=s;best=e;}
        }
        if(best!=null && bestScore>=0.36){
            context.getSharedPreferences(PREF,0).edit().putString(KEY_LAST,best.intent).apply();
            return new Reply(best.answer,best.action,best.actionLabel);
        }

        Reply lifestyle=lifestyleIntent(q); if(lifestyle!=null)return lifestyle;
        if(isGreeting(q)) return new Reply("Halo 👋 Saya Trans Asisten. Tanyakan cara memakai layanan Transiva atau kebutuhan Anda, misalnya ‘mau pulang kantor’, ‘cara kirim paket’, ‘saldo kurang’, atau pertanyaan panduan lainnya.","","");
        if(hasAny(q,"terima kasih","makasih","thanks")) return new Reply("Sama-sama. Kalau ada kebutuhan lain di Transiva, langsung tanyakan saja.","","");
        return new Reply("Saya belum menemukan jawaban yang paling cocok. Coba tulis lebih spesifik. Admin juga dapat menambahkan pertanyaan dan jawaban baru agar pengetahuan saya terus bertambah.","OPEN_HELP","Buka bantuan");
    }

    private Reply answerFromRuntime(String q,TransAssistantRuntimeContext s){
        if(hasAny(q,"internet","koneksi","offline","jaringan")&&!s.online)
            return new Reply("Perangkat sedang offline. Panduan lokal tetap bisa saya jawab, tetapi data merchant dan status terbaru membutuhkan internet.","","");
        if(hasAny(q,"gps","lokasi mati","lokasi tidak aktif","titik jemput tidak terbaca")&&!s.locationEnabled)
            return new Reply("GPS/Lokasi perangkat sedang tidak aktif. Aktifkan lokasi agar titik jemput dan pencarian driver lebih akurat.","OPEN_LOCATION_SETTINGS","Aktifkan lokasi");
        if(hasAny(q,"panggilan tidak muncul","telepon tidak muncul","call tidak muncul","overlay","atas aplikasi")&&!s.overlayAllowed)
            return new Reply("Izin Tampil di atas aplikasi lain belum aktif. Aktifkan izin ini agar layar panggilan lebih mudah muncul saat Transiva berada di latar belakang.","OPEN_OVERLAY","Aktifkan izin");
        // Jangan menangkap semua kata 'driver/order'; hanya pertanyaan yang benar-benar menanyakan status order aktif.
        if(isStatusQuestion(q)&&s.hasActiveOrder){
            String service=s.activeService.isEmpty()?"pesanan Transiva":s.activeService;
            String label=s.activeOrderStatus.isEmpty()?"sedang aktif":OrderStatusPresentation.label(s.activeOrderStatus,service);
            return new Reply("Anda memiliki "+service+" yang masih aktif. Status terakhir: "+label+". Buka pusat pesanan untuk posisi/status terbaru.","OPEN_ACTIVE_ORDER","Lihat pesanan aktif");
        }
        return null;
    }

    private boolean isStatusQuestion(String q){return hasAny(q,"status pesanan","status order","cek pesanan","cek order","driver dimana","driver di mana","pesanan saya dimana","pesanan saya di mana","order saya dimana","order saya di mana");}

    private Reply lifestyleIntent(String q){
        if(hasAny(q,"lapar","mau makan","makan siang","makan malam","cari makanan"))return new Reply("Sepertinya Anda ingin makanan. Buka TransFood untuk memilih merchant dan menu yang tersedia.","OPEN_FOOD","Cari makanan");
        if(hasAny(q,"pulang kantor","mau pulang","ke kantor","berangkat kerja","butuh kendaraan","pesan ojek","ojek motor"))return new Reply("Untuk perjalanan motor, gunakan TransRide. Tentukan titik jemput dan tujuan, lalu cek estimasi sebelum memesan.","OPEN_RIDE","Buka TransRide");
        if(hasAny(q,"pesan barang","belanja barang","butuh barang","mau belanja","titip belanja"))return new Reply("Jika ingin membeli barang, gunakan TransShop. Jika barangnya sudah ada dan hanya perlu dikirim, gunakan TransPickup.","OPEN_SHOP","Buka TransShop");
        if(hasAny(q,"kirim barang","antar paket","kirim paket","ambil barang"))return new Reply("Gunakan TransPickup untuk mengambil atau mengirim barang. Isi lokasi pengambilan, tujuan, dan detail barang.","OPEN_PICKUP","Kirim barang");
        return null;
    }

    private boolean isGreeting(String q){return q.equals("halo")||q.equals("hai")||q.equals("hi")||q.equals("pagi")||q.equals("siang")||q.equals("malam")||q.startsWith("selamat pagi")||q.startsWith("selamat siang")||q.startsWith("selamat malam");}
    private boolean isFollowUp(String q){return q.matches(".*\\b(lalu|setelah itu|terus|selanjutnya|bagaimana lagi|gimana lagi|itu)\\b.*")||q.length()<18;}

    private double score(String q,TransAssistantKnowledge.Entry e){
        if(e.answer==null||e.answer.trim().isEmpty())return 0;
        double best=0,total=0;
        for(String k:e.keywords){
            String nk=norm(k);if(nk.isEmpty())continue;
            double s=phraseScore(q,nk);best=Math.max(best,s);
            if(s>=0.72)total+=0.10;
        }
        String title=norm(e.title);
        if(!title.isEmpty()&&q.contains(title))total+=0.25;
        double priority=Math.max(0,Math.min(100,e.priority))/1000.0;
        return Math.min(1.0,best+Math.min(.20,total)+priority);
    }

    private double phraseScore(String q,String k){
        if(q.equals(k))return .92;
        if(q.contains(k))return k.contains(" ")?.80:.62;
        if(k.contains(q)&&q.length()>=5)return .68;
        String[] qa=q.split("\\s+"),ka=k.split("\\s+");
        Set<String> qs=new HashSet<>(Arrays.asList(qa)),ks=new HashSet<>(Arrays.asList(ka));
        int hit=0;for(String x:qs)if(ks.contains(x))hit++;
        double overlap=(double)hit/Math.max(1,Math.max(qs.size(),ks.size()));
        double fuzzy=tokenSimilarity(q,k);
        return overlap*.58+fuzzy*.22;
    }

    private static boolean hasAny(String q,String...terms){for(String t:terms)if(q.contains(norm(t)))return true;return false;}
    private static double tokenSimilarity(String a,String b){String[] aa=a.split("\\s+"),bb=b.split("\\s+");double sum=0;int n=0;for(String x:aa){double best=0;for(String y:bb)best=Math.max(best,similarity(x,y));if(best>.55){sum+=best;n++;}}return n==0?0:sum/Math.max(aa.length,bb.length);}
    private static double similarity(String a,String b){int d=lev(a,b),m=Math.max(a.length(),b.length());return m==0?1:1.0-((double)d/m);}
    private static int lev(String a,String b){int[] p=new int[b.length()+1];for(int j=0;j<p.length;j++)p[j]=j;for(int i=1;i<=a.length();i++){int[] n=new int[b.length()+1];n[0]=i;for(int j=1;j<=b.length();j++)n[j]=Math.min(Math.min(n[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));p=n;}return p[b.length()];}
    static String norm(String s){if(s==null)return"";String n=Normalizer.normalize(s.toLowerCase(new Locale("id","ID")),Normalizer.Form.NFD).replaceAll("\\p{M}","");return n.replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}
}
