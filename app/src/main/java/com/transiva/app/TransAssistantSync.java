package com.transiva.app;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Versioned knowledge sync. Cache is used instantly; refresh is throttled and lifecycle-independent. */
public final class TransAssistantSync {
    private static final long TTL=6L*60L*60L*1000L;
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    private TransAssistantSync(){}

    public static void sync(Context c){sync(c,false);}

    public static void sync(Context c,boolean force){
        if(c==null)return;
        final Context app=c.getApplicationContext();
        if(!force&&System.currentTimeMillis()-TransAssistantKnowledge.lastSync(app)<TTL)return;
        if(!RUNNING.compareAndSet(false,true))return;
        TransivaNetworkExecutor.execute(()->{
            HttpURLConnection h=null;
            try{
                h=CustomerApiClient.open(app,ApiConfig.server("trans_assistant_knowledge.php"));
                h.setRequestMethod("GET");
                h.setConnectTimeout(7000);h.setReadTimeout(7000);
                h.setRequestProperty("Accept","application/json");
                h.setRequestProperty("X-Trans-Assistant-Version",TransAssistantKnowledge.version(app));
                int code=h.getResponseCode();
                if(code==304)return;
                InputStream in=code>=200&&code<300?h.getInputStream():h.getErrorStream();
                StringBuilder b=new StringBuilder();
                if(in!=null){
                    try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null)b.append(s);}
                }
                boolean handled=CustomerApiClient.handleSessionResponse(app,code,b.toString());
                if(!handled&&code>=200&&code<300&&b.length()>2)TransAssistantKnowledge.saveRemote(app,b.toString());
            }catch(Throwable ignored){}finally{if(h!=null)h.disconnect();RUNNING.set(false);}
        });
    }
}
