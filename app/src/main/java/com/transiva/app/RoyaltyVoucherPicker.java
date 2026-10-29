package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
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
                    if(rows==null||rows.length()==0){manual(a,current,callback);return;}
                    String[] labels=new String[rows.length()];
                    for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null){labels[i]="Voucher tidak tersedia";continue;}
                        labels[i]=r.optString("title")+"\n"+(r.optBoolean("eligible")?"Hemat Rp"+String.format(new Locale("id","ID"),"%,d",r.optInt("discount")):r.optString("reason","Belum memenuhi syarat"));}
                    new TransivaAlertDialogBuilder(a).setTitle("Pilih voucher ongkir")
                        .setItems(labels,(dialog,index)->{JSONObject r=rows.optJSONObject(index);if(r==null)return;if(r.optBoolean("eligible"))callback.apply(r.optString("voucher_code"),r.optInt("discount"));else new TransivaAlertDialogBuilder(a).setTitle("Voucher belum bisa dipakai").setMessage(r.optString("reason")).setPositiveButton("OK",null).show();})
                        .setPositiveButton("Masukkan kode",(d,w)->manual(a,current,callback))
                        .setNeutralButton("Hapus voucher",(d,w)->callback.apply("",0)).setNegativeButton("Batal",null).show();
                });
            }catch(Exception e){a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()||!waiting.isShowing())return;waiting.dismiss();new TransivaAlertDialogBuilder(a).setTitle("Voucher belum dimuat").setMessage("Coba lagi atau masukkan kode voucher.").setPositiveButton("Masukkan kode",(d,w)->manual(a,current,callback)).setNegativeButton("Batal",null).show();});}
        },()->a.runOnUiThread(()->{if(waiting.isShowing())waiting.dismiss();}));
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
