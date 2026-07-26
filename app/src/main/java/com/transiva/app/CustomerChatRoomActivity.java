package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.animation.DecelerateInterpolator;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
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
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CustomerChatRoomActivity extends Activity {

    private static final String BASE_URL =
            "https://transiva.my.id/";

    private static final String GET_CHAT_URL =
            BASE_URL + "server/getChat.php";

    private static final String SEND_CHAT_URL =
            BASE_URL + "server/sendChat.php";

    private static final long REFRESH_MS = 2500L;

    private static final String UPLOAD_IMAGE_URL =
            BASE_URL + "server/upload_chat_image.php";

    private static final String UPLOAD_VOICE_URL =
            BASE_URL + "server/upload_chat_voice.php";

    private static final int REQUEST_GALLERY = 4101;
    private static final int REQUEST_CAMERA = 4102;
    private static final int REQUEST_INTERNAL_CAMERA = 4104;
    private static final int REQUEST_CAMERA_PERMISSION = 4103;
    private static final int REQUEST_AUDIO_PERMISSION = 4105;
    private static final String IMAGE_PREFIX = "[[IMAGE]]";
    private static final String IMAGE_V2_PREFIX = "[[IMAGE2]]";

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private LinearLayout messagesBox;
    private ScrollView messagesScroll;
    private TextView participantText;
    private TextView statusText;
    private EditText input;
    private Button sendButton;
    private Button attachButton;
    private Button voiceButton;
    private ProgressBar progress;
    private LinearLayout inputCard;

    private String orderId = "";
    private String roomId = "";
    private String participantName = "";
    private String orderType = "";
    private String orderStatus = "";

    private boolean readOnly;
    private boolean loading;
    private boolean sending;
    private boolean uploading;
    private boolean destroyed;
    private boolean chatVisible;
    private int lastId;
    private boolean firstLoad = true;
    private final SparseArray<TextView> receiptViews = new SparseArray<>();

    private Uri cameraPhotoUri;
    private File cameraPhotoFile;
    private boolean cameraUsesMediaStore;
    private long pendingPhotoSequence;

    private final Runnable refreshRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!destroyed && !readOnly) {
                        loadMessages(false);

                        mainHandler.postDelayed(
                                this,
                                REFRESH_MS
                        );
                    }
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

        readIntent();
        setContentView(buildScreen());
        CustomerAppSettings.apply(this);

        CustomerChatNotificationPoller.requestPermission(
                this
        );

        int notificationUserId = 0;

        try {
            SessionManager session =
                    new SessionManager(this);

            notificationUserId =
                    Integer.parseInt(
                            first(
                                    session.getId(),
                                    session.getUserId(),
                                    "0"
                            )
                    );

        } catch (Exception ignored) {
        }

        CustomerChatNotificationPoller.start(
                this,
                notificationUserId
        );

        CustomerChatNotificationPoller.setOpenRoom(
                roomId
        );

        if (roomId.isEmpty()) {
            showMessage(
                    "Chat tidak tersedia",
                    "Room percakapan tidak ditemukan.",
                    true
            );

            return;
        }

        applyReadOnlyState();
        loadMessages(true);

        if (!readOnly) {
            mainHandler.postDelayed(
                    refreshRunnable,
                    REFRESH_MS
            );
        }
    }

    private void readIntent() {
        orderId = first(
                getIntent().getStringExtra(
                        "order_id"
                ),
                ""
        );

        roomId = normalizeRoom(
                first(
                        getIntent().getStringExtra(
                                "room_id"
                        ),
                        orderId.isEmpty()
                                ? ""
                                : "ROOM-" + orderId
                )
        );

        participantName = first(
                getIntent().getStringExtra(
                        "participant_name"
                ),
                getIntent().getStringExtra(
                        "driver_name"
                ),
                "Driver"
        );

        orderType = first(
                getIntent().getStringExtra(
                        "order_type"
                ),
                ""
        );

        orderStatus = first(
                getIntent().getStringExtra(
                        "order_status"
                ),
                ""
        );

        readOnly =
                getIntent().getBooleanExtra(
                        "read_only",
                        false
                )
                        || CustomerMessageStatus
                        .isEnded(orderStatus);
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);

        page.setBackgroundColor(
                Color.parseColor("#F4F8FD")
        );

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(10)
        );

        page.addView(
                root,
                new FrameLayout.LayoutParams(-1, -1)
        );

        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
                dp(10),
                dp(9),
                dp(10),
                dp(9)
        );

        header.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#DCE8F6",
                        18,
                        1
                )
        );

        TextView back = text("←", 24, "#0B7CFF", true);
        back.setGravity(Gravity.CENTER);
        back.setIncludeFontPadding(false);
        back.setPadding(0, 0, 0, dp(1));
        back.setBackground(round("#EAF4FF", 15));
        back.setOnClickListener(view -> finish());

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
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
                0,
                0
        );

        participantText = text(
                participantName,
                16,
                "#0B3A78",
                true
        );

        participantText.setSingleLine(true);
        titleBox.addView(participantText);

        statusText = text(
                readOnly
                        ? "Riwayat percakapan"
                        : "Menghubungkan chat...",
                10,
                readOnly
                        ? "#8495A8"
                        : "#0B7CFF",
                true
        );

        titleBox.addView(statusText);

        header.addView(
                titleBox,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView orderLabel = text(
                serviceName(orderType),
                10,
                "#0B7CFF",
                true
        );

        orderLabel.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );

        orderLabel.setBackground(
                round("#EAF4FF", 12)
        );

        header.addView(orderLabel);
        root.addView(header);

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);

        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                );

        scrollLp.setMargins(
                0,
                dp(10),
                0,
                dp(10)
        );

        root.addView(messagesScroll, scrollLp);

        messagesBox =
                new LinearLayout(this);

        messagesBox.setOrientation(
                LinearLayout.VERTICAL
        );

        messagesBox.setPadding(
                dp(2),
                dp(8),
                dp(2),
                dp(8)
        );

        messagesScroll.addView(
                messagesBox,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        inputCard = new LinearLayout(this);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(
                dp(9),
                dp(7),
                dp(9),
                dp(7)
        );

        inputCard.setBackground(
                roundStroke(
                        "#FFFFFF",
                        "#D7E6F8",
                        20,
                        1
                )
        );

        inputCard.setElevation(dp(5));

        attachButton = new Button(this);
        attachButton.setText("+");
        attachButton.setAllCaps(false);
        attachButton.setTextSize(22);
        attachButton.setTextColor(Color.parseColor("#0B7CFF"));
        attachButton.setPadding(0, 0, 0, 0);
        attachButton.setBackground(round("#EAF4FF", 15));
        attachButton.setOnClickListener(view -> showAttachmentMenu());

        inputCard.addView(
                attachButton,
                new LinearLayout.LayoutParams(dp(44), -1)
        );

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setTextSize(13);
        input.setTextColor(
                Color.parseColor("#0F172A")
        );

        input.setHintTextColor(
                Color.parseColor("#94A3B8")
        );

        input.setHint("Ketik pesan...");
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        input.setImeOptions(
                EditorInfo.IME_ACTION_SEND
        );

        input.setPadding(
                dp(13),
                0,
                dp(13),
                0
        );

        input.setBackground(
                roundStroke(
                        "#F8FBFF",
                        "#D8E4F2",
                        16,
                        1
                )
        );

        LinearLayout.LayoutParams inputLp =
                new LinearLayout.LayoutParams(0, -1, 1);
        inputLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(input, inputLp);

        sendButton = primaryButton("Kirim");

        LinearLayout.LayoutParams sendLp =
                new LinearLayout.LayoutParams(
                        dp(74),
                        -1
                );

        sendLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(sendButton, sendLp);

        voiceButton = new Button(this);
        voiceButton.setText("🎙");
        voiceButton.setTextSize(18);
        voiceButton.setAllCaps(false);
        voiceButton.setPadding(0, 0, 0, 0);
        voiceButton.setTextColor(Color.parseColor("#0B7CFF"));
        voiceButton.setBackground(round("#EAF4FF", 15));
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(dp(48), -1);
        voiceLp.setMargins(dp(7), 0, 0, 0);
        inputCard.addView(voiceButton, voiceLp);
        setupVoiceRecorder();

        sendButton.setOnClickListener(
                view -> { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); sendMessage(); }
        );

        input.setOnEditorActionListener(
                (view, actionId, event) -> {
                    boolean enter =
                            event != null
                                    && event.getKeyCode()
                                    == KeyEvent.KEYCODE_ENTER
                                    && event.getAction()
                                    == KeyEvent.ACTION_DOWN;

                    if (
                            actionId
                                    == EditorInfo.IME_ACTION_SEND
                                    || enter
                    ) {
                        sendMessage();
                        return true;
                    }

                    return false;
                }
        );

        root.addView(
                inputCard,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(62)
                )
        );

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(
                        dp(44),
                        dp(44)
                );

        progressLp.gravity = Gravity.CENTER;
        page.addView(progress, progressLp);

        return page;
    }

    private void showAttachmentMenu() {
        if (readOnly || uploading) return;

        new AlertDialog.Builder(this)
                .setTitle("Kirim Foto")
                .setItems(
                        new String[]{"Ambil Foto", "Pilih dari Galeri"},
                        (dialog, which) -> {
                            if (which == 0) openCamera();
                            else openGallery();
                        }
                )
                .show();
    }

    private void openCamera() {
        if (
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    REQUEST_CAMERA_PERMISSION
            );

            return;
        }

        launchCameraInternal();
    }

    private void launchCameraInternal() {
        try {
            Intent intent = new Intent(
                    this,
                    ChatCameraActivity.class
            );

            startActivityForResult(
                    intent,
                    REQUEST_INTERNAL_CAMERA
            );

        } catch (Exception error) {
            toast(
                    "Kamera tidak tersedia: "
                            + error.getMessage()
            );
        }
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
                requestCode
                        == REQUEST_CAMERA_PERMISSION
        ) {
            if (
                    grantResults.length > 0
                            &&
                    grantResults[0]
                            == PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraInternal();
            } else {
                toast(
                        "Izin kamera diperlukan untuk mengambil foto."
                );
            }
        }
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_OPEN_DOCUMENT
            );

            intent.addCategory(
                    Intent.CATEGORY_OPENABLE
            );

            intent.setType("image/*");

            startActivityForResult(
                    intent,
                    REQUEST_GALLERY
            );

        } catch (Exception error) {
            toast("Galeri tidak tersedia");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (
                requestCode == REQUEST_INTERNAL_CAMERA
        ) {
            if (
                    resultCode != RESULT_OK
                            || data == null
            ) {
                toast("Pengambilan foto dibatalkan");
                return;
            }

            String path =
                    data.getStringExtra(
                            "photo_path"
                    );

            if (
                    path == null
                            || path.trim().isEmpty()
            ) {
                showMessage(
                        "Foto gagal diproses",
                        "File kamera tidak ditemukan.",
                        false
                );

                return;
            }

            processCameraFile(
                    path.trim()
            );

            return;
        }

        if (requestCode == REQUEST_GALLERY) {
            if (
                    resultCode != RESULT_OK
                            || data == null
                            || data.getData() == null
            ) {
                return;
            }

            processSelectedPhoto(
                    data.getData(),
                    false
            );
        }
    }

    private void processSelectedPhoto(
            Uri sourceUri,
            boolean fromCamera
    ) {
        if (sourceUri == null || uploading) {
            return;
        }

        uploading = true;
        progress.setVisibility(View.VISIBLE);
        attachButton.setEnabled(false);
        sendButton.setEnabled(false);

        new Thread(() -> {
            try {
                ChatImageProcessor.ImagePayload payload =
                        ChatImageProcessor.fromUri(
                                getContentResolver(),
                                sourceUri
                        );

                mainHandler.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    uploadPhoto(payload);
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    attachButton.setEnabled(true);
                    sendButton.setEnabled(true);

                    showMessage(
                            "Foto gagal diproses",
                            first(
                                    error.getMessage(),
                                    "Foto tidak dapat dibaca."
                            ),
                            false
                    );
                });
            }
        }).start();
    }

    private void processCameraFile(
            String filePath
    ) {
        if (
                filePath == null
                        || filePath.trim().isEmpty()
                        || uploading
        ) {
            return;
        }

        uploading = true;
        progress.setVisibility(View.VISIBLE);
        attachButton.setEnabled(false);
        sendButton.setEnabled(false);

        new Thread(() -> {
            java.io.File file =
                    new java.io.File(filePath);

            try {
                ChatImageProcessor.ImagePayload payload =
                        ChatImageProcessor.fromFile(file);

                mainHandler.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    uploadPhoto(payload);

                    try {
                        file.delete();
                    } catch (Exception ignored) {
                    }
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    uploading = false;
                    progress.setVisibility(View.GONE);
                    attachButton.setEnabled(true);
                    sendButton.setEnabled(true);

                    showMessage(
                            "Foto gagal diproses",
                            first(
                                    error.getMessage(),
                                    "Foto kamera tidak dapat dibaca."
                            ),
                            false
                    );
                });
            }
        }).start();
    }

    private void waitForCameraOutput(
            Uri uri
    ) throws Exception {
        long size = 0L;
        long previousSize = -1L;
        int stableCount = 0;

        for (int attempt = 0; attempt < 20; attempt++) {
            size = getUriLength(uri);

            if (size > 1024L && size == previousSize) {
                stableCount++;

                if (stableCount >= 2) {
                    return;
                }

            } else {
                stableCount = 0;
            }

            previousSize = size;

            try {
                Thread.sleep(150L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (size <= 1024L) {
            throw new IllegalStateException(
                    "Kamera belum menghasilkan file foto"
            );
        }
    }

    private long getUriLength(
            Uri uri
    ) {
        if (uri == null) {
            return 0L;
        }

        try (
                AssetFileDescriptor descriptor =
                        getContentResolver()
                                .openAssetFileDescriptor(
                                        uri,
                                        "r"
                                )
        ) {
            if (descriptor == null) {
                return 0L;
            }

            long length = descriptor.getLength();

            if (length >= 0L) {
                return length;
            }

            try (
                    InputStream input =
                            descriptor
                                    .createInputStream()
            ) {
                return input.available();
            }

        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void uploadPhoto(
            ChatImageProcessor.ImagePayload payload
    ) {
        if (
                uploading
                        || readOnly
                        || payload == null
        ) {
            return;
        }

        uploading = true;
        attachButton.setEnabled(false);
        sendButton.setEnabled(false);

        final PendingPhotoBubble pendingBubble =
                addPendingPhotoBubble(payload);

        scrollBottom();

        new Thread(() -> {
            try {
                JSONObject response =
                        CustomerMessageApi.uploadImagePair(
                                UPLOAD_IMAGE_URL,
                                roomId,
                                "customer",
                                payload
                        );

                mainHandler.post(() -> {
                    uploading = false;
                    attachButton.setEnabled(true);
                    sendButton.setEnabled(true);

                    if (
                            response.optBoolean(
                                    "success",
                                    false
                            )
                    ) {
                        pendingBubble.markSuccess();
                        cleanupCameraFile();

                        /*
                         * Beri server/getChat.php sedikit waktu lalu ambil
                         * pesan baru. Jika poller sedang bekerja, fungsi ini
                         * menunggu sampai request sebelumnya selesai.
                         */
                        refreshMessagesAfterUpload(
                                pendingBubble,
                                0
                        );

                        return;
                    }

                    pendingBubble.markFailed(
                            first(
                                    response.optString(
                                            "message"
                                    ),
                                    "Foto gagal dikirim"
                            )
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    uploading = false;
                    attachButton.setEnabled(true);
                    sendButton.setEnabled(true);

                    pendingBubble.markFailed(
                            first(
                                    error.getMessage(),
                                    "Periksa koneksi lalu coba lagi."
                            )
                    );

                    cleanupCameraFile();
                });
            }
        }).start();
    }

    private void refreshMessagesAfterUpload(
            PendingPhotoBubble pendingBubble,
            int attempt
    ) {
        if (destroyed) {
            return;
        }

        if (loading && attempt < 12) {
            mainHandler.postDelayed(
                    () -> refreshMessagesAfterUpload(
                            pendingBubble,
                            attempt + 1
                    ),
                    350
            );

            return;
        }

        if (pendingBubble.root.getParent() != null) {
            messagesBox.removeView(
                    pendingBubble.root
            );
        }

        loadMessages(false);

        /*
         * Fallback kedua. Ini mengatasi kondisi last_id server baru belum
         * terbaca pada request pertama setelah upload.
         */
        mainHandler.postDelayed(
                () -> {
                    if (!loading) {
                        loadMessages(false);
                    }
                },
                1100
        );
    }

    private PendingPhotoBubble addPendingPhotoBubble(
            ChatImageProcessor.ImagePayload payload
    ) {
        pendingPhotoSequence++;

        LinearLayout wrapper =
                new LinearLayout(this);

        wrapper.setOrientation(
                LinearLayout.VERTICAL
        );

        wrapper.setGravity(Gravity.RIGHT);

        LinearLayout.LayoutParams wrapperLp =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        wrapperLp.setMargins(
                0,
                dp(4),
                0,
                dp(4)
        );

        FrameLayout imageFrame =
                new FrameLayout(this);

        imageFrame.setBackground(
                round("#EAF1FA", 16)
        );

        ImageView preview =
                new ImageView(this);

        preview.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        Bitmap previewBitmap =
                BitmapFactory.decodeByteArray(
                        payload.previewWebp,
                        0,
                        payload.previewWebp.length
                );

        if (previewBitmap != null) {
            preview.setImageBitmap(
                    previewBitmap
            );
        }

        imageFrame.addView(
                preview,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        FrameLayout loadingLayer =
                new FrameLayout(this);

        loadingLayer.setBackgroundColor(
                Color.argb(
                        70,
                        0,
                        0,
                        0
                )
        );

        ProgressBar spinner =
                new ProgressBar(this);

        spinner.setIndeterminate(true);

        FrameLayout.LayoutParams spinnerLp =
                new FrameLayout.LayoutParams(
                        dp(42),
                        dp(42)
                );

        spinnerLp.gravity = Gravity.CENTER;

        loadingLayer.addView(
                spinner,
                spinnerLp
        );

        imageFrame.addView(
                loadingLayer,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        wrapper.addView(
                imageFrame,
                new LinearLayout.LayoutParams(
                        dp(220),
                        dp(165)
                )
        );

        TextView state = text(
                "Mengirim foto…",
                9,
                "#64748B",
                true
        );

        state.setGravity(Gravity.RIGHT);
        state.setPadding(
                dp(7),
                dp(3),
                dp(7),
                0
        );

        wrapper.addView(
                state,
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                )
        );

        messagesBox.addView(
                wrapper,
                wrapperLp
        );

        return new PendingPhotoBubble(
                wrapper,
                loadingLayer,
                state
        );
    }

    private final class PendingPhotoBubble {

        final LinearLayout root;
        final FrameLayout loadingLayer;
        final TextView state;

        PendingPhotoBubble(
                LinearLayout root,
                FrameLayout loadingLayer,
                TextView state
        ) {
            this.root = root;
            this.loadingLayer = loadingLayer;
            this.state = state;
        }

        void markSuccess() {
            loadingLayer.setVisibility(
                    View.GONE
            );

            state.setText(
                    "Terkirim • memuat chat…"
            );

            state.setTextColor(
                    Color.parseColor("#0B7CFF")
            );
        }

        void markFailed(String message) {
            loadingLayer.removeAllViews();

            TextView failed = text(
                    "!",
                    22,
                    "#FFFFFF",
                    true
            );

            failed.setGravity(Gravity.CENTER);

            loadingLayer.setBackgroundColor(
                    Color.argb(
                            145,
                            190,
                            38,
                            38
                    )
            );

            loadingLayer.addView(
                    failed,
                    new FrameLayout.LayoutParams(
                            -1,
                            -1
                    )
            );

            state.setText(
                    first(
                            message,
                            "Foto gagal dikirim"
                    )
            );

            state.setTextColor(
                    Color.parseColor("#C23636")
            );
        }
    }

    private void cleanupCameraFile() {
        try {
            if (
                    cameraUsesMediaStore
                            && cameraPhotoUri != null
            ) {
                getContentResolver().delete(
                        cameraPhotoUri,
                        null,
                        null
                );

            } else if (
                    cameraPhotoFile != null
                            && cameraPhotoFile.exists()
            ) {
                cameraPhotoFile.delete();
            }

        } catch (Exception ignored) {
        }

        try {
            if (cameraPhotoUri != null) {
                revokeUriPermission(
                        cameraPhotoUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                | Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }
        } catch (Exception ignored) {
        }

        cameraPhotoFile = null;
        cameraPhotoUri = null;
        cameraUsesMediaStore = false;
    }

    private void applyReadOnlyState() {
        if (inputCard == null) {
            return;
        }

        if (readOnly) {
            input.setEnabled(false);
            input.setHint(
                    "Percakapan ini hanya dapat dibaca"
            );

            attachButton.setEnabled(false);
            attachButton.setAlpha(0.45f);
            if (voiceButton != null) { voiceButton.setEnabled(false); voiceButton.setAlpha(0.45f); }

            sendButton.setEnabled(false);
            sendButton.setText("Selesai");
            sendButton.setAlpha(0.55f);

            statusText.setText(
                    "Order selesai • riwayat hanya baca"
            );
        }
    }

    private void setupVoiceRecorder() {
        ChatVoiceNote.attachRecorder(this, voiceButton, REQUEST_AUDIO_PERMISSION, new ChatVoiceNote.Listener() {
            @Override public void onState(String text, boolean recording, boolean cancelArmed) {
                statusText.setText(text);
                voiceButton.setText(cancelArmed ? "✕" : (recording ? "●" : "🎙"));
            }
            @Override public void onReady(File file, long durationMs) { uploadVoiceNote(file, durationMs); }
            @Override public void onError(String message) { toast(message); voiceButton.setText("🎙"); }
        });
    }

    private void uploadVoiceNote(File file, long durationMs) {
        if (readOnly || uploading || file == null) return;
        uploading = true;
        voiceButton.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject upload = CustomerMessageApi.uploadVoice(
                        UPLOAD_VOICE_URL, roomId, "customer", file, durationMs);
                if (!upload.optBoolean("success", false)) throw new IllegalStateException(upload.optString("message", "Upload voice note gagal"));
                String audioUrl = upload.optString("url", upload.optString("audio_url", ""));
                JSONObject payload = new JSONObject();
                payload.put("room_id", roomId); payload.put("sender_type", "customer");
                payload.put("order_id", orderId); payload.put("message", ChatVoiceNote.encode(audioUrl, durationMs));
                JSONObject sent = CustomerMessageApi.post(SEND_CHAT_URL, payload);
                mainHandler.post(() -> {
                    uploading = false; voiceButton.setEnabled(!readOnly); voiceButton.setText("🎙");
                    if (sent.optBoolean("success", false)) loadMessages(false);
                    else toast(sent.optString("message", "Voice note gagal dikirim"));
                });
            } catch (Exception e) {
                mainHandler.post(() -> { uploading = false; voiceButton.setEnabled(!readOnly); voiceButton.setText("🎙"); statusText.setText("Voice note pending • jaringan"); toast(first(e.getMessage(), "Voice note gagal dikirim")); });
            } finally { file.delete(); }
        }).start();
    }

    private String absoluteVoiceContent(String content) {
        String url = ChatVoiceNote.voiceUrl(content);
        long duration = ChatVoiceNote.voiceDuration(content);
        return ChatVoiceNote.encode(absoluteUrl(url), duration);
    }

    private PendingText addPendingText(String content) {
        LinearLayout wrapper = new LinearLayout(this); wrapper.setOrientation(LinearLayout.VERTICAL); wrapper.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(4), 0, dp(4)); messagesBox.addView(wrapper, lp);
        TextView bubble = text(content, 13, "#FFFFFF", false); bubble.setPadding(dp(13), dp(9), dp(13), dp(9)); bubble.setBackground(gradient("#086BFF", "#2EA2FF", 17)); wrapper.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        TextView state = text("Pending…", 9, "#94A3B8", false); state.setPadding(dp(7), dp(2), dp(7), 0); wrapper.addView(state, new LinearLayout.LayoutParams(-2, -2));
        animateMessage(wrapper, true);
        scrollBottom();
        return new PendingText(wrapper, state);
    }

    private static final class PendingText {
        final LinearLayout root; final TextView state; PendingText(LinearLayout root, TextView state) { this.root=root; this.state=state; }
        void markNetworkPending() { state.setText("Pending • jaringan"); }
    }

    private void loadMessages(
            boolean showLoading
    ) {
        if (loading) {
            return;
        }

        loading = true;

        if (showLoading) {
            progress.setVisibility(View.VISIBLE);
        }

        int requestedLastId = 0;

        new Thread(() -> {
            try {
                String endpoint =
                        GET_CHAT_URL
                                + "?room_id="
                                + URLEncoder.encode(
                                roomId,
                                StandardCharsets.UTF_8.name()
                        )
;

                if (requestedLastId > 0) {
                    endpoint +=
                            "&last_id="
                                    + requestedLastId;
                }

                JSONObject response =
                        CustomerMessageApi.get(endpoint);

                mainHandler.post(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);

                    handleResponse(
                            response,
                            firstLoad
                    );
                });

            } catch (Exception error) {
                mainHandler.post(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);

                    statusText.setText(
                            "Koneksi chat bermasalah"
                    );
                });
            }
        }).start();
    }

    private void handleResponse(
            JSONObject response,
            boolean reset
    ) {
        orderStatus = response.optString(
                "status",
                orderStatus
        );

        boolean ended =
                response.optBoolean(
                        "ended",
                        false
                )
                        || CustomerMessageStatus
                        .isEnded(orderStatus);

        if (ended) {
            readOnly = true;
            applyReadOnlyState();

            mainHandler.removeCallbacks(
                    refreshRunnable
            );
        } else {
            statusText.setText(
                    CustomerMessageStatus
                            .orderLabel(
                                    orderStatus,
                                    orderType
                            )
            );
        }

        JSONObject driver =
                response.optJSONObject("driver");

        if (driver != null) {
            String serverName = first(
                    driver.optString("name"),
                    driver.optString("username"),
                    ""
            );

            if (!serverName.isEmpty()) {
                participantName = serverName;
                participantText.setText(serverName);
            }
        }

        if (
                !response.optBoolean(
                        "success",
                        false
                )
        ) {
            statusText.setText(
                    first(
                            response.optString(
                                    "message"
                            ),
                            "Gagal memuat chat"
                    )
            );

            return;
        }

        JSONArray array =
                response.optJSONArray(
                        "messages"
                );

        if (array == null) {
            return;
        }

        if (reset) {
            messagesBox.removeAllViews();
            receiptViews.clear();
        }

        boolean added = false;

        for (
                int i = 0;
                i < array.length();
                i++
        ) {
            JSONObject message =
                    array.optJSONObject(i);

            if (message == null) {
                continue;
            }

            int id = message.optInt("id", 0);

            if (!reset && id <= lastId) {
                updateReceipt(message);
                continue;
            }

            if (id > lastId) {
                lastId = id;
            }

            addBubble(message, !firstLoad);
            added = true;
        }

        if (
                reset
                        && array.length() == 0
        ) {
            addSystemMessage(
                    "Belum ada pesan pada percakapan ini."
            );
        }

        firstLoad = false;

        if (added || reset) {
            scrollBottom();
        }

        if (chatVisible && hasWindowFocus() && lastId > 0) {
            markMessagesReadThrough(lastId);
        }
    }

    private void markMessagesReadThrough(int readThroughId) {
        if (!chatVisible || !hasWindowFocus() || readThroughId <= 0 || destroyed) return;

        new Thread(() -> {
            try {
                // Beri waktu singkat agar pesan benar-benar sempat tampil di layar.
                Thread.sleep(350L);
                if (!chatVisible || !hasWindowFocus() || destroyed) return;

                String endpoint = GET_CHAT_URL
                        + "?room_id=" + URLEncoder.encode(roomId, StandardCharsets.UTF_8.name())
                        + "&viewer_type=customer"
                        + "&mark_read=1"
                        + "&read_through_id=" + readThroughId;
                CustomerMessageApi.get(endpoint);
            } catch (Exception ignored) {
                // Read receipt will be retried on the next visible refresh.
            }
        }, "chat-read-ack").start();
    }

    private void addBubble(JSONObject message, boolean animate) {
        String sender = CustomerMessageStatus.normalize(
                message.optString("sender_type", "")
        );
        boolean mine = sender.equals("customer");
        String content = message.optString("message", "");

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout.LayoutParams wrapperLp =
                new LinearLayout.LayoutParams(-1, -2);
        wrapperLp.setMargins(0, dp(4), 0, dp(4));
        messagesBox.addView(wrapper, wrapperLp);

        if (ChatVoiceNote.isVoice(content)) {
            wrapper.addView(
                    ChatVoiceNote.createPlayerBubble(this, absoluteVoiceContent(content), mine),
                    new LinearLayout.LayoutParams(-2, -2)
            );

        } else if (
                content.startsWith(IMAGE_V2_PREFIX)
                        || content.startsWith(IMAGE_PREFIX)
        ) {
            String previewUrl;
            String hdUrl;

            if (content.startsWith(IMAGE_V2_PREFIX)) {
                String value = content.substring(
                        IMAGE_V2_PREFIX.length()
                ).trim();

                String[] parts = value.split(
                        "\\|",
                        2
                );

                previewUrl =
                        parts.length > 0
                                ? parts[0].trim()
                                : "";

                hdUrl =
                        parts.length > 1
                                ? parts[1].trim()
                                : previewUrl;

            } else {
                previewUrl = content.substring(
                        IMAGE_PREFIX.length()
                ).trim();

                hdUrl = previewUrl;
            }

            ImageView image = new ImageView(this);

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            image.setBackground(
                    round("#EAF1FA", 16)
            );

            wrapper.addView(
                    image,
                    new LinearLayout.LayoutParams(
                            dp(220),
                            dp(165)
                    )
            );

            loadRemoteImage(
                    image,
                    previewUrl
            );

            TextView hdHint = text(
                    "Ketuk untuk lihat HD",
                    9,
                    "#0B7CFF",
                    true
            );

            hdHint.setPadding(
                    dp(7),
                    dp(3),
                    dp(7),
                    0
            );

            wrapper.addView(
                    hdHint,
                    new LinearLayout.LayoutParams(
                            -2,
                            -2
                    )
            );

            String finalHdUrl = hdUrl;

            image.setOnClickListener(
                    view -> showHdImage(
                            finalHdUrl
                    )
            );

            hdHint.setOnClickListener(
                    view -> showHdImage(
                            finalHdUrl
                    )
            );

        } else {
            TextView bubble = text(
                    content,
                    13,
                    mine ? "#FFFFFF" : "#0F172A",
                    false
            );
            bubble.setPadding(dp(13), dp(9), dp(13), dp(9));
            bubble.setMaxWidth((int)(
                    getResources().getDisplayMetrics().widthPixels * 0.75
            ));
            bubble.setBackground(
                    mine
                            ? gradient("#086BFF", "#2EA2FF", 17)
                            : roundStroke("#FFFFFF", "#D7E6F8", 17, 1)
            );
            wrapper.addView(
                    bubble,
                    new LinearLayout.LayoutParams(-2, -2)
            );
        }

        String time = formatTime(message.optString("created_at", ""));
        if (!time.isEmpty()) {
            String receipt = mine
                    ? (message.optString("read_at", "").trim().isEmpty()
                    ? "  ✓ Terkirim" : "  ✓✓ Dibaca")
                    : "";
            TextView timestamp = text(time + receipt, 9, "#94A3B8", false);
            timestamp.setPadding(dp(7), dp(2), dp(7), 0);
            wrapper.addView(
                    timestamp,
                    new LinearLayout.LayoutParams(-2, -2)
            );
            if (mine) receiptViews.put(message.optInt("id", 0), timestamp);
        }

        if (animate) animateMessage(wrapper, mine);
    }

    private void updateReceipt(JSONObject message) {
        int id = message.optInt("id", 0);
        TextView receipt = receiptViews.get(id);
        if (receipt == null) return;

        String sender = CustomerMessageStatus.normalize(message.optString("sender_type", ""));
        if (!"customer".equals(sender)) return;

        String time = formatTime(message.optString("created_at", ""));
        boolean read = !message.optString("read_at", "").trim().isEmpty();
        String next = time + (read ? "  ✓✓ Dibaca" : "  ✓ Terkirim");
        if (!next.contentEquals(receipt.getText())) {
            receipt.setText(next);
            receipt.setAlpha(0.35f);
            receipt.animate().alpha(1f).setDuration(220).start();
        }
    }

    private void animateMessage(View view, boolean mine) {
        view.setAlpha(0f);
        view.setTranslationX(dp(mine ? 18 : -18));
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        view.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void loadRemoteImage(ImageView target, String imageUrl) {
        final String fixed = absoluteUrl(imageUrl);

        new Thread(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)new URL(fixed).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setUseCaches(true);

                try (InputStream stream = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(stream);
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }

            Bitmap result = bitmap;
            mainHandler.post(() -> {
                if (result != null) {
                    target.setAlpha(0f);
                    target.setImageBitmap(result);
                    target.animate().alpha(1f).setDuration(180).start();
                } else target.setImageResource(
                        android.R.drawable.ic_menu_report_image
                );
            });
        }).start();
    }

    private void showHdImage(
            String imageUrl
    ) {
        final Dialog dialog =
                new Dialog(this);

        FrameLayout page =
                new FrameLayout(this);

        page.setBackgroundColor(Color.BLACK);

        ZoomableImageView zoomImage =
                new ZoomableImageView(this);

        zoomImage.setBackgroundColor(
                Color.BLACK
        );

        page.addView(
                zoomImage,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        ProgressBar loadingHd =
                new ProgressBar(this);

        FrameLayout.LayoutParams loadingLp =
                new FrameLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );

        loadingLp.gravity = Gravity.CENTER;

        page.addView(
                loadingHd,
                loadingLp
        );

        TextView hint = text(
                "Cubit untuk zoom • geser gambar",
                11,
                "#FFFFFF",
                true
        );

        hint.setGravity(Gravity.CENTER);
        hint.setPadding(
                dp(14),
                dp(9),
                dp(14),
                dp(9)
        );

        hint.setBackgroundColor(
                Color.argb(
                        135,
                        0,
                        0,
                        0
                )
        );

        FrameLayout.LayoutParams hintLp =
                new FrameLayout.LayoutParams(
                        -2,
                        -2
                );

        hintLp.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        hintLp.topMargin = dp(18);
        page.addView(hint, hintLp);

        TextView close = text(
                "✕",
                20,
                "#FFFFFF",
                true
        );

        close.setGravity(Gravity.CENTER);

        close.setBackground(
                round("#66000000", 22)
        );

        close.setOnClickListener(
                view -> dialog.dismiss()
        );

        FrameLayout.LayoutParams closeLp =
                new FrameLayout.LayoutParams(
                        dp(44),
                        dp(44)
                );

        closeLp.gravity =
                Gravity.TOP | Gravity.RIGHT;

        closeLp.topMargin = dp(14);
        closeLp.rightMargin = dp(14);

        page.addView(close, closeLp);

        dialog.setContentView(page);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(
                            Color.BLACK
                    )
            );

            window.setLayout(
                    -1,
                    -1
            );
        }

        dialog.setOnShowListener(ignored -> {
            Window shownWindow =
                    dialog.getWindow();

            if (shownWindow != null) {
                shownWindow.setLayout(
                        -1,
                        -1
                );
            }
        });

        dialog.show();

        final String fixed =
                absoluteUrl(imageUrl);

        new Thread(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;

            try {
                connection =
                        (HttpURLConnection)
                                new URL(fixed)
                                        .openConnection();

                connection.setConnectTimeout(25000);
                connection.setReadTimeout(45000);
                connection.setUseCaches(true);

                try (
                        InputStream stream =
                                connection.getInputStream()
                ) {
                    bitmap =
                            BitmapFactory.decodeStream(
                                    stream
                            );
                }

            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            Bitmap finalBitmap = bitmap;

            mainHandler.post(() -> {
                loadingHd.setVisibility(View.GONE);

                if (finalBitmap != null) {
                    zoomImage.setImageBitmap(
                            finalBitmap
                    );

                    mainHandler.postDelayed(
                            () -> hint.animate()
                                    .alpha(0f)
                                    .setDuration(350)
                                    .start(),
                            1800
                    );

                } else {
                    zoomImage.setImageResource(
                            android.R.drawable
                                    .ic_menu_report_image
                    );

                    toast(
                            "Gambar HD tidak dapat dimuat"
                    );
                }
            });
        }).start();
    }

    private void addSystemMessage(
            String value
    ) {
        TextView message = text(
                value,
                10,
                "#718096",
                false
        );

        message.setGravity(Gravity.CENTER);
        message.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(8)
        );

        message.setBackground(
                round("#EAF1FA", 13)
        );

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        lp.gravity = Gravity.CENTER;
        lp.setMargins(
                0,
                dp(10),
                0,
                dp(10)
        );

        messagesBox.addView(message, lp);
    }

    private void sendMessage() {
        if (readOnly || sending) return;

        String message = input.getText().toString().trim();
        if (message.isEmpty()) return;

        if (message.length() > 1000) {
            showMessage(
                    "Pesan terlalu panjang",
                    "Maksimal 1000 karakter.",
                    false
            );
            return;
        }

        sending = true;
        sendButton.setEnabled(false);
        sendButton.setText("...");
        final String originalMessage = message;
        final PendingText pending = addPendingText(originalMessage);
        input.setText("");

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("room_id", roomId);
                payload.put("sender_type", "customer");
                payload.put("message", originalMessage);
                payload.put("order_id", orderId);

                JSONObject response = CustomerMessageApi.post(
                        SEND_CHAT_URL,
                        payload
                );

                mainHandler.post(() -> {
                    if (response.optBoolean("success", false)) {
                        sending = false;
                        sendButton.setEnabled(true);
                        sendButton.setText("Kirim");
                        if (pending != null) messagesBox.removeView(pending.root);
                        loadMessages(false);
                    } else {
                        if (pending != null) pending.markNetworkPending();
                        verifyDelivered(originalMessage);
                    }
                });

            } catch (Exception error) {
                mainHandler.post(() -> { if (pending != null) pending.markNetworkPending(); verifyDelivered(originalMessage); });
            }
        }).start();
    }

    private void verifyDelivered(String originalMessage) {
        new Thread(() -> {
            boolean delivered = false;

            try {
                String endpoint = GET_CHAT_URL
                        + "?room_id="
                        + URLEncoder.encode(
                                roomId,
                                StandardCharsets.UTF_8.name()
                        );

                JSONObject response = CustomerMessageApi.get(endpoint);
                JSONArray array = response.optJSONArray("messages");

                if (array != null) {
                    for (
                            int i = array.length() - 1;
                            i >= 0 && i >= array.length() - 12;
                            i--
                    ) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) continue;

                        if (
                                "customer".equalsIgnoreCase(
                                        item.optString("sender_type", "")
                                )
                                        && originalMessage.equals(
                                        item.optString("message", "")
                                )
                        ) {
                            delivered = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            final boolean found = delivered;
            mainHandler.post(() -> {
                sending = false;
                sendButton.setEnabled(true);
                sendButton.setText("Kirim");

                if (found) {
                    input.setText("");
                    loadMessages(false);
                } else {
                    showMessage(
                            "Pesan belum terkirim",
                            "Koneksi bermasalah. Coba lagi.",
                            false
                    );
                }
            });
        }).start();
    }

    private void scrollBottom() {
        mainHandler.postDelayed(
                () -> {
                    try {
                        messagesScroll.fullScroll(
                                View.FOCUS_DOWN
                        );
                    } catch (Exception ignored) {
                    }
                },
                120
        );
    }

    private String normalizeRoom(String value) {
        String room =
                value == null
                        ? ""
                        : value.trim()
                        .replace('_', '-')
                        .toUpperCase(Locale.US);

        room = room.replaceAll(
                "[^A-Z0-9\\-]",
                ""
        );

        if (
                !room.isEmpty()
                        && !room.startsWith("ROOM-")
        ) {
            room = "ROOM-" + room;
        }

        return room;
    }

    private String serviceName(String type) {
        type = CustomerMessageStatus.normalize(type);

        if (type.contains("food")) {
            return "TransFood";
        }

        if (
                type.contains("car")
                        || type.contains("mobil")
        ) {
            return "TransCar";
        }

        return "TransRide";
    }

    private String formatTime(String value) {
        if (value == null || value.trim().isEmpty()) {
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
                        ).parse(value.trim());

                if (date != null) {
                    return new SimpleDateFormat(
                            "dd MMM • HH:mm",
                            new Locale("id", "ID")
                    ).format(date);
                }

            } catch (Exception ignored) {
            }
        }

        return value;
    }

    private String absoluteUrl(String value) {
        String path = value == null
                ? ""
                : value.trim().replace("\\", "/");

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }

        if (path.startsWith("/")) {
            return "https://transiva.my.id" + path;
        }

        return BASE_URL + path;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setBackground(
                gradient(
                        "#086BFF",
                        "#2EA2FF",
                        13
                )
        );

        return button;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
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

        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(radius));

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

        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
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
            ) {
                return value.trim();
            }
        }

        return "";
    }

    private void showMessage(
            String title,
            String message,
            boolean finishAfter
    ) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> {
                            if (finishAfter) {
                                finish();
                            }
                        }
                )
                .show();
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        chatVisible = true;
        CustomerAppSettings.apply(this);

        CustomerChatNotificationPoller.setOpenRoom(roomId);
        mainHandler.removeCallbacks(refreshRunnable);
        if (!readOnly) {
            loadMessages(false);
            mainHandler.postDelayed(refreshRunnable, REFRESH_MS);
        }
    }

    @Override
    protected void onPause() {
        chatVisible = false;
        mainHandler.removeCallbacks(refreshRunnable);
        CustomerChatNotificationPoller.clearOpenRoom(roomId);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;

        mainHandler.removeCallbacks(
                refreshRunnable
        );

        super.onDestroy();
    }
}
