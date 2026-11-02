package com.transiva.app;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Isolates conversation state across accounts and login sessions; stores no raw bearer token. */
final class TransAssistantSessionStorage {
    private TransAssistantSessionStorage() {}
    static String key(Context c,String prefix){
        try{
            SessionManager session=new SessionManager(c);
            String id=session.getUserId();String token=session.getToken();
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(((id==null?"":id)+":"+(token==null?"":token)).getBytes(StandardCharsets.UTF_8));
            StringBuilder suffix=new StringBuilder();for(int i=0;i<12;i++)suffix.append(String.format(java.util.Locale.ROOT,"%02x",hash[i]&255));
            return prefix+"_cs4_"+suffix;
        }catch(Exception e){throw new IllegalStateException("Conversation storage unavailable",e);}
    }
}
