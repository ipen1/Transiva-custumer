package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerHistoryActivity extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/";

    private static final int TIMEOUT_MS = 20000;
    private static final String ACTION_URL = BASE_URL + "server/customer_order_action.php";
    private static final String DRIVER_REVIEW_URL = BASE_URL + "server/save_driver_review.php";
    private static final String FOOD_REVIEW_URL = BASE_URL + "server/save_food_review.php";
    private static final String FOOD_ORDER_REVIEW_URL = BASE_URL + "server/customer_food_order_review.php";

    private static final String TAB_ACTIVE = "active";
    private static final String TAB_HISTORY = "history";

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final List<JSONObject> allOrders =
            new ArrayList<>();

    private FrameLayout page;
    private LinearLayout content;
    private LinearLayout listBox;
    private LinearLayout serviceChipRow;
    private LinearLayout tabRow;
    private ProgressBar progressBar;
    private EditText searchInput;
    private TextView summaryTotal;
    private TextView summaryActive;
    private TextView summaryDone;
    private TextView summarySpent;

    private int userId;
    private String username = "User";

    private String selectedTab = TAB_ACTIVE;
    private String selectedService = "all";
    private String searchQuery = "";
    private boolean loading;
    private boolean activityVisible;
    private static final long REALTIME_REFRESH_MS = 5000L;

    private final Runnable realtimeRefresh = new Runnable() {
        @Override
        public void run() {
            if (!activityVisible) {
                return;
            }

            if (!loading && listBox != null) {
                loadHistory(true);
            }

            mainHandler.postDelayed(this, REALTIME_REFRESH_MS);
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

        loadSession();
        setContentView(buildScreen());
        CustomerAppSettings.apply(this);
        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);
        activityVisible = true;

        mainHandler.removeCallbacks(realtimeRefresh);

        if (!loading && listBox != null) {
            loadHistory(true);
        }

        mainHandler.postDelayed(realtimeRefresh, REALTIME_REFRESH_MS);
    }

    @Override
    protected void onPause() {
        activityVisible = false;
        mainHandler.removeCallbacks(realtimeRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        activityVisible = false;
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void loadSession() {
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
        page = new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F6F9FE")
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
                dp(14),
                dp(14),
                dp(24)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(-1, -2)
        );

        buildHeader();
        buildSummary();
        buildTabs();
        buildSearch();
        buildServiceFilters();

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams listLp =
                new LinearLayout.LayoutParams(-1, -2);

        listLp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        content.addView(listBox, listLp);

        shell.addView(
                buildBottomNavigation(),
                new LinearLayout.LayoutParams(-1, dp(66))
        );

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(
                        dp(44),
                        dp(44)
                );

        progressLp.gravity = Gravity.CENTER;

        page.addView(progressBar, progressLp);

        renderOrders();

        return page;
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(
                "Aktivitas",
                24,
                "#0B3A78",
                true
        );

        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );

        titleBox.addView(title);

        titleBox.addView(
                text(
                        "Pantau semua pesanan Transiva",
                        11,
                        "#718096",
                        false
                )
        );

        row.addView(
                titleBox,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView refresh = text(
                "↻",
                25,
                "#0B7CFF",
                true
        );

        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#DCE8F6",
                        16,
                        1
                )
        );

        refresh.setOnClickListener(
                view -> loadHistory()
        );

        row.addView(
                refresh,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                )
        );

        content.addView(row);
    }

    private void buildSummary() {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(14)
        );

        card.setBackground(
                gradient(
                        "#0868F5",
                        "#23A7FF",
                        20
                )
        );

        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(-1, -2);

        cardLp.setMargins(
                0,
                dp(14),
                0,
                dp(14)
        );

        content.addView(card, cardLp);

        card.addView(
                text(
                        "Ringkasan aktivitas",
                        13,
                        "#EAF5FF",
                        true
                )
        );

        TextView greeting = text(
                "Halo, " + username,
                19,
                "#FFFFFF",
                true
        );

        LinearLayout.LayoutParams greetingLp =
                new LinearLayout.LayoutParams(-1, -2);

        greetingLp.setMargins(
                0,
                dp(2),
                0,
                dp(12)
        );

        card.addView(greeting, greetingLp);

        LinearLayout stats =
                new LinearLayout(this);

        stats.setOrientation(
                LinearLayout.HORIZONTAL
        );

        summaryTotal = summaryMetric(
                stats,
                "0",
                "Total"
        );

        summaryActive = summaryMetric(
                stats,
                "0",
                "Berjalan"
        );

        summaryDone = summaryMetric(
                stats,
                "0",
                "Selesai"
        );

        summarySpent = summaryMetric(
                stats,
                "Rp0",
                "Pengeluaran"
        );

        card.addView(stats);
    }

    private TextView summaryMetric(
            LinearLayout parent,
            String value,
            String label
    ) {
        LinearLayout item =
                new LinearLayout(this);

        item.setOrientation(
                LinearLayout.VERTICAL
        );

        item.setGravity(Gravity.CENTER);

        TextView number = text(
                value,
                15,
                "#FFFFFF",
                true
        );

        number.setGravity(Gravity.CENTER);
        number.setSingleLine(true);

        TextView caption = text(
                label,
                9,
                "#DDEFFF",
                false
        );

        caption.setGravity(Gravity.CENTER);

        item.addView(number);
        item.addView(caption);

        parent.addView(
                item,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        return number;
    }

    private void buildTabs() {
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        tabRow.setPadding(
                dp(4),
                dp(4),
                dp(4),
                dp(4)
        );

        tabRow.setBackground(
                round(
                        "#EAF1FA",
                        15
                )
        );

        content.addView(
                tabRow,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                )
        );

        rebuildTabs();
    }

    private void rebuildTabs() {
        tabRow.removeAllViews();

        Button active = tabButton(
                "Sedang Berjalan",
                TAB_ACTIVE.equals(selectedTab)
        );

        Button history = tabButton(
                "Riwayat",
                TAB_HISTORY.equals(selectedTab)
        );

        active.setOnClickListener(view -> {
            selectedTab = TAB_ACTIVE;
            rebuildTabs();
            renderOrders();
        });

        history.setOnClickListener(view -> {
            selectedTab = TAB_HISTORY;
            rebuildTabs();
            renderOrders();
        });

        tabRow.addView(
                active,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                )
        );

        LinearLayout.LayoutParams historyLp =
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1
                );

        historyLp.setMargins(
                dp(4),
                0,
                0,
                0
        );

        tabRow.addView(history, historyLp);
    }

    private Button tabButton(
            String label,
            boolean active
    ) {
        Button button = new Button(this);

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);

        button.setTextColor(
                Color.parseColor(
                        active
                                ? "#0B7CFF"
                                : "#64748B"
                )
        );

        button.setTypeface(
                Typeface.DEFAULT,
                active
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );

        button.setBackground(
                active
                        ? round("#FFFFFF", 12)
                        : round("#EAF1FA", 12)
        );

        if (active) {
            button.setElevation(dp(1));
        }

        return button;
    }

    private void buildSearch() {
        searchInput = new EditText(this);

        searchInput.setHint(
                "Cari order, driver, merchant..."
        );

        searchInput.setSingleLine(true);
        searchInput.setTextSize(12);

        searchInput.setTextColor(
                Color.parseColor("#0F172A")
        );

        searchInput.setHintTextColor(
                Color.parseColor("#94A3B8")
        );

        searchInput.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        searchInput.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#DCE8F6",
                        15,
                        1
                )
        );

        searchInput.setImeOptions(
                EditorInfo.IME_ACTION_SEARCH
        );

        searchInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence sequence,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence sequence,
                            int start,
                            int before,
                            int count
                    ) {
                        searchQuery =
                                sequence == null
                                        ? ""
                                        : sequence
                                        .toString()
                                        .trim()
                                        .toLowerCase(
                                                Locale.ROOT
                                        );

                        renderOrders();
                    }

                    @Override
                    public void afterTextChanged(
                            Editable editable
                    ) {
                    }
                }
        );

        LinearLayout.LayoutParams searchLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                );

        searchLp.setMargins(
                0,
                dp(12),
                0,
                dp(10)
        );

        content.addView(searchInput, searchLp);
    }

    private void buildServiceFilters() {
        HorizontalScrollView scroll =
                new HorizontalScrollView(this);

        scroll.setHorizontalScrollBarEnabled(
                false
        );

        scroll.setClipToPadding(false);

        serviceChipRow =
                new LinearLayout(this);

        serviceChipRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        scroll.addView(
                serviceChipRow,
                new HorizontalScrollView.LayoutParams(
                        -2,
                        -2
                )
        );

        content.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(42)
                )
        );

        rebuildServiceFilters();
    }

    private void rebuildServiceFilters() {
        serviceChipRow.removeAllViews();

        addServiceChip("Semua", "all");
        addServiceChip("TransRide", "ride");
        addServiceChip("TransCar", "car");
        addServiceChip("TransFood", "food");
        addServiceChip("TransSend", "pickup");
        addServiceChip("TransShop", "shop");
    }

    private void addServiceChip(
            String label,
            String value
    ) {
        boolean selected =
                value.equals(selectedService);

        TextView chip = text(
                label,
                10,
                selected
                        ? "#FFFFFF"
                        : "#52647A",
                selected
        );

        chip.setGravity(Gravity.CENTER);

        chip.setPadding(
                dp(13),
                dp(7),
                dp(13),
                dp(7)
        );

        chip.setBackground(
                selected
                        ? round("#0B7CFF", 15)
                        : roundStroke(
                                "#FFFFFF",
                                "#DCE8F6",
                                15,
                                1
                        )
        );

        chip.setOnClickListener(view -> {
            selectedService = value;
            rebuildServiceFilters();
            renderOrders();
        });

        LinearLayout.LayoutParams chipLp =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(34)
                );

        chipLp.setMargins(
                0,
                0,
                dp(7),
                0
        );

        serviceChipRow.addView(chip, chipLp);
    }

    private void updateSummary() {
        int total = allOrders.size();
        int active = 0;
        int done = 0;
        double spent = 0;

        for (JSONObject order : allOrders) {
            String status = normalizedStatus(
                    order.optString("status")
            );

            if (isActiveStatus(status)) {
                active++;
            }

            if (isCompletedStatus(status)) {
                done++;
                spent += orderPrice(order);
            }
        }

        summaryTotal.setText(
                String.valueOf(total)
        );

        summaryActive.setText(
                String.valueOf(active)
        );

        summaryDone.setText(
                String.valueOf(done)
        );

        summarySpent.setText(
                compactRupiah(spent)
        );
    }

    private void renderOrders() {
        if (listBox == null) {
            return;
        }

        listBox.removeAllViews();
        updateSummary();

        if (loading) {
            addInfoState(
                    "Memuat aktivitas...",
                    "Data pesanan sedang diperbarui."
            );
            return;
        }

        List<JSONObject> filtered =
                filteredOrders();

        if (filtered.isEmpty()) {
            if (TAB_ACTIVE.equals(selectedTab)) {
                addEmptyState(
                        "Belum ada aktivitas berjalan",
                        "Pesan layanan Transiva dan pantau prosesnya di sini.",
                        "Pesan Sekarang",
                        () -> startActivity(
                                new Intent(
                                        this,
                                        CustomerDashboardActivity.class
                                )
                        )
                );
            } else {
                addEmptyState(
                        "Riwayat belum tersedia",
                        "Pesanan yang selesai atau dibatalkan akan tampil di sini.",
                        "Kembali ke Beranda",
                        () -> startActivity(
                                new Intent(
                                        this,
                                        CustomerDashboardActivity.class
                                )
                        )
                );
            }

            return;
        }

        TextView section = text(
                TAB_ACTIVE.equals(selectedTab)
                        ? "Pesanan berjalan"
                        : "Riwayat pesanan",
                15,
                "#0B3A78",
                true
        );

        LinearLayout.LayoutParams sectionLp =
                new LinearLayout.LayoutParams(-1, -2);

        sectionLp.setMargins(
                0,
                0,
                0,
                dp(9)
        );

        listBox.addView(section, sectionLp);

        for (JSONObject order : filtered) {
            listBox.addView(
                    buildOrderCard(order)
            );
        }
    }

    private List<JSONObject> filteredOrders() {
        List<JSONObject> result =
                new ArrayList<>();

        for (JSONObject order : allOrders) {
            String status = normalizedStatus(
                    order.optString("status")
            );

            boolean tabMatch =
                    TAB_ACTIVE.equals(selectedTab)
                            ? isActiveStatus(status)
                            : !isActiveStatus(status);

            if (!tabMatch) {
                continue;
            }

            if (!serviceMatches(order)) {
                continue;
            }

            if (!queryMatches(order)) {
                continue;
            }

            result.add(order);
        }

        return result;
    }

    private boolean serviceMatches(
            JSONObject order
    ) {
        if ("all".equals(selectedService)) {
            return true;
        }

        String type = serviceType(order);

        return type.contains(selectedService);
    }

    private boolean queryMatches(
            JSONObject order
    ) {
        if (searchQuery.isEmpty()) {
            return true;
        }

        String haystack =
                (
                        first(
                                order.optString("order_id"),
                                order.optString("id"),
                                ""
                        )
                                + " "
                                + serviceName(order)
                                + " "
                                + order.optString("driver")
                                + " "
                                + order.optString(
                                "driver_username"
                        )
                                + " "
                                + order.optString(
                                "restaurant_name"
                        )
                                + " "
                                + order.optString(
                                "merchant_name"
                        )
                                + " "
                                + order.optString(
                                "wisata_name"
                        )
                                + " "
                                + order.optString(
                                "pickup_address"
                        )
                                + " "
                                + order.optString(
                                "delivery_address"
                        )
                )
                        .toLowerCase(Locale.ROOT);

        return haystack.contains(searchQuery);
    }

    private View buildOrderCard(
            JSONObject order
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(14),
                dp(13),
                dp(14),
                dp(13)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E3ECF7",
                        18,
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
                dp(10)
        );

        card.setLayoutParams(cardLp);

        LinearLayout top =
                new LinearLayout(this);

        top.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconFrame =
                new FrameLayout(this);

        iconFrame.setBackground(
                round(
                        serviceSoftColor(order),
                        14
                )
        );

        ImageView icon = new ImageView(this);

        int iconResource =
                serviceDrawable(order);

        if (iconResource != 0) {
            icon.setImageResource(iconResource);
        }

        icon.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        FrameLayout.LayoutParams iconLp =
                new FrameLayout.LayoutParams(
                        dp(32),
                        dp(32)
                );

        iconLp.gravity = Gravity.CENTER;

        iconFrame.addView(icon, iconLp);

        top.addView(
                iconFrame,
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                )
        );

        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );

        titleBox.setPadding(
                dp(10),
                0,
                dp(6),
                0
        );

        titleBox.addView(
                text(
                        serviceName(order),
                        14,
                        "#0F3C75",
                        true
                )
        );

        titleBox.addView(
                text(
                        "Order #"
                                + first(
                                order.optString(
                                        "order_id"
                                ),
                                order.optString("id"),
                                "-"
                        ),
                        9,
                        "#8495A8",
                        false
                )
        );

        top.addView(
                titleBox,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        String status =
                normalizedStatus(
                        order.optString("status")
                );

        TextView badge = text(
                OrderStatusPresentation.label(
                        status,
                        serviceType(order)
                ),
                9,
                statusTextColor(status),
                true
        );

        badge.setGravity(Gravity.CENTER);

        badge.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );

        badge.setBackground(
                round(
                        statusBackground(status),
                        13
                )
        );

        top.addView(badge);

        card.addView(top);

        // Khusus TransSend: OTP ditampilkan kepada customer, bukan tombol konfirmasi terima.
        String deliveryOtp = order.optString("delivery_otp", "").trim();
        if (isPickupOrder(order) && isActiveStatus(status) && !deliveryOtp.isEmpty()) {
            LinearLayout otpBox = new LinearLayout(this);
            otpBox.setOrientation(LinearLayout.VERTICAL);
            otpBox.setPadding(dp(13), dp(10), dp(13), dp(10));
            otpBox.setBackground(roundStroke("#EFF8FF", "#79BFFF", 14, 1));

            otpBox.addView(text("Kode OTP TransSend", 10, "#52708F", true));

            LinearLayout otpRow = new LinearLayout(this);
            otpRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView otpValue = text(deliveryOtp, 22, "#0B5EB7", true);
            otpValue.setLetterSpacing(0.12f);
            otpValue.setTextIsSelectable(true);
            otpRow.addView(otpValue, new LinearLayout.LayoutParams(0, -2, 1));

            Button copyOtp = outlineButton("Salin");
            copyOtp.setOnClickListener(view -> copyOtpToClipboard(deliveryOtp));
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(dp(82), dp(38));
            copyLp.setMargins(dp(8), 0, 0, 0);
            otpRow.addView(copyOtp, copyLp);

            LinearLayout.LayoutParams otpRowLp = new LinearLayout.LayoutParams(-1, -2);
            otpRowLp.setMargins(0, dp(3), 0, 0);
            otpBox.addView(otpRow, otpRowLp);

            TextView otpHint = text(
                    "Berikan kode ini kepada driver setelah paket benar-benar Anda terima.",
                    10,
                    "#64748B",
                    false
            );
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
            hintLp.setMargins(0, dp(4), 0, 0);
            otpBox.addView(otpHint, hintLp);

            LinearLayout.LayoutParams otpLp = new LinearLayout.LayoutParams(-1, -2);
            otpLp.setMargins(0, dp(10), 0, 0);
            card.addView(otpBox, otpLp);
        }

        String mainLine = orderMainLine(order);

        if (!mainLine.isEmpty()) {
            TextView route = text(
                    mainLine,
                    12,
                    "#26384D",
                    true
            );

            route.setMaxLines(2);

            LinearLayout.LayoutParams routeLp =
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    );

            routeLp.setMargins(
                    0,
                    dp(10),
                    0,
                    0
            );

            card.addView(route, routeLp);
        }

        String progress =
                progressDescription(order);

        if (!progress.isEmpty()) {
            LinearLayout progressRow =
                    new LinearLayout(this);

            progressRow.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            View dot = new View(this);

            dot.setBackground(
                    round(
                            statusDotColor(status),
                            4
                    )
            );

            progressRow.addView(
                    dot,
                    new LinearLayout.LayoutParams(
                            dp(8),
                            dp(8)
                    )
            );

            TextView progressText = text(
                    progress,
                    11,
                    "#64748B",
                    false
            );

            LinearLayout.LayoutParams progressTextLp =
                    new LinearLayout.LayoutParams(
                            0,
                            -2,
                            1
                    );

            progressTextLp.setMargins(
                    dp(7),
                    0,
                    0,
                    0
            );

            progressRow.addView(
                    progressText,
                    progressTextLp
            );

            LinearLayout.LayoutParams progressLp =
                    new LinearLayout.LayoutParams(
                            -1,
                            -2
                    );

            progressLp.setMargins(
                    0,
                    dp(7),
                    0,
                    0
            );

            card.addView(progressRow, progressLp);
        }

        LinearLayout meta =
                new LinearLayout(this);

        meta.setGravity(Gravity.CENTER_VERTICAL);

        TextView time = text(
                displayDate(order),
                10,
                "#8495A8",
                false
        );

        meta.addView(
                time,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        double price = orderPrice(order);

        TextView total = text(
                price > 0
                        ? rupiah(price)
                        : "-",
                13,
                "#0B3A78",
                true
        );

        meta.addView(total);

        LinearLayout.LayoutParams metaLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        metaLp.setMargins(
                0,
                dp(10),
                0,
                0
        );

        card.addView(meta, metaLp);

        if (isCompletedStatus(status) && supportsActivityRating(order)) {
            card.addView(buildRatingStatus(order), ratingStatusLayoutParams());
        }

        LinearLayout actions =
                new LinearLayout(this);

        actions.setOrientation(
                LinearLayout.HORIZONTAL
        );

        actions.setPadding(
                0,
                dp(11),
                0,
                0
        );

        Button detail =
                outlineButton("Lihat Detail");

        detail.setOnClickListener(
                view -> showOrderDetail(order)
        );

        actions.addView(
                detail,
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1
                )
        );

        if (isActiveStatus(status)) {
            boolean customerReceived = order.optInt("customer_received", 0) == 1;
            String normalizedStatus = normalized(status).trim();
            boolean showReceive = "arrived_delivery".equals(normalizedStatus)
                    && !customerReceived
                    && supportsReceiveButton(order);

            // Tombol utama tetap menampilkan Lacak. Tombol konfirmasi tidak boleh
            // menggantikan tombol Lacak ketika driver telah tiba di pengantaran.
            if (canTrackOrder(status)) {
                Button track = primaryButton("Lacak");

                track.setOnClickListener(
                        view -> openActiveOrder(order)
                );

                LinearLayout.LayoutParams trackLp =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(42),
                                1
                        );

                trackLp.setMargins(
                        dp(8),
                        0,
                        0,
                        0
                );

                actions.addView(track, trackLp);
            } else if (canCustomerCancel(status)) {
                Button cancel = dangerButton("Batalkan");

                cancel.setOnClickListener(
                        view -> confirmCancelOrder(order)
                );

                LinearLayout.LayoutParams cancelLp =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(42),
                                1
                        );

                cancelLp.setMargins(
                        dp(8),
                        0,
                        0,
                        0
                );

                actions.addView(cancel, cancelLp);
            }

            if (showReceive) {
                Button receive = primaryButton("Terima Pesanan");

                receive.setOnClickListener(
                        view -> confirmReceivedFromActivity(order)
                );

                LinearLayout.LayoutParams receiveLp =
                        new LinearLayout.LayoutParams(
                                -1,
                                dp(44)
                        );

                receiveLp.setMargins(
                        0,
                        dp(9),
                        0,
                        0
                );

                card.addView(receive, receiveLp);
            }
        } else {
            if (isCompletedStatus(status) && supportsActivityRating(order) && (isFoodOrder(order) || order.optInt("rating", 0) <= 0)) {
                Button rate = primaryButton(isFoodOrder(order) ? "Nilai Pesanan" : "Nilai Driver");
                rate.setOnClickListener(view -> showActivityReviewDialog(order));
                LinearLayout.LayoutParams rateLp = new LinearLayout.LayoutParams(0, dp(42), 1);
                rateLp.setMargins(dp(8), 0, 0, 0);
                actions.addView(rate, rateLp);
            }

            Button repeat =
                    primaryButton("Pesan Lagi");

            repeat.setOnClickListener(
                    view -> openRepeat(order)
            );

            LinearLayout.LayoutParams repeatLp =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(42),
                            1
                    );

            repeatLp.setMargins(
                    dp(8),
                    0,
                    0,
                    0
            );

            actions.addView(repeat, repeatLp);
        }

        card.addView(actions);

        return card;
    }

    private LinearLayout.LayoutParams ratingStatusLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        return lp;
    }

    private View buildRatingStatus(JSONObject order) {
        int rating = order.optInt("rating", 0);
        boolean food = isFoodOrder(order);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(11), dp(8), dp(11), dp(8));

        if (food) {
            box.setBackground(roundStroke("#EFF6FF", "#BFDBFE", 13, 1));
            TextView label = text("Nilai merchant, menu yang dipesan, dan pelayanan driver", 10, "#1D4ED8", true);
            box.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView hint = text("Buka", 10, "#0B7CFF", true);
            hint.setOnClickListener(view -> showFoodOrderReviewDialog(order));
            box.addView(hint);
        } else if (rating > 0) {
            box.setBackground(roundStroke("#F0FDF4", "#BBF7D0", 13, 1));
            TextView label = text("Driver sudah dinilai  " + starText(rating), 10, "#15803D", true);
            box.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            box.setBackground(roundStroke("#FFF7ED", "#FED7AA", 13, 1));
            TextView label = text("Driver belum dinilai", 10, "#C2410C", true);
            box.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView hint = text("Beri rating", 10, "#0B7CFF", true);
            hint.setOnClickListener(view -> showActivityReviewDialog(order));
            box.addView(hint);
        }
        return box;
    }

    private String starText(int rating) {
        int safe = Math.max(1, Math.min(5, rating));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < safe; i++) out.append("★");
        for (int i = safe; i < 5; i++) out.append("☆");
        return out.toString();
    }

    private boolean supportsActivityRating(JSONObject order) {
        String type = serviceType(order);
        return type.contains("food")
                || type.contains("ride")
                || type.contains("bike")
                || type.contains("car")
                || type.contains("mobil")
                || type.contains("pickup");
    }

    private boolean isFoodOrder(JSONObject order) {
        return serviceType(order).contains("food");
    }

    private void showActivityReviewDialog(JSONObject order) {
        if (isFoodOrder(order)) {
            showFoodOrderReviewDialog(order);
            return;
        }
        if (order.optInt("rating", 0) > 0) {
            toast(isFoodOrder(order) ? "Makanan sudah pernah dinilai." : "Driver sudah pernah dinilai.");
            return;
        }

        boolean food = isFoodOrder(order);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView prompt = text(
                food
                        ? "Bagaimana makanan dan pelayanan " + first(order.optString("restaurant_name"), order.optString("pickup_address"), "merchant") + "?"
                        : "Bagaimana pelayanan " + first(order.optString("driver_name"), order.optString("driver"), "driver") + "?",
                14,
                "#0B3A78",
                true
        );
        box.addView(prompt);

        RatingBar stars = new RatingBar(this, null, android.R.attr.ratingBarStyle);
        stars.setNumStars(5);
        stars.setStepSize(1f);
        stars.setRating(5f);
        LinearLayout.LayoutParams starsLp = new LinearLayout.LayoutParams(-2, -2);
        starsLp.setMargins(0, dp(6), 0, 0);
        box.addView(stars, starsLp);

        EditText review = new EditText(this);
        review.setHint(food ? "Tulis ulasan makanan (opsional)" : "Tulis ulasan driver (opsional)");
        review.setMinLines(2);
        review.setMaxLines(4);
        review.setMaxWidth(dp(320));
        box.addView(review, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new TransivaAlertDialogBuilder(this)
                .setTitle(food ? "Beri Rating Makanan" : "Beri Rating Driver")
                .setView(box)
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Kirim", null)
                .create();

        dialog.setOnShowListener(ignore -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int rating = Math.max(1, Math.min(5, Math.round(stars.getRating())));
            submitActivityReview(order, rating, review.getText().toString().trim(), dialog);
        }));
        dialog.show();
    }


    private void showFoodOrderReviewDialog(JSONObject order) {
        if (loading) return;
        loading = true;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String endpoint = FOOD_ORDER_REVIEW_URL
                        + "?id=" + order.optInt("id", 0)
                        + "&order_id=" + Uri.encode(first(order.optString("order_id"), ""))
                        + "&v=" + System.currentTimeMillis();
                JSONObject state = getJson(endpoint);
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    if (!state.optBoolean("success", false)) {
                        new TransivaAlertDialogBuilder(this)
                                .setTitle("Penilaian Pesanan")
                                .setMessage(first(state.optString("message"), "Gagal memuat status penilaian."))
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    buildFoodOrderReviewDialog(order, state);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    toast("Gagal memuat penilaian pesanan.");
                });
            }
        }, "food-order-review-load").start();
    }

    private static class FoodReviewInput {
        String type;
        int targetId;
        int rating;
        LinearLayout starsRow;
        EditText comment;
        FoodReviewInput(String type, int targetId, int rating, LinearLayout starsRow, EditText comment) {
            this.type = type;
            this.targetId = targetId;
            this.rating = Math.max(1, Math.min(5, rating));
            this.starsRow = starsRow;
            this.comment = comment;
        }
    }

    private void buildFoodOrderReviewDialog(JSONObject order, JSONObject state) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(12));
        java.util.ArrayList<FoodReviewInput> inputs = new java.util.ArrayList<>();

        String merchantName = first(order.optString("restaurant_name"), order.optString("pickup_address"), "Merchant");
        JSONObject merchantState = state.optJSONObject("merchant");
        addFoodReviewSection(body, inputs, "merchant", order.optInt("restaurant_id", state.optInt("restaurant_id", 0)),
                "Merchant • " + merchantName,
                merchantState == null ? 0 : merchantState.optInt("rating", 0),
                merchantState == null ? "" : merchantState.optString("comment", ""));

        JSONArray foodItems = order.optJSONArray("food_items");
        JSONObject menuStates = state.optJSONObject("menus");
        java.util.HashSet<Integer> shownMenus = new java.util.HashSet<>();
        if (foodItems != null) {
            for (int i = 0; i < foodItems.length(); i++) {
                JSONObject item = foodItems.optJSONObject(i);
                if (item == null) continue;
                int menuId = item.optInt("menu_id", item.optInt("id", 0));
                if (menuId <= 0 || shownMenus.contains(menuId)) continue;
                shownMenus.add(menuId);
                JSONObject menuState = menuStates == null ? null : menuStates.optJSONObject(String.valueOf(menuId));
                String menuName = first(item.optString("name"), "Menu #" + menuId);
                addFoodReviewSection(body, inputs, "menu", menuId,
                        "Menu • " + menuName,
                        menuState == null ? 0 : menuState.optInt("rating", 0),
                        menuState == null ? "" : menuState.optString("comment", ""));
            }
        }

        String driverName = first(order.optString("driver_name"), order.optString("driver"), "");
        if (!driverName.isEmpty()) {
            JSONObject driverState = state.optJSONObject("driver");
            addFoodReviewSection(body, inputs, "driver", 0,
                    "Driver • " + driverName,
                    driverState == null ? 0 : driverState.optInt("rating", 0),
                    driverState == null ? "" : driverState.optString("comment", ""));
        }

        TextView note = text("Anda dapat memperbarui penilaian sebelumnya. Rating merchant dan menu akan terlihat oleh customer lain.", 11, "#64748B", false);
        note.setPadding(0, dp(8), 0, dp(4));
        body.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog dialog = new TransivaAlertDialogBuilder(this)
                .setTitle("Nilai Pesanan TransFood")
                .setView(scroll)
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Kirim Penilaian", null)
                .create();
        dialog.setOnShowListener(ignore -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submitFoodOrderReviews(order, inputs, dialog)));
        dialog.show();
    }

    private void addFoodReviewSection(LinearLayout parent, java.util.List<FoodReviewInput> inputs,
                                      String type, int targetId, String title, int currentRating, String currentComment) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundStroke("#FFFFFF", "#DCE8F6", 14, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(10));
        parent.addView(card, cardLp);
        card.addView(text(title, 13, "#0B3A78", true));

        LinearLayout starsRow = new LinearLayout(this);
        starsRow.setOrientation(LinearLayout.HORIZONTAL);
        starsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams starLp = new LinearLayout.LayoutParams(-1, dp(52));
        starLp.setMargins(0, dp(5), 0, dp(3));
        card.addView(starsRow, starLp);

        EditText comment = new EditText(this);
        comment.setHint("Komentar/saran (opsional)");
        comment.setMinLines(1);
        comment.setMaxLines(3);
        comment.setText(currentComment == null ? "" : currentComment);
        card.addView(comment, new LinearLayout.LayoutParams(-1, -2));

        FoodReviewInput input = new FoodReviewInput(
                type,
                targetId,
                currentRating > 0 ? currentRating : 5,
                starsRow,
                comment
        );
        inputs.add(input);
        renderFoodReviewStars(input);
    }

    private void renderFoodReviewStars(FoodReviewInput input) {
        if (input == null || input.starsRow == null) return;
        input.starsRow.removeAllViews();

        for (int i = 1; i <= 5; i++) {
            final int starValue = i;
            TextView star = text(i <= input.rating ? "★" : "☆", 32,
                    i <= input.rating ? "#FACC15" : "#94A3B8", true);
            star.setGravity(Gravity.CENTER);
            star.setContentDescription("Beri " + i + " bintang");
            star.setClickable(true);
            star.setFocusable(true);
            star.setPadding(dp(5), 0, dp(5), 0);
            star.setOnClickListener(v -> {
                input.rating = starValue;
                renderFoodReviewStars(input);
            });
            input.starsRow.addView(star, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        TextView value = text(input.rating + "/5", 12, "#475569", true);
        value.setPadding(dp(6), 0, 0, 0);
        input.starsRow.addView(value, new LinearLayout.LayoutParams(-2, -2));
    }

    private void submitFoodOrderReviews(JSONObject order, java.util.List<FoodReviewInput> inputs, AlertDialog dialog) {
        if (loading) return;
        final JSONObject payload = new JSONObject();
        try {
            payload.put("id", order.optInt("id", 0));
            payload.put("order_id", first(order.optString("order_id"), ""));
            JSONArray menus = new JSONArray();
            for (FoodReviewInput input : inputs) {
                int rating = Math.max(1, Math.min(5, input.rating));
                String comment = input.comment.getText().toString().trim();
                JSONObject value = new JSONObject();
                value.put("rating", rating);
                value.put("comment", comment);
                if ("merchant".equals(input.type)) {
                    payload.put("merchant", value);
                } else if ("driver".equals(input.type)) {
                    payload.put("driver", value);
                } else if ("menu".equals(input.type)) {
                    value.put("menu_id", input.targetId);
                    menus.put(value);
                }
            }
            payload.put("menus", menus);
        } catch (Exception e) {
            toast("Data penilaian tidak valid.");
            return;
        }

        loading = true;
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                JSONObject response = postJson(FOOD_ORDER_REVIEW_URL, payload);
                boolean success = response.optBoolean("success", false);
                String message = first(response.optString("message"), success ? "Penilaian berhasil disimpan." : "Penilaian gagal disimpan.");
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    if (success && dialog != null && dialog.isShowing()) dialog.dismiss();
                    new TransivaAlertDialogBuilder(this)
                            .setTitle(success ? "Terima kasih" : "Gagal")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    toast("Koneksi gagal menyimpan penilaian.");
                });
            }
        }, "food-order-review-save").start();
    }

    private void submitActivityReview(JSONObject order, int rating, String review, AlertDialog dialog) {
        if (loading) return;
        loading = true;
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                boolean food = isFoodOrder(order);
                JSONObject payload = new JSONObject();
                payload.put("order_id", first(order.optString("order_id"), order.optString("id")));
                payload.put("id", order.optInt("id", 0));
                payload.put("source", order.optString("source", "").contains("pickup") ? "pickup_orders" : "orders");
                payload.put("rating", rating);
                payload.put("review", review == null ? "" : review);

                JSONObject response = postJson(food ? FOOD_REVIEW_URL : DRIVER_REVIEW_URL, payload);
                boolean success = response.optBoolean("success", false);
                String message = first(
                        response.optString("message"),
                        success ? "Terima kasih atas penilaian Anda." : "Rating gagal disimpan."
                );

                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    if (success) {
                        try {
                            order.put("rating", rating);
                            order.put("review", review == null ? "" : review);
                        } catch (Exception ignored) {}
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        renderOrders();
                    }
                    new TransivaAlertDialogBuilder(this)
                            .setTitle(success ? "Terima kasih" : "Gagal")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    new TransivaAlertDialogBuilder(this)
                            .setTitle("Gagal")
                            .setMessage("Koneksi server bermasalah. Rating belum dikirim, silakan coba kembali.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "activity-save-review").start();
    }

    private void confirmReceivedFromActivity(JSONObject order) {
        new TransivaAlertDialogBuilder(this)
                .setTitle("Terima pesanan ini?")
                .setMessage("Pastikan pesanan sudah Anda terima dengan baik sebelum melakukan konfirmasi.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Ya, sudah diterima", (dialog, which) -> sendReceivedConfirmation(order))
                .show();
    }

    private void sendReceivedConfirmation(JSONObject order) {
        if (loading) {
            return;
        }

        loading = true;
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put(
                        "order_id",
                        first(order.optString("order_id"), order.optString("id"))
                );
                payload.put(
                        "source",
                        order.optString("source", "").contains("pickup")
                                ? "pickup_orders"
                                : "orders"
                );
                payload.put("action", "confirm_received");

                JSONObject response = postJson(ACTION_URL, payload);
                boolean success = response.optBoolean("success", false);
                String message = first(
                        response.optString("message"),
                        success ? "Pesanan berhasil dikonfirmasi diterima." : "Konfirmasi gagal."
                );

                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);

                    if (success) {
                        try {
                            order.put("customer_received", 1);
                        } catch (Exception ignored) {
                        }
                        renderOrders();
                    }

                    new TransivaAlertDialogBuilder(this)
                            .setTitle(success ? "Berhasil" : "Gagal")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);

                    new TransivaAlertDialogBuilder(this)
                            .setTitle("Gagal")
                            .setMessage("Koneksi server bermasalah. Silakan coba kembali.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }, "activity-confirm-received").start();
    }

    private JSONObject postJson(String endpoint, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            CustomerApiClient.applySecurity(this, connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] body = payload.toString().getBytes("UTF-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
                output.flush();
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (stream == null) {
                throw new IllegalStateException("Respons server kosong");
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, "UTF-8")
            );
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
            reader.close();

            return new JSONObject(responseBody.toString());

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void addEmptyState(
            String title,
            String description,
            String buttonLabel,
            Runnable action
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(Gravity.CENTER);

        card.setPadding(
                dp(20),
                dp(26),
                dp(20),
                dp(24)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E3ECF7",
                        19,
                        1
                )
        );

        ImageView image = new ImageView(this);

        int emptyDrawable =
                drawable("img_order_empty");

        if (emptyDrawable != 0) {
            image.setImageResource(
                    emptyDrawable
            );
        }

        image.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        card.addView(
                image,
                new LinearLayout.LayoutParams(
                        dp(100),
                        dp(82)
                )
        );

        TextView heading = text(
                title,
                15,
                "#0B3A78",
                true
        );

        heading.setGravity(Gravity.CENTER);

        card.addView(heading);

        TextView body = text(
                description,
                11,
                "#718096",
                false
        );

        body.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams bodyLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        bodyLp.setMargins(
                0,
                dp(6),
                0,
                dp(14)
        );

        card.addView(body, bodyLp);

        Button button =
                primaryButton(buttonLabel);

        button.setOnClickListener(
                view -> action.run()
        );

        card.addView(
                button,
                new LinearLayout.LayoutParams(
                        dp(180),
                        dp(44)
                )
        );

        listBox.addView(card);
    }

    private void addInfoState(
            String title,
            String description
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(16),
                dp(18),
                dp(16),
                dp(18)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E3ECF7",
                        17,
                        1
                )
        );

        card.addView(
                text(
                        title,
                        14,
                        "#0B3A78",
                        true
                )
        );

        TextView body = text(
                description,
                11,
                "#718096",
                false
        );

        LinearLayout.LayoutParams bodyLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        bodyLp.setMargins(
                0,
                dp(5),
                0,
                0
        );

        card.addView(body, bodyLp);

        listBox.addView(card);
    }

    private void loadHistory() {
        loadHistory(false);
    }

    private void loadHistory(boolean silent) {
        if (loading) {
            return;
        }

        if (userId <= 0) {
            toast(
                    "Sesi pengguna tidak ditemukan. Silakan login ulang."
            );
            renderOrders();
            return;
        }

        loading = true;
        if (!silent) {
            progressBar.setVisibility(View.VISIBLE);
            renderOrders();
        }

        new Thread(() -> {
            try {
                String endpoint =
                        BASE_URL
                                + "server/get_user_orders.php?user_id="
                                + Uri.encode(
                                String.valueOf(userId)
                        )
                                + "&username="
                                + Uri.encode(username)
                                + "&_="
                                + System.currentTimeMillis();

                JSONObject response =
                        getJson(endpoint);

                JSONArray array =
                        response.optJSONArray("orders");

                List<JSONObject> fresh =
                        new ArrayList<>();

                if (
                        response.optBoolean(
                                "success",
                                false
                        )
                                && array != null
                ) {
                    for (
                            int i = 0;
                            i < array.length();
                            i++
                    ) {
                        JSONObject order =
                                array.optJSONObject(i);

                        if (order != null) {
                            fresh.add(order);
                        }
                    }
                }

                mainHandler.post(() -> {
                    allOrders.clear();
                    allOrders.addAll(fresh);

                    loading = false;
                    progressBar.setVisibility(View.GONE);

                    renderOrders();
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;
                    progressBar.setVisibility(View.GONE);

                    renderOrders();

                    if (!silent) {
                        toast(
                                "Gagal memuat aktivitas. Periksa koneksi."
                        );
                    }
                });
            }
        }).start();
    }

    private JSONObject getJson(
            String endpoint
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(endpoint)
                                    .openConnection();
            CustomerApiClient.applySecurity(this, connection);

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(
                    TIMEOUT_MS
            );

            connection.setReadTimeout(
                    TIMEOUT_MS
            );

            connection.setUseCaches(false);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache"
            );

            int status =
                    connection.getResponseCode();

            InputStream stream =
                    status >= 200 && status < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (stream == null) {
                throw new IllegalStateException(
                        "Respons server kosong"
                );
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    stream,
                                    "UTF-8"
                            )
                    );

            StringBuilder body =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            reader.close();

            if (status < 200 || status >= 400) {
                throw new IllegalStateException(
                        "HTTP " + status
                );
            }

            return new JSONObject(
                    body.toString()
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String priceChangeDetail(JSONObject order) {
        double now = orderPrice(order);
        double original = order.optDouble("original_price", now);
        double requested = order.optDouble("price_change_requested", 0);
        String status = order.optString("price_change_status", "none");
        String reason = order.optString("price_change_reason", "").trim();
        StringBuilder b = new StringBuilder();
        if (original > 0 && now > 0 && Math.abs(original-now) > 0.5) {
            b.append("\nPerubahan harga: ").append(rupiah(original)).append(" → ").append(rupiah(now));
            if (!reason.isEmpty()) b.append("\nAlasan driver: ").append(reason);
        } else if ("pending".equalsIgnoreCase(status) && requested > 0) {
            b.append("\nPengajuan harga: ").append(rupiah(requested));
            if (!reason.isEmpty()) b.append("\nAlasan driver: ").append(reason);
        }
        return b.toString();
    }

    private void showOrderDetail(JSONObject order) {
        Intent detail = new Intent(this, CustomerOrderDetailActivity.class);
        detail.putExtra("order_json", order.toString());
        startActivity(detail);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openActiveOrder(
            JSONObject order
    ) {
        try {
            Intent trip = new Intent(
                    this,
                    CustomerTripActivity.class
            );

            trip.putExtra(
                    "order_id",
                    first(
                            order.optString(
                                    "order_id"
                            ),
                            order.optString("id"),
                            ""
                    )
            );

            trip.putExtra(
                    "active_order_id",
                    first(
                            order.optString(
                                    "order_id"
                            ),
                            order.optString("id"),
                            ""
                    )
            );

            trip.putExtra(
                    "order_source",
                    first(
                            order.optString("source"),
                            order.optString("_transiva_table"),
                            "orders"
                    )
            );

            trip.putExtra(
                    "pickup_lat",
                    order.optDouble(
                            "pickup_lat",
                            0
                    )
            );

            trip.putExtra(
                    "pickup_lng",
                    order.optDouble(
                            "pickup_lng",
                            0
                    )
            );

            trip.putExtra(
                    "delivery_lat",
                    order.optDouble(
                            "delivery_lat",
                            0
                    )
            );

            trip.putExtra(
                    "delivery_lng",
                    order.optDouble(
                            "delivery_lng",
                            0
                    )
            );

            trip.putExtra("tracking_only", true);

            trip.putExtra(
                    "active_driver_type",
                    isCarOrder(order)
                            ? "car"
                            : "motor"
            );

            startActivity(trip);

        } catch (Exception error) {
            toast(
                    "Trip View customer tidak dapat dibuka."
            );
        }
    }

    private void confirmCancelOrder(
            JSONObject order
    ) {
        String orderId = first(
                order.optString("order_id"),
                order.optString("id"),
                ""
        );

        if (orderId.isEmpty()) {
            toast("ID order tidak ditemukan");
            return;
        }

        new TransivaAlertDialogBuilder(this)
                .setTitle("Batalkan Pesanan")
                .setMessage(
                        "Pesanan dapat dibatalkan selama belum diambil driver. Lanjutkan pembatalan?"
                )
                .setNegativeButton(
                        "Kembali",
                        null
                )
                .setPositiveButton(
                        "Batalkan",
                        (dialog, which) ->
                                cancelOrder(order)
                )
                .show();
    }

    private void cancelOrder(
            JSONObject order
    ) {
        if (loading) {
            return;
        }

        loading = true;
        progressBar.setVisibility(View.VISIBLE);

        CustomerActivityOrderAction.cancel(
                this,
                order,
                userId,
                username,
                new CustomerActivityOrderAction.Callback() {
                    @Override
                    public void onSuccess(
                            String message
                    ) {
                        loading = false;
                        progressBar.setVisibility(
                                View.GONE
                        );

                        toast(message);
                        loadHistory();
                    }

                    @Override
                    public void onError(
                            String message
                    ) {
                        loading = false;
                        progressBar.setVisibility(
                                View.GONE
                        );

                        toast(message);
                        loadHistory();
                    }
                }
        );
    }

    private boolean isCarOrder(
            JSONObject order
    ) {
        String type = serviceType(order);

        return type.contains("car")
                || type.contains("mobil");
    }

    private void openRepeat(
            JSONObject order
    ) {
        try {
            RepeatOrderData data =
                    RepeatOrderData.fromOrder(order);

            Intent intent;

            if (data.isFood()) {
                intent = new Intent(
                        this,
                        RepeatFoodOrderActivity.class
                );
            } else if (
                    data.isCar()
                            || serviceType(order)
                            .contains("ride")
                            || serviceType(order)
                            .contains("bike")
                            || serviceType(order)
                            .contains("motor")
            ) {
                intent = new Intent(
                        this,
                        RepeatRideOrderActivity.class
                );
            } else {
                toast(
                        "Pesan lagi belum tersedia untuk layanan ini."
                );
                return;
            }

            data.putInto(intent);
            startActivity(intent);

        } catch (Exception error) {
            toast(
                    "Data order lama tidak dapat dibuka."
            );
        }
    }

    private View buildBottomNavigation() {
        return CustomerBottomNavigation.build(this, CustomerPageTransition.ACTIVITY);
    }

    private void openTransactions() {
        String[] candidates = {
                "com.transiva.app.CustomerBalanceHistoryActivity",
                "com.transiva.app.BalanceTransactionHistoryActivity",
                "com.transiva.app.CustomerTransactionHistoryActivity",
                "com.transiva.app.CustomerTopUpActivity"
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

        toast(
                "Halaman transaksi sedang disiapkan."
        );
    }

    private String serviceType(
            JSONObject order
    ) {
        return normalized(
                first(
                        order.optString(
                                "order_type"
                        ),
                        order.optString(
                                "service_type"
                        ),
                        order.optString("service"),
                        order.optString(
                                "service_name"
                        ),
                        "ride"
                )
        );
    }

    private String serviceName(
            JSONObject order
    ) {
        String type = serviceType(order);

        if (type.contains("food")) {
            return "TransFood";
        }

        if (
                type.contains("tour")
                        || type.contains("wisata")
        ) {
            return "TransTour";
        }

        if (type.contains("laundry")) {
            return "Laundry";
        }

        if (type.contains("pickup")) {
            return "TransSend";
        }

        if (type.contains("mart") || type.contains("shop")) {
            return "TransShop";
        }

        if (
                type.contains("car")
                        || type.contains("mobil")
        ) {
            return "TransCar";
        }

        return "TransRide";
    }

    private int serviceDrawable(
            JSONObject order
    ) {
        String type = serviceType(order);

        if (type.contains("food")) {
            return drawable("ic_service_food");
        }

        if (
                type.contains("tour")
                        || type.contains("wisata")
        ) {
            return drawable("ic_service_tour");
        }

        if (type.contains("laundry")) {
            return drawable(
                    "ic_service_laundry"
            );
        }

        if (type.contains("pickup")) {
            return drawable(
                    "ic_service_pickup"
            );
        }

        if (type.contains("mart") || type.contains("shop")) {
            return drawable("ic_service_mart");
        }

        if (
                type.contains("car")
                        || type.contains("mobil")
        ) {
            return drawable("ic_service_car");
        }

        return drawable("ic_service_ride");
    }

    private String serviceSoftColor(
            JSONObject order
    ) {
        String type = serviceType(order);

        if (type.contains("food")) {
            return "#FFF4E8";
        }

        if (
                type.contains("tour")
                        || type.contains("wisata")
        ) {
            return "#F2EDFF";
        }

        if (type.contains("laundry")) {
            return "#ECFDF5";
        }

        if (type.contains("pickup")) {
            return "#EEF6FF";
        }

        if (type.contains("mart") || type.contains("shop")) {
            return "#FFF9E8";
        }

        return "#EAF4FF";
    }

    private String normalizedStatus(
            String status
    ) {
        return normalized(status);
    }

    private boolean isActiveStatus(
            String status
    ) {
        return !isCompletedStatus(status)
                && !isCanceledStatus(status);
    }

    /**
     * Server cancel_order.php mengizinkan pembatalan pada:
     * pending dan merchant_accepted.
     *
     * Status pencarian juga ditampilkan sebagai Batalkan karena secara UI
     * driver belum mengambil order. Server tetap menjadi validasi terakhir.
     */
    private boolean canCustomerCancel(
            String status
    ) {
        return status.equals("pending")
                || status.equals("merchant_accepted");
    }

    /** Status resmi driver dari endpoint PHP. */
    private boolean canTrackOrder(
            String status
    ) {
        return status.equals("taken")
                || status.equals("driver_accepted")
                || status.equals("accepted")
                || status.equals("driver_assigned")
                || status.equals("assigned")
                || status.equals("arrived_pickup")
                || status.equals("on_delivery")
                || status.equals("arrived_delivery");
    }

    private boolean isCompletedStatus(
            String status
    ) {
        return status.contains("completed")
                || status.contains("complete")
                || status.contains("finished")
                || status.equals("finish")
                || status.contains("selesai")
                || status.contains("delivered")
                || status.contains("done")
                || status.contains("success");
    }

    private boolean isCanceledStatus(
            String status
    ) {
        return status.equals("merchant_rejected")
                || status.equals("canceled")
                || status.equals("cancelled")
                || status.contains("batal")
                || status.contains("failed")
                || status.contains("expired");
    }

    private String statusLabel(
            String status
    ) {
        return OrderStatusPresentation.label(status, "");
    }

    private String statusTextColor(
            String status
    ) {
        return OrderStatusPresentation.textColor(status);
    }

    private String statusBackground(
            String status
    ) {
        return OrderStatusPresentation.backgroundColor(status);
    }

    private String statusDotColor(
            String status
    ) {
        return OrderStatusPresentation.dotColor(status);
    }

    private String progressDescription(
            JSONObject order
    ) {
        return OrderStatusPresentation.description(order);
    }

    private String orderMainLine(
            JSONObject order
    ) {
        String restaurant = first(
                order.optString(
                        "restaurant_name"
                ),
                order.optString(
                        "merchant_name"
                ),
                ""
        );

        if (!restaurant.isEmpty()) {
            return restaurant;
        }

        String wisata = first(
                order.optString("wisata_name"),
                order.optString("tour_name"),
                ""
        );

        if (!wisata.isEmpty()) {
            return wisata;
        }

        String from = first(
                order.optString(
                        "pickup_address"
                ),
                order.optString(
                        "from_address"
                ),
                ""
        );

        String to = first(
                order.optString(
                        "delivery_address"
                ),
                order.optString(
                        "to_address"
                ),
                order.optString(
                        "destination"
                ),
                ""
        );

        if (!from.isEmpty() && !to.isEmpty()) {
            return shortText(from)
                    + " → "
                    + shortText(to);
        }

        return first(
                from,
                to,
                order.optString("description"),
                ""
        );
    }

    private boolean isPickupOrder(JSONObject order) {
        String type = serviceType(order).toLowerCase(Locale.ROOT);
        String source = order.optString("source", "").toLowerCase(Locale.ROOT);
        String table = order.optString("_transiva_table", "").toLowerCase(Locale.ROOT);

        return type.contains("pickup")
                || type.contains("send")
                || source.contains("pickup_orders")
                || table.contains("pickup_orders");
    }

    private boolean supportsReceiveButton(JSONObject order) {
        // Gunakan identitas layanan yang ditampilkan di kartu. Jangan memakai
        // nama tabel/source karena order TransRide/TransCar juga berasal dari
        // tabel orders dan beberapa respons lama memiliki field source ambigu.
        String name = serviceName(order).toLowerCase(Locale.ROOT).trim();
        String type = serviceType(order).toLowerCase(Locale.ROOT).trim();

        if (name.contains("transsend") || type.contains("pickup") || type.contains("send")) {
            return false;
        }

        return name.contains("transride")
                || name.contains("transcar")
                || name.contains("transfood")
                || type.contains("ride")
                || type.contains("car")
                || type.contains("mobil")
                || type.contains("food");
    }

    private void copyOtpToClipboard(String otp) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                toast("Tidak dapat mengakses clipboard.");
                return;
            }

            clipboard.setPrimaryClip(ClipData.newPlainText("OTP TransSend", otp));
            toast("Kode OTP berhasil disalin.");
        } catch (Exception ignored) {
            toast("Gagal menyalin kode OTP.");
        }
    }

    private String shortText(String value) {
        value = first(value, "");

        if (value.length() <= 30) {
            return value;
        }

        return value.substring(0, 27) + "...";
    }

    private String displayDate(
            JSONObject order
    ) {
        String value = first(
                order.optString("created_at"),
                order.optString("order_date"),
                order.optString("date"),
                order.optString("updated_at"),
                ""
        );

        if (value.isEmpty()) {
            return "Waktu tidak tersedia";
        }

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };

        for (String format : formats) {
            try {
                Date date =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        ).parse(value);

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM yyyy • HH:mm",
                            new Locale("id", "ID")
                    ).format(date);
                }

            } catch (Exception ignored) {
            }
        }

        return value;
    }

    private String trackButtonLabel(
            JSONObject order
    ) {
        String type = serviceType(order);

        if (type.contains("food")) {
            return "Lihat Pesanan";
        }

        if (
                type.contains("laundry")
                        || type.contains("pickup")
        ) {
            return "Lihat Proses";
        }

        return "Lacak";
    }

    private double orderPrice(
            JSONObject order
    ) {
        String[] keys = {
                "total_amount",
                "total_price",
                "price",
                "fare",
                "amount",
                "total"
        };

        for (String key : keys) {
            Object value = order.opt(key);

            if (value == null) {
                continue;
            }

            try {
                if (value instanceof Number) {
                    return ((Number) value)
                            .doubleValue();
                }

                String cleaned =
                        String.valueOf(value)
                                .replaceAll(
                                        "[^0-9.,-]",
                                        ""
                                )
                                .replace(".", "")
                                .replace(",", ".");

                if (!cleaned.isEmpty()) {
                    return Double.parseDouble(
                            cleaned
                    );
                }

            } catch (Exception ignored) {
            }
        }

        return 0;
    }

    private String compactRupiah(
            double amount
    ) {
        if (amount >= 1_000_000) {
            return String.format(
                    new Locale("id", "ID"),
                    "Rp%.1f jt",
                    amount / 1_000_000d
            );
        }

        if (amount >= 1_000) {
            return String.format(
                    new Locale("id", "ID"),
                    "Rp%.0f rb",
                    amount / 1_000d
            );
        }

        return "Rp" + Math.round(amount);
    }

    private String rupiah(double amount) {
        return NumberFormat
                .getCurrencyInstance(
                        new Locale("id", "ID")
                )
                .format(amount);
    }

    private Button primaryButton(
            String label
    ) {
        Button button = new Button(this);

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setBackground(
                gradient(
                        "#086BFF",
                        "#2EA2FF",
                        12
                )
        );

        return button;
    }

    private Button dangerButton(
            String label
    ) {
        Button button = new Button(this);

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setBackground(
                round(
                        "#E34848",
                        12
                )
        );

        return button;
    }

    private Button outlineButton(
            String label
    ) {
        Button button = new Button(this);

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);

        button.setTextColor(
                Color.parseColor("#0B7CFF")
        );

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#B9DBFF",
                        12,
                        1
                )
        );

        return button;
    }

    private TextView text(
            String value,
            int sizeSp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(
                value == null ? "" : value
        );

        view.setTextSize(sizeSp);

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

    private GradientDrawable round(
            String color,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.parseColor(color)
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radiusDp,
            int strokeDp
    ) {
        GradientDrawable drawable =
                round(fill, radiusDp);

        drawable.setStroke(
                dp(strokeDp),
                Color.parseColor(stroke)
        );

        return drawable;
    }

    private GradientDrawable gradient(
            String start,
            String end,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable
                                .Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
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

    private String normalized(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
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

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }
}
