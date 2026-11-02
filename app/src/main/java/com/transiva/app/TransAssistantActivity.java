package com.transiva.app;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Intent;
import android.text.InputFilter;
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

/** Premium Trans Asisten UI with native dark-mode palette and local intelligent engine. */
public class TransAssistantActivity extends CustomerAdaptiveActivity {
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
    private boolean dark;
    private boolean sending;
    private final CustomerLifecycleNetworkScope csScope=new CustomerLifecycleNetworkScope();
    private CustomerSessionGuard csSession;
    private String selectedOrder="", selectedSource="";
    private TextView sendButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dark = false; // Source colors stay light; the shared theme engine renders both modes.
        CustomerWindowPolicy.status(w, dark ? Color.rgb(4,13,25) : NAVY);
        CustomerWindowPolicy.navigation(w, dark ? Color.rgb(5,16,29) : Color.WHITE);
        csSession=CustomerSessionGuard.capture(this);
        engine = new TransAssistantEngine(this);
        TransAssistantSync.sync(this,true);
        setContentView(build());
        setState("IDLE", 0, 59, true);
        addBot("Halo, Kak! Saya Trans Asisten, bantuan otomatis Transiva. Ceritakan kendala pesanan, saldo, voucher, atau akun. Untuk pemeriksaan data terbaru saya memerlukan internet. Kakak juga bisa meminta bantuan admin.", "OPEN_SUPPORT", "Pusat Bantuan", 1.0, false);
        restoreConversation();

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
        csScope.destroy();
        handler.removeCallbacksAndMessages(null);
        if (mascot != null) mascot.cancelAnimation();
        super.onDestroy();
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? Color.rgb(7,18,32) : Color.rgb(246,250,255));

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
        titleBox.addView(text("Trans Asisten", 20, "#FFFFFF", true));
        titleBox.addView(text("CS otomatis • pesanan • saldo • bantuan", 11, "#CDEBFF", false));
        bar.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView local = text("●  CS", 10, "#BDF7FF", true);
        local.setPadding(dp(10), dp(6), dp(10), dp(6));
        local.setBackground(round("#1836A5D9", "#6AE8FF", 20, 1));
        bar.addView(local);
        TextView reset=text("↺",24,"#FFFFFF",true);reset.setPadding(dp(8),0,0,0);reset.setContentDescription("Mulai percakapan baru");
        reset.setOnClickListener(v->{if(sending)return;TransAssistantMemory.clear(this);TransAssistantConversationState.clear(this);selectedOrder="";selectedSource="";messages.removeAllViews();addBot("Percakapan baru dimulai. Apa yang bisa saya bantu, Kak?","OPEN_SUPPORT","Pusat Bantuan",1,false);});
        bar.addView(reset);
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
        String[] prompts = {"🧾 Cek pesanan", "💳 Saldo kepotong", "🎟 Voucher tidak bisa", "📋 Cek status laporan", "🎧 Hubungi admin"};
        for (String p : prompts) {
            TextView c = text(p, 12, dark ? "#D9ECFF" : "#0B4E9B", true);
            c.setPadding(dp(12), dp(9), dp(12), dp(9));
            c.setBackground(round(dark ? "#122A43" : "#FFFFFF", dark ? "#315B7F" : "#CDE4FF", 18, 1));
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
        composerWrap.setBackgroundColor(dark ? Color.rgb(9,22,37) : Color.WHITE);

        input = new EditText(this);
        input.setHint("Tanya Trans Asisten...");
        input.setTextSize(14);
        input.setTextColor(Color.parseColor(dark ? "#EEF5FF" : "#17324D"));
        input.setHintTextColor(Color.parseColor(dark ? "#8FA8C1" : "#8296AD"));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(round(dark ? "#132B42" : "#F3F8FF", dark ? "#315B7F" : "#CFE3F8", 22, 1));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1500)});
        input.setSingleLine(false); input.setMaxLines(3); input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnFocusChangeListener((v,focused)->{ if(focused && input.getText().length()==0) setState("MENDENGARKAN",60,119,true); else if(!focused) setState("SIAP MEMBANTU",0,59,true); });
        input.setOnEditorActionListener((v, action, e) -> { if (action == EditorInfo.IME_ACTION_SEND) { send(); return true; } return false; });
        composerWrap.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        TextView send = text("➤", 24, "#FFFFFF", true);
        sendButton=send;
        send.setGravity(Gravity.CENTER); send.setContentDescription("Kirim pertanyaan");
        send.setBackground(round("#0878F9", null, 24, 0));
        send.setOnClickListener(v -> send());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(48), dp(48)); sp.setMargins(dp(8),0,0,0);
        composerWrap.addView(send, sp);
        root.addView(composerWrap, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void restoreConversation(){
        JSONArray turns=TransAssistantMemory.snapshot(this);
        long cutoff=System.currentTimeMillis()-30L*60L*1000L;
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.optJSONObject(i);if(turn==null||turn.optLong("ts",0)<cutoff)continue;
            if("user".equals(turn.optString("role")))addUser(turn.optString("text",""));
            else addBot(turn.optString("text",""),"","",1,false);
        }
    }

    private void send() {
        final String q=input.getText().toString().trim();
        if(q.isEmpty()||sending)return;
        if(!csSession.matches(this)){finish();return;}
        sending=true;sendButton.setEnabled(false);sendButton.setAlpha(.45f);
        addUser(TransAssistantMemory.redact(q));input.setText("");
        setState("MEMIKIRKAN...",120,179,true);
        final String previous=TransAssistantConversationState.read(this).intent;
        final String orderForRequest=selectedOrder, sourceForRequest=selectedSource;
        csScope.execute(()->{
            JSONObject remote=null;boolean unavailable=false;
            TransAssistantEngine.Reply local;
            try{local=engine.preview(q);}catch(Exception e){local=new TransAssistantEngine.Reply("Panduan belum berhasil disiapkan. Silakan buka Pusat Bantuan.","OPEN_SUPPORT","Pusat Bantuan","UNKNOWN",0,false);}
            try{if(!"CS_SECURITY".equals(local.intent)&&!"CS_PRIVACY".equals(local.intent))remote=TransAssistantCsClient.ask(this,csSession,q,previous,orderForRequest,sourceForRequest);}
            catch(Exception e){unavailable=true;}
            final JSONObject result=remote;final boolean failed=unavailable;final TransAssistantEngine.Reply localReply=local;
            csScope.post(handler,()->{
                if(isFinishing()||isDestroyed()||!csSession.matches(this))return;
                TransAssistantEngine.Reply reply;
                JSONObject data=result==null?null:result.optJSONObject("reply");
                if(result!=null&&result.optBoolean("handled")&&data!=null&&!data.optString("text").isEmpty()){
                    reply=new TransAssistantEngine.Reply(data.optString("text"),data.optString("action"),data.optString("action_label"),data.optString("intent"),.9,false);
                    if(!"CS_HUMAN".equals(reply.intent)){selectedOrder=data.optString("order_id","");selectedSource=data.optString("order_source","");}
                    TransAssistantMemory.add(this,"user",q,"");
                    TransAssistantMemory.add(this,"assistant",reply.text,reply.intent);
                    TransAssistantConversationState.update(this,reply.intent,TransAssistantMemory.redact(q));
                }else{
                    reply=localReply;
                    if(!reply.intent.startsWith("CS_")||"CS_SECURITY".equals(reply.intent)||"CS_PRIVACY".equals(reply.intent)){selectedOrder="";selectedSource="";}
                    if(failed)reply=new TransAssistantEngine.Reply("Pemeriksaan server belum berhasil. Berikut panduan lokal; data terbaru belum terverifikasi.\n\n"+reply.text,reply.action,reply.actionLabel,reply.intent,reply.confidence,reply.needsConfirmation);
                    engine.remember(q,reply);
                }
                sending=false;sendButton.setEnabled(true);sendButton.setAlpha(1f);
                setState("MENJAWAB",180,239,true);
                addBot(reply.text,reply.action,reply.actionLabel,reply.confidence,reply.needsConfirmation);
                handler.postDelayed(()->{if(!isFinishing()&&!isDestroyed()&&!sending)setState("SIAP MEMBANTU",0,59,true);},900L);
            });
        },()->csScope.post(handler,()->{
            sending=false;sendButton.setEnabled(true);sendButton.setAlpha(1f);input.setText(q);
            addBot("Aplikasi sedang sibuk. Pertanyaan belum diproses; silakan kirim kembali.","OPEN_SUPPORT","Pusat Bantuan",1,false);
            setState("SIAP MEMBANTU",0,59,true);
        }));
    }

    private void openSupport(){
        Intent intent=new Intent(this,CustomerSupportActivity.class);
        JSONArray turns=TransAssistantMemory.snapshot(this);StringBuilder summary=new StringBuilder("Ringkasan percakapan Trans Asisten (belum diverifikasi admin):\n");
        for(int i=Math.max(0,turns.length()-6);i<turns.length();i++){
            JSONObject t=turns.optJSONObject(i);if(t==null)continue;
            String text=t.optString("text","");if(text.length()>500)text=text.substring(0,500)+"…";
            summary.append("user".equals(t.optString("role"))?"Customer: ":"Asisten: ").append(text).append('\n');
        }
        intent.putExtra("assistant_subject","Bantuan dari Trans Asisten");
        intent.putExtra("assistant_detail",TransAssistantMemory.redact(summary.toString()));
        if(!selectedOrder.isEmpty()){intent.putExtra("order_id",selectedOrder);intent.putExtra("source",selectedSource);}
        startActivity(intent);
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

    private void addBot(String s, String action, String label, double confidence, boolean confirm) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.TOP);
        TextView badge = text("T", 15, "#FFFFFF", true); badge.setGravity(Gravity.CENTER); badge.setBackground(round("#075EF4", null, 18, 0));
        row.addView(badge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(13), dp(10), dp(13), dp(10));
        box.setBackground(round(dark ? "#122A43" : "#FFFFFF", dark ? "#315B7F" : "#D7E8F8", 18, 1));
        TextView v = text(s, 14, dark ? "#EEF5FF" : "#17324D", false); box.addView(v);
        if (confirm) {
            TextView hint = text("Mohon pastikan topik yang dimaksud", 10, dark ? "#9FC5E8" : "#6685A3", true);
            hint.setPadding(0, dp(7), 0, 0); box.addView(hint);
        }
        if (action != null && !action.isEmpty()) {
            TextView b = text((label == null || label.isEmpty() ? "Buka" : label) + "  ›", 13, dark ? "#67B6FF" : "#0878F9", true);
            b.setPadding(0, dp(9), 0, dp(2)); b.setOnClickListener(x -> {if("OPEN_SUPPORT".equals(action))openSupport();else TransAssistantActions.run(this,action);}); box.addView(b);
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
