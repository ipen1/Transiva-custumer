package com.transiva.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;

final class CustomerEmailVerification {
    private final Activity activity;private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
    private final Handler handler=new Handler(Looper.getMainLooper());private final CustomerSessionGuard session;
    private Button send,verify;private EditText otp;private TextView status;private String proof="";private boolean busy;private long resendAt;
    private AlertDialog openDialog;
    private volatile java.net.HttpURLConnection connection;
    private CustomerEmailVerification(Activity a){activity=a;session=CustomerSessionGuard.capture(a);}
    static void show(Activity activity,Runnable refreshed){new CustomerEmailVerification(activity).open(refreshed);}
    private void open(Runnable refreshed){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(1);int pad=Math.round(24*activity.getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);
        status=new TextView(activity);status.setText("Kode akan dikirim ke email yang tersimpan di akun Anda.");status.setTextSize(15);box.addView(status);
        otp=new EditText(activity);otp.setHint("6 digit OTP");otp.setInputType(InputType.TYPE_CLASS_NUMBER);otp.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});otp.setSaveEnabled(false);box.addView(otp);
        send=new Button(activity);send.setAllCaps(false);send.setText("Kirim kode");box.addView(send);
        verify=new Button(activity);verify.setAllCaps(false);verify.setText("Verifikasi email");box.addView(verify);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Verifikasi email").setView(box).setNegativeButton("Tutup",null).create();
        openDialog=dialog;
        activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
        dialog.setOnDismissListener(d->{activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);openDialog=null;scope.destroy();handler.removeCallbacksAndMessages(null);java.net.HttpURLConnection c=connection;if(c!=null)c.disconnect();});
        send.setOnClickListener(v->{if(busy)return;if(android.os.SystemClock.elapsedRealtime()<resendAt){status.setText("Tunggu sebelum mengirim ulang kode.");return;}proof="";request("sendEmailOtp.php",new JSONObject(),r->{resendAt=android.os.SystemClock.elapsedRealtime()+r.optInt("retry_after",60)*1000L;if(r.optInt("email_verified",0)==1){refreshed.run();dialog.dismiss();}});});
        verify.setOnClickListener(v->{if(busy)return;if(!proof.isEmpty()){consume(dialog,refreshed);return;}String code=otp.getText().toString().trim();if(!code.matches("[0-9]{6}")){status.setText("Masukkan 6 digit kode.");return;}JSONObject body=new JSONObject();try{body.put("otp",code);}catch(Exception ignored){}request("verifyEmailOtp.php",body,r->{proof=r.optString("verification_proof","");if(!proof.isEmpty())consume(dialog,refreshed);});});
        dialog.show();
    }
    private void consume(AlertDialog dialog,Runnable refreshed){JSONObject b=new JSONObject();try{b.put("verification_proof",proof);}catch(Exception ignored){}request("consumeEmailProof.php",b,r->{refreshed.run();Toast.makeText(activity,"Email terverifikasi",Toast.LENGTH_SHORT).show();dialog.dismiss();});}
    interface Success {void apply(JSONObject result);}
    private void request(String endpoint,JSONObject body,Success success){
        if(!session.matches(activity))return;setBusy(true);
        scope.execute(()->{JSONObject result;try{result=AuthRequestJson.post(activity,endpoint,body,session,c->connection=c);}catch(Exception e){result=AuthRequestJson.failure("Hasil belum terkonfirmasi. Coba kembali.");}finally{connection=null;}final JSONObject r=result;scope.post(handler,()->{if(activity.isFinishing()||!session.matches(activity))return;setBusy(false);status.setText(r.optString("message","Silakan coba kembali."));if(r.optBoolean("success"))success.apply(r);else if("EMAIL_PROOF_EXPIRED".equals(r.optString("code"))){proof="";}else if(r.optInt("retry_after",0)>0)resendAt=android.os.SystemClock.elapsedRealtime()+r.optInt("retry_after")*1000L;});},()->scope.post(handler,()->{setBusy(false);status.setText("Antrean jaringan penuh. Coba kembali.");}));
    }
    private final android.app.Application.ActivityLifecycleCallbacks lifecycle=new android.app.Application.ActivityLifecycleCallbacks(){
        public void onActivityDestroyed(Activity a){if(a==activity&&openDialog!=null)openDialog.dismiss();}
        public void onActivityCreated(Activity a,android.os.Bundle b){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,android.os.Bundle b){}
    };
    private void setBusy(boolean value){busy=value;send.setEnabled(!value);verify.setEnabled(!value);otp.setEnabled(!value);verify.setText(value?"Memproses…":"Verifikasi email");}
}
