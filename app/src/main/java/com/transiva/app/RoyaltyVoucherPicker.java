package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.*;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Server-authoritative eligibility; supplied amounts are a preview, never payment authority. */
public final class RoyaltyVoucherPicker {
    private RoyaltyVoucherPicker() {}
    public interface Selection { void apply(String code, int discount); }
    public static void show(Activity a,int total,int delivery,String current,Selection callback) {
        AlertDialog waiting=new TransivaAlertDialogBuilder(a).setTitle("Voucher kamu").setMessage("Memeriksa voucher yang bisa dipakai…").setNegativeButton("Batal",null).show();
        TransivaNetworkExecutor.execute(()->{
            try {
                JSONObject body=new JSONObject().put("order_total",Math.max(0,total)).put("delivery_fee",Math.max(0,delivery));
                JSONObject response=TransivaHttpRepository.postJson(a,"https://transiva.my.id/server/customer_royalty_vouchers.php",body,15000);
                if(!response.optBoolean("success"))throw new Exception(response.optString("message","Voucher belum bisa dimuat."));
                a.runOnUiThread(()->{
                    if(a.isFinishing()||a.isDestroyed()||!waiting.isShowing())return;
                    waiting.dismiss();JSONArray rows=response.optJSONArray("vouchers");
                    showOwned(a,rows,current,callback);

                });
            }catch(Exception e){final String reason=e instanceof TransivaHttpRepository.ServerResponseException?e.getMessage():"Koneksi belum stabil. Coba muat kembali voucher.";a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()||!waiting.isShowing())return;waiting.dismiss();new TransivaAlertDialogBuilder(a).setTitle("Voucher belum dimuat").setMessage(reason).setPositiveButton("Masukkan kode",(d,w)->manual(a,current,callback)).setNegativeButton("Batal",null).show();});}
        },()->a.runOnUiThread(()->{if(waiting.isShowing())waiting.dismiss();}));
    }
    private static int dp(Activity a,int value){return (int)(value*a.getResources().getDisplayMetrics().density+.5f);}
    private static TextView label(Activity a,String text,int size,boolean bold){TextView v=new TextView(a);v.setText(text);v.setTextSize(size);v.setTextColor(Color.parseColor(bold?"#0B3A78":"#64748B"));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private static String money(int value){return "Rp"+String.format(new Locale("id","ID"),"%,d",value);}
    private static void showOwned(Activity a,JSONArray rows,String current,Selection callback){
        if(rows==null||rows.length()==0){new TransivaAlertDialogBuilder(a).setTitle("Voucher kamu").setMessage("Belum ada voucher aktif. Tukarkan Poin Transiva untuk mendapatkan voucher.")
            .setPositiveButton("Lihat poin",(d,w)->a.startActivity(new android.content.Intent(a,CustomerLoyaltyActivity.class)))
            .setNeutralButton("Masukkan kode",(d,w)->manual(a,current,callback)).setNegativeButton("Tutup",null).show();return;}
        ScrollView scroll=new ScrollView(a);LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(a,16),0,dp(a,16),0);scroll.addView(list);
        LinearLayout holder=new LinearLayout(a);holder.setOrientation(LinearLayout.VERTICAL);int limit=Math.min(dp(a,340),a.getResources().getDisplayMetrics().heightPixels/2);holder.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(limit,dp(a,rows.length()*110))));
        final AlertDialog dialog=new TransivaAlertDialogBuilder(a).setTitle("Voucher kamu").setView(holder)
            .setPositiveButton("Masukkan kode",(d,w)->manual(a,current,callback)).setNeutralButton("Hapus voucher",(d,w)->callback.apply("",0)).setNegativeButton("Tutup",null).create();
        // Eligible vouchers first; unavailable vouchers remain visible with their reason.
        for(int group=0;group<2;group++)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;boolean eligible=r.optBoolean("eligible");if((group==0)!=eligible)continue;
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(a,10),0,dp(a,10));LinearLayout info=new LinearLayout(a);info.setOrientation(LinearLayout.VERTICAL);
            info.addView(label(a,r.optString("title"),14,true));
            TextView saving=label(a,eligible?"Hemat "+money(r.optInt("discount")):r.optString("reason","Belum memenuhi syarat"),12,false);info.addView(saving);
            info.addView(label(a,"Min. transaksi "+money(r.optInt("min_order")),11,false));
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button use=new Button(a);use.setAllCaps(false);use.setText("Gunakan");use.setTextSize(12);use.setMinHeight(dp(a,44));use.setEnabled(eligible);
            LinearLayout.LayoutParams useLp=new LinearLayout.LayoutParams(-2,-2);useLp.setMargins(dp(a,8),0,0,0);row.addView(use,useLp);
            use.setOnClickListener(v->{dialog.dismiss();callback.apply(r.optString("voucher_code"),r.optInt("discount"));});list.addView(row);
            android.view.View divider=new android.view.View(a);divider.setBackgroundColor(Color.parseColor("#DCE5EF"));list.addView(divider,new LinearLayout.LayoutParams(-1,dp(a,1)));
        }
        // Keep long collections scrollable inside the dialog on small screens.
        dialog.show();
    }
    private static void manual(Activity a,String current,Selection callback){
        EditText field=new EditText(a);field.setSingleLine(true);field.setHint("Kode voucher");field.setText(current);
        new TransivaAlertDialogBuilder(a).setTitle("Gunakan kode voucher").setMessage("Potongan diperiksa server sebelum pesanan dibuat.").setView(field)
            .setPositiveButton("Gunakan",(d,w)->callback.apply(field.getText().toString().trim().toUpperCase(Locale.US),0))
            .setNeutralButton("Hapus",(d,w)->callback.apply("",0)).setNegativeButton("Batal",null).show();
    }
    public interface Preview { void result(int discount,String message); }
    public static void preview(Activity a,String code,int total,int fee,Preview callback){
        if(code==null||code.isEmpty()){callback.result(0,"");return;}
        TransivaNetworkExecutor.execute(()->{try{JSONObject body=new JSONObject().put("order_total",total).put("delivery_fee",fee);JSONObject r=TransivaHttpRepository.postJson(a,"https://transiva.my.id/server/customer_royalty_vouchers.php",body,15000);
            JSONArray rows=r.optJSONArray("vouchers");int discount=0;String message="Potongan kode promo diperiksa kembali saat memesan.";
            if(!r.optBoolean("success"))message="Potongan voucher belum terkonfirmasi. Coba pilih voucher lagi.";
            if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject v=rows.optJSONObject(i);if(v!=null&&code.equalsIgnoreCase(v.optString("voucher_code"))){discount=v.optInt("discount");message=v.optBoolean("eligible")?"Voucher siap digunakan.":v.optString("reason");break;}}
            final int value=discount;final String note=message;a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())callback.result(value,note);});
        }catch(Exception e){a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())callback.result(0,"Potongan voucher belum terkonfirmasi. Coba pilih voucher lagi.");});}});
    }
}
