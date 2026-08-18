package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

public final class BiometricSecurityManager {
    private static final String PREF = "transiva_biometric_security";
    private static final String KEY_ENABLED = "enabled";
    private BiometricSecurityManager() {}

    public interface Callback { void onSuccess(); void onUnavailable(String message); }

    public static boolean canUse(Context c) {
        int r = BiometricManager.from(c).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return r == BiometricManager.BIOMETRIC_SUCCESS;
    }
    public static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false) && canUse(c);
    }
    public static void setEnabled(Context c, boolean enabled) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }
    public static void authenticate(FragmentActivity a, String title, String subtitle, Callback cb) {
        if (!canUse(a)) { cb.onUnavailable("Biometrik kuat belum tersedia atau belum didaftarkan di perangkat ini."); return; }
        BiometricPrompt prompt = new BiometricPrompt(a, ContextCompat.getMainExecutor(a), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { super.onAuthenticationSucceeded(result); cb.onSuccess(); }
            @Override public void onAuthenticationError(int code, CharSequence msg) { super.onAuthenticationError(code, msg); if (code != BiometricPrompt.ERROR_NEGATIVE_BUTTON && code != BiometricPrompt.ERROR_USER_CANCELED) cb.onUnavailable(msg == null ? "Autentikasi biometrik gagal." : msg.toString()); }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Gunakan PIN").build();
        prompt.authenticate(info);
    }
}
