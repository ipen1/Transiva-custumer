package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

/** Restores an unfinished customer order after process death or force-close. */
public final class ActiveOrderRecovery {
    private ActiveOrderRecovery() { }

    public static boolean route(Activity activity) {
        try {
            SharedPreferences sp = activity.getSharedPreferences("transiva", Activity.MODE_PRIVATE);
            String orderId = clean(sp.getString("active_order_id", ""));
            if (orderId.isEmpty()) return false;
            // Satu order aktif harus selalu dipulihkan ke layar aktivitas/trip.
            // CustomerTripActivity dapat menampilkan status menunggu driver, sehingga tidak
            // perlu mengirim customer kembali ke SearchDriverActivity setelah force-close.
            Intent i = new Intent(activity, CustomerTripActivity.class);
            i.putExtra("order_id", orderId);
            i.putExtra("active_order_id", orderId);
            i.putExtra("order_source", sp.getString("active_order_source", "orders"));
            i.putExtra("active_driver_type", sp.getString("active_driver_type", "motor"));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(i);
            activity.finish();
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
