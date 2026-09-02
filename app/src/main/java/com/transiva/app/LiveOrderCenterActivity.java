package com.transiva.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Premium unified view for every currently active order. */
public class LiveOrderCenterActivity extends Activity implements UnifiedLiveOrderCenter.Listener {
    private LinearLayout list; private ProgressBar loading; private TextView info;
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));setContentView(screen());CustomerAppSettings.apply(this);UnifiedLiveOrderCenter.addListener(this);refresh();}
    @Override protected void onDestroy(){UnifiedLiveOrderCenter.removeListener(this);super.onDestroy();}
    private View screen(){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setBackgroundColor(Color.parseColor("#F6F9FE"));LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(18),dp(18),dp(18),dp(30));s.addView(r);TextView h=t("‹  Live Order Center",24,"#0B3A78",true);h.setOnClickListener(v->finish());r.addView(h);r.addView(t("Semua pesanan aktif Anda dalam satu tempat. Status selalu dikonfirmasi ke server.",13,"#64748B",false));loading=new ProgressBar(this);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(dp(36),dp(36));pl.gravity=Gravity.CENTER;pl.setMargins(0,dp(18),0,dp(8));r.addView(loading,pl);info=t("Memeriksa pesanan aktif...",12,"#64748B",false);info.setGravity(Gravity.CENTER);r.addView(info);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);r.addView(list);return s;}
    private void refresh(){UnifiedLiveOrderCenter.refresh(this,(o,cache,e)->{loading.setVisibility(View.GONE);info.setText(cache&&e!=null?"Jaringan belum stabil • menampilkan data terakhir":"Status diperbarui dari server");render(o);});}
    @Override public void onOrdersChanged(List<UnifiedLiveOrderCenter.Order> orders){if(!isFinishing())runOnUiThread(()->render(orders));}
    private void render(List<UnifiedLiveOrderCenter.Order> orders){list.removeAllViews();if(orders==null||orders.isEmpty()){list.addView(cardText("Tidak ada pesanan aktif","Pesanan baru yang Anda buat akan otomatis muncul di sini."),lp());return;}for(UnifiedLiveOrderCenter.Order o:orders){LinearLayout c=box();c.addView(t(o.title(),17,"#0B3A78",true));TextView st=t(o.statusLabel(),14,OrderStatusPresentation.textColor(o.status),true);st.setPadding(0,dp(6),0,0);c.addView(st);String route=first(o.raw.optString("pickup_address"),"Lokasi jemput")+"  →  "+first(o.raw.optString("delivery_address"),"Tujuan");c.addView(t(route,12,"#64748B",false));TextView action=t(o.hasDriver()?"Buka perjalanan  ›":"Pantau pencarian driver  ›",13,"#0B7CFF",true);action.setPadding(0,dp(12),0,0);c.addView(action);c.setOnClickListener(v->{UnifiedLiveOrderCenter.persistLegacyActiveOrder(this,o);startActivity(UnifiedLiveOrderCenter.routeIntent(this,o));});list.addView(c,lp());}}
    private LinearLayout cardText(String a,String b){LinearLayout x=box();x.addView(t(a,16,"#0B3A78",true));x.addView(t(b,12,"#64748B",false));return x;}
    private LinearLayout box(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(16),dp(16),dp(16),dp(16));x.setBackground(round("#FFFFFF",20));x.setElevation(dp(2));return x;}
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(14),0,0);return p;}
    private TextView t(String s,int z,String c,boolean b){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(c));if(b)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(c));g.setCornerRadius(dp(r));return g;}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}private static String first(String...v){for(String x:v)if(x!=null&&!x.trim().isEmpty())return x.trim();return"";}
}
