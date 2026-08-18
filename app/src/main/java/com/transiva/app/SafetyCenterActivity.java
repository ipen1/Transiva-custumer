package com.transiva.app;

import android.app.Activity; import android.content.Intent; import android.graphics.Color; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.net.Uri; import android.os.Bundle; import android.view.Gravity; import android.widget.*;

public class SafetyCenterActivity extends Activity {
    @Override protected void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(Color.parseColor("#0B7CFF")); setContentView(screen()); CustomerAppSettings.apply(this); }
    private ScrollView screen(){ ScrollView s=new ScrollView(this); s.setBackgroundColor(Color.parseColor("#F6F9FE")); LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(dp(18),dp(18),dp(18),dp(30)); s.addView(r);
        TextView back=t("‹  Transiva Safety Center",24,"#0B3A78",true); back.setOnClickListener(v->finish()); r.addView(back);
        TextView hero=t("Perjalanan lebih aman, bantuan lebih cepat",22,"#0B3A78",true); hero.setPadding(0,dp(24),0,dp(6)); r.addView(hero); r.addView(t("Gunakan fitur ini saat perjalanan aktif atau ketika Anda membutuhkan bantuan darurat.",13,"#64748B",false));
        r.addView(card("🆘  Panggilan Darurat 112","Hubungi layanan darurat terpadu. Ketersediaan operasional mengikuti wilayah setempat.",()->startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))) , lp());
        r.addView(card("📍  Bagikan Perjalanan","Buka aktivitas pesanan untuk membagikan perjalanan aktif kepada orang tepercaya.",()->startActivity(new Intent(this,CustomerHistoryActivity.class))),lp());
        r.addView(card("🚗  Data Driver & Kendaraan","Cocokkan nama driver, kendaraan, dan nomor polisi pada detail pesanan sebelum berangkat.",()->startActivity(new Intent(this,CustomerHistoryActivity.class))),lp());
        r.addView(card("🔐  Lindungi Transiva Pay","Aktifkan biometrik agar akses PIN dan pembayaran di perangkat ini memiliki lapisan keamanan tambahan.",()->startActivity(new Intent(this,CustomerSettingsActivity.class))),lp());
        LinearLayout tips=box(); tips.addView(t("Tips keselamatan",17,"#0B3A78",true)); tips.addView(t("• Pastikan identitas driver sesuai aplikasi.\n• Jangan berikan OTP/PIN kepada siapa pun.\n• Untuk TransSend, berikan OTP hanya setelah paket diterima.\n• Jika situasi terasa tidak aman, hentikan perjalanan di lokasi ramai dan cari bantuan.",13,"#475569",false)); r.addView(tips,lp()); return s; }
    private LinearLayout card(String a,String b,Runnable run){ LinearLayout x=box(); TextView h=t(a,16,"#0B3A78",true); x.addView(h); TextView d=t(b,12,"#64748B",false); d.setPadding(0,dp(6),0,0); x.addView(d); x.setOnClickListener(v->run.run()); return x; }
    private LinearLayout box(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(16),dp(16),dp(16),dp(16));x.setBackground(round("#FFFFFF",20));x.setElevation(dp(2));return x;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(14),0,0);return p;}
    private TextView t(String s,int sp,String c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.parseColor(c));if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(c));g.setCornerRadius(dp(r));return g;} private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
}
