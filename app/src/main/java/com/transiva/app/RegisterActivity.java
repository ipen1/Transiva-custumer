package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Three-step signup. Email verification belongs to the signed-in account. */
public class RegisterActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
    private volatile HttpURLConnection activeConnection;
    private EditText username,email,phone,password,confirm;
    private LinearLayout[] steps=new LinearLayout[3];
    private TextView heading,description,position,message;
    private Button next,back; private ProgressBar progress;
    private int step=0; private boolean busy=false;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(244,248,255));
        getWindow().setNavigationBarColor(Color.rgb(244,248,255));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        build();
        CustomerResponsiveUi.apply(this);
        if(saved!=null){username.setText(saved.getString("username",""));email.setText(saved.getString("email",""));phone.setText(saved.getString("phone",""));step=Math.min(2,saved.getInt("step",0));}
        showStep();
    }
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(244,248,255));
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);outer.setGravity(Gravity.CENTER_HORIZONTAL);outer.setPadding(dp(20),dp(30),dp(20),dp(28));scroll.addView(outer);
        TextView brand=text("TRANSIVA",15,true);brand.setTextColor(Color.rgb(28,117,233));brand.setLetterSpacing(.18f);outer.addView(brand);
        TextView welcome=text("Perjalanan baru dimulai",27,true);welcome.setGravity(Gravity.CENTER);welcome.setPadding(0,dp(12),0,dp(24));outer.addView(welcome);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(24),dp(24),dp(24),dp(24));card.setBackground(bg("#FFFFFF",24));card.setElevation(dp(4));
        int width=Math.min(dp(480),getResources().getDisplayMetrics().widthPixels-dp(40));outer.addView(card,new LinearLayout.LayoutParams(width,-2));
        position=text("",12,true);position.setTextColor(Color.rgb(28,117,233));card.addView(position);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(3);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(5));pl.setMargins(0,dp(12),0,dp(20));card.addView(progress,pl);
        heading=text("",24,true);card.addView(heading);
        description=text("",14,false);description.setTextColor(Color.rgb(100,116,139));description.setPadding(0,dp(8),0,dp(20));card.addView(description);
        message=text("",14,false);message.setTextColor(Color.rgb(185,28,28));message.setVisibility(View.GONE);card.addView(message);
        for(int i=0;i<3;i++){steps[i]=new LinearLayout(this);steps[i].setOrientation(1);card.addView(steps[i]);}
        username=field(steps[0],"Nama pengguna","Contoh: meipen",InputType.TYPE_CLASS_TEXT);
        email=field(steps[1],"Email","nama@email.com",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        phone=field(steps[1],"Nomor HP","0812… atau 62812…",InputType.TYPE_CLASS_PHONE);
        password=field(steps[2],"Kata sandi","Minimal 8 karakter",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm=field(steps[2],"Ulangi kata sandi","Pastikan sama",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);confirm.setSaveEnabled(false);
        CheckBox visible=new CheckBox(this);visible.setText("Tampilkan kata sandi");steps[2].addView(visible);visible.setOnCheckedChangeListener((v,on)->{int type=InputType.TYPE_CLASS_TEXT|(on?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setInputType(type);confirm.setInputType(type);});
        TextView note=text("Verifikasi email tersedia di menu Akun setelah masuk.",12,false);note.setTextColor(Color.rgb(100,116,139));note.setPadding(0,dp(12),0,dp(8));steps[2].addView(note);
        next=button("Lanjut →",true);card.addView(next,buttonLp());next.setOnClickListener(v->advance());
        back=button("← Sebelumnya",false);card.addView(back,buttonLp());back.setOnClickListener(v->{if(!busy&&step>0){step--;showStep();}});
        Button google=button("Lanjutkan dengan Google",false);card.addView(google,buttonLp());google.setOnClickListener(v->{if(busy)return;Intent in=new Intent(this,LoginActivity.class);in.putExtra("start_google",true);startActivity(in);});
        TextView login=text("Sudah punya akun? Masuk",14,true);login.setGravity(Gravity.CENTER);login.setPadding(0,dp(20),0,dp(8));card.addView(login);login.setOnClickListener(v->{if(!busy)openLogin();});
        TextView legal=text("Dengan membuat akun, Anda menyetujui Syarat & Ketentuan dan Kebijakan Privasi Transiva.",12,false);legal.setGravity(Gravity.CENTER);legal.setPadding(0,dp(24),0,0);outer.addView(legal);
        Button terms=button("Syarat & Ketentuan • Kebijakan Privasi",false);outer.addView(terms,buttonLp());terms.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setItems(new String[]{"Syarat & Ketentuan","Kebijakan Privasi"},(d,which)->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://transiva.my.id/"+(which==0?"terms.html":"privacy-policy.html"))))).show());
        username.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_NEXT){advance();return true;}return false;});
        confirm.setImeOptions(EditorInfo.IME_ACTION_DONE);confirm.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE){advance();return true;}return false;});
        setContentView(scroll);
    }
    private void showStep(){
        String[] titles={"Kenalan dulu, yuk","Ke mana kami menghubungi?","Amankan akun Anda"};
        String[] desc={"Pilih nama pengguna untuk masuk ke Transiva.","Isi email dan nomor HP yang Anda gunakan.","Buat kata sandi yang hanya Anda ketahui."};
        heading.setText(titles[step]);description.setText(desc[step]);position.setText("LANGKAH "+(step+1)+" DARI 3");progress.setProgress(step+1);
        for(int i=0;i<3;i++)steps[i].setVisibility(i==step?View.VISIBLE:View.GONE);
        back.setVisibility(step>0?View.VISIBLE:View.GONE);next.setText(step==2?"Buat akun →":"Lanjut →");message.setVisibility(View.GONE);
    }
    private void advance(){
        if(busy)return;
        String u=username.getText().toString().trim().toLowerCase(Locale.US),e=email.getText().toString().trim().toLowerCase(Locale.US),p=phone.getText().toString().replaceAll("\\D","");
        if(!u.matches("[a-z0-9_.-]{3,30}")){step=0;showStep();error("Nama pengguna harus 3–30 karakter: huruf, angka, titik, strip, underscore.");return;}
        if(step==0){step=1;showStep();email.requestFocus();return;}
        if(!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()||e.length()>254){error("Isi email yang valid.");return;}
        if(p.startsWith("0"))p=p.substring(1);if(!p.startsWith("62"))p="62"+p;
        if(!p.matches("62[0-9]{9,13}")){error("Isi nomor HP yang valid.");return;}
        if(step==1){step=2;showStep();password.requestFocus();return;}
        String pass=password.getText().toString();
        if(pass.codePointCount(0,pass.length())<8||pass.getBytes(StandardCharsets.UTF_8).length>72){error("Kata sandi minimal 8 karakter, maksimal 72 byte.");return;}
        if(!pass.equals(confirm.getText().toString())){error("Konfirmasi kata sandi belum sama.");return;}
        try{
            JSONObject payload=new JSONObject();payload.put("username",u);payload.put("email",e);payload.put("phone",p);
            String digest=AuthRequestJson.sha256(payload.toString());
            android.content.SharedPreferences prefs=getSharedPreferences("signup_retry",MODE_PRIVATE);
            String operation=digest.equals(prefs.getString("digest",""))?prefs.getString("operation",""):"";
            if(operation.isEmpty()){operation="signup-"+UUID.randomUUID();if(!prefs.edit().putString("digest",digest).putString("operation",operation).commit()){error("ID pendaftaran belum tersimpan. Coba kembali.");return;}}
            payload.put("password",pass);payload.put("operation_id",operation);setBusy(true);
            scope.execute(()->{
                JSONObject result;
                try{result=AuthRequestJson.post(this,"register.php",payload,null,c->activeConnection=c);}
                catch(Exception ex){result=AuthRequestJson.failure("Hasil belum terkonfirmasi. Coba lagi dengan data yang sama.");}
                finally{activeConnection=null;}
                final JSONObject response=result;
                scope.post(handler,()->{if(isFinishing())return;setBusy(false);if(response.optBoolean("success")){getSharedPreferences("signup_retry",MODE_PRIVATE).edit().clear().apply();Toast.makeText(this,"Akun siap. Masuk dan verifikasi email di menu Akun.",Toast.LENGTH_LONG).show();openLogin();}else {if("REGISTRATION_ID_CONFLICT".equals(response.optString("code")))getSharedPreferences("signup_retry",MODE_PRIVATE).edit().clear().apply();error(response.optString("message","Belum berhasil. Coba kembali."));}});
            },()->scope.post(handler,()->{setBusy(false);error("Antrean jaringan penuh. Coba kembali sebentar lagi.");}));
        }catch(Exception ex){setBusy(false);error("Pendaftaran belum dapat diproses.");}
    }
    private void setBusy(boolean value){busy=value;next.setEnabled(!value);back.setEnabled(!value);for(EditText f:new EditText[]{username,email,phone,password,confirm})f.setEnabled(!value);next.setText(value?"Membuat akun…":step==2?"Buat akun →":"Lanjut →");}
    private void error(String msg){message.setText(msg);message.setVisibility(View.VISIBLE);}
    private void openLogin(){Intent in=new Intent(this,LoginActivity.class);in.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(in);finish();}
    @Override public void onBackPressed(){if(busy){Toast.makeText(this,"Pendaftaran sedang diproses…",Toast.LENGTH_SHORT).show();return;}if(step>0){step--;showStep();}else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("step",step);b.putString("username",username.getText().toString());b.putString("email",email.getText().toString());b.putString("phone",phone.getText().toString());super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){scope.destroy();handler.removeCallbacksAndMessages(null);HttpURLConnection c=activeConnection;if(c!=null)c.disconnect();super.onDestroy();}
    private EditText field(LinearLayout box,String name,String hint,int type){TextView label=text(name,14,true);label.setPadding(0,dp(12),0,dp(8));box.addView(label);EditText f=new EditText(this);f.setTextSize(16);f.setSingleLine(true);f.setHint(hint);f.setInputType(type);f.setPadding(dp(16),dp(12),dp(16),dp(12));f.setBackground(bg("#F3F7FD",14));f.setImeOptions(EditorInfo.IME_ACTION_NEXT);f.setSaveEnabled(false);f.setMinimumHeight(dp(56));box.addView(f,new LinearLayout.LayoutParams(-1,-2));return f;}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(18,47,78));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(56));b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setTextColor(Color.parseColor(primary?"#FFFFFF":"#1668C7"));b.setBackground(bg(primary?"#1676ED":"#EEF5FF",16));return b;}
    private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);return lp;}
    private GradientDrawable bg(String color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(radius));return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
