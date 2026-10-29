package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Optional, repeatable native help. No permissions, overlay, account data, or network request. */
public final class CustomerBeginnerGuide {
    private CustomerBeginnerGuide() {}
    private static String guideKey(Context c) {
        String account = CustomerSessionGuard.capture(c).account;
        return "trip_tips_" + account;
    }
    public static boolean tipsEnabled(Context c) {
        return !c.getSharedPreferences("customer_beginner_v1", Context.MODE_PRIVATE).getBoolean(guideKey(c), false);
    }
    public static void hideTips(Context c) {
        c.getSharedPreferences("customer_beginner_v1", Context.MODE_PRIVATE).edit().putBoolean(guideKey(c), true).apply();
    }
    public static View dashboardCard(Activity activity) {
        LinearLayout box = new LinearLayout(activity); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity,12), dp(activity,10), dp(activity,12), dp(activity,12));
        box.setBackground(CustomerUiPrimitives.roundStroke(activity,"#FFFFFF","#D8E5F2",dp(activity,18),1));
        box.addView(label(activity,"Lebih mudah untuk Anda",17,true));
        addAction(activity,box,"Pesan lagi", "Pilih perjalanan atau makanan sebelumnya; biaya diperiksa kembali.",
                () -> activity.startActivity(new Intent(activity,SmartReorderActivity.class)));
        addAction(activity,box,"Poin & voucher saya", "Lihat poin, voucher, dan syarat penggunaannya.",
                () -> activity.startActivity(new Intent(activity,CustomerLoyaltyActivity.class)));
        addAction(activity,box,"Cara memesan", "Panduan perjalanan, makanan, belanja, dan kirim barang.", () -> show(activity));
        addAction(activity,box,"Butuh bantuan?", "Pilih kendala atau hubungi admin.",
                () -> activity.startActivity(new Intent(activity,CustomerSupportActivity.class)));
        return box;
    }
    private static void addAction(Activity a, LinearLayout parent, String title, String sub, Runnable action) {
        LinearLayout row = new LinearLayout(a); row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(dp(a,56)); row.setPadding(0,dp(a,10),0,dp(a,10));
        row.addView(label(a,title+"  ›",15,true)); row.addView(label(a,sub,13,false));
        row.setFocusable(true); row.setClickable(true); row.setContentDescription(title+". "+sub);
        row.setOnClickListener(v -> action.run()); parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    public static void show(Activity a) { show(a,null); }
    public static void show(Activity a, Runnable onTipsChanged) {
        if(a.isFinishing() || a.isDestroyed()) return;
        ScrollView scroll = new ScrollView(a);
        LinearLayout body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a,20),dp(a,8),dp(a,20),dp(a,12)); scroll.addView(body);
        section(a,body,"Naik motor atau mobil", "1. Pilih TransRide untuk motor atau TransCar untuk mobil.\n2. Periksa lokasi jemput dan pilih tujuan.\n3. Periksa biaya dan pembayaran.\n4. Konfirmasi pesanan, lalu pantau driver dari Pesanan.");
        section(a,body,"Pesan makanan", "1. Pilih TransFood dan pastikan alamat antar benar.\n2. Pilih penjual, menu, jumlah, dan pilihan yang tersedia.\n3. Periksa isi pesanan, ongkir, dan pembayaran.\n4. Konfirmasi, lalu pantau pesanan Anda.");
        section(a,body,"Belanja atau kirim barang", "TransShop untuk belanja kebutuhan toko. TransSend untuk kirim barang. Isi alamat dan rincian yang diminta, lalu periksa ringkasan biaya sebelum memesan.");
        section(a,body,"Rumah, kantor, dan pesan lagi", "Ketuk Rumah atau Kantor di Beranda. Simpan alamat jika belum ada; jika sudah, pilih motor atau mobil dan periksa tujuan yang terisi. Pesan lagi memakai pesanan sebelumnya sebagai acuan; harga dan ketersediaan diperiksa kembali.");
        section(a,body,"Pembayaran dan biaya tambahan", "Tunai berarti membayar kepada driver sesuai tagihan. Transiva Pay memakai saldo aplikasi. Baca rincian dan ketentuan biaya tunggu atau tambahan sebelum mengonfirmasi. Voucher hanya berlaku jika syaratnya terpenuhi.");
        section(a,body,"Jika mengalami kendala", "Buka Butuh bantuan, pilih masalah, lalu periksa laporan sebelum menekan Kirim. Untuk masalah pesanan, buka bantuan dari rincian pesanan agar nomor pesanan ikut dilampirkan. Anda juga dapat menghubungi admin lewat WhatsApp dari Pusat Bantuan.");
        android.app.AlertDialog.Builder builder = new TransivaAlertDialogBuilder(a).setTitle("Cara memakai Transiva")
                .setView(scroll).setPositiveButton("Mengerti",null)
                .setNeutralButton("Bantuan admin",(d,w)->a.startActivity(new Intent(a,CustomerSupportActivity.class)));
        if(onTipsChanged!=null) builder.setNegativeButton("Sembunyikan petunjuk",(d,w)->{hideTips(a);onTipsChanged.run();});
        builder.show(); CustomerAppSettings.applyToView(a,body);
    }
    private static void section(Activity a,LinearLayout body,String title,String copy) {
        TextView heading=label(a,title,16,true); heading.setPadding(0,dp(a,14),0,dp(a,6));body.addView(heading);
        body.addView(label(a,copy,14,false));
    }
    private static TextView label(Context c,String text,int size,boolean bold) {
        TextView t=new TextView(c);t.setText(text);t.setTextSize(size);t.setTextColor(Color.parseColor(bold?"#123B65":"#405B73"));
        t.setLineSpacing(dp(c,2),1.05f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private static int dp(Context c,int n){return CustomerUiPrimitives.dp(c,n);}
}
