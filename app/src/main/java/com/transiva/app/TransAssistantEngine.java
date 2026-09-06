package com.transiva.app;

import android.content.Context;
import java.text.Normalizer;
import java.util.*;

/** Trans Asisten 3.0: local hybrid intent engine, Indonesian slang normalization, confidence, negatives and multi-turn context. */
public final class TransAssistantEngine {
    public static final class Reply {
        public final String text,action,actionLabel,intent; public final double confidence; public final boolean needsConfirmation;
        Reply(String t,String a,String l,String i,double c,boolean confirm){text=t;action=a;actionLabel=l;intent=i;confidence=c;needsConfirmation=confirm;}
    }
    private static final double DIRECT=0.72, CONFIRM=0.52;
    private final Context context;
    private static final Map<String,String> SLANG=new HashMap<>();
    static{String[][]x={{"gmn","bagaimana"},{"gimana","bagaimana"},{"kmn","kemana"},{"mkn","makan"},{"psn","pesan"},{"sy","saya"},{"tdk","tidak"},{"gak","tidak"},{"ga","tidak"},{"nggak","tidak"},{"ngga","tidak"},{"mo","mau"},{"pgn","ingin"},{"pengen","ingin"},{"ojol","ojek"},{"krm","kirim"},{"brg","barang"},{"dmn","dimana"},{"d mana","dimana"},{"blm","belum"},{"udh","sudah"},{"sdh","sudah"},{"trims","terima kasih"},{"mksh","terima kasih"}};for(String[]p:x)SLANG.put(p[0],p[1]);}
    public TransAssistantEngine(Context c){context=c.getApplicationContext();}

    public Reply answer(String raw){
        String q=smartNorm(raw); if(q.isEmpty())return r("Ceritakan kebutuhan Anda. Saya bisa membantu perjalanan, makanan, pengiriman, pembayaran, akun, chat, dan status pesanan.","","","EMPTY",1,false);
        TransAssistantMemory.add(context,"user",raw,"");
        Reply runtime=answerFromRuntime(q,TransAssistantRuntimeContext.read(context)); if(runtime!=null){remember(runtime);return runtime;}
        if(isGreeting(q)){Reply z=r("Halo 👋 Saya Trans Asisten 3.0. Saya memahami bahasa sehari-hari seperti ‘mau pulang’, ‘lapar’, ‘saldo kurang’, atau ‘driver di mana’.","","","GREETING",.99,false);remember(z);return z;}
        if(hasAny(q,"terima kasih","makasih","thanks")){Reply z=r("Sama-sama. Saya siap membantu kebutuhan Transiva berikutnya.","","","THANKS",.99,false);remember(z);return z;}

        String previous=TransAssistantMemory.lastIntent(context); String recent=smartNorm(TransAssistantMemory.recentUserText(context));
        List<TransAssistantKnowledge.Entry> entries=TransAssistantKnowledge.load(context); Ranked best=null,second=null;
        for(TransAssistantKnowledge.Entry e:entries){double s=score(q,e); if(!previous.isEmpty()&&previous.equals(e.intent)&&isFollowUp(q))s+=.13; if(isVeryShortFollowUp(q)&&!recent.isEmpty())s=Math.max(s,score(recent+" "+q,e)-.04); s=Math.min(1,s); Ranked z=new Ranked(e,s); if(best==null||z.score>best.score){second=best;best=z;}else if(second==null||z.score>second.score)second=z;}
        Reply lifestyle=lifestyleIntent(q); if(lifestyle!=null && (best==null||best.score<.70)){remember(lifestyle);return lifestyle;}
        if(best!=null){double threshold=Math.max(CONFIRM,best.entry.minConfidence); boolean ambiguous=second!=null && best.score-second.score<.08;
            if(best.score>=DIRECT && !ambiguous){Reply z=r(best.entry.answer,best.entry.action,best.entry.actionLabel,best.entry.intent,best.score,false);remember(z);return z;}
            if(best.score>=threshold){String t="Sepertinya Anda menanyakan "+best.entry.title+". "+best.entry.answer;Reply z=r(t,best.entry.action,best.entry.actionLabel,best.entry.intent,best.score,true);remember(z);return z;}
        }
        Reply z=r("Saya belum yakin dengan maksud Anda. Coba tulis sedikit lebih spesifik, misalnya ‘pesan ojek’, ‘kirim paket’, ‘saldo kurang’, atau ‘cek pesanan’. Pertanyaan yang belum saya pahami juga bisa ditambahkan admin ke pengetahuan saya.","OPEN_HELP","Buka bantuan","UNKNOWN",best==null?0:best.score,false);remember(z);return z;
    }

    private void remember(Reply x){TransAssistantMemory.add(context,"assistant",x.text,x.intent);}
    private Reply answerFromRuntime(String q,TransAssistantRuntimeContext s){
        if(hasAny(q,"internet","koneksi","offline","jaringan")&&!s.online)return r("Perangkat sedang offline. Panduan lokal tetap tersedia, tetapi data merchant dan status terbaru membutuhkan internet.","","","DEVICE_NETWORK",1,false);
        if(hasAny(q,"gps","lokasi mati","lokasi tidak aktif","titik jemput tidak terbaca")&&!s.locationEnabled)return r("Lokasi perangkat sedang tidak aktif. Aktifkan GPS agar titik jemput dan pencarian driver lebih akurat.","OPEN_LOCATION_SETTINGS","Aktifkan lokasi","DEVICE_LOCATION",1,false);
        if(hasAny(q,"panggilan tidak muncul","telepon tidak muncul","call tidak muncul","overlay","atas aplikasi")&&!s.overlayAllowed)return r("Izin Tampil di atas aplikasi lain belum aktif. Aktifkan izin ini agar layar panggilan lebih mudah muncul saat Transiva berada di latar belakang.","OPEN_OVERLAY","Aktifkan izin","DEVICE_OVERLAY",1,false);
        if(isStatusQuestion(q)&&s.hasActiveOrder){String service=s.activeService.isEmpty()?"pesanan Transiva":s.activeService;String label=s.activeOrderStatus.isEmpty()?"sedang aktif":OrderStatusPresentation.label(s.activeOrderStatus,service);return r("Anda memiliki "+service+" yang masih aktif. Status terakhir: "+label+".","OPEN_ACTIVE_ORDER","Lihat pesanan aktif","ORDER_STATUS",1,false);}return null;
    }
    private boolean isStatusQuestion(String q){return hasAny(q,"status pesanan","status order","cek pesanan","cek order","driver dimana","driver di mana","pesanan saya dimana","order saya dimana");}
    private Reply lifestyleIntent(String q){
        if(hasAny(q,"lapar","mau makan","ingin makan","cari makanan"))return r("Sepertinya Anda ingin makanan. Buka TransFood untuk memilih merchant dan menu yang tersedia.","OPEN_FOOD","Cari makanan","ORDER_FOOD",.88,false);
        if(hasAny(q,"pulang kantor","mau pulang","ke kantor","berangkat kerja","butuh kendaraan","pesan ojek"))return r("Untuk perjalanan motor, gunakan TransRide. Tentukan titik jemput dan tujuan, lalu cek estimasi sebelum memesan.","OPEN_RIDE","Buka TransRide","ORDER_RIDE",.88,false);
        if(hasAny(q,"kirim barang","antar paket","kirim paket","ambil barang"))return r("Gunakan TransPickup untuk mengambil atau mengirim barang. Isi lokasi pengambilan, tujuan, dan detail barang.","OPEN_PICKUP","Kirim barang","PICKUP",.88,false);
        if(hasAny(q,"pesan barang","belanja barang","titip belanja"))return r("Untuk membeli barang gunakan TransShop. Jika barang sudah ada dan hanya perlu dikirim, gunakan TransPickup.","OPEN_SHOP","Buka TransShop","ORDER_SHOP",.84,false);return null;
    }
    private double score(String q,TransAssistantKnowledge.Entry e){if(e.answer==null||e.answer.trim().isEmpty())return 0;for(String n:e.negativeKeywords)if(containsPhrase(q,smartNorm(n)))return .05;double best=0,total=0;
        for(String k:e.strongKeywords){double s=phraseScore(q,smartNorm(k));best=Math.max(best,s);if(s>=.74)total+=.13;}
        for(String k:e.questions){double s=phraseScore(q,smartNorm(k));best=Math.max(best,s);if(s>=.72)total+=.11;}
        for(String k:e.weakKeywords){double s=phraseScore(q,smartNorm(k))*.80;best=Math.max(best,s);if(s>=.55)total+=.05;}
        String title=smartNorm(e.title);if(!title.isEmpty()&&q.contains(title))total+=.16;return Math.min(1,best+Math.min(.22,total)+Math.max(0,Math.min(100,e.priority))/1200.0);}
    private double phraseScore(String q,String k){if(k.isEmpty())return 0;if(q.equals(k))return .96;if(containsPhrase(q,k))return k.contains(" ")?.86:.68;if(k.contains(q)&&q.length()>=6)return .70;Set<String>qs=new HashSet<>(Arrays.asList(q.split("\\s+"))),ks=new HashSet<>(Arrays.asList(k.split("\\s+")));int hit=0;for(String x:qs)if(ks.contains(x))hit++;double overlap=(double)hit/Math.max(1,Math.max(qs.size(),ks.size()));return overlap*.62+tokenSimilarity(q,k)*.24;}
    private boolean isGreeting(String q){return q.equals("halo")||q.equals("hai")||q.equals("hi")||q.equals("pagi")||q.equals("siang")||q.equals("malam")||q.startsWith("selamat pagi")||q.startsWith("selamat siang")||q.startsWith("selamat malam");}
    private boolean isFollowUp(String q){return hasAny(q,"lalu","setelah itu","terus","selanjutnya","yang murah","yang dekat","itu saja","bagaimana lagi")||q.split(" ").length<=3;}
    private boolean isVeryShortFollowUp(String q){return q.split(" ").length<=4;}
    private static boolean containsPhrase(String q,String k){return !k.isEmpty() && (q.equals(k)||q.startsWith(k+" ")||q.endsWith(" "+k)||q.contains(" "+k+" "));}
    private static boolean hasAny(String q,String...t){for(String x:t)if(q.contains(smartNorm(x)))return true;return false;}
    private static double tokenSimilarity(String a,String b){String[]aa=a.split("\\s+"),bb=b.split("\\s+");double sum=0;for(String x:aa){double best=0;for(String y:bb)best=Math.max(best,similarity(x,y));if(best>.58)sum+=best;}return sum/Math.max(1,aa.length);}
    private static double similarity(String a,String b){int d=lev(a,b),m=Math.max(a.length(),b.length());return m==0?1:1.0-(double)d/m;}
    private static int lev(String a,String b){int[]p=new int[b.length()+1];for(int j=0;j<p.length;j++)p[j]=j;for(int i=1;i<=a.length();i++){int[]n=new int[b.length()+1];n[0]=i;for(int j=1;j<=b.length();j++)n[j]=Math.min(Math.min(n[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));p=n;}return p[b.length()];}
    static String smartNorm(String s){if(s==null)return"";String n=Normalizer.normalize(s.toLowerCase(new Locale("id","ID")),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();StringBuilder b=new StringBuilder();for(String w:n.split(" ")){String x=SLANG.containsKey(w)?SLANG.get(w):w;if(b.length()>0)b.append(' ');b.append(x);}return b.toString().replaceAll("\\s+"," ").trim();}
    static String norm(String s){return smartNorm(s);} private static Reply r(String t,String a,String l,String i,double c,boolean n){return new Reply(t,a,l,i,c,n);} private static final class Ranked{final TransAssistantKnowledge.Entry entry;final double score;Ranked(TransAssistantKnowledge.Entry e,double s){entry=e;score=s;}}
}
