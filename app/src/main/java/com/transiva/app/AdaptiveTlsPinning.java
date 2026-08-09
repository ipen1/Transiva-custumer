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
 * TLS continuity monitor untuk transiva.my.id.
 *
 * Keamanan utama tetap memakai validasi TLS bawaan Android (CA + hostname).
 * Versi lama menjadikan SPKI yang pernah tersimpan sebagai hard-pin dan baru
 * menerima rotasi setelah 30 hari. Jika hosting/CDN mengganti sertifikat/key
 * lebih cepat, seluruh HTTPS customer dapat ditolak walaupun sertifikat baru sah.
 *
 * Versi ini tetap mencatat active/backup SPKI untuk audit, tetapi TIDAK
 * memblokir sertifikat baru yang sudah lolos validasi TLS + hostname Android.
 * Jadi pergantian/perpanjangan sertifikat normal tidak mengunci aplikasi.
 */
public final class AdaptiveTlsPinning {
    private static final String TAG = "TransivaTLS";
    private static final String HOST = "transiva.my.id";
    private static final String PREF = "transiva_tls_pins_v2";
    private static final String KEY_CURRENT = "current_spki";
    private static final String KEY_BACKUP = "backup_spki";
    private static final String KEY_SEEN_AT = "current_seen_at";
    private static volatile boolean installed;

    private AdaptiveTlsPinning() {}

    public static synchronized void install(Context context) {
        if (installed || context == null) return;

        final Context app = context.getApplicationContext();
        final HostnameVerifier platform = HttpsURLConnection.getDefaultHostnameVerifier();

        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> {
            // Jangan pernah melewati verifikasi hostname bawaan Android.
            if (!platform.verify(hostname, session)) return false;

            // Host lain cukup memakai verifikasi platform.
            if (!HOST.equalsIgnoreCase(hostname)) return true;

            // Untuk host Transiva, SPKI hanya dipakai sebagai continuity/audit.
            // Kegagalan pencatatan tidak boleh memutus koneksi TLS yang sah.
            try {
                rememberPresentedKey(app, session);
            } catch (Throwable e) {
                Log.w(TAG, "Tidak dapat mencatat SPKI; TLS platform tetap valid", e);
            }
            return true;
        });

        installed = true;
    }

    private static void rememberPresentedKey(Context context, SSLSession session) throws Exception {
        Certificate[] chain = session.getPeerCertificates();
        if (chain == null || chain.length == 0) return;

        Set<String> presented = new LinkedHashSet<>();
        for (Certificate c : chain) {
            if (c instanceof X509Certificate) {
                presented.add(spki((X509Certificate) c));
            }
        }
        if (presented.isEmpty()) return;

        String next = presented.iterator().next();
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String current = p.getString(KEY_CURRENT, "");
        String backup = p.getString(KEY_BACKUP, "");

        if (next.equals(current) || next.equals(backup)) return;

        SharedPreferences.Editor edit = p.edit();
        if (!current.isEmpty()) edit.putString(KEY_BACKUP, current);
        edit.putString(KEY_CURRENT, next);
        edit.putLong(KEY_SEEN_AT, System.currentTimeMillis());
        edit.apply();

        if (!current.isEmpty()) {
            Log.i(TAG, "Sertifikat Transiva berubah dan lolos validasi TLS Android; SPKI diperbarui");
        }
    }

    private static String spki(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(certificate.getPublicKey().getEncoded());
        return "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP);
    }
}
