package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Small local conversation memory for Trans Asisten 3.0. Stores only the latest few turns. */
public final class TransAssistantMemory {
    private static final String PREF="trans_assistant_memory";
    private static final String KEY="turns";
    private static final int MAX_TURNS=20;
    private TransAssistantMemory(){}

    public static void add(Context c,String role,String text,String intent){
        try{
            JSONArray a=readArray(c);
            JSONObject o=new JSONObject();
            o.put("role",safe(role)); o.put("text",redact(safe(text))); o.put("intent",safe(intent)); o.put("ts",System.currentTimeMillis());
            a.put(o);
            while(a.length()>MAX_TURNS){ JSONArray n=new JSONArray(); for(int i=1;i<a.length();i++) n.put(a.get(i)); a=n; }
            prefs(c).edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public static String lastIntent(Context c){
        JSONArray a=readArray(c);
        for(int i=a.length()-1;i>=0;i--){ JSONObject o=a.optJSONObject(i); if(o!=null){String x=o.optString("intent",""); if(!x.isEmpty())return x;} }
        return "";
    }

    public static String recentUserText(Context c){
        JSONArray a=readArray(c); StringBuilder b=new StringBuilder(); int count=0;
        for(int i=a.length()-1;i>=0 && count<3;i--){ JSONObject o=a.optJSONObject(i); if(o!=null && "user".equals(o.optString("role"))){ if(b.length()>0)b.insert(0," "); b.insert(0,o.optString("text","")); count++; } }
        return b.toString().trim();
    }

    public static void clear(Context c){ prefs(c).edit().remove(KEY).apply(); }
    private static JSONArray readArray(Context c){
        try{
            JSONArray saved=new JSONArray(prefs(c).getString(KEY,"[]")),fresh=new JSONArray();
            long cutoff=System.currentTimeMillis()-30L*60L*1000L;
            for(int i=0;i<saved.length();i++){JSONObject turn=saved.optJSONObject(i);if(turn!=null&&turn.optLong("ts",0)>=cutoff)fresh.put(turn);}
            return fresh;
        }catch(Exception e){return new JSONArray();}
    }
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(TransAssistantSessionStorage.key(c,PREF),Context.MODE_PRIVATE);}
    static JSONArray snapshot(Context c){return readArray(c);}
    static String redact(String text){
        String bounded=text.substring(0,Math.min(text.length(),5000));
        return bounded.replaceAll("(?i)\\b(otp|pin|password|kata sandi)\\b(\\s*[:=]?\\s*)(\\S+)","$1 [disembunyikan]");
    }
    private static String safe(String s){return s==null?"":s.trim();}
}
