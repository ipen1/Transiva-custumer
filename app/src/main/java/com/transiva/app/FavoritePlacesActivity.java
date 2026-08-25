package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Smart Home/Work/Favorite + Transiva AI one-tap ride. */
public class FavoritePlacesActivity extends Activity {
    private static final String URL="https://transiva.my.id/server/customer_favorites.php";
    private static final int REQ_LOCATION=5108;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout list, aiQuick;
    private EditText dialogLabel, dialogLocation;
    private String editingType="favorite";
    private double editingLat=0, editingLng=0;
    private boolean saveAfterLocation=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        try{getWindow().setStatusBarColor(Color.parseColor("#071426"));}catch(Exception ignored){}
        build(); load();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.parseColor(themeColor("#F4F8FD")));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(bg("#0878F9",0));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=tx("‹",32,"#FFFFFF",true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(40),dp(42)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(tx("Smart Favorit",22,"#FFFFFF",true));titles.addView(tx("Rumah, kantor, dan tempat favorit siap dipakai sekali tap.",12,"#DCEEFF",false));top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));hero.addView(top);
        LinearLayout ai=new LinearLayout(this);ai.setOrientation(LinearLayout.HORIZONTAL);ai.setGravity(Gravity.CENTER_VERTICAL);ai.setPadding(dp(14),dp(12),dp(14),dp(12));ai.setBackground(bg("#FFFFFF",17));TextView spark=tx("✦",23,"#0878F9",true);spark.setGravity(Gravity.CENTER);spark.setBackground(bg("#EAF4FF",14));ai.addView(spark,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cplp=new LinearLayout.LayoutParams(0,-2,1);cplp.setMargins(dp(10),0,0,0);ai.addView(cp,cplp);cp.addView(tx("Transiva AI Favorit",12,"#0B3A78",true));cp.addView(tx("Pilih Motor atau Mobil. Titik jemput akan diambil dari lokasi Anda dan tujuan langsung diisi.",12,"#64748B",false));LinearLayout.LayoutParams ailp=new LinearLayout.LayoutParams(-1,-2);ailp.setMargins(0,dp(14),0,0);hero.addView(ai,ailp);root.addView(hero);

        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(14),dp(16),dp(24));sc.addView(body);
        body.addView(tx("Aksi pintar",14,"#0B3A78",true));aiQuick=new LinearLayout(this);aiQuick.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(-1,-2);qlp.setMargins(0,dp(8),0,0);body.addView(aiQuick,qlp);
        LinearLayout quick=new LinearLayout(this);Button home=button("＋ Rumah",false),work=button("＋ Kantor",false),fav=button("＋ Favorit",false);quick.addView(home,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(0,dp(46),1);wlp.setMargins(dp(7),0,0,0);quick.addView(work,wlp);LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(0,dp(46),1);flp.setMargins(dp(7),0,0,0);quick.addView(fav,flp);LinearLayout.LayoutParams klp=new LinearLayout.LayoutParams(-1,-2);klp.setMargins(0,dp(14),0,dp(13));body.addView(quick,klp);home.setOnClickListener(v->edit("home","Rumah"));work.setOnClickListener(v->edit("work","Kantor"));fav.setOnClickListener(v->edit("favorite","Favorit"));
        body.addView(tx("Lokasi tersimpan",14,"#0B3A78",true));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,-2);llp.setMargins(0,dp(8),0,0);body.addView(list,llp);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void load(){TransivaNetworkExecutor.execute(()->{try{JSONObject r=get(URL+"?action=list");runOnUiThread(()->render(r.optJSONArray("places")));}catch(Exception e){runOnUiThread(()->showError("Gagal memuat Smart Favorit."));}});}

    private void render(JSONArray a){
        list.removeAllViews();aiQuick.removeAllViews();JSONObject home=null,work=null;
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String type=x.optString("type","favorite");if("home".equals(type))home=x;if("work".equals(type))work=x;addPlace(x);}
        if(home!=null)addAiAction("🏠","Pulang ke Rumah","Tujuan rumah sudah siap. Pilih kendaraan Anda.",home);else addMissing("🏠","Simpan Rumah agar AI bisa membuat tombol ‘Pulang ke Rumah’.","home","Rumah");
        if(work!=null)addAiAction("🏢","Ke Kantor","Titik kantor siap dipakai untuk perjalanan berikutnya.",work);else addMissing("🏢","Simpan Kantor agar perjalanan kerja cukup sekali tap.","work","Kantor");
        if(a==null||a.length()==0){LinearLayout empty=card();empty.addView(tx("Belum ada lokasi tersimpan",15,"#0B3A78",true));empty.addView(tx("Simpan Rumah atau Kantor. Saat menyimpan, kolom lokasi dapat diisi otomatis dari GPS.",12,"#64748B",false));list.addView(empty);}
    }

    private void addAiAction(String icon,String title,String sub,JSONObject place){
        LinearLayout c=card();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView ico=tx(icon,21,"#0878F9",true);ico.setGravity(Gravity.CENTER);ico.setBackground(bg("#EEF6FF",14));head.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cplp=new LinearLayout.LayoutParams(0,-2,1);cplp.setMargins(dp(10),0,0,0);head.addView(cp,cplp);cp.addView(tx(title,15,"#0B3A78",true));cp.addView(tx(sub,11,"#64748B",false));c.addView(head);
        LinearLayout row=new LinearLayout(this);Button motor=button("🏍 Motor",true),car=button("🚗 Mobil",false);row.addView(motor,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(46),1);clp.setMargins(dp(8),0,0,0);row.addView(car,clp);LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.setMargins(0,dp(10),0,0);c.addView(row,rlp);motor.setOnClickListener(v->openSmartRide(TransRideActivity.class,place));car.setOnClickListener(v->openSmartRide(PassengerCarActivity.class,place));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));aiQuick.addView(c,lp);
    }
    private void addMissing(String icon,String message,String type,String label){LinearLayout c=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView ico=tx(icon,20,"#94A3B8",true);row.addView(ico,new LinearLayout.LayoutParams(dp(36),dp(40)));TextView m=tx(message,12,"#64748B",false);row.addView(m,new LinearLayout.LayoutParams(0,-2,1));Button b=button("Atur",false);b.setOnClickListener(v->edit(type,label));row.addView(b,new LinearLayout.LayoutParams(dp(76),dp(40)));c.addView(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));aiQuick.addView(c,lp);}

    private void addPlace(JSONObject x){
        String type=x.optString("type","favorite"),label=x.optString("label","Favorit"),address=x.optString("address",""),icon="home".equals(type)?"🏠":"work".equals(type)?"🏢":"★";int id=x.optInt("id");
        LinearLayout c=card();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.TOP);TextView ico=tx(icon,21,"#0878F9",true);head.addView(ico,new LinearLayout.LayoutParams(dp(36),dp(42)));LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);head.addView(cp,new LinearLayout.LayoutParams(0,-2,1));cp.addView(tx(label,15,"#0F172A",true));cp.addView(tx(address,12,"#64748B",false));c.addView(head);Button del=button("Hapus lokasi",false);del.setOnClickListener(v->confirmRemove(id,label));LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(40));dlp.setMargins(0,dp(8),0,0);c.addView(del,dlp);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));list.addView(c,lp);
    }

    private void edit(String type,String label){
        editingType=type;editingLat=0;editingLng=0;saveAfterLocation=false;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(4),0,dp(4),0);box.addView(tx("Nama tempat",12,"#0B3A78",true));dialogLabel=new EditText(this);dialogLabel.setText(label);dialogLabel.setHint("Contoh: Rumah, Kantor, Rumah Ibu");box.addView(dialogLabel,new LinearLayout.LayoutParams(-1,dp(52)));TextView locTitle=tx("Lokasi",12,"#0B3A78",true);locTitle.setPadding(0,dp(8),0,0);box.addView(locTitle);dialogLocation=new EditText(this);dialogLocation.setHint("Alamat / lokasi akan terisi otomatis dari GPS");dialogLocation.setMinLines(2);dialogLocation.setMaxLines(3);box.addView(dialogLocation,new LinearLayout.LayoutParams(-1,dp(76)));Button gps=button("⌖ Gunakan lokasi saya",false);gps.setOnClickListener(v->captureCurrentLocation(false));LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,dp(46));glp.setMargins(0,dp(8),0,0);box.addView(gps,glp);box.addView(tx("AI memakai koordinat ini sebagai tujuan ketika Anda menekan Motor/Mobil.",11,"#64748B",false));
        new TransivaAlertDialogBuilder(this).setTitle("Simpan "+label).setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->prepareSave()).show();
        main.postDelayed(()->captureCurrentLocation(false),300);
    }

    private void prepareSave(){
        if(dialogLocation==null)return;String address=dialogLocation.getText().toString().trim();if(valid(editingLat,editingLng)){saveFavorite(address);return;} if(address.isEmpty()){saveAfterLocation=true;captureCurrentLocation(true);return;} geocodeAndSave(address);
    }
    private void captureCurrentLocation(boolean saveAfter){
        saveAfterLocation=saveAfter;
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        try{LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;for(String p:lm.getProviders(true)){try{Location l=lm.getLastKnownLocation(p);if(l!=null&&(best==null||l.getAccuracy()<best.getAccuracy()))best=l;}catch(Exception ignored){}}if(best!=null){applyLocation(best);return;}LocationListener listener=new LocationListener(){@Override public void onLocationChanged(Location location){try{lm.removeUpdates(this);}catch(Exception ignored){}applyLocation(location);}@Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle e){}};String provider=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;lm.requestSingleUpdate(provider,listener,Looper.getMainLooper());main.postDelayed(()->{try{lm.removeUpdates(listener);}catch(Exception ignored){}},8000);}catch(Exception e){Toast.makeText(this,"GPS belum mendapatkan lokasi.",Toast.LENGTH_SHORT).show();}
    }
    private void applyLocation(Location l){if(l==null)return;editingLat=l.getLatitude();editingLng=l.getLongitude();TransivaNetworkExecutor.execute(()->{String address=reverse(editingLat,editingLng);runOnUiThread(()->{if(dialogLocation!=null)dialogLocation.setText(address);if(saveAfterLocation)saveFavorite(address);});});}
    private String reverse(double lat,double lng){try{Geocoder g=new Geocoder(this,new Locale("id","ID"));List<Address>a=g.getFromLocation(lat,lng,1);if(a!=null&&!a.isEmpty()){Address x=a.get(0);String s=x.getAddressLine(0);if(s!=null&&!s.trim().isEmpty())return s.trim();}}catch(Exception ignored){}return String.format(Locale.US,"%.6f, %.6f",lat,lng);}
    private void geocodeAndSave(String address){TransivaNetworkExecutor.execute(()->{try{Geocoder g=new Geocoder(this,new Locale("id","ID"));List<Address>a=g.getFromLocationName(address,1);if(a!=null&&!a.isEmpty()){editingLat=a.get(0).getLatitude();editingLng=a.get(0).getLongitude();}runOnUiThread(()->saveFavorite(address));}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Alamat belum bisa ditemukan. Gunakan GPS agar koordinat akurat.",Toast.LENGTH_LONG).show());}});}
    private void saveFavorite(String address){String label=dialogLabel==null?"Favorit":dialogLabel.getText().toString().trim();if(label.isEmpty())label="Favorit";if(address==null||address.trim().isEmpty()){Toast.makeText(this,"Lokasi belum tersedia.",Toast.LENGTH_SHORT).show();return;}if(!valid(editingLat,editingLng)){Toast.makeText(this,"Koordinat lokasi belum ditemukan. Tekan ‘Gunakan lokasi saya’.",Toast.LENGTH_LONG).show();return;}try{JSONObject o=new JSONObject();o.put("action","save");o.put("type",editingType);o.put("label",label);o.put("address",address.trim());o.put("latitude",editingLat);o.put("longitude",editingLng);send(o);}catch(Exception ignored){}}

    private void openSmartRide(Class<?> cls,JSONObject p){double lat=p.optDouble("latitude",0),lng=p.optDouble("longitude",0);String addr=p.optString("address","");if(!valid(lat,lng)){Toast.makeText(this,"Lokasi ini belum memiliki koordinat. Simpan ulang menggunakan GPS.",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(this,cls);i.putExtra("smart_favorite",true);i.putExtra("smart_destination_lat",lat);i.putExtra("smart_destination_lng",lng);i.putExtra("smart_destination_address",addr);i.putExtra("smart_destination_label",p.optString("label","Tujuan"));startActivity(i);markUsed(p.optInt("id"));}
    private void markUsed(int id){try{JSONObject o=new JSONObject();o.put("action","used");o.put("id",id);TransivaNetworkExecutor.execute(()->{try{post(URL,o);}catch(Exception ignored){}});}catch(Exception ignored){}}
    private void confirmRemove(int id,String label){new TransivaAlertDialogBuilder(this).setTitle("Hapus lokasi?").setMessage(label+" akan dihapus dari Smart Favorit.").setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->remove(id)).show();}
    private void remove(int id){try{JSONObject o=new JSONObject();o.put("action","delete");o.put("id",id);send(o);}catch(Exception ignored){}}
    private void send(JSONObject o){TransivaNetworkExecutor.execute(()->{try{JSONObject r=post(URL,o);runOnUiThread(()->{Toast.makeText(this,r.optString("message","Selesai"),Toast.LENGTH_SHORT).show();if(r.optBoolean("success"))load();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Gagal menyimpan favorit",Toast.LENGTH_SHORT).show());}});}
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==REQ_LOCATION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)captureCurrentLocation(saveAfterLocation);}
    private void showError(String s){list.removeAllViews();LinearLayout c=card();c.addView(tx(s,14,"#B45309",true));list.addView(c);}
    private boolean valid(double lat,double lng){return lat>=-90&&lat<=90&&lng>=-180&&lng<=180&&lat!=0&&lng!=0;}
    private JSONObject get(String u)throws Exception{HttpURLConnection c=CustomerApiClient.open(this,u);c.setRequestMethod("GET");return read(c);}private JSONObject post(String u,JSONObject o)throws Exception{HttpURLConnection c=CustomerApiClient.open(this,u);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream os=c.getOutputStream()){os.write(o.toString().getBytes(StandardCharsets.UTF_8));}return read(c);}private JSONObject read(HttpURLConnection c)throws Exception{InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String l;while((l=br.readLine())!=null)s.append(l);return new JSONObject(s.toString());}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackground(bgStroke("#FFFFFF","#DFEBF7",18,1));c.setElevation(dp(1));return c;}private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.parseColor(themeColor(primary?"#FFFFFF":"#0B6DD9")));b.setBackground(primary?bg("#0878F9",15):bgStroke("#F2F8FF","#CDE4FF",15,1));return b;}private TextView tx(String s,int z,String color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(Color.parseColor(themeColor(color)));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}private GradientDrawable bg(String color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(themeColor(color)));g.setCornerRadius(dp(radius));return g;}private GradientDrawable bgStroke(String fill,String stroke,int radius,int width){GradientDrawable g=bg(fill,radius);g.setStroke(dp(width),Color.parseColor(themeColor(stroke)));return g;}private boolean isDark(){return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    private String themeColor(String c){if(!isDark())return c;String u=c.toUpperCase(java.util.Locale.US);if(u.equals("#F4F8FD")||u.equals("#F5F8FD"))return "#08111F";if(u.equals("#FFFFFF"))return "#111C2C";if(u.equals("#0F172A")||u.equals("#0B3A78"))return "#F1F5F9";if(u.equals("#64748B")||u.equals("#7890AA")||u.equals("#718096"))return "#AFC0D4";if(u.equals("#94A3B8"))return "#7F93A9";if(u.equals("#E0ECF8")||u.equals("#DFEBF7")||u.equals("#D5E8FF")||u.equals("#D7E9FF")||u.equals("#CBE3FF")||u.equals("#CDE4FF"))return "#26384F";if(u.equals("#EFF6FF")||u.equals("#EEF6FF")||u.equals("#EAF4FF")||u.equals("#F2F8FF"))return "#12243A";if(u.equals("#0B6DD9")||u.equals("#0878F9"))return "#66AFFF";if(u.equals("#B45309"))return "#FCD34D";return c;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
