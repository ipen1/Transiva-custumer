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

/** Smart Home/Work/Favorite + Transiva quick ride. */
public class FavoritePlacesActivity extends CustomerAdaptiveActivity {
    private static final String URL="https://transiva.my.id/server/customer_favorites.php";
    private static final int REQ_LOCATION=5108;
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout list, aiQuick;
    private EditText dialogLabel, dialogLocation;
    private String editingType="favorite";
    private double editingLat=0, editingLng=0;
    private boolean saveAfterLocation=false;
    private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
    private String quickType="";
    private boolean quickHandled;
    private android.app.AlertDialog editDialog;
    private boolean settingAddress;
    private boolean saving;
    private long editGeneration;
    private LocationManager pendingLocationManager;
    private LocationListener pendingLocationListener;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        quickType=getIntent().getStringExtra("quick_destination_type");
        if(quickType==null)quickType="";
        quickHandled=b!=null && b.getBoolean("quick_handled",false);
        try{CustomerWindowPolicy.status(getWindow(), Color.parseColor("#071426"));}catch(Exception ignored){}
        build(); load();
    }

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.parseColor(themeColor("#F4F8FD")));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(18),dp(18),dp(18),dp(18));hero.setBackground(bg("#0878F9",0));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=tx("‹",32,"#FFFFFF",true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(tx("Alamat Favorit",22,"#FFFFFF",true));titles.addView(tx("Rumah, kantor, dan tempat favorit siap dipakai lebih mudah.",12,"#DCEEFF",false));top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));hero.addView(top);
        LinearLayout ai=new LinearLayout(this);ai.setOrientation(LinearLayout.HORIZONTAL);ai.setGravity(Gravity.CENTER_VERTICAL);ai.setPadding(dp(14),dp(12),dp(14),dp(12));ai.setBackground(bg("#FFFFFF",17));TextView spark=tx("✦",23,"#0878F9",true);spark.setGravity(Gravity.CENTER);spark.setBackground(bg("#EAF4FF",14));ai.addView(spark,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cplp=new LinearLayout.LayoutParams(0,-2,1);cplp.setMargins(dp(10),0,0,0);ai.addView(cp,cplp);cp.addView(tx("Alamat siap dipakai",12,"#0B3A78",true));cp.addView(tx("Pilih Motor atau Mobil. Titik jemput akan diambil dari lokasi Anda dan tujuan langsung diisi.",12,"#64748B",false));LinearLayout.LayoutParams ailp=new LinearLayout.LayoutParams(-1,-2);ailp.setMargins(0,dp(14),0,0);hero.addView(ai,ailp);root.addView(hero);

        ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(14),dp(16),dp(24));sc.addView(body);
        body.addView(tx("Tujuan cepat",14,"#0B3A78",true));aiQuick=new LinearLayout(this);aiQuick.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(-1,-2);qlp.setMargins(0,dp(8),0,0);body.addView(aiQuick,qlp);
        LinearLayout quick=new LinearLayout(this);Button home=button("＋ Rumah",false),work=button("＋ Kantor",false),fav=button("＋ Favorit",false);quick.addView(home,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(0,dp(52),1);wlp.setMargins(dp(7),0,0,0);quick.addView(work,wlp);LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(0,dp(52),1);flp.setMargins(dp(7),0,0,0);quick.addView(fav,flp);LinearLayout.LayoutParams klp=new LinearLayout.LayoutParams(-1,-2);klp.setMargins(0,dp(14),0,dp(13));body.addView(quick,klp);home.setOnClickListener(v->edit("home","Rumah"));work.setOnClickListener(v->edit("work","Kantor"));fav.setOnClickListener(v->edit("favorite","Favorit"));
        body.addView(tx("Lokasi tersimpan",14,"#0B3A78",true));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,-2);llp.setMargins(0,dp(8),0,0);body.addView(list,llp);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root); CustomerAppSettings.apply(this);
    }


    @Override protected void onResume(){ super.onResume(); CustomerAppSettings.apply(this); }
    private void load(){scope.execute(()->{try{JSONObject r=get(URL+"?action=list");
        scope.post(main,()->{if(!r.optBoolean("success")){showError("Alamat belum dapat dimuat. Silakan coba lagi.");return;}render(r.optJSONArray("places"));});
    }catch(Exception e){scope.post(main,()->showError("Alamat belum dapat dimuat. Periksa koneksi dan coba lagi."));}},
        ()->scope.post(main,()->showError("Aplikasi sedang sibuk. Silakan coba lagi.")));}

    private void render(JSONArray a){
        list.removeAllViews();aiQuick.removeAllViews();JSONObject home=null,work=null;
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String type=x.optString("type","favorite");if("home".equals(type))home=x;if("work".equals(type))work=x;addPlace(x);}
        if(home!=null)addAiAction("🏠","Pulang ke Rumah","Tujuan rumah sudah siap. Pilih kendaraan Anda.",home);else addMissing("🏠","Simpan alamat rumah untuk mengisi tujuan perjalanan dengan mudah.","home","Rumah");
        if(work!=null)addAiAction("🏢","Ke Kantor","Titik kantor siap dipakai untuk perjalanan berikutnya.",work);else addMissing("🏢","Simpan Kantor agar perjalanan kerja cukup lebih mudah.","work","Kantor");
        if(!quickHandled && ("home".equals(quickType)||"work".equals(quickType))) {
            quickHandled=true;
            JSONObject place="home".equals(quickType)?home:work;
            if(place==null)edit(quickType,"home".equals(quickType)?"Rumah":"Kantor");
            else chooseVehicle(place);
        }
        if(a==null||a.length()==0){LinearLayout empty=card();empty.addView(tx("Belum ada lokasi tersimpan",15,"#0B3A78",true));empty.addView(tx("Simpan Rumah atau Kantor. Saat menyimpan, kolom lokasi dapat diisi otomatis dari GPS.",12,"#64748B",false));list.addView(empty);}
    }

    private void addAiAction(String icon,String title,String sub,JSONObject place){
        LinearLayout c=card();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView ico=tx(icon,21,"#0878F9",true);ico.setGravity(Gravity.CENTER);ico.setBackground(bg("#EEF6FF",14));head.addView(ico,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cplp=new LinearLayout.LayoutParams(0,-2,1);cplp.setMargins(dp(10),0,0,0);head.addView(cp,cplp);cp.addView(tx(title,15,"#0B3A78",true));cp.addView(tx(sub,11,"#64748B",false));c.addView(head);
        LinearLayout row=new LinearLayout(this);Button motor=button("🏍 Motor",true),car=button("🚗 Mobil",false);row.addView(motor,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(52),1);clp.setMargins(dp(8),0,0,0);row.addView(car,clp);LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);rlp.setMargins(0,dp(10),0,0);c.addView(row,rlp);motor.setOnClickListener(v->openSmartRide(TransRideActivity.class,place));car.setOnClickListener(v->openSmartRide(PassengerCarActivity.class,place));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));aiQuick.addView(c,lp);
    }
    private void addMissing(String icon,String message,String type,String label){LinearLayout c=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView ico=tx(icon,20,"#94A3B8",true);row.addView(ico,new LinearLayout.LayoutParams(dp(36),dp(48)));TextView m=tx(message,12,"#64748B",false);row.addView(m,new LinearLayout.LayoutParams(0,-2,1));Button b=button("Atur",false);b.setOnClickListener(v->edit(type,label));row.addView(b,new LinearLayout.LayoutParams(dp(76),dp(48)));c.addView(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));aiQuick.addView(c,lp);}

    private void addPlace(JSONObject x){
        String type=x.optString("type","favorite"),label=x.optString("label","Favorit"),address=x.optString("address",""),icon="home".equals(type)?"🏠":"work".equals(type)?"🏢":"★";int id=x.optInt("id");
        LinearLayout c=card();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.TOP);TextView ico=tx(icon,21,"#0878F9",true);head.addView(ico,new LinearLayout.LayoutParams(dp(36),dp(48)));LinearLayout cp=new LinearLayout(this);cp.setOrientation(LinearLayout.VERTICAL);head.addView(cp,new LinearLayout.LayoutParams(0,-2,1));cp.addView(tx(label,15,"#0F172A",true));cp.addView(tx(address,12,"#64748B",false));c.addView(head);Button del=button("Hapus lokasi",false);del.setOnClickListener(v->confirmRemove(id,label));LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(48));dlp.setMargins(0,dp(8),0,0);c.addView(del,dlp);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(9));list.addView(c,lp);
    }

    private void edit(String type,String label){
        editingType=type;editingLat=0;editingLng=0;saveAfterLocation=false;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(4),0,dp(4),0);box.addView(tx("Nama tempat",12,"#0B3A78",true));dialogLabel=new EditText(this);dialogLabel.setText(label);dialogLabel.setHint("Contoh: Rumah, Kantor, Rumah Ibu");box.addView(dialogLabel,new LinearLayout.LayoutParams(-1,dp(52)));TextView locTitle=tx("Lokasi",12,"#0B3A78",true);locTitle.setPadding(0,dp(8),0,0);box.addView(locTitle);dialogLocation=new EditText(this);dialogLocation.setHint("Alamat / lokasi akan terisi otomatis dari GPS");dialogLocation.setMinLines(2);dialogLocation.setMaxLines(3);box.addView(dialogLocation,new LinearLayout.LayoutParams(-1,dp(76)));Button gps=button("⌖ Gunakan lokasi saya",false);gps.setOnClickListener(v->captureCurrentLocation(false));LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,dp(52));glp.setMargins(0,dp(8),0,0);box.addView(gps,glp);box.addView(tx("Pastikan alamat sesuai titik yang Anda maksud. Alamat ini akan digunakan sebagai tujuan perjalanan.",11,"#64748B",false));
        ++editGeneration;
        dialogLocation.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){if(!settingAddress){editingLat=0;editingLng=0;}}
            public void afterTextChanged(android.text.Editable e){}
        });
        ScrollView editScroll=new ScrollView(this);editScroll.addView(box);
        editDialog=new TransivaAlertDialogBuilder(this).setTitle("Simpan "+label).setView(editScroll)
                .setNegativeButton("Batal",null).setPositiveButton("Simpan",null).create();
        editDialog.setOnDismissListener(d->{stopLocation();editGeneration++;saveAfterLocation=false;dialogLabel=null;dialogLocation=null;});
        editDialog.show();editDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->prepareSave());
        // GPS is requested only when the customer explicitly chooses Gunakan lokasi saya.
        CustomerAppSettings.applyToView(this,box);
    }

    private void prepareSave(){
        if(dialogLocation==null)return;String address=dialogLocation.getText().toString().trim();if(valid(editingLat,editingLng)){saveFavorite(address);return;} if(address.isEmpty()){saveAfterLocation=true;captureCurrentLocation(true);return;} geocodeAndSave(address);
    }
    private void stopLocation(){
        if(pendingLocationManager!=null&&pendingLocationListener!=null)try{pendingLocationManager.removeUpdates(pendingLocationListener);}catch(Exception ignored){}
        pendingLocationManager=null;pendingLocationListener=null;
    }
    private void captureCurrentLocation(boolean saveAfter){
        if(dialogLocation==null||scope.isDestroyed())return;saveAfterLocation=saveAfter;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                &&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;
        }
        stopLocation();final long generation=editGeneration;
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;
            for(String provider:lm.getProviders(true))try{
                Location candidate=lm.getLastKnownLocation(provider);
                if(candidate!=null&&Math.abs(System.currentTimeMillis()-candidate.getTime())<=120000L
                        &&candidate.hasAccuracy()&&candidate.getAccuracy()<=100f
                        &&(best==null||candidate.getTime()>best.getTime()))best=candidate;
            }catch(Exception ignored){}
            if(best!=null){applyLocation(best);return;}
            pendingLocationManager=lm;
            pendingLocationListener=new LocationListener(){
                @Override public void onLocationChanged(Location location){
                    if(generation!=editGeneration||scope.isDestroyed())return;
                    stopLocation();applyLocation(location);
                }
                @Override public void onProviderEnabled(String provider){}
                @Override public void onProviderDisabled(String provider){}
                @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            };
            LocationListener owned=pendingLocationListener;
            String provider=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
            lm.requestSingleUpdate(provider,owned,Looper.getMainLooper());
            main.postDelayed(()->{if(pendingLocationListener==owned){stopLocation();if(generation==editGeneration&&dialogLocation!=null)Toast.makeText(this,"Lokasi belum ditemukan. Ketik alamat lengkap atau coba lagi di tempat dengan sinyal lebih baik.",Toast.LENGTH_LONG).show();}},10000);
        }catch(Exception error){stopLocation();Toast.makeText(this,"Lokasi belum tersedia. Anda bisa mengetik alamat lengkap.",Toast.LENGTH_LONG).show();}
    }
    private void applyLocation(Location l){
        if(l==null||dialogLocation==null||scope.isDestroyed())return;
        if(!l.hasAccuracy()||l.getAccuracy()>150f){Toast.makeText(this,"Lokasi belum cukup akurat. Ketik alamat lengkap atau coba lokasi lagi.",Toast.LENGTH_LONG).show();return;}
        final long generation=editGeneration; final double lat=l.getLatitude(),lng=l.getLongitude();
        final String before=dialogLocation.getText().toString();
        scope.execute(()->{String address=reverse(lat,lng);scope.post(main,()->{
            if(generation!=editGeneration||dialogLocation==null||!before.equals(dialogLocation.getText().toString()))return;
            editingLat=lat;editingLng=lng;settingAddress=true;dialogLocation.setText(address);settingAddress=false;
            if(saveAfterLocation)saveFavorite(address);
        });});
    }
    private String reverse(double lat,double lng){try{Geocoder g=new Geocoder(this,new Locale("id","ID"));List<Address>a=g.getFromLocation(lat,lng,1);if(a!=null&&!a.isEmpty()){Address x=a.get(0);String s=x.getAddressLine(0);if(s!=null&&!s.trim().isEmpty())return s.trim();}}catch(Exception ignored){}return String.format(Locale.US,"%.6f, %.6f",lat,lng);}
    private void geocodeAndSave(String address){
        final long generation=editGeneration;
        scope.execute(()->{double lat=0,lng=0;
            try{List<Address>a=new Geocoder(this,new Locale("id","ID")).getFromLocationName(address,1);
                if(a!=null&&!a.isEmpty()){lat=a.get(0).getLatitude();lng=a.get(0).getLongitude();}}catch(Exception ignored){}
            final double foundLat=lat,foundLng=lng;
            scope.post(main,()->{if(generation!=editGeneration||dialogLocation==null||!address.equals(dialogLocation.getText().toString().trim()))return;
                if(!valid(foundLat,foundLng)){Toast.makeText(this,"Alamat belum ditemukan. Lengkapi alamat atau gunakan lokasi saya saat berada di tempat tersebut.",Toast.LENGTH_LONG).show();return;}
                editingLat=foundLat;editingLng=foundLng;saveFavorite(address);
            });
        });
    }
    private void saveFavorite(String address){String label=dialogLabel==null?"Favorit":dialogLabel.getText().toString().trim();if(label.isEmpty())label="Favorit";if(address==null||address.trim().isEmpty()){Toast.makeText(this,"Lokasi belum tersedia.",Toast.LENGTH_SHORT).show();return;}if(!valid(editingLat,editingLng)){Toast.makeText(this,"Koordinat lokasi belum ditemukan. Tekan ‘Gunakan lokasi saya’.",Toast.LENGTH_LONG).show();return;}try{JSONObject o=new JSONObject();o.put("action","save");o.put("type",editingType);o.put("label",label);o.put("address",address.trim());o.put("latitude",editingLat);o.put("longitude",editingLng);send(o);}catch(Exception ignored){}}

    private void chooseVehicle(JSONObject place){
        if(!valid(place.optDouble("latitude",Double.NaN),place.optDouble("longitude",Double.NaN))){
            new TransivaAlertDialogBuilder(this).setTitle("Perbarui alamat")
                    .setMessage("Alamat ini belum memiliki titik yang valid. Simpan ulang alamat sebelum menggunakannya.")
                    .setNegativeButton("Tutup",null).setPositiveButton("Atur alamat",(d,w)->edit(place.optString("type","favorite"),place.optString("label","Favorit"))).show();return;
        }
        new TransivaAlertDialogBuilder(this).setTitle("Ke "+place.optString("label","tujuan"))
                .setMessage(place.optString("address","")+"\n\nPilih kendaraan. Tujuan akan terisi; Anda tetap memeriksa biaya sebelum memesan.")
                .setNegativeButton("Batal",null).setNeutralButton("Motor",(d,w)->openSmartRide(TransRideActivity.class,place))
                .setPositiveButton("Mobil",(d,w)->openSmartRide(PassengerCarActivity.class,place)).show();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("quick_handled",quickHandled);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){stopLocation();editGeneration++;main.removeCallbacksAndMessages(null);scope.destroy();super.onDestroy();}
    private void openSmartRide(Class<?> cls,JSONObject p){double lat=p.optDouble("latitude",0),lng=p.optDouble("longitude",0);String addr=p.optString("address","");if(!valid(lat,lng)){Toast.makeText(this,"Lokasi ini belum memiliki koordinat. Simpan ulang menggunakan GPS.",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(this,cls);i.putExtra("smart_favorite",true);i.putExtra("smart_destination_lat",lat);i.putExtra("smart_destination_lng",lng);i.putExtra("smart_destination_address",addr);i.putExtra("smart_destination_label",p.optString("label","Tujuan"));startActivity(i);markUsed(p.optInt("id"));}
    private void markUsed(int id){try{JSONObject o=new JSONObject();o.put("action","used");o.put("id",id);scope.execute(()->{try{post(URL,o);}catch(Exception ignored){}});}catch(Exception ignored){}}
    private void confirmRemove(int id,String label){new TransivaAlertDialogBuilder(this).setTitle("Hapus lokasi?").setMessage(label+" akan dihapus dari Alamat Favorit.").setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->remove(id)).show();}
    private void remove(int id){try{JSONObject o=new JSONObject();o.put("action","delete");o.put("id",id);send(o);}catch(Exception ignored){}}
    private void send(JSONObject o){
        if(saving)return;saving=true;
        final long generation=editGeneration;final String action=o.optString("action");
        if(editDialog!=null && editDialog.isShowing())editDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        scope.execute(()->{try{JSONObject r=post(URL,o);scope.post(main,()->{
            saving=false;
            if(editDialog!=null&&editDialog.isShowing())editDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(true);
            Toast.makeText(this,r.optString("message","Belum berhasil. Coba lagi."),Toast.LENGTH_SHORT).show();
            if(r.optBoolean("success")){
                if("save".equals(action)&&generation==editGeneration&&editDialog!=null)editDialog.dismiss();
                load();
            }
        });}catch(Exception e){scope.post(main,this::saveFailed);}},()->scope.post(main,this::saveFailed));
    }
    private void saveFailed(){saving=false;if(editDialog!=null&&editDialog.isShowing())editDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(true);Toast.makeText(this,"Belum tersimpan. Periksa koneksi lalu coba lagi.",Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==REQ_LOCATION){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)captureCurrentLocation(saveAfterLocation);else Toast.makeText(this,"Anda tetap bisa mengetik alamat lengkap tanpa izin lokasi.",Toast.LENGTH_LONG).show();}}
    private void showError(String s){list.removeAllViews();LinearLayout c=card();c.addView(tx(s,14,"#B45309",true));Button retry=button("Coba lagi",true);retry.setMinHeight(dp(48));retry.setOnClickListener(v->load());c.addView(retry);list.addView(c);}
    private boolean valid(double lat,double lng){return !Double.isNaN(lat)&&!Double.isNaN(lng)&&!Double.isInfinite(lat)&&!Double.isInfinite(lng)&&lat>=-90&&lat<=90&&lng>=-180&&lng<=180&&(lat!=0||lng!=0);}
    private JSONObject get(String u)throws Exception{return TransivaHttpRepository.getJson(this,u,12000);}
    private JSONObject post(String u,JSONObject o)throws Exception{return TransivaHttpRepository.postJson(this,u,o,12000);}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackground(bgStroke("#FFFFFF","#DFEBF7",18,1));c.setElevation(dp(1));return c;}private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.parseColor(themeColor(primary?"#FFFFFF":"#0B6DD9")));b.setBackground(primary?bg("#0878F9",15):bgStroke("#F2F8FF","#CDE4FF",15,1));return b;}private TextView tx(String s,int z,String color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(Math.max(13,z));v.setTextColor(Color.parseColor(themeColor(color)));if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}private GradientDrawable bg(String color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(themeColor(color)));g.setCornerRadius(dp(radius));return g;}private GradientDrawable bgStroke(String fill,String stroke,int radius,int width){GradientDrawable g=bg(fill,radius);g.setStroke(dp(width),Color.parseColor(themeColor(stroke)));return g;}private boolean isDark(){return false; /* Shared reversible theme policy. */}
    private String themeColor(String c){if(!isDark())return c;String u=c.toUpperCase(java.util.Locale.US);if(u.equals("#F4F8FD")||u.equals("#F5F8FD"))return "#08111F";if(u.equals("#FFFFFF"))return "#111C2C";if(u.equals("#0F172A")||u.equals("#0B3A78"))return "#F1F5F9";if(u.equals("#64748B")||u.equals("#7890AA")||u.equals("#718096"))return "#AFC0D4";if(u.equals("#94A3B8"))return "#7F93A9";if(u.equals("#E0ECF8")||u.equals("#DFEBF7")||u.equals("#D5E8FF")||u.equals("#D7E9FF")||u.equals("#CBE3FF")||u.equals("#CDE4FF"))return "#26384F";if(u.equals("#EFF6FF")||u.equals("#EEF6FF")||u.equals("#EAF4FF")||u.equals("#F2F8FF"))return "#12243A";if(u.equals("#0B6DD9")||u.equals("#0878F9"))return "#66AFFF";if(u.equals("#B45309"))return "#FCD34D";return c;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
