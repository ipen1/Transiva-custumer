package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Structured local conversation state for Trans Asisten 3.1. */
public final class TransAssistantConversationState {
    public final String intent;
    public final String lastQuery;
    public final String budget;
    public final String proximity;
    public final long updatedAt;

    private static final String PREF="trans_assistant_state";
    private static final String KEY="state";
    private static final long TTL_MS=30L*60L*1000L;

    private TransAssistantConversationState(String intent,String lastQuery,String budget,String proximity,long updatedAt){
        this.intent=safe(intent);this.lastQuery=safe(lastQuery);this.budget=safe(budget);this.proximity=safe(proximity);this.updatedAt=updatedAt;
    }

    public static TransAssistantConversationState read(Context c){
        try{
            String raw=prefs(c).getString(KEY,"");
            if(raw.isEmpty())return empty();
            JSONObject o=new JSONObject(raw);
            long ts=o.optLong("updated_at",0L);
            if(ts<=0L||System.currentTimeMillis()-ts>TTL_MS){clear(c);return empty();}
            return new TransAssistantConversationState(o.optString("intent",""),o.optString("last_query",""),o.optString("budget",""),o.optString("proximity",""),ts);
        }catch(Throwable ignored){return empty();}
    }

    public static void update(Context c,String intent,String query){
        try{
            TransAssistantConversationState old=read(c);
            String q=TransAssistantEngine.smartNorm(query);
            String budget=old.budget;
            String proximity=old.proximity;
            if(q.contains("murah")||q.contains("hemat")||q.contains("terjangkau"))budget="hemat";
            if(q.contains("dekat")||q.contains("terdekat")||q.contains("sekitar sini"))proximity="dekat";
            JSONObject o=new JSONObject();
            o.put("intent",safe(intent));o.put("last_query",safe(query));o.put("budget",budget);o.put("proximity",proximity);o.put("updated_at",System.currentTimeMillis());
            prefs(c).edit().putString(KEY,o.toString()).apply();
        }catch(Throwable ignored){}
    }

    public static void clear(Context c){prefs(c).edit().remove(KEY).apply();}
    private static TransAssistantConversationState empty(){return new TransAssistantConversationState("","","","",0L);}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static String safe(String s){return s==null?"":s.trim();}
}
