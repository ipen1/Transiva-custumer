package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

/** Explicit reauthentication, single-flight requests, and lifecycle/session guarded completion. */
public final class CustomerAccountDeletionController {
    private static final int GOOGLE_DELETE = 5983;
    private static final Map<Activity,Flow> OPEN = new WeakHashMap<>();
    public interface Listener { void onBusyChanged(boolean busy); void onError(String message); }
    private CustomerAccountDeletionController() { }
    public static void show(Activity activity, Listener listener) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || OPEN.containsKey(activity)) return;
        Flow flow = new Flow(activity, listener); OPEN.put(activity, flow); flow.show();
    }
    public static boolean handleGoogleResult(Activity activity,int requestCode,int resultCode,Intent data) {
        if(requestCode != GOOGLE_DELETE) return false;
        Flow flow=OPEN.get(activity); if(flow==null) return true;
        flow.googleWaiting=false;if(flow.googleDeadline!=null)flow.handler.removeCallbacks(flow.googleDeadline); flow.busy(false);
        if(!flow.current())return true;
        if(resultCode!=Activity.RESULT_OK || data==null){flow.error("Konfirmasi Google dibatalkan. Akun Anda belum dihapus.");return true;}
        try {
            GoogleSignInAccount account=GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            String token=account==null?null:account.getIdToken();
            if(token==null || token.isEmpty())flow.error("Konfirmasi Google belum berhasil. Pilih kembali akun Anda.");
            else flow.request("",token);
        } catch(Exception error){flow.error("Konfirmasi Google belum berhasil. Pilih kembali akun Anda.");}
        return true;
    }
    private static final class Flow implements Application.ActivityLifecycleCallbacks {
        final Activity activity;final Listener listener;final CustomerSessionGuard owner;
        final Handler handler=new Handler(Looper.getMainLooper());
        final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
        AlertDialog dialog;EditText phrase,password;TextView status;Button google;
        boolean working,googleWaiting,closed;long generation;Future<?> task;
        volatile HttpURLConnection connection;Runnable googleDeadline;
        Flow(Activity activity,Listener listener){this.activity=activity;this.listener=listener;owner=CustomerSessionGuard.capture(activity);}
        boolean current(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed()&&owner.matches(activity);}
        int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
        void show(){
            LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));
            TextView explanation=new TextView(activity);explanation.setText("Pastikan saldo Rp0 dan semua pesanan selesai. Data akun akan dihapus permanen. Sebagian catatan transaksi tetap disimpan untuk riwayat pembayaran.");explanation.setTextSize(14);box.addView(explanation);
            phrase=new EditText(activity);phrase.setHint("Ketik HAPUS AKUN");phrase.setSingleLine(true);phrase.setSaveEnabled(false);box.addView(phrase);
            password=new EditText(activity);password.setHint("Kata sandi akun");password.setSingleLine(true);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setSaveEnabled(false);box.addView(password);
            google=new Button(activity);google.setText("Konfirmasi dengan Google");google.setAllCaps(false);box.addView(google);
            TextView hint=new TextView(activity);hint.setText("Daftar memakai Google? Ketik HAPUS AKUN, lalu pilih Google yang sama. Tidak perlu memasukkan kata sandi.");hint.setTextSize(12);box.addView(hint);
            status=new TextView(activity);status.setTextColor(android.graphics.Color.rgb(180,35,53));status.setTextSize(13);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);box.addView(status);
            ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(box);
            dialog=new TransivaAlertDialogBuilder(activity).setTitle("Hapus akun permanen").setView(scroll)
                    .setNegativeButton("Batal",null).setPositiveButton("Hapus dengan kata sandi",null).create();
            dialog.setOnDismissListener(d->close());activity.getApplication().registerActivityLifecycleCallbacks(this);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(working||!confirmed())return;
                String pass=password.getText().toString();
                if(pass.isEmpty()){password.setError("Masukkan kata sandi, atau gunakan Google di bawah.");return;}
                request(pass,"");
            });
            google.setOnClickListener(v->{if(!working&&confirmed())startGoogle();});
        }
        boolean confirmed(){
            if(!current())return false;
            if(!"HAPUS AKUN".equalsIgnoreCase(phrase.getText().toString().trim())){phrase.setError("Ketik HAPUS AKUN untuk melanjutkan.");phrase.requestFocus();return false;}
            return true;
        }
        void startGoogle(){
            int id=activity.getResources().getIdentifier("google_web_client_id","string",activity.getPackageName());
            String client=id==0?"":activity.getString(id).trim();
            if(client.isEmpty()||client.startsWith("REPLACE_")){error("Konfirmasi melalui Google belum tersedia. Gunakan kata sandi atau hubungi bantuan Transiva.");return;}
            busy(true);googleWaiting=true;
            com.google.android.gms.auth.api.signin.GoogleSignInClient clientApi=GoogleSignIn.getClient(activity,
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestIdToken(client).build());
            clientApi.signOut().addOnCompleteListener(activity,result->{
                if(!current())return;
                try{activity.startActivityForResult(clientApi.getSignInIntent(),GOOGLE_DELETE);}
                catch(Exception e){googleWaiting=false;busy(false);error("Google belum bisa dibuka. Silakan coba lagi.");}
            });
            googleDeadline=()->{if(current()&&googleWaiting){googleWaiting=false;busy(false);error("Konfirmasi Google belum selesai. Silakan coba lagi.");}};handler.postDelayed(googleDeadline,90000L);
        }
        void request(String pass,String googleToken){
            if(!current()||working)return;
            busy(true);status.setText("Sedang menghapus akun…");final long attempt=++generation;
            final Runnable deadline=()->{
                if(!current()||attempt!=generation||!working)return;
                generation++;if(task!=null)task.cancel(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();
                busy(false);error("Kami belum bisa memastikan penghapusan selesai. Coba lagi dari halaman ini; aplikasi akan memeriksa hasil sebelumnya.");
            };
            handler.postDelayed(deadline,45000L);
            task=scope.execute(()->{
                JSONObject result;final HttpURLConnection[] owned=new HttpURLConnection[1];
                try {
                    JSONObject body=new JSONObject().put("confirm","HAPUS AKUN").put("password",pass);
                    if(!googleToken.isEmpty())body.put("google_id_token",googleToken);
                    result=AuthRequestJson.post(activity,"customer_delete_account.php",body,owner,c->{owned[0]=c;connection=c;});
                }catch(Exception e){result=AuthRequestJson.failure("Kami belum bisa memastikan penghapusan selesai. Periksa internet, lalu coba lagi dari halaman ini.");}
                finally{if(connection==owned[0])connection=null;}
                final JSONObject response=result;
                scope.post(handler,()->{
                    if(!current()||attempt!=generation)return;handler.removeCallbacks(deadline);busy(false);
                    if(!response.optBoolean("success")){error(response.optString("message"));return;}
                    CustomerProfileCache.clear(activity);
                    new SessionManager(activity).markLoggedOut("account_deleted");SecureTokenStore.clear(activity);
                    close();dialog.dismiss();
                    Intent intent=new Intent(activity,LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra("account_deleted",true);activity.startActivity(intent);activity.finish();
                });
            },()->scope.post(handler,()->{if(current()&&attempt==generation){handler.removeCallbacks(deadline);busy(false);error("Aplikasi sedang sibuk. Tunggu sebentar, lalu coba lagi.");}}));
        }
        void busy(boolean value){working=value;if(listener!=null&&current())listener.onBusyChanged(value);
            phrase.setEnabled(!value);password.setEnabled(!value);google.setEnabled(!value);
            dialog.setCancelable(!value);dialog.setCanceledOnTouchOutside(!value);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!value);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!value);
        }
        void error(String message){status.setText(CustomerErrorMessages.humanize(message,"Akun belum berhasil dihapus. Silakan coba lagi sebentar."));}
        void close(){if(closed)return;closed=true;generation++;handler.removeCallbacksAndMessages(null);scope.destroy();
            HttpURLConnection c=connection;if(c!=null)c.disconnect();OPEN.remove(activity);activity.getApplication().unregisterActivityLifecycleCallbacks(this);
            if(listener!=null&&!activity.isFinishing()&&!activity.isDestroyed()&&owner.matches(activity))listener.onBusyChanged(false);
        }
        public void onActivityDestroyed(Activity a){if(a==activity){close();if(dialog!=null&&dialog.isShowing())dialog.dismiss();}}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}
        public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
    }
}
