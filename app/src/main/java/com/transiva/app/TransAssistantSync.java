package com.transiva.app;
import android.content.Context;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;
/** Lightweight versioned knowledge sync. Cache is used instantly; network refresh is throttled. */
public final class TransAssistantSync{
    private static final long TTL=6L*60L*60L*1000L; private TransAssistantSync(){}
    public static void sync(Context c){sync(c,false);} public static void sync(Context c,boolean force){Context app=c.getApplicationContext();if(!force&&System.currentTimeMillis()-TransAssistantKnowledge.lastSync(app)<TTL)return;new Thread(()->{HttpURLConnection h=null;try{h=CustomerApiClient.open(app,ApiConfig.server("trans_assistant_knowledge.php"));h.setRequestMethod("GET");h.setRequestProperty("X-Trans-Assistant-Version",TransAssistantKnowledge.version(app));int code=h.getResponseCode();InputStream in=code>=200&&code<300?h.getInputStream():h.getErrorStream();StringBuilder b=new StringBuilder();if(in!=null){BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));String s;while((s=r.readLine())!=null)b.append(s);}if(!CustomerApiClient.handleSessionResponse(app,code,b.toString())&&code>=200&&code<300&&b.length()>2)TransAssistantKnowledge.saveRemote(app,b.toString());}catch(Exception ignored){}finally{if(h!=null)h.disconnect();}},"TransAssistantSync3").start();}
}
