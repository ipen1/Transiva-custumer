package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Centralized JSON gateway with auth, request ids and optional offline fallback. */
public final class TransivaApiRepository {
    private final Context app;
    public TransivaApiRepository(Context context) { app=context.getApplicationContext(); }

    public JSONObject get(String serverPath, boolean cacheable) throws Exception {
        String url=ApiConfig.server(serverPath); String cacheKey="GET:"+url;
        try {
            JSONObject out=request("GET", url, null);
            if (cacheable && out.optBoolean("success", true)) OfflineJsonCache.put(app, cacheKey, out);
            return out;
        } catch (Exception e) {
            if (cacheable) { JSONObject cached=OfflineJsonCache.get(app, cacheKey, 10*60*1000L, true); if(cached!=null){ cached.put("_offline_cache", true); return cached; } }
            throw e;
        }
    }

    public JSONObject post(String serverPath, JSONObject body, String action) throws Exception {
        return request("POST", ApiConfig.server(serverPath), body == null ? new JSONObject() : body, action);
    }

    private JSONObject request(String method, String url, JSONObject body) throws Exception { return request(method,url,body,"api"); }
    private JSONObject request(String method, String url, JSONObject body, String action) throws Exception {
        HttpURLConnection c=null;
        try {
            c=CustomerApiClient.open(app,url); c.setRequestMethod(method); c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("X-Request-ID", CustomerApiClient.idempotencyKey(action));
            if(body!=null){ c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=UTF-8"); try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));} }
            int code=c.getResponseCode(); InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream(); String raw=read(in);
            JSONObject out=raw.trim().startsWith("{")?new JSONObject(raw):new JSONObject().put("success", code>=200&&code<300).put("raw",raw);
            out.put("_http_status",code); if(code<200||code>=400) throw new IOException("HTTP "+code+": "+out.optString("message","request failed")); return out;
        } finally { if(c!=null)c.disconnect(); }
    }
    private static String read(InputStream in) throws Exception { if(in==null)return "{}"; try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){ StringBuilder b=new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); return b.toString(); } }
}
