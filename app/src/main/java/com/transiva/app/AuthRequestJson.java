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
            String raw=result.toString().trim();if(raw.startsWith("\uFEFF"))raw=raw.substring(1).trim();
            JSONObject json;
            try{json=new JSONObject(raw);}catch(org.json.JSONException invalid){throw new InvalidResponseException(code);}
            if(code<200||code>=300)json.put("success",false);if(!json.optBoolean("success"))json.put("message",CustomerErrorMessages.humanize(json.optString("message"),"Layanan sedang mengalami kendala. Silakan coba lagi sebentar."));return json;
        }finally{c.disconnect();}
    }
    private static final class InvalidResponseException extends IOException {InvalidResponseException(int code){super("HTTP "+code);}}
    static JSONObject userFailure(Exception error,boolean mutation){
        if(error instanceof InvalidResponseException)return failure(mutation?"Kami belum bisa memastikan akun sudah dibuat. Coba lagi dengan data yang sama; jangan mengganti data Anda dulu.":"Layanan sedang mengalami kendala. Silakan coba lagi sebentar.");
        if(error instanceof java.net.UnknownHostException)return failure("Server belum dapat dijangkau. Periksa koneksi internet Anda.");
        if(error instanceof javax.net.ssl.SSLException)return failure("Koneksi aman ke server belum berhasil. Periksa tanggal perangkat atau hubungi admin.");
        return failure(mutation?"Kami belum bisa memastikan akun sudah dibuat. Coba lagi dengan data yang sama.":"Data belum dapat diperiksa. Periksa koneksi lalu coba kembali.");
    }
    static JSONObject failure(String message){JSONObject j=new JSONObject();try{j.put("success",false);j.put("message",message);}catch(Exception ignored){}return j;}
    static String sha256(String text){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.US,"%02x",v&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
