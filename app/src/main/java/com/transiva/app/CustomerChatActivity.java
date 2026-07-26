package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CustomerChatActivity extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/";

    private static final String CONVERSATIONS_URL =
            BASE_URL
                    + "server/get_customer_conversations.php";

    private static final String IMAGE_PREFIX =
            "[[IMAGE]]";

    private static final String IMAGE_V2_PREFIX =
            "[[IMAGE2]]";

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final List<JSONObject> conversations =
            new ArrayList<>();

    private LinearLayout content;
    private LinearLayout listBox;
    private LinearLayout tabRow;
    private ProgressBar progress;

    private int userId;
    private String selectedTab = "active";
    private boolean loading;

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

        loadSession();
        setContentView(buildScreen());
        CustomerAppSettings.apply(this);

        CustomerChatNotificationPoller
                .requestPermission(this);

        CustomerChatNotificationPoller.start(
                this,
                userId
        );

        loadConversations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CustomerAppSettings.apply(this);

        if (!loading && listBox != null) {
            loadConversations();
        }
    }

    private void loadSession() {
        try {
            SessionManager session =
                    new SessionManager(this);

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
        buildInfoCard();
        buildTabs();

        listBox =
                new LinearLayout(this);

        listBox.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams listLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        listLp.setMargins(
                0,
                dp(12),
                0,
                0
        );

        content.addView(
                listBox,
                listLp
        );

        shell.addView(
                buildBottomNavigation(),
                new LinearLayout.LayoutParams(
                        -1,
                        dp(66)
                )
        );

        progress =
                new ProgressBar(this);

        progress.setVisibility(
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
                progress,
                progressLp
        );

        render();

        return page;
    }

    private void buildHeader() {
        LinearLayout row =
                new LinearLayout(this);

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout titleBox =
                new LinearLayout(this);

        titleBox.setOrientation(
                LinearLayout.VERTICAL
        );

        titleBox.addView(
                text(
                        "Pesan",
                        24,
                        "#0B3A78",
                        true
                )
        );

        titleBox.addView(
                text(
                        "Percakapan terkait pesanan Transiva",
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
                view -> loadConversations()
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

    private void buildInfoCard() {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(13),
                dp(12),
                dp(13),
                dp(12)
        );

        card.setBackground(
                gradient(
                        "#0868F5",
                        "#23A7FF",
                        18
                )
        );

        card.setElevation(dp(2));

        ImageView icon =
                new ImageView(this);

        int resource =
                drawable("ic_nav_chat");

        if (resource != 0) {
            icon.setImageResource(
                    resource
            );
        }

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                )
        );

        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.VERTICAL
        );

        info.setPadding(
                dp(11),
                0,
                0,
                0
        );

        info.addView(
                text(
                        "Chat aman dan terkait order",
                        13,
                        "#FFFFFF",
                        true
                )
        );

        info.addView(
                text(
                        "Chat selesai tetap tersimpan sebagai riwayat baca.",
                        10,
                        "#EAF5FF",
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

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardLp.setMargins(
                0,
                dp(14),
                0,
                dp(14)
        );

        content.addView(
                card,
                cardLp
        );
    }

    private void buildTabs() {
        tabRow =
                new LinearLayout(this);

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
                round("#EAF1FA", 15)
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

        Button active =
                tabButton(
                        "Aktif",
                        selectedTab.equals("active")
                );

        Button history =
                tabButton(
                        "Riwayat",
                        selectedTab.equals("history")
                );

        active.setOnClickListener(
                view -> {
                    selectedTab = "active";
                    rebuildTabs();
                    render();
                }
        );

        history.setOnClickListener(
                view -> {
                    selectedTab = "history";
                    rebuildTabs();
                    render();
                }
        );

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

        tabRow.addView(
                history,
                historyLp
        );
    }

    private Button tabButton(
            String label,
            boolean selected
    ) {
        Button button =
                new Button(this);

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);

        button.setTextColor(
                Color.parseColor(
                        selected
                                ? "#0B7CFF"
                                : "#64748B"
                )
        );

        button.setTypeface(
                Typeface.DEFAULT,
                selected
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );

        button.setBackground(
                selected
                        ? round("#FFFFFF", 12)
                        : round("#EAF1FA", 12)
        );

        if (selected) {
            button.setElevation(dp(1));
        }

        return button;
    }

    private void loadConversations() {
        if (loading) {
            return;
        }

        if (userId <= 0) {
            toast(
                    "Sesi pengguna tidak ditemukan"
            );

            return;
        }

        loading = true;
        progress.setVisibility(
                View.VISIBLE
        );

        render();

        new Thread(() -> {
            try {
                String endpoint =
                        CONVERSATIONS_URL
                                + "?user_id="
                                + URLEncoder.encode(
                                        String.valueOf(
                                                userId
                                        ),
                                        StandardCharsets
                                                .UTF_8
                                                .name()
                                )
                                + "&_="
                                + System.currentTimeMillis();

                JSONObject response =
                        CustomerMessageApi.get(
                                endpoint
                        );

                JSONArray array =
                        response.optJSONArray(
                                "conversations"
                        );

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
                        JSONObject item =
                                array.optJSONObject(i);

                        if (item != null) {
                            fresh.add(item);
                        }
                    }
                }

                mainHandler.post(() -> {
                    conversations.clear();
                    conversations.addAll(
                            fresh
                    );

                    loading = false;

                    progress.setVisibility(
                            View.GONE
                    );

                    render();
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;

                    progress.setVisibility(
                            View.GONE
                    );

                    render();

                    toast(
                            "Gagal memuat daftar pesan"
                    );
                });
            }
        }).start();
    }

    private void render() {
        if (listBox == null) {
            return;
        }

        listBox.removeAllViews();

        if (loading) {
            addState(
                    "Memuat percakapan...",
                    "Daftar pesan sedang diperbarui."
            );

            return;
        }

        List<JSONObject> filtered =
                new ArrayList<>();

        for (JSONObject item : conversations) {
            boolean history =
                    item.optBoolean(
                            "is_history",
                            false
                    )
                            || CustomerMessageStatus
                            .isEnded(
                                    item.optString(
                                            "status",
                                            ""
                                    )
                            );

            if (
                    selectedTab.equals("history")
                            == history
            ) {
                filtered.add(item);
            }
        }

        if (filtered.isEmpty()) {
            if (
                    selectedTab.equals(
                            "active"
                    )
            ) {
                addState(
                        "Belum ada pesan aktif",
                        "Percakapan akan tersedia setelah order diterima merchant atau driver."
                );

            } else {
                addState(
                        "Riwayat pesan masih kosong",
                        "Chat dari order yang selesai akan tersimpan di sini."
                );
            }

            return;
        }

        TextView title =
                text(
                        selectedTab.equals(
                                "active"
                        )
                                ? "Percakapan aktif"
                                : "Riwayat percakapan",
                        15,
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
                0,
                0,
                dp(9)
        );

        listBox.addView(
                title,
                titleLp
        );

        for (JSONObject item : filtered) {
            listBox.addView(
                    conversationCard(item)
            );
        }
    }

    private View conversationCard(
            JSONObject item
    ) {
        boolean history =
                item.optBoolean(
                        "is_history",
                        false
                )
                        || CustomerMessageStatus
                        .isEnded(
                                item.optString(
                                        "status",
                                        ""
                                )
                        );

        boolean canOpen =
                history
                        || item.optBoolean(
                        "chat_available",
                        false
                )
                        || CustomerMessageStatus
                        .canSend(
                                item.optString(
                                        "status",
                                        ""
                                )
                        );

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
                        17,
                        1
                )
        );

        card.setElevation(dp(1));

        FrameLayout iconFrame =
                new FrameLayout(this);

        iconFrame.setBackground(
                round(
                        serviceSoftColor(
                                item.optString(
                                        "order_type",
                                        ""
                                )
                        ),
                        14
                )
        );

        ImageView icon =
                new ImageView(this);

        int iconResource =
                serviceDrawable(
                        item.optString(
                                "order_type",
                                ""
                        )
                );

        if (iconResource != 0) {
            icon.setImageResource(
                    iconResource
            );
        }

        icon.setScaleType(
                ImageView.ScaleType
                        .CENTER_INSIDE
        );

        FrameLayout.LayoutParams iconLp =
                new FrameLayout.LayoutParams(
                        dp(34),
                        dp(34)
                );

        iconLp.gravity =
                Gravity.CENTER;

        iconFrame.addView(
                icon,
                iconLp
        );

        card.addView(
                iconFrame,
                new LinearLayout.LayoutParams(
                        dp(50),
                        dp(50)
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
                dp(7),
                0
        );

        String participant =
                first(
                        item.optString(
                                "participant_name"
                        ),
                        item.optString(
                                "driver"
                        ),
                        serviceName(
                                item.optString(
                                        "order_type",
                                        ""
                                )
                        )
                );

        TextView participantView =
                text(
                        participant,
                        14,
                        "#0B3A78",
                        true
                );

        participantView.setSingleLine(
                true
        );

        participantView.setEllipsize(
                TextUtils.TruncateAt.END
        );

        info.addView(
                participantView
        );

        info.addView(
                buildLastMessagePreview(
                        item,
                        participant,
                        history
                )
        );

        TextView orderInfo =
                text(
                        serviceName(
                                item.optString(
                                        "order_type",
                                        ""
                                )
                        )
                                + " • "
                                + CustomerMessageStatus
                                .orderLabel(
                                        item.optString(
                                                "status",
                                                ""
                                        ),
                                        item.optString(
                                                "order_type",
                                                ""
                                        )
                                ),
                        9,
                        history
                                ? "#8495A8"
                                : "#0B7CFF",
                        true
                );

        orderInfo.setSingleLine(true);

        orderInfo.setEllipsize(
                TextUtils.TruncateAt.END
        );

        info.addView(orderInfo);

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

        end.setGravity(Gravity.END);

        TextView date =
                text(
                        formatDate(
                                item.optString(
                                        "last_message_at",
                                        item.optString(
                                                "created_at",
                                                ""
                                        )
                                )
                        ),
                        9,
                        "#94A3B8",
                        false
                );

        date.setGravity(
                Gravity.END
        );

        end.addView(date);

        TextView arrow =
                text(
                        canOpen
                                ? "›"
                                : "🔒",
                        canOpen
                                ? 25
                                : 13,
                        canOpen
                                ? "#0B7CFF"
                                : "#94A3B8",
                        true
                );

        arrow.setGravity(
                Gravity.END
        );

        end.addView(arrow);

        card.addView(
                end,
                new LinearLayout.LayoutParams(
                        dp(70),
                        -2
                )
        );

        if (canOpen) {
            card.setOnClickListener(
                    view -> openRoom(
                            item,
                            history
                    )
            );

        } else {
            card.setOnClickListener(
                    view -> toast(
                            "Chat belum tersedia untuk status order ini."
                    )
            );
        }

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        cardLp.setMargins(
                0,
                0,
                0,
                dp(9)
        );

        card.setLayoutParams(
                cardLp
        );

        return card;
    }

    private View buildLastMessagePreview(
            JSONObject item,
            String participant,
            boolean history
    ) {
        String fallback =
                CustomerMessageStatus
                        .availabilityLabel(
                                item.optString(
                                        "status",
                                        ""
                                ),
                                history
                        );

        String raw =
                item.optString(
                        "last_message",
                        ""
                ).trim();

        boolean imageMessage =
                isImageMessage(raw);

        String preview;

        if (imageMessage) {
            preview =
                    isLastMessageMine(item)
                            ? "Anda mengirim foto"
                            : first(
                            participant,
                            "Mitra"
                    )
                            + " mengirim foto";

        } else {
            preview = first(
                    raw,
                    fallback,
                    "Belum ada pesan"
            );
        }

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        if (imageMessage) {
            row.setPadding(
                    dp(7),
                    dp(4),
                    dp(8),
                    dp(4)
            );

            row.setBackground(
                    round(
                            history
                                    ? "#F1F5F9"
                                    : "#EAF4FF",
                            9
                    )
            );

            TextView camera =
                    text(
                            "▣",
                            11,
                            history
                                    ? "#64748B"
                                    : "#0B7CFF",
                            true
                    );

            camera.setGravity(
                    Gravity.CENTER
            );

            LinearLayout.LayoutParams cameraLp =
                    new LinearLayout.LayoutParams(
                            dp(20),
                            dp(20)
                    );

            cameraLp.setMargins(
                    0,
                    0,
                    dp(4),
                    0
            );

            row.addView(
                    camera,
                    cameraLp
            );
        }

        TextView message =
                text(
                        preview,
                        11,
                        imageMessage
                                ? (
                                history
                                        ? "#64748B"
                                        : "#0B5FAF"
                        )
                                : "#64748B",
                        imageMessage
                );

        message.setSingleLine(true);

        message.setEllipsize(
                TextUtils.TruncateAt.END
        );

        message.setMaxLines(1);

        row.addView(
                message,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        rowLp.setMargins(
                0,
                dp(2),
                0,
                dp(2)
        );

        row.setLayoutParams(rowLp);

        return row;
    }

    private boolean isImageMessage(
            String message
    ) {
        String value =
                message == null
                        ? ""
                        : message.trim();

        return value.startsWith(
                IMAGE_PREFIX
        )
                || value.startsWith(
                IMAGE_V2_PREFIX
        );
    }

    private boolean isLastMessageMine(
            JSONObject item
    ) {
        if (
                item.optBoolean(
                        "last_message_is_mine",
                        false
                )
                        || item.optBoolean(
                        "is_mine",
                        false
                )
        ) {
            return true;
        }

        String sender =
                first(
                        item.optString(
                                "last_sender_type"
                        ),
                        item.optString(
                                "sender_type"
                        ),
                        item.optString(
                                "last_message_sender"
                        ),
                        item.optString(
                                "sender_role"
                        )
                ).toLowerCase(
                        Locale.US
                );

        return sender.equals(
                "customer"
        )
                || sender.equals(
                "user"
        )
                || sender.equals(
                "pelanggan"
        );
    }

    private void openRoom(
            JSONObject item,
            boolean history
    ) {
        Intent intent =
                new Intent(
                        this,
                        CustomerChatRoomActivity.class
                );

        intent.putExtra(
                "order_id",
                first(
                        item.optString(
                                "order_id"
                        ),
                        item.optString(
                                "id"
                        )
                )
        );

        intent.putExtra(
                "room_id",
                item.optString(
                        "room_id",
                        ""
                )
        );

        intent.putExtra(
                "driver_name",
                item.optString(
                        "driver",
                        ""
                )
        );

        intent.putExtra(
                "participant_name",
                item.optString(
                        "participant_name",
                        ""
                )
        );

        intent.putExtra(
                "order_type",
                item.optString(
                        "order_type",
                        ""
                )
        );

        intent.putExtra(
                "order_status",
                item.optString(
                        "status",
                        ""
                )
        );

        intent.putExtra(
                "read_only",
                history
        );

        startActivity(intent);
    }

    private void addState(
            String title,
            String description
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(
                Gravity.CENTER
        );

        card.setPadding(
                dp(18),
                dp(28),
                dp(18),
                dp(28)
        );

        card.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#E1EAF5",
                        18,
                        1
                )
        );

        ImageView image =
                new ImageView(this);

        int resource =
                drawable("ic_nav_chat");

        if (resource != 0) {
            image.setImageResource(
                    resource
            );
        }

        image.setAlpha(0.7f);

        card.addView(
                image,
                new LinearLayout.LayoutParams(
                        dp(58),
                        dp(58)
                )
        );

        TextView heading =
                text(
                        title,
                        14,
                        "#0B3A78",
                        true
                );

        heading.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams headingLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        headingLp.setMargins(
                0,
                dp(10),
                0,
                dp(5)
        );

        card.addView(
                heading,
                headingLp
        );

        TextView body =
                text(
                        description,
                        11,
                        "#718096",
                        false
                );

        body.setGravity(
                Gravity.CENTER
        );

        card.addView(body);
        listBox.addView(card);
    }

    private View buildBottomNavigation() {
        return CustomerBottomNavigation.build(this, CustomerPageTransition.CHAT);
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
                                Class.forName(
                                        className
                                )
                        )
                );

                return;

            } catch (Exception ignored) {
            }
        }

        toast(
                "Halaman transaksi sedang disiapkan"
        );
    }

    private String serviceName(
            String type
    ) {
        type =
                CustomerMessageStatus
                        .normalize(type);

        if (type.contains("food")) {
            return "TransFood";
        }

        if (
                type.contains("car")
                        || type.contains("mobil")
        ) {
            return "TransCar";
        }

        if (type.contains("tour")) {
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

        return "TransRide";
    }

    private int serviceDrawable(
            String type
    ) {
        type =
                CustomerMessageStatus
                        .normalize(type);

        if (type.contains("food")) {
            return drawable(
                    "ic_service_food"
            );
        }

        if (
                type.contains("car")
                        || type.contains("mobil")
        ) {
            return drawable(
                    "ic_service_car"
            );
        }

        if (type.contains("tour")) {
            return drawable(
                    "ic_service_tour"
            );
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
            return drawable(
                    "ic_service_mart"
            );
        }

        return drawable(
                "ic_service_ride"
        );
    }

    private String serviceSoftColor(
            String type
    ) {
        type =
                CustomerMessageStatus
                        .normalize(type);

        if (type.contains("food")) {
            return "#FFF4E8";
        }

        if (type.contains("tour")) {
            return "#F2EDFF";
        }

        if (type.contains("laundry")) {
            return "#ECFDF5";
        }

        return "#EAF4FF";
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
                            "dd MMM\nHH:mm",
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

        view.setIncludeFontPadding(
                false
        );

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
                round(
                        fill,
                        radius
                );

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

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
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
                            && !"undefined"
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
