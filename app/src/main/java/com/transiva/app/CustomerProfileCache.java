package com.transiva.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;

/** Account/session scoped profile snapshots. Never used to authorize payments. */
public final class CustomerProfileCache {
    public interface Callback { void done(JSONObject profile); }
    private static final String PREF="customer_profile_cache_v28";
    private static final long FRESH_MS=120000L, MAX_AGE_MS=86400000L;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<String,List<Callback>> FLIGHTS=new HashMap<>();
    private static final Map<String,Long> RETRY=new HashMap<>();
    private static long revision;
    private CustomerProfileCache() {}
    private static String key(Context c) {
        SessionManager s=new SessionManager(c);
        if(!s.isLoggedIn()||!"customer".equalsIgnoreCase(s.getRole()))return "";
        String id=s.getId();if(id==null||id.isEmpty())id=s.getUserId();
        String token=s.getToken();if(id==null||id.isEmpty()||token==null||token.isEmpty())return "";
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((id+":"+token).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();for(byte x:hash)b.append(String.format(java.util.Locale.US,"%02x",x&255));return b.toString();
        }catch(Exception e){return "";}
    }
    public static JSONObject cached(Context c) {
        String k=key(c);if(k.isEmpty())return null;
        android.content.SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long age=System.currentTimeMillis()-p.getLong("saved",0);
        if(!k.equals(p.getString("owner",""))||age<0||age>MAX_AGE_MS)return null;
        try{return new JSONObject(p.getString("json",""));}catch(Exception e){return null;}
    }
    public static synchronized void clear(Context c) {
        revision++; RETRY.clear();c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply();
    }
    public static synchronized void put(Context c,JSONObject user) {
        String k=key(c);SessionManager session=new SessionManager(c);
        String id=session.getId();if(id==null||id.isEmpty())id=session.getUserId();
        if(k.isEmpty()||user==null||!String.valueOf(user.opt("id")).equals(id))return;
        // Persist only profile fields, never tokens, passwords, or a cached wallet balance.
        JSONObject safe=new JSONObject();
        String[] fields={"id","username","email","phone","phone_number","profile_photo","photo","delivery_address","delivery_lat","delivery_lng","email_verified","verified_by_admin","role"};
        try{for(String field:fields)if(user.has(field))safe.put(field,user.opt(field));}catch(Exception e){return;}
        revision++;
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("owner",k).putString("json",safe.toString()).putLong("saved",System.currentTimeMillis()).apply();
        DeliveryAddressGate.storeProfile(c,id,safe);
    }
    public static void warm(Context c) { fetch(c,false,null); }
    public static void fetch(Context context,boolean force,Callback callback) {
        final Context app=context.getApplicationContext(); final String k=key(app);
        if(k.isEmpty()){if(callback!=null)MAIN.post(()->callback.done(null));return;}
        JSONObject cached=cached(app);long age=System.currentTimeMillis()-app.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong("saved",0);
        final long before;
        synchronized(CustomerProfileCache.class){
            if(!force && cached!=null && age>=0 && age<FRESH_MS){if(callback!=null)MAIN.post(()->callback.done(cached(app)));return;}
            List<Callback> waiting=FLIGHTS.get(k);
            if(waiting!=null){if(callback!=null)waiting.add(callback);return;}
            if(!force&&System.currentTimeMillis()<(RETRY.containsKey(k)?RETRY.get(k):0L)){if(callback!=null)MAIN.post(()->callback.done(cached(app)));return;}
            waiting=new ArrayList<>();if(callback!=null)waiting.add(callback);FLIGHTS.put(k,waiting);before=revision;
        }
        try {
            DashboardReadExecutor.submit(()->{
                JSONObject result=null;
                try{
                    if(k.equals(key(app))){
                        JSONObject response=TransivaHttpRepository.getJsonOnce(app,ApiConfig.server("get_customer_profile.php"),5000);
                        if(response.optBoolean("success",false))result=response.optJSONObject("user");
                    }
                }catch(Exception ignored){}
                finish(app,k,before,result);return null;
            });
        }catch(java.util.concurrent.RejectedExecutionException busy){finish(app,k,before,null);}
    }
    private static void finish(Context app,String k,long before,JSONObject result) {
        MAIN.post(()->{
            List<Callback> callbacks;JSONObject answer=null;
            synchronized(CustomerProfileCache.class){
                callbacks=FLIGHTS.remove(k);
                if(k.equals(key(app))){
                    if(result!=null&&revision==before)put(app,result);
                    answer=cached(app);
                    if(result==null)RETRY.put(k,System.currentTimeMillis()+15000L);else RETRY.remove(k);
                }
            }
            if(callbacks!=null)for(Callback cb:callbacks){try{cb.done(answer==null?null:new JSONObject(answer.toString()));}catch(Exception ignored){}}
        });
    }
}
