package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatCameraActivity extends ComponentActivity {

    private static final String STATE_FLASH_MODE = "camera_flash_mode";

    private static final int FLASH_OFF = 0;
    private static final int FLASH_AUTO = 1;
    private static final int FLASH_ON = 2;
    private static final int FLASH_TORCH = 3;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private Button captureButton;
    private Button flashButton;
    private boolean capturing;
    private boolean flashAvailable;
    private int flashMode = FLASH_AUTO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            flashMode = savedInstanceState.getInt(STATE_FLASH_MODE, FLASH_AUTO);
        }

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        cameraExecutor = Executors.newSingleThreadExecutor();

        setContentView(buildScreen());
        startCamera();
    }

    private FrameLayout buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(-1, -1));

        flashButton = new Button(this);
        flashButton.setAllCaps(false);
        flashButton.setText("⚡ Auto");
        flashButton.setTextColor(Color.WHITE);
        flashButton.setBackgroundColor(0x66000000);
        flashButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        flashButton.setOnClickListener(this::showFlashMenu);

        FrameLayout.LayoutParams flashLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        flashLp.gravity = Gravity.TOP | Gravity.END;
        flashLp.topMargin = dp(18);
        flashLp.rightMargin = dp(16);
        root.addView(flashButton, flashLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(20), dp(18), dp(20), dp(26));

        captureButton = new Button(this);
        captureButton.setText("Ambil Foto");
        captureButton.setAllCaps(false);
        captureButton.setOnClickListener(view -> takePhoto());

        controls.addView(
                captureButton,
                new LinearLayout.LayoutParams(-1, -2)
        );

        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(-1, -2);
        controlsLp.gravity = Gravity.BOTTOM;
        root.addView(controls, controlsLp);

        return root;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(this);

        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(95)
                        .build();

                provider.unbindAll();

                camera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );

                flashAvailable = camera.getCameraInfo().hasFlashUnit();
                if (!flashAvailable) {
                    flashMode = FLASH_OFF;
                    flashButton.setEnabled(false);
                    flashButton.setText("⚡ Tidak tersedia");
                    flashButton.setAlpha(0.6f);
                } else {
                    flashButton.setEnabled(true);
                    flashButton.setAlpha(1f);
                    applyFlashMode(false);
                }

            } catch (Exception error) {
                toast("Kamera gagal dibuka: " + error.getMessage());
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showFlashMenu(android.view.View anchor) {
        if (!flashAvailable || imageCapture == null || camera == null) {
            toast("Flash tidak tersedia pada kamera ini");
            return;
        }

        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, FLASH_TORCH, 0, "🔦 Senter");
        menu.getMenu().add(0, FLASH_AUTO, 1, "⚡ Auto");
        menu.getMenu().add(0, FLASH_ON, 2, "⚡ Aktif");
        menu.getMenu().add(0, FLASH_OFF, 3, "⚡ Nonaktif");

        menu.setOnMenuItemClickListener(item -> {
            flashMode = item.getItemId();
            applyFlashMode(true);
            return true;
        });
        menu.show();
    }

    private void applyFlashMode(boolean showToast) {
        if (imageCapture == null || camera == null || !flashAvailable) {
            return;
        }

        // Torch dan flash foto tidak boleh aktif bersamaan.
        camera.getCameraControl().enableTorch(false);

        String label;
        String message;

        switch (flashMode) {
            case FLASH_AUTO:
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
                label = "⚡ Auto";
                message = "Flash otomatis";
                break;

            case FLASH_ON:
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_ON);
                label = "⚡ Aktif";
                message = "Flash aktif saat mengambil foto";
                break;

            case FLASH_TORCH:
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
                camera.getCameraControl().enableTorch(true);
                label = "🔦 Senter";
                message = "Mode senter aktif";
                break;

            case FLASH_OFF:
            default:
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
                label = "⚡ Nonaktif";
                message = "Flash nonaktif";
                break;
        }

        flashButton.setText(label);

        if (showToast) {
            toast(message);
        }
    }

    private void takePhoto() {
        if (capturing || imageCapture == null) {
            return;
        }

        capturing = true;
        captureButton.setEnabled(false);
        captureButton.setText("Memproses…");

        File directory = new File(getCacheDir(), "chat_camera_full");

        if (!directory.exists() && !directory.mkdirs()) {
            capturing = false;
            captureButton.setEnabled(true);
            captureButton.setText("Ambil Foto");
            toast("Folder kamera gagal dibuat");
            return;
        }

        File output = new File(
                directory,
                "chat_" + System.currentTimeMillis() + ".jpg"
        );

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(output).build();

        imageCapture.takePicture(
                options,
                cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults outputFileResults
                    ) {
                        runOnUiThread(() -> {
                            Intent result = new Intent();
                            result.putExtra("photo_path", output.getAbsolutePath());
                            setResult(RESULT_OK, result);
                            finish();
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            capturing = false;
                            captureButton.setEnabled(true);
                            captureButton.setText("Ambil Foto");
                            toast("Foto gagal: " + exception.getMessage());
                        });
                    }
                }
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera != null && imageCapture != null && flashAvailable) {
            applyFlashMode(false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_FLASH_MODE, flashMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        if (camera != null) {
            camera.getCameraControl().enableTorch(false);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (camera != null) {
            camera.getCameraControl().enableTorch(false);
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        super.onDestroy();
    }
}
