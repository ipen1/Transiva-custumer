package com.transiva.app;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;

/** Premium Trans Asisten 2.0 UI. All mascot animation is bundled locally: no API key/network needed. */
public class TransAssistantActivity extends Activity {
    private static final int BLUE = Color.rgb(7, 94, 244);
    private static final int CYAN = Color.rgb(0, 210, 255);
    private static final int NAVY = Color.rgb(5, 25, 55);

    private LinearLayout messages;
    private ScrollView messageScroll;
    private EditText input;
    private TransAssistantEngine engine;
    private LottieAnimationView mascot;
    private TextView stateText;
    private TextView robotMode;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        w.setStatusBarColor(NAVY);
        w.setNavigationBarColor(Color.WHITE);
        engine = new TransAssistantEngine(this);
        TransAssistantSync.sync(this);
        setContentView(build());
        setState("IDLE", 0, 59, true);
        addBot("Halo! Saya Trans Asisten 2.0. Ceritakan kebutuhan Anda—misalnya mau pesan barang, pulang kantor, lapar, cari tempat, atau cek pesanan.", "", "");

        // Dashboard can hand a suggested phrase directly to the assistant.
        String dashboardPrompt = getIntent() == null ? "" : getIntent().getStringExtra("ai_prompt");
        if (dashboardPrompt != null && !dashboardPrompt.trim().isEmpty()) {
            final String p = dashboardPrompt.trim();
            handler.postDelayed(() -> {
                if (isFinishing() || isDestroyed() || input == null) return;
                input.setText(p);
                input.setSelection(input.length());
            }, 180L);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (mascot != null) mascot.cancelAnimation();
        super.onDestroy();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 250, 255));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(10), dp(16), dp(12));
        hero.setBackground(gradient("#061A39", "#075EF4", 0));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 36, "#FFFFFF", false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Kembali");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(44)));
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("Trans Asisten 2.0", 20, "#FFFFFF", true));
        titleBox.addView(text("Asisten pintar Transiva • aktif 24/7", 11, "#CDEBFF", false));
        bar.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = text("●  LOKAL", 10, "#BDF7FF", true);
        local.setPadding(dp(10), dp(6), dp(10), dp(6));
        local.setBackground(round("#1836A5D9", "#6AE8FF", 20, 1));
        bar.addView(local);
        hero.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout mascotStage = new FrameLayout(this);
        mascot = new LottieAnimationView(this);
        mascot.setAnimation("trans_assistant_premium.json");
        mascot.setImageAssetsFolder("images/");
        mascot.setRenderMode(com.airbnb.lottie.RenderMode.HARDWARE);
        mascot.setContentDescription("Robot Trans Asisten bergerak dan berubah mode");
        mascotStage.addView(mascot, new FrameLayout.LayoutParams(-1, dp(205)));
        robotMode = text("👋  SIAP", 10, "#FFFFFF", true);
        robotMode.setGravity(Gravity.CENTER); robotMode.setPadding(dp(10),dp(6),dp(10),dp(6));
        robotMode.setBackground(round("#3320D8FF","#7BEAFF",18,1));
        FrameLayout.LayoutParams rmp = new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT); rmp.setMargins(0,0,dp(8),dp(8));
        mascotStage.addView(robotMode,rmp);
        hero.addView(mascotStage, new LinearLayout.LayoutParams(-1, dp(205)));

        stateText = text("SIAP MEMBANTU", 11, "#D8F7FF", true);
        stateText.setGravity(Gravity.CENTER);
        stateText.setLetterSpacing(.12f);
        hero.addView(stateText, new LinearLayout.LayoutParams(-1, -2));
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(12), dp(10), dp(12), dp(6));
        String[] prompts = {"📦 Mau pesan barang", "🏍 Pulang kantor", "🍜 Saya lapar", "📍 Cari tempat", "🧾 Cek pesanan"};
        for (String p : prompts) {
            TextView c = text(p, 12, "#0B4E9B", true);
            c.setPadding(dp(12), dp(9), dp(12), dp(9));
            c.setBackground(round("#FFFFFF", "#CDE4FF", 18, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(0,0,dp(7),0);
            chips.addView(c, lp);
            c.setOnClickListener(v -> { input.setText(p.substring(p.indexOf(' ') + 1)); input.setSelection(input.length()); send(); });
        }
        chipsScroll.addView(chips);
        root.addView(chipsScroll, new LinearLayout.LayoutParams(-1, -2));

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(4), dp(12), dp(12));
        messageScroll.addView(messages);
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composerWrap = new LinearLayout(this);
        composerWrap.setGravity(Gravity.CENTER_VERTICAL);
        composerWrap.setPadding(dp(10), dp(8), dp(10), dp(10));
        composerWrap.setBackgroundColor(Color.WHITE);

        input = new EditText(this);
        input.setHint("Tanya Trans Asisten...");
        input.setTextSize(14);
        input.setTextColor(Color.parseColor("#17324D"));
        input.setHintTextColor(Color.parseColor("#8296AD"));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(round("#F3F8FF", "#CFE3F8", 22, 1));
        input.setSingleLine(false); input.setMaxLines(3); input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnFocusChangeListener((v,focused)->{ if(focused && input.getText().length()==0) setState("MENDENGARKAN",60,119,true); else if(!focused) setState("SIAP MEMBANTU",0,59,true); });
        input.setOnEditorActionListener((v, action, e) -> { if (action == EditorInfo.IME_ACTION_SEND) { send(); return true; } return false; });
        composerWrap.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        TextView send = text("➤", 24, "#FFFFFF", true);
        send.setGravity(Gravity.CENTER); send.setContentDescription("Kirim pertanyaan");
        send.setBackground(round("#0878F9", null, 24, 0));
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(48), dp(48)); sp.setMargins(dp(8),0,0,0);
        composerWrap.addView(send, sp);
        root.addView(composerWrap, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void send() {
        final String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        addUser(q); input.setText("");
        setState("MEMIKIRKAN...", 120, 179, true);
        // Short visual thinking phase keeps the local engine responsive while making state changes legible.
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            TransAssistantEngine.Reply r = engine.answer(q);
            setState("MENJAWAB", 180, 239, true);
            addBot(r.text, r.action, r.actionLabel);
            handler.postDelayed(() -> { if (!isFinishing() && !isDestroyed()) setState("SELESAI", 240, 299, false); }, 520);
            handler.postDelayed(() -> { if (!isFinishing() && !isDestroyed()) setState("SIAP MEMBANTU", 0, 59, true); }, 1450);
        }, 420);
    }

    private void setState(String label, int min, int max, boolean loop) {
        if (stateText != null) stateText.setText(label);
        if (robotMode != null) {
            String mode = "👋  SIAP"; String fill="#3320D8FF";
            if(label.contains("MENDENGAR")){mode="🎧  MENDENGAR";fill="#334BE6FF";}
            else if(label.contains("MEMIKIRKAN")){mode="•••  BERPIKIR";fill="#335B6CFF";}
            else if(label.contains("MENJAWAB")){mode="▂▄▆  BICARA";fill="#3337D67A";}
            else if(label.contains("SELESAI")){mode="✓  SELESAI";fill="#3334C759";}
            robotMode.setText(mode); robotMode.setBackground(round(fill,"#7BEAFF",18,1));
        }
        if (mascot == null) return;
        mascot.animate().cancel();
        float scale=1f,rot=0f,tx=0f;
        if(label.contains("MENDENGAR")){scale=1.035f;rot=-1.5f;}
        else if(label.contains("MEMIKIRKAN")){scale=1.07f;rot=2.5f;}
        else if(label.contains("MENJAWAB")){scale=1.04f;tx=dp(3);}
        else if(label.contains("SELESAI")){scale=1.09f;rot=-2f;}
        mascot.animate().scaleX(scale).scaleY(scale).rotation(rot).translationX(tx).setDuration(220).start();
        mascot.cancelAnimation(); mascot.setMinAndMaxFrame(min,max); mascot.setRepeatCount(loop?ValueAnimator.INFINITE:0); mascot.playAnimation();
    }

    private void addUser(String s) {
        TextView v = text(s, 14, "#FFFFFF", false);
        v.setPadding(dp(13), dp(10), dp(13), dp(10));
        v.setBackground(round("#0878F9", null, 18, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.gravity = Gravity.END; lp.setMargins(dp(52), dp(5), 0, dp(5));
        messages.addView(v, lp); scrollBottom();
    }

    private void addBot(String s, String action, String label) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.TOP);
        TextView badge = text("T", 15, "#FFFFFF", true); badge.setGravity(Gravity.CENTER); badge.setBackground(round("#075EF4", null, 18, 0));
        row.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(13), dp(10), dp(13), dp(10)); box.setBackground(round("#FFFFFF", "#D7E8F8", 18, 1));
        TextView v = text(s, 14, "#17324D", false); box.addView(v);
        if (action != null && !action.isEmpty()) {
            TextView b = text((label == null || label.isEmpty() ? "Buka" : label) + "  ›", 13, "#0878F9", true);
            b.setPadding(0, dp(9), 0, dp(2)); b.setOnClickListener(x -> TransAssistantActions.run(this, action)); box.addView(b);
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1); bp.setMargins(dp(8),0,dp(30),0); row.addView(box, bp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.setMargins(0,dp(5),0,dp(5)); messages.addView(row, rp); scrollBottom();
    }

    private void scrollBottom() { if (messageScroll != null) messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN)); }
    private TextView text(String s, int z, String color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(Color.parseColor(color)); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private GradientDrawable round(String fill, String stroke, int radius, int strokeDp) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(fill)); g.setCornerRadius(dp(radius)); if (stroke != null && strokeDp > 0) g.setStroke(dp(strokeDp), Color.parseColor(stroke)); return g; }
    private GradientDrawable gradient(String start, String end, int radius) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(start), Color.parseColor(end)}); g.setCornerRadius(dp(radius)); return g; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
