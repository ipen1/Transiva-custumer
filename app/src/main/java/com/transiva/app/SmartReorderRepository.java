package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;import java.io.InputStream;import java.io.InputStreamReader;import java.net.HttpURLConnection;import java.nio.charset.StandardCharsets;

/** Network/data responsibility extracted from SmartReorderActivity. */
public final class SmartReorderRepository {
    private static final String URL="https://transiva.my.id/server/customer_smart_reorder.php";
    public interface Callback{void onResult(JSONObject data,Throwable error);}
    public void load(Context context,Callback cb){NetworkResilienceManager.executeSafe(()->{try{HttpURLConnection c=CustomerApiClient.open(context,URL+"?_="+System.currentTimeMillis());c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(12000);int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);r.close();c.disconnect();JSONObject o=new JSONObject(b.toString());new android.os.Handler(android.os.Looper.getMainLooper()).post(()->cb.onResult(o,null));}catch(Throwable e){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->cb.onResult(null,e));}});}
}
