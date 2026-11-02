package com.transiva.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Deterministic, auditable native CS rules. No generative-model or external SDK dependency. */
public final class TransAssistantCsBrain {
    private static volatile JSONArray knowledge;
    private TransAssistantCsBrain() {}

    public static String normalize(String raw) {
        String q=TransAssistantEngine.smartNorm(raw);
        String[][] aliases={{"ongkirnya","ongkir"},{"saldonya","saldo"},{"drivernya","driver"},{"vouchernya","voucher"},{"pesanannya","pesanan"},{"pesenan","pesanan"},{"pesananan","pesanan"},{"pesananmu","pesanan"},{"orderan","order"},{"orderannya","order"},{"makanannya","makanan"},{"barangnya","barang"},{"paketnya","paket"},{"tarifnya","tarif"},{"harganya","harga"},{"blum","belum"},{"drver","driver"},{"drivr","driver"},{"salod","saldo"},{"sldo","saldo"},{"trpotong","terpotong"},{"kepotng","kepotong"},{"doble","dobel"},{"dobbel","dobel"},{"knp","kenapa"},{"bgmn","bagaimana"},{"bs","bisa"},{"bgt","banget"},{"lg","lagi"},{"trs","terus"},{"min","admin"},{"mimin","admin"},{"komplen","komplain"},{"voucer","voucher"},{"vocer","voucher"}};
        for(String[] pair:aliases)q=(" "+q+" ").replace(" "+pair[0]+" "," "+pair[1]+" ").trim();
        return q;
    }
    private static boolean matches(String q,String expression){return Pattern.compile(expression).matcher(q).find();}
    private static JSONArray entries(Context c) throws Exception {
        if(knowledge!=null)return knowledge;
        try(InputStream in=c.getAssets().open("trans_assistant_cs_knowledge.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>200000)throw new java.io.IOException("Knowledge too large");}
            knowledge=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("entries");
            return knowledge;
        }
    }
    static TransAssistantEngine.Reply answer(Context c,String raw,String previous){
        try{
            String q=normalize(raw);
            if(matches(q,"\\b(abaikan|ignore|bypass)\\b.*\\b(aturan|instruksi|login|keamanan|instructions)\\b")||matches(q,"\\b(saldo|data|pesanan)\\b.*\\b(orang lain|customer lain|akun lain)\\b"))
                return reply("Saya hanya membantu data akun yang sedang login. Saya tidak dapat membuka data akun lain atau melewati pemeriksaan keamanan.","CS_PRIVACY","OPEN_SUPPORT","Pusat Bantuan");
            JSONArray entries=entries(c);Map<String,JSONObject> hits=new LinkedHashMap<>();
            for(int i=0;i<entries.length();i++){
                JSONObject e=entries.getJSONObject(i);JSONArray negatives=e.optJSONArray("negative_patterns");boolean excluded=false;
                if(negatives!=null)for(int j=0;j<negatives.length();j++)if(matches(q,negatives.getString(j))){excluded=true;break;}
                if(excluded)continue;
                JSONArray patterns=e.getJSONArray("patterns");
                for(int j=0;j<patterns.length();j++)if(matches(q,patterns.getString(j))){hits.put(e.getString("intent"),e);break;}
            }
            JSONObject selected=null;
            String[] priority={"CS_SECURITY","CS_DRIVER_CONDUCT","CS_PAYMENT_DISPUTE","CS_LOST_ITEM","CS_TICKET","CS_HUMAN","CS_DEBIT","CS_TOPUP_PENDING","CS_REFUND","CS_FOOD_PROBLEM","CS_CANCEL","CS_DRIVER_LATE","CS_VOUCHER","CS_REWARD","CS_STATUS","CS_BALANCE"};
            for(String key:priority)if(hits.containsKey(key)){selected=hits.get(key);break;}
            if(selected==null&&!hits.isEmpty())selected=hits.values().iterator().next();
            if(selected==null&&matches(q,"^(masih( belum| sama| begitu| error| gagal)?|belum( juga| masuk| bisa)?|sudah( berhasil| transfer)?|sudah saya coba|tetap( sama| gagal)?|lanjut|terus|lalu|bagaimana( lagi)?|kenapa|yang tadi|dua kali|tidak berkurang|tidak muncul|ditolak|tidak tahu|belum paham)( kak| ya)?$"))
                for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).optString("intent").equals(previous)){selected=entries.getJSONObject(i);break;}
            if(selected==null)return null;
            String text=selected.getString("answer");JSONArray cases=selected.optJSONArray("cases");
            if(cases!=null)for(int i=0;i<cases.length();i++){JSONObject item=cases.getJSONObject(i);if(matches(q,item.getString("pattern"))){text=item.getString("answer");break;}}
            String follow=text.equals(selected.getString("answer"))?selected.optString("follow_up",""):"";
            if(!follow.isEmpty())text+="\n\n"+follow;
            return reply(text,selected.getString("intent"),selected.optString("action",""),selected.optString("action_label",""));
        }catch(Exception ignored){return null;}
    }
    private static TransAssistantEngine.Reply reply(String text,String intent,String action,String label){return new TransAssistantEngine.Reply(text,action,label,intent,.9,false);}
}
