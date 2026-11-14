package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

class AntarKilatGameView extends LinearLayout {
    private static final int NAVY = 0xff092659;
    private static final int BLUE = 0xff1468df;
    private static final int DARK = 0xff081833;
    private static final String BEST_SCORE = "best_score";
    private enum State { READY, RUNNING, PAUSED, FINISHED }

    private final AntarKilatRoadView road;
    private final SharedPreferences scores;
    private final TextView clock;
    private final TextView score;
    private final TextView status;
    private final LinearLayout orderBanner;
    private final Button track;
    private final Button pause;
    private final LinearLayout steering;
    private final ScrollView overlay;
    private final TextView overlayTitle;
    private final TextView overlayDetail;
    private final Button overlayAction;
    private final Runnable frame = this::step;
    private AntarKilatRound round = new AntarKilatRound(new Random());
    private State state = State.READY;
    private long previousFrame;
    private int displayedScore = -1;
    private int displayedSeconds = -1;
    private boolean themed;
    private boolean darkTheme;
    private SoundPool sounds;
    private int pickupSound;
    private int hitSound;
    private int activeStream;

    AntarKilatGameView(Context context, String orderStatus, Runnable close, boolean dark) {
        super(context);
        scores = context.getSharedPreferences("antar_kilat", Context.MODE_PRIVATE);
        setOrientation(VERTICAL);
        setBackgroundColor(DARK);

        LinearLayout header = row();
        header.setPadding(dp(6), 0, dp(6), 0);
        header.setBackgroundColor(NAVY);
        Button back = button("‹", NAVY, Color.WHITE, close);
        back.setContentDescription("Kembali");
        header.addView(back, new LayoutParams(dp(48), dp(48)));
        header.addView(label("Antar Kilat Transiva", 18, Color.WHITE, true), new LayoutParams(0, -2, 1));
        pause = button("Jeda", NAVY, Color.WHITE, this::pauseRound);
        header.addView(pause, new LayoutParams(-2, dp(48)));
        addView(header, new LayoutParams(-1, -2));

        status = label("", 13, NAVY, true);
        status.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        status.setPadding(dp(6), dp(4), dp(4), dp(4));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        track = button("Lacak", 0xffdbeaff, NAVY, close);
        orderBanner = row();
        orderBanner.setPadding(dp(8), 0, dp(8), 0);
        orderBanner.addView(status, new LayoutParams(0, -2, 1));
        orderBanner.addView(track, new LayoutParams(-2, dp(48)));
        if (orderStatus != null) {
            updateOrderStatus(orderStatus);
            addView(orderBanner, new LayoutParams(-1, -2));
        }

        FrameLayout stage = new FrameLayout(context);
        addView(stage, new LayoutParams(-1, 0, 1));
        road = new AntarKilatRoadView(context);
        stage.addView(road, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout hud = row();
        hud.setPadding(dp(12), dp(7), dp(12), dp(7));
        hud.setBackground(surface(0xd90a2548, 16));
        clock = label("", 15, Color.WHITE, true);
        score = label("", 15, Color.WHITE, true);
        hud.addView(clock);
        LayoutParams scoreParams = new LayoutParams(-2, -2);
        scoreParams.setMarginStart(dp(20));
        hud.addView(score, scoreParams);
        FrameLayout.LayoutParams hudParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hudParams.topMargin = dp(8);
        stage.addView(hud, hudParams);

        steering = row();
        steering.setPadding(dp(5), 0, dp(5), 0);
        steering.setBackgroundColor(DARK);
        Button left = button("‹", NAVY, Color.WHITE, () -> steer(-1));
        left.setContentDescription("Geser ke kiri");
        steering.addView(left, new LayoutParams(dp(52), dp(48)));
        steering.addView(label("Geser kiri atau kanan", 13, 0xffe0ecff, true), new LayoutParams(0, -2, 1));
        Button right = button("›", NAVY, Color.WHITE, () -> steer(1));
        right.setContentDescription("Geser ke kanan");
        steering.addView(right, new LayoutParams(dp(52), dp(48)));
        addView(steering, new LayoutParams(-1, -2));

        overlay = new ScrollView(context);
        overlay.setBackgroundColor(0x5d061129);
        overlay.setFillViewport(true);
        LinearLayout center = new LinearLayout(context);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(surface(0xf20b254a, 22));
        overlayTitle = label("", 23, Color.WHITE, true);
        overlayDetail = label("", 14, 0xffd9e8ff, false);
        overlayDetail.setPadding(0, dp(8), 0, dp(10));
        overlayAction = button("", BLUE, Color.WHITE, this::play);
        card.addView(overlayTitle);
        card.addView(overlayDetail);
        card.addView(overlayAction, new LayoutParams(-1, -2));
        center.addView(card, new LayoutParams(-2, -2));
        overlay.addView(center, new ScrollView.LayoutParams(-1, -2));
        stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        applyPalette(dark);
        showState();
    }

    final void applyPalette(boolean dark) {
        if (themed && dark == darkTheme) return;
        themed = true;
        darkTheme = dark;
        orderBanner.setBackgroundColor(dark ? 0xff132d4d : 0xfff1f7ff);
        status.setTextColor(dark ? 0xffeaf4ff : NAVY);
        track.setTextColor(dark ? Color.WHITE : NAVY);
        track.setBackground(ripple(dark ? 0xff173b65 : 0xffdbeaff));
    }

    void updateOrderStatus(String orderStatus) {
        if (!orderStatus.contentEquals(status.getText())) status.setText(orderStatus);
    }

    void pauseRound() {
        if (state != State.RUNNING) return;
        removeCallbacks(frame);
        stopSound();
        state = State.PAUSED;
        showState();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sounds = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        pickupSound = sounds.load(getContext(), R.raw.antar_kilat_pickup, 1);
        hitSound = sounds.load(getContext(), R.raw.antar_kilat_hit, 1);
    }

    @Override protected void onDetachedFromWindow() {
        pauseRound();
        if (sounds != null) {
            sounds.release();
            sounds = null;
        }
        super.onDetachedFromWindow();
    }

    private void playSound(int sample) {
        if (sounds == null || sample == 0) return;
        stopSound();
        activeStream = sounds.play(sample, .30f, .30f, 1, 0, 1f);
    }

    private void stopSound() {
        if (sounds != null && activeStream != 0) sounds.stop(activeStream);
        activeStream = 0;
    }

    private void play() {
        if (state != State.PAUSED) round = new AntarKilatRound(new Random());
        state = State.RUNNING;
        previousFrame = System.nanoTime();
        showState();
        postOnAnimation(frame);
    }

    private void steer(int direction) {
        if (state == State.RUNNING) round.steer(direction);
    }

    private void step() {
        if (state != State.RUNNING) return;
        long now = System.nanoTime();
        int pickups = round.pickupCount;
        int hits = round.hitCount;
        round.advance((now - previousFrame) / 1_000_000_000f);
        previousFrame = now;
        if (round.hitCount != hits) playSound(hitSound);
        else if (round.pickupCount != pickups) playSound(pickupSound);
        if (round.elapsed >= AntarKilatRound.DURATION) {
            state = State.FINISHED;
            if (round.score > scores.getInt(BEST_SCORE, 0)) {
                scores.edit().putInt(BEST_SCORE, round.score).apply();
            }
            showState();
            return;
        }
        updateHud();
        road.render(round, true);
        postOnAnimation(frame);
    }

    private void showState() {
        boolean running = state == State.RUNNING;
        pause.setEnabled(running);
        pause.setAlpha(running ? 1f : .45f);
        steering.setVisibility(running ? VISIBLE : INVISIBLE);
        overlay.setVisibility(running ? GONE : VISIBLE);
        if (state == State.READY) {
            overlayTitle.setText("Siap antar?");
            overlayDetail.setText("Ambil paket emas dan hindari lubang serta genangan selama 30 detik.");
            overlayAction.setText("Mulai main");
        } else if (state == State.PAUSED) {
            overlayTitle.setText("Game dijeda");
            overlayDetail.setText("Lanjutkan saat kamu siap.");
            overlayAction.setText("Lanjut main");
        } else if (state == State.FINISHED) {
            overlayTitle.setText("Waktu habis!");
            overlayDetail.setText("Skor " + round.score + "  ·  Rekor " + scores.getInt(BEST_SCORE, 0));
            overlayAction.setText("Main lagi");
        }
        updateHud();
        road.render(round, running);
    }

    private void updateHud() {
        int seconds = (int) Math.ceil(AntarKilatRound.DURATION - round.elapsed);
        if (seconds != displayedSeconds) {
            displayedSeconds = seconds;
            clock.setText(String.format(Locale.US, "Waktu  00:%02d", seconds));
        }
        if (round.score != displayedScore) {
            displayedScore = round.score;
            score.setText("Skor  " + displayedScore);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private RippleDrawable ripple(int color) {
        return new RippleDrawable(ColorStateList.valueOf(0x304e93dc), surface(color, 13), null);
    }

    private Button button(String text, int background, int ink, Runnable action) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(ink);
        button.setTextSize(14);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundTintList(null);
        button.setBackground(ripple(background));
        button.setOnClickListener(v -> action.run());
        return button;
    }
}
