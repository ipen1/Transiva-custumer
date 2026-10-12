package com.transiva.app;
import android.content.Context;
/** Captures identity only in memory; never logs/persists the token. */
public final class CustomerSessionGuard {
    public final String account;
    private final String token;
    private CustomerSessionGuard(String account, String token) { this.account=account; this.token=token; }
    public static CustomerSessionGuard capture(Context c) {
        SessionManager s=new SessionManager(c);
        if(!s.isLoggedIn())return new CustomerSessionGuard("", "");
        String id=s.getId();if(id==null||id.isEmpty())id=s.getUserId();
        return new CustomerSessionGuard(id==null?"":id, s.getToken()==null?"":s.getToken());
    }
    public boolean matches(Context c) {
        SessionManager s=new SessionManager(c);String id=s.getId();if(id==null||id.isEmpty())id=s.getUserId();
        return CustomerReadSafetyRules.sameSession(account,token,id,s.getToken()==null?"":s.getToken(),s.isLoggedIn());
    }
}
