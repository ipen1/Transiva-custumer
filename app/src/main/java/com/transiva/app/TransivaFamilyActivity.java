package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.widget.*;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class TransivaFamilyActivity extends Activity {
    private static final String URL="https://transiva.my.id/server/customer_family.php";
    private static final int REQ_CONTACT=4102;
    private LinearLayout list;
    private Button addButton;
    private TextView tierText, quotaText;
    private EditText dialogName, dialogPhone;
    private Spinner dialogRelation;
    private int editingId=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        try{getWindow().setStatusBarColor(Color.parseColor("#071426"));}catch(Exception ignored){}
        build(); load();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#F4F8FD"));
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(18),dp(18),dp(18)); hero.setBackground(bg("#0878F9",0));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); TextView back=tx("‹",32,"#FFFFFF",true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(dp(40),dp(42)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.addView(tx("Transiva Family",22,"#FFFFFF",true)); titles.addView(tx("Pesankan perjalanan untuk orang terdekat dengan lebih praktis.",12,"#DCEEFF",false)); top.addView(titles,new LinearLayout.LayoutParams(0,-2,1)); hero.addView(top);
        LinearLayout memberInfo=new LinearLayout(this); memberInfo.setOrientation(LinearLayout.VERTICAL); memberInfo.setPadding(dp(14),dp(12),dp(14),dp(12)); memberInfo.setBackground(bg("#FFFFFF",17));
        tierText=tx("★ Member Bronze",13,"#0B3A78",true); quotaText=tx("Memuat kapasitas Family...",12,"#64748B",false); memberInfo.addView(tierText); memberInfo.addView(quotaText); LinearLayout.LayoutParams mi=new LinearLayout.LayoutParams(-1,-2); mi.setMargins(0,dp(14),0,0); hero.addView(memberInfo,mi); root.addView(hero);

        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(16),dp(15),dp(16),dp(24));
        LinearLayout ai=new LinearLayout(this); ai.setOrientation(LinearLayout.HORIZONTAL); ai.setGravity(Gravity.CENTER_VERTICAL); ai.setPadding(dp(14),dp(12),dp(14),dp(12)); ai.setBackground(bgStroke("#FFFFFF","#D7E9FF",18,1));
        TextView ico=tx("✦",23,"#0878F9",true); ico.setGravity(Gravity.CENTER); ico.setBackground(bg("#EAF4FF",14)); ai.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout cp=new LinearLayout(this); cp.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cplp=new LinearLayout.LayoutParams(0,-2,1); cplp.setMargins(dp(10),0,0,0); ai.addView(cp,cplp); cp.addView(tx("Family pintar",12,"#0B3A78",true)); cp.addView(tx("Bronze mendapat 1 slot. Setiap kenaikan tier membuka 1 slot keluarga tambahan.",12,"#64748B",false)); body.addView(ai);
        addButton=button("＋ Tambah anggota keluarga",true); addButton.setOnClickListener(v->edit(0,"","","Keluarga")); LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,dp(50)); alp.setMargins(0,dp(14),0,dp(12)); body.addView(addButton,alp);
        ScrollView sc=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sc.addView(list); body.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }

    private void load(){
        TransivaNetworkExecutor.execute(()->{try{JSONObject r=get(URL+"?action=list");runOnUiThread(()->render(r));}catch(Exception e){runOnUiThread(()->showError("Gagal memuat Transiva Family."));}});
    }

    private void render(JSONObject r){
        JSONArray a=r.optJSONArray("members"); String tier=r.optString("tier","BRONZE"); int count=r.optInt("member_count",a==null?0:a.length()), max=r.optInt("max_members",1); boolean can=r.optBoolean("can_add",count<max);
        tierText.setText("★ Member "+prettyTier(tier)); quotaText.setText(count+" dari "+max+" slot Family digunakan • "+unlockText(tier,max)); addButton.setEnabled(can); addButton.setAlpha(can?1f:.55f); addButton.setText(can?"＋ Tambah anggota keluarga":"🔒 Slot Family penuh • naik tier untuk tambah");
        list.removeAllViews();
        if(a==null||a.length()==0){LinearLayout empty=card();empty.addView(tx("Belum ada anggota Family",16,"#0B3A78",true));empty.addView(tx("Tambahkan 1 orang terdekat agar Anda bisa langsung memesan perjalanan atas nama mereka.",12,"#64748B",false));list.addView(empty);return;}
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;addMember(x);}
    }

    private void addMember(JSONObject x){
        int id=x.optInt("id"); String name=x.optString("name","Keluarga"), phone=x.optString("phone",""), rel=x.optString("relationship","Keluarga");
        LinearLayout c=card(); LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); TextView avatar=tx(initial(name),18,"#FFFFFF",true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(bg("#0878F9",18)); head.addView(avatar,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout copy=new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1); cp.setMargins(dp(11),0,0,0); head.addView(copy,cp); copy.addView(tx(name,16,"#0F172A",true)); copy.addView(tx(rel+(phone.isEmpty()?"":" • "+phone),12,"#64748B",false)); c.addView(head);
        LinearLayout rideRow=new LinearLayout(this); Button motor=button("🏍 Motor",false), car=button("🚗 Mobil",false); rideRow.addView(motor,new LinearLayout.LayoutParams(0,dp(46),1)); LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(46),1);clp.setMargins(dp(8),0,0,0);rideRow.addView(car,clp);LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.setMargins(0,dp(12),0,0);c.addView(rideRow,rlp);
        LinearLayout tools=new LinearLayout(this); Button edit=smallButton("Edit"), del=smallButton("Hapus"); tools.addView(edit,new LinearLayout.LayoutParams(0,dp(40),1)); LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(0,dp(40),1);dlp.setMargins(dp(8),0,0,0);tools.addView(del,dlp);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.setMargins(0,dp(8),0,0);c.addView(tools,tlp);
        motor.setOnClickListener(v->openRide(TransRideActivity.class,id,name)); car.setOnClickListener(v->openRide(PassengerCarActivity.class,id,name)); edit.setOnClickListener(v->edit(id,name,phone,rel)); del.setOnClickListener(v->confirmDelete(id,name)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));list.addView(c,lp);
    }

    private void openRide(Class<?> cls,int id,String name){Intent in=new Intent(this,cls);in.putExtra("family_member_id",id);in.putExtra("family_member_name",name);startActivity(in);}

    private void edit(int id,String n,String p,String r){
        editingId=id; LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(4),0,dp(4),0);
        TextView helper=tx(id>0?"Perbarui data anggota Family.":"Nomor HP bisa diambil langsung dari kontak perangkat.",12,"#64748B",false);box.addView(helper);
        dialogName=new EditText(this);dialogName.setHint("Nama anggota");dialogName.setText(n);box.addView(dialogName,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView relTitle=tx("Hubungan",12,"#0B3A78",true);relTitle.setPadding(0,dp(8),0,dp(4));box.addView(relTitle);
        dialogRelation=new Spinner(this);String[] opts={"Suami/Istri","Anak","Keluarga"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);dialogRelation.setAdapter(adapter);for(int i=0;i<opts.length;i++)if(opts[i].equalsIgnoreCase(r))dialogRelation.setSelection(i);box.addView(dialogRelation,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout phoneRow=new LinearLayout(this); phoneRow.setGravity(Gravity.CENTER_VERTICAL); dialogPhone=new EditText(this);dialogPhone.setHint("Nomor HP");dialogPhone.setText(p);dialogPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);phoneRow.addView(dialogPhone,new LinearLayout.LayoutParams(0,dp(54),1));Button contact=smallButton("Kontak");contact.setOnClickListener(v->pickContact());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(88),dp(44));cp.setMargins(dp(8),0,0,0);phoneRow.addView(contact,cp);box.addView(phoneRow);
        new TransivaAlertDialogBuilder(this).setTitle(id>0?"Edit anggota Family":"Tambah anggota Family").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->saveDialog()).show();
    }

    private void pickContact(){
        try{Intent i=new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);startActivityForResult(i,REQ_CONTACT);}catch(Exception e){Toast.makeText(this,"Pemilih kontak tidak tersedia.",Toast.LENGTH_SHORT).show();}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_CONTACT||resultCode!=RESULT_OK||data==null)return;Uri uri=data.getData();if(uri==null)return;Cursor c=null;try{c=getContentResolver().query(uri,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null);if(c!=null&&c.moveToFirst()){String name=c.getString(0),phone=c.getString(1);if(dialogName!=null&&dialogName.getText().toString().trim().isEmpty())dialogName.setText(name);if(dialogPhone!=null)dialogPhone.setText(normalizePhone(phone));}}catch(Exception e){Toast.makeText(this,"Kontak tidak bisa dibaca.",Toast.LENGTH_SHORT).show();}finally{if(c!=null)c.close();}}

    private void saveDialog(){
        try{String name=dialogName==null?"":dialogName.getText().toString().trim();String phone=dialogPhone==null?"":normalizePhone(dialogPhone.getText().toString());String rel=dialogRelation==null?"Keluarga":String.valueOf(dialogRelation.getSelectedItem());if(name.isEmpty()){Toast.makeText(this,"Nama anggota wajib diisi.",Toast.LENGTH_SHORT).show();return;}JSONObject o=new JSONObject();o.put("action","save");o.put("name",name);o.put("phone",phone);o.put("relationship",rel);if(editingId>0)o.put("id",editingId);send(o);}catch(Exception ignored){}
    }
    private void confirmDelete(int id,String name){new TransivaAlertDialogBuilder(this).setTitle("Hapus anggota?").setMessage(name+" akan dihapus dari Transiva Family.").setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->{try{JSONObject o=new JSONObject();o.put("action","delete");o.put("id",id);send(o);}catch(Exception ignored){}}).show();}
    private void send(JSONObject o){TransivaNetworkExecutor.execute(()->{try{JSONObject r=post(URL,o);runOnUiThread(()->{Toast.makeText(this,r.optString("message","Selesai"),Toast.LENGTH_LONG).show();if(r.optBoolean("success"))load();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Gagal menyimpan Family",Toast.LENGTH_SHORT).show());}});}
    private void showError(String s){list.removeAllViews();LinearLayout c=card();c.addView(tx(s,14,"#B45309",true));list.addView(c);}
    private String unlockText(String tier,int max){if("PLATINUM".equalsIgnoreCase(tier))return "kapasitas maksimum terbuka";String next="SILVER";if("SILVER".equalsIgnoreCase(tier))next="GOLD";else if("GOLD".equalsIgnoreCase(tier))next="DIAMOND";else if("DIAMOND".equalsIgnoreCase(tier))next="PLATINUM";return "naik ke "+next+" untuk membuka slot ke-"+(max+1);}
    private String prettyTier(String t){String s=t==null?"Bronze":t.toLowerCase();return Character.toUpperCase(s.charAt(0))+s.substring(1);} private String initial(String n){String s=n==null?"?":n.trim();return s.isEmpty()?"?":s.substring(0,1).toUpperCase();}
    private String normalizePhone(String p){String s=p==null?"":p.replaceAll("[^0-9+]","");if(s.startsWith("+62"))return "62"+s.substring(3);if(s.startsWith("0"))return "62"+s.substring(1);return s;}
    private JSONObject get(String u)throws Exception{HttpURLConnection c=CustomerApiClient.open(this,u);c.setRequestMethod("GET");return read(c);} private JSONObject post(String u,JSONObject o)throws Exception{HttpURLConnection c=CustomerApiClient.open(this,u);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream os=c.getOutputStream()){os.write(o.toString().getBytes(StandardCharsets.UTF_8));}return read(c);} private JSONObject read(HttpURLConnection c)throws Exception{InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String l;while((l=br.readLine())!=null)s.append(l);return new JSONObject(s.toString());}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(bgStroke("#FFFFFF","#DFEBF7",18,1));c.setElevation(dp(1));return c;}
    private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.parseColor(primary?"#FFFFFF":"#0B6DD9"));b.setBackground(primary?bg("#0878F9",16):bgStroke("#F0F7FF","#CDE4FF",15,1));return b;} private Button smallButton(String text){Button b=button(text,false);b.setTextSize(12);return b;}
    private TextView tx(String s,int z,String color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(color));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;} private GradientDrawable bg(String color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(color));g.setCornerRadius(dp(radius));return g;} private GradientDrawable bgStroke(String fill,String stroke,int radius,int width){GradientDrawable g=bg(fill,radius);g.setStroke(dp(width),Color.parseColor(stroke));return g;} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
