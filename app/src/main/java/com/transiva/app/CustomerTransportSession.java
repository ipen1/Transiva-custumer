package com.transiva.app;

import android.content.Context;
import java.net.HttpURLConnection;

/** Immutable request identity, also permitting stable anonymous/public requests. Never persisted. */
final class CustomerTransportSession {
    private final String user, token;
    private final boolean loggedIn;
    private CustomerTransportSession(SessionManager session) {
        user=clean(session.getUserId());token=clean(session.getToken());loggedIn=session.isLoggedIn();
    }
    static CustomerTransportSession capture(Context context){return new CustomerTransportSession(new SessionManager(context));}
    boolean matches(Context context){
        SessionManager live=new SessionManager(context);
        return loggedIn==live.isLoggedIn() && user.equals(clean(live.getUserId())) && token.equals(clean(live.getToken()));
    }
    void check(Context context)throws InterruptedException{if(!matches(context))throw new InterruptedException("Request session changed");}
    void pin(Context context,HttpURLConnection connection)throws InterruptedException{
        check(context);
        if(NetworkSafetyRules.maySendCredentials(connection.getURL().toString()))
            connection.setRequestProperty("Authorization",token.isEmpty()?"":"Bearer "+token);
        check(context);
    }
    private static String clean(String value){return value==null?"":value.trim();}
}
