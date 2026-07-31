package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * System certificate validation plus adaptive SPKI pinning for transiva.my.id.
 * The first certificate that passes Android's normal HTTPS validation is pinned locally.
 * Other hosts (Maps, OSRM, Firebase, CDN) remain under normal Android trust validation.
 */
public final class AdaptiveTlsPinning {
    private static final String HOST = "transiva.my.id";
    private static final String PREF = "transiva_tls_pins_v1";
    private static final String KEY = "spki";
    private static volatile boolean installed;

    private AdaptiveTlsPinning() { }

    public static synchronized void install(Context context) {
        if (installed) return;
        final Context app = context.getApplicationContext();
        final HostnameVerifier platform = HttpsURLConnection.getDefaultHostnameVerifier();
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
            if (!platform.verify(hostname, session)) return false;
            if (!HOST.equalsIgnoreCase(hostname)) return true;
            try {
                return verifyOrEnroll(app, session);
            } catch (Throwable error) {
                Log.e("TransivaTLS", "TLS public-key pin ditolak", error);
                return false;
            }
        });
        installed = true;
    }

    private static boolean verifyOrEnroll(Context context, SSLSession session) throws Exception {
        Certificate[] chain = session.getPeerCertificates();
        if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate)) return false;
        String presented = spki((X509Certificate) chain[0]);
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> saved = new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
        if (saved.isEmpty()) {
            saved.add(presented);
            prefs.edit().putStringSet(KEY, saved).commit();
            Log.i("TransivaTLS", "Pin TLS Transiva berhasil direkam");
            return true;
        }
        return saved.contains(presented);
    }

    private static String spki(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded());
        return "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP);
    }
}
