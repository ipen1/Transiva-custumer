package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public class RegisterActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String SEND_OTP_URL = BASE_URL + "server/sendEmailOtp.php";
    private static final String VERIFY_OTP_URL = BASE_URL + "server/verifyEmailOtp.php";
    private static final String REGISTER_URL = BASE_URL + "server/register.php";
    private static final String PRIVACY_URL = BASE_URL + "privacy.html";
    private static final String TERMS_URL = BASE_URL + "terms.html";
    private static final int TIMEOUT_MS = 25000;

    private boolean otpVerified = false;
    private boolean loading = false;
    private String lastEmail = "";

    private EditText usernameInput;
    private EditText emailInput;
    private EditText phoneInput;
    private EditText otpInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;

    private TextView messageText;
    private TextView otpStatusText;
    private Button sendOtpBtn;
    private Button verifyOtpBtn;
    private Button registerBtn;
    private ProgressBar progressBar;

    private CountDownTimer resendTimer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.parseColor("#0A1A2E"));
            getWindow().setNavigationBarColor(Color.parseColor("#0A1A2E"));
        } catch (Exception ignored) {}
        buildLayout();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
    }

    private void buildLayout() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F4F8FF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        ImageView logo = new ImageView(this);
        int logoRes = findDrawable("transiva_logo");
        if (logoRes == 0) logoRes = findDrawable("logo_transiva");
        if (logoRes == 0) logoRes = findDrawable("logo");
        if (logoRes == 0) logoRes = getApplicationInfo().icon;
        logo.setImageResource(logoRes);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(165), dp(62));
        logoLp.setMargins(0, dp(2), 0, dp(4));
        root.addView(logo, logoLp);

        TextView title = text("Daftar Transiva", 25, "#123F7A", true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("Buat akun untuk mulai menggunakan layanan", 14, "#667085", false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(4), dp(6), dp(4), dp(14));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(roundStroke("#FFFFFF", "#EEF3FA", dp(24), 1));
        card.setElevation(dp(5));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(18));
        root.addView(card, cardLp);

        messageText = text("", 12, "#B91C1C", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(0, 0, 0, dp(10));
        card.addView(messageText, msgLp);

        card.addView(label("Nama Pengguna"));
        usernameInput = input("Masukkan Nama Pengguna", InputType.TYPE_CLASS_TEXT, 30);
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(usernameInput, fieldLp());

        card.addView(label("Email"));
        emailInput = input("contoh@email.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 80);
        emailInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(emailInput, fieldLp());

        card.addView(label("No HP"));
        phoneInput = phoneInput("81234567890");
        phoneInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(phoneInput, fieldLp());

        sendOtpBtn = secondaryButton("Kirim OTP");
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-1, dp(44));
        sendLp.setMargins(0, 0, 0, dp(10));
        card.addView(sendOtpBtn, sendLp);

        card.addView(label("Kode OTP"));
        LinearLayout otpRow = new LinearLayout(this);
        otpRow.setOrientation(LinearLayout.HORIZONTAL);
        otpRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams otpRowLp = new LinearLayout.LayoutParams(-1, dp(48));
        otpRowLp.setMargins(0, 0, 0, dp(8));
        card.addView(otpRow, otpRowLp);

        otpInput = input("6 digit OTP", InputType.TYPE_CLASS_NUMBER, 6);
        otpInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        LinearLayout.LayoutParams otpInputLp = new LinearLayout.LayoutParams(0, -1, 1f);
        otpInputLp.setMargins(0, 0, dp(8), 0);
        otpRow.addView(otpInput, otpInputLp);

        verifyOtpBtn = secondaryButton("Verifikasi");
        otpRow.addView(verifyOtpBtn, new LinearLayout.LayoutParams(dp(108), -1));

        otpStatusText = text("OTP boleh dikosongkan, admin dapat verifikasi manual.", 11, "#667085", false);
        otpStatusText.setGravity(Gravity.CENTER);
        otpStatusText.setPadding(0, 0, 0, dp(8));
        card.addView(otpStatusText, new LinearLayout.LayoutParams(-1, -2));

        card.addView(label("Kata Sandi"));
        passwordInput = input("Masukkan Kata Sandi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 100);
        passwordInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        card.addView(passwordInput, fieldLp());

        card.addView(label("Konfirmasi Kata Sandi"));
        confirmPasswordInput = input("Ulangi Kata Sandi", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 100);
        confirmPasswordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        card.addView(confirmPasswordInput, fieldLp());

        registerBtn = new Button(this);
        registerBtn.setAllCaps(false);
        registerBtn.setText("Buat Akun   →");
        registerBtn.setTextSize(16);
        registerBtn.setTypeface(Typeface.DEFAULT_BOLD);
        registerBtn.setTextColor(Color.WHITE);
        registerBtn.setBackground(roundGradient("#006BEF", "#2E9BFF", dp(16)));
        LinearLayout.LayoutParams registerLp = new LinearLayout.LayoutParams(-1, dp(52));
        registerLp.setMargins(0, dp(6), 0, dp(14));
        card.addView(registerBtn, registerLp);

        TextView toLogin = text("Sudah punya akun? Masuk", 14, "#1685F2", true);
        toLogin.setGravity(Gravity.CENTER);
        toLogin.setPadding(0, dp(2), 0, dp(14));
        card.addView(toLogin, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout legal = new LinearLayout(this);
        legal.setGravity(Gravity.CENTER);
        legal.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(legal, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = text("Kebijakan Privasi", 12, "#1685F2", true);
        TextView sep = text("   |   ", 12, "#CBD5E1", false);
        TextView terms = text("Syarat & Ketentuan", 12, "#1685F2", true);
        legal.addView(privacy);
        legal.addView(sep);
        legal.addView(terms);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(dp(52), dp(52));
        progressLp.gravity = Gravity.CENTER;
        page.addView(progressBar, progressLp);

        setContentView(page);

        usernameInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String clean = cleanUsername(s.toString());
                if (!clean.equals(s.toString())) {
                    usernameInput.setText(clean);
                    usernameInput.setSelection(clean.length());
                }
            }
        });

        emailInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String currentEmail = cleanEmail(s.toString());
                if (!lastEmail.isEmpty() && !currentEmail.equals(lastEmail)) resetOtpState();
            }
        });

        phoneInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String raw = s == null ? "" : s.toString();
                String clean = cleanPhoneForInput(raw);
                if (!clean.equals(raw)) {
                    phoneInput.setText(clean);
                    phoneInput.setSelection(clean.length());
                }
            }
        });

        otpInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(android.text.Editable s) {
                String clean = s.toString().replaceAll("\\D", "");
                if (clean.length() > 6) clean = clean.substring(0, 6);
                if (!clean.equals(s.toString())) {
                    otpInput.setText(clean);
                    otpInput.setSelection(clean.length());
                }
            }
        });

        sendOtpBtn.setOnClickListener(v -> sendOtp());
        verifyOtpBtn.setOnClickListener(v -> verifyOtp());
        registerBtn.setOnClickListener(v -> registerUser());
        toLogin.setOnClickListener(v -> openLogin());
        privacy.setOnClickListener(v -> openBrowser(PRIVACY_URL));
        terms.setOnClickListener(v -> openBrowser(TERMS_URL));

        confirmPasswordInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                registerUser();
                return true;
            }
            return false;
        });
    }

    private void sendOtp() {
        if (loading) return;
        clearMessage();
        String email = cleanEmail(emailInput.getText().toString());
        if (email.isEmpty()) { showMessage("Email wajib diisi", false); return; }
        if (!isValidEmail(email)) { showMessage("Format email tidak valid", false); return; }

        otpVerified = false;
        lastEmail = "";
        setGlobalLoading(true, sendOtpBtn, "Mengirim...");

        runNetwork(() -> {
            JSONObject result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                result = postJson(SEND_OTP_URL, payload);
            } catch (Exception e) {
                result = error("OTP gagal dikirim. Anda tetap bisa daftar, nanti admin dapat memverifikasi manual.");
            }
            JSONObject finalResult = result;
            mainHandler.post(() -> {
                setGlobalLoading(false, sendOtpBtn, "Kirim OTP");
                if (!finalResult.optBoolean("success", false)) {
                    showMessage(finalResult.optString("message", "Gagal mengirim OTP"), false);
                    return;
                }
                lastEmail = email;
                otpStatusText.setText("Kode OTP sudah dikirim ke email.");
                otpStatusText.setTextColor(Color.parseColor("#166534"));
                showMessage("Kode OTP berhasil dikirim", true);
                startResendCooldown();
                otpInput.requestFocus();
            });
        });
    }

    private void verifyOtp() {
        if (loading) return;
        clearMessage();
        String email = cleanEmail(emailInput.getText().toString());
        String otp = otpInput.getText().toString().trim();
        if (email.isEmpty() || !isValidEmail(email)) { showMessage("Email tidak valid", false); return; }
        if (!otp.matches("^[0-9]{6}$")) { showMessage("OTP harus 6 angka", false); return; }
        if (lastEmail.isEmpty() || !email.equals(lastEmail)) { showMessage("Kirim OTP ke email ini terlebih dahulu", false); return; }

        setGlobalLoading(true, verifyOtpBtn, "Cek...");
        runNetwork(() -> {
            JSONObject result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("otp", otp);
                result = postJson(VERIFY_OTP_URL, payload);
            } catch (Exception e) {
                result = error("Server error saat verifikasi OTP");
            }
            JSONObject finalResult = result;
            mainHandler.post(() -> {
                setGlobalLoading(false, verifyOtpBtn, "Verifikasi");
                if (!finalResult.optBoolean("success", false)) {
                    otpVerified = false;
                    showMessage(finalResult.optString("message", "OTP salah"), false);
                    return;
                }
                otpVerified = true;
                lastEmail = email;
                emailInput.setEnabled(false);
                otpInput.setEnabled(false);
                verifyOtpBtn.setEnabled(false);
                verifyOtpBtn.setText("Verified");
                verifyOtpBtn.setBackground(roundGradient("#16A34A", "#22C55E", dp(16)));
                otpStatusText.setText("Email berhasil diverifikasi.");
                otpStatusText.setTextColor(Color.parseColor("#166534"));
                showMessage("Email berhasil diverifikasi", true);
                passwordInput.requestFocus();
            });
        });
    }

    private void registerUser() {
        if (loading) return;
        clearMessage();
        String username = cleanUsername(usernameInput.getText().toString());
        String email = cleanEmail(emailInput.getText().toString());
        String phone = normalizePhone62(phoneInput.getText().toString());
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        if (username.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) { showMessage("Semua field wajib diisi", false); return; }
        if (username.length() < 3) { showMessage("Nama Pengguna minimal 3 karakter", false); return; }
        if (!USERNAME_PATTERN.matcher(username).matches()) { showMessage("Nama Pengguna hanya boleh huruf, angka, titik, strip, dan underscore", false); return; }
        if (!isValidEmail(email)) { showMessage("Format email tidak valid", false); return; }
        if (!isValidPhone62(phone)) { showMessage("No HP tidak valid. Contoh: 81234567890", false); return; }
        if (password.length() < 5) { showMessage("Kata Sandi minimal 5 karakter", false); return; }
        if (!password.equals(confirmPassword)) { showMessage("Konfirmasi Kata Sandi tidak cocok", false); return; }

        int emailVerified = otpVerified && email.equals(lastEmail) ? 1 : 0;
        setGlobalLoading(true, registerBtn, "Membuat...");

        runNetwork(() -> {
            JSONObject result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("username", username);
                payload.put("email", email);
                payload.put("phone", phone);
                payload.put("no_hp", phone);
                payload.put("phone_number", phone);
                payload.put("password", password);
                payload.put("role", "customer");
                payload.put("email_verified", emailVerified);
                result = postJson(REGISTER_URL, payload);
            } catch (Exception e) {
                result = error("Server error");
            }
            JSONObject finalResult = result;
            mainHandler.post(() -> {
                setGlobalLoading(false, registerBtn, "Buat Akun   →");
                if (!finalResult.optBoolean("success", false)) {
                    showMessage(finalResult.optString("message", "Gagal membuat akun"), false);
                    return;
                }
                showMessage(emailVerified == 1 ? "Akun berhasil dibuat dan email sudah terverifikasi" : "Akun berhasil dibuat. Email belum terverifikasi.", true);
                mainHandler.postDelayed(this::openLogin, 1200);
            });
        });
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is).trim();
            if (body.isEmpty()) return error("Server tidak mengirim response");
            JSONObject json = new JSONObject(body);
            if (!json.has("success")) json.put("success", code >= 200 && code < 300);
            return json;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private void startResendCooldown() {
        if (resendTimer != null) resendTimer.cancel();
        sendOtpBtn.setEnabled(false);
        resendTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long millisUntilFinished) { sendOtpBtn.setText("Kirim ulang " + (millisUntilFinished / 1000) + "d"); }
            @Override public void onFinish() { sendOtpBtn.setEnabled(true); sendOtpBtn.setText("Kirim Ulang OTP"); }
        };
        resendTimer.start();
    }

    private void resetOtpState() {
        otpVerified = false;
        lastEmail = "";
        otpInput.setText("");
        otpInput.setEnabled(true);
        emailInput.setEnabled(true);
        verifyOtpBtn.setEnabled(true);
        verifyOtpBtn.setText("Verifikasi");
        verifyOtpBtn.setBackground(roundGradient("#006BEF", "#2E9BFF", dp(16)));
        otpStatusText.setText("OTP boleh dikosongkan, admin dapat verifikasi manual.");
        otpStatusText.setTextColor(Color.parseColor("#667085"));
    }

    private void setGlobalLoading(boolean value, Button activeButton, String text) {
        loading = value;
        progressBar.setVisibility(value ? View.VISIBLE : View.GONE);
        usernameInput.setEnabled(!value);
        emailInput.setEnabled(!value || otpVerified);
        phoneInput.setEnabled(!value);
        otpInput.setEnabled(!value && !otpVerified);
        passwordInput.setEnabled(!value);
        confirmPasswordInput.setEnabled(!value);
        sendOtpBtn.setEnabled(!value);
        verifyOtpBtn.setEnabled(!value && !otpVerified);
        registerBtn.setEnabled(!value);
        activeButton.setText(text);
        activeButton.setAlpha(value ? 0.75f : 1f);
        if (!value) activeButton.setAlpha(1f);
    }

    private JSONObject error(String message) {
        JSONObject obj = new JSONObject();
        try { obj.put("success", false); obj.put("message", message == null || message.length() == 0 ? "Server error" : message); } catch (Exception ignored) {}
        return obj;
    }

    private void showMessage(String message, boolean success) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(message);
        messageText.setTextColor(Color.parseColor(success ? "#166534" : "#B91C1C"));
        messageText.setBackground(round(success ? "#DCFCE7" : "#FEE2E2", dp(12)));
    }

    private void clearMessage() {
        messageText.setVisibility(View.GONE);
        messageText.setText("");
    }

    private TextView label(String value) {
        TextView tv = text(value, 14, "#123F7A", true);
        tv.setPadding(0, dp(5), 0, dp(6));
        return tv;
    }

    private EditText input(String hint, int inputType, int maxLength) {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setTextColor(Color.parseColor("#1F2937"));
        et.setHintTextColor(Color.parseColor("#98A2B3"));
        et.setHint(hint);
        et.setInputType(inputType);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        et.setPadding(dp(16), 0, dp(16), 0);
        et.setBackground(roundStroke("#FFFFFF", "#D8E1ED", dp(16), 1));
        return et;
    }

    private EditText phoneInput(String hint) {
        EditText et = input(hint, InputType.TYPE_CLASS_PHONE, 15);
        et.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        et.setText("");
        et.setHint(hint);
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private Button secondaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setAllCaps(false);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(roundGradient("#006BEF", "#2E9BFF", dp(16)));
        return btn;
    }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(color));
        gd.setCornerRadius(radius);
        return gd;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int width) {
        GradientDrawable gd = round(color, radius);
        gd.setStroke(dp(width), Color.parseColor(stroke));
        return gd;
    }

    private GradientDrawable roundGradient(String start, String end, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Color.parseColor(start), Color.parseColor(end)});
        gd.setCornerRadius(radius);
        return gd;
    }

    private boolean isValidEmail(String email) { return EMAIL_PATTERN.matcher(email).matches(); }

    private String cleanUsername(String value) {
        return String.valueOf(value == null ? "" : value).trim().replaceAll("\\s+", "").toLowerCase(Locale.US);
    }

    private String cleanEmail(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.US);
    }

    private String cleanPhoneForInput(String value) {
        String digits = String.valueOf(value == null ? "" : value).replaceAll("\\D", "");
        if (digits.startsWith("62")) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() > 13) digits = digits.substring(0, 13);
        return digits;
    }

    private String normalizePhone62(String value) {
        String digits = String.valueOf(value == null ? "" : value).replaceAll("\\D", "");
        if (digits.startsWith("0")) digits = digits.substring(1);
        if (digits.startsWith("62")) return digits;
        return digits.length() == 0 ? "" : "62" + digits;
    }

    private boolean isValidPhone62(String phone) {
        String clean = normalizePhone62(phone);
        return clean.matches("^62[0-9]{9,13}$");
    }

    private void openLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openBrowser(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) {}
    }

    private void runNetwork(Runnable runnable) { new Thread(runnable).start(); }

    private int findDrawable(String name) {
        try { return getResources().getIdentifier(name, "drawable", getPackageName()); }
        catch (Exception e) { return 0; }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}