package com.transiva.app;

import android.content.Context;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.*;

/** Trans Asisten 3.1 Premium: local hybrid NLP, confidence, negatives, memory, semantic follow-up and runtime brain. */
public final class TransAssistantEngine {
    public static final class Reply{
        public final String text,action,actionLabel,intent;public final double confidence;public final boolean needsConfirmation;
        Reply(String t,String a,String l,String i,double c,boolean n){text=t;action=a;actionLabel=l;intent=i;confidence=c;needsConfirmation=n;}
    }
    private static final double DIRECT=.74,CONFIRM=.54,AMBIGUITY=.07;
    private final Context context;
    private static final Map<String,String> SLANG=new HashMap<>();
    private static final LinkedHashMap<String,String> PHRASES=new LinkedHashMap<>();
    static{
        String[][]x={{"gmn","bagaimana"},{"gimana","bagaimana"},{"kmn","kemana"},{"mkn","makan"},{"psn","pesan"},{"sy","saya"},{"gw","saya"},{"tdk","tidak"},{"gak","tidak"},{"ga","tidak"},{"nggak","tidak"},{"ngga","tidak"},{"gk","tidak"},{"mo","mau"},{"mw","mau"},{"pgn","ingin"},{"pengen","ingin"},{"ojol","ojek"},{"krm","kirim"},{"brg","barang"},{"dmn","dimana"},{"blm","belum"},{"udh","sudah"},{"sdh","sudah"},{"trims","terima kasih"},{"mksh","terima kasih"},{"dkt","dekat"},{"murmer","murah"}};
        for(String[]p:x)SLANG.put(p[0],p[1]);
        PHRASES.put("d mana","dimana");PHRASES.put("g bisa","tidak bisa");PHRASES.put("gk bisa","tidak bisa");PHRASES.put("ga bisa","tidak bisa");PHRASES.put("nggak bisa","tidak bisa");PHRASES.put("udah sampai","sudah sampai");PHRASES.put("mau kmn","mau kemana");
    }
    public TransAssistantEngine(Context c){context=c.getApplicationContext();}

    public Reply answer(String raw){
        String q=smartNorm(raw);if(q.isEmpty())return r("Ceritakan kebutuhan Anda. Saya bisa membantu perjalanan, makanan, pengiriman, pembayaran, akun, chat, dan status pesanan.","","","EMPTY",1,false);
        TransAssistantMemory.add(context,"user",raw,"");
        TransAssistantRuntimeContext runtime=TransAssistantRuntimeContext.read(context);
        Reply z=runtimeAnswer(q,runtime);if(z!=null)return done(z,q);
        if(isGreeting(q))return done(r("Halo 👋 Saya Trans Asisten 3.1. Tulis seperti biasa—misalnya ‘mau pulang’, ‘lapar’, ‘saldo kurang’, ‘kirim paket’, atau ‘driver di mana’.","","","GREETING",.99,false),q);
        if(hasAny(q,"terima kasih","makasih","thanks"))return done(r("Sama-sama. Saya siap membantu kebutuhan Transiva berikutnya.","","","THANKS",.99,false),q);

        String previous=TransAssistantMemory.lastIntent(context);
        Reply follow=followUp(q,previous,TransAssistantConversationState.read(context));if(follow!=null)return done(follow,q);

        String recent=smartNorm(TransAssistantMemory.recentUserText(context));
        List<TransAssistantKnowledge.Entry> entries=TransAssistantKnowledge.load(context);Ranked best=null,second=null;
        for(TransAssistantKnowledge.Entry e:entries){
            double s=score(q,e);
            if(!previous.isEmpty()&&previous.equals(e.intent)&&isFollowUp(q))s+=.14;
            if(isVeryShortFollowUp(q)&&!recent.isEmpty())s=Math.max(s,score(recent+" "+q,e)-.05);
            s=Math.min(1,s);Ranked rr=new Ranked(e,s);if(best==null||rr.score>best.score){second=best;best=rr;}else if(second==null||rr.score>second.score)second=rr;
        }
        Reply lifestyle=lifestyleIntent(q);if(lifestyle!=null&&(best==null||best.score<.72))return done(lifestyle,q);
        if(best!=null){
            double threshold=Math.max(CONFIRM,best.entry.minConfidence);boolean ambiguous=second!=null&&best.score-second.score<AMBIGUITY;
            String ans=best.entry.answerFor(q);
            if(best.score>=DIRECT&&!ambiguous)return done(r(ans,best.entry.action,best.entry.actionLabel,best.entry.intent,best.score,false),q);
            if(best.score>=threshold){String t="Sepertinya Anda menanyakan "+best.entry.title+". "+ans;return done(r(t,best.entry.action,best.entry.actionLabel,best.entry.intent,best.score,true),q);}
        }
        return done(r("Saya belum yakin dengan maksud Anda. Coba tulis lebih spesifik, misalnya ‘pesan ojek’, ‘kirim paket’, ‘saldo kurang’, ‘cari makanan’, atau ‘cek pesanan’.","OPEN_HELP","Buka bantuan","UNKNOWN",best==null?0:best.score,false),q);
    }

    private Reply done(Reply x,String q){TransAssistantMemory.add(context,"assistant",x.text,x.intent);if(!x.intent.isEmpty()&&!"GREETING".equals(x.intent)&&!"THANKS".equals(x.intent)&&!"EMPTY".equals(x.intent)){TransAssistantLearningReporter.report(context,q,x);}if(!x.intent.isEmpty()&&!"UNKNOWN".equals(x.intent)&&!"GREETING".equals(x.intent)&&!"THANKS".equals(x.intent))TransAssistantConversationState.update(context,x.intent,q);return x;}

    private Reply runtimeAnswer(String q,TransAssistantRuntimeContext s){
        if(hasAny(q,"internet","koneksi","offline","jaringan")&&!s.online)return r("Perangkat sedang offline. Panduan lokal tetap tersedia, tetapi data merchant dan status terbaru membutuhkan internet.","","","DEVICE_NETWORK",1,false);
        if(hasAny(q,"gps","lokasi mati","lokasi tidak aktif","titik jemput tidak terbaca")&&!s.locationEnabled)return r("Lokasi perangkat sedang tidak aktif. Aktifkan GPS agar titik jemput dan pencarian driver lebih akurat.","OPEN_LOCATION_SETTINGS","Aktifkan lokasi","DEVICE_LOCATION",1,false);
        if(hasAny(q,"panggilan tidak muncul","telepon tidak muncul","call tidak muncul","overlay","atas aplikasi")&&!s.overlayAllowed)return r("Izin Tampil di atas aplikasi lain belum aktif. Aktifkan izin ini agar layar panggilan lebih mudah muncul saat Transiva berada di latar belakang.","OPEN_OVERLAY","Aktifkan izin","DEVICE_OVERLAY",1,false);
        if(hasAny(q,"saldo saya","berapa saldo","cek saldo","saldo sekarang")){String b=formatMoney(s.balance);return r("Saldo Transiva Pay yang tersimpan pada sesi Anda saat ini: "+b+".","OPEN_TOPUP","Buka Top Up","PAYMENT",.98,false);}
        if(isStatusQuestion(q)&&s.hasActiveOrder){String service=s.activeService.isEmpty()?"pesanan Transiva":s.activeService;String label=s.activeOrderStatus.isEmpty()?"sedang aktif":OrderStatusPresentation.label(s.activeOrderStatus,service);return r("Anda memiliki "+service+" yang masih aktif. Status terakhir: "+label+".","OPEN_ACTIVE_ORDER","Lihat pesanan aktif","ORDER_STATUS",1,false);}
        if(isStatusQuestion(q)&&!s.hasActiveOrder)return r("Saat ini saya tidak menemukan pesanan aktif pada perangkat. Anda dapat membuka Riwayat untuk melihat pesanan sebelumnya.","OPEN_HISTORY","Buka Riwayat","ORDER_STATUS",.96,false);
        return null;
    }

    private Reply followUp(String q,String previous,TransAssistantConversationState state){
        if(previous==null||previous.isEmpty())return null;
        boolean cheap=hasAny(q,"murah","hemat","terjangkau"),near=hasAny(q,"dekat","terdekat","sekitar sini");
        if("ORDER_FOOD".equals(previous)&&(cheap||near||q.equals("yang itu")||q.equals("lanjut"))){String extra=cheap&&near?"yang dekat dan lebih hemat":cheap?"yang lebih hemat":"yang dekat";return r("Baik. Untuk mencari makanan "+extra+", buka TransFood lalu bandingkan merchant/menu dan total pembayaran yang tersedia.","OPEN_FOOD","Cari di TransFood","ORDER_FOOD",.92,false);}
        if("ORDER_RIDE".equals(previous)&&(cheap||q.equals("lanjut")||hasAny(q,"yang motor","motor saja")))return r("Baik. Lanjutkan melalui TransRide dan periksa estimasi perjalanan sebelum membuat pesanan.","OPEN_RIDE","Buka TransRide","ORDER_RIDE",.92,false);
        if("ORDER_SHOP".equals(previous)&&(cheap||near))return r("Baik. Buka TransShop untuk membandingkan toko/barang yang tersedia sesuai kebutuhan Anda.","OPEN_SHOP","Buka TransShop","ORDER_SHOP",.90,false);
        if("PICKUP".equals(previous)&&hasAny(q,"lanjut","ambil sekarang","kirim sekarang"))return r("Siap. Buka TransPickup, isi lokasi pengambilan, tujuan dan detail barang, lalu buat pesanan.","OPEN_PICKUP","Buka TransPickup","PICKUP",.93,false);
        return null;
    }

    private Reply lifestyleIntent(String q){
        if(hasAny(q,"lapar","mau makan","ingin makan","cari makanan"))return r("Sepertinya Anda ingin makanan. Buka TransFood untuk memilih merchant dan menu yang tersedia.","OPEN_FOOD","Cari makanan","ORDER_FOOD",.90,false);
        if(hasAny(q,"pulang kantor","mau pulang","ke kantor","berangkat kerja","butuh kendaraan","pesan ojek"))return r("Untuk perjalanan motor, gunakan TransRide. Tentukan titik jemput dan tujuan, lalu cek estimasi sebelum memesan.","OPEN_RIDE","Buka TransRide","ORDER_RIDE",.90,false);
        if(hasAny(q,"kirim barang","antar paket","kirim paket","ambil barang"))return r("Gunakan TransPickup untuk mengambil atau mengirim barang. Isi lokasi pengambilan, tujuan, dan detail barang.","OPEN_PICKUP","Kirim barang","PICKUP",.90,false);
        if(hasAny(q,"pesan barang","belanja barang","titip belanja","beli obat","pesan obat","butuh obat","apotek"))return r("Untuk membeli kebutuhan atau obat dari toko/apotek yang tersedia, gunakan TransShop. Pilih barang atau toko, isi tujuan, lalu konfirmasi pesanan.","OPEN_SHOP","Buka TransShop","ORDER_SHOP",.90,false);
        return null;
    }

    private double score(String q,TransAssistantKnowledge.Entry e){
        if(e.primaryAnswer().isEmpty())return 0;for(String n:e.negativeKeywords)if(containsPhrase(q,smartNorm(n)))return .02;
        double best=0,total=0;for(String k:e.strongKeywords){double s=phraseScore(q,smartNorm(k));best=Math.max(best,s);if(s>=.74)total+=.14;}
        for(String k:e.questions){double s=phraseScore(q,smartNorm(k));best=Math.max(best,s);if(s>=.72)total+=.12;}
        for(String k:e.weakKeywords){double s=phraseScore(q,smartNorm(k))*.78;best=Math.max(best,s);if(s>=.54)total+=.045;}
        String title=smartNorm(e.title);if(!title.isEmpty()&&containsPhrase(q,title))total+=.14;
        return Math.min(1,best+Math.min(.24,total)+Math.max(0,Math.min(100,e.priority))/1250.0);
    }

    private double phraseScore(String q,String k){
        if(k.isEmpty())return 0;if(q.equals(k))return .98;if(containsPhrase(q,k))return k.contains(" ")?.88:.66;if(k.contains(q)&&q.length()>=6)return .70;
        Set<String> qs=new HashSet<>(Arrays.asList(q.split("\\s+"))),ks=new HashSet<>(Arrays.asList(k.split("\\s+")));int hit=0;for(String x:qs)if(ks.contains(x))hit++;
        double overlap=(double)hit/Math.max(1,Math.max(qs.size(),ks.size()));return overlap*.64+tokenSimilarity(q,k)*.24;
    }

    private boolean isStatusQuestion(String q){return hasAny(q,"status pesanan","status order","cek pesanan","cek order","driver dimana","driver di mana","pesanan saya dimana","order saya dimana","sudah sampai mana");}
    private boolean isGreeting(String q){return q.equals("halo")||q.equals("hai")||q.equals("hi")||q.equals("pagi")||q.equals("siang")||q.equals("malam")||q.startsWith("selamat pagi")||q.startsWith("selamat siang")||q.startsWith("selamat malam");}
    private boolean isFollowUp(String q){return hasAny(q,"lalu","setelah itu","terus","selanjutnya","yang murah","yang dekat","itu saja","bagaimana lagi","lanjut")||q.split(" ").length<=3;}
    private boolean isVeryShortFollowUp(String q){return q.split(" ").length<=4;}
    private static boolean containsPhrase(String q,String k){return !k.isEmpty()&&(q.equals(k)||q.startsWith(k+" ")||q.endsWith(" "+k)||q.contains(" "+k+" "));}
    private static boolean hasAny(String q,String...t){for(String x:t)if(q.contains(smartNorm(x)))return true;return false;}
    private static double tokenSimilarity(String a,String b){String[]aa=a.split("\\s+"),bb=b.split("\\s+");double sum=0;for(String x:aa){double best=0;for(String y:bb)best=Math.max(best,similarity(x,y));if(best>.60)sum+=best;}return sum/Math.max(1,aa.length);}
    private static double similarity(String a,String b){int d=lev(a,b),m=Math.max(a.length(),b.length());return m==0?1:1.0-(double)d/m;}
    private static int lev(String a,String b){int[]p=new int[b.length()+1];for(int j=0;j<p.length;j++)p[j]=j;for(int i=1;i<=a.length();i++){int[]n=new int[b.length()+1];n[0]=i;for(int j=1;j<=b.length();j++)n[j]=Math.min(Math.min(n[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));p=n;}return p[b.length()];}

    static String smartNorm(String s){
        if(s==null)return"";String n=Normalizer.normalize(s.toLowerCase(new Locale("id","ID")),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
        for(Map.Entry<String,String> e:PHRASES.entrySet())n=(" "+n+" ").replace(" "+e.getKey()+" "," "+e.getValue()+" ").trim();
        StringBuilder b=new StringBuilder();for(String w:n.split(" ")){String x=SLANG.containsKey(w)?SLANG.get(w):w;if(b.length()>0)b.append(' ');b.append(x);}return b.toString().replaceAll("\\s+"," ").trim();
    }
    static String norm(String s){return smartNorm(s);}
    private static String formatMoney(String raw){try{String d=raw==null?"0":raw.replaceAll("[^0-9-]","");long v=Long.parseLong(d.isEmpty()?"0":d);NumberFormat f=NumberFormat.getIntegerInstance(new Locale("id","ID"));return "Rp"+f.format(v);}catch(Exception e){return "Rp0";}}
    private static Reply r(String t,String a,String l,String i,double c,boolean n){return new Reply(t,a,l,i,c,n);}
    private static final class Ranked{final TransAssistantKnowledge.Entry entry;final double score;Ranked(TransAssistantKnowledge.Entry e,double s){entry=e;score=s;}}
}
