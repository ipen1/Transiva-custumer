package com.transiva.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public class CustomerLoyaltyActivity extends CustomerAdaptiveActivity{
 private static final String URL="https://transiva.my.id/server/customer_loyalty.php", REDEEM="https://transiva.my.id/server/customer_loyalty_redeem.php";
 private LinearLayout root; private ProgressBar loading; private JSONObject data; private boolean redeemBusy; private String redeemKey=""; private int redeemRewardId;
 @Override protected void onCreate(Bundle b){super.onCreate(b);CustomerWindowPolicy.status(getWindow(), Color.parseColor(isDark()?"#07111F":"#075ED1"));CustomerWindowPolicy.appearance(getWindow(), isDark()?0:CustomerWindowPolicy.LIGHT_NAVIGATION);setContentView(base());CustomerAppSettings.apply(this);load();}

    @Override protected void onResume(){ super.onResume(); CustomerAppSettings.apply(this); if(data!=null&&!redeemBusy)load(); }
 private View base(){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setBackgroundColor(Color.parseColor(isDark()?"#08111F":"#F4F7FC"));root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(14),dp(16),dp(24));s.addView(root);TextView h=tx("‹   Poin Transiva",22,"#0B3A78",true);h.setOnClickListener(v->finish());root.addView(h);root.addView(tx("Kumpulkan poin, nikmati hadiahnya",13,"#64748B",false));loading=new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-2,-2);l.gravity=Gravity.CENTER;l.setMargins(0,dp(30),0,0);root.addView(loading,l);return s;}
 private void load(){TransivaNetworkExecutor.execute(()->{try{JSONObject o=request(URL,null);runOnUiThread(()->render(o));}catch(Exception e){String m=e.getMessage();runOnUiThread(()->error("Gagal memuat Poin Transiva"+(m==null||m.trim().isEmpty()?".":": "+m)));}});}
 private void render(JSONObject o){loading.setVisibility(View.GONE);while(root.getChildCount()>3)root.removeViewAt(3);if(!o.optBoolean("success")){error(o.optString("message","Gagal memuat Poin Transiva"));return;}data=o.optJSONObject("data");if(data==null)data=new JSONObject();
  int points=data.optInt("points_balance",data.optInt("points"));
  String tier=data.optString("season_tier",data.optString("tier","BRONZE"));
  LinearLayout hero=card("#0878F9",20);
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  row.addView(tierImage(tier,48),new LinearLayout.LayoutParams(dp(48),dp(48)));
  LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
  text.addView(tx(titleCase(tier),14,"#EAF4FF",true));text.addView(tx(fmt(points)+" poin",26,"#FFFFFF",true));
  LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(0,-2,1);textLp.setMargins(dp(12),0,0,0);row.addView(text,textLp);hero.addView(row);
  String progress=data.optString("card_progress",data.optString("season_next_tier_text",""));
  int levelPoints=data.optInt("season_points"),levelTarget=data.optInt("season_next_tier_points");
  if(levelTarget>0)progress="Progres level: "+fmt(levelPoints)+"/"+fmt(levelTarget)+" poin • Kurang "+fmt(Math.max(0,levelTarget-levelPoints))+" poin.";
  if(!progress.trim().isEmpty())hero.addView(tx(pointCopy(progress),12,"#FFFFFF",false),margin(10));
  int current=data.optInt("season_points"),target=data.optInt("season_next_tier_points");
  if(target>0){int floor=0;JSONArray rules=data.optJSONArray("season_tiers");if(rules!=null)for(int i=0;i<rules.length();i++){JSONObject rule=rules.optJSONObject(i);if(rule!=null&&rule.optInt("points")<=current)floor=Math.max(floor,rule.optInt("points"));}
   ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,target-floor));bar.setProgress(Math.max(0,current-floor));hero.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));}
  String benefit=pointCopy(data.optString("card_benefit","Poin dari pesanan yang memenuhi syarat."));
  hero.addView(tx(benefit,12,"#EAF4FF",false),margin(8));root.addView(hero,margin(14));
  addMissions(data.optJSONArray("missions"));
  JSONArray owned=data.optJSONArray("owned_vouchers");
  if(owned!=null&&owned.length()>0){root.addView(tx("Voucher saya",17,"#0B3A78",true),margin(18));for(int i=0;i<owned.length();i++){JSONObject v=owned.optJSONObject(i);if(v==null)continue;
   LinearLayout c=card("#ECFDF5",16);c.addView(tx(v.optString("title"),14,"#065F46",true));
   c.addView(tx("Min. "+rupiah(v.optInt("min_order"))+" • "+expiry(v.optString("expires_at")),11,"#475569",false),margin(4));
   Button use=action("Gunakan",true);use.setOnClickListener(view->useVoucher(v.optString("voucher_code")));c.addView(use,margin(8));root.addView(c,margin(8));}}
  root.addView(tx("Tukar poin",17,"#0B3A78",true),margin(18));
  JSONArray rewards=data.optJSONArray("rewards");int available=0;
  if(rewards!=null)for(int i=0;i<rewards.length();i++){JSONObject r=rewards.optJSONObject(i);if(r!=null&&r.optBoolean("unlocked")){addReward(r,points);available++;}}
  if(available==0)root.addView(tx("Belum ada voucher untuk levelmu. Cek lagi nanti.",12,"#64748B",false),margin(8));
  TextView help=tx("Cara kerja poin  ⓘ",12,"#0878F9",true);help.setMinHeight(dp(48));help.setGravity(Gravity.CENTER_VERTICAL);root.addView(help,margin(6));
  help.setOnClickListener(v->{JSONObject season=data.optJSONObject("season");int months=season==null?6:season.optInt("duration_months",6);
   new TransivaAlertDialogBuilder(this).setTitle("Cara kerja Poin Transiva").setMessage("Poin dari misi dan pesanan masuk ke satu saldo.\n\nGunakan untuk potongan pembayaran atau tukarkan voucher. Untuk potongan langsung, 1 poin = Rp1.\n\nLevel dihitung dari poin yang dikumpulkan selama "+months+" bulan. Menggunakan poin tidak menurunkan level.").setPositiveButton("Mengerti",null).show();});
 }
 private String pointCopy(String value){return value.replaceAll("(?i)koin","poin");}
 private String shortDate(String value){if(value==null||value.isEmpty()||"null".equals(value))return "";try{java.text.SimpleDateFormat input=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);input.setLenient(false);java.util.Date d=input.parse(value.substring(0,10));return new java.text.SimpleDateFormat("d MMM yyyy",new Locale("id","ID")).format(d);}catch(Exception e){return value;}}
 private String expiry(String value){String date=shortDate(value);return date.isEmpty()?"Tanpa batas waktu":"Berlaku s.d. "+date;}
 private Button action(String label,boolean enabled){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(13);b.setMinHeight(dp(44));b.setEnabled(enabled);b.setTextColor(Color.WHITE);b.setBackground(round(enabled?"#0878F9":"#64748B",12));return b;}
 private ImageView tierImage(String tier,int size){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);String key="tier_"+tier.toLowerCase(Locale.US);int id=getResources().getIdentifier(key,"drawable",getPackageName());if(id!=0){v.setImageResource(id);}else{android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Color.parseColor("#EAF4FF"));g.setStroke(dp(2),Color.parseColor("#0878F9"));v.setBackground(g);TextViewCompatFallback.apply(v,tier);}v.setContentDescription(titleCase(tier)+" tier");return v;}
 private static class TextViewCompatFallback{static void apply(ImageView v,String tier){v.setImageDrawable(null);}}
 private void addReward(JSONObject r,int points){int cost=r.optInt("points_cost"),remaining=r.optInt("quota_remaining",-1);boolean can=r.optBoolean("can_redeem");
  LinearLayout c=card("#FFFFFF",16);c.addView(tx(r.optString("title"),15,"#0F172A",true));
  c.addView(tx("Min. "+rupiah(r.optInt("min_order"))+(remaining>=0?" • Sisa "+remaining:""),11,"#64748B",false),margin(4));
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView costText=tx(fmt(cost)+" poin",14,"#0878F9",true);row.addView(costText,new LinearLayout.LayoutParams(0,-2,1));
  Button b=action(can?"Tukar":points<cost?"Poin kurang":"Habis",can);row.addView(b,new LinearLayout.LayoutParams(-2,dp(44)));b.setOnClickListener(v->confirmRedeem(r));c.addView(row,margin(8));
  TextView details=tx("Detail voucher ›",11,"#0878F9",true);details.setMinHeight(dp(44));details.setGravity(Gravity.CENTER_VERTICAL);details.setOnClickListener(v->new TransivaAlertDialogBuilder(this).setTitle(r.optString("title")).setMessage(r.optString("description")+"\n\n"+r.optString("terms_text","Syarat mengikuti ketentuan voucher.")).setPositiveButton("Tutup",null).show());c.addView(details);root.addView(c,margin(8));}
 private void confirmRedeem(JSONObject r){String terms=r.optString("terms_text","Voucher mengikuti syarat dan ketentuan Transiva.");new TransivaAlertDialogBuilder(this).setTitle("Konfirmasi Penukaran").setMessage(r.optString("title")+"\n"+r.optString("description")+"\n\nBiaya: "+r.optInt("points_cost")+" poin\n\nS&K:\n"+terms+"\n\nPoin akan dipotong setelah Anda mengonfirmasi.").setNegativeButton("Batal",null).setPositiveButton("Tukar Voucher",(d,w)->redeem(r.optInt("id"))).show();}
 private void redeem(int id){if(redeemBusy)return;redeemBusy=true;
  if(redeemKey.isEmpty()||redeemRewardId!=id){String stored=getSharedPreferences("royalty_redeem_"+new SessionManager(this).getUserId(),0).getString("reward_"+id,"");redeemKey=stored.isEmpty()?java.util.UUID.randomUUID().toString():stored;redeemRewardId=id;getSharedPreferences("royalty_redeem_"+new SessionManager(this).getUserId(),0).edit().putString("reward_"+id,redeemKey).apply();}
  final String key=redeemKey;
  TransivaNetworkExecutor.execute(()->{try{JSONObject p=new JSONObject();p.put("reward_id",id);p.put("request_key",key);JSONObject o;
   try{o=request(REDEEM,p);}catch(TransivaHttpRepository.ServerResponseException e){if(e.response!=null)o=e.response;else{redeemFailed("Layanan penukaran belum dapat memproses permintaan. Coba lagi sebentar.");return;}}
   final JSONObject result=o;
   runOnUiThread(()->{redeemBusy=false;if(isFinishing()||isDestroyed())return;
    if(!result.optBoolean("success")){showRedeemError(result.optString("message","Voucher belum berhasil ditukar."));load();return;}
    redeemKey="";getSharedPreferences("royalty_redeem_"+new SessionManager(this).getUserId(),0).edit().remove("reward_"+id).apply();load();
    new TransivaAlertDialogBuilder(this).setTitle("Voucher siap dipakai").setMessage("Voucher tersimpan di Voucher saya.").setPositiveButton("Gunakan",(d,w)->useVoucher(result.optString("voucher_code"))).setNegativeButton("Nanti",null).show();
   });
  }catch(java.io.IOException e){redeemFailed("Koneksi belum stabil. Coba tukar lagi untuk mengecek hasilnya. Permintaan yang sama tidak memotong poin dua kali.");}
   catch(Exception e){redeemFailed("Hasil penukaran belum dapat dibaca. Coba lagi dengan permintaan yang sama untuk mengecek voucher.");}
  });
 }
 private void redeemFailed(String message){runOnUiThread(()->{redeemBusy=false;if(isFinishing()||isDestroyed())return;showRedeemError(message);load();});}
 private void showRedeemError(String message){new TransivaAlertDialogBuilder(this).setTitle("Penukaran belum berhasil").setMessage(message).setPositiveButton("Mengerti",null).show();}
 private void useVoucher(String code){new TransivaAlertDialogBuilder(this).setTitle("Mau pakai di layanan apa?").setItems(new String[]{"TransRide","TransCar","TransFood"},(d,w)->{Class<?> destination=w==0?TransRideActivity.class:w==1?PassengerCarActivity.class:TransFoodActivity.class;startActivity(new android.content.Intent(this,destination).putExtra("royalty_voucher_code",code));}).setNegativeButton("Nanti",null).show();}
 private JSONArray orderedMissions(JSONArray input){JSONArray result=new JSONArray();if(input!=null)for(int group=0;group<3;group++)for(int i=0;i<input.length();i++){JSONObject m=input.optJSONObject(i);if(m==null)continue;String status=m.optString("status");int rank="active".equals(status)?0:"upcoming".equals(status)?1:2;if(rank==group)result.put(m);}return result;}
 private void addMissions(JSONArray missions){missions=orderedMissions(missions);root.addView(tx("Misi untukmu",17,"#0B3A78",true),margin(18));
  LinearLayout more=new LinearLayout(this);more.setOrientation(LinearLayout.VERTICAL);LinearLayout history=new LinearLayout(this);history.setOrientation(LinearLayout.VERTICAL);int active=0,past=0;
  if(missions!=null)for(int i=0;i<missions.length();i++){JSONObject m=missions.optJSONObject(i);if(m==null)continue;String state=m.optString("status");boolean done="completed".equals(state),expired="expired".equals(state);
   LinearLayout c=card(done?"#ECFDF5":"#FFFFFF",16);c.addView(tx(m.optString("title"),14,"#0F172A",true));
   if(done||expired){c.addView(tx(done?"✓ +"+fmt(m.optInt("reward_points"))+" poin sudah masuk":"Misi berakhir",12,done?"#047857":"#64748B",true),margin(4));history.addView(c,margin(8));past++;continue;}
   c.addView(tx(m.optString("description"),12,"#475569",false),margin(4));
   int target=Math.max(1,m.optInt("target_orders",1)),progress=Math.max(0,m.optInt("progress"));
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(tx("+"+fmt(m.optInt("reward_points"))+" poin",15,"#0878F9",true),new LinearLayout.LayoutParams(0,-2,1));row.addView(tx(progress+"/"+target+" pesanan",11,"#475569",true));c.addView(row,margin(8));
   ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(target);bar.setProgress(Math.min(target,progress));c.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));
   boolean upcoming="upcoming".equals(state);c.addView(tx(upcoming?"Mulai "+shortDate(m.optString("starts_at")):expiry(m.optString("ends_at")),11,"#64748B",false),margin(5));
   if(m.optInt("min_order_amount")>0)c.addView(tx("Min. per pesanan "+rupiah(m.optInt("min_order_amount")),11,"#64748B",false),margin(3));
   if("active".equals(state)){Button order=action("Buat pesanan",true);order.setOnClickListener(v->startActivity(new android.content.Intent(this,AllServicesActivity.class)));c.addView(order,margin(8));}
   if(active<2)root.addView(c,margin(8));else more.addView(c,margin(8));active++;}
  if(active==0)root.addView(tx("Misi baru sedang disiapkan.",12,"#64748B",false),margin(6));
  if(active>2)addDisclosure("Misi lainnya ("+(active-2)+")",more);
  if(past>0)addDisclosure("Riwayat misi ("+past+")",history);
 }
 private void addDisclosure(String label,LinearLayout content){TextView toggle=tx(label+"  ⌄",12,"#0878F9",true);toggle.setMinHeight(dp(44));toggle.setGravity(Gravity.CENTER_VERTICAL);root.addView(toggle);content.setVisibility(View.GONE);root.addView(content);toggle.setOnClickListener(v->{boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(label+(show?"  ⌃":"  ⌄"));});}
 private JSONObject request(String u,JSONObject body)throws Exception{ return body==null ? TransivaHttpRepository.getJsonFresh(this,u,15000) : TransivaHttpRepository.postJson(this,u,body,15000); }
 private void error(String s){loading.setVisibility(View.GONE);TextView x=tx(s,14,"#B91C1C",true);root.addView(x,margin(16));}
 private boolean isDark(){return false; /* Preserve existing Royalti theme policy. */}
 private String themeColor(String c){if(!isDark())return c;String u=c.toUpperCase(Locale.US);if(u.equals("#FFFFFF"))return "#111C2C";if(u.equals("#0F172A")||u.equals("#0B3A78")||u.equals("#334155"))return "#F1F5F9";if(u.equals("#475569")||u.equals("#64748B"))return "#AFC0D4";if(u.equals("#94A3B8"))return "#7F93A9";if(u.equals("#ECFDF5"))return "#0D2A22";if(u.equals("#065F46")||u.equals("#047857"))return "#6EE7B7";if(u.equals("#B91C1C"))return "#FCA5A5";if(u.equals("#B45309"))return "#FCD34D";return c;}
 private LinearLayout card(String c,int r){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),dp(12),dp(14),dp(12));x.setBackground(round(themeColor(c),r));return x;}private GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(themeColor(c)));g.setCornerRadius(dp(r));return g;}private LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,0);return p;}private TextView tx(String s,int z,String c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.parseColor(themeColor(c)));t.setLineSpacing(0,1.12f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private String fmt(int n){return NumberFormat.getIntegerInstance(new Locale("id","ID")).format(n);}private String rupiah(int n){return "Rp"+fmt(n);}private String titleCase(String s){if(s==null||s.isEmpty())return "Bronze";s=s.toLowerCase(Locale.US);return Character.toUpperCase(s.charAt(0))+s.substring(1);}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
