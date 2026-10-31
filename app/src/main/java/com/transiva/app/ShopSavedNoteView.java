package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps the original input as the order payload while exposing a protected saved note. */
final class ShopSavedNoteView extends LinearLayout {
    interface Listener { void onSavedChanged(boolean saved); }
    private final Activity activity;
    private final EditText input;
    private final TextView preview;
    private final Button action;
    private final Listener listener;
    private boolean saved;

    ShopSavedNoteView(Activity activity, EditText input, boolean saved, Listener listener) {
        super(activity);
        this.activity=activity;
        this.input=input;
        this.listener=listener;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        boolean dark=CustomerAppSettings.isDarkMode(activity);
        setPadding(dp(2),dp(2),dp(2),dp(2));
        GradientDrawable card=new GradientDrawable();
        card.setColor(Color.parseColor(dark?"#13243A":"#FFFDF6"));
        card.setCornerRadius(dp(12));
        card.setStroke(dp(1),Color.parseColor(dark?"#806735":"#DFC891"));
        setBackground(card);
        FrameLayout content=new FrameLayout(activity);
        if(input.getParent() instanceof ViewGroup)((ViewGroup)input.getParent()).removeView(input);
        content.addView(input,new FrameLayout.LayoutParams(-1,-2));
        preview=new TextView(activity);
        preview.setTextSize(13);
        preview.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        preview.setTextColor(Color.parseColor(dark?"#F1D596":"#173D66"));
        preview.setPadding(dp(10),dp(10),dp(6),dp(10));
        preview.setMinHeight(dp(44));
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setContentDescription("Catatan tersimpan. Ketuk untuk mengedit");
        content.addView(preview,new FrameLayout.LayoutParams(-1,-2));
        addView(content,new LinearLayout.LayoutParams(0,-2,1));
        action=new Button(activity);
        action.setAllCaps(false);
        action.setTextSize(12);
        action.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        action.setTextColor(Color.parseColor(dark?"#F1D596":"#0B5AB3"));
        action.setBackgroundColor(Color.TRANSPARENT);
        action.setPadding(0,0,0,0);
        action.setMinWidth(0);action.setMinimumWidth(0);
        action.setMinHeight(0);action.setMinimumHeight(0);
        addView(action,new LinearLayout.LayoutParams(dp(68),dp(48)));
        action.setOnClickListener(v->{if(this.saved)edit();else save();});
        preview.setOnClickListener(v->edit());
        display(saved&&!input.getText().toString().trim().isEmpty());
    }
    private void display(boolean value){
        saved=value;
        preview.setText(input.getText().toString().trim());
        preview.setVisibility(saved?View.VISIBLE:View.GONE);
        input.setVisibility(saved?View.GONE:View.VISIBLE);
        action.setText(saved?"Ubah":"Simpan");
        action.setContentDescription(saved?"Ubah catatan tersimpan":"Simpan catatan belanja");
    }
    private void save(){
        String value=input.getText().toString().trim();
        if(value.isEmpty()){input.setError("Tulis daftar belanja terlebih dahulu");input.requestFocus();return;}
        input.setText(value);
        input.clearFocus();
        InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);
        display(true);
        listener.onSavedChanged(true);
    }
    private void edit(){
        if(!saved)return;
        display(false);
        listener.onSavedChanged(false);
        input.requestFocus();
        input.setSelection(input.length());
        input.post(()->{InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);});
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
