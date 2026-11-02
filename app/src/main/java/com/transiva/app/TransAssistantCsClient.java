package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Bounded authenticated transport, invoked only off the UI thread. */
public final class TransAssistantCsClient {
    private TransAssistantCsClient() {}
    static JSONObject ask(Context context,CustomerSessionGuard session,String message,String previous,String orderId,String source) throws Exception {
        HttpURLConnection connection=null;
        try{
            JSONObject body=new JSONObject();body.put("message",message);body.put("previous_intent",previous);body.put("order_id",orderId);body.put("source",source);
            connection=CustomerApiClient.open(context,ApiConfig.server("trans_assistant_cs.php"));
            session.authorize(context,connection);
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setUseCaches(false);connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Accept","application/json");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
            int status=connection.getResponseCode();InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
            if(stream==null)throw new java.io.IOException("Empty CS response");
            String raw;
            try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>65536)throw new java.io.IOException("CS response too large");}
                raw=new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
            if(!session.matches(context))throw new java.io.InterruptedIOException("Session changed");
            if(CustomerApiClient.handleSessionResponse(context,status,raw))throw new java.io.InterruptedIOException("Session expired");
            if(status<200||status>=300)throw new java.io.IOException("CS unavailable");
            JSONObject result=new JSONObject(raw);if(!result.optBoolean("success"))throw new java.io.IOException("CS unavailable");
            return result;
        }finally{if(connection!=null)connection.disconnect();}
    }
}
