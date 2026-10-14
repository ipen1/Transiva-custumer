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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class RegisterActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
    private volatile HttpURLConnection activeConnection;
    private EditText username,email,phone,password,confirm;
    private final LinearLayout[] steps=new LinearLayout[3];
    private TextView heading,description,position,message,usernameStatus,emailStatus,phoneStatus;
    private TextView back,google,login;
    private Button next;private ProgressBar progress;
    private int step;private boolean busy,checking,restoring;
    private AuthPublicRequest lookup;private Runnable debounce;
    private long revision;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(244,248,255));getWindow().setNavigationBarColor(Color.rgb(244,248,255));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        build();CustomerResponsiveUi.apply(this);
        restoring=true;
        if(saved!=null){username.setText(saved.getString("username",""));email.setText(saved.getString("email",""));phone.setText(saved.getString("phone",""));step=Math.max(0,Math.min(2,saved.getInt("step",0)));}
        restoring=false;showStep();scheduleCheck();
    }
    private void build(){
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(244,248,255));scroll.setClipToPadding(false);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(1);outer.setGravity(Gravity.CENTER_HORIZONTAL);outer.setPadding(dp(16),dp(14),dp(16),dp(18));scroll.addView(outer);
        TextView brand=text("TRANSIVA",14,true);brand.setTextColor(Color.rgb(28,117,233));brand.setLetterSpacing(.14f);brand.setPadding(0,dp(4),0,dp(14));outer.addView(brand);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(20),dp(20),dp(20),dp(18));card.setBackground(bg("#FFFFFF",22));card.setElevation(dp(2));
        outer.addView(card,new LinearLayout.LayoutParams(Math.min(dp(440),getResources().getDisplayMetrics().widthPixels-dp(32)),-2));
        position=text("",11,true);position.setTextColor(Color.rgb(28,117,233));card.addView(position);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28,117,233)));progress.setMax(3);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(4));pl.setMargins(0,dp(10),0,dp(16));card.addView(progress,pl);
        heading=text("",22,true);card.addView(heading);
        description=text("",13,false);description.setTextColor(Color.rgb(100,116,139));description.setPadding(0,dp(6),0,dp(12));card.addView(description);
        message=text("",13,false);message.setTextColor(Color.rgb(185,28,28));message.setPadding(dp(10),dp(9),dp(10),dp(9));message.setBackground(bg("#FFF1F2",10));message.setVisibility(View.GONE);card.addView(message);
        for(int i=0;i<3;i++){steps[i]=new LinearLayout(this);steps[i].setOrientation(1);card.addView(steps[i]);}
        username=field(steps[0],"Nama pengguna","Contoh: meipen",InputType.TYPE_CLASS_TEXT);
        usernameStatus=status(steps[0]);
        email=field(steps[1],"Email","nama@email.com",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);emailStatus=status(steps[1]);
        phone=field(steps[1],"Nomor HP","0812… atau 62812…",InputType.TYPE_CLASS_PHONE);phoneStatus=status(steps[1]);
        password=field(steps[2],"Kata sandi","Minimal 8 karakter",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm=field(steps[2],"Ulangi kata sandi","Pastikan sama",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox visible=new CheckBox(this);visible.setText("Tampilkan kata sandi");visible.setTextSize(13);visible.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28,117,233)));steps[2].addView(visible);
        visible.setOnCheckedChangeListener((v,on)->{int type=InputType.TYPE_CLASS_TEXT|(on?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setInputType(type);confirm.setInputType(type);password.setSelection(password.length());confirm.setSelection(confirm.length());});
        TextView note=text("Verifikasi email dilakukan di menu Akun setelah masuk.",12,false);note.setTextColor(Color.rgb(100,116,139));note.setPadding(0,dp(5),0,dp(4));steps[2].addView(note);
        next=new Button(this);next.setAllCaps(false);next.setTextSize(15);next.setTypeface(Typeface.DEFAULT_BOLD);next.setTextColor(Color.WHITE);next.setMinHeight(dp(48));next.setPadding(dp(12),dp(10),dp(12),dp(10));next.setStateListAnimator(null);next.setBackground(bg("#1676ED",14));LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(-1,-2);nl.topMargin=dp(14);card.addView(next,nl);next.setOnClickListener(v->advance());
        back=link("← Sebelumnya");card.addView(back);back.setOnClickListener(v->{if(!busy&&step>0){cancelCheck();step--;showStep();scheduleCheck();}});
        google=link("Lanjutkan dengan Google");google.setBackground(bg("#F2F6FC",12));card.addView(google);google.setOnClickListener(v->{if(busy)return;cancelCheck();Intent in=new Intent(this,LoginActivity.class);in.putExtra("start_google",true);startActivity(in);});
        login=link("Sudah punya akun? Masuk");card.addView(login);login.setOnClickListener(v->{if(!busy){cancelCheck();openLogin();}});
        TextView legal=link("Syarat & Ketentuan  •  Kebijakan Privasi");legal.setTextSize(11);outer.addView(legal);legal.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setItems(new String[]{"Syarat & Ketentuan","Kebijakan Privasi"},(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://transiva.my.id/"+(w==0?"terms.html":"privacy-policy.html"))))).show());
        for(EditText f:new EditText[]{username,email,phone})f.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){if(restoring)return;cancelCheck();clearMessage();usernameStatus.setText("");emailStatus.setText("");phoneStatus.setText("");scheduleCheck();}});
        username.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_NEXT){advance();return true;}return false;});
        phone.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_NEXT){advance();return true;}return false;});
        confirm.setImeOptions(EditorInfo.IME_ACTION_DONE);confirm.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE){advance();return true;}return false;});
        setContentView(scroll);
    }
    private void showStep(){
        String[] titles={"Buat identitas Anda","Lengkapi kontak Anda","Amankan akun Anda"};String[] descriptions={"Pilih nama pengguna yang belum digunakan.","Gunakan email dan nomor HP aktif.","Buat kata sandi minimal 8 karakter."};
        heading.setText(titles[step]);description.setText(descriptions[step]);position.setText("LANGKAH "+(step+1)+" DARI 3");progress.setProgress(step+1);
        for(int i=0;i<3;i++)steps[i].setVisibility(i==step?View.VISIBLE:View.GONE);back.setVisibility(step>0?View.VISIBLE:View.GONE);clearMessage();updateButton();
        scroll.post(()->{if(!isFinishing())scroll.smoothScrollTo(0,0);});
    }
    private JSONObject fields(boolean requireValid)throws Exception{
        JSONObject fields=new JSONObject();
        if(step==0){String u=username.getText().toString().trim().toLowerCase(Locale.US);if(u.matches("[a-z0-9_.-]{3,30}"))fields.put("username",u);else if(requireValid)username.setError("Gunakan 3–30 huruf, angka, titik, strip, atau underscore.");}
        else if(step==1){String e=email.getText().toString().trim().toLowerCase(Locale.US),p=normalizePhone(phone.getText().toString());
            if(android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()&&e.length()<=254)fields.put("email",e);else if(requireValid)email.setError("Isi email yang valid.");
            if(p.matches("62[0-9]{9,13}"))fields.put("phone",p);else if(requireValid)phone.setError("Isi nomor HP yang valid.");}
        return fields;
    }
    private void scheduleCheck(){if(busy||step==2||scope.isDestroyed())return;debounce=()->check(false);handler.postDelayed(debounce,650L);}
    private void check(boolean advance){
        if(busy||scope.isDestroyed())return;
        final JSONObject fields;
        try{fields=fields(advance);}catch(Exception e){return;}
        if(fields.length()==0||advance&&fields.length()!=(step==0?1:2))return;
        cancelCheck();final long version=revision;final int expectedStep=step;
        checking=true;updateButton();
        for(String field:new String[]{"username","email","phone"})if(fields.has(field))setStatus(field,"Memeriksa…",false,false);
        JSONObject payload=new JSONObject();try{payload.put("fields",fields);}catch(Exception ignored){}
        AuthPublicRequest request=new AuthPublicRequest();lookup=request;
        request.start(this,"customer_auth_check.php",payload,response->{
            if(scope.isDestroyed()||isFinishing()||lookup!=request||version!=revision||step!=expectedStep)return;
            lookup=null;checking=false;updateButton();
            if(!response.optBoolean("success")){for(String field:new String[]{"username","email","phone"})if(fields.has(field))setStatus(field,"Belum dapat diperiksa",false,false);error(response.optString("message","Pemeriksaan belum berhasil. Coba kembali."));return;}
            JSONObject results=response.optJSONObject("fields");boolean available=true;
            for(String field:new String[]{"username","email","phone"})if(fields.has(field)){JSONObject value=results==null?null:results.optJSONObject(field);boolean ok=value!=null&&value.optBoolean("valid")&&value.optBoolean("available");available&=ok;setStatus(field,value==null?"Pemeriksaan belum lengkap.":value.optString("message"),ok,!ok);}
            if(available){clearMessage();if(advance){step++;showStep();if(step==1){email.requestFocus();scheduleCheck();}else password.requestFocus();}}
            else if(advance)error("Data sudah digunakan atau belum valid. Perbaiki sebelum melanjutkan.");
        });
    }
    private void advance(){
        if(busy)return;if(step<2){check(true);return;}
        String u=username.getText().toString().trim().toLowerCase(Locale.US),e=email.getText().toString().trim().toLowerCase(Locale.US),p=normalizePhone(phone.getText().toString());
        String pass=password.getText().toString();
        if(pass.codePointCount(0,pass.length())<8||pass.getBytes(StandardCharsets.UTF_8).length>72){password.setError("Minimal 8 karakter, maksimal 72 byte.");return;}
        if(!pass.equals(confirm.getText().toString())){confirm.setError("Konfirmasi kata sandi belum sama.");return;}
        cancelCheck();clearMessage();setBusy(true);
        scope.execute(()->{
            JSONObject response;
            try{
                JSONObject payload=new JSONObject();payload.put("username",u);payload.put("email",e);payload.put("phone",p);
                String operation=SignupOperationStore.operation(getApplicationContext(),payload.toString());
                payload.put("password",pass);payload.put("operation_id",operation);
                response=AuthRequestJson.post(this,"register.php",payload,null,c->activeConnection=c);
            }catch(Exception error){response=AuthRequestJson.userFailure(error,true);}finally{activeConnection=null;}
            final JSONObject result=response;
            scope.post(handler,()->{if(isFinishing())return;setBusy(false);
                if(result.optBoolean("success")){getSharedPreferences("signup_retry",MODE_PRIVATE).edit().clear().apply();Toast.makeText(this,"Akun siap. Silakan masuk.",Toast.LENGTH_LONG).show();openLogin();return;}
                if("REGISTRATION_ID_CONFLICT".equals(result.optString("code")))getSharedPreferences("signup_retry",MODE_PRIVATE).edit().clear().apply();
                JSONObject conflicts=result.optJSONObject("field_errors");if(conflicts!=null&&conflicts.length()>0){step=conflicts.has("username")?0:1;showStep();for(String f:new String[]{"username","email","phone"})if(conflicts.has(f))setStatus(f,conflicts.optString(f),false,true);}
                error(result.optString("message","Pendaftaran belum berhasil. Coba kembali."));
            });
        },()->scope.post(handler,()->{setBusy(false);error("Antrean jaringan penuh. Coba kembali sebentar lagi.");}));
    }
    private void cancelCheck(){revision++;if(debounce!=null)handler.removeCallbacks(debounce);if(lookup!=null)lookup.cancel();lookup=null;checking=false;if(next!=null)updateButton();}
    private void updateButton(){next.setEnabled(!busy);next.setText(busy?"Membuat akun…":checking?"Memeriksa…":step==2?"Buat akun →":"Lanjut →");}
    private void setBusy(boolean value){busy=value;for(EditText f:new EditText[]{username,email,phone,password,confirm})f.setEnabled(!value);back.setEnabled(!value);google.setEnabled(!value);login.setEnabled(!value);updateButton();}
    private void setStatus(String field,String value,boolean available,boolean conflict){TextView v="username".equals(field)?usernameStatus:"email".equals(field)?emailStatus:phoneStatus;v.setText(value);v.setTextColor(Color.parseColor(available?"#178354":conflict?"#B42335":"#64748B"));}
    private void error(String msg){message.setText(msg);message.setVisibility(View.VISIBLE);}
    private void clearMessage(){message.setVisibility(View.GONE);}
    private static String normalizePhone(String value){String p=value.replaceAll("\\D","");if(p.startsWith("0062"))p=p.substring(2);if(p.startsWith("0"))p=p.substring(1);return p.isEmpty()?"":p.startsWith("62")?p:"62"+p;}
    private void openLogin(){Intent in=new Intent(this,LoginActivity.class);in.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(in);finish();}
    @Override public void onBackPressed(){if(busy){Toast.makeText(this,"Pendaftaran sedang diproses…",Toast.LENGTH_SHORT).show();return;}cancelCheck();if(step>0){step--;showStep();scheduleCheck();}else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("step",step);b.putString("username",username.getText().toString());b.putString("email",email.getText().toString());b.putString("phone",phone.getText().toString());super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){cancelCheck();scope.destroy();handler.removeCallbacksAndMessages(null);HttpURLConnection c=activeConnection;if(c!=null)c.disconnect();super.onDestroy();}
    private EditText field(LinearLayout box,String name,String hint,int type){TextView label=text(name,13,true);label.setPadding(0,dp(10),0,dp(6));box.addView(label);EditText f=new EditText(this);f.setTextSize(15);f.setSingleLine(true);f.setHint(hint);f.setInputType(type);f.setTextColor(Color.rgb(18,47,78));f.setHintTextColor(Color.rgb(145,155,175));f.setPadding(dp(13),dp(11),dp(13),dp(11));f.setBackground(bg("#F3F6FC",12));f.setImeOptions(EditorInfo.IME_ACTION_NEXT);f.setSaveEnabled(false);f.setMinimumHeight(dp(48));box.addView(f,new LinearLayout.LayoutParams(-1,-2));return f;}
    private TextView status(LinearLayout box){TextView v=text("",11,false);v.setMinHeight(dp(20));v.setPadding(dp(2),dp(3),0,0);box.addView(v);return v;}
    private TextView link(String value){TextView v=text(value,13,true);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(44));v.setPadding(dp(10),dp(10),dp(10),dp(10));v.setTextColor(Color.rgb(28,105,198));return v;}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(18,47,78));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable bg(String color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(Color.parseColor(color));d.setCornerRadius(dp(radius));return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
