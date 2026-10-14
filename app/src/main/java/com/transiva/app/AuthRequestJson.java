package com.transiva.app;
import android.content.Context;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class AuthRequestJson {
    interface ConnectionObserver { void opened(HttpURLConnection connection); }
    static JSONObject post(Context context,String endpoint,JSONObject body,CustomerSessionGuard session,ConnectionObserver observer) throws Exception {
        HttpURLConnection c=CustomerApiClient.open(context,ApiConfig.server(endpoint));
        try{
            if(observer!=null)observer.opened(c);
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            if(session!=null)session.authorize(context,c);
            c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();InputStream stream=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            if(stream==null)throw new IOException("Empty response");
            StringBuilder result=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){result.append(line);if(result.length()>1048576)throw new IOException("Response too large");}}
            JSONObject json=new JSONObject(result.toString());if(code<200||code>=300)json.put("success",false);return json;
        }finally{c.disconnect();}
    }
    static JSONObject failure(String message){JSONObject j=new JSONObject();try{j.put("success",false);j.put("message",message);}catch(Exception ignored){}return j;}
    static String sha256(String text){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.US,"%02x",v&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
