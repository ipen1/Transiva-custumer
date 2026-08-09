package com.transiva.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CustomerArrivalAlertActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(8, 30, 62));
        getWindow().setNavigationBarColor(Color.rgb(5, 20, 42));

        CustomerArrivalMessage.Content content = CustomerArrivalMessage.build(
                clean(getIntent().getStringExtra("event")),
                clean(getIntent().getStringExtra("order_type")),
                clean(getIntent().getStringExtra("source")),
                clean(getIntent().getStringExtra("restaurant_name")),
                clean(getIntent().getStringExtra("driver")),
                clean(getIntent().getStringExtra("order_id"))
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(44), dp(28), dp(40));
        root.setBackgroundColor(Color.rgb(8, 30, 62));

        TextView brand = text("TRANSIVA", 14, true);
        brand.setTextColor(Color.rgb(125, 190, 255));
        brand.setLetterSpacing(0.12f);

        TextView badge = text(content.badge, 16, true);
        badge.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = text(content.headline, 29, true);
        title.setPadding(dp(4), dp(14), dp(4), dp(12));

        TextView body = text(content.body, 17, false);
        body.setTextColor(Color.rgb(220, 235, 250));

        TextView hint = text("Ketuk layar untuk membuka detail perjalanan", 14, false);
        hint.setTextColor(Color.rgb(156, 190, 224));
        hint.setPadding(dp(8), dp(28), dp(8), dp(8));

        root.addView(brand);
        root.addView(badge);
        root.addView(title);
        root.addView(body);
        root.addView(hint);
        root.setOnClickListener(v -> openTrip());
        setContentView(root);

        handler.postDelayed(this::finish, 15000L);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void openTrip() {
        Intent i = new Intent(this, CustomerTripActivity.class);
        i.putExtra("order_id", clean(getIntent().getStringExtra("order_id")));
        i.putExtra("source", clean(getIntent().getStringExtra("source")));
        i.putExtra("from_fcm", true);
        startActivity(i);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String clean(String v) {
        return v == null ? "" : v.trim();
    }
}
