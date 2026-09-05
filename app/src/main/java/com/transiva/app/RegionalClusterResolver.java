package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;

/** Resolves Regional/Cluster from the server database; local cluster is only fallback. */
public final class RegionalClusterResolver {
    public static final class Result {
        public final String regionalName, clusterName; public final int clusterId; public final boolean fromServer;
        Result(String r,String c,int id,boolean s){regionalName=r;clusterName=c;clusterId=id;fromServer=s;}
    }
    private RegionalClusterResolver(){}
    public static Result resolve(Context context,double lat,double lng){
        try{
            JSONObject j=new TransivaApiRepository(context).get("customer_region_resolve.php?lat="+lat+"&lng="+lng,false);
            JSONObject c=j.optJSONObject("cluster"),r=j.optJSONObject("regional");
            if(j.optBoolean("success")&&c!=null)return new Result(r!=null?r.optString("name",""):"",c.optString("name",""),c.optInt("id",0),true);
        }catch(Exception ignored){}
        TransivaCluster.Item c=TransivaCluster.nearest(lat,lng);return new Result("",c.name,c.id,false);
    }
}
