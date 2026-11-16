package com.transiva.app;

import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class TransPayApprovalActivity extends CustomerAdaptiveFragmentActivity {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String STORE = "transpay_biometric_checkout";
    private TransPayCheckoutApproval.Request request;
    private EditText pinInput;
    private CheckBox enroll;
    private Button verify;
    private Button biometric;
    private TextView status;
    private boolean busy;

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        request = TransPayCheckoutApproval.find(getIntent().getStringExtra("request_id"));
        if (request == null || !request.session.matches(this)) {
            finish();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(22 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView help = new TextView(this);
        long amount = request.payload.optLong("approved_price", -1);
        String instruction = "Setujui pembayaran untuk pesanan ini dengan PIN Transiva atau sidik jari.";
        if (amount >= 0) {
            java.text.NumberFormat rupiah = java.text.NumberFormat.getIntegerInstance(new java.util.Locale("id", "ID"));
            instruction += "\nTotal TransPay Rp" + rupiah.format(amount);
        }
        help.setText(instruction);
        content.addView(help);
        pinInput = new EditText(this);
        pinInput.setHint("PIN 6 digit");
        pinInput.setSingleLine(true);
        pinInput.setSaveEnabled(false);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        content.addView(pinInput);
        status = new TextView(this);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);

        biometric = new Button(this);
        biometric.setText("Gunakan sidik jari");
        biometric.setOnClickListener(view -> unlockBiometric());
        content.addView(biometric);
        enroll = new CheckBox(this);
        enroll.setText("Aktifkan sidik jari untuk checkout berikutnya");
        content.addView(enroll);
        updateBiometricOptions();

        android.app.AlertDialog dialog = new TransivaAlertDialogBuilder(this)
                .setTitle("Verifikasi TransPay")
                .setView(content)
                .setPositiveButton("Setujui pembayaran", null)
                .setNegativeButton("Batalkan", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .create();
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        verify = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        verify.setOnClickListener(view -> {
            String value = pinInput.getText().toString();
            if (!value.matches("[0-9]{6}")) {
                pinInput.setError("Masukkan 6 digit PIN.");
                return;
            }
            pinInput.getText().clear();
            verifyPin(value.getBytes(StandardCharsets.UTF_8), enroll.isChecked(), false);
        });
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (TransPayCheckoutApproval.find(request.id) == null) finish();
        }, 151_000);
    }

    private void enableInputs(boolean enabled) {
        busy = !enabled;
        verify.setEnabled(enabled);
        biometric.setEnabled(enabled);
        enroll.setEnabled(enabled);
        pinInput.setEnabled(enabled);
    }

    private void verifyPin(byte[] pin, boolean remember, boolean fromBiometric) {
        if (busy) {
            Arrays.fill(pin, (byte) 0);
            return;
        }
        enableInputs(false);
        status.setText("Memeriksa PIN…");
        new Thread(() -> {
            String proof = null;
            String error = "PIN belum dapat diperiksa. Periksa koneksi lalu coba lagi.";
            boolean invalid = false;
            try {
                JSONObject body = new JSONObject()
                        .put("pin", new String(pin, StandardCharsets.UTF_8))
                        .put("order_route", request.route)
                        .put("order_attempt", request.attempt)
                        .put("order_payload", request.payload);
                JSONObject response = TransivaHttpRepository.postJson(this, ApiConfig.server("pin_verify.php"),
                        body, 12_000, request.session);
                proof = response.optString("checkout_proof");
                if (proof.isEmpty()) {
                    proof = null;
                    error = "Server belum mendukung persetujuan checkout. Coba lagi setelah server diperbarui.";
                }
            } catch (TransivaHttpRepository.ServerResponseException rejected) {
                invalid = rejected.response != null && "PIN_INVALID".equals(rejected.response.optString("code"));
                error = rejected.getMessage();
            } catch (Exception unavailable) {
                // A connectivity error does not authorize a payment or consume the local credential.
            }
            String approved = proof;
            String message = error;
            boolean removeBiometric = fromBiometric && invalid;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || TransPayCheckoutApproval.find(request.id) == null
                        || !request.session.matches(this)) {
                    Arrays.fill(pin, (byte) 0);
                    finish();
                } else if (approved == null) {
                    Arrays.fill(pin, (byte) 0);
                    if (removeBiometric) clearBiometric();
                    enableInputs(true);
                    status.setText(message);
                } else if (remember && strongBiometric()) {
                    enrollBiometric(pin, approved);
                } else {
                    Arrays.fill(pin, (byte) 0);
                    approve(approved);
                }
            });
        }, "transpay-pin").start();
    }

    private boolean strongBiometric() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private String alias() {
        return "transpay_checkout_" + request.user;
    }

    private android.content.SharedPreferences credentials() {
        return getSharedPreferences(STORE, MODE_PRIVATE);
    }

    private void updateBiometricOptions() {
        boolean supported = strongBiometric();
        boolean saved = supported && BiometricSecurityManager.isEnabled(this)
                && credentials().contains(alias());
        biometric.setVisibility(saved ? Button.VISIBLE : Button.GONE);
        enroll.setVisibility(supported && !saved ? CheckBox.VISIBLE : CheckBox.GONE);
        enroll.setChecked(supported && !saved && BiometricSecurityManager.isEnabled(this));
    }

    private void clearBiometric() {
        credentials().edit().remove(alias()).remove(alias() + "_iv").apply();
        try {
            KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
            store.load(null);
            store.deleteEntry(alias());
        } catch (Exception ignored) {
        }
        updateBiometricOptions();
    }

    private void unlockBiometric() {
        if (busy) return;
        try {
            KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
            store.load(null);
            SecretKey key = (SecretKey) store.getKey(alias(), null);
            byte[] encrypted = Base64.decode(credentials().getString(alias(), ""), Base64.NO_WRAP);
            byte[] iv = Base64.decode(credentials().getString(alias() + "_iv", ""), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            enableInputs(false);
            prompt("Setujui pembayaran TransPay", cipher, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    enableInputs(true);
                    try {
                        verifyPin(result.getCryptoObject().getCipher().doFinal(encrypted), false, true);
                    } catch (Exception invalidated) {
                        clearBiometric();
                        status.setText("Verifikasi sidik jari perlu diaktifkan kembali. Gunakan PIN.");
                    }
                }

                @Override public void onAuthenticationError(int code, CharSequence message) {
                    enableInputs(true);
                    pinInput.requestFocus();
                    status.setText("Masukkan PIN untuk menyetujui pembayaran.");
                }
            });
        } catch (Exception unavailable) {
            enableInputs(true);
            clearBiometric();
            status.setText("Verifikasi sidik jari perlu diaktifkan kembali. Gunakan PIN.");
        }
    }

    private void enrollBiometric(byte[] pin, String proof) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(alias(),
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= 30) {
                spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
            } else {
                spec.setUserAuthenticationValidityDurationSeconds(-1);
            }
            if (Build.VERSION.SDK_INT >= 24) spec.setInvalidatedByBiometricEnrollment(true);
            generator.init(spec.build());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, generator.generateKey());
            prompt("Aktifkan sidik jari untuk TransPay", cipher, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    try {
                        Cipher unlocked = result.getCryptoObject().getCipher();
                        String encrypted = Base64.encodeToString(unlocked.doFinal(pin), Base64.NO_WRAP);
                        credentials().edit().putString(alias(), encrypted)
                                .putString(alias() + "_iv", Base64.encodeToString(unlocked.getIV(), Base64.NO_WRAP)).apply();
                        BiometricSecurityManager.setEnabled(TransPayApprovalActivity.this, true);
                    } catch (Exception unavailable) {
                        clearBiometric();
                    } finally {
                        Arrays.fill(pin, (byte) 0);
                    }
                    approve(proof);
                }

                @Override public void onAuthenticationError(int code, CharSequence message) {
                    Arrays.fill(pin, (byte) 0);
                    approve(proof);
                }
            });
        } catch (Exception unavailable) {
            Arrays.fill(pin, (byte) 0);
            approve(proof);
        }
    }

    private void prompt(String title, Cipher cipher, BiometricPrompt.AuthenticationCallback callback) {
        new BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setNegativeButtonText("Gunakan PIN")
                        .build(), new BiometricPrompt.CryptoObject(cipher));
    }

    private void approve(String proof) {
        if (!isFinishing() && request.session.matches(this)) request.complete(proof);
        finish();
    }

    @Override protected void onDestroy() {
        if (request != null && !isChangingConfigurations()) request.complete(null);
        super.onDestroy();
    }
}
