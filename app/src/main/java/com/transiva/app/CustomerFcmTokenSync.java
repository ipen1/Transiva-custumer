package com.transiva.app;
import android.content.Context;
/** Compatibility entry points; one coordinator owns acknowledgements and retries. */
public final class CustomerFcmTokenSync {
    private CustomerFcmTokenSync(){}
    public static void syncIfNeeded(Context context){ /* Runtime checks on every foreground tick. */ }
    public static void forceSync(Context context){CallReliabilityRuntime.requestTokenSync();}
    public static void syncTokenNow(Context context,String token){
        if(context==null||token==null||token.trim().length()<20)return;
        new SessionManager(context.getApplicationContext()).saveFcmToken(token.trim());
        CallReliabilityRuntime.requestTokenSync();
    }
}
