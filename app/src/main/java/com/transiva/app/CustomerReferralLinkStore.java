package com.transiva.app;
import android.content.Context;
import android.content.SharedPreferences;
/** Pending link only. A permanent binding lives on the server, never solely on this device. */
public final class CustomerReferralLinkStore {
    private CustomerReferralLinkStore(){}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("customer_pending_referral_v3",Context.MODE_PRIVATE);}
    public static void remember(Context c,String raw){String code=ReferralCodeRules.normalize(raw);if(ReferralCodeRules.valid(code))prefs(c).edit().putString("code",code).putBoolean("routed",false).apply();}
    public static String pending(Context c){String code=prefs(c).getString("code","");return ReferralCodeRules.valid(code)?code:"";}
    public static boolean needsRoute(Context c){return !pending(c).isEmpty()&&!prefs(c).getBoolean("routed",false);}
    public static void markRouted(Context c){prefs(c).edit().putBoolean("routed",true).apply();}
    public static void clear(Context c){prefs(c).edit().clear().apply();}
}
