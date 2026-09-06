package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.Normalizer;
import java.util.*;

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
        if(q.isEmpty()) return new Reply("Silakan tulis pertanyaan tentang penggunaan Transiva.","","");
        TransAssistantKnowledge.Entry best=null; double bestScore=0;
        String previous=context.getSharedPreferences(PREF,0).getString(KEY_LAST,"");
        for(TransAssistantKnowledge.Entry e: entries){
            double s=score(q,e);
            if(!previous.isEmpty() && e.intent.equals(previous) && isFollowUp(q)) s+=0.22;
            if(s>bestScore){bestScore=s;best=e;}
        }
        if(best==null || bestScore<0.34){
            return new Reply("Saya belum menemukan panduan yang tepat. Coba tanyakan tentang cara pesan, TransRide, TransFood, TransShop, chat, panggilan, pembayaran, akun, riwayat, atau izin tampil di atas aplikasi lain.","OPEN_HELP","Lihat bantuan");
        }
        context.getSharedPreferences(PREF,0).edit().putString(KEY_LAST,best.intent).apply();
        return new Reply(best.answer,best.action,best.actionLabel);
    }

    private boolean isFollowUp(String q){
        return q.matches(".*\\b(lalu|setelah itu|terus|selanjutnya|bagaimana lagi|gimana lagi|itu)\\b.*") || q.length()<22;
    }
    private double score(String q, TransAssistantKnowledge.Entry e){
        double total=0;
        for(String k:e.keywords){
            String nk=norm(k);
            if(q.contains(nk)) total += nk.contains(" ") ? 0.34 : 0.18;
            else total += tokenSimilarity(q,nk)*0.12;
        }
        if(q.contains(norm(e.title))) total+=0.35;
        return Math.min(1.0,total);
    }
    private static double tokenSimilarity(String a,String b){
        String[] aa=a.split("\\s+"), bb=b.split("\\s+"); double best=0;
        for(String x:aa) for(String y:bb) best=Math.max(best, similarity(x,y));
        return best;
    }
    private static double similarity(String a,String b){
        int d=lev(a,b), m=Math.max(a.length(),b.length()); return m==0?1:1.0-((double)d/m);
    }
    private static int lev(String a,String b){ int[] p=new int[b.length()+1]; for(int j=0;j<p.length;j++)p[j]=j; for(int i=1;i<=a.length();i++){int[] n=new int[b.length()+1];n[0]=i;for(int j=1;j<=b.length();j++)n[j]=Math.min(Math.min(n[j-1]+1,p[j]+1),p[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));p=n;}return p[b.length()]; }
    static String norm(String s){ if(s==null)return""; String n=Normalizer.normalize(s.toLowerCase(new Locale("id","ID")),Normalizer.Form.NFD).replaceAll("\\p{M}",""); return n.replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim(); }
}
