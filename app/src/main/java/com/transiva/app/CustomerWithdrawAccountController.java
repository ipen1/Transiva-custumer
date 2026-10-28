package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONObject;

/** One confirmed withdrawal destination per account. All persistence is on the authenticated server. */
public final class CustomerWithdrawAccountController {
    public interface Submit {void send(int amount,String bank,String name,String number,String note);}
    private static final String URL=ApiConfig.ROOT+"server/customer_withdraw_account.php";
    private static final String[] CODES={"BRI","BCA","MANDIRI","SINARMAS","GOPAY","DANA"};
    private static final String[] NAMES={"BRI","BCA","Mandiri","Sinarmas","GoPay","DANA"};
    private static final int[] LOGOS={R.drawable.wd_bank_bri,R.drawable.wd_bank_bca,R.drawable.wd_bank_mandiri,R.drawable.wd_bank_sinarmas,R.drawable.wd_bank_gopay,R.drawable.wd_bank_dana};
    private final Activity activity;
    private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean busy;private AlertDialog current;
    public CustomerWithdrawAccountController(Activity activity){this.activity=activity;}
    public void destroy(){scope.destroy();handler.removeCallbacksAndMessages(null);if(current!=null)current.dismiss();}
    private boolean alive(CustomerSessionGuard owner){return !scope.isDestroyed()&&!activity.isFinishing()&&!activity.isDestroyed()&&owner.matches(activity);}
    public void show(Submit submit){
        if(busy)return;busy=true;final CustomerSessionGuard owner=CustomerSessionGuard.capture(activity);
        Toast.makeText(activity,"Memuat rekening WD…",Toast.LENGTH_SHORT).show();
        scope.execute(()->{
            try{JSONObject result=TransivaHttpRepository.getJsonFresh(activity,URL,15000);scope.post(handler,()->{busy=false;if(!alive(owner))return;if(!result.optBoolean("success")){info(result.optString("message","Rekening belum dapat dimuat."));return;}JSONObject account=result.optJSONObject("account");if(account==null)saveForm(submit);else withdrawForm(account,submit);});}
            catch(Exception e){scope.post(handler,()->{busy=false;if(alive(owner))info(message(e));});}
        },()->scope.post(handler,()->{busy=false;if(alive(owner))info("Antrean jaringan penuh. Coba kembali.");}));
    }
    private void saveForm(Submit submit){
        final CustomerSessionGuard formOwner=CustomerSessionGuard.capture(activity);
        LinearLayout form=form();
        form.addView(text("Pilih tujuan penarikan",15,true));
        Spinner bank=new Spinner(activity);bank.setAdapter(new BankAdapter());form.addView(bank,new LinearLayout.LayoutParams(-1,dp(64)));
        EditText name=field("Nama pemilik rekening",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText number=field("Nomor rekening / HP e-wallet",InputType.TYPE_CLASS_NUMBER);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(150)});number.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(30)});
        form.addView(name);form.addView(number);
        form.addView(text("Rekening ini disimpan sekali pada akun Anda dan otomatis digunakan untuk WD berikutnya, termasuk setelah login di perangkat lain. Periksa semua data sebelum menyimpan.",13,false));
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        current=new TransivaAlertDialogBuilder(activity).setTitle("Simpan rekening WD").setView(scroll).setNegativeButton("Batal",null).setPositiveButton("Periksa & Simpan",null).create();
        final AlertDialog dialog=current;dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            String holder=name.getText().toString().trim(),account=number.getText().toString().trim();
            if(holder.length()<2){name.setError("Isi nama pemilik rekening.");return;}
            if(!account.matches("[0-9]{6,30}")){number.setError("Periksa nomor rekening/HP.");return;}
            final int selected=bank.getSelectedItemPosition();
            new TransivaAlertDialogBuilder(activity).setTitle("Konfirmasi rekening permanen")
                .setMessage(NAMES[selected]+"\n"+holder+"\n"+account+"\n\nPastikan rekening milik Anda. Setelah tersimpan, data tidak dapat diganti sendiri.")
                .setNegativeButton("Periksa lagi",null).setPositiveButton("Simpan permanen",(d,which)->saveAccount(dialog,selected,holder,account,submit,formOwner)).show();
        }));dialog.show();
    }
    private void saveAccount(AlertDialog dialog,int selected,String holder,String number,Submit submit,CustomerSessionGuard owner){
        if(busy||!alive(owner))return;busy=true;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        scope.execute(()->{
            try{JSONObject body=new JSONObject().put("bank_code",CODES[selected]).put("account_name",holder).put("account_number",number).put("confirm_permanent",true);
                JSONObject result=TransivaHttpRepository.postJson(activity,URL,body,15000);
                scope.post(handler,()->{busy=false;if(!alive(owner))return;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    JSONObject account=result.optJSONObject("account");
                    if(!result.optBoolean("success")||account==null){info(result.optString("message","Rekening belum tersimpan. Muat ulang sebelum mencoba kembali."));return;}
                    dialog.dismiss();withdrawForm(account,submit);
                });
            }catch(Exception e){scope.post(handler,()->{busy=false;if(alive(owner)){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);info(message(e)+"\nJika hasil belum pasti, tutup lalu buka WD untuk memeriksa rekening tersimpan.");}});}
        },()->scope.post(handler,()->{busy=false;if(alive(owner)){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);info("Antrean jaringan penuh. Coba kembali.");}}));
    }
    private void withdrawForm(JSONObject account,Submit submit){
        final CustomerSessionGuard formOwner=CustomerSessionGuard.capture(activity);
        LinearLayout form=form();String code=account.optString("bank_code");
        int selected=-1;for(int i=0;i<CODES.length;i++)if(CODES[i].equals(code))selected=i;
        if(selected>=0)form.addView(bankRow(selected));else form.addView(text(account.optString("bank_name"),16,true));
        form.addView(text(account.optString("account_name"),16,true));
        TextView number=text(account.optString("account_number"),16,true);number.setTextIsSelectable(true);form.addView(number);
        form.addView(text("Rekening tersimpan permanen. Pengajuan ini memakai data rekening di atas.",12,false));
        EditText amount=field("Nominal WD (minimal Rp10.000)",InputType.TYPE_CLASS_NUMBER);
        amount.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        EditText note=field("Catatan (opsional)",InputType.TYPE_CLASS_TEXT);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(255)});
        form.addView(amount);form.addView(note);ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        current=new TransivaAlertDialogBuilder(activity).setTitle("Withdraw Dana").setMessage("Saldo akan ditahan sampai admin memproses pencairan.").setView(scroll).setNegativeButton("Batal",null).setPositiveButton("Ajukan WD",null).create();
        final AlertDialog dialog=current;dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            final int value;try{long parsed=Long.parseLong(amount.getText().toString());if(parsed<10000||parsed>Integer.MAX_VALUE)throw new NumberFormatException();value=(int)parsed;}catch(Exception e){amount.setError("Isi nominal minimal Rp10.000 yang valid.");return;}
            if(!alive(formOwner)){dialog.dismiss();return;}
            dialog.dismiss();submit.send(value,account.optString("bank_name"),account.optString("account_name"),account.optString("account_number"),note.getText().toString().trim());
        }));dialog.show();
    }
    private LinearLayout bankRow(int i){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));
        ImageView logo=new ImageView(activity);logo.setImageResource(LOGOS[i]);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription(NAMES[i]);logo.setBackgroundColor(Color.WHITE);
        row.addView(logo,new LinearLayout.LayoutParams(dp(92),dp(38)));TextView label=text(NAMES[i],16,true);label.setPadding(dp(14),0,0,0);row.addView(label);return row;
    }
    private final class BankAdapter extends BaseAdapter implements SpinnerAdapter {
        @Override public int getCount(){return CODES.length;}
        @Override public String getItem(int position){return CODES[position];}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int p,View recycled,ViewGroup parent){return bankRow(p);}
        @Override public View getDropDownView(int p,View recycled,ViewGroup parent){return bankRow(p);}
    }
    private LinearLayout form(){LinearLayout f=new LinearLayout(activity);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(8),dp(20),dp(16));return f;}
    private EditText field(String hint,int type){EditText f=new EditText(activity);f.setHint(hint);f.setTextSize(15);f.setInputType(type);f.setSingleLine(true);f.setMinHeight(dp(52));return f;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setPadding(0,dp(7),0,dp(7));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void info(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private String message(Exception e){return e instanceof TransivaHttpRepository.ServerResponseException?e.getMessage():"Koneksi belum berhasil. Coba kembali.";}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
}
