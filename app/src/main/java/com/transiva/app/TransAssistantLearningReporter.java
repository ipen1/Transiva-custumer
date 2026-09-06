package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lightweight, non-blocking telemetry for Admin Learning Analytics.
 * Only sends the customer's question + AI classification metadata after an authenticated interaction.
 * Failures are intentionally silent so assistant UX never depends on analytics availability.
 */
public final class TransAssistantLearningReporter {
    private static final String URL="https://transiva.my.id/server/trans_assistant_learning.php";
    private static final Object LOCK=new Object();
    private static String lastKey="";
    private static long lastAt=0L;
    private TransAssistantLearningReporter(){}

    public static void report(Context context,String question,TransAssistantEngine.Reply reply){
        if(context==null||reply==null)return;
        final Context app=context.getApplicationContext();
        final String q=question==null?"":question.trim();
        if(q.isEmpty())return;
        final String key=(q+"|"+reply.intent+"|"+String.format(Locale.US,"%.2f",reply.confidence)).toLowerCase(Locale.ROOT);
        long now=System.currentTimeMillis();
        synchronized(LOCK){if(key.equals(lastKey)&&now-lastAt<8000L)return;lastKey=key;lastAt=now;}
        TransivaNetworkExecutor.execute(()->send(app,q,reply));
    }

    private static void send(Context app,String q,TransAssistantEngine.Reply r){
        HttpURLConnection c=null;
        try{
            c=CustomerApiClient.open(app,URL);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(7000);c.setReadTimeout(7000);
            c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            JSONObject o=new JSONObject();
            o.put("question",q.length()>500?q.substring(0,500):q);
            o.put("intent",r.intent==null?"UNKNOWN":r.intent);
            o.put("confidence",Math.max(0,Math.min(1,r.confidence)));
            o.put("needs_confirmation",r.needsConfirmation);
            o.put("source","assistant_3_1");
            byte[] b=o.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=c.getOutputStream()){out.write(b);}
            int code=c.getResponseCode();
            InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();
            if(in!=null){try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){while(br.readLine()!=null){}}}
            CustomerApiClient.handleSessionResponse(app,code,"");
        }catch(Throwable ignored){}finally{if(c!=null)c.disconnect();}
    }
}
