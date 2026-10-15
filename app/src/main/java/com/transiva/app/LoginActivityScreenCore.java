package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
class LoginActivityScreenCore extends FragmentActivity {



    protected static final String TAG = "TRANSIVA_LOGIN";
    protected static final String BASE_URL = "https://transiva.my.id/";
    protected static final String LOGIN_URL = BASE_URL + "server/customer_login_native.php";
    protected static final String SAVE_FCM_URL =
            BASE_URL + "server/save_fcm_token.php";
    protected static final String PRIVACY_URL = BASE_URL + "privacy-policy.html";
    protected static final String TERMS_URL = BASE_URL + "terms.html";
    protected static final int TIMEOUT_MS = 25000;
    protected static final int RC_GOOGLE_SIGN_IN = 9101;
    protected static final String GOOGLE_LOGIN_URL = BASE_URL + "server/customer_google_login_native.php";

    protected final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    protected EditText usernameInput;
    protected EditText passwordInput;
    protected Button loginButton;
    protected TextView messageText;
    protected ProgressBar loadingView;
    protected ImageButton eyeButton;
    protected Button googleButton;
    protected TextView biometricLoginButton;
    protected GoogleSignInClient googleSignInClient;

    private final CustomerLifecycleNetworkScope authScope=new CustomerLifecycleNetworkScope();
    private int loginStep;
    private String authSessionToken="";
    private TextView stepLabel,usernameLabel,passwordLabel,previous;
    private FrameLayout loginPasswordBox;
    private LinearLayout loginIdentityCard;
    private ImageView loginAvatar;
    private TextView loginInitial,loginIdentityName;
    private AuthPublicRequest previewRequest;
    private AuthPublicRequest googleSignupLookup;
    private long previewGeneration;
    private String googleSignupUsername="",googleSignupPhone="";
    protected boolean loading;
    protected boolean passwordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().setStatusBarColor(
                    Color.parseColor("#0A1A2E"));
            getWindow().setNavigationBarColor(
                    Color.parseColor("#0A1A2E"));
        } catch (Exception ignored) {}

        /*
         * Jangan otomatis mengarahkan berdasarkan session lama di sini.
         * Session invalid akan dibersihkan saat login/dashboard.
         */
        authSessionToken=safe(new SessionManager(this).getToken());
        setContentView(buildScreen());
        CustomerResponsiveUi.apply(this);
        passwordInput.setSaveEnabled(false);
        if(getIntent().getBooleanExtra("account_deleted",false)) showMessage("Akun Anda sudah berhasil dihapus. Terima kasih telah menggunakan Transiva.",true);
        if(savedInstanceState!=null){usernameInput.setText(savedInstanceState.getString("login_username",""));loginStep=0;updateLoginStep();if(savedInstanceState.getInt("login_step",0)==1)authScope.post(mainHandler,this::loadLoginPreview);}
        if(getIntent().getBooleanExtra("start_google",false))authScope.post(mainHandler,this::startGoogleLogin);

        try {
            TransivaNotificationPermission.ask(this);
        } catch (Exception error) {
            Log.w(TAG, "Permission notifikasi gagal", error);
        }
    }

    protected View buildScreen() {
        FrameLayout page = new FrameLayout(this);
        page.setBackground(CustomerAuthDesign.backdrop());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new CustomerAuthDesign.Column(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL));

        root.addView(CustomerAuthDesign.brand(this, "Selamat datang kembali", "Perjalanan nyaman dimulai dari sini."), new LinearLayout.LayoutParams(-1,-2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        card.setBackground(
                CustomerAuthDesign.surface(this));
        card.setElevation(dp(5));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        messageText = text("", 12, "#B91C1C", true);
        messageText.setVisibility(View.GONE);
        messageText.setPadding(dp(14), dp(11), dp(14), dp(11));

        LinearLayout.LayoutParams messageLp =
                new LinearLayout.LayoutParams(-1, -2);
        messageLp.setMargins(0, 0, 0, dp(13));
        card.addView(messageText, messageLp);

        stepLabel=text("LANGKAH 1 DARI 2 • IDENTITAS",12,"#1685F2",true);
        stepLabel.setPadding(0,0,0,dp(12));card.addView(stepLabel);
        usernameLabel=label("Nama Pengguna");card.addView(usernameLabel);

        usernameInput = input(
                "Masukkan Nama Pengguna",
                InputType.TYPE_CLASS_TEXT,
                false
        );
        card.addView(usernameInput, fieldLayout());

        loginIdentityCard=new LinearLayout(this);loginIdentityCard.setOrientation(LinearLayout.HORIZONTAL);loginIdentityCard.setGravity(Gravity.CENTER_VERTICAL);loginIdentityCard.setPadding(dp(12),dp(14),dp(12),dp(14));loginIdentityCard.setBackground(round("#F3F7FD",dp(16)));
        LinearLayout.LayoutParams identityLp=new LinearLayout.LayoutParams(-1,-2);identityLp.bottomMargin=dp(12);card.addView(loginIdentityCard,identityLp);
        FrameLayout circle=new FrameLayout(this);circle.setBackground(avatarOval("#DDEBFF"));loginIdentityCard.addView(circle,new LinearLayout.LayoutParams(dp(68),dp(68)));
        loginInitial=text("",25,"#1676ED",true);loginInitial.setGravity(Gravity.CENTER);loginInitial.setBackground(avatarOval("#FFFFFF"));FrameLayout.LayoutParams initialLp=new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.CENTER);circle.addView(loginInitial,initialLp);
        loginAvatar=new ImageView(this);loginAvatar.setBackground(avatarOval("#00FFFFFF"));loginAvatar.setClipToOutline(true);loginAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);loginAvatar.setContentDescription("Foto profil akun");circle.addView(loginAvatar,new FrameLayout.LayoutParams(dp(60),dp(60),Gravity.CENTER));
        loginIdentityName=text("",16,"#123F7A",true);loginIdentityName.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);loginIdentityName.setPadding(dp(12),0,0,0);loginIdentityName.setMaxLines(2);loginIdentityName.setEllipsize(android.text.TextUtils.TruncateAt.END);loginIdentityCard.addView(loginIdentityName,new LinearLayout.LayoutParams(0,-2,1f));
        passwordLabel=label("Kata Sandi");card.addView(passwordLabel);

        FrameLayout passwordBox = new FrameLayout(this);loginPasswordBox=passwordBox;passwordBox.setMinimumHeight(dp(48));

        passwordInput = input(
                "Masukkan Kata Sandi",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                true
        );
        passwordBox.addView(
                passwordInput,
                new FrameLayout.LayoutParams(-1, -2)
        );

        eyeButton = new ImageButton(this);
        eyeButton.setImageResource(
                android.R.drawable.ic_menu_view);
        eyeButton.setBackgroundColor(Color.TRANSPARENT);
        eyeButton.setColorFilter(Color.parseColor("#1E88F5"));

        FrameLayout.LayoutParams eyeLp =
                new FrameLayout.LayoutParams(dp(44), dp(44));
        eyeLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        eyeLp.rightMargin = dp(5);
        passwordBox.addView(eyeButton, eyeLp);

        card.addView(passwordBox, fieldLayout());

        loginButton = new Button(this);
        loginButton.setText("Masuk →");
        loginButton.setAllCaps(false);
        loginButton.setTextSize(15);loginButton.setMinHeight(dp(48));loginButton.setStateListAnimator(null);
        loginButton.setTextColor(Color.WHITE);
        loginButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        loginButton.setBackground(
                gradient("#006BEF", "#2E9BFF", dp(17)));

        LinearLayout.LayoutParams loginLp =
                new LinearLayout.LayoutParams(-1, -2);
        loginLp.setMargins(0, dp(8), 0, dp(14));
        card.addView(loginButton, loginLp);
        previous=text("← Ubah nama pengguna",14,"#1685F2",true);previous.setPadding(0,dp(8),0,dp(16));card.addView(previous);
        previous.setOnClickListener(v->{if(!loading)returnToUsername();});
        updateLoginStep();

        TextView or = text("atau", 12, "#94A3B8", false);
        or.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams orLp = new LinearLayout.LayoutParams(-1, -2);
        orLp.setMargins(0, dp(1), 0, dp(9));
        card.addView(or, orLp);

        googleButton = new Button(this);
        googleButton.setText("G   Lanjutkan dengan Google");
        googleButton.setAllCaps(false);
        googleButton.setTextSize(14);googleButton.setMinHeight(dp(48));googleButton.setStateListAnimator(null);
        googleButton.setTextColor(Color.parseColor("#17324D"));
        googleButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        googleButton.setBackground(roundStroke("#FFFFFF", "#D7E2EF", dp(17), 1));
        LinearLayout.LayoutParams googleLp = new LinearLayout.LayoutParams(-1, -2);
        googleLp.setMargins(0, 0, 0, dp(8));
        card.addView(googleButton, googleLp);

        biometricLoginButton = text("◉  Masuk dengan sidik jari / biometrik", 13, "#1685F2", true);
        biometricLoginButton.setGravity(Gravity.CENTER);biometricLoginButton.setMinHeight(dp(48));
        biometricLoginButton.setPadding(dp(8), dp(10), dp(8), dp(10));
        biometricLoginButton.setVisibility(BiometricSecurityManager.isEnabled(this) ? View.VISIBLE : View.GONE);
        card.addView(biometricLoginButton, new LinearLayout.LayoutParams(-1, -2));

        TextView register = text(
                "Belum punya akun? Daftar",
                14,
                "#1685F2",
                true
        );
        register.setGravity(Gravity.CENTER);
        register.setPadding(0, dp(3), 0, dp(15));
        card.addView(register);

        LinearLayout legal = new LinearLayout(this);
        legal.setGravity(Gravity.CENTER);

        TextView privacy =
                text("Kebijakan Privasi", 12, "#1685F2", true);
        TextView separator =
                text(" | ", 12, "#CBD5E1", false);
        TextView terms =
                text("Syarat & Ketentuan", 12, "#1685F2", true);

        legal.addView(privacy);
        legal.addView(separator);
        legal.addView(terms);
        card.addView(legal);

        loadingView = new ProgressBar(this);
        loadingView.setVisibility(View.GONE);

        FrameLayout.LayoutParams loadingLp =
                new FrameLayout.LayoutParams(
                        dp(52), dp(52), Gravity.CENTER);
        page.addView(loadingView, loadingLp);

        loginButton.setOnClickListener(v -> {
            if(loading)return;
            if(loginStep==0)loadLoginPreview();else attemptLogin();
        });
        usernameInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        usernameInput.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_NEXT){loginButton.performClick();return true;}return false;});
        googleButton.setOnClickListener(v -> startGoogleLogin());
        biometricLoginButton.setOnClickListener(v -> attemptBiometricLogin());
        eyeButton.setOnClickListener(v -> togglePassword());
        register.setOnClickListener(v -> openRegister());
        privacy.setOnClickListener(v -> openBrowser(PRIVACY_URL));
        terms.setOnClickListener(v -> openBrowser(TERMS_URL));

        passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordInput.setOnEditorActionListener(
                (v, actionId, event) -> {
                    boolean enter = event != null
                            && event.getKeyCode()
                            == KeyEvent.KEYCODE_ENTER;

                    if (actionId == EditorInfo.IME_ACTION_DONE
                            || enter) {
                        attemptLogin();
                        return true;
                    }
                    return false;
                }
        );

        initGoogleSignIn();
        return page;
    }

    protected void initGoogleSignIn() {
        int id = getResources().getIdentifier("google_web_client_id", "string", getPackageName());
        String clientId = id == 0 ? "" : getString(id).trim();
        if (clientId.isEmpty() || clientId.startsWith("REPLACE_")) {
            googleSignInClient = null;
            return;
        }
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(clientId)
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, options);
    }

    protected void startGoogleLogin() {
        if (loading) return;
        clearMessage();
        if (googleSignInClient == null) {
            showMessage("Masuk dengan Google belum tersedia saat ini. Gunakan nama pengguna dan kata sandi, atau hubungi bantuan Transiva.", false);
            return;
        }
        try {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                if(!authScope.isDestroyed()&&!isFinishing())startActivityForResult(googleSignInClient.getSignInIntent(),RC_GOOGLE_SIGN_IN);
            });
        } catch (Exception e) {
            showMessage("Tidak dapat membuka Google Login.", false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_GOOGLE_SIGN_IN) return;
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            String idToken = account == null ? "" : safe(account.getIdToken()).trim();
            if (idToken.isEmpty()) {
                showMessage("Konfirmasi Google belum selesai. Pilih kembali akun Google Anda.", false);
                return;
            }
            loginWithGoogleToken(idToken);
        } catch (ApiException e) {
            Log.w(TAG, "Google sign-in gagal code=" + e.getStatusCode(), e);
            showMessage("Masuk dengan Google belum selesai. Silakan pilih akun Anda kembali.", false);
        }
    }

    protected void loginWithGoogleToken(String idToken) {
        if (loading) return;
        authSessionToken=safe(new SessionManager(this).getToken());
        setLoading(true);
        runAuthNetwork(() -> {
            LoginResult result = doGoogleLogin(idToken);
            postAuth(() -> {
                setLoading(false);
                if("GOOGLE_REGISTRATION_REQUIRED".equals(result.message))showGoogleRegistration(idToken);
                else handleLoginResult(result);
            });
        });
    }

    protected void attemptBiometricLogin() {
        if (loading) return;
        SessionManager saved = new SessionManager(this);
        if (!saved.isLoggedIn() || safe(saved.getToken()).trim().isEmpty()) {
            showMessage("Biometrik dapat digunakan setelah akun berhasil login dan fitur biometrik diaktifkan di Pengaturan.", false);
            return;
        }
        BiometricSecurityManager.authenticate(this, "Masuk ke Transiva", "Verifikasi sidik jari / biometrik untuk melanjutkan.", new BiometricSecurityManager.Callback() {
            @Override public void onSuccess() { openPinPage("customer"); }
            @Override public void onUnavailable(String message) { showMessage(message, false); }
        });
    }

    protected void attemptLogin() {
        if (loading) return;

        clearMessage();

        String username =
                usernameInput.getText().toString().trim();
        String password =
                passwordInput.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage(
                    "Lengkapi Nama Pengguna dan Kata Sandi",
                    false
            );
            return;
        }

        authSessionToken=safe(new SessionManager(this).getToken());
        setLoading(true);

        runAuthNetwork(() -> {
            LoginResult result = doLogin(username, password);

            postAuth(() -> {
                setLoading(false);
                handleLoginResult(result);
            });
        });
    }

    protected void handleLoginResult(LoginResult result) {
        if(authScope.isDestroyed()||isFinishing()||!authSessionToken.equals(safe(new SessionManager(this).getToken())))return;
        if (!result.success) { showMessage(result.message, false); return; }
        if (result.user == null) { showMessage("Server tidak mengirim data pengguna.", false); return; }
        String apiToken = result.user.optString("token", "").trim();
        if (apiToken.isEmpty()) { showMessage("Proses masuk belum selesai. Silakan coba lagi.", false); return; }
        String role = normalizeRole(result.user.optString("role", result.role));
        try { result.user.put("role", role); } catch (Exception ignored) {}
        SessionManager session = new SessionManager(this);
        boolean saved;
        try {
            session.forceLogout("replace_login_session");
            saved = session.saveUser(result.user);
        } catch (Exception e) { Log.e(TAG, "Gagal menyimpan session", e); saved = false; }
        if (!saved || !session.isLoggedIn() || session.getUsername().trim().isEmpty() || session.getToken().trim().isEmpty()) {
            session.forceLogout("login_session_invalid");
            showMessage("Sesi belum dapat disimpan. Tutup aplikasi, lalu coba masuk kembali.", false);
            return;
        }
        try { TransivaSession.saveUser(this, result.user); } catch (Exception e) { Log.w(TAG, "Legacy session gagal", e); }
        authSessionToken=safe(session.getToken());
        saveFcmTokenAfterLogin(result.user);
        CustomerFcmTokenSync.forceSync(this);
        showMessage("Login berhasil", true);
        mainHandler.postDelayed(() -> {if(!authScope.isDestroyed()&&!isFinishing()&&authSessionToken.equals(safe(new SessionManager(this).getToken())))openPinPage(role);},420);
    }

    protected LoginResult doGoogleLogin(String idToken) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) CustomerApiClient.open(this, GOOGLE_LOGIN_URL);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Transiva-Client", "Android-Native-Google");
            JSONObject payload = new JSONObject();
            payload.put("id_token", idToken);
            payload.put("signup_username",googleSignupUsername);payload.put("signup_phone",googleSignupPhone);
            payload.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
            payload.put("platform", "android_native");
            payload.put("app_scope", "customer");
            payload.put("installation_uuid", DeviceIdentityManager.getInstallationUuid(this));
            payload.put("manufacturer", Build.MANUFACTURER);
            payload.put("model", Build.MODEL);
            payload.put("android_version", Build.VERSION.RELEASE);
            try { payload.put("app_version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName); } catch (Exception ignored) { payload.put("app_version", "unknown"); }
            String fcm = getCachedFcmToken(); if (!fcm.isEmpty()) payload.put("fcm_token", fcm);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) { writer.write(payload.toString()); }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String raw = readStream(stream).trim();
            if (raw.startsWith("\uFEFF")) raw = raw.substring(1).trim();
            int a=raw.indexOf('{'), b=raw.lastIndexOf('}'); if(a>0 && b>a) raw=raw.substring(a,b+1);
            JSONObject response = new JSONObject(raw);
            boolean success = response.optBoolean("success", false);
            String message = response.optString("message", success ? "Login berhasil" : "Login Google gagal");
            if (!success || code < 200 || code >= 300) return LoginResult.fail("GOOGLE_REGISTRATION_REQUIRED".equals(response.optString("code"))?"GOOGLE_REGISTRATION_REQUIRED":message);
            JSONObject user = response.optJSONObject("user");
            if (user == null) return LoginResult.fail("Data pengguna Google tidak ditemukan.");
            if (user.optString("token", "").trim().isEmpty()) user.put("token", response.optString("token", ""));
            user.put("role", "customer");
            return LoginResult.ok(message, "customer", user);
        } catch (Exception e) { Log.e(TAG, "Google native login gagal", e); return LoginResult.fail("Masuk dengan Google belum berhasil. Periksa internet Anda, lalu coba lagi."); }
        finally { if (connection != null) connection.disconnect(); }
    }



    protected LoginResult doLogin(
            String username,
            String password
    ) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)
                    CustomerApiClient.open(this, LOGIN_URL);

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );
            connection.setRequestProperty(
                    "X-Transiva-Client",
                    "Android-Native"
            );
            connection.setRequestProperty("X-App-Scope", "customer");
            connection.setRequestProperty(
                    "X-Device-UUID",
                    DeviceIdentityManager.getInstallationUuid(this)
            );

            JSONObject payload = new JSONObject();
            payload.put("username", username);
            payload.put("password", password);
            payload.put(
                    "device_name",
                    Build.MANUFACTURER + " " + Build.MODEL
            );
            payload.put("platform", "android_native");
            payload.put("app_scope", "customer");
            payload.put("installation_uuid", DeviceIdentityManager.getInstallationUuid(this));
            payload.put("manufacturer", Build.MANUFACTURER);
            payload.put("model", Build.MODEL);
            payload.put("android_version", Build.VERSION.RELEASE);
            try {
                payload.put("app_version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            } catch (Exception ignored) {
                payload.put("app_version", "unknown");
            }

            String cachedFcmToken = getCachedFcmToken();

            if (!cachedFcmToken.isEmpty()) {
                /*
                 * FCM token hanya untuk notifikasi.
                 * Jangan kirim sebagai field token autentikasi.
                 */
                payload.put("fcm_token", cachedFcmToken);
            }

            try (BufferedWriter writer =
                         new BufferedWriter(
                                 new OutputStreamWriter(
                                         connection.getOutputStream(),
                                         StandardCharsets.UTF_8
                                 )
                         )) {
                writer.write(payload.toString());
                writer.flush();
            }

            int httpCode = connection.getResponseCode();

            InputStream stream =
                    httpCode >= 200 && httpCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String raw = readStream(stream).trim();

            Log.d(
                    TAG,
                    "Login HTTP=" + httpCode
                            + ", bodyLength=" + raw.length()
            );

            if (raw.isEmpty()) {
                return LoginResult.fail(
                        "Server belum merespons. Tunggu sebentar, lalu coba lagi."
                );
            }

            // Tahan terhadap BOM/whitespace atau banner hosting sebelum JSON.
            if (raw.startsWith("\uFEFF")) {
                raw = raw.substring(1).trim();
            }
            int jsonStart = raw.indexOf('{');
            int jsonEnd = raw.lastIndexOf('}');
            if (jsonStart > 0 && jsonEnd > jsonStart) {
                raw = raw.substring(jsonStart, jsonEnd + 1);
            }

            JSONObject response;
            try {
                response = new JSONObject(raw);
            } catch (Exception jsonError) {
                Log.e(TAG, "Response login bukan JSON. HTTP=" + httpCode, jsonError);
                return LoginResult.fail(
                        "Layanan masuk sedang mengalami kendala. Coba lagi sebentar."
                );
            }

            boolean success =
                    response.optBoolean("success", false);

            String message = response.optString(
                    "message",
                    success ? "Login berhasil" : "Login gagal"
            );

            if (!success || httpCode < 200 || httpCode >= 300) {
                return LoginResult.fail(message);
            }

            JSONObject user =
                    response.optJSONObject("user");

            if (user == null) {
                return LoginResult.fail(
                        "Data pengguna tidak ditemukan."
                );
            }

            /*
             * Kompatibilitas jika server menaruh token di root.
             */
            if (user.optString("token", "").trim().isEmpty()) {
                String rootToken =
                        response.optString("token", "").trim();

                if (!rootToken.isEmpty()) {
                    user.put("token", rootToken);
                }
            }

            String rawRole = user.optString("role", "customer");
            if (!isCustomerRole(rawRole)) {
                return LoginResult.fail(
                        "Akun ini bukan akun customer. Gunakan aplikasi Transiva sesuai jenis akun Anda."
                );
            }

            String role = "customer";
            user.put("role", role);

            return LoginResult.ok(
                    message,
                    role,
                    user
            );

        } catch (java.net.SocketTimeoutException error) {
            Log.e(TAG, "Login timeout", error);
            return LoginResult.fail("Server terlalu lama merespons. Coba lagi.");
        } catch (javax.net.ssl.SSLException error) {
            Log.e(TAG, "Login TLS gagal", error);
            return LoginResult.fail("Koneksi aman ke server gagal. Periksa tanggal/waktu dan jaringan perangkat.");
        } catch (java.net.UnknownHostException error) {
            Log.e(TAG, "DNS login gagal", error);
            return LoginResult.fail("Belum bisa terhubung ke Transiva. Periksa internet Anda, lalu coba lagi.");
        } catch (Exception error) {
            Log.e(TAG, "Login gagal", error);
            return LoginResult.fail(
                    "Belum bisa terhubung. Periksa internet Anda, lalu coba lagi."
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected void saveFcmTokenAfterLogin(
            JSONObject user
    ) {
        try {
            int userId = firstPositiveInt(
                    user.optInt("id", 0),
                    user.optInt("user_id", 0),
                    user.optInt("uid", 0)
            );

            String username = firstNotEmpty(
                    user.optString("username", ""),
                    user.optString("user_name", ""),
                    user.optString("name", "")
            );

            String role = normalizeRole(
                    user.optString("role", "customer")
            );

            String cachedToken = getCachedFcmToken();

            if (!cachedToken.isEmpty()) {
                saveFcmLocal(
                        cachedToken,
                        userId,
                        username,
                        role
                );

                uploadFcmToken(
                        userId,
                        username,
                        role,
                        cachedToken
                );
            }

            FirebaseMessaging.getInstance()
                    .getToken()
                    .addOnSuccessListener(token -> {
                        if(authScope.isDestroyed()||isFinishing()||!authSessionToken.equals(safe(new SessionManager(this).getToken())))return;
                        String clean =
                                token == null
                                        ? ""
                                        : token.trim();

                        if (clean.isEmpty()) return;

                        saveFcmLocal(
                                clean,
                                userId,
                                username,
                                role
                        );

                        uploadFcmToken(
                                userId,
                                username,
                                role,
                                clean
                        );
                    })
                    .addOnFailureListener(
                            error -> Log.e(
                                    TAG,
                                    "FCM token gagal",
                                    error
                            )
                    );

        } catch (Exception error) {
            Log.e(TAG, "FCM setelah login gagal", error);
        }
    }

    protected void uploadFcmToken(
            int userId,
            String username,
            String role,
            String fcmToken
    ) {
        if (fcmToken == null || fcmToken.trim().isEmpty()) {
            return;
        }

        authScope.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        CustomerApiClient.open(this, SAVE_FCM_URL);
                CustomerApiClient.applySecurity(this, connection);

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("id", userId);
                payload.put("username", username);
                payload.put("role", role);
                payload.put("fcm_token", fcmToken.trim());
                payload.put("token", fcmToken.trim());
                payload.put("platform", "android_native");
            payload.put("app_scope", "customer");
            payload.put("installation_uuid", DeviceIdentityManager.getInstallationUuid(this));
            payload.put("manufacturer", Build.MANUFACTURER);
            payload.put("model", Build.MODEL);
            payload.put("android_version", Build.VERSION.RELEASE);
            try {
                payload.put("app_version", getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            } catch (Exception ignored) {
                payload.put("app_version", "unknown");
            }

                try (BufferedWriter writer =
                             new BufferedWriter(
                                     new OutputStreamWriter(
                                             connection.getOutputStream(),
                                             StandardCharsets.UTF_8
                                     )
                             )) {
                    writer.write(payload.toString());
                }

                int code = connection.getResponseCode();

                InputStream stream =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                Log.d(
                        TAG,
                        "FCM upload HTTP=" + code
                                + ", body=" + readStream(stream)
                );

            } catch (Exception error) {
                Log.e(TAG, "Upload FCM gagal", error);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    protected String getCachedFcmToken() {
        try {
            String value = getSharedPreferences(
                    "transiva_fcm",
                    MODE_PRIVATE
            ).getString("fcm_token", "");

            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        } catch (Exception ignored) {}

        try {
            String value =
                    new SessionManager(this).getFcmToken();

            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        } catch (Exception ignored) {}

        return "";
    }

    protected void saveFcmLocal(
            String token,
            int userId,
            String username,
            String role
    ) {
        String cleanToken =
                token == null ? "" : token.trim();

        android.content.SharedPreferences fcmPrefs = getSharedPreferences(
                "transiva_fcm",
                MODE_PRIVATE
        );
        String previousToken = fcmPrefs.getString("fcm_token", "");
        int previousUserId = fcmPrefs.getInt("user_id", 0);

        android.content.SharedPreferences.Editor fcmEditor = fcmPrefs.edit()
                .putString("fcm_token", cleanToken)
                .putInt("user_id", userId)
                .putString("username", username)
                .putString("role", role)
                .putLong(
                        "fcm_token_saved_at",
                        System.currentTimeMillis()
                );

        // Sesi/perangkat baru wajib sinkron ulang ke server walau token Firebase kebetulan sama.
        if (previousUserId != userId || !cleanToken.equals(previousToken)) {
            fcmEditor.putLong("server_sync_at", 0L);
        }
        fcmEditor.apply();

        try {
            new SessionManager(this)
                    .saveFcmToken(cleanToken);
        } catch (Exception ignored) {}
    }

    protected void openPinPage(String role) {
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("native_role", normalizeRole(role));
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        startActivity(intent);
        finish();
    }

    protected void openRolePage(String role) {
        Intent intent = new Intent(this, CustomerDashboardActivity.class);
        intent.putExtra("native_role", "customer");
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        startActivity(intent);
        finish();
    }

    protected boolean isCustomerRole(String role) {
        String clean = role == null ? "" : role.trim().toLowerCase(Locale.US);
        return clean.isEmpty()
                || clean.equals("customer")
                || clean.equals("pelanggan")
                || clean.equals("user");
    }

    protected String normalizeRole(String role) {
        return "customer";
    }

    protected void togglePassword() {
        int selection =
                passwordInput.getSelectionStart();

        passwordVisible = !passwordVisible;

        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | (
                        passwordVisible
                                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                                : InputType.TYPE_TEXT_VARIATION_PASSWORD
                )
        );

        passwordInput.setSelection(
                Math.max(0, selection)
        );
    }

    protected void setLoading(boolean value) {
        loading = value;

        loadingView.setVisibility(
                value ? View.VISIBLE : View.GONE);

        loginButton.setEnabled(!value);
        usernameInput.setEnabled(!value);
        passwordInput.setEnabled(!value);
        eyeButton.setEnabled(!value);

        loginButton.setText(
                value ? "Memuat..." : loginStep==0?"Lanjut →":"Masuk →");
        if(googleButton!=null)googleButton.setEnabled(!value);
        if(previous!=null)previous.setEnabled(!value);
    }



    private void showMessage(
            String message,
            boolean success
    ) {
        messageText.setVisibility(View.VISIBLE);
        messageText.setText(success?message:CustomerErrorMessages.humanize(message,"Belum berhasil masuk. Silakan coba lagi."));
        messageText.setTextColor(
                Color.parseColor(
                        success ? "#166534" : "#B91C1C"
                )
        );
        messageText.setBackground(
                round(
                        success ? "#DCFCE7" : "#FEE2E2",
                        dp(12)
                )
        );
    }

    private void clearMessage() {
        messageText.setText("");
        messageText.setVisibility(View.GONE);
    }

    private void openRegister() {
        if(loading)return;
        try {
            startActivity(
                    new Intent(this, RegisterActivity.class)
            );
        } catch (Exception error) {
            openBrowser(BASE_URL + "register.php");
        }
    }

    private void openBrowser(String url) {
        try {
            startActivity(
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    )
            );
        } catch (Exception error) {
            showInfo(
                    "Tidak dapat membuka halaman",
                    url
            );
        }
    }

    private void showInfo(
            String title,
            String message
    ) {
        new TransivaAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String readStream(
            InputStream stream
    ) throws Exception {
        if (stream == null) return "";

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     stream,
                                     StandardCharsets.UTF_8
                             )
                     )) {
            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();
        }
    }

    private TextView label(String value) {
        TextView view =
                text(value, 14, "#123F7A", true);
        view.setPadding(0, dp(5), 0, dp(6));
        return view;
    }

    private EditText input(
            String hint,
            int inputType,
            boolean hasEye
    ) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(15);field.setMinimumHeight(dp(48));
        field.setTextColor(Color.parseColor("#1F2937"));
        field.setHintTextColor(Color.parseColor("#98A2B3"));
        field.setHint(hint);
        field.setInputType(inputType);
        field.setPadding(
                dp(18),
                0,
                hasEye ? dp(52) : dp(18),
                0
        );
        field.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D8E1ED",
                        dp(16),
                        1
                )
        );
        field.setBackground(CustomerAuthDesign.field(this));
        field.setSaveEnabled(false);
        return field;
    }

    private LinearLayout.LayoutParams fieldLayout() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(9));
        return lp;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private GradientDrawable round(
            String fill,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius,
            int width
    ) {
        GradientDrawable drawable =
                round(fill, radius);

        drawable.setStroke(
                dp(width),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    private GradientDrawable gradient(
            String start,
            String end,
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int findDrawable(String name) {
        return getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private int firstPositiveInt(int... values) {
        if (values == null) return 0;

        for (int value : values) {
            if (value > 0) return value;
        }

        return 0;
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";

        for (String value : values) {
            if (value != null
                    && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private static final class LoginResult {
        final boolean success;
        final String message;
        final String role;
        final JSONObject user;

        private LoginResult(
                boolean success,
                String message,
                String role,
                JSONObject user
        ) {
            this.success = success;
            this.message = message;
            this.role = role;
            this.user = user;
        }

        static LoginResult ok(
                String message,
                String role,
                JSONObject user
        ) {
            return new LoginResult(
                    true,
                    message,
                    role,
                    user
            );
        }

        static LoginResult fail(String message) {
            return new LoginResult(
                    false,
                    message,
                    "",
                    null
            );
        }
    }
    private void updateLoginStep(){
        boolean password=loginStep==1;
        loginIdentityCard.setVisibility(password?View.VISIBLE:View.GONE);
        usernameInput.setVisibility(password?View.GONE:View.VISIBLE);usernameLabel.setVisibility(password?View.GONE:View.VISIBLE);
        passwordLabel.setVisibility(password?View.VISIBLE:View.GONE);loginPasswordBox.setVisibility(password?View.VISIBLE:View.GONE);previous.setVisibility(password?View.VISIBLE:View.GONE);
        stepLabel.setText(password?"LANGKAH 2 DARI 2 • KATA SANDI":"LANGKAH 1 DARI 2 • IDENTITAS");
        loginButton.setText(password?"Masuk →":"Lanjut →");
    }
    private static String normalizeAuthPhone(String value){String p=value.replaceAll("\\D","");if(p.startsWith("0062"))p=p.substring(2);if(p.startsWith("0"))p=p.substring(1);return p.isEmpty()?"":p.startsWith("62")?p:"62"+p;}
    private GradientDrawable avatarOval(String color){GradientDrawable shape=new GradientDrawable();shape.setShape(GradientDrawable.OVAL);shape.setColor(Color.parseColor(color));return shape;}
    private void cancelLoginPreview(){previewGeneration++;if(previewRequest!=null)previewRequest.cancel();previewRequest=null;}
    private void returnToUsername(){cancelLoginPreview();passwordInput.setText("");loginAvatar.setTag(new Object());loginAvatar.setImageDrawable(null);loginInitial.setText("");loginIdentityName.setText("");loginStep=0;updateLoginStep();clearMessage();usernameInput.requestFocus();}
    private void loadLoginPreview(){
        if(loading||authScope.isDestroyed())return;
        final String name=usernameInput.getText().toString().trim();
        if(name.isEmpty()){usernameInput.setError("Isi nama pengguna");return;}
        clearMessage();cancelLoginPreview();final long generation=previewGeneration;
        authSessionToken=safe(new SessionManager(this).getToken());setLoading(true);loginButton.setText("Memeriksa akun…");
        JSONObject body=new JSONObject();try{body.put("username",name);}catch(Exception ignored){}
        AuthPublicRequest request=new AuthPublicRequest();previewRequest=request;
        request.start(this,"customer_login_preview.php",body,response->{
            if(authScope.isDestroyed()||isFinishing()||previewRequest!=request||generation!=previewGeneration||!name.equals(usernameInput.getText().toString().trim()))return;
            previewRequest=null;setLoading(false);
            if(!authSessionToken.equals(safe(new SessionManager(this).getToken())))return;
            if(!response.optBoolean("success")){showMessage(response.optString("message","Akun belum dapat diperiksa."),false);return;}
            if(!response.optBoolean("found")){showMessage(response.optString("message","Nama pengguna belum terdaftar."),false);return;}
            JSONObject profile=response.optJSONObject("profile");if(profile==null){showMessage("Data akun belum lengkap. Coba kembali.",false);return;}
            String shown=profile.optString("username",name);loginIdentityName.setText("@"+shown);loginInitial.setText(shown.isEmpty()?"T":shown.substring(0,1).toUpperCase(Locale.US));
            String photo=ImageUrlResolver.resolve(profile.optString("photo",""));loginAvatar.setTag(new Object());loginAvatar.setImageDrawable(null);
            if(!photo.isEmpty())RemoteImageLoader.loadCenterCrop(loginAvatar,photo,0);
            loginStep=1;updateLoginStep();passwordInput.requestFocus();
        });
    }
    private void showGoogleRegistration(String token){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(24),dp(12),dp(24),dp(12));
        EditText name=input("Nama pengguna",InputType.TYPE_CLASS_TEXT,false);
        EditText phone=input("Nomor HP",InputType.TYPE_CLASS_PHONE,false);box.addView(name,fieldLayout());box.addView(phone,fieldLayout());
        AlertDialog dialog=new TransivaAlertDialogBuilder(this).setTitle("Lengkapi akun Google").setMessage("Email Google Anda sudah diverifikasi. Lengkapi identitas untuk mulai menggunakan Transiva.").setView(box).setNegativeButton("Batal",null).setPositiveButton("Buat akun",null).create();
        dialog.setOnDismissListener(d->{if(googleSignupLookup!=null)googleSignupLookup.cancel();googleSignupLookup=null;});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final String u=name.getText().toString().trim().toLowerCase(Locale.US);
            final String p=normalizeAuthPhone(phone.getText().toString());
            if(!u.matches("[a-z0-9_.-]{3,30}")){name.setError("Nama pengguna 3–30 karakter");return;}
            if(!p.matches("62[0-9]{9,13}")){phone.setError("Nomor HP tidak valid");return;}
            JSONObject fields=new JSONObject(),body=new JSONObject();try{fields.put("username",u);fields.put("phone",p);body.put("fields",fields);}catch(Exception ignored){}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            AuthPublicRequest request=new AuthPublicRequest();googleSignupLookup=request;
            request.start(this,"customer_auth_check.php",body,response->{
                if(authScope.isDestroyed()||isFinishing()||!dialog.isShowing()||googleSignupLookup!=request)return;
                googleSignupLookup=null;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if(!response.optBoolean("success")){Toast.makeText(this,CustomerErrorMessages.humanize(response.optString("message"),"Data belum dapat diperiksa. Silakan coba lagi."),Toast.LENGTH_LONG).show();return;}
                JSONObject result=response.optJSONObject("fields"),un=result==null?null:result.optJSONObject("username"),ph=result==null?null:result.optJSONObject("phone");
                boolean nameOk=un!=null&&un.optBoolean("available"),phoneOk=ph!=null&&ph.optBoolean("available");
                if(!nameOk)name.setError(un==null?"Data belum diperiksa.":un.optString("message"));
                if(!phoneOk)phone.setError(ph==null?"Data belum diperiksa.":ph.optString("message"));
                if(!nameOk||!phoneOk)return;
                if(!u.equals(name.getText().toString().trim().toLowerCase(Locale.US))||!p.equals(normalizeAuthPhone(phone.getText().toString()))){Toast.makeText(this,"Data berubah. Tekan Buat akun untuk memeriksa lagi.",Toast.LENGTH_SHORT).show();return;}
                googleSignupUsername=u;googleSignupPhone=p;dialog.dismiss();loginWithGoogleToken(token);
            });
        }));dialog.show();
    }
    private void runAuthNetwork(Runnable work){authScope.execute(work,()->postAuth(()->{setLoading(false);showMessage("Antrean jaringan penuh. Coba kembali sebentar lagi.",false);}));}
    private void postAuth(Runnable result){authScope.post(mainHandler,()->{if(!isFinishing()&&authSessionToken.equals(safe(new SessionManager(this).getToken())))result.run();});}
    @Override public void onBackPressed(){if(previewRequest!=null){cancelLoginPreview();setLoading(false);return;}if(loginStep==1&&!loading)returnToUsername();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("login_username",usernameInput.getText().toString());state.putInt("login_step",loginStep);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){if(loginAvatar!=null){loginAvatar.setTag(new Object());loginAvatar.setImageDrawable(null);}if(googleSignupLookup!=null)googleSignupLookup.cancel();cancelLoginPreview();authScope.destroy();mainHandler.removeCallbacksAndMessages(null);super.onDestroy();}
    private static String safe(String v) { return v == null ? "" : v; }

}
