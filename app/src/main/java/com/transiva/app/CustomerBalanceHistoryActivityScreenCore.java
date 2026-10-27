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
class CustomerBalanceHistoryActivityScreenCore extends CustomerAdaptiveActivity {
    protected CustomerPullRefreshLayout pullRefresh;




    protected static final String BASE_URL =
            "https://transiva.my.id/server/";

    protected static final String SUMMARY_URL =
            BASE_URL + "customer_wallet_summary.php";

    protected static final String TRANSFER_URL =
            BASE_URL + "customer_wallet_transfer.php";

    protected static final String QUOTE_TRANSFER_URL =
            BASE_URL + "customer_wallet_quote.php";

    protected static final String LOOKUP_USER_URL =
            BASE_URL + "customer_wallet_lookup_user.php";

    protected static final String WITHDRAW_URL =
            BASE_URL + "customer_wallet_withdraw.php";

    protected final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    protected final CustomerFeatureRuntimeController featureRuntime =
            new CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role.HISTORY);

    protected final List<JSONObject> transactions =
            new ArrayList<>();

    protected LinearLayout content;
    protected LinearLayout mutationBox;
    protected LinearLayout filterRow;
    protected ProgressBar loading;

    protected TextView balanceText;
    protected TextView incomeText;
    protected TextView expenseText;
    protected TextView pendingText;

    protected String username = "";
    protected int userId;
    protected String filter = "all";
    private int periodDays=30;
    private int displayedBalance;
    private boolean balanceHidden;
    private ScrollView historyScroll;
    private TextView periodText;
    private String lastListSignature="";
    protected boolean loadingData;
    private boolean operationBusy;
    private boolean pendingPrompted;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        CustomerWindowPolicy.status(getWindow(), 
                Color.parseColor("#0B7CFF")
        );

        CustomerWindowPolicy.navigation(getWindow(), 
                Color.parseColor("#071426")
        );

        readSession();
        setContentView(buildScreen());
        CustomerResponsiveUi.apply(this);
        CustomerAppSettings.apply(this);
        loadWallet();
    }

    @Override
    protected void onResume() {
        super.onResume();
        featureRuntime.onResume();
        CustomerAppSettings.apply(this);
        if (!pendingPrompted) { pendingPrompted=true; inspectWalletPending(null); }

        if (
                content != null
                        && !loadingData
        ) {
            loadWallet();
        }
    }

    protected void readSession() {
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

    protected View buildScreen() {
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

        historyScroll=scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        pullRefresh = new CustomerPullRefreshLayout(this, scroll, () -> loadWallet(), () -> loadingData||userId<=0);
        pullRefresh.setTimeoutAction(this::cancelWalletRead);
        shell.addView(
                pullRefresh,
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
                new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);

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

    protected void buildHeader() {
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
                view -> { if(pullRefresh!=null)pullRefresh.requestRefresh(); else loadWallet(); }
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

    protected void buildBalanceCard() {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(16),dp(18),dp(14));card.setBackground(gradient("#075EF4","#22A4FF",20));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("Transiva Pay",16,"#FFFFFF",true),new LinearLayout.LayoutParams(0,-2,1));
        TextView eye=text("Sembunyikan",10,"#FFFFFF",true);eye.setPadding(dp(8),dp(8),dp(8),dp(8));eye.setContentDescription("Tampilkan atau sembunyikan saldo");eye.setOnClickListener(v->{balanceHidden=!balanceHidden;balanceText.setText(balanceHidden?"Rp ••••••":rupiah(displayedBalance));eye.setText(balanceHidden?"Tampilkan":"Sembunyikan");});top.addView(eye);card.addView(top);
        card.addView(text("Saldo tersedia",11,"#DDEFFF",false));balanceText=text("Memuat...",27,"#FFFFFF",true);balanceText.setMaxLines(2);card.addView(balanceText);
        TextView note=text("✓ Riwayat mutasi tercatat",10,"#EAF5FF",false);note.setPadding(0,dp(7),0,0);card.addView(note);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,dp(12));content.addView(card,lp);
    }

    protected void buildActionGrid() {
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
                        "Isi Saldo",
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
                        "Tarik Saldo",
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

    protected LinearLayout.LayoutParams actionLp(
            boolean margin
    ) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(76),
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

    protected View actionItem(
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

    protected void buildSummary() {
        content.addView(text("Mutasi Transiva Pay • periode terpilih",11,"#64748B",true));
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

    protected TextView summaryCard(
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

    protected void buildMutationSection() {
        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.addView(
                text(
                        "Riwayat Transaksi",
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
                        "30 hari ▾",
                        10,
                        "#718096",
                        false
                );

        periodText=period;
        period.setPadding(dp(8),dp(12),dp(8),dp(12));period.setOnClickListener(v->new TransivaAlertDialogBuilder(this).setTitle("Periode transaksi").setItems(new String[]{"7 hari terakhir","30 hari terakhir","90 hari terakhir"},(dialog,which)->{periodDays=new int[]{7,30,90}[which];periodText.setText(periodDays+" hari ▾");loadWallet();}).show());
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

    protected void rebuildFilters() {
        filterRow.removeAllViews();

        addFilter("all", "Semua");
        addFilter("transpay", "Transiva Pay");
        addFilter("cash", "Tunai");
        addFilter("in", "Pemasukan");
        addFilter("out", "Pengeluaran");
        addFilter("pending", "Diproses");

    }



    protected void addFilter(
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

    private CustomerCancelableRead walletRead;
    private long walletGeneration;
    private final ThreadLocal<CustomerSessionGuard> operationSession=new ThreadLocal<>();
    private void executeSession(Runnable work) {
        CustomerSessionGuard session=CustomerSessionGuard.capture(this);
        featureRuntime.execute(() -> { operationSession.set(session);try{work.run();}finally{operationSession.remove();} },
                () -> featureRuntime.post(mainHandler,() -> {if(!isFinishing()&&!isDestroyed()&&session.matches(this)){operationBusy=false;setLoading(false);toast("Aplikasi sedang sibuk. Coba kembali sebentar lagi.");}}));
    }
    private void postSession(Runnable result) {
        CustomerSessionGuard tagged=operationSession.get();
        final CustomerSessionGuard session=tagged==null?CustomerSessionGuard.capture(this):tagged;
        featureRuntime.post(mainHandler, () -> {
            if(isFinishing()||isDestroyed())return;
            if(!Integer.toString(userId).equals(session.account)||!session.matches(this)){operationBusy=false;setLoading(false);return;}
            result.run();
        });
    }
    private void cancelWalletRead() {
        ++walletGeneration;if(walletRead!=null)walletRead.cancel();walletRead=null;loadingData=false;setLoading(false);
    }
    private void postWallet(CustomerCancelableRead request,long generation,Runnable result) {
        featureRuntime.post(mainHandler, () -> {
            if(isFinishing()||isDestroyed()||walletRead!=request||generation!=walletGeneration)return;
            if(!request.current()){cancelWalletRead();if(pullRefresh!=null)pullRefresh.finish(false);return;}result.run();
        });
    }
    protected void loadWallet() {
        if(loadingData)return;
        CustomerSessionGuard session=CustomerSessionGuard.capture(this);
        if(userId<=0||!Integer.toString(userId).equals(session.account))return;
        loadingData=true;setLoading(true);
        if(pullRefresh!=null&&pullRefresh.isRefreshing()&&loading!=null)loading.setVisibility(View.GONE);
        final int requestedDays=periodDays;final long generation=++walletGeneration;
        final CustomerCancelableRead request=new CustomerCancelableRead(this);walletRead=request;
        request.start(() -> {
            try {
                JSONObject payload=new JSONObject().put("user_id",userId).put("username",username).put("days",requestedDays);
                JSONObject response=request.read(SUMMARY_URL,payload,20000);JSONArray array=response.optJSONArray("transactions");
                if(!response.optBoolean("success",false)||array==null)throw new IllegalStateException("Gagal memuat transaksi");
                final int balance=response.getInt("balance"),income=response.optInt("income"),expense=response.optInt("expense"),pending=response.optInt("pending");
                List<JSONObject> fresh=new ArrayList<>();for(int i=0;i<array.length();i++){JSONObject item=array.optJSONObject(i);if(item!=null){
                    if("cash".equalsIgnoreCase(item.optString("payment_channel")))item.put("status",CustomerBalanceFormatter.cashOrderStatus(item.optString("order_status"),item.optString("status")));
                    fresh.add(item);
                }}
                postWallet(request,generation,() -> {
                    loadingData=false;walletRead=null;setLoading(false);
                    if(requestedDays!=periodDays){loadWallet();return;}
                    displayedBalance=balance;balanceText.setText(balanceHidden?"Rp ••••••":rupiah(balance));
                    incomeText.setText("+"+rupiah(income));expenseText.setText("-"+rupiah(expense));pendingText.setText(rupiah(pending));
                    String signature=fresh.toString();if(!signature.equals(lastListSignature)){
                        int savedY=historyScroll==null?0:historyScroll.getScrollY();lastListSignature=signature;
                        transactions.clear();transactions.addAll(fresh);renderTransactions();
                        if(historyScroll!=null)historyScroll.post(() -> {if(!isFinishing()&&!isDestroyed())historyScroll.scrollTo(0,savedY);});
                    }
                    if(pullRefresh!=null)pullRefresh.finish(true);
                });
            } catch(Exception error){postWallet(request,generation,() -> {
                loadingData=false;walletRead=null;setLoading(false);
                boolean pulled=pullRefresh!=null&&pullRefresh.isRefreshing();if(pullRefresh!=null)pullRefresh.finish(false);
                if(!pulled)toast("Gagal memuat transaksi. Periksa koneksi.");
            });}
        }, () -> postWallet(request,generation,() -> {cancelWalletRead();if(pullRefresh!=null)pullRefresh.finish(false);}), () -> {if(!isFinishing()&&!isDestroyed()&&walletRead==request&&walletGeneration==generation){cancelWalletRead();if(pullRefresh!=null)pullRefresh.finish(false);}});
    }

    protected void renderTransactions() {
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

            String paymentChannel =
                    item.optString(
                            "payment_channel",
                            "transpay"
                    ).toLowerCase(Locale.ROOT);

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
                    )
                            || (
                            filter.equals("cash")
                                    && paymentChannel.equals("cash")
                    )
                            || (
                            filter.equals("transpay")
                                    && paymentChannel.equals("transpay")
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



    protected View transactionCard(JSONObject item) {
        boolean cash="cash".equals(item.optString("payment_channel"));boolean incoming="in".equals(item.optString("direction"));String status=item.optString("status","success");
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(roundStroke("#FFFFFF","#E1EAF5",14,1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(cash?cashService(item):item.optString("title",transactionTitle(item.optString("type"))),12,"#0B3A78",true);title.setMaxLines(2);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView amount=text((cash?"":incoming?"+":"−")+rupiah(item.optInt("amount")),12,cash?"#0B3A78":incoming?"#0E9F4B":"#D9485F",true);amount.setPadding(dp(8),0,0,0);top.addView(amount);card.addView(top);
        String channel=cash?"Tunai":"Transiva Pay";
        TextView meta=text(channel+" • "+transactionStatus(item),10,statusColor(status),false);meta.setPadding(0,dp(4),0,0);card.addView(meta);
        card.addView(text(formatDate(item.optString("created_at")),10,"#8495A8",false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(7));card.setLayoutParams(lp);card.setOnClickListener(v->showTransactionDetails(item));return card;
    }
    private String transactionStatus(JSONObject item){
        String status=item.optString("status");
        if(status.equals("cancelled")||status.equals("canceled"))return "cash".equals(item.optString("payment_channel"))?"Dibatalkan · Tidak perlu dibayar":"Dibatalkan";
        if("cash".equals(item.optString("payment_channel"))){if(status.equals("success"))return "Dibayar tunai";if(status.equals("pending"))return "Menunggu pembayaran";}
        return statusLabel(status);
    }
    private String cashService(JSONObject item){
        String service=item.optString("service_label").toLowerCase(Locale.ROOT);
        if(service.contains("food"))return "TransFood";if(service.contains("shop"))return "TransShop";if(service.contains("car"))return "TransCar";if(service.contains("pickup")||service.contains("send"))return "TransSend";if(service.contains("bike")||service.contains("ride"))return "TransRide";return "Pembayaran Tunai";
    }
    private void showTransactionDetails(JSONObject item){
        boolean cash="cash".equals(item.optString("payment_channel"));
        String body="Nominal: "+rupiah(item.optInt("amount"))+"\nMetode: "+(cash?"Tunai":"Transiva Pay")+"\nStatus: "+transactionStatus(item)+"\nWaktu: "+formatDate(item.optString("created_at"))+"\nReferensi: "+item.optString("reference","-")+"\n\n"+item.optString("description");
        if(cash&&!"cancelled".equals(item.optString("status"))&&item.has("waiting_fee"))body+="\n\nOngkir: "+rupiah(item.optInt("fare_before_waiting"))+"\nSmart Waiting: "+rupiah(item.optInt("waiting_fee"))+"\nTotal: "+rupiah(item.optInt("amount"));
        body+=cash&&"cancelled".equals(item.optString("status"))?"\n\nPesanan sudah dibatalkan. Tidak ada pembayaran tunai yang perlu dilakukan dan tidak ada saldo Transiva Pay yang dipotong.":cash?"\n\nPembayaran tunai tidak memotong saldo Transiva Pay.":"\n\nSaldo sebelum: "+rupiah(item.optInt("balance_before"))+"\nSaldo setelah: "+rupiah(item.optInt("balance_after"));
        new TransivaAlertDialogBuilder(this).setTitle(item.optString("title","Detail transaksi")).setMessage(body).setPositiveButton("Tutup",null).show();
    }

    protected void openDeposit() {
        startActivity(
                new Intent(
                        this,
                        CustomerTopUpActivity.class
                )
        );
    }

    protected void showTransferDialog() { inspectWalletPending(this::showTransferForm); }

    private void showTransferForm() {
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
                new TransivaAlertDialogBuilder(this)
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

    protected void requestTransferQuote(
            String recipient,
            int amount,
            String note
    ) {
        if (operationBusy) return;
        operationBusy=true; setLoading(true);

        executeSession(() -> {
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

                postSession(() -> {
                    operationBusy=false; setLoading(false);

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
                postSession(() -> {
                    operationBusy=false; setLoading(false);

                    showInfo(
                            "Gagal Membuat Detail",
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi internet."
                            )
                    );
                });
            }
        });
    }



    protected void showTransferConfirmation(
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

        new TransivaAlertDialogBuilder(this)
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

    protected void submitTransfer(String recipient, int amount, String note, String quoteToken, String requestId) {
        if (operationBusy) return;
        if (quoteToken == null || quoteToken.trim().isEmpty() || requestId == null || requestId.isEmpty()) {
            showInfo("Kirim Dana", "Detail transfer tidak valid. Silakan ulangi."); return;
        }
        try {
            JSONObject payload=new JSONObject().put("recipient",recipient).put("amount",amount)
                    .put("note",note).put("quote_token",quoteToken).put("request_id",requestId);
            operationBusy=true;setLoading(true);
            CustomerSessionGuard guard=CustomerSessionGuard.capture(this);
            walletCoordinator().start("transfer",payload,walletCallback(guard,null));
        } catch(Exception error){operationBusy=false;setLoading(false);showInfo("Kirim Dana", "Operasi belum dapat disiapkan.");}
    }

    protected void installRupiahFormatting(
            EditText input
    ) {
        input.addTextChangedListener(
                new TextWatcher() {

                    protected boolean updating;

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

    protected void showWithdrawDialog() { inspectWalletPending(this::showWithdrawForm); }

    private void showWithdrawForm() {
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
                new TransivaAlertDialogBuilder(this)
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



    protected void submitWithdraw(int amount, String bank, String accountName, String accountNumber, String note) {
        if(operationBusy)return;
        try {
            JSONObject payload=new JSONObject().put("amount",amount).put("bank_name",bank)
                    .put("account_name",accountName).put("account_number",accountNumber).put("note",note);
            operationBusy=true;setLoading(true);
            CustomerSessionGuard guard=CustomerSessionGuard.capture(this);
            walletCoordinator().start("withdraw",payload,walletCallback(guard,null));
        }catch(Exception error){operationBusy=false;setLoading(false);showInfo("Withdraw","Operasi belum dapat disiapkan.");}
    }

    private CustomerWalletCoordinator walletCoordinator(){
        return new CustomerWalletCoordinator(getApplicationContext(),Integer.toString(userId));
    }
    private void inspectWalletPending(Runnable whenEmpty){
        if(operationBusy)return;
        operationBusy=true;setLoading(true);
        CustomerSessionGuard guard=CustomerSessionGuard.capture(this);
        walletCoordinator().inspect(walletCallback(guard,whenEmpty));
    }
    private CustomerWalletCoordinator.Callback walletCallback(CustomerSessionGuard guard,Runnable whenEmpty){
        return result -> featureRuntime.post(mainHandler,()->{
            if(isFinishing()||isDestroyed())return;
            operationBusy=false;setLoading(false);
            if(!Integer.toString(userId).equals(guard.account)||!guard.matches(this))return;
            if("pending".equals(result.state)) {
                if(result.operation==null){if(whenEmpty!=null)whenEmpty.run();}
                else showPendingWallet(result.operation);
            } else if("succeeded".equals(result.state)||"rejected".equals(result.state)) {
                JSONObject receipt=result.receipt;
                showInfo("succeeded".equals(result.state)?("transfer".equals(result.operation.optString("action"))?"Kirim Dana Berhasil":"Withdraw Diajukan"):"Operasi Tidak Diproses",
                        result.message+"\nID: "+receipt.optString("operation_id")
                        +(receipt.optString("reference").isEmpty()?"":"\nReferensi: "+receipt.optString("reference")));
                loadWallet();
            } else if("not_found".equals(result.state)&&result.operation!=null) {
                showConfirmWalletResend(result.operation);
            } else if("processing".equals(result.state)) {
                showInfo("Operasi Masih Diproses","Belum ada hasil final. ID tetap disimpan. Periksa status kembali nanti.");
            } else {
                showInfo("busy".equals(result.state)?"Wallet Sedang Sibuk":"Hasil Belum Terkonfirmasi",result.message);
            }
        });
    }
    private void showPendingWallet(JSONObject operation){
        new TransivaAlertDialogBuilder(this).setTitle("Periksa status transaksi")
                .setMessage("Ada operasi wallet yang belum terkonfirmasi. Tombol ini hanya membaca status dan tidak mengirim ulang transaksi.\nID: "+operation.optString("id"))
                .setPositiveButton("Periksa status",(dialog,which)->checkWalletStatus(operation))
                .setNegativeButton("Nanti",null).show();
    }
    private void checkWalletStatus(JSONObject operation){
        if(operationBusy)return;
        operationBusy=true;setLoading(true);
        CustomerSessionGuard guard=CustomerSessionGuard.capture(this);
        walletCoordinator().check(operation,walletCallback(guard,null));
    }
    private void showConfirmWalletResend(JSONObject operation){
        JSONObject payload=operation.optJSONObject("payload");
        String amount=payload==null?"":rupiah(payload.optInt("amount"));
        new TransivaAlertDialogBuilder(this).setTitle("Hasil belum ditemukan")
                .setMessage("Server belum memiliki hasil terkonfirmasi untuk operasi "+amount+" ini. Request sebelumnya mungkin masih berjalan. Jika Anda ingin mengirim ulang, ID dan data operasi tetap sama.\nID: "+operation.optString("id"))
                .setPositiveButton("Kirim ulang ID sama",(dialog,which)->{
                    if(operationBusy)return;
                    operationBusy=true;setLoading(true);
                    CustomerSessionGuard guard=CustomerSessionGuard.capture(this);
                    walletCoordinator().retry(operation,walletCallback(guard,null));
                }).setNegativeButton("Nanti",null).show();
    }

    protected JSONObject postJson(String endpoint, JSONObject payload) throws Exception {
        CustomerSessionGuard tagged=operationSession.get();
        CustomerSessionGuard session=tagged==null?CustomerSessionGuard.capture(this):tagged;
        if(!session.matches(this))throw new java.io.InterruptedIOException("Session changed");
        HttpURLConnection connection=CustomerApiClient.open(this,endpoint);
        try {
            session.authorize(this, connection);
            String idempotency = payload.optString("idempotency_key", "");
            if (!idempotency.isEmpty()) connection.setRequestProperty("Idempotency-Key", idempotency);
            connection.setConnectTimeout(25000);connection.setReadTimeout(25000);connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            int code=connection.getResponseCode();
            String response=BoundedResponseReader.read(code>=200&&code<400?connection.getInputStream():connection.getErrorStream(),4*1024*1024,25000);
            if(!session.matches(this))throw new java.io.InterruptedIOException("Session changed");
            CustomerApiClient.handleSessionResponse(this,code,response);
            if(response.isEmpty())throw new IllegalStateException("Respons server kosong");return new JSONObject(response);
        } finally {connection.disconnect();}
    }

    protected View buildBottomNavigation() {
        return CustomerBottomNavigation.build(this, CustomerPageTransition.WALLET);
    }

    protected LinearLayout whiteCard() {
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

    protected LinearLayout dialogForm() {
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

    protected EditText input(
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

    protected View spacer() {
        View spacer = new View(this);

        spacer.setLayoutParams(
                new LinearLayout.LayoutParams(
                        -1,
                        dp(8)
                )
        );

        return spacer;
    }

    protected String transactionTitle(
            String type
    ) {
        return CustomerBalanceFormatter.transactionTitle(type);
    }

    protected String statusLabel(
            String status
    ) {
        return CustomerBalanceFormatter.statusLabel(status);
    }

    protected String statusColor(
            String status
    ) {
        return CustomerBalanceFormatter.statusColor(status);
    }

    protected String statusBackground(
            String status
    ) {
        return CustomerBalanceFormatter.statusBackground(status);
    }

    protected String formatDate(
            String value
    ) {
        return CustomerBalanceFormatter.formatDate(value);
    }

    protected String rupiah(
            int amount
    ) {
        return CustomerCommonFormatters.rupiah(amount);
    }

    protected void setLoading(
            boolean show
    ) {
        if (loading != null) {
            loading.setVisibility(
                    (show || operationBusy)
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    protected void showInfo(
            String title,
            String message
    ) {
        new TransivaAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    protected TextView text(
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

    protected GradientDrawable round(
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

    protected GradientDrawable roundStroke(
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

    protected GradientDrawable gradient(
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

    protected int drawable(
            String name
    ) {
        return getResources()
                .getIdentifier(
                        name,
                        "drawable",
                        getPackageName()
                );
    }

    protected int dp(
            int value
    ) {
        return CustomerUiPrimitives.dp(this, value);
    }

    protected int parseInt(
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

    protected String first(
            String... values
    ) {
        return CustomerCommonFormatters.firstBasic(values);
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

    @Override
    protected void onPause() {
        featureRuntime.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelWalletRead();
        featureRuntime.destroy();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

}
