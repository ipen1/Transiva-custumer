package com.transiva.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;

public class CustomerSupportActivity extends Activity {
    private LinearLayout root, history;
    private EditText subject, detail;
    private Spinner category;
    private Button send;
    private int blue = Color.rgb(20,91,190);
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+.5f); }
    private TextView text(String value, int size) { TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(32,49,72)); v.setPadding(0,dp(9),0,dp(9)); return v; }
    @Override public void onCreate(Bundle b) { super.onCreate(b); getWindow().setStatusBarColor(blue); getWindow().setNavigationBarColor(Color.WHITE);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(246,249,254)); root=new LinearLayout(this); root.setPadding(dp(20),dp(18),dp(20),dp(28)); root.setOrientation(1); scroll.addView(root); setContentView(scroll);
        TextView title=text("‹    🎧  Pusat Bantuan Transiva",21); title.setTextColor(blue); title.setOnClickListener(v->finish()); root.addView(title);
        root.addView(text("Sampaikan kendala Anda. Laporan tersimpan dan dapat ditindaklanjuti admin.",14));
        root.addView(text("Kategori keluhan",14)); category=new Spinner(this); String[] cats={"Pesanan / perjalanan","Pembayaran / saldo","Akun / login","Driver / merchant","Aplikasi / teknis","Lainnya"}; category.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,cats)); root.addView(category);
        subject=new EditText(this); subject.setSingleLine(true); subject.setTextSize(15); subject.setHint("Judul keluhan"); root.addView(subject);
        detail=new EditText(this); detail.setTextSize(15); detail.setGravity(Gravity.TOP); detail.setMinLines(5); detail.setHint("Ceritakan kendala secara lengkap..."); root.addView(detail);
        send=new Button(this); send.setText("Kirim keluhan"); root.addView(send); send.setOnClickListener(v->submit());
        Button wa=new Button(this); wa.setText("Hubungi admin melalui WhatsApp"); root.addView(wa); wa.setOnClickListener(v->{try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/6282393633393?text="+Uri.encode("Halo Admin Transiva, saya membutuhkan bantuan.")))); }catch(Exception e){Toast.makeText(this,"Tidak dapat membuka WhatsApp",Toast.LENGTH_SHORT).show();}});
        root.addView(text("Riwayat keluhan saya",18)); history=new LinearLayout(this); history.setOrientation(1); root.addView(history); load();
    }
    private void submit() { String a=subject.getText().toString().trim(), d=detail.getText().toString().trim(); if(a.length()<5 || d.length()<15){Toast.makeText(this,"Judul minimal 5 dan uraian minimal 15 karakter",Toast.LENGTH_LONG).show();return;} if(a.length()>150 || d.length()>4000){Toast.makeText(this,"Keluhan terlalu panjang",Toast.LENGTH_SHORT).show();return;}
        send.setEnabled(false); try {JSONObject body=new JSONObject();body.put("category",category.getSelectedItem().toString());body.put("subject",a);body.put("detail",d); request("POST",body, result->{send.setEnabled(true);if(result.optBoolean("success")){subject.setText("");detail.setText("");Toast.makeText(this,"Keluhan berhasil dikirim",Toast.LENGTH_SHORT).show();load();}else Toast.makeText(this,result.optString("message","Gagal mengirim keluhan"),Toast.LENGTH_LONG).show();});}catch(Exception e){send.setEnabled(true);}
    }
    private interface Result {void done(JSONObject data);}
    private void request(String method,JSONObject payload,Result callback){TransivaNetworkExecutor.execute(()->{JSONObject result=new JSONObject();HttpURLConnection c=null;try{c=CustomerApiClient.open(this,ApiConfig.server("customer_support_native.php"));c.setConnectTimeout(12000);c.setReadTimeout(16000);c.setRequestProperty("Accept","application/json");if("POST".equals(method)){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");try(OutputStream o=c.getOutputStream()){o.write(payload.toString().getBytes("UTF-8"));}} InputStream in=(c.getResponseCode()<400?c.getInputStream():c.getErrorStream());ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);result=new JSONObject(out.toString("UTF-8"));}catch(Exception e){try{result.put("message","Koneksi bermasalah. Silakan coba lagi.");}catch(Exception ignored){}}finally{if(c!=null)c.disconnect();}JSONObject finalResult=result;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())callback.done(finalResult);});});}
    private void load(){history.removeAllViews();history.addView(text("Memuat riwayat...",13));request("GET",null,r->{history.removeAllViews();if(!r.optBoolean("success")){history.addView(text(r.optString("message","Riwayat belum tersedia"),13));return;}JSONArray rows=r.optJSONArray("tickets");if(rows==null||rows.length()==0){history.addView(text("Belum ada keluhan.",13));return;}for(int i=0;i<rows.length();i++){JSONObject t=rows.optJSONObject(i);if(t==null)continue;TextView item=text("#"+t.optInt("id")+"  •  "+t.optString("status")+"\n"+t.optString("subject")+"\n"+t.optString("created_at")+(t.optString("admin_reply").isEmpty()?"":"\nBalasan admin: "+t.optString("admin_reply")),14);item.setBackgroundColor(Color.WHITE);item.setPadding(dp(12),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);history.addView(item,lp);}});}
}
