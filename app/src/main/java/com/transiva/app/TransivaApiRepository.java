package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;

/** Compatibility facade: all transport now uses the session/cancellation/bounded HTTP gateway. */
public final class TransivaApiRepository {
    private final Context app;
    public TransivaApiRepository(Context context){app=context.getApplicationContext();}
    public JSONObject get(String serverPath,boolean cacheable)throws Exception{
        String url=ApiConfig.server(serverPath);
        try{return TransivaHttpRepository.getJson(app,url,20000);}
        catch(java.io.IOException offline){
            if(cacheable){JSONObject cached=TransivaHttpRepository.cachedCatalog(app,url);if(cached!=null){cached.put("_offline_cache",true);return cached;}}
            throw offline;
        }
    }
    public JSONObject post(String serverPath,JSONObject body,String action)throws Exception{return TransivaHttpRepository.postJson(app,ApiConfig.server(serverPath),body==null?new JSONObject():body,20000);}
}
