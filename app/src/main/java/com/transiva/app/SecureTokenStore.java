package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only the authentication token encrypted with an Android Keystore key. */
public final class SecureTokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "transiva_customer_auth_v1";
    private static final String PREF = "transiva_secure_auth";
    private static final String CIPHER_TEXT = "token_cipher";
    private static final String IV = "token_iv";

    private SecureTokenStore() {}

    public static boolean put(Context context, String token) {
        if (context == null) return false;
        String clean = token == null ? "" : token.trim();
        if (clean.isEmpty()) { clear(context); return true; }
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(clean.getBytes(StandardCharsets.UTF_8));
            context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Throwable error) {
            TransivaCrashReporter.record(error, "secure_token_write", "keystore");
            return false;
        }
    }

    public static String get(Context context) {
        if (context == null) return "";
        try {
            SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String cipherText = p.getString(CIPHER_TEXT, "");
            String iv = p.getString(IV, "");
            if (cipherText == null || cipherText.isEmpty() || iv == null || iv.isEmpty()) return "";
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            java.security.Key key = ks.getKey(ALIAS, null);
            if (!(key instanceof SecretKey)) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, (SecretKey) key,
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (Throwable error) {
            // Key can become invalid after lock-screen/security changes. Fail closed.
            clear(context);
            TransivaCrashReporter.record(error, "secure_token_read", "keystore");
            return "";
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        try { context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply(); }
        catch (Throwable ignored) { }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        java.security.Key existing = ks.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
