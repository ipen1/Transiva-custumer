package com.transiva.app;
import java.util.Locale;
/** Shared rules for typed codes, links, and registration. Server remains authoritative. */
public final class ReferralCodeRules {
    private ReferralCodeRules(){}
    public static String normalize(String raw){return raw==null?"":raw.trim().toUpperCase(Locale.US);}
    public static boolean valid(String code){return code!=null&&code.matches("[A-Z0-9]{3,40}");}
    public static String shareUrl(String code){String value=normalize(code);if(!valid(value))throw new IllegalArgumentException("Kode referral tidak valid");return "https://transiva.my.id/referral.php?code="+value;}
}
