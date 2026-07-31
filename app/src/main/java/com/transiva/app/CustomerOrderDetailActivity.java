package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerOrderDetailActivity extends Activity {
    private static final String BASE_URL = "https://transiva.my.id/";
    private static final String ACTION_URL = BASE_URL + "server/customer_order_action.php";
    private final Handler main = new Handler(Looper.getMainLooper());
    private JSONObject order = new JSONObject();
    private LinearLayout body, actionBox;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { order = new JSONObject(getIntent().getStringExtra("order_json")); } catch (Exception ignored) {}
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244,248,253));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(14), dp(18), dp(14));
        top.setBackgroundColor(Color.rgb(21,126,245));
        TextView back = text("‹", 38, Color.WHITE, true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView title = text("Detail Pesanan", 24, Color.WHITE, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,-2,1); tlp.setMargins(dp(8),0,0,0); top.addView(title,tlp);
        root.addView(top, new LinearLayout.LayoutParams(-1,-2));

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18),dp(18),dp(18),dp(32));
        scroll.addView(body, new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        progress = new ProgressBar(this); progress.setVisibility(View.GONE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        setContentView(root);
        render();
    }

    private void render() {
        body.removeAllViews();
        String service = first(order.optString("service_name"), order.optString("order_type"), order.optString("service"), "Pesanan Transiva");
        String status = first(order.optString("status_label"), prettyStatus(order.optString("status")), "-");
        LinearLayout hero = card();
        hero.addView(text(service,24,Color.rgb(12,67,120),true));
        TextView badge=text(status,14,Color.rgb(5,120,87),true); badge.setPadding(dp(12),dp(7),dp(12),dp(7)); badge.setBackground(round("#DCFCE7",99));
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2); blp.setMargins(0,dp(10),0,0); hero.addView(badge,blp);
        addCard(hero);

        LinearLayout driverCard=card(); driverCard.addView(sectionTitle("Driver & Kendaraan"));
        LinearLayout photos=new LinearLayout(this); photos.setOrientation(LinearLayout.HORIZONTAL);
        photos.addView(photoBox(first(order.optString("driver_photo"),order.optString("photo_driver"))),new LinearLayout.LayoutParams(0,dp(150),1));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(dp(10),1); photos.addView(new View(this),gap);
        photos.addView(photoBox(first(order.optString("vehicle_photo"),order.optString("photo_vehicle"))),new LinearLayout.LayoutParams(0,dp(150),1));
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(-1,-2); plp.setMargins(0,dp(12),0,dp(12)); driverCard.addView(photos,plp);
        driverCard.addView(info("Nama driver", first(order.optString("driver"),order.optString("driver_username"),"Belum ada driver")));
        driverCard.addView(info("Kendaraan", vehicleLabel(first(order.optString("driver_type"),order.optString("vehicle_type"),"-"))));
        driverCard.addView(info("Nomor polisi", first(order.optString("driver_plate"),order.optString("plate"),"-")));
        double rating=order.optDouble("driver_rating",0); driverCard.addView(info("Rating", rating>0?"★ "+String.format(Locale.US,"%.1f",rating):"Belum ada rating"));
        addCard(driverCard);

        LinearLayout route=card(); route.addView(sectionTitle("Rincian Perjalanan"));
        route.addView(info("Order ID",first(order.optString("order_id"),order.optString("id"),"-")));
        route.addView(info("Pickup",first(order.optString("pickup_address"),order.optString("from_address"),order.optString("restaurant_name"),"-")));
        route.addView(info("Tujuan",first(order.optString("delivery_address"),order.optString("to_address"),order.optString("destination"),"-")));
        route.addView(info("Metode pembayaran",paymentLabel(order.optString("payment_method"))));
        route.addView(info("Total dibayar",rupiah(order.optDouble("price",0))));
        addCard(route);

        double price=order.optDouble("price",0), original=order.optDouble("original_price",price), requested=order.optDouble("price_change_requested",0);
        String pcs=order.optString("price_change_status","none").toLowerCase(Locale.US), reason=order.optString("price_change_reason","").trim();
        if(Math.abs(original-price)>0.5 || "pending".equals(pcs) || !reason.isEmpty()) {
            LinearLayout change=card(); change.addView(sectionTitle("Perubahan Harga"));
            change.addView(info("Harga awal",rupiah(original)));
            if("pending".equals(pcs)) change.addView(info("Harga diajukan",rupiah(requested)));
            else change.addView(info("Harga akhir",rupiah(price)));
            change.addView(info("Status",priceStatus(pcs)));
            if(!reason.isEmpty()) change.addView(info("Catatan driver",reason));
            addCard(change);
        }

        actionBox=card(); actionBox.addView(sectionTitle("Tindakan Pesanan"));
        String statusRaw=order.optString("status","").toLowerCase(Locale.US);
        boolean received=order.optInt("customer_received",0)==1;
        if("arrived_delivery".equals(statusRaw) && !received) {
            Button receive=primary("✓ Terima Pesanan"); receive.setOnClickListener(v->confirmAction("confirm_received","Terima pesanan ini?","Pastikan pesanan sudah Anda terima dengan baik.")); actionBox.addView(receive,buttonLp());
        }
        if("pending".equals(pcs)) {
            Button approve=primary("Setujui Harga "+rupiah(requested)); approve.setOnClickListener(v->confirmAction("approve_price","Setujui perubahan harga?","Total pesanan akan berubah menjadi "+rupiah(requested)+".")); actionBox.addView(approve,buttonLp());
            Button reject=outline("Tolak Perubahan Harga"); reject.setOnClickListener(v->confirmAction("reject_price","Tolak perubahan harga?","Harga pesanan tidak akan dinaikkan.")); actionBox.addView(reject,buttonLp());
        }
        if(actionBox.getChildCount()==1) actionBox.addView(text(received?"Pesanan sudah dikonfirmasi diterima.":"Belum ada tindakan yang diperlukan.",15,Color.DKGRAY,false));
        addCard(actionBox);
    }

    private void confirmAction(String action,String title,String message){ new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Batal",null).setPositiveButton("Ya",(d,w)->sendAction(action)).show(); }
    private void sendAction(String action){
        progress.setVisibility(View.VISIBLE);
        new Thread(()->{ try{
            JSONObject p=new JSONObject(); p.put("order_id",first(order.optString("order_id"),order.optString("id"))); p.put("source",order.optString("source","").contains("pickup")?"pickup_orders":"orders"); p.put("action",action);
            JSONObject r=post(ACTION_URL,p); boolean ok=r.optBoolean("success",false); String msg=first(r.optString("message"),ok?"Berhasil":"Gagal");
            if(ok){ if("confirm_received".equals(action)) order.put("customer_received",1); else if("approve_price".equals(action)){ order.put("price",order.optDouble("price_change_requested",order.optDouble("price",0))); order.put("price_change_status","approved"); } else if("reject_price".equals(action)) order.put("price_change_status","rejected"); }
            main.post(()->{progress.setVisibility(View.GONE); new AlertDialog.Builder(this).setTitle(ok?"Berhasil":"Gagal").setMessage(msg).setPositiveButton("OK",null).show(); if(ok) render();});
        }catch(Exception e){main.post(()->{progress.setVisibility(View.GONE); new AlertDialog.Builder(this).setTitle("Gagal").setMessage("Koneksi server bermasalah.").setPositiveButton("OK",null).show();});}},"detail-action").start();
    }

    private ImageView photoBox(String raw){ ImageView v=new ImageView(this); v.setScaleType(ImageView.ScaleType.CENTER_CROP); v.setImageResource(android.R.drawable.ic_menu_gallery); v.setBackground(round("#EAF2FB",18)); String u=absoluteUrl(raw); if(!u.isEmpty()) loadImage(v,u); return v; }
    private void loadImage(ImageView v,String u){ new Thread(()->{ try(InputStream in=new URL(u).openStream()){ Bitmap b=BitmapFactory.decodeStream(in); if(b!=null) main.post(()->v.setImageBitmap(b)); }catch(Exception ignored){} },"detail-photo").start(); }
    private String absoluteUrl(String p){
        if(p==null||p.trim().isEmpty()) return "";
        p=p.trim();
        if(p.startsWith("http://")||p.startsWith("https://")) return p;
        while(p.startsWith("/")) p=p.substring(1);
        // Samakan dengan DriverProfileActivity: file driver tersimpan di server/uploads/drivers/...
        if(p.startsWith("uploads/")) return BASE_URL+"server/"+p;
        if(p.startsWith("server/")) return BASE_URL+p;
        return BASE_URL+p;
    }
    private JSONObject post(String url,JSONObject payload)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(20000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8"); try(OutputStream o=c.getOutputStream()){o.write(payload.toString().getBytes(StandardCharsets.UTF_8));} InputStream in=c.getResponseCode()>=400?c.getErrorStream():c.getInputStream(); StringBuilder b=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(in))){String line;while((line=r.readLine())!=null)b.append(line);} c.disconnect(); return new JSONObject(b.toString()); }

    private LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(18),dp(18),dp(18));x.setBackground(round("#FFFFFF",22));return x;}
    private void addCard(View v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));body.addView(v,lp);}
    private TextView sectionTitle(String s){TextView t=text(s,19,Color.rgb(12,67,120),true);t.setPadding(0,0,0,dp(8));return t;}
    private TextView info(String k,String val){TextView t=text(k+"\n"+val,15,Color.rgb(45,57,72),false);t.setLineSpacing(0,1.08f);t.setPadding(0,dp(7),0,dp(7));return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(round("#167DF5",16));return b;}
    private Button outline(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setTextColor(Color.rgb(18,105,190));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(roundStroke("#FFFFFF","#8CC7F7",16));return b;}
    private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(10),0,0);return lp;}
    private GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(c));g.setCornerRadius(dp(r));return g;}
    private GradientDrawable roundStroke(String c,String s,int r){GradientDrawable g=round(c,r);g.setStroke(dp(1),Color.parseColor(s));return g;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String rupiah(double n){return "Rp"+NumberFormat.getNumberInstance(new Locale("id","ID")).format(Math.round(n));}
    private String first(String...v){for(String s:v)if(s!=null&&!s.trim().isEmpty()&&!"null".equalsIgnoreCase(s.trim()))return s.trim();return "";}
    private String paymentLabel(String s){s=first(s,"cash").toLowerCase(Locale.US);return s.contains("balance")||s.contains("transpay")||s.contains("wallet")||s.contains("saldo")?"TransPay (Non-tunai)":"Tunai";}
    private String vehicleLabel(String s){s=first(s,"-").toLowerCase(Locale.US);return s.equals("car")||s.equals("mobil")?"Mobil / Car":s.equals("bike")||s.equals("motor")?"Motor / Bike":s;}
    private String priceStatus(String s){if("pending".equals(s))return "Menunggu konfirmasi Anda";if("approved".equals(s))return "Disetujui";if("rejected".equals(s))return "Ditolak";return "Tidak ada pengajuan";}
    private String prettyStatus(String s){s=first(s,"-").replace('_',' ');StringBuilder b=new StringBuilder();for(String p:s.split(" ")){if(p.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}
}
