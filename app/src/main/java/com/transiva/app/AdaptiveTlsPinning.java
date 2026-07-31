package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * Platform TLS validation plus two-slot SPKI pin rotation for transiva.my.id.
 * Keeps current and previous valid keys, preventing permanent lockout during certificate rotation.
 */
public final class AdaptiveTlsPinning {
    private static final String HOST = "transiva.my.id";
    private static final String PREF = "transiva_tls_pins_v2";
    private static final String KEY_CURRENT = "current_spki";
    private static final String KEY_BACKUP = "backup_spki";
    private static final String KEY_SEEN_AT = "current_seen_at";
    private static final long ROTATION_GRACE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static volatile boolean installed;
    private AdaptiveTlsPinning() {}

    public static synchronized void install(Context context) {
        if (installed || context == null) return;
        Context app = context.getApplicationContext();
        HostnameVerifier platform = HttpsURLConnection.getDefaultHostnameVerifier();
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
            if (!platform.verify(hostname, session)) return false;
            if (!HOST.equalsIgnoreCase(hostname)) return true;
            try { return verify(app, session); }
            catch (Throwable e) { Log.e("TransivaTLS", "SPKI pin rejected", e); return false; }
        });
        installed = true;
    }

    private static boolean verify(Context context, SSLSession session) throws Exception {
        Certificate[] chain = session.getPeerCertificates();
        if (chain == null || chain.length == 0) return false;
        Set<String> presented = new LinkedHashSet<>();
        for (Certificate c : chain) if (c instanceof X509Certificate) presented.add(spki((X509Certificate)c));
        if (presented.isEmpty()) return false;

        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String current = p.getString(KEY_CURRENT, "");
        String backup = p.getString(KEY_BACKUP, "");
        long seenAt = p.getLong(KEY_SEEN_AT, 0L);
        long now = System.currentTimeMillis();

        if (current.isEmpty()) {
            String first = presented.iterator().next();
            p.edit().putString(KEY_CURRENT, first).putLong(KEY_SEEN_AT, now).commit();
            return true;
        }
        if (presented.contains(current) || (!backup.isEmpty() && presented.contains(backup))) return true;

        // Rotation is accepted only after the old key has been stable for 30 days and Android TLS validation succeeded.
        if (seenAt > 0 && now - seenAt >= ROTATION_GRACE_MS) {
            String next = presented.iterator().next();
            p.edit().putString(KEY_BACKUP, current).putString(KEY_CURRENT, next).putLong(KEY_SEEN_AT, now).commit();
            Log.w("TransivaTLS", "SPKI pin rotated into dual-slot store");
            return true;
        }
        return false;
    }

    private static String spki(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded());
        return "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP);
    }
}
