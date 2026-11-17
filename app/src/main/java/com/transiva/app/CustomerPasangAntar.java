package com.transiva.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An in-Activity game window keeps the existing order poller running. */
final class CustomerPasangAntar {
    private static final String HOST = "pasangantar.transiva.local";
    private static final String GAME_URL = "https://" + HOST + "/index.html";
    private static final int SURFACE = 0xff103339;
    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final Pattern CHROME_VERSION = Pattern.compile("Chrome/(\\d+)");

    private final Activity host;
    private final Dialog dialog;
    private final FrameLayout main;
    private final CustomerGameOrderHeader header;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final File directory;
    private WebView web;
    private boolean closed, foreground = true;

    CustomerPasangAntar(Activity host, String state, String label) {
        this.host = host;
        directory = new File(host.getNoBackupFilesDir(), "transiva-game-packs");
        dialog = new Dialog(host, android.R.style.Theme_Material_NoActionBar);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> dispose());

        LinearLayout shell = new LinearLayout(host);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(SURFACE);
        header = new CustomerGameOrderHeader(host, this::close);
        header.bind(state, label);
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
        main = new FrameLayout(host);
        shell.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        dialog.setContentView(shell);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            CustomerWindowPolicy.install(window);
            CustomerWindowPolicy.setBarColorsIfChanged(window, 0xff102e52, SURFACE);
        }
        setForeground(true);
        locatePackage();
    }

    static Button launcher(Context context, Runnable open) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText("Main Pasang & Antar");
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 48));
        button.setBackground(surface(context, 0xff285bc3, 0xff287bba, 18));
        button.setOnClickListener(v -> open.run());
        return button;
    }

    static String label(String raw, String orderType) {
        return OrderStatusPresentation.label(raw, orderType == null ? "" : orderType);
    }

    boolean isShowing() { return !closed && dialog.isShowing(); }
    void bind(String state, String label) { if (isShowing()) header.bind(state, label); }
    void reconnecting() { if (isShowing()) header.reconnecting(); }

    void setForeground(boolean active) {
        if (closed) return;
        foreground = active;
        header.setForeground(active);
        if (web != null) {
            if (active) web.onResume();
            web.evaluateJavascript("window.pasangAntarForeground && window.pasangAntarForeground("
                    + active + ")", null);
            if (!active) web.onPause();
        }
    }

    void close() {
        if (closed) return;
        dialog.dismiss();
        dispose();
    }

    private void dispose() {
        if (closed) return;
        closed = true;
        header.setForeground(false);
        releaseWeb();
    }

    private void releaseWeb() {
        if (web == null) return;
        web.onPause();
        main.removeView(web);
        web.destroy();
        web = null;
    }

    private void locatePackage() {
        File packageFile = new File(directory, PasangAntarPack.FILE_NAME);
        if (!packageFile.isFile()) {
            downloadOffer(false);
            return;
        }
        panel("Menyiapkan game", "Memeriksa paket yang tersimpan di perangkat.");
        IO.execute(() -> {
            boolean verified = PasangAntarPack.isVerified(packageFile);
            ui.post(() -> {
                if (!isShowing()) return;
                if (verified) showGame(packageFile); else downloadOffer(true);
            });
        });
    }

    private void downloadOffer(boolean retry) {
        LinearLayout content = panel("Pasang & Antar", retry
                ? "Paket belum bisa dibuka. Unduh ulang untuk memperbaikinya."
                : "Unduh paket 1,27 MB sekali. Setelah itu, 20 level bisa dimainkan tanpa internet.");
        Button download = launcher(host, this::download);
        download.setText(retry ? "Unduh ulang" : "Unduh game");
        content.addView(download, spaced());
    }

    private void download() {
        LinearLayout content = panel("Mengunduh game", "Kamu bisa kembali ke pesanan selama unduhan.");
        ProgressBar bar = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        content.addView(bar, spaced());
        TextView percent = text("0%", 13, 0xffbce3ff);
        percent.setGravity(Gravity.CENTER);
        content.addView(percent, spaced());
        IO.execute(() -> {
            try {
                File file = PasangAntarPack.fetch(directory, value -> ui.post(() -> {
                    if (!isShowing()) return;
                    bar.setProgress(value);
                    percent.setText(value + "%");
                }));
                ui.post(() -> { if (isShowing()) showGame(file); });
            } catch (IOException | RuntimeException error) {
                ui.post(() -> { if (isShowing()) downloadOffer(true); });
            }
        });
    }

    private void showGame(File pack) {
        WebView view;
        try {
            view = new WebView(host);
        } catch (RuntimeException unavailable) {
            panel("Game belum bisa dibuka", "Perbarui Android System WebView, lalu coba lagi.");
            return;
        }
        WebSettings settings = view.getSettings();
        Matcher agent = CHROME_VERSION.matcher(settings.getUserAgentString());
        if (agent.find() && Integer.parseInt(agent.group(1)) < 58) {
            view.destroy();
            panel("WebView perlu diperbarui", "Perbarui Android System WebView agar papan bisa ditampilkan.");
            return;
        }
        view.setBackgroundColor(SURFACE);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        view.setWebViewClient(new WebViewClient() {
            @android.annotation.TargetApi(26)
            @Override public boolean onRenderProcessGone(WebView source, android.webkit.RenderProcessGoneDetail detail) {
                if (web == source) {
                    releaseWeb();
                    if (isShowing()) {
                        LinearLayout content = panel("Game terhenti", "Buka kembali game dari paket yang sudah tersimpan.");
                        Button retry = launcher(host, () -> showGame(pack));
                        retry.setText("Buka kembali");
                        content.addView(retry, spaced());
                    }
                }
                return true;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView source, WebResourceRequest request) {
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView source, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getAuthority())
                        || !"GET".equals(request.getMethod())) return notFound();
                String raw = uri.getEncodedPath();
                if (raw == null || raw.length() < 2) return notFound();
                String path = raw.substring(1);
                String mime;
                if (path.endsWith(".html")) mime = "text/html";
                else if (path.endsWith(".css")) mime = "text/css";
                else if (path.endsWith(".js")) mime = "text/javascript";
                else if (path.endsWith(".webp")) mime = "image/webp";
                else return notFound();
                try {
                    return new WebResourceResponse(mime, "image/webp".equals(mime) ? null : "UTF-8",
                            PasangAntarPack.open(pack, path));
                } catch (IOException error) {
                    return notFound();
                }
            }

            @Override public void onPageFinished(WebView source, String url) {
                if (web == source && isShowing() && !foreground) setForeground(false);
            }

            @Override public void onReceivedError(WebView source, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && web == source && isShowing()) {
                    releaseWeb();
                    downloadOffer(true);
                }
            }

            @Override public void onReceivedHttpError(WebView source, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && web == source && isShowing()) {
                    releaseWeb();
                    downloadOffer(true);
                }
            }
        });
        main.removeAllViews();
        web = view;
        main.addView(view, new FrameLayout.LayoutParams(-1, -1));
        view.loadUrl(GAME_URL);
        if (!foreground) view.onPause();
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private LinearLayout panel(String title, String description) {
        main.removeAllViews();
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(host, 19), dp(host, 19), dp(host, 19), dp(host, 19));
        box.setBackground(surface(host, 0xff1b536a, 0xff194c53, 22));
        TextView name = text(title, 23, 0xfff9e9b9);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(name);
        box.addView(text(description, 14, 0xffe3f5f4), spaced());
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        layout.leftMargin = layout.rightMargin = dp(host, 18);
        main.addView(box, layout);
        return box;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(host, 10);
        return layout;
    }

    private static GradientDrawable surface(Context context, int from, int to, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from,to});
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
