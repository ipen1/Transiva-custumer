package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.transiva.app.customer.data.CustomerDashboardRepositoryImpl;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.Promo;
import com.transiva.app.customer.presentation.CustomerDashboardContract;
import com.transiva.app.customer.presentation.CustomerDashboardPresenter;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CustomerDashboardActivity extends Activity
        implements CustomerDashboardContract.View {

    // Menyimpan pilihan customer ketika card transisi K Online di Dashboard ditutup.
    private static final String PREF_KONLINE_TRANSITION = "k_online_transition";
    private static final String KEY_KONLINE_DASH_DISMISSED = "dashboard_dismissed";

    private static final int REQ_LOCATION = 1201;
    private static final long PROMO_INTERVAL_MS = 4500L;
    private static final int PROMO_CARD_WIDTH_DP = 275;
    private static final int PROMO_CARD_GAP_DP = 9;
    private static final String PREF_SMART_USAGE = "transiva_smart_usage";

    private final Handler uiHandler =
            new Handler(Looper.getMainLooper());

    private CustomerDashboardPresenter presenter;

    private LinearLayout content;
    private TextView locationText;
    private TextView clusterText;
    private TextView balanceText;
    private TextView orderText;
    private TextView orderHint;
    private FrameLayout orderCard;
    private JSONObject activeOrderJson;
    private TextView loyaltyTierText;
    private ImageView loyaltyTierBadge;
    private TextView loyaltyPointsText;
    private TextView loyaltyNextText;
    private TextView offerTitleText;
    private TextView offerDetailText;
    private TextView referralCodeText;
    private TextView referralStatText;
    private TextView verificationText;
    private TextView greetingText;
    private TextView aiTitleText;
    private TextView aiMessageText;
    private TextView aiActionText;
    private LinearLayout aiCard;
    private Runnable aiAction;
    private JSONObject aiHomeFavorite;
    private JSONObject aiWorkFavorite;
    private int aiFamilyCount = -1;
    private int aiFamilyMax = 1;
    private double currentBalance;
    private String currentOrderText = "Belum ada pesanan aktif";
    private String currentLocation = "Lokasi saya";

    private LinearLayout promoSection;
    private TextView promoHeader;
    private TextView promoEmptyText;
    private HorizontalScrollView promoScroll;
    private LinearLayout promoTrack;
    private LinearLayout promoDots;

    private ProgressBar loading;
    private RecommendationSectionController recommendationController;

    private int promoCount;
    private int activePromoIndex;

    private String username = "User";
    private int userId;

    private final Runnable promoAutoRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (
                            promoCount <= 1
                                    || promoScroll == null
                                    || promoScroll.getVisibility()
                                    != View.VISIBLE
                    ) {
                        return;
                    }

                    activePromoIndex =
                            (activePromoIndex + 1) % promoCount;

                    int target =
                            activePromoIndex
                                    * (
                                    dp(PROMO_CARD_WIDTH_DP)
                                            + dp(PROMO_CARD_GAP_DP)
                            );

                    promoScroll.smoothScrollTo(target, 0);
                    updatePromoDots(activePromoIndex);

                    uiHandler.postDelayed(
                            this,
                            PROMO_INTERVAL_MS
                    );
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        readSession();

        presenter =
                new CustomerDashboardPresenter(
                        new CustomerDashboardRepositoryImpl(this),
                        this
                );

        setContentView(buildScreen());
        CustomerAppSettings.apply(this);

        presenter.load(username, userId);
        loadLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Terapkan ulang agar perubahan tema dari menu Pengaturan langsung
        // terlihat saat kembali ke Dashboard.
        CustomerAppSettings.apply(this);

        SessionValidationClient.validate(this);

        if (presenter != null) {
            presenter.refresh(username, userId);
        }

        startPromoAutoSlide();

        if (recommendationController != null) {
            recommendationController.refresh();
        }

        if (greetingText != null) {
            greetingText.setText(timeGreeting());
        }
        refreshSmartRecommendation();
    }

    @Override
    protected void onPause() {
        stopPromoAutoSlide();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopPromoAutoSlide();

        if (presenter != null) {
            presenter.destroy();
        }

        super.onDestroy();
    }

    private void readSession() {
        try {
            SessionManager session =
                    new SessionManager(this);

            username = first(
                    session.getUsername(),
                    session.getName(),
                    "User"
            );

            try {
                userId = Integer.parseInt(
                        first(
                                session.getId(),
                                session.getUserId(),
                                "0"
                        )
                );
            } catch (Exception ignored) {
                userId = 0;
            }

        } catch (Exception ignored) {
            username = "User";
            userId = 0;
        }
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(
                Color.parseColor("#F7FAFF")
        );

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);

        page.addView(
                shell,
                new FrameLayout.LayoutParams(-1, -1)
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        shell.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(20)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(-1, -2)
        );

        buildHeader();
        if (!isKOnlineDashboardCardDismissed()) {
            buildKOnlineTransitionCard();
        }
        buildSmartRecommendation();
        buildWalletCard();
        buildGrowthCards();
        buildFeatureShortcuts();
        buildPromoSection();
        buildServiceSection();
        buildOrderSection();
        buildRecommendationSection();

        shell.addView(
                buildBottomNavigation(),
                new LinearLayout.LayoutParams(-1, dp(64))
        );

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);

        FrameLayout.LayoutParams loadingLp =
                new FrameLayout.LayoutParams(
                        dp(42),
                        dp(42)
                );

        loadingLp.gravity = Gravity.CENTER;
        page.addView(loading, loadingLp);

        return page;
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(15), dp(16), dp(14));
        header.setBackground(
                Shape.gradient("#075EF4", "#22A4FF", dp(24))
        );
        header.setElevation(dp(4));

        LinearLayout.LayoutParams headerLp =
                new LinearLayout.LayoutParams(-1, -2);
        headerLp.setMargins(0, 0, 0, dp(12));
        content.addView(header, headerLp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));

        greetingText = text(timeGreeting(), 12, "#EAF4FF", false);
        identity.addView(greetingText);

        TextView name = text(formatDisplayName(username) + " 👋", 22, "#FFFFFF", true);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.setMargins(0, dp(2), 0, dp(6));
        identity.addView(name, nameLp);

        boolean verified = isVerifiedUser();
        verificationText = text(
                verified ? "✓ Akun terverifikasi" : "• Verifikasi akun",
                10,
                "#FFFFFF",
                true
        );
        verificationText.setPadding(dp(9), dp(5), dp(9), dp(5));
        verificationText.setBackground(
                Shape.roundStroke(
                        verified ? "#1AFFFFFF" : "#26FFF4D6",
                        verified ? "#70FFFFFF" : "#FFFFD166",
                        dp(13),
                        1
                )
        );
        verificationText.setOnClickListener(view -> {
            if (!verified) {
                Toast.makeText(this, "Lengkapi verifikasi melalui menu Akun", Toast.LENGTH_SHORT).show();
            }
        });
        identity.addView(verificationText, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(actions, new LinearLayout.LayoutParams(-2, -2));

        actions.addView(headerIconAction("ic_notification_bell", "Pemberitahuan", () ->
                startActivity(new Intent(this, CustomerNotificationActivity.class))));

        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsLp.setMargins(dp(8), 0, 0, 0);
        View settings = headerAction("⚙", "Pengaturan", () ->
                startActivity(new Intent(this, CustomerSettingsActivity.class)));
        actions.addView(settings, settingsLp);

        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        chatLp.setMargins(dp(8), 0, 0, 0);
        View chat = headerAction("💬", "Chat", () ->
                startActivity(new Intent(this, CustomerChatActivity.class)));
        actions.addView(chat, chatLp);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#3DFFFFFF"));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.setMargins(0, dp(14), 0, dp(12));
        header.addView(divider, dividerLp);

        LinearLayout locationCard = new LinearLayout(this);
        locationCard.setOrientation(LinearLayout.HORIZONTAL);
        locationCard.setGravity(Gravity.CENTER_VERTICAL);
        locationCard.setPadding(dp(10), dp(9), dp(10), dp(9));
        locationCard.setBackground(Shape.round("#20FFFFFF", dp(16)));
        locationCard.setOnClickListener(view -> loadLocation());

        ImageView pin = new ImageView(this);
        pin.setImageResource(drawable("ic_location_pin"));
        pin.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        locationCard.addView(pin, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout locationTexts = new LinearLayout(this);
        locationTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams locationTextsLp = new LinearLayout.LayoutParams(0, -2, 1);
        locationTextsLp.setMargins(dp(7), 0, dp(6), 0);
        locationCard.addView(locationTexts, locationTextsLp);

        locationTexts.addView(text("Lokasi Anda", 9, "#D9EDFF", false));
        locationText = text("Memuat lokasi...", 12, "#FFFFFF", true);
        locationText.setSingleLine(true);
        locationTexts.addView(locationText);
        clusterText = text("Cluster: mendeteksi...", 10, "#D9EDFF", true);
        locationTexts.addView(clusterText);

        TextView change = text("Perbarui ›", 10, "#FFFFFF", true);
        locationCard.addView(change, new LinearLayout.LayoutParams(-2, -2));
        header.addView(locationCard, new LinearLayout.LayoutParams(-1, -2));
    }

    private View headerAction(String symbol, String description, Runnable action) {
        TextView button = text(symbol, 19, "#FFFFFF", false);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(Shape.roundStroke("#1AFFFFFF", "#55FFFFFF", dp(14), 1));
        button.setOnClickListener(view -> action.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return button;
    }

    private View headerIconAction(String iconName, String description, Runnable action) {
        ImageView button = new ImageView(this);
        button.setImageResource(drawable(iconName));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setContentDescription(description);
        button.setBackground(Shape.roundStroke("#1AFFFFFF", "#55FFFFFF", dp(14), 1));
        button.setOnClickListener(view -> {
            view.animate().scaleX(0.90f).scaleY(0.90f).setDuration(80L)
                    .withEndAction(() -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start();
                        action.run();
                    }).start();
        });
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return button;
    }

    /**
     * Brand-transition card:
     * memberi nuansa bahwa layanan K Online sekarang berada di dalam
     * ekosistem Transiva tanpa mengubah alur order atau logic backend.
     */
    private void buildKOnlineTransitionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(13)
        );

        card.setBackground(
                Shape.gradient(
                        "#F3F8FF",
                        "#FFFFFF",
                        dp(20)
                )
        );

        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardLp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        content.addView(
                card,
                cardLp
        );

        LinearLayout topBar =
                new LinearLayout(this);

        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        topBar.addView(
                row,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView close =
                text(
                        "×",
                        22,
                        "#6C8199",
                        false
                );

        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Tutup informasi K Online");
        close.setBackground(Shape.round("#EEF4FA", dp(18)));

        LinearLayout.LayoutParams closeLp =
                new LinearLayout.LayoutParams(
                        dp(34),
                        dp(34)
                );
        closeLp.setMargins(dp(8), 0, 0, 0);

        topBar.addView(close, closeLp);

        card.addView(
                topBar,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        close.setOnClickListener(v -> {
            getSharedPreferences(PREF_KONLINE_TRANSITION, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_KONLINE_DASH_DISMISSED, true)
                    .apply();

            content.removeView(card);
        });

        // Logo resmi K Online. Simpan PNG sebagai res/drawable-nodpi/k_online_logo.png
        ImageView kLogo = new ImageView(this);
        int kLogoId = getResources().getIdentifier("k_online_logo", "drawable", getPackageName());
        if (kLogoId != 0) kLogo.setImageResource(kLogoId);
        kLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        kLogo.setContentDescription("Logo resmi K Online");
        row.addView(kLogo, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView arrow =
                text(
                        "→",
                        18,
                        "#7A8DA6",
                        true
                );

        arrow.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams arrowLp =
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(50)
                );

        row.addView(
                arrow,
                arrowLp
        );

        LinearLayout brandBox =
                new LinearLayout(this);

        brandBox.setOrientation(
                LinearLayout.HORIZONTAL
        );

        brandBox.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.addView(
                brandBox,
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                )
        );

        ImageView transivaLogo =
                new ImageView(this);

        transivaLogo.setImageResource(
                getResources().getIdentifier(
                        "transiva_logo",
                        "drawable",
                        getPackageName()
                )
        );

        transivaLogo.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        brandBox.addView(
                transivaLogo,
                new LinearLayout.LayoutParams(
                        dp(40),
                        dp(40)
                )
        );

        LinearLayout transivaTextBox =
                new LinearLayout(this);

        transivaTextBox.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams brandTextLp =
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                );

        brandTextLp.setMargins(
                dp(8),
                0,
                0,
                0
        );

        brandBox.addView(
                transivaTextBox,
                brandTextLp
        );

        transivaTextBox.addView(
                text(
                        "TRANSIVA",
                        16,
                        "#0B3A78",
                        true
                )
        );

        transivaTextBox.addView(
                text(
                        "Satu ekosistem, lebih lengkap",
                        9,
                        "#6C8199",
                        false
                )
        );

        TextView title =
                text(
                        "K Online kini menjadi bagian dari Transiva",
                        14,
                        "#0B3A78",
                        true
                );

        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        titleLp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        card.addView(
                title,
                titleLp
        );

        TextView message =
                text(
                        "Layanan yang Anda kenal kini terhubung dalam satu aplikasi Transiva — lebih praktis, aman, dan lengkap.",
                        10,
                        "#5D728A",
                        false
                );

        message.setLineSpacing(
                0f,
                1.08f
        );

        LinearLayout.LayoutParams messageLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        messageLp.setMargins(
                0,
                dp(5),
                0,
                0
        );

        card.addView(
                message,
                messageLp
        );

        LinearLayout footer =
                new LinearLayout(this);

        footer.setOrientation(
                LinearLayout.HORIZONTAL
        );

        footer.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams footerLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        footerLp.setMargins(
                0,
                dp(11),
                0,
                0
        );

        card.addView(
                footer,
                footerLp
        );

        TextView trust =
                text(
                        "✓ Tetap melayani Anda",
                        9,
                        "#12834B",
                        true
                );

        trust.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );

        trust.setBackground(
                Shape.roundStroke(
                        "#ECFAF2",
                        "#BFE8CF",
                        dp(12),
                        1
                )
        );

        footer.addView(
                trust,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView learn =
                text(
                        "Selengkapnya  ›",
                        10,
                        "#0B7CFF",
                        true
                );

        learn.setGravity(
                Gravity.CENTER_VERTICAL
                        | Gravity.RIGHT
        );

        learn.setPadding(
                dp(8),
                dp(6),
                0,
                dp(6)
        );

        footer.addView(
                learn,
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                )
        );

        View.OnClickListener openInfo =
                view ->
                        showKOnlineTransitionDialog();

        learn.setOnClickListener(openInfo);
        card.setOnClickListener(openInfo);
    }

    private boolean isKOnlineDashboardCardDismissed() {
        return getSharedPreferences(PREF_KONLINE_TRANSITION, MODE_PRIVATE)
                .getBoolean(KEY_KONLINE_DASH_DISMISSED, false);
    }

    private void showKOnlineTransitionDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("K Online × Transiva")
                    .setMessage(
                            "K Online kini menjadi bagian dari Transiva.\n\n"
                                    + "Anda tetap dapat menikmati layanan yang sudah dikenal, kini dalam ekosistem Transiva yang lebih lengkap.\n\n"
                                    + "• Satu aplikasi untuk berbagai kebutuhan\n"
                                    + "• Dukungan layanan dan keamanan Transiva\n"
                                    + "• Pengalaman pemesanan yang lebih terintegrasi\n\n"
                                    + "Terima kasih telah tumbuh bersama kami.\n\n"
                                    + "#KOnlineBersamaTransiva"
                    )
                    .setPositiveButton(
                            "Mengerti",
                            null
                    )
                    .show();

        } catch (Throwable ignored) { }
    }

    private void buildSmartRecommendation() {
        aiCard = new LinearLayout(this);
        aiCard.setOrientation(LinearLayout.HORIZONTAL);
        aiCard.setGravity(Gravity.CENTER_VERTICAL);
        aiCard.setPadding(dp(13), dp(12), dp(12), dp(12));
        aiCard.setBackground(Shape.roundStroke("#FFFFFF", "#D9E9FF", dp(19), 1));
        aiCard.setElevation(dp(2));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(13));
        content.addView(aiCard, cardLp);

        TextView icon = text("✦", 23, "#0B7CFF", true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Shape.round("#EAF4FF", dp(18)));
        aiCard.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.setMargins(dp(11), 0, dp(8), 0);
        aiCard.addView(copy, copyLp);

        aiTitleText = text("Transiva AI", 12, "#0B3A78", true);
        copy.addView(aiTitleText);

        aiMessageText = text("Menyiapkan rekomendasi terbaik untuk Anda...", 11, "#64748B", false);
        aiMessageText.setMaxLines(2);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(-1, -2);
        messageLp.setMargins(0, dp(3), 0, 0);
        copy.addView(aiMessageText, messageLp);

        aiActionText = text("Lihat ›", 10, "#FFFFFF", true);
        aiActionText.setGravity(Gravity.CENTER);
        aiActionText.setPadding(dp(10), dp(8), dp(10), dp(8));
        aiActionText.setBackground(Shape.round("#0B7CFF", dp(13)));
        aiCard.addView(aiActionText, new LinearLayout.LayoutParams(-2, -2));

        View.OnClickListener listener = view -> {
            if (aiAction != null) aiAction.run();
        };
        aiCard.setOnClickListener(listener);
        aiActionText.setOnClickListener(listener);
        refreshSmartRecommendation();
        loadAiFavorites();
        loadAiFamilyMeta();
    }

    private void refreshSmartRecommendation() {
        if (aiMessageText == null) return;

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        boolean weekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY;
        boolean activeOrder = isActiveOrderText(currentOrderText);

        String title;
        String message;
        String button;
        Runnable action;

        if (activeOrder) {
            title = "Pesanan sedang berjalan";
            message = currentOrderText + ". Pantau aktivitas terbaru pesanan Anda.";
            button = "Pantau ›";
            action = () -> startActivity(new Intent(this, CustomerHistoryActivity.class));
        } else if (hour >= 5 && hour < 11 && aiWorkFavorite != null) {
            title = "Transiva AI • Berangkat ke Kantor? 🏢";
            message = "Tujuan Kantor sudah tersimpan. Saya bisa isi lokasi jemput Anda otomatis.";
            button = "Motor ›";
            action = () -> openAiFavorite(aiWorkFavorite, false);
        } else if (hour >= 16 && hour < 22 && aiHomeFavorite != null) {
            title = "Transiva AI • Pulang ke Rumah? 🏠";
            message = "Rumah sudah siap sebagai tujuan. Titik jemput akan mengikuti lokasi Anda sekarang.";
            button = "Motor ›";
            action = () -> openAiFavorite(aiHomeFavorite, false);
        } else if ((hour >= 22 || hour < 5) && aiHomeFavorite != null) {
            title = "Transiva AI • Pulang lebih nyaman 🌙";
            message = "Saya siapkan Rumah sebagai tujuan dan TransCar untuk perjalanan malam.";
            button = "Mobil ›";
            action = () -> openAiFavorite(aiHomeFavorite, true);
        } else if (currentBalance > 0 && currentBalance < 20000) {
            title = "Saldo Transiva Pay menipis";
            message = "Isi saldo sekarang agar pembayaran layanan berikutnya tetap lancar.";
            button = "Top Up ›";
            action = () -> startActivity(new Intent(this, CustomerTopUpActivity.class));
        } else if (aiFamilyCount == 0 && hour >= 14 && hour < 17) {
            title = "Transiva AI • Family belum diatur 👨‍👩‍👧";
            message = "Anda punya " + aiFamilyMax + " slot Family. Tambahkan orang terdekat agar bisa dipesankan perjalanan lebih cepat.";
            button = "Atur Family ›";
            action = () -> startActivity(new Intent(this, TransivaFamilyActivity.class));
        } else if (weekend && hour >= 8 && hour < 18) {
            title = "Kebutuhan akhir pekan lebih praktis";
            message = "Kirim barang dengan aman dari " + first(currentLocation, "lokasi Anda") + " menggunakan TransSend.";
            button = "Kirim ›";
            action = () -> openTrackedService("TransSend");
        } else if (hour >= 10 && hour < 14) {
            title = "Waktunya makan siang 🍜";
            message = "Temukan menu favorit dan merchant terdekat lewat TransFood.";
            button = "Pesan ›";
            action = () -> openTrackedService("TransFood");
        } else if (hour >= 17 && hour < 21) {
            title = "Perjalanan pulang lebih mudah";
            message = "Pesan TransRide dari " + first(currentLocation, "lokasi Anda") + " tanpa perlu menunggu lama.";
            button = "Ride ›";
            action = () -> openTrackedService("TransRide");
        } else if (hour >= 21 || hour < 5) {
            title = "Perjalanan malam yang praktis 🌙";
            message = "Gunakan TransCar untuk perjalanan yang lebih nyaman malam ini.";
            button = "TransCar ›";
            action = () -> openTrackedService("TransCar");
        } else {
            String favorite = favoriteServiceKey();
            if (!favorite.isEmpty()) {
                title = "Pilihan favorit Anda ✦";
                message = "Anda cukup sering menggunakan " + favorite + ". Buka lagi layanan favorit Anda dari "
                        + first(currentLocation, "lokasi Anda") + ".";
                button = "Buka ›";
                action = () -> openTrackedService(favorite);
            } else {
                title = "Transiva AI siap membantu ✦";
                message = "Ketik kebutuhan seperti ‘mau pulang’, ‘lagi lapar’, atau ‘cari mobil’. Saya akan arahkan ke layanan yang cocok.";
                button = "Tanya AI ›";
                action = () -> { Intent i = new Intent(this, GlobalSearchActivity.class); i.putExtra("ai_prompt", ""); startActivity(i); };
            }
        }

        aiTitleText.setText(title);
        aiMessageText.setText(message);
        aiActionText.setText(button);
        aiAction = action;

        aiCard.setAlpha(0f);
        aiCard.animate().alpha(1f).setDuration(280L).start();
    }

    private void loadAiFavorites() {
        TransivaNetworkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = CustomerApiClient.open(this, "https://transiva.my.id/server/customer_favorites.php?action=list");
                conn.setRequestMethod("GET");
                InputStream in = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject o = new JSONObject(sb.toString());
                JSONArray a = o.optJSONArray("places");
                JSONObject home = null, work = null;
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject x = a.optJSONObject(i); if (x == null) continue;
                    if ("home".equalsIgnoreCase(x.optString("type"))) home = x;
                    else if ("work".equalsIgnoreCase(x.optString("type"))) work = x;
                }
                final JSONObject fHome = home, fWork = work;
                uiHandler.post(() -> { aiHomeFavorite = fHome; aiWorkFavorite = fWork; refreshSmartRecommendation(); });
            } catch (Exception ignored) {
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void loadAiFamilyMeta() {
        TransivaNetworkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = CustomerApiClient.open(this, "https://transiva.my.id/server/customer_family.php?action=list");
                conn.setRequestMethod("GET");
                InputStream in = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject o = new JSONObject(sb.toString());
                final int count = o.optInt("member_count", -1);
                final int max = Math.max(1, o.optInt("max_members", 1));
                uiHandler.post(() -> { aiFamilyCount = count; aiFamilyMax = max; refreshSmartRecommendation(); });
            } catch (Exception ignored) {
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void openAiFavorite(JSONObject place, boolean car) {
        if (place == null) { startActivity(new Intent(this, FavoritePlacesActivity.class)); return; }
        double lat = place.optDouble("latitude", 0), lng = place.optDouble("longitude", 0);
        if (lat == 0 || lng == 0) { startActivity(new Intent(this, FavoritePlacesActivity.class)); return; }
        Intent i = new Intent(this, car ? PassengerCarActivity.class : TransRideActivity.class);
        i.putExtra("smart_favorite", true);
        i.putExtra("smart_destination_lat", lat);
        i.putExtra("smart_destination_lng", lng);
        i.putExtra("smart_destination_address", first(place.optString("address"), place.optString("label"), "Tujuan favorit"));
        i.putExtra("smart_destination_label", first(place.optString("label"), "Tujuan"));
        startActivity(i);
    }

    private String timeGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 11) return "Selamat pagi";
        if (hour < 15) return "Selamat siang";
        if (hour < 18) return "Selamat sore";
        return "Selamat malam";
    }

    private String formatDisplayName(String value) {
        String clean = first(value, "User");
        if (clean.isEmpty()) return "User";
        String[] words = clean.toLowerCase(new Locale("id", "ID")).split("\\s+");
        StringBuilder output = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (output.length() > 0) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) output.append(word.substring(1));
        }
        return output.length() == 0 ? "User" : output.toString();
    }

    private boolean isVerifiedUser() {
        try {
            SharedPreferences preferences =
                    getSharedPreferences(
                            "transiva_native_session",
                            MODE_PRIVATE
                    );

            JSONObject user = new JSONObject(
                    preferences.getString(
                            "raw_user",
                            "{}"
                    )
            );

            return user.optInt(
                    "email_verified",
                    0
            ) == 1
                    || user.optInt(
                    "verified_by_admin",
                    0
            ) == 1
                    || user.optBoolean(
                    "verified",
                    false
            )
                    || user.optBoolean(
                    "is_verified",
                    false
            );

        } catch (Exception ignored) {
            return false;
        }
    }

    private void buildWalletCard() {
        FrameLayout frame = new FrameLayout(this);

        frame.setBackground(
                Shape.gradient(
                        "#075EF4",
                        "#22A4FF",
                        dp(22)
                )
        );

        frame.setElevation(dp(3));

        LinearLayout.LayoutParams frameLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(128)
                );

        frameLp.setMargins(
                0,
                dp(14),
                0,
                dp(16)
        );

        content.addView(frame, frameLp);

        ImageView art = new ImageView(this);
        art.setImageResource(
                drawable("img_wallet_transiva")
        );

        art.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        FrameLayout.LayoutParams artLp =
                new FrameLayout.LayoutParams(
                        dp(112),
                        dp(112)
                );

        artLp.gravity =
                Gravity.END | Gravity.CENTER_VERTICAL;

        frame.addView(art, artLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        card.setPadding(
                dp(16),
                dp(13),
                dp(16),
                dp(10)
        );

        frame.addView(
                card,
                new FrameLayout.LayoutParams(-1, -1)
        );

        card.addView(
                text(
                        "Transiva Pay",
                        15,
                        "#FFFFFF",
                        true
                )
        );

        card.addView(
                text(
                        "Saldo Anda",
                        11,
                        "#EAF4FF",
                        false
                )
        );

        balanceText = text(
                "Memuat saldo...",
                25,
                "#FFFFFF",
                true
        );

        balanceText.setSingleLine(true);

        LinearLayout.LayoutParams balanceLp =
                new LinearLayout.LayoutParams(-1, -2);

        balanceLp.setMargins(
                0,
                dp(1),
                0,
                0
        );

        card.addView(balanceText, balanceLp);

    }

    private void buildGrowthCards() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout loyalty = miniGrowthCard("★", "Royalti", "Bronze", "0 poin");
        // Premium loyalty badge: use the active season tier PNG instead of a generic star.
        loyalty.removeViewAt(0);
        loyaltyTierBadge = TierBadgeUi.image(this, TierBadgeUi.getCachedActiveTier(this), 0);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        badgeLp.setMargins(0, 0, dp(8), 0);
        loyalty.addView(loyaltyTierBadge, 0, badgeLp);
        loyaltyTierText = (TextView) ((LinearLayout) loyalty.getChildAt(1)).getChildAt(1);
        loyaltyPointsText = (TextView) ((LinearLayout) loyalty.getChildAt(1)).getChildAt(2);
        loyalty.setOnClickListener(v -> startActivity(new Intent(this, CustomerLoyaltyActivity.class)));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1);
        a.setMargins(0, 0, dp(6), 0);
        row.addView(loyalty, a);

        LinearLayout referral = miniGrowthCard("🎁", "Referral", "Ajak teman", "Dapat poin");
        referralCodeText = (TextView) ((LinearLayout) referral.getChildAt(1)).getChildAt(1);
        referralStatText = (TextView) ((LinearLayout) referral.getChildAt(1)).getChildAt(2);
        referral.setOnClickListener(v -> startActivity(new Intent(this, CustomerReferralActivity.class)));
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -2, 1);
        b.setMargins(dp(6), 0, 0, 0);
        row.addView(referral, b);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, 0, 0, dp(12));
        content.addView(row, rowLp);

        LinearLayout offer = new LinearLayout(this);
        offer.setOrientation(LinearLayout.VERTICAL);
        offer.setPadding(dp(16), dp(14), dp(16), dp(14));
        offer.setBackground(Shape.roundStroke("#ECFDF5", "#B7E8CD", dp(18), 1));
        offerTitleText = text("💸 Transiva Hemat", 15, "#076B42", true);
        offerDetailText = text("Promo terbaik akan dipilih otomatis", 12, "#39745D", false);
        offerDetailText.setPadding(0, dp(4), 0, 0);
        loyaltyNextText = text("", 11, "#64748B", false);
        loyaltyNextText.setPadding(0, dp(6), 0, 0);
        offer.addView(offerTitleText);
        offer.addView(offerDetailText);
        offer.addView(loyaltyNextText);
        offer.setOnClickListener(v -> startActivity(new Intent(this, CustomerLoyaltyActivity.class)));
        LinearLayout.LayoutParams offerLp = new LinearLayout.LayoutParams(-1, -2);
        offerLp.setMargins(0, 0, 0, dp(14));
        content.addView(offer, offerLp);
    }

    private LinearLayout miniGrowthCard(String iconText, String label, String value, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#D9E8F8", dp(17), 1));
        TextView icon = text(iconText, 22, "#0878F9", true);
        card.addView(icon, new LinearLayout.LayoutParams(dp(34), -2));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(label, 10, "#64748B", true);
        TextView main = text(value, 13, "#0B3A78", true);
        TextView secondary = text(sub, 10, "#7890AA", false);
        body.addView(title);
        body.addView(main);
        body.addView(secondary);
        card.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private void buildFeatureShortcuts() {
        TextView title = text("Fitur Pintar & Aman", 15, "#0B3A78", true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, 0, 0, dp(8));
        content.addView(title, titleLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addFeatureShortcut(row, "✦", "AI Cari", GlobalSearchActivity.class, 0);
        addFeatureShortcut(row, "👨‍👩‍👧", "Family", TransivaFamilyActivity.class, 1);
        addFeatureShortcut(row, "⌂", "AI Favorit", FavoritePlacesActivity.class, 2);
        addFeatureShortcut(row, "🛡️", "Safety", SafetyCenterActivity.class, 3);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, dp(82));
        rowLp.setMargins(0, 0, 0, dp(16));
        content.addView(row, rowLp);
    }

    private void addFeatureShortcut(LinearLayout row, String icon, String label, Class<?> target, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#D9E8F8", dp(17), dp(1)));
        card.setElevation(dp(1));
        card.addView(text(icon, 22, "#0B7CFF", true));
        card.addView(text(label, 11, "#0B3A78", true));
        card.setOnClickListener(v -> startActivity(new Intent(this, target)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (index > 0) lp.setMargins(dp(8), 0, 0, 0);
        row.addView(card, lp);
    }

    private void openBalanceTransactions() {
        String[] candidates = {
                "com.transiva.app.CustomerBalanceHistoryActivity",
                "com.transiva.app.BalanceTransactionHistoryActivity",
                "com.transiva.app.CustomerTransactionHistoryActivity"
        };

        for (String className : candidates) {
            try {
                startActivity(
                        new Intent(
                                this,
                                Class.forName(className)
                        )
                );
                return;

            } catch (Exception ignored) {
            }
        }

        Toast.makeText(
                this,
                "Riwayat transaksi saldo sedang disiapkan",
                Toast.LENGTH_SHORT
        ).show();
    }

    /**
     * Promo section memakai tinggi WRAP_CONTENT.
     *
     * Saat tidak ada promo:
     * - banner disembunyikan;
     * - dots disembunyikan;
     * - hanya teks kecil "Belum ada promo hari ini";
     * - Layanan Transiva langsung naik ke bawahnya.
     */
    private void buildPromoSection() {
        promoSection = new LinearLayout(this);
        promoSection.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams sectionLp =
                new LinearLayout.LayoutParams(-1, -2);

        sectionLp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        content.addView(promoSection, sectionLp);

        promoHeader = text(
                "Promo Hari Ini",
                16,
                "#0B3A78",
                true
        );

        promoSection.addView(
                promoHeader,
                new LinearLayout.LayoutParams(-1, -2)
        );

        TextView promoHint = text(
                "Geser untuk melihat penawaran terbaik",
                10,
                "#7B8DA3",
                false
        );
        LinearLayout.LayoutParams promoHintLp = new LinearLayout.LayoutParams(-1, -2);
        promoHintLp.setMargins(0, dp(3), 0, 0);
        promoSection.addView(promoHint, promoHintLp);

        promoEmptyText = text(
                "Belum ada promo hari ini",
                12,
                "#7B8DA3",
                false
        );

        promoEmptyText.setGravity(
                Gravity.CENTER_VERTICAL
        );

        promoEmptyText.setPadding(
                dp(14),
                dp(11),
                dp(14),
                dp(11)
        );

        promoEmptyText.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#E3ECF7",
                        dp(14),
                        1
                )
        );

        LinearLayout.LayoutParams emptyLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(46)
                );

        emptyLp.setMargins(
                0,
                dp(7),
                0,
                0
        );

        promoSection.addView(
                promoEmptyText,
                emptyLp
        );

        promoScroll =
                new HorizontalScrollView(this);

        promoScroll.setHorizontalScrollBarEnabled(
                false
        );

        promoScroll.setClipToPadding(false);
        promoScroll.setVisibility(View.GONE);

        promoTrack = new LinearLayout(this);
        promoTrack.setOrientation(
                LinearLayout.HORIZONTAL
        );

        promoScroll.addView(
                promoTrack,
                new HorizontalScrollView.LayoutParams(
                        -2,
                        -2
                )
        );

        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(126)
                );

        scrollLp.setMargins(
                0,
                dp(7),
                0,
                dp(5)
        );

        promoSection.addView(
                promoScroll,
                scrollLp
        );

        promoDots = new LinearLayout(this);
        promoDots.setGravity(Gravity.CENTER);
        promoDots.setVisibility(View.GONE);

        LinearLayout.LayoutParams dotsLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(14)
                );

        promoSection.addView(
                promoDots,
                dotsLp
        );

        promoScroll.setOnScrollChangeListener(
                (
                        view,
                        scrollX,
                        scrollY,
                        oldX,
                        oldY
                ) -> {
                    if (promoCount <= 1) {
                        return;
                    }

                    int width =
                            dp(PROMO_CARD_WIDTH_DP)
                                    + dp(PROMO_CARD_GAP_DP);

                    int index = Math.max(
                            0,
                            Math.min(
                                    promoCount - 1,
                                    Math.round(
                                            (float) scrollX
                                                    / width
                                    )
                            )
                    );

                    if (index != activePromoIndex) {
                        activePromoIndex = index;
                        updatePromoDots(index);
                    }

                    stopPromoAutoSlide();

                    uiHandler.postDelayed(
                            promoAutoRunnable,
                            PROMO_INTERVAL_MS
                    );
                }
        );
    }

    private void renderPromos(List<Promo> promos) {
        stopPromoAutoSlide();

        promoTrack.removeAllViews();
        promoDots.removeAllViews();

        promoCount =
                promos == null
                        ? 0
                        : Math.min(2, promos.size());

        if (promoCount == 0) {
            activePromoIndex = 0;

            promoEmptyText.setVisibility(
                    View.VISIBLE
            );

            promoScroll.setVisibility(
                    View.GONE
            );

            promoDots.setVisibility(
                    View.GONE
            );

            promoScroll.scrollTo(0, 0);

            // Tidak ada fixed-height banner,
            // sehingga Layanan Transiva langsung naik.
            return;
        }

        promoEmptyText.setVisibility(View.GONE);
        promoScroll.setVisibility(View.VISIBLE);

        promoDots.setVisibility(
                promoCount > 1
                        ? View.VISIBLE
                        : View.GONE
        );

        for (int i = 0; i < promoCount; i++) {
            promoTrack.addView(
                    promoBanner(promos.get(i))
            );
        }

        if (promoCount > 1) {
            for (int i = 0; i < promoCount; i++) {
                View dot = new View(this);

                LinearLayout.LayoutParams dotLp =
                        new LinearLayout.LayoutParams(
                                dp(7),
                                dp(7)
                        );

                dotLp.setMargins(
                        dp(3),
                        0,
                        dp(3),
                        0
                );

                promoDots.addView(dot, dotLp);
            }
        }

        activePromoIndex = 0;
        updatePromoDots(0);

        promoScroll.post(
                () -> promoScroll.scrollTo(0, 0)
        );

        startPromoAutoSlide();
    }

    private View promoBanner(Promo promo) {
        FrameLayout card = new FrameLayout(this);

        card.setBackground(
                Shape.gradient(
                        promo.themeStart,
                        promo.themeEnd,
                        dp(17)
                )
        );

        card.setElevation(dp(2));

        ImageView image = new ImageView(this);

        RemoteImageLoader.loadCenterCrop(
                image,
                promo.imageUrl,
                drawable("img_promo_vehicle")
        );

        FrameLayout.LayoutParams imageLp =
                new FrameLayout.LayoutParams(
                        dp(132),
                        -1
                );

        imageLp.gravity = Gravity.END;
        card.addView(image, imageLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        box.setPadding(
                dp(13),
                dp(10),
                dp(8),
                dp(8)
        );

        card.addView(
                box,
                new FrameLayout.LayoutParams(-1, -1)
        );

        box.addView(
                text(
                        promo.title,
                        20,
                        "#FFFFFF",
                        true
                )
        );

        TextView description = text(
                promo.description,
                11,
                "#FFFFFF",
                false
        );

        LinearLayout.LayoutParams descriptionLp =
                new LinearLayout.LayoutParams(
                        dp(165),
                        -2
                );

        descriptionLp.setMargins(
                0,
                dp(2),
                0,
                dp(6)
        );

        box.addView(
                description,
                descriptionLp
        );

        if (
                promo.code != null
                        && !promo.code.trim().isEmpty()
        ) {
            TextView code = text(
                    "Kode: " + promo.code,
                    10,
                    "#FFFFFF",
                    true
            );

            code.setPadding(
                    dp(7),
                    dp(4),
                    dp(7),
                    dp(4)
            );

            code.setBackground(
                    Shape.roundStroke(
                            "#0A6FEA",
                            "#FFFFFF",
                            dp(8),
                            1
                    )
            );

            box.addView(
                    code,
                    new LinearLayout.LayoutParams(-2, -2)
            );
        }

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        dp(PROMO_CARD_WIDTH_DP),
                        dp(118)
                );

        cardLp.setMargins(
                0,
                0,
                dp(PROMO_CARD_GAP_DP),
                0
        );

        card.setLayoutParams(cardLp);
        card.setOnClickListener(view -> {
            view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80L)
                    .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(120L).start())
                    .start();
            if (promo.code != null && !promo.code.trim().isEmpty()) {
                Toast.makeText(this, "Kode promo: " + promo.code, Toast.LENGTH_SHORT).show();
            }
        });

        return card;
    }

    private void updatePromoDots(int selected) {
        if (promoDots == null) {
            return;
        }

        for (
                int i = 0;
                i < promoDots.getChildCount();
                i++
        ) {
            promoDots
                    .getChildAt(i)
                    .setBackground(
                            Shape.round(
                                    i == selected
                                            ? "#0B7CFF"
                                            : "#CBD5E1",
                                    dp(4)
                            )
                    );
        }
    }

    private void startPromoAutoSlide() {
        stopPromoAutoSlide();

        if (
                promoCount > 1
                        && promoScroll != null
                        && promoScroll.getVisibility()
                        == View.VISIBLE
        ) {
            uiHandler.postDelayed(
                    promoAutoRunnable,
                    PROMO_INTERVAL_MS
            );
        }
    }

    private void stopPromoAutoSlide() {
        uiHandler.removeCallbacks(
                promoAutoRunnable
        );
    }

    private void buildServiceSection() {
        TextView header = text(
                "Layanan Transiva",
                16,
                "#0B3A78",
                true
        );

        content.addView(
                header,
                new LinearLayout.LayoutParams(-1, -2)
        );

        TextView serviceHint = text(
                "Pilih layanan yang Anda butuhkan",
                10,
                "#7B8DA3",
                false
        );
        LinearLayout.LayoutParams serviceHintLp = new LinearLayout.LayoutParams(-1, -2);
        serviceHintLp.setMargins(0, dp(3), 0, 0);
        content.addView(serviceHint, serviceHintLp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams gridLp =
                new LinearLayout.LayoutParams(-1, -2);

        gridLp.setMargins(
                0,
                dp(7),
                0,
                dp(14)
        );

        content.addView(grid, gridLp);

        grid.addView(
                serviceRow(
                        service(
                                "TransRide",
                                "ic_service_ride",
                                TransRideActivity.class
                        ),
                        service(
                                "TransCar",
                                "ic_service_car",
                                PassengerCarActivity.class
                        ),
                        service(
                                "TransFood",
                                "ic_service_food",
                                TransFoodActivity.class
                        )
                )
        );

        grid.addView(
                serviceRow(
                        service(
                                "TransSend",
                                "ic_service_pickup",
                                TransPickupActivity.class
                        ),
                        service(
                                "TransShop",
                                "ic_service_mart",
                                TransShopActivity.class
                        )
                )
        );
    }

    private View serviceRow(View... items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        for (int i = 0; i < items.length; i++) {
            LinearLayout.LayoutParams itemLp =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(84),
                            1
                    );

            if (i > 0) {
                itemLp.setMargins(
                        dp(6),
                        0,
                        0,
                        0
                );
            }

            row.addView(items[i], itemLp);
        }

        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(-1, -2);

        rowLp.setMargins(
                0,
                0,
                0,
                dp(6)
        );

        row.setLayoutParams(rowLp);
        return row;
    }

    private View service(
            String title,
            String icon,
            Class<?> destination
    ) {
        return serviceAction(
                title,
                icon,
                () -> startActivity(
                        new Intent(
                                this,
                                destination
                        )
                )
        );
    }

    private View serviceAction(
            String title,
            String icon,
            Runnable action
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(4), dp(7), dp(4), dp(6));
        card.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#DDEBFA",
                        dp(18),
                        1
                )
        );
        card.setElevation(dp(2));

        FrameLayout iconHolder = new FrameLayout(this);
        iconHolder.setBackground(Shape.round("#EEF6FF", dp(17)));
        card.addView(iconHolder, new LinearLayout.LayoutParams(dp(46), dp(46)));

        ImageView image = new ImageView(this);
        image.setImageResource(drawable(icon));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(5), dp(5), dp(5), dp(5));
        FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(dp(38), dp(38));
        imageLp.gravity = Gravity.CENTER;
        iconHolder.addView(image, imageLp);

        TextView label = text(title, 9, "#0B3A78", true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.setMargins(0, dp(5), 0, 0);
        card.addView(label, labelLp);

        card.setOnClickListener(view -> {
            view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(75L)
                    .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start())
                    .start();
            recordServiceUsage(title);
            action.run();
        });

        return card;
    }

    private void recordServiceUsage(String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()
                || "Lainnya".equalsIgnoreCase(serviceName)) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREF_SMART_USAGE, MODE_PRIVATE);
        String key = "count_" + serviceName.toLowerCase(Locale.ROOT);
        int count = prefs.getInt(key, 0) + 1;
        prefs.edit()
                .putInt(key, count)
                .putString("last_service", serviceName)
                .putLong("last_used_at", System.currentTimeMillis())
                .apply();
    }

    private String favoriteServiceKey() {
        SharedPreferences prefs = getSharedPreferences(PREF_SMART_USAGE, MODE_PRIVATE);
        String[] services = {"TransRide", "TransCar", "TransFood", "TransSend", "TransShop"};
        String favorite = "";
        int best = 0;
        for (String service : services) {
            int count = prefs.getInt("count_" + service.toLowerCase(Locale.ROOT), 0);
            if (count > best) {
                best = count;
                favorite = service;
            }
        }
        if (best <= 0) {
            return first(prefs.getString("last_service", ""), "");
        }
        return favorite;
    }

    private void openTrackedService(String serviceName) {
        recordServiceUsage(serviceName);
        if ("TransRide".equalsIgnoreCase(serviceName)) {
            startActivity(new Intent(this, TransRideActivity.class));
        } else if ("TransCar".equalsIgnoreCase(serviceName)) {
            startActivity(new Intent(this, PassengerCarActivity.class));
        } else if ("TransFood".equalsIgnoreCase(serviceName)) {
            startActivity(new Intent(this, TransFoodActivity.class));
        } else if ("TransSend".equalsIgnoreCase(serviceName)
                || "Pickup".equalsIgnoreCase(serviceName)) {
            startActivity(new Intent(this, TransPickupActivity.class));
        } else if ("TransShop".equalsIgnoreCase(serviceName)
                || "TransMart".equalsIgnoreCase(serviceName)) {
            startActivity(new Intent(this, TransShopActivity.class));
        } else {
            startActivity(new Intent(this, TransRideActivity.class));
        }
    }

    private void buildOrderSection() {
        FrameLayout card = new FrameLayout(this);
        orderCard = card;

        card.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#EDF2F7",
                        dp(17),
                        1
                )
        );

        card.setElevation(dp(1));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardLp.setMargins(
                0,
                0,
                0,
                dp(15)
        );

        content.addView(card, cardLp);

        ImageView illustration =
                new ImageView(this);

        illustration.setImageResource(
                drawable("img_order_empty")
        );

        illustration.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        FrameLayout.LayoutParams artLp =
                new FrameLayout.LayoutParams(
                        dp(105),
                        -1
                );

        artLp.gravity = Gravity.END;
        card.addView(illustration, artLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        box.setPadding(
                dp(14),
                dp(12),
                dp(110),
                dp(10)
        );

        card.addView(
                box,
                new FrameLayout.LayoutParams(-1, -1)
        );

        box.addView(
                text(
                        "Status Pesanan",
                        15,
                        "#0B3A78",
                        true
                )
        );

        orderText = text(
                "Belum ada pesanan aktif",
                11,
                "#718096",
                false
        );

        LinearLayout.LayoutParams orderLp =
                new LinearLayout.LayoutParams(-1, -2);

        orderLp.setMargins(
                0,
                dp(5),
                0,
                0
        );

        box.addView(orderText, orderLp);

        orderHint = text(
                "Yuk, pesan layanan Transiva sekarang!",
                9,
                "#8AA0B8",
                false
        );

        LinearLayout.LayoutParams hintLp =
                new LinearLayout.LayoutParams(-1, -2);

        hintLp.setMargins(
                0,
                dp(2),
                0,
                0
        );

        box.addView(orderHint, hintLp);

        card.setClickable(true);
        card.setOnClickListener(v -> openActiveOrder());
    }

    private void openActiveOrder() {
        if (activeOrderJson != null) {
            Intent intent = new Intent(this, CustomerOrderDetailActivity.class);
            intent.putExtra("order_json", activeOrderJson.toString());
            startActivity(intent);
        } else {
            startActivity(new Intent(this, CustomerHistoryActivity.class));
        }
    }

    private void buildRecommendationSection() {
        recommendationController =
                new RecommendationSectionController(this);

        content.addView(
                recommendationController.buildView(),
                new LinearLayout.LayoutParams(-1, -2)
        );
    }

    private View buildBottomNavigation() {
        return CustomerBottomNavigation.build(this, CustomerPageTransition.HOME);
    }

    private void loadLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        if (locationText != null) locationText.setText("Mencari lokasi terbaru...");
        if (clusterText != null) clusterText.setText("Cluster: mendeteksi...");
        try {
            TransivaFreshLocation.request(this, new TransivaFreshLocation.Callback() {
                @Override public void onLocation(Location location, boolean fresh) {
                    resolveLocation(location);
                }
                @Override public void onFailure(String message) {
                    if (locationText != null) locationText.setText("Lokasi belum ditemukan");
                    if (clusterText != null) clusterText.setText("Cluster: belum diketahui");
                    new TransivaAlertDialogBuilder(CustomerDashboardActivity.this)
                            .setTitle("Lokasi")
                            .setMessage(message)
                            .setNegativeButton("Tutup", null)
                            .setPositiveButton("Pengaturan", (d,w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                            .show();
                }
            });
        } catch (SecurityException e) {
            if (locationText != null) locationText.setText("Izin lokasi diperlukan");
        }
    }

    private void resolveLocation(Location location) {
        new Thread(
                () -> {
                    String result = "Lokasi saya";

                    try {
                        List<Address> addresses =
                                new Geocoder(
                                        this,
                                        new Locale("id", "ID")
                                ).getFromLocation(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        1
                                );

                        if (
                                addresses != null
                                        && !addresses.isEmpty()
                        ) {
                            Address address =
                                    addresses.get(0);

                            result = first(
                                    address.getSubLocality(),
                                    address.getLocality(),
                                    address.getSubAdminArea(),
                                    "Lokasi saya"
                            );
                        }

                        new SessionManager(this)
                                .saveLastLocation(
                                        String.valueOf(
                                                location.getLatitude()
                                        ),
                                        String.valueOf(
                                                location.getLongitude()
                                        )
                                );

                    } catch (Exception ignored) {
                    }

                    String finalResult = result;

                    runOnUiThread(
                            () -> {
                                currentLocation = finalResult;
                                locationText.setText(finalResult);
                                TransivaCluster.Item cluster = TransivaCluster.nearest(location.getLatitude(), location.getLongitude());
                                if (clusterText != null) clusterText.setText("Cluster " + cluster.id + " • " + cluster.name);
                                refreshSmartRecommendation();
                            }
                    );
                }
        ).start();
    }

    @Override
    public void showLoading(boolean visible) {
        if (loading != null) {
            loading.setVisibility(
                    visible
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    @Override
    public void showDashboard(
            DashboardState state
    ) {
        if (state == null) {
            return;
        }

        currentBalance = state.balance;
        balanceText.setText(
                rupiah(state.balance)
        );

        String activeOrderText =
                first(
                        state.activeOrderText,
                        "Belum ada pesanan aktif"
                );

        currentOrderText = activeOrderText;
        activeOrderJson = state.activeOrder;
        orderText.setText(activeOrderText);

        if (orderCard != null) {
            orderCard.setBackground(Shape.roundStroke(
                    state.activeOrder != null ? "#F0F7FF" : "#FFFFFF",
                    state.activeOrder != null ? "#9CCBFF" : "#EDF2F7", dp(17), 1));
        }

        renderGrowthState(state);

        boolean hasActiveOrder =
                isActiveOrderText(activeOrderText);

        if (orderHint != null) {
            orderHint.setVisibility(
                    hasActiveOrder
                            ? View.GONE
                            : View.VISIBLE
            );
        }

        renderPromos(state.promos);
        refreshSmartRecommendation();
    }

    private void renderGrowthState(DashboardState state) {
        if (state.loyalty != null) {
            JSONObject d = state.loyalty.optJSONObject("data");
            if (d != null) {
                String activeTier = first(d.optString("season_tier"), d.optString("tier"), "BRONZE");
                TierBadgeUi.saveActiveTier(this, activeTier);
                if (loyaltyTierBadge != null) TierBadgeUi.applyToImage(loyaltyTierBadge, activeTier);
                if (loyaltyTierText != null) loyaltyTierText.setText(TierBadgeUi.normalize(activeTier));
                int coinBalance = d.optInt("coin_balance", 0);
                if (loyaltyPointsText != null) loyaltyPointsText.setText(coinBalance + " koin");
                if (loyaltyNextText != null) loyaltyNextText.setText(first(d.optString("next_tier_text"), ""));
            }
        }
        if (state.referral != null) {
            String code = first(state.referral.optString("referral_code"), "Ajak teman");
            if (referralCodeText != null) referralCodeText.setText(code);
            if (referralStatText != null) referralStatText.setText(state.referral.optInt("rewarded_count", 0) + " berhasil");
        }
        if (state.bestOffer != null) {
            JSONObject offer = state.bestOffer.optJSONObject("offer");
            if (offer != null) {
                if (offerTitleText != null) offerTitleText.setText("💸 " + first(offer.optString("title"), "Transiva Hemat"));
                if (offerDetailText != null) offerDetailText.setText(first(offer.optString("saving_text"), offer.optString("description"), "Promo terbaik tersedia"));
            }
        }
    }

    @Override
    public void showError(String message) {
        // Saat dashboard gagal dimuat, promo tidak boleh meninggalkan
        // area kosong tinggi. Tampilkan empty state yang ringkas.
        renderPromos(null);

        Toast.makeText(
                this,
                first(
                        message,
                        "Dashboard gagal dimuat"
                ),
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean isActiveOrderText(
            String value
    ) {
        String normalized =
                first(value)
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (normalized.isEmpty()) {
            return false;
        }

        return !normalized.equals(
                "belum ada pesanan aktif"
        )
                && !normalized.equals(
                "tidak ada pesanan aktif"
        )
                && !normalized.equals(
                "belum ada order aktif"
        )
                && !normalized.equals(
                "tidak ada order aktif"
        )
                && !normalized.equals(
                "no active order"
        );
    }

    private TextView text(
            String value,
            int sp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(
                value == null ? "" : value
        );

        view.setTextSize(sp);

        view.setTextColor(
                Color.parseColor(color)
        );

        view.setIncludeFontPadding(false);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private int drawable(String name) {
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

    private String rupiah(double amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(
                new Locale("id", "ID")
        );
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(amount);
    }

    private String first(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
                            && !"null".equalsIgnoreCase(
                                    value.trim()
                            )
                            && !"undefined".equalsIgnoreCase(
                                    value.trim()
                            )
            ) {
                return value.trim();
            }
        }

        return "";
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (
                requestCode == REQ_LOCATION
                        && grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED
        ) {
            loadLocation();

        } else if (requestCode == REQ_LOCATION) {
            locationText.setText("Izin ditolak");
        }
    }
}
