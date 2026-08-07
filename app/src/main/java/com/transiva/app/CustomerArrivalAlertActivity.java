package com.transiva.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.Random;

public class CustomerArrivalAlertActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String event = getIntent().getStringExtra("event");
        String driver = clean(getIntent().getStringExtra("driver"));
        boolean pickup = "arrived_pickup".equalsIgnoreCase(event);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(42, 48, 42, 48);
        root.setBackgroundColor(Color.rgb(8, 30, 62));

        TextView badge = text(pickup ? "📍 DRIVER SUDAH TIBA" : "🏁 SAMPAI DI PENGANTARAN", 18, true);
        TextView title = text(randomMessage(pickup, driver), 30, true);
        TextView body = text(pickup
                ? "Driver Anda sudah berada di titik penjemputan. Silakan bersiap dan pastikan barang bawaan sudah lengkap."
                : "Driver sudah tiba di titik pengantaran. Pastikan perjalanan atau pesanan Anda diterima dengan baik.", 18, false);
        TextView hint = text("Ketuk layar untuk membuka detail perjalanan", 15, false);
        root.addView(badge); root.addView(title); root.addView(body); root.addView(hint);
        root.setOnClickListener(v -> openTrip());
        setContentView(root);
        handler.postDelayed(this::finish, 12000L);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(sp); v.setGravity(Gravity.CENTER);
        v.setPadding(12, 18, 12, 18); if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    private String randomMessage(boolean pickup, String driver) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String time = hour < 11 ? "Pagi ini" : hour < 15 ? "Siang ini" : hour < 18 ? "Sore ini" : "Malam ini";
        String name = driver.isEmpty() ? "Driver Anda" : driver;
        String[] pickupMsg = {
                "Kabar bagus! " + name + " sudah menunggu di titik penjemputan ✨",
                time + " makin praktis — " + name + " sudah tiba untuk menjemput Anda 🚙",
                "Siap berangkat? " + name + " sudah sampai di titik penjemputan 🙌",
                "Waktunya jalan! " + name + " sudah tiba di penjemputan 🎉"
        };
        String[] deliveryMsg = {
                "Sampai tujuan! " + name + " sudah tiba di titik pengantaran 🏁",
                "Perjalanan hampir beres — Anda sudah tiba di pengantaran ✨",
                "Yeay, sudah sampai! Pastikan semua barang Anda tidak tertinggal 🙌",
                time + " selesai dengan baik — driver sudah tiba di titik pengantaran 🎉"
        };
        String[] arr = pickup ? pickupMsg : deliveryMsg;
        return arr[new Random().nextInt(arr.length)];
    }

    private void openTrip() {
        android.content.Intent i = new android.content.Intent(this, CustomerTripActivity.class);
        i.putExtra("order_id", clean(getIntent().getStringExtra("order_id")));
        i.putExtra("from_fcm", true);
        startActivity(i); finish();
    }
    private static String clean(String v) { return v == null ? "" : v.trim(); }
}
