package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Local favorite places: home, work, and one custom place. */
public class FavoritePlacesActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout list;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("transiva_favorites", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(18)); root.setBackgroundColor(Color.parseColor("#F6F9FE"));
        TextView title = new TextView(this); title.setText("Lokasi Favorit"); title.setTextSize(24); title.setTextColor(Color.parseColor("#0B3A78")); title.setTypeface(null,1); root.addView(title);
        TextView sub = new TextView(this); sub.setText("Simpan alamat agar pemesanan berikutnya lebih cepat."); sub.setTextColor(Color.DKGRAY); root.addView(sub);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list, new LinearLayout.LayoutParams(-1,0,1));
        Button close = button("Selesai"); close.setOnClickListener(v -> finish()); root.addView(close,new LinearLayout.LayoutParams(-1,dp(50)));
        setContentView(root); render();
    }
    private void render() {
        list.removeAllViews(); addRow("home","🏠 Rumah"); addRow("work","🏢 Kantor"); addRow("custom","⭐ Favorit");
    }
    private void addRow(String key,String label) {
        Button b=button(label+"\n"+prefs.getString(key,"Belum disimpan")); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(72)); lp.setMargins(0,dp(12),0,0); list.addView(b,lp); b.setOnClickListener(v->edit(key,label));
    }
    private void edit(String key,String label) {
        EditText input=new EditText(this); input.setHint("Nama jalan, desa, patokan"); input.setText(prefs.getString(key,"")); input.setPadding(dp(16),0,dp(16),0);
        new TransivaAlertDialogBuilder(this).setTitle(label).setView(input).setNegativeButton("Batal",null).setNeutralButton("Hapus",(d,w)->{prefs.edit().remove(key).apply();render();})
                .setPositiveButton("Simpan",(d,w)->{String x=input.getText().toString().trim(); if(!x.isEmpty()){prefs.edit().putString(key,x).apply();Toast.makeText(this,"Lokasi disimpan",Toast.LENGTH_SHORT).show();}render();}).show();
    }
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
