package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Global Search + Transiva AI intent suggestions. */
public class GlobalSearchActivity extends Activity {
    private static final String URL="https://transiva.my.id/server/customer_global_search.php?q=";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable pending;
    private LinearLayout results, chips;
    private EditText query;
    private TextView aiCopy, countText;
    private ProgressBar loading;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try { getWindow().setStatusBarColor(Color.parseColor("#071426")); } catch(Exception ignored){}
        build();
        String initial=getIntent()==null?"":safe(getIntent().getStringExtra("ai_prompt"));
        if(!initial.isEmpty()) query.setText(initial); else search("");
    }

    private void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#F4F8FD"));
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(18),dp(18),dp(18)); hero.setBackground(bg("#0878F9",0,0));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=tx("‹",32,"#FFFFFF",true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(dp(40),dp(42)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tx("Cari di Transiva",22,"#FFFFFF",true)); titles.addView(tx("Tulis kebutuhan Anda, Transiva AI bantu carikan.",12,"#DCEEFF",false));
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1)); hero.addView(top);

        LinearLayout searchBox=new LinearLayout(this); searchBox.setGravity(Gravity.CENTER_VERTICAL); searchBox.setPadding(dp(13),0,dp(10),0); searchBox.setBackground(bg("#FFFFFF",18,0));
        TextView icon=tx("⌕",23,"#0878F9",true); searchBox.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(54)));
        query=new EditText(this); query.setSingleLine(true); query.setTextSize(15); query.setTextColor(Color.parseColor("#0F172A")); query.setHintTextColor(Color.parseColor("#94A3B8")); query.setHint("Contoh: mau pulang, lapar, cari motor..."); query.setBackgroundColor(Color.TRANSPARENT); query.setPadding(0,0,0,0);
        searchBox.addView(query,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView clear=tx("×",24,"#64748B",false); clear.setGravity(Gravity.CENTER); clear.setOnClickListener(v->query.setText("")); searchBox.addView(clear,new LinearLayout.LayoutParams(dp(36),dp(54)));
        LinearLayout.LayoutParams sbLp=new LinearLayout.LayoutParams(-1,dp(54)); sbLp.setMargins(0,dp(14),0,0); hero.addView(searchBox,sbLp); root.addView(hero);

        ScrollView scroll=new ScrollView(this); LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(14),dp(16),dp(24)); scroll.addView(body);
        LinearLayout ai=new LinearLayout(this); ai.setOrientation(LinearLayout.HORIZONTAL); ai.setGravity(Gravity.CENTER_VERTICAL); ai.setPadding(dp(14),dp(12),dp(14),dp(12)); ai.setBackground(bgStroke("#FFFFFF","#D5E8FF",18,1));
        TextView sparkle=tx("✦",23,"#0878F9",true); sparkle.setGravity(Gravity.CENTER); sparkle.setBackground(bg("#EAF4FF",14,0)); ai.addView(sparkle,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout copy=new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1); cp.setMargins(dp(10),0,0,0); ai.addView(copy,cp);
        copy.addView(tx("Transiva AI",12,"#0B3A78",true)); aiCopy=tx("Saya bisa memahami kata seperti ‘lapar’, ‘pulang’, ‘mobil’, atau ‘belanja’.",12,"#64748B",false); copy.addView(aiCopy); body.addView(ai);

        TextView st=tx("Saran untuk Anda",13,"#0B3A78",true); st.setPadding(0,dp(15),0,dp(7)); body.addView(st);
        HorizontalScrollView hsc=new HorizontalScrollView(this); hsc.setHorizontalScrollBarEnabled(false); chips=new LinearLayout(this); chips.setOrientation(LinearLayout.HORIZONTAL); hsc.addView(chips); body.addView(hsc,new LinearLayout.LayoutParams(-1,dp(45)));

        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView resultTitle=tx("Hasil pencarian",15,"#0B3A78",true); countText=tx("",11,"#7890AA",false); row.addView(resultTitle,new LinearLayout.LayoutParams(0,-2,1)); row.addView(countText); LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.setMargins(0,dp(17),0,dp(8)); body.addView(row,rlp);
        loading=new ProgressBar(this); loading.setVisibility(View.GONE); body.addView(loading,new LinearLayout.LayoutParams(dp(32),dp(32)));
        results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); body.addView(results);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);

        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ if(pending!=null)handler.removeCallbacks(pending); pending=()->search(s.toString()); handler.postDelayed(pending,280);} public void afterTextChanged(Editable e){}});
    }

    private void search(String q){
        loading.setVisibility(View.VISIBLE);
        final String queryText=safe(q);
        TransivaNetworkExecutor.execute(()->{ try{
            HttpURLConnection c=CustomerApiClient.open(this,URL+URLEncoder.encode(queryText,"UTF-8")); c.setRequestMethod("GET");
            JSONObject o=read(c); JSONArray a=o.optJSONArray("results"), s=o.optJSONArray("ai_suggestions"); String intent=o.optString("ai_intent","");
            runOnUiThread(()->render(a,s,intent,queryText));
        }catch(Exception e){ runOnUiThread(()->{loading.setVisibility(View.GONE); renderError();}); }});
    }

    private void render(JSONArray a,JSONArray suggestions,String intent,String q){
        loading.setVisibility(View.GONE); results.removeAllViews(); chips.removeAllViews();
        if(!intent.isEmpty()) aiCopy.setText(aiSentence(intent)); else if(q.isEmpty()) aiCopy.setText("Ceritakan kebutuhan Anda. Saya akan arahkan ke layanan, makanan, atau merchant yang paling cocok."); else aiCopy.setText("Saya juga mencoba kata dan maksud yang mirip agar hasilnya lebih relevan.");
        if(suggestions!=null) for(int i=0;i<suggestions.length();i++){ String s=suggestions.optString(i,""); if(!s.isEmpty()) addChip(s); }
        int n=a==null?0:a.length(); countText.setText(n+" hasil");
        if(n==0){ LinearLayout empty=card(); empty.addView(tx("Belum menemukan hasil",16,"#0B3A78",true)); empty.addView(tx("Coba kata lain seperti ‘lapar’, ‘motor’, ‘mobil’, ‘belanja’, atau nama merchant.",12,"#64748B",false)); results.addView(empty); return; }
        for(int i=0;i<n;i++){ JSONObject x=a.optJSONObject(i); if(x==null)continue; addResult(x); }
    }

    private void addChip(String text){
        TextView v=tx(text,12,"#0B6DD9",true); v.setGravity(Gravity.CENTER); v.setPadding(dp(14),0,dp(14),0); v.setBackground(bgStroke("#EEF6FF","#CBE3FF",18,1)); v.setOnClickListener(x->{query.setText(text);query.setSelection(query.length());}); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38)); lp.setMargins(0,0,dp(8),0); chips.addView(v,lp);
    }

    private void addResult(JSONObject x){
        String kind=x.optString("kind",""), name=x.optString("name",""), sub=x.optString("subtitle","");
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(13),dp(11),dp(12),dp(11)); card.setBackground(bgStroke("#FFFFFF","#E0ECF8",17,1));
        TextView ico=tx(iconFor(kind),22,"#0878F9",true); ico.setGravity(Gravity.CENTER); ico.setBackground(bg("#EFF6FF",14,0)); card.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout copy=new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams lpC=new LinearLayout.LayoutParams(0,-2,1); lpC.setMargins(dp(11),0,dp(8),0); card.addView(copy,lpC); copy.addView(tx(name,15,"#0F172A",true)); copy.addView(tx(sub.isEmpty()?labelFor(kind):sub,11,"#64748B",false));
        TextView go=tx("›",26,"#0878F9",true); card.addView(go); card.setOnClickListener(v->open(kind,name)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(9)); results.addView(card,lp);
    }

    private void open(String kind,String name){ hideKeyboard(); if("service".equals(kind)){ Class<?> c=TransRideActivity.class; if(name.equalsIgnoreCase("TransCar"))c=PassengerCarActivity.class; else if(name.equalsIgnoreCase("TransFood"))c=TransFoodActivity.class; else if(name.equalsIgnoreCase("TransShop"))c=TransShopActivity.class; else if(name.equalsIgnoreCase("TransSend")){ try{ c=Class.forName("com.transiva.app.TransSendActivity"); }catch(Exception ignored){} } startActivity(new Intent(this,c)); return; } Intent i=new Intent(this,TransFoodActivity.class); i.putExtra("global_search_query",name); startActivity(i); }
    private String aiSentence(String intent){ if("food".equals(intent))return "Sepertinya Anda sedang mencari makanan. Saya prioritaskan TransFood dan pilihan yang berhubungan."; if("ride".equals(intent))return "Saya menangkap kebutuhan perjalanan motor. TransRide dan opsi pulang/ke kantor saya naikkan ke atas."; if("car".equals(intent))return "Saya menangkap kebutuhan perjalanan mobil. Saya prioritaskan TransCar dan perjalanan nyaman."; if("shop".equals(intent))return "Sepertinya Anda ingin titip belanja. Saya arahkan ke TransShop dan kata terkait."; if("send".equals(intent))return "Sepertinya Anda ingin mengirim barang. Saya arahkan ke layanan kirim Transiva."; return "Saya mencari layanan yang paling sesuai."; }
    private String iconFor(String k){return "service".equals(k)?"✦":("merchant".equals(k)?"🏪":"🍜");} private String labelFor(String k){return "service".equals(k)?"Layanan Transiva":("merchant".equals(k)?"Merchant":"Menu makanan");}
    private void renderError(){results.removeAllViews();LinearLayout c=card();c.addView(tx("Pencarian belum tersedia",15,"#B45309",true));c.addView(tx("Periksa koneksi lalu coba lagi.",12,"#64748B",false));results.addView(c);}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(bgStroke("#FFFFFF","#E0ECF8",17,1));return c;}
    private JSONObject read(HttpURLConnection c)throws Exception{InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String l;while((l=br.readLine())!=null)s.append(l);return new JSONObject(s.toString());}
    private TextView tx(String s,int z,String color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(color));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private GradientDrawable bg(String color,int radius,int ignored){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(color));g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable bgStroke(String fill,String stroke,int radius,int width){GradientDrawable g=bg(fill,radius,0);g.setStroke(dp(width),Color.parseColor(stroke));return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private String safe(String s){return s==null?"":s.trim();}
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);}catch(Exception ignored){}}
}
