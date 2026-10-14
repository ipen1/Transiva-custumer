package com.transiva.app;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.UUID;
/** Called only on a background worker before a registration request is sent. */
final class SignupOperationStore {
    static synchronized String operation(Context c,String identity) throws IOException {
        SharedPreferences p=c.getSharedPreferences("signup_retry",Context.MODE_PRIVATE);
        String digest=AuthRequestJson.sha256(identity),id=digest.equals(p.getString("digest",""))?p.getString("operation",""):"";
        if(id.isEmpty()){id="signup-"+UUID.randomUUID();if(!p.edit().putString("digest",digest).putString("operation",id).commit())throw new IOException("Signup ID not persisted");}
        return id;
    }
}
