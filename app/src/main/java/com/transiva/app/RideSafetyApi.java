package com.transiva.app;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class RideSafetyApi {
    private RideSafetyApi() {}
    public static JSONObject post(android.content.Context context, String endpoint, JSONObject body) throws Exception {
        HttpURLConnection c=CustomerApiClient.open(context,"https://transiva.my.id/server/"+endpoint);
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try(OutputStream os=c.getOutputStream()){os.write((body==null?"{}":body.toString()).getBytes(StandardCharsets.UTF_8));}
        InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();
        BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); String l; while((l=br.readLine())!=null)s.append(l);
        return s.length()==0?new JSONObject():new JSONObject(s.toString());
    }
}
