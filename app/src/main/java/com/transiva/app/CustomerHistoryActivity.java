package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        if (!loading && listBox != null) {
            loadHistory();
        }
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
        addServiceChip("Laundry", "laundry");
        addServiceChip("Pickup", "pickup");
        addServiceChip("TransTour", "tour");
        addServiceChip("TransMart", "mart");
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
            if (canCustomerCancel(status)) {
                Button cancel =
                        dangerButton("Batalkan");

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

            } else if (canTrackOrder(status)) {
                Button track =
                        primaryButton("Lacak");

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
            }

        } else {
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
        progressBar.setVisibility(View.VISIBLE);
        renderOrders();

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

                    toast(
                            "Gagal memuat aktivitas. Periksa koneksi."
                    );
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

    private void showOrderDetail(
            JSONObject order
    ) {
        String detail =
                "Order ID: "
                        + first(
                        order.optString(
                                "order_id"
                        ),
                        order.optString("id"),
                        "-"
                )
                        + "\nLayanan: "
                        + serviceName(order)
                        + "\nStatus: "
                        + statusLabel(
                        normalizedStatus(
                                order.optString(
                                        "status"
                                )
                        )
                )
                        + "\nPickup: "
                        + first(
                        order.optString(
                                "pickup_address"
                        ),
                        order.optString(
                                "from_address"
                        ),
                        order.optString(
                                "restaurant_name"
                        ),
                        "-"
                )
                        + "\nTujuan: "
                        + first(
                        order.optString(
                                "delivery_address"
                        ),
                        order.optString(
                                "to_address"
                        ),
                        order.optString(
                                "destination"
                        ),
                        "-"
                )
                        + "\nDriver: "
                        + first(
                        order.optString("driver"),
                        order.optString(
                                "driver_username"
                        ),
                        "Belum ada"
                )
                        + "\nTanggal: "
                        + displayDate(order)
                        + "\nTotal: "
                        + rupiah(orderPrice(order));

        new AlertDialog.Builder(this)
                .setTitle("Detail Aktivitas")
                .setMessage(detail)
                .setPositiveButton(
                        "Tutup",
                        null
                )
                .show();
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

        new AlertDialog.Builder(this)
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
            return "Pickup";
        }

        if (type.contains("mart")) {
            return "TransMart";
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

        if (type.contains("mart")) {
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

        if (type.contains("mart")) {
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
