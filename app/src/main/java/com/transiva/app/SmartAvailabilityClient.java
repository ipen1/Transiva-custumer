package com.transiva.app;

import org.json.JSONObject;
public final class SmartAvailabilityClient {
    public interface Callback { void done(JSONObject response); }
    private SmartAvailabilityClient(){}
    public static void check(android.content.Context c,double lat,double lng,String service,Callback cb){TransivaNetworkExecutor.execute(()->{JSONObject r;try{r=RideSafetyApi.post(c,"ride_smart_availability.php",new JSONObject().put("latitude",lat).put("longitude",lng).put("service",service));}catch(Exception e){r=new JSONObject();}JSONObject out=r;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(cb!=null)cb.done(out);});});}
}
