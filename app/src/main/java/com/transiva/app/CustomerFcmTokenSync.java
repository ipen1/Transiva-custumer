package com.transiva.app;
import android.content.Context;
/** Compatibility facade: all token writes are owned by one coordinator. */
public final class CustomerFcmTokenSync {
    private CustomerFcmTokenSync(){}
    public static void syncIfNeeded(Context c){FcmTokenCoordinator.syncIfNeeded(c);}
    public static void forceSync(Context c){FcmTokenCoordinator.forceSync(c);}
    public static void syncTokenNow(Context c,String token){FcmTokenCoordinator.onNewToken(c,token);}
}
