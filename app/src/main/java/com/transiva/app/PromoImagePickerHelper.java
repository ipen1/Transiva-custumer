package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

public final class PromoImagePickerHelper {

    public static final int REQUEST_CODE = 8101;

    private static final String UPLOAD_URL =
            "https://transiva.my.id/server/upload_promo_image.php";

    private static final int TARGET_WIDTH = 1100;
    private static final int TARGET_HEIGHT = 480;
    private static final int MAX_UPLOAD_BYTES = 3 * 1024 * 1024;

    private final Activity activity;

    private ImageView preview;
    private TextView placeholder;
    private TextView status;
    private Button chooseButton;
    private Button removeButton;
    private ProgressBar progress;

    private Bitmap currentBitmap;
    private String imageUrl = "";
    private boolean busy;

    public PromoImagePickerHelper(Activity activity) {
        this.activity = activity;
    }

    public View buildView() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setBackground(roundStroke("#F9FBFF", "#D7E6F8", 15));

        FrameLayout previewFrame = new FrameLayout(activity);
        previewFrame.setBackground(roundStroke("#EAF4FF", "#C5DDF6", 13));

        preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        placeholder = text(
                "🖼\nBelum ada foto banner",
                13,
                "#7890AA",
                true
        );
        placeholder.setGravity(Gravity.CENTER);
        previewFrame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp =
                new FrameLayout.LayoutParams(dp(38), dp(38));
        progressLp.gravity = Gravity.CENTER;
        previewFrame.addView(progress, progressLp);

        previewFrame.setOnClickListener(v -> openGallery());
        box.addView(previewFrame, new LinearLayout.LayoutParams(-1, dp(145)));

        status = text(
                "Foto otomatis dipotong ke rasio 1100 × 480",
                10,
                "#64748B",
                false
        );
        status.setPadding(0, dp(7), 0, dp(7));
        box.addView(status);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        chooseButton = primaryButton("Pilih dari Galeri");
        chooseButton.setOnClickListener(v -> openGallery());
        actions.addView(
                chooseButton,
                new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        removeButton = outlineButton("Hapus Foto");
        removeButton.setOnClickListener(v -> clear(true));
        setRemoveEnabled(false);

        LinearLayout.LayoutParams removeLp =
                new LinearLayout.LayoutParams(0, dp(44), 1);
        removeLp.setMargins(dp(7), 0, 0, 0);
        actions.addView(removeButton, removeLp);

        box.addView(actions);
        return box;
    }

    public void openGallery() {
        if (busy) return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");

        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    public boolean handleActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        if (requestCode != REQUEST_CODE) return false;

        if (
                resultCode != Activity.RESULT_OK
                        || data == null
                        || data.getData() == null
        ) {
            return true;
        }

        Uri uri = data.getData();

        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        processAndUpload(uri);
        return true;
    }

    public String getImageUrl() {
        return imageUrl == null ? "" : imageUrl.trim();
    }

    public void setExistingUrl(String url) {
        clearBitmap();

        imageUrl = url == null ? "" : url.trim();

        if (imageUrl.isEmpty()) {
            preview.setImageDrawable(null);
            placeholder.setText("🖼\nBelum ada foto banner");
            placeholder.setVisibility(View.VISIBLE);
            status.setText("Belum ada foto banner");
            setRemoveEnabled(false);
        } else {
            preview.setImageDrawable(null);
            placeholder.setText("🖼\nFoto lama tersimpan");
            placeholder.setVisibility(View.VISIBLE);
            status.setText("Pilih foto baru untuk mengganti banner lama");
            setRemoveEnabled(true);
        }
    }

    public void clear(boolean showToast) {
        imageUrl = "";
        clearBitmap();

        if (preview != null) preview.setImageDrawable(null);
        if (placeholder != null) {
            placeholder.setText("🖼\nBelum ada foto banner");
            placeholder.setVisibility(View.VISIBLE);
        }
        if (status != null) {
            status.setText("Foto otomatis dipotong ke rasio 1100 × 480");
        }

        setRemoveEnabled(false);

        if (showToast) {
            Toast.makeText(
                    activity,
                    "Foto dihapus dari form. Simpan promo untuk menerapkan.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    public void destroy() {
        clearBitmap();
    }

    private void processAndUpload(Uri uri) {
        if (busy) return;

        busy = true;
        setBusyUi(true);
        status.setText("Memproses dan memotong foto...");

        new Thread(() -> {
            Bitmap source = null;
            Bitmap cropped = null;

            try {
                source = decodeBitmap(uri);

                if (source == null) {
                    throw new IllegalStateException("Foto tidak dapat dibaca");
                }

                cropped = centerCrop(
                        source,
                        TARGET_WIDTH,
                        TARGET_HEIGHT
                );

                byte[] jpeg = compress(cropped, 88);

                if (jpeg.length > MAX_UPLOAD_BYTES) {
                    jpeg = compress(cropped, 74);
                }

                if (jpeg.length > MAX_UPLOAD_BYTES) {
                    throw new IllegalStateException(
                            "Ukuran foto terlalu besar setelah dikompres"
                    );
                }

                JSONObject response = upload(jpeg);

                if (!response.optBoolean("success", false)) {
                    throw new IllegalStateException(
                            response.optString("message", "Upload gagal")
                    );
                }

                String remoteUrl =
                        response.optString("image_url", "").trim();

                if (remoteUrl.isEmpty()) {
                    throw new IllegalStateException(
                            "Server tidak mengirim URL gambar"
                    );
                }

                Bitmap finalBitmap = cropped;
                cropped = null;
                int finalBytes = jpeg.length;

                activity.runOnUiThread(() -> {
                    clearBitmap();
                    currentBitmap = finalBitmap;
                    imageUrl = remoteUrl;

                    preview.setImageBitmap(currentBitmap);
                    placeholder.setVisibility(View.GONE);
                    status.setText(
                            "Foto siap • 1100 × 480 • "
                                    + String.format(
                                    Locale.US,
                                    "%.1f KB",
                                    finalBytes / 1024f
                            )
                    );

                    setRemoveEnabled(true);
                    busy = false;
                    setBusyUi(false);

                    Toast.makeText(
                            activity,
                            "Foto banner berhasil diunggah",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception error) {
                if (cropped != null && !cropped.isRecycled()) {
                    cropped.recycle();
                }

                activity.runOnUiThread(() -> {
                    busy = false;
                    setBusyUi(false);
                    status.setText("Gagal memproses foto");

                    Toast.makeText(
                            activity,
                            "Gagal memilih foto: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {
                if (source != null && !source.isRecycled()) {
                    source.recycle();
                }
            }
        }).start();
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source =
                    ImageDecoder.createSource(
                            activity.getContentResolver(),
                            uri
                    );

            return ImageDecoder.decodeBitmap(
                    source,
                    (decoder, info, src) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);

                        int width = info.getSize().getWidth();
                        int height = info.getSize().getHeight();
                        int max = Math.max(width, height);

                        if (max > 2400) {
                            float scale = 2400f / max;

                            decoder.setTargetSize(
                                    Math.max(1, Math.round(width * scale)),
                                    Math.max(1, Math.round(height * scale))
                            );
                        }
                    }
            );
        }

        InputStream raw =
                activity.getContentResolver().openInputStream(uri);

        if (raw == null) {
            throw new IllegalStateException("File gambar tidak ditemukan");
        }

        BufferedInputStream input = new BufferedInputStream(raw);
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        input.close();

        return bitmap;
    }

    private Bitmap centerCrop(
            Bitmap source,
            int targetWidth,
            int targetHeight
    ) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        float sourceRatio = (float) sourceWidth / sourceHeight;
        float targetRatio = (float) targetWidth / targetHeight;

        int cropWidth;
        int cropHeight;
        int x;
        int y;

        if (sourceRatio > targetRatio) {
            cropHeight = sourceHeight;
            cropWidth = Math.round(cropHeight * targetRatio);
            x = Math.max(0, (sourceWidth - cropWidth) / 2);
            y = 0;
        } else {
            cropWidth = sourceWidth;
            cropHeight = Math.round(cropWidth / targetRatio);
            x = 0;
            y = Math.max(0, (sourceHeight - cropHeight) / 2);
        }

        cropWidth = Math.min(cropWidth, sourceWidth - x);
        cropHeight = Math.min(cropHeight, sourceHeight - y);

        Bitmap crop = Bitmap.createBitmap(
                source,
                x,
                y,
                cropWidth,
                cropHeight
        );

        Bitmap scaled = Bitmap.createScaledBitmap(
                crop,
                targetWidth,
                targetHeight,
                true
        );

        if (crop != source && crop != scaled && !crop.isRecycled()) {
            crop.recycle();
        }

        return scaled;
    }

    private byte[] compress(Bitmap bitmap, int quality) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw new IllegalStateException("Kompresi gambar gagal");
        }

        return output.toByteArray();
    }

    private JSONObject upload(byte[] jpeg) throws Exception {
        String boundary = "----Transiva" + UUID.randomUUID();
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(UPLOAD_URL).openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(25000);
            connection.setReadTimeout(45000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary
            );
            connection.setRequestProperty("Accept", "application/json");

            DataOutputStream output =
                    new DataOutputStream(connection.getOutputStream());

            output.writeBytes("--" + boundary + "\r\n");
            output.writeBytes(
                    "Content-Disposition: form-data; "
                            + "name=\"banner\"; "
                            + "filename=\"promo_banner.jpg\"\r\n"
            );
            output.writeBytes("Content-Type: image/jpeg\r\n\r\n");
            output.write(jpeg);
            output.writeBytes("\r\n");
            output.writeBytes("--" + boundary + "--\r\n");
            output.flush();
            output.close();

            int statusCode = connection.getResponseCode();

            InputStream stream =
                    statusCode >= 200 && statusCode < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (stream == null) {
                throw new IllegalStateException(
                        "Server tidak mengirim respons"
                );
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(stream, "UTF-8")
                    );

            StringBuilder raw = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }

            reader.close();

            JSONObject response =
                    new JSONObject(
                            raw.length() == 0 ? "{}" : raw.toString()
                    );

            if (statusCode < 200 || statusCode >= 400) {
                throw new IllegalStateException(
                        response.optString(
                                "message",
                                "HTTP " + statusCode
                        )
                );
            }

            return response;

        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void setBusyUi(boolean value) {
        if (progress != null) {
            progress.setVisibility(value ? View.VISIBLE : View.GONE);
        }

        if (chooseButton != null) {
            chooseButton.setEnabled(!value);
            chooseButton.setAlpha(value ? 0.6f : 1f);
        }

        if (removeButton != null) {
            removeButton.setEnabled(!value && !getImageUrl().isEmpty());
        }
    }

    private void setRemoveEnabled(boolean enabled) {
        if (removeButton == null) return;

        removeButton.setEnabled(enabled);
        removeButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void clearBitmap() {
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }

        currentBitmap = null;
    }

    private Button primaryButton(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable background =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor("#086BFF"),
                                Color.parseColor("#2EA2FF")
                        }
                );

        background.setCornerRadius(dp(13));
        button.setBackground(background);

        return button;
    }

    private Button outlineButton(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.parseColor("#0B7CFF"));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(
                roundStroke("#FFFFFF", "#B9DBFF", 13)
        );

        return button;
    }

    private TextView text(
            String value,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);

        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable roundStroke(
            String fill,
            String stroke,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(
                value
                        * activity.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
