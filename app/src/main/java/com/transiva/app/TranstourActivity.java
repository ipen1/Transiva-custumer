package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TranstourActivity extends Activity {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final int TIMEOUT_MS = 20000;
    private static final int PICK_PROOF = 7721;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<JSONObject> wisataPlaces = new ArrayList<>();
    private final List<JSONObject> tickets = new ArrayList<>();
    private final Map<String, Bitmap> imageCache = new HashMap<>();

    private FrameLayout page;
    private LinearLayout root;
    private ProgressBar progressBar;

    private int userId = 0;
    private String username = "User";
    private JSONObject selectedWisata;
    private int selectedQty = 1;
    private Uri selectedProofUri;
    private String selectedProofName = "bukti_transfer.jpg";
    private String currentPage = "list";
    private boolean ticketPollingActive = false;
    private boolean ticketLoading = false;
    private AlertDialog barcodeDialog;
    private String openedBarcodeTicketCode = "";
    private String ticketNoticeMessage = "";

    private final Runnable ticketPollingRunnable = new Runnable() {
        @Override public void run() {
            if (!ticketPollingActive || !"tickets".equals(currentPage)) return;
            loadTickets(false);
            mainHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}

        loadSession();
        buildBase();
        showHome();
        loadWisata();
    }

    @Override
    protected void onDestroy() {
        stopTicketPolling();
        try {
            if (barcodeDialog != null && barcodeDialog.isShowing()) barcodeDialog.dismiss();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void loadSession() {
        try {
            SessionManager session = new SessionManager(this);
            if (session.isLoggedIn()) {
                username = firstNonEmpty(session.getUsername(), session.getName(), "User");
                try { userId = Integer.parseInt(firstNonEmpty(session.getId(), session.getUserId(), "0")); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception ignored) {}

        try {
            android.content.SharedPreferences sp = getSharedPreferences("transiva", MODE_PRIVATE);
            username = firstNonEmpty(sp.getString("username", ""), sp.getString("player_username", ""), "User");
            userId = sp.getInt("id", sp.getInt("user_id", 0));
        } catch (Exception ignored) {}
    }

    private void buildBase() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        page.addView(progressBar, lp);

        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    private void showHome() {
        stopTicketPolling();
        currentPage = "list";
        root.removeAllViews();
        buildTopBar("Transtour", "Tiket wisata & tempat liburan", true);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        Button listBtn = choiceButton("Daftar Wisata", true);
        Button statusBtn = choiceButton("Tiket Saya", false);
        listBtn.setOnClickListener(v -> showHome());
        statusBtn.setOnClickListener(v -> showTickets());
        tabs.addView(listBtn, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(52), 1);
        slp.setMargins(dp(10), 0, 0, 0);
        tabs.addView(statusBtn, slp);
        addWithMargin(tabs, 0, 0, 0, dp(14));

        if (wisataPlaces.isEmpty()) {
            addStatus("Memuat tempat wisata...");
        } else {
            renderWisataList();
        }
    }

    private void renderWisataList() {
        for (JSONObject w : wisataPlaces) addWisataCard(w);
    }

    private void addWisataCard(JSONObject w) {
        LinearLayout card = card();
        card.setPadding(0, 0, 0, dp(14));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#EAF4FF"));
        card.addView(img, new LinearLayout.LayoutParams(-1, dp(190)));
        loadImage(img, absoluteUrl(firstNonEmpty(w.optString("image"), "assets/default-wisata.png")));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), 0);
        card.addView(body);

        body.addView(text(firstNonEmpty(w.optString("name"), "Tempat Wisata"), 20, "#0B3A78", true));

        TextView desc = text(firstNonEmpty(w.optString("description"), "Wisata tersedia di Transiva."), 13, "#64748B", false);
        desc.setPadding(0, dp(6), 0, 0);
        desc.setMaxLines(3);
        body.addView(desc);

        TextView loc = text("📍 " + firstNonEmpty(w.optString("location"), "Lokasi belum tersedia"), 13, "#64748B", false);
        loc.setPadding(0, dp(8), 0, 0);
        body.addView(loc);

        TextView price = text("🎫 " + rupiah(w.optDouble("price", 0)), 18, "#0B7CFF", true);
        price.setPadding(0, dp(10), 0, dp(10));
        body.addView(price);

        LinearLayout qtyBox = new LinearLayout(this);
        qtyBox.setOrientation(LinearLayout.HORIZONTAL);
        qtyBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("Jumlah tiket", 13, "#64748B", true);
        qtyBox.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        EditText qty = new EditText(this);
        qty.setSingleLine(true);
        qty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        qty.setText("1");
        qty.setTextSize(16);
        qty.setGravity(Gravity.CENTER);
        qty.setTextColor(Color.parseColor("#0F172A"));
        qty.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(16), 1));
        qtyBox.addView(qty, new LinearLayout.LayoutParams(dp(88), dp(48)));
        body.addView(qtyBox);

        Button buy = primaryButton("Lanjut Pembayaran");
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(52));
        blp.setMargins(0, dp(12), 0, 0);
        body.addView(buy, blp);
        buy.setOnClickListener(v -> {
            int q = 1;
            try { q = Integer.parseInt(qty.getText().toString().trim()); } catch (Exception ignored) {}
            if (q <= 0) { showInfo("Jumlah tiket", "Jumlah tiket tidak valid."); return; }
            selectedWisata = w;
            selectedQty = q;
            selectedProofUri = null;
            selectedProofName = "bukti_transfer.jpg";
            showPayment();
        });

        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void showPayment() {
        stopTicketPolling();
        currentPage = "payment";
        root.removeAllViews();
        buildTopBar("Pembayaran Tiket", firstNonEmpty(selectedWisata.optString("name"), "Transtour"), true);

        double price = selectedWisata.optDouble("price", 0);
        double total = price * selectedQty;

        LinearLayout info = card();
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        info.addView(text(firstNonEmpty(selectedWisata.optString("name"), "Tempat Wisata"), 20, "#0B3A78", true));
        TextView detail = text("Jumlah tiket: " + selectedQty + "\nTotal bayar: " + rupiah(total), 15, "#0F172A", true);
        detail.setPadding(0, dp(10), 0, 0);
        info.addView(detail);
        addWithMargin(info, 0, 0, 0, dp(14));

        LinearLayout qris = card();
        qris.setGravity(Gravity.CENTER_HORIZONTAL);
        qris.setPadding(dp(16), dp(16), dp(16), dp(16));
        qris.addView(text("Scan QRIS", 18, "#0B3A78", true));
        TextView note = text("Bayar sesuai nominal, lalu upload bukti transfer.", 13, "#64748B", false);
        note.setPadding(0, dp(5), 0, dp(12));
        qris.addView(note);
        ImageView qrisImg = new ImageView(this);
        qrisImg.setAdjustViewBounds(true);
        qrisImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrisImg.setBackgroundColor(Color.WHITE);
        qris.addView(qrisImg, new LinearLayout.LayoutParams(dp(230), dp(230)));
        loadImage(qrisImg, absoluteUrl("assets/qris.jpg"));
        addWithMargin(qris, 0, 0, 0, dp(14));

        LinearLayout upload = card();
        upload.setPadding(dp(16), dp(14), dp(16), dp(14));
        upload.addView(text("Bukti Pembayaran", 17, "#0B3A78", true));
        TextView fileName = text(selectedProofUri == null ? "Belum ada file dipilih" : selectedProofName, 13, selectedProofUri == null ? "#64748B" : "#0B7CFF", true);
        fileName.setPadding(0, dp(8), 0, dp(10));
        upload.addView(fileName);

        Button choose = choiceButton("Pilih Bukti Transfer", false);
        choose.setOnClickListener(v -> pickProof());
        upload.addView(choose, new LinearLayout.LayoutParams(-1, dp(52)));

        Button submit = primaryButton("Kirim Bukti Pembayaran");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(52));
        sp.setMargins(0, dp(12), 0, 0);
        upload.addView(submit, sp);
        submit.setOnClickListener(v -> submitPayment());
        addWithMargin(upload, 0, 0, 0, dp(20));
    }

    private void pickProof() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih bukti pembayaran"), PICK_PROOF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PROOF && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedProofUri = data.getData();
            selectedProofName = getFileName(selectedProofUri);
            showPayment();
        }
    }

    private void showTickets() {
        currentPage = "tickets";
        root.removeAllViews();
        buildTopBar("Tiket Saya", "Status tiket wisata", true);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button listBtn = choiceButton("Daftar Wisata", false);
        Button statusBtn = choiceButton("Tiket Saya", true);
        listBtn.setOnClickListener(v -> showHome());
        statusBtn.setOnClickListener(v -> loadTickets(true));
        tabs.addView(listBtn, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(52), 1);
        slp.setMargins(dp(10), 0, 0, 0);
        tabs.addView(statusBtn, slp);
        addWithMargin(tabs, 0, 0, 0, dp(14));

        addStatus("Memuat tiket saya...");
        startTicketPolling();
        loadTickets(true);
    }

    private void startTicketPolling() {
        ticketPollingActive = true;
        mainHandler.removeCallbacks(ticketPollingRunnable);
        mainHandler.postDelayed(ticketPollingRunnable, 3000);
    }

    private void stopTicketPolling() {
        ticketPollingActive = false;
        mainHandler.removeCallbacks(ticketPollingRunnable);
    }

    private void renderTickets() {
        removeViewsAfter(2);
        if (ticketNoticeMessage != null && ticketNoticeMessage.length() > 0) {
            addClaimNotice(ticketNoticeMessage);
        }
        if (tickets.isEmpty()) {
            addStatus("Belum memiliki tiket wisata.");
            return;
        }
        for (JSONObject t : tickets) addTicketCard(t);
    }

    private void addClaimNotice(String message) {
        TextView notice = text(message, 15, "#16A34A", true);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(14), dp(14), dp(14), dp(14));
        notice.setBackground(roundStroke("#DCFCE7", "#86EFAC", dp(20), 1));
        addWithMargin(notice, 0, 0, 0, dp(14));
    }

    private void addTicketCard(JSONObject t) {
        String status = firstNonEmpty(t.optString("status"), "-");
        boolean active = "confirmed".equals(status) || "approved".equals(status) || "paid".equals(status);
        boolean claimed = "claimed".equals(status) || "used".equals(status);

        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (claimed) card.setBackground(roundStroke("#F0FDF4", "#86EFAC", dp(22), 1));

        card.addView(text("🎫 " + firstNonEmpty(t.optString("ticket_code"), "Kode Tiket"), 17, "#0B3A78", true));
        TextView body = text(
                "Wisata: " + firstNonEmpty(t.optString("wisata_name"), "-") +
                "\nLokasi: " + firstNonEmpty(t.optString("wisata_location"), "-") +
                "\nJumlah: " + t.optInt("qty", 0) +
                "\nTotal: " + rupiah(t.optDouble("amount", 0)) +
                "\nStatus: " + formatStatus(status),
                14, "#0F172A", false);
        body.setPadding(0, dp(10), 0, 0);
        card.addView(body);

        if (claimed) {
            TextView done = text("✅ Tiket sudah diclaim" + (t.optString("claimed_at").length() > 0 ? "\n" + t.optString("claimed_at") : ""), 14, "#16A34A", true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(dp(10), dp(12), dp(10), dp(12));
            done.setBackground(round("#DCFCE7", dp(16)));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
            dlp.setMargins(0, dp(12), 0, 0);
            card.addView(done, dlp);
        } else if (active && firstNonEmpty(t.optString("security_code"), "").length() > 0) {
            Button barcode = primaryButton("Lihat Barcode Tiket");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
            lp.setMargins(0, dp(12), 0, 0);
            card.addView(barcode, lp);
            barcode.setOnClickListener(v -> showBarcodeDialog(t.optString("ticket_code"), t.optString("security_code")));
        } else {
            TextView wait = text("Barcode muncul setelah pembayaran dikonfirmasi admin.", 13, "#64748B", false);
            wait.setGravity(Gravity.CENTER);
            wait.setPadding(dp(10), dp(12), dp(10), 0);
            card.addView(wait);
        }

        addWithMargin(card, 0, 0, 0, dp(14));
    }

    private void showBarcodeDialog(String ticketCode, String securityCode) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        ImageView qr = new ImageView(this);
        qr.setAdjustViewBounds(true);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(qr, new LinearLayout.LayoutParams(dp(260), dp(260)));

        String qrText = "{\"ticket_code\":\"" + ticketCode + "\",\"security_code\":\"" + securityCode + "\"}";
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=" + Uri.encode(qrText);
        loadImage(qr, qrUrl);

        TextView code = text("Kode Booking:\n" + ticketCode + "\n\nKode Keamanan:\n" + securityCode, 14, "#0F172A", true);
        code.setGravity(Gravity.CENTER);
        code.setPadding(0, dp(12), 0, 0);
        box.addView(code);

        openedBarcodeTicketCode = firstNonEmpty(ticketCode, "");
        if (!ticketPollingActive) startTicketPolling();
        try {
            if (barcodeDialog != null && barcodeDialog.isShowing()) barcodeDialog.dismiss();
        } catch (Exception ignored) {}
        barcodeDialog = new AlertDialog.Builder(this)
                .setTitle("Barcode Tiket")
                .setView(box)
                .setPositiveButton("Tutup", null)
                .create();
        barcodeDialog.setOnDismissListener(d -> openedBarcodeTicketCode = "");
        barcodeDialog.show();
    }

    private void loadWisata() {
        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject res = getJson(BASE_URL + "server/getWisataPlaces.php?v=" + System.currentTimeMillis());
                JSONArray arr = res.optJSONArray("places");
                wisataPlaces.clear();
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) wisataPlaces.add(arr.getJSONObject(i));
                }
                mainHandler.post(() -> { setLoading(false); if ("list".equals(currentPage)) showHome(); });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); addStatus("Gagal memuat tempat wisata."); showInfo("Gagal", e.getMessage()); });
            }
        }).start();
    }

    private void loadTickets(boolean showLoader) {
        if (userId <= 0) { removeViewsAfter(2); addStatus("Silakan login ulang untuk melihat tiket."); return; }
        if (ticketLoading) return;
        ticketLoading = true;
        if (showLoader) setLoading(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                JSONObject res = postJson(BASE_URL + "server/checkWisataTicket.php?_=" + System.currentTimeMillis(), payload);

                List<JSONObject> freshTickets = new ArrayList<>();
                JSONArray arr = res.optJSONArray("tickets");
                if (res.optBoolean("success", false) && arr != null) {
                    for (int i = 0; i < arr.length(); i++) freshTickets.add(arr.getJSONObject(i));
                }

                String claimedCode = detectNewClaimedTicket(freshTickets);

                mainHandler.post(() -> {
                    ticketLoading = false;
                    if (showLoader) setLoading(false);
                    tickets.clear();
                    tickets.addAll(freshTickets);

                    if (claimedCode.length() > 0) {
                        ticketNoticeMessage = "✅ Tiket berhasil di-claim oleh owner wisata.";
                        try {
                            if (barcodeDialog != null && barcodeDialog.isShowing()) barcodeDialog.dismiss();
                        } catch (Exception ignored) {}
                    }

                    if ("tickets".equals(currentPage)) renderTickets();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    ticketLoading = false;
                    if (showLoader) setLoading(false);
                    if (showLoader && "tickets".equals(currentPage)) { removeViewsAfter(2); addStatus("Gagal memuat tiket."); }
                });
            }
        }).start();
    }

    private String detectNewClaimedTicket(List<JSONObject> freshTickets) {
        for (JSONObject fresh : freshTickets) {
            String code = firstNonEmpty(fresh.optString("ticket_code"), fresh.optString("code"));
            String status = firstNonEmpty(fresh.optString("status"), "").toLowerCase(Locale.ROOT);
            if (!"claimed".equals(status) && !"used".equals(status)) continue;

            boolean wasClaimed = false;
            for (JSONObject old : tickets) {
                String oldCode = firstNonEmpty(old.optString("ticket_code"), old.optString("code"));
                if (!code.equals(oldCode)) continue;
                String oldStatus = firstNonEmpty(old.optString("status"), "").toLowerCase(Locale.ROOT);
                wasClaimed = "claimed".equals(oldStatus) || "used".equals(oldStatus);
                break;
            }

            if (!wasClaimed) return code;
            if (openedBarcodeTicketCode.length() > 0 && openedBarcodeTicketCode.equals(code)) return code;
        }
        return "";
    }

    private void submitPayment() {
        if (userId <= 0) { showInfo("Login", "User ID tidak ditemukan. Silakan login ulang."); return; }
        if (selectedWisata == null) { showInfo("Wisata", "Pilih wisata terlebih dahulu."); return; }
        if (selectedProofUri == null) { showInfo("Bukti Transfer", "Upload bukti transfer terlebih dahulu."); return; }

        setLoading(true);
        new Thread(() -> {
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("user_id", String.valueOf(userId));
                fields.put("wisata_id", String.valueOf(selectedWisata.optInt("id", 0)));
                fields.put("qty", String.valueOf(selectedQty));
                fields.put("amount", String.valueOf((long) (selectedWisata.optDouble("price", 0) * selectedQty)));
                JSONObject res = postMultipart(BASE_URL + "server/createWisataOrder.php", fields, "proof", selectedProofUri, selectedProofName);
                boolean ok = res.optBoolean("success", false);
                String msg = firstNonEmpty(res.optString("message"), ok ? "Bukti pembayaran berhasil dikirim." : "Gagal mengirim bukti pembayaran.");
                mainHandler.post(() -> {
                    setLoading(false);
                    if (ok) {
                        new AlertDialog.Builder(this)
                                .setTitle("Berhasil")
                                .setMessage(msg + "\n\nKode Booking: " + res.optString("ticket_code", "-") + "\nStatus: Menunggu verifikasi admin")
                                .setPositiveButton("Lihat Tiket", (d, w) -> showTickets())
                                .show();
                    } else showInfo("Gagal", msg);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { setLoading(false); showInfo("Error", "Gagal mengirim pembayaran: " + e.getMessage()); });
            }
        }).start();
    }

    private JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("GET");
        return new JSONObject(readStream(c));
    }

    private JSONObject postJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
        return new JSONObject(readStream(c));
    }

    private JSONObject postMultipart(String urlText, Map<String, String> fields, String fileField, Uri fileUri, String fileName) throws Exception {
        String boundary = "----TransivaBoundary" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream raw = new BufferedOutputStream(c.getOutputStream());
        for (String key : fields.keySet()) {
            write(raw, "--" + boundary + "\r\n");
            write(raw, "Content-Disposition: form-data; name=\"" + key + "\"\r\n\r\n");
            write(raw, fields.get(key) + "\r\n");
        }

        String mime = "image/jpeg";
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".png")) mime = "image/png";
        else if (fileName.toLowerCase(Locale.ROOT).endsWith(".webp")) mime = "image/webp";

        write(raw, "--" + boundary + "\r\n");
        write(raw, "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName + "\"\r\n");
        write(raw, "Content-Type: " + mime + "\r\n\r\n");

        InputStream is = new BufferedInputStream(getContentResolver().openInputStream(fileUri));
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) raw.write(buf, 0, len);
        is.close();
        write(raw, "\r\n--" + boundary + "--\r\n");
        raw.flush();
        raw.close();

        return new JSONObject(readStream(c));
    }

    private void write(OutputStream os, String s) throws Exception {
        os.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private String readStream(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        return sb.toString();
    }

    private void loadImage(ImageView view, String urlText) {
        String finalUrl = firstNonEmpty(urlText, "");
        view.setTag(finalUrl);
        Bitmap cached = imageCache.get(finalUrl);
        if (cached != null) { view.setImageBitmap(cached); return; }

        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(finalUrl).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                Bitmap bm = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bm != null) imageCache.put(finalUrl, bm);
                mainHandler.post(() -> {
                    Object tag = view.getTag();
                    if (bm != null && tag != null && finalUrl.equals(tag.toString())) view.setImageBitmap(bm);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private String getFileName(Uri uri) {
        String name = "bukti_transfer.jpg";
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && cursor.moveToFirst()) name = firstNonEmpty(cursor.getString(idx), name);
                cursor.close();
            }
        } catch (Exception ignored) {}
        name = name.replace("\"", "").replace("/", "_");
        if (!name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp)$")) name += ".jpg";
        return name;
    }

    private String absoluteUrl(String value) {
        value = firstNonEmpty(value, "assets/default-wisata.png").trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("/")) return BASE_URL.substring(0, BASE_URL.length() - 1) + value;
        return BASE_URL + value;
    }

    private void buildTopBar(String title, String sub, boolean back) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(16));
        if (back) {
            TextView b = text("‹", 34, "#0B3A78", true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(round("#FFFFFF", dp(18)));
            b.setOnClickListener(v -> handleBack());
            row.addView(b, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text(title, 23, "#0B3A78", true));
        if (sub != null && sub.length() > 0) col.addView(text(sub, 12, "#64748B", false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void handleBack() {
        if ("payment".equals(currentPage)) { selectedWisata = null; selectedProofUri = null; showHome(); return; }
        if ("tickets".equals(currentPage)) { showHome(); return; }
        finish();
    }

    @Override public void onBackPressed() { handleBack(); }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(roundStroke("#FFFFFF", "#E2ECF8", dp(22), 1));
        v.setElevation(dp(2));
        return v;
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round("#0B7CFF", dp(18)));
        return b;
    }

    private Button choiceButton(String s, boolean active) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor(active ? "#FFFFFF" : "#0B3A78"));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundStroke(active ? "#0B7CFF" : "#FFFFFF", active ? "#0B7CFF" : "#D7E6F8", dp(18), 1));
        return b;
    }

    private GradientDrawable round(String color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable roundStroke(String color, String stroke, int radius, int sw) {
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(sw), Color.parseColor(stroke));
        return g;
    }

    private void removeViewsAfter(int keepCount) {
        if (root == null) return;
        int count = root.getChildCount();
        if (count > keepCount) {
            root.removeViews(keepCount, count - keepCount);
        }
    }

    private void addWithMargin(View v, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, b);
        root.addView(v, lp);
    }

    private void addStatus(String message) {
        TextView t = text(message, 14, "#64748B", false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(24), dp(16), dp(24));
        t.setBackground(roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));
        addWithMargin(t, 0, 0, 0, dp(12));
    }

    private void setLoading(boolean b) {
        if (progressBar != null) progressBar.setVisibility(b ? View.VISIBLE : View.GONE);
    }

    private void showInfo(String title, String msg) {
        try { new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show(); } catch (Exception ignored) {}
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private String rupiah(double v) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) v);
    }

    private String formatStatus(String status) {
        if ("pending_payment".equals(status) || "pending".equals(status)) return "Menunggu Verifikasi";
        if ("paid".equals(status)) return "Pembayaran Diterima";
        if ("approved".equals(status) || "confirmed".equals(status)) return "Tiket Aktif";
        if ("claimed".equals(status)) return "Tiket Sudah Diclaim";
        if ("rejected".equals(status)) return "Pembayaran Ditolak";
        if ("used".equals(status)) return "Tiket Sudah Diclaim";
        return firstNonEmpty(status, "-");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String s : values) if (s != null && s.trim().length() > 0 && !"null".equalsIgnoreCase(s.trim())) return s.trim();
        return "";
    }
}
