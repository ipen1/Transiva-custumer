package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CustomerBalanceHistoryActivity
        extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/server/";

    private static final String SUMMARY_URL =
            BASE_URL + "customer_wallet_summary.php";

    private static final String TRANSFER_URL =
            BASE_URL + "customer_wallet_transfer.php";

    private static final String QUOTE_TRANSFER_URL =
            BASE_URL + "customer_wallet_quote.php";

    private static final String LOOKUP_USER_URL =
            BASE_URL + "customer_wallet_lookup_user.php";

    private static final String WITHDRAW_URL =
            BASE_URL + "customer_wallet_withdraw.php";

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final List<JSONObject> transactions =
            new ArrayList<>();

    private LinearLayout content;
    private LinearLayout mutationBox;
    private LinearLayout filterRow;
    private ProgressBar loading;

    private TextView balanceText;
    private TextView incomeText;
    private TextView expenseText;
    private TextView pendingText;

    private String username = "";
    private int userId;
    private String filter = "all";
    private boolean loadingData;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        readSession();
        setContentView(buildScreen());
        CustomerAppSettings.apply(this);
        loadWallet();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);

        if (
                content != null
                        && !loadingData
        ) {
            loadWallet();
        }
    }

    private void readSession() {
        try {
            SessionManager session =
                    new SessionManager(this);

            username = first(
                    session.getUsername(),
                    session.getName(),
                    ""
            );

            userId = parseInt(
                    first(
                            session.getId(),
                            session.getUserId(),
                            "0"
                    )
            );

        } catch (Exception ignored) {
            username = "";
            userId = 0;
        }
    }

    private View buildScreen() {
        FrameLayout page =
                new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F6F9FE")
        );

        LinearLayout shell =
                new LinearLayout(this);

        shell.setOrientation(
                LinearLayout.VERTICAL
        );

        page.addView(
                shell,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        shell.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(24)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        buildHeader();
        buildBalanceCard();
        buildActionGrid();
        buildSummary();
        buildMutationSection();

        shell.addView(
                buildBottomNavigation(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(66)
                )
        );

        loading =
                new ProgressBar(this);

        loading.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(
                        dp(44),
                        dp(44)
                );

        progressLp.gravity =
                Gravity.CENTER;

        page.addView(
                loading,
                progressLp
        );

        return page;
    }

    private void buildHeader() {
        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout title =
                new LinearLayout(this);

        title.setOrientation(
                LinearLayout.VERTICAL
        );

        title.addView(
                text(
                        "Transaksi",
                        24,
                        "#0B3A78",
                        true
                )
        );

        title.addView(
                text(
                        "Kelola Transiva Pay dengan aman",
                        11,
                        "#718096",
                        false
                )
        );

        row.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView refresh =
                text(
                        "↻",
                        25,
                        "#0B7CFF",
                        true
                );

        refresh.setGravity(
                Gravity.CENTER
        );

        refresh.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#DCE8F6",
                        16,
                        1
                )
        );

        refresh.setOnClickListener(
                view -> loadWallet()
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

    private void buildBalanceCard() {
        FrameLayout frame =
                new FrameLayout(this);

        frame.setBackground(
                gradient(
                        "#075EF4",
                        "#22A4FF",
                        22
                )
        );

        frame.setElevation(dp(3));

        LinearLayout.LayoutParams frameLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(180)
                );

        frameLp.setMargins(
                0,
                dp(14),
                0,
                dp(14)
        );

        content.addView(frame, frameLp);

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(18),
                dp(17),
                dp(18),
                dp(14)
        );

        frame.addView(
                card,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        card.addView(
                text(
                        "Transiva Pay",
                        16,
                        "#FFFFFF",
                        true
                )
        );

        card.addView(
                text(
                        "Saldo tersedia",
                        11,
                        "#EAF4FF",
                        false
                )
        );

        balanceText =
                text(
                        "Memuat...",
                        29,
                        "#FFFFFF",
                        true
                );

        balanceText.setSingleLine(true);

        LinearLayout.LayoutParams balanceLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        balanceLp.setMargins(
                0,
                dp(3),
                0,
                dp(13)
        );

        card.addView(
                balanceText,
                balanceLp
        );

        LinearLayout security =
                new LinearLayout(this);

        security.setGravity(
                Gravity.CENTER_VERTICAL
        );

        security.setPadding(
                dp(10),
                dp(7),
                dp(10),
                dp(7)
        );

        security.setBackground(
                round("#FFD84D", 12)
        );

        TextView secureIcon =
                text(
                        "✓",
                        12,
                        "#5A3B00",
                        true
                );

        security.addView(secureIcon);

        TextView secureText =
                text(
                        "  Transaksi terlindungi dan tercatat",
                        10,
                        "#5A3B00",
                        true
                );

        security.addView(
                secureText,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        card.addView(
                security,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );
    }

    private void buildActionGrid() {
        LinearLayout card =
                whiteCard();

        card.setPadding(
                dp(12),
                dp(14),
                dp(12),
                dp(14)
        );

        card.addView(
                text(
                        "Aksi Cepat",
                        15,
                        "#0B3A78",
                        true
                )
        );

        LinearLayout actions =
                new LinearLayout(this);

        actions.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams actionsLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        actionsLp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        card.addView(
                actions,
                actionsLp
        );

        actions.addView(
                actionItem(
                        "＋",
                        "Deposit",
                        "#EAF4FF",
                        this::openDeposit
                ),
                actionLp(false)
        );

        actions.addView(
                actionItem(
                        "⇄",
                        "Kirim Dana",
                        "#ECFDF5",
                        this::showTransferDialog
                ),
                actionLp(true)
        );

        actions.addView(
                actionItem(
                        "↓",
                        "Withdraw",
                        "#FFF4E8",
                        this::showWithdrawDialog
                ),
                actionLp(true)
        );

        /*
         * Wajib menambahkan card Aksi Cepat ke content.
         * Sebelumnya card hanya dibuat dan diisi, tetapi tidak pernah
         * dimasukkan ke layout halaman sehingga seluruh tombol tidak tampil.
         */
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
    }

    private LinearLayout.LayoutParams actionLp(
            boolean margin
    ) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(92),
                        1
                );

        if (margin) {
            lp.setMargins(
                    dp(8),
                    0,
                    0,
                    0
            );
        }

        return lp;
    }

    private View actionItem(
            String symbol,
            String label,
            String softColor,
            Runnable action
    ) {
        LinearLayout item =
                new LinearLayout(this);

        item.setOrientation(
                LinearLayout.VERTICAL
        );

        item.setGravity(
                Gravity.CENTER
        );

        item.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E1EAF5",
                        16,
                        1
                )
        );

        TextView icon =
                text(
                        symbol,
                        23,
                        "#0B7CFF",
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        icon.setBackground(
                round(
                        softColor,
                        13
                )
        );

        item.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                )
        );

        TextView caption =
                text(
                        label,
                        10,
                        "#0B3A78",
                        true
                );

        caption.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams captionLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        captionLp.setMargins(
                0,
                dp(5),
                0,
                0
        );

        item.addView(
                caption,
                captionLp
        );

        item.setOnClickListener(
                view -> action.run()
        );

        return item;
    }

    private void buildSummary() {
        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        rowLp.setMargins(
                0,
                dp(12),
                0,
                dp(14)
        );

        content.addView(row, rowLp);

        incomeText =
                summaryCard(
                        row,
                        "Pemasukan",
                        "Rp0",
                        "#0E9F4B",
                        false
                );

        expenseText =
                summaryCard(
                        row,
                        "Pengeluaran",
                        "Rp0",
                        "#D9485F",
                        true
                );

        pendingText =
                summaryCard(
                        row,
                        "Diproses",
                        "Rp0",
                        "#D97706",
                        true
                );
    }

    private TextView summaryCard(
            LinearLayout parent,
            String label,
            String value,
            String color,
            boolean margin
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(10),
                dp(11),
                dp(10),
                dp(11)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E3ECF7",
                        15,
                        1
                )
        );

        TextView labelView =
                text(
                        label,
                        9,
                        "#718096",
                        false
                );

        card.addView(labelView);

        TextView valueView =
                text(
                        value,
                        13,
                        color,
                        true
                );

        valueView.setSingleLine(true);
        valueView.setEllipsize(
                TextUtils.TruncateAt.END
        );

        LinearLayout.LayoutParams valueLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        valueLp.setMargins(
                0,
                dp(3),
                0,
                0
        );

        card.addView(
                valueView,
                valueLp
        );

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(72),
                        1
                );

        if (margin) {
            cardLp.setMargins(
                    dp(7),
                    0,
                    0,
                    0
            );
        }

        parent.addView(
                card,
                cardLp
        );

        return valueView;
    }

    private void buildMutationSection() {
        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.addView(
                text(
                        "Mutasi Saldo",
                        16,
                        "#0B3A78",
                        true
                ),
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView period =
                text(
                        "30 hari terakhir",
                        10,
                        "#718096",
                        false
                );

        header.addView(period);
        content.addView(header);

        HorizontalScrollView scroll =
                new HorizontalScrollView(this);

        scroll.setHorizontalScrollBarEnabled(
                false
        );

        filterRow =
                new LinearLayout(this);

        filterRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        scroll.addView(
                filterRow,
                new HorizontalScrollView.LayoutParams(
                        -2,
                        -2
                )
        );

        LinearLayout.LayoutParams filterLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(46)
                );

        filterLp.setMargins(
                0,
                dp(9),
                0,
                dp(8)
        );

        content.addView(
                scroll,
                filterLp
        );

        rebuildFilters();

        mutationBox =
                new LinearLayout(this);

        mutationBox.setOrientation(
                LinearLayout.VERTICAL
        );

        content.addView(
                mutationBox,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        renderTransactions();
    }

    private void rebuildFilters() {
        filterRow.removeAllViews();

        addFilter("all", "Semua");
        addFilter("in", "Pemasukan");
        addFilter("out", "Pengeluaran");
        addFilter("pending", "Diproses");
    }

    private void addFilter(
            String value,
            String label
    ) {
        TextView chip =
                text(
                        label,
                        10,
                        filter.equals(value)
                                ? "#FFFFFF"
                                : "#64748B",
                        filter.equals(value)
                );

        chip.setGravity(
                Gravity.CENTER
        );

        chip.setPadding(
                dp(16),
                0,
                dp(16),
                0
        );

        chip.setBackground(
                roundStroke(
                        filter.equals(value)
                                ? "#0B7CFF"
                                : "#FFFFFF",
                        filter.equals(value)
                                ? "#0B7CFF"
                                : "#DCE7F4",
                        18,
                        1
                )
        );

        chip.setOnClickListener(
                view -> {
                    filter = value;
                    rebuildFilters();
                    renderTransactions();
                }
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(38)
                );

        if (filterRow.getChildCount() > 0) {
            lp.setMargins(
                    dp(7),
                    0,
                    0,
                    0
            );
        }

        filterRow.addView(
                chip,
                lp
        );
    }

    private void loadWallet() {
        if (loadingData) {
            return;
        }

        if (
                userId <= 0
                        && username.isEmpty()
        ) {
            toast("Sesi pengguna tidak ditemukan");
            return;
        }

        loadingData = true;
        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject request =
                        new JSONObject();

                request.put(
                        "user_id",
                        userId
                );

                request.put(
                        "username",
                        username
                );

                JSONObject response =
                        postJson(
                                SUMMARY_URL,
                                request
                        );

                if (
                        !response.optBoolean(
                                "success",
                                false
                        )
                ) {
                    throw new IllegalStateException(
                            response.optString(
                                    "message",
                                    "Gagal memuat transaksi"
                            )
                    );
                }

                int balance =
                        response.optInt(
                                "balance",
                                0
                        );

                int income =
                        response.optInt(
                                "income",
                                0
                        );

                int expense =
                        response.optInt(
                                "expense",
                                0
                        );

                int pending =
                        response.optInt(
                                "pending",
                                0
                        );

                JSONArray array =
                        response.optJSONArray(
                                "transactions"
                        );

                List<JSONObject> fresh =
                        new ArrayList<>();

                if (array != null) {
                    for (
                            int i = 0;
                            i < array.length();
                            i++
                    ) {
                        JSONObject item =
                                array.optJSONObject(i);

                        if (item != null) {
                            fresh.add(item);
                        }
                    }
                }

                mainHandler.post(() -> {
                    loadingData = false;
                    setLoading(false);

                    balanceText.setText(
                            rupiah(balance)
                    );

                    incomeText.setText(
                            "+" + rupiah(income)
                    );

                    expenseText.setText(
                            "-" + rupiah(expense)
                    );

                    pendingText.setText(
                            rupiah(pending)
                    );

                    transactions.clear();
                    transactions.addAll(fresh);
                    renderTransactions();
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    loadingData = false;
                    setLoading(false);

                    toast(
                            first(
                                    error.getMessage(),
                                    "Gagal memuat data transaksi"
                            )
                    );
                });
            }
        }).start();
    }

    private void renderTransactions() {
        if (mutationBox == null) {
            return;
        }

        mutationBox.removeAllViews();

        List<JSONObject> visible =
                new ArrayList<>();

        for (JSONObject item : transactions) {
            String direction =
                    item.optString(
                            "direction",
                            ""
                    );

            String status =
                    item.optString(
                            "status",
                            ""
                    );

            boolean matches =
                    filter.equals("all")
                            || (
                            filter.equals("in")
                                    && direction.equals("in")
                    )
                            || (
                            filter.equals("out")
                                    && direction.equals("out")
                    )
                            || (
                            filter.equals("pending")
                                    && status.equals("pending")
                    );

            if (matches) {
                visible.add(item);
            }
        }

        if (visible.isEmpty()) {
            LinearLayout empty =
                    whiteCard();

            empty.setGravity(Gravity.CENTER);
            empty.setPadding(
                    dp(18),
                    dp(24),
                    dp(18),
                    dp(24)
            );

            TextView message =
                    text(
                            "Belum ada mutasi pada kategori ini",
                            11,
                            "#718096",
                            false
                    );

            message.setGravity(
                    Gravity.CENTER
            );

            empty.addView(message);
            mutationBox.addView(empty);
            return;
        }

        for (JSONObject item : visible) {
            mutationBox.addView(
                    transactionCard(item)
            );
        }
    }

    private View transactionCard(
            JSONObject item
    ) {
        String direction =
                item.optString(
                        "direction",
                        "out"
                );

        String status =
                item.optString(
                        "status",
                        "success"
                );

        boolean incoming =
                direction.equals("in");

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E1EAF5",
                        16,
                        1
                )
        );

        TextView icon =
                text(
                        incoming
                                ? "↓"
                                : "↑",
                        20,
                        incoming
                                ? "#0E9F4B"
                                : "#D9485F",
                        true
                );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                round(
                        incoming
                                ? "#ECFDF5"
                                : "#FFF0F2",
                        13
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(44),
                        dp(44)
                )
        );

        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.VERTICAL
        );

        info.setPadding(
                dp(10),
                0,
                dp(8),
                0
        );

        TextView title =
                text(
                        item.optString(
                                "title",
                                transactionTitle(
                                        item.optString(
                                                "type",
                                                ""
                                        )
                                )
                        ),
                        12,
                        "#0B3A78",
                        true
                );

        title.setSingleLine(true);
        title.setEllipsize(
                TextUtils.TruncateAt.END
        );

        info.addView(title);

        TextView description =
                text(
                        item.optString(
                                "description",
                                ""
                        ),
                        9,
                        "#718096",
                        false
                );

        description.setMaxLines(2);
        description.setEllipsize(
                TextUtils.TruncateAt.END
        );

        info.addView(description);

        info.addView(
                text(
                        formatDate(
                                item.optString(
                                        "created_at",
                                        ""
                                )
                        ),
                        9,
                        "#94A3B8",
                        false
                )
        );

        card.addView(
                info,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        LinearLayout end =
                new LinearLayout(this);

        end.setOrientation(
                LinearLayout.VERTICAL
        );

        end.setGravity(
                Gravity.END
        );

        TextView amount =
                text(
                        (
                                incoming
                                        ? "+"
                                        : "-"
                        )
                                + rupiah(
                                item.optInt(
                                        "amount",
                                        0
                                )
                        ),
                        12,
                        incoming
                                ? "#0E9F4B"
                                : "#D9485F",
                        true
                );

        amount.setGravity(Gravity.END);
        end.addView(amount);

        TextView statusView =
                text(
                        statusLabel(status),
                        9,
                        statusColor(status),
                        true
                );

        statusView.setGravity(
                Gravity.CENTER
        );

        statusView.setPadding(
                dp(7),
                dp(3),
                dp(7),
                dp(3)
        );

        statusView.setBackground(
                round(
                        statusBackground(status),
                        10
                )
        );

        LinearLayout.LayoutParams statusLp =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        statusLp.setMargins(
                0,
                dp(5),
                0,
                0
        );

        end.addView(
                statusView,
                statusLp
        );

        card.addView(
                end,
                new LinearLayout.LayoutParams(
                        dp(112),
                        -2
                )
        );

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardLp.setMargins(
                0,
                0,
                0,
                dp(8)
        );

        card.setLayoutParams(cardLp);

        return card;
    }

    private void openDeposit() {
        startActivity(
                new Intent(
                        this,
                        CustomerTopUpActivity.class
                )
        );
    }

    private void showTransferDialog() {
        LinearLayout form =
                dialogForm();

        EditText recipient =
                input(
                        "Username penerima",
                        InputType.TYPE_CLASS_TEXT
                );

        EditText amount =
                input(
                        "Minimal Rp10.000",
                        InputType.TYPE_CLASS_NUMBER
                );

        installRupiahFormatting(amount);

        EditText note =
                input(
                        "Catatan (opsional)",
                        InputType.TYPE_CLASS_TEXT
                );

        TextView quotaInfo =
                text(
                        "5 transfer gratis setiap bulan. Setelah kuota habis, biaya Rp500 per transfer.",
                        10,
                        "#64748B",
                        false
                );

        quotaInfo.setPadding(
                dp(4),
                dp(7),
                dp(4),
                dp(4)
        );

        form.addView(recipient);
        form.addView(spacer());
        form.addView(amount);
        form.addView(quotaInfo);
        form.addView(spacer());
        form.addView(note);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Kirim Dana")
                        .setMessage(
                                "Masukkan username customer dan nominal yang akan dikirim."
                        )
                        .setView(form)
                        .setNegativeButton(
                                "Batal",
                                null
                        )
                        .setPositiveButton(
                                "Lihat Detail",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored -> dialog
                        .getButton(
                                AlertDialog.BUTTON_POSITIVE
                        )
                        .setOnClickListener(
                                view -> {
                                    String to =
                                            recipient
                                                    .getText()
                                                    .toString()
                                                    .trim();

                                    int value =
                                            parseInt(
                                                    amount
                                                            .getText()
                                                            .toString()
                                            );

                                    if (to.isEmpty()) {
                                        recipient.setError(
                                                "Username wajib diisi"
                                        );
                                        return;
                                    }

                                    if (
                                            !username.isEmpty()
                                                    && to.equalsIgnoreCase(
                                                    username
                                            )
                                    ) {
                                        recipient.setError(
                                                "Tidak dapat kirim ke akun sendiri"
                                        );
                                        return;
                                    }

                                    if (value < 10000) {
                                        amount.setError(
                                                "Minimal transfer Rp10.000"
                                        );
                                        return;
                                    }

                                    dialog.dismiss();

                                    requestTransferQuote(
                                            to,
                                            value,
                                            note
                                                    .getText()
                                                    .toString()
                                                    .trim()
                                    );
                                }
                        )
        );

        dialog.show();
    }

    private void requestTransferQuote(
            String recipient,
            int amount,
            String note
    ) {
        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject request =
                        new JSONObject();

                request.put(
                        "user_id",
                        userId
                );

                request.put(
                        "username",
                        username
                );

                request.put(
                        "recipient",
                        recipient
                );

                request.put(
                        "amount",
                        amount
                );

                request.put(
                        "request_id",
                        UUID.randomUUID()
                                .toString()
                );

                JSONObject response =
                        postJson(
                                QUOTE_TRANSFER_URL,
                                request
                        );

                mainHandler.post(() -> {
                    setLoading(false);

                    if (
                            !response.optBoolean(
                                    "success",
                                    false
                            )
                    ) {
                        showInfo(
                                "Kirim Dana",
                                response.optString(
                                        "message",
                                        "Detail transfer tidak dapat dibuat."
                                )
                        );

                        return;
                    }

                    showTransferConfirmation(
                            response,
                            recipient,
                            amount,
                            note
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);

                    showInfo(
                            "Gagal Membuat Detail",
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi internet."
                            )
                    );
                });
            }
        }).start();
    }

    private void showTransferConfirmation(
            JSONObject quote,
            String recipient,
            int amount,
            String note
    ) {
        String receiverName =
                first(
                        quote.optString(
                                "receiver_name"
                        ),
                        quote.optString(
                                "receiver_username"
                        ),
                        recipient
                );

        int fee =
                quote.optInt(
                        "fee",
                        0
                );

        int totalDebit =
                quote.optInt(
                        "total_debit",
                        amount + fee
                );

        int remainingBefore =
                quote.optInt(
                        "free_remaining_before",
                        0
                );

        int remainingAfter =
                quote.optInt(
                        "free_remaining_after",
                        0
                );

        boolean free =
                quote.optBoolean(
                        "free_transfer",
                        fee == 0
                );

        String quotaLine =
                free
                        ? "Kuota gratis digunakan: 1\n"
                        + "Sisa setelah transfer: "
                        + remainingAfter
                        + " kali"
                        : "Kuota gratis bulan ini telah habis";

        String detail =
                "Penerima\n"
                        + receiverName
                        + " ("
                        + quote.optString(
                        "receiver_username",
                        recipient
                )
                        + ")\n\n"
                        + "Nominal diterima     "
                        + rupiah(amount)
                        + "\n"
                        + "Biaya layanan        "
                        + (
                        fee == 0
                                ? "Gratis"
                                : rupiah(fee)
                )
                        + "\n"
                        + "Total saldo dipotong "
                        + rupiah(totalDebit)
                        + "\n\n"
                        + quotaLine
                        + (
                        note.isEmpty()
                                ? ""
                                : "\n\nCatatan: "
                                + note
                );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Detail Pengiriman"
                )
                .setMessage(detail)
                .setNegativeButton(
                        "Periksa Lagi",
                        null
                )
                .setPositiveButton(
                        "Kirim Sekarang",
                        (confirm, which) ->
                                submitTransfer(
                                        recipient,
                                        amount,
                                        note,
                                        quote.optString(
                                                "quote_token",
                                                ""
                                        ),
                                        quote.optString(
                                                "request_id",
                                                UUID.randomUUID()
                                                        .toString()
                                        )
                                )
                )
                .show();
    }

    private void submitTransfer(
            String recipient,
            int amount,
            String note,
            String quoteToken,
            String requestId
    ) {
        if (
                quoteToken == null
                        || quoteToken.trim()
                        .isEmpty()
        ) {
            showInfo(
                    "Kirim Dana",
                    "Detail transfer tidak valid. Silakan ulangi."
            );

            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject request =
                        new JSONObject();

                request.put(
                        "user_id",
                        userId
                );

                request.put(
                        "username",
                        username
                );

                request.put(
                        "recipient",
                        recipient
                );

                request.put(
                        "amount",
                        amount
                );

                request.put(
                        "note",
                        note
                );

                request.put(
                        "quote_token",
                        quoteToken
                );

                request.put(
                        "request_id",
                        requestId
                );

                JSONObject response =
                        postJson(
                                TRANSFER_URL,
                                request
                        );

                mainHandler.post(() -> {
                    setLoading(false);

                    if (
                            response.optBoolean(
                                    "success",
                                    false
                            )
                    ) {
                        int fee =
                                response.optInt(
                                        "fee",
                                        0
                                );

                        int total =
                                response.optInt(
                                        "total_debit",
                                        amount + fee
                                );

                        showInfo(
                                "Kirim Dana Berhasil",
                                response.optString(
                                        "message",
                                        "Dana berhasil dikirim."
                                )
                                        + "\n\nBiaya: "
                                        + (
                                        fee == 0
                                                ? "Gratis"
                                                : rupiah(fee)
                                )
                                        + "\nTotal dipotong: "
                                        + rupiah(total)
                                        + "\nSisa kuota gratis: "
                                        + response.optInt(
                                        "free_remaining",
                                        0
                                )
                                        + " kali"
                        );

                        loadWallet();

                    } else {
                        showInfo(
                                "Kirim Dana Gagal",
                                response.optString(
                                        "message",
                                        "Transfer tidak dapat diproses."
                                )
                        );
                    }
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);

                    showInfo(
                            "Kirim Dana Gagal",
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi internet."
                            )
                    );
                });
            }
        }).start();
    }

    private void installRupiahFormatting(
            EditText input
    ) {
        input.addTextChangedListener(
                new TextWatcher() {

                    private boolean updating;

                    @Override
                    public void beforeTextChanged(
                            CharSequence value,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence value,
                            int start,
                            int before,
                            int count
                    ) {
                    }

                    @Override
                    public void afterTextChanged(
                            Editable editable
                    ) {
                        if (updating) {
                            return;
                        }

                        updating = true;

                        String digits =
                                editable.toString()
                                        .replaceAll(
                                                "[^0-9]",
                                                ""
                                        );

                        if (digits.isEmpty()) {
                            input.setText("");
                            updating = false;
                            return;
                        }

                        try {
                            long number =
                                    Long.parseLong(
                                            digits
                                    );

                            String formatted =
                                    NumberFormat
                                            .getNumberInstance(
                                                    new Locale(
                                                            "id",
                                                            "ID"
                                                    )
                                            )
                                            .format(number);

                            input.setText(formatted);

                            input.setSelection(
                                    formatted.length()
                            );

                        } catch (Exception ignored) {
                        }

                        updating = false;
                    }
                }
        );
    }

    private void showWithdrawDialog() {
        LinearLayout form =
                dialogForm();

        EditText amount =
                input(
                        "Nominal withdraw",
                        InputType.TYPE_CLASS_NUMBER
                );

        EditText bank =
                input(
                        "Bank / e-wallet",
                        InputType.TYPE_CLASS_TEXT
                );

        EditText accountName =
                input(
                        "Nama pemilik rekening",
                        InputType.TYPE_CLASS_TEXT
                );

        EditText accountNumber =
                input(
                        "Nomor rekening / e-wallet",
                        InputType.TYPE_CLASS_TEXT
                );

        EditText note =
                input(
                        "Catatan (opsional)",
                        InputType.TYPE_CLASS_TEXT
                );

        form.addView(amount);
        form.addView(spacer());
        form.addView(bank);
        form.addView(spacer());
        form.addView(accountName);
        form.addView(spacer());
        form.addView(accountNumber);
        form.addView(spacer());
        form.addView(note);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Withdraw Dana")
                        .setMessage(
                                "Saldo akan ditahan sampai admin menyetujui pencairan."
                        )
                        .setView(form)
                        .setNegativeButton(
                                "Batal",
                                null
                        )
                        .setPositiveButton(
                                "Ajukan",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored -> dialog
                        .getButton(
                                AlertDialog.BUTTON_POSITIVE
                        )
                        .setOnClickListener(
                                view -> {
                                    int value =
                                            parseInt(
                                                    amount
                                                            .getText()
                                                            .toString()
                                            );

                                    if (value < 10000) {
                                        amount.setError(
                                                "Minimal withdraw Rp10.000"
                                        );
                                        return;
                                    }

                                    if (
                                            bank.getText()
                                                    .toString()
                                                    .trim()
                                                    .isEmpty()
                                    ) {
                                        bank.setError(
                                                "Bank/e-wallet wajib diisi"
                                        );
                                        return;
                                    }

                                    if (
                                            accountName.getText()
                                                    .toString()
                                                    .trim()
                                                    .isEmpty()
                                    ) {
                                        accountName.setError(
                                                "Nama pemilik wajib diisi"
                                        );
                                        return;
                                    }

                                    if (
                                            accountNumber.getText()
                                                    .toString()
                                                    .trim()
                                                    .isEmpty()
                                    ) {
                                        accountNumber.setError(
                                                "Nomor rekening wajib diisi"
                                        );
                                        return;
                                    }

                                    dialog.dismiss();

                                    submitWithdraw(
                                            value,
                                            bank.getText()
                                                    .toString()
                                                    .trim(),
                                            accountName
                                                    .getText()
                                                    .toString()
                                                    .trim(),
                                            accountNumber
                                                    .getText()
                                                    .toString()
                                                    .trim(),
                                            note.getText()
                                                    .toString()
                                                    .trim()
                                    );
                                }
                        )
        );

        dialog.show();
    }

    private void submitWithdraw(
            int amount,
            String bank,
            String accountName,
            String accountNumber,
            String note
    ) {
        setLoading(true);

        new Thread(() -> {
            try {
                JSONObject request =
                        new JSONObject();

                request.put(
                        "user_id",
                        userId
                );

                request.put(
                        "username",
                        username
                );

                request.put(
                        "amount",
                        amount
                );

                request.put(
                        "bank_name",
                        bank
                );

                request.put(
                        "account_name",
                        accountName
                );

                request.put(
                        "account_number",
                        accountNumber
                );

                request.put(
                        "note",
                        note
                );

                JSONObject response =
                        postJson(
                                WITHDRAW_URL,
                                request
                        );

                mainHandler.post(() -> {
                    setLoading(false);

                    if (
                            response.optBoolean(
                                    "success",
                                    false
                            )
                    ) {
                        showInfo(
                                "Withdraw Diajukan",
                                response.optString(
                                        "message",
                                        "Pengajuan withdraw berhasil dibuat."
                                )
                        );

                        loadWallet();

                    } else {
                        showInfo(
                                "Withdraw Gagal",
                                response.optString(
                                        "message",
                                        "Withdraw tidak dapat diproses."
                                )
                        );
                    }
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    setLoading(false);

                    showInfo(
                            "Withdraw Gagal",
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi internet."
                            )
                    );
                });
            }
        }).start();
    }

    private JSONObject postJson(
            String endpoint,
            JSONObject request
    ) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(endpoint)
                                .openConnection();

        connection.setConnectTimeout(25000);
        connection.setReadTimeout(25000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        byte[] body =
                request.toString()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        connection.setFixedLengthStreamingMode(
                body.length
        );

        try (
                OutputStream output =
                        connection.getOutputStream()
        ) {
            output.write(body);
        }

        int code =
                connection.getResponseCode();

        InputStream input =
                code >= 200 && code < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        StringBuilder response =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        input,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {
                response.append(line);
            }
        }

        connection.disconnect();

        if (response.length() == 0) {
            throw new IllegalStateException(
                    "Respons server kosong"
            );
        }

        return new JSONObject(
                response.toString()
        );
    }

    private View buildBottomNavigation() {
        return CustomerBottomNavigation.build(this, CustomerPageTransition.WALLET);
    }

    private LinearLayout whiteCard() {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E1EAF5",
                        18,
                        1
                )
        );

        card.setElevation(dp(1));

        return card;
    }

    private LinearLayout dialogForm() {
        LinearLayout form =
                new LinearLayout(this);

        form.setOrientation(
                LinearLayout.VERTICAL
        );

        form.setPadding(
                dp(20),
                dp(8),
                dp(20),
                0
        );

        return form;
    }

    private EditText input(
            String hint,
            int type
    ) {
        EditText input =
                new EditText(this);

        input.setHint(hint);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setInputType(type);
        input.setImeOptions(
                EditorInfo.IME_ACTION_NEXT
        );

        input.setPadding(
                dp(14),
                0,
                dp(14),
                0
        );

        input.setBackground(
                roundStroke(
                        "#F8FBFF",
                        "#D7E6F8",
                        14,
                        1
                )
        );

        input.setLayoutParams(
                new LinearLayout.LayoutParams(
                        -1,
                        dp(52)
                )
        );

        return input;
    }

    private View spacer() {
        View spacer = new View(this);

        spacer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        -1,
                        dp(8)
                )
        );

        return spacer;
    }

    private String transactionTitle(
            String type
    ) {
        type =
                type == null
                        ? ""
                        : type.toLowerCase(
                                Locale.US
                        );

        if (type.contains("deposit")) {
            return "Deposit Saldo";
        }

        if (type.contains("transfer_in")) {
            return "Transfer Masuk";
        }

        if (type.contains("transfer_out")) {
            return "Transfer Keluar";
        }

        if (type.contains("withdraw")) {
            return "Withdraw Dana";
        }

        if (type.contains("refund")) {
            return "Refund Pesanan";
        }

        if (
                type.contains("payment")
                        || type.contains("order")
        ) {
            return "Pembayaran Pesanan";
        }

        return "Mutasi Saldo";
    }

    private String statusLabel(
            String status
    ) {
        status =
                status == null
                        ? ""
                        : status.toLowerCase(
                                Locale.US
                        );

        if (
                status.equals("pending")
                        || status.equals("processing")
        ) {
            return "Diproses";
        }

        if (
                status.equals("failed")
                        || status.equals("rejected")
                        || status.equals("cancelled")
        ) {
            return "Gagal";
        }

        return "Berhasil";
    }

    private String statusColor(
            String status
    ) {
        String label =
                statusLabel(status);

        if (label.equals("Diproses")) {
            return "#D97706";
        }

        if (label.equals("Gagal")) {
            return "#D9485F";
        }

        return "#0E9F4B";
    }

    private String statusBackground(
            String status
    ) {
        String label =
                statusLabel(status);

        if (label.equals("Diproses")) {
            return "#FFF7E6";
        }

        if (label.equals("Gagal")) {
            return "#FFF0F2";
        }

        return "#ECFDF5";
    }

    private String formatDate(
            String value
    ) {
        if (
                value == null
                        || value.trim().isEmpty()
        ) {
            return "";
        }

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String format : formats) {
            try {
                Date date =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        ).parse(
                                value.trim()
                        );

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM yyyy • HH:mm",
                            new Locale(
                                    "id",
                                    "ID"
                            )
                    ).format(date);
                }

            } catch (Exception ignored) {
            }
        }

        return value;
    }

    private String rupiah(
            int amount
    ) {
        NumberFormat format =
                NumberFormat.getCurrencyInstance(
                        new Locale(
                                "id",
                                "ID"
                        )
                );

        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);

        return format.format(amount);
    }

    private void setLoading(
            boolean show
    ) {
        if (loading != null) {
            loading.setVisibility(
                    show
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    private void showInfo(
            String title,
            String message
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view =
                new TextView(this);

        view.setText(
                value == null
                        ? ""
                        : value
        );

        view.setTextSize(size);
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
            int radius
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                Color.parseColor(color)
        );

        drawable.setCornerRadius(
                dp(radius)
        );

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
                        GradientDrawable
                                .Orientation
                                .LEFT_RIGHT,
                        new int[]{
                                Color.parseColor(start),
                                Color.parseColor(end)
                        }
                );

        drawable.setCornerRadius(
                dp(radius)
        );

        return drawable;
    }

    private int drawable(
            String name
    ) {
        return getResources()
                .getIdentifier(
                        name,
                        "drawable",
                        getPackageName()
                );
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private int parseInt(
            String value
    ) {
        try {
            return Integer.parseInt(
                    value == null
                            ? "0"
                            : value.replaceAll(
                                    "[^0-9]",
                                    ""
                            )
            );

        } catch (Exception ignored) {
            return 0;
        }
    }

    private String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim()
                            .isEmpty()
                            && !"null"
                            .equalsIgnoreCase(
                                    value.trim()
                            )
            ) {
                return value.trim();
            }
        }

        return "";
    }

    private void toast(
            String message
    ) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }
}
