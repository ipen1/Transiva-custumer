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
 private LinearLayout root; private ProgressBar loading; private JSONObject data;
 private boolean redeeming;
 private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 @Override protected void onCreate(Bundle b){super.onCreate(b);CustomerWindowPolicy.status(getWindow(), Color.parseColor(isDark()?"#07111F":"#075ED1"));CustomerWindowPolicy.appearance(getWindow(), isDark()?0:CustomerWindowPolicy.LIGHT_NAVIGATION);setContentView(base());CustomerAppSettings.apply(this);load();}

    @Override protected void onResume(){ super.onResume(); CustomerAppSettings.apply(this); }
 private View base(){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setBackgroundColor(Color.parseColor(isDark()?"#08111F":"#F4F7FC"));root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(36));s.addView(root);TextView h=tx("‹   Poin & voucher saya",24,"#0B3A78",true);h.setOnClickListener(v->finish());root.addView(h);root.addView(tx("Lihat poin, koin, dan voucher yang dapat Anda gunakan.",13,"#64748B",false));loading=new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-2,-2);l.gravity=Gravity.CENTER;l.setMargins(0,dp(30),0,0);root.addView(loading,l);return s;}
 private void load(){scope.execute(()->{try{JSONObject o=request(URL,null);scope.post(handler,()->render(o));}catch(Exception e){scope.post(handler,()->error("Poin dan voucher belum dapat dimuat. Periksa koneksi lalu coba lagi."));}},()->scope.post(handler,()->error("Aplikasi sedang sibuk. Silakan perbarui kembali.")));}
 private void render(JSONObject o){
  loading.setVisibility(View.GONE);while(root.getChildCount()>3)root.removeViewAt(3);
  if(!o.optBoolean("success")){error(o.optString("message","Poin belum dapat dimuat."));return;}
  redeeming=false;
  data=o.optJSONObject("data");if(data==null)data=new JSONObject();
  int points=Math.max(0,data.optInt("points")),lifetime=data.optInt("earned_points",points),seasonPoints=data.optInt("season_points",0);
  String activeTier=data.optString("season_tier",data.optString("tier","BRONZE"));
  String lifetimeTier=data.optString("lifetime_tier",activeTier);
  LinearLayout hero=card("#0878F9",22);
  hero.addView(tx("Poin tersedia untuk ditukar",16,"#FFFFFF",true));
  hero.addView(tx(fmt(points)+" poin",30,"#FFFFFF",true));
  hero.addView(tx("Pilih voucher di bawah untuk melihat syarat dan menukar poin.",14,"#FFFFFF",false),margin(8));root.addView(hero,margin(16));
  if(data.has("coin_balance")){
   LinearLayout coin=card("#FFFFFF",18);coin.addView(tx("Transiva Coin: "+fmt(Math.max(0,data.optInt("coin_balance")))+" koin",18,"#0B3A78",true));
   coin.addView(tx("Koin berbeda dari poin voucher. Gunakan menu Hemat saat memesan jika memenuhi ketentuan.",14,"#475569",false),margin(6));root.addView(coin,margin(12));
  }
  JSONArray owned=data.optJSONArray("owned_vouchers");
  root.addView(tx("Voucher saya",20,"#0B3A78",true),margin(20));
  root.addView(tx("Masukkan kode melalui menu Voucher saat memesan. Potongan berlaku setelah syarat diperiksa.",14,"#475569",false),margin(6));
  if(owned==null||owned.length()==0)root.addView(tx("Belum ada voucher tersimpan. Anda bisa menukar poin pada pilihan di bawah.",14,"#64748B",false),margin(10));
  else for(int i=0;i<owned.length();i++){
   JSONObject v=owned.optJSONObject(i);if(v==null)continue;
   LinearLayout c=card("#ECFDF5",18);c.addView(tx(v.optString("title","Voucher Anda"),17,"#065F46",true));
   final String code=v.optString("voucher_code","");TextView codeText=tx(code,19,"#047857",true);codeText.setTextIsSelectable(true);c.addView(codeText,margin(6));
   String expiry=v.optString("expires_at","");c.addView(tx(expiry.isEmpty()||"null".equals(expiry)?"Periksa masa berlaku pada ketentuan voucher.":"Berlaku sampai "+expiry,13,"#475569",false),margin(6));
   Button copy=new Button(this);copy.setText("Salin kode voucher");copy.setAllCaps(false);copy.setMinHeight(dp(48));
   copy.setOnClickListener(view->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Kode voucher Transiva",code));Toast.makeText(this,"Kode disalin. Tempel di menu Voucher saat memesan.",Toast.LENGTH_LONG).show();}});
   c.addView(copy,margin(8));root.addView(c,margin(10));
  }
  root.addView(tx("Tukar poin dengan voucher",20,"#0B3A78",true),margin(22));
  JSONArray rewards=data.optJSONArray("rewards");int ready=0;
  if(rewards!=null)for(int i=0;i<rewards.length();i++){JSONObject reward=rewards.optJSONObject(i);if(reward!=null&&reward.optBoolean("can_redeem"))ready++;}
  root.addView(tx(ready>0?ready+" pilihan voucher sudah bisa Anda tukar.":"Ketersediaan voucher mengikuti poin, status anggota, dan kuota.",14,"#475569",false),margin(6));
  if(rewards==null||rewards.length()==0)root.addView(tx("Pilihan voucher belum tersedia. Periksa kembali nanti.",14,"#64748B",false),margin(10));
  else for(int pass=0;pass<2;pass++)for(int i=0;i<rewards.length();i++){JSONObject r=rewards.optJSONObject(i);if(r!=null&&r.optBoolean("can_redeem")== (pass==0))addReward(r,points);}
  LinearLayout explanation=card("#FFFFFF",18);explanation.addView(tx("Poin, koin, dan status anggota",17,"#0B3A78",true));
  explanation.addView(tx("Poin tersedia dapat ditukar dengan voucher. Koin dipakai melalui fitur Hemat. Status anggota mengikuti ketentuan periode yang sedang berjalan. Penukaran hanya diproses setelah Anda mengonfirmasi.",14,"#475569",false),margin(8));
  final LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setVisibility(View.GONE);
  JSONObject season=data.optJSONObject("season");String period=season==null?"Periode aktif":season.optString("label","Periode aktif");
  details.addView(tx("Status saat ini: "+titleCase(activeTier)+" • "+fmt(seasonPoints)+" poin periode ini",16,"#0B3A78",true));
  details.addView(tx(period,14,"#475569",false),margin(6));
  if(season!=null)details.addView(tx(season.optString("start","")+" sampai "+season.optString("end",""),13,"#64748B",false),margin(4));
  details.addView(tx(plain(data.optString("season_next_tier_text","")),14,"#475569",false),margin(6));
  addTierTrackInto(details,seasonPoints,data.optJSONArray("season_tiers"));
  details.addView(tx("Pencapaian sepanjang penggunaan: "+titleCase(lifetimeTier)+" • "+fmt(lifetime)+" poin terkumpul",14,"#475569",false),margin(12));
  details.addView(tx("Pencapaian ini berbeda dari saldo poin yang masih dapat ditukar.",13,"#64748B",false),margin(6));
  TextView toggle=tx("Lihat rincian status anggota  ›",15,"#0878F9",true);toggle.setMinHeight(dp(48));toggle.setGravity(Gravity.CENTER_VERTICAL);
  toggle.setOnClickListener(v->{boolean open=details.getVisibility()!=View.VISIBLE;details.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"Tutup rincian status anggota":"Lihat rincian status anggota  ›");});
  explanation.addView(toggle,margin(6));explanation.addView(details);root.addView(explanation,margin(18));CustomerAppSettings.apply(this);
 }
 private String plain(String text){return text.replace("season","periode").replace("lifetime","sepanjang penggunaan").replace("Achievement","Pencapaian").replace("tier","status anggota").replace("di-reset","diulang");}
 private void addTierTrackInto(LinearLayout c,int currentPoints,JSONArray rules){
  if(rules==null)return;
  for(int i=0;i<rules.length();i++){
   JSONObject r=rules.optJSONObject(i);if(r==null)continue;int threshold=r.optInt("points");
   String title=titleCase(r.optString("name","Anggota"))+" • "+fmt(threshold)+" poin";
   c.addView(tx(title+(currentPoints>=threshold?" • tercapai":" • kurang "+fmt(threshold-currentPoints)+" poin"),14,"#475569",false),margin(8));
  }
 }
 private ImageView tierImage(String tier,int size){ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);String key="tier_"+tier.toLowerCase(Locale.US);int id=getResources().getIdentifier(key,"drawable",getPackageName());if(id!=0){v.setImageResource(id);}else{android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Color.parseColor("#EAF4FF"));g.setStroke(dp(2),Color.parseColor("#0878F9"));v.setBackground(g);TextViewCompatFallback.apply(v,tier);}v.setContentDescription(titleCase(tier)+" tier");return v;}
 private static class TextViewCompatFallback{static void apply(ImageView v,String tier){v.setImageDrawable(null);}}
 private void addReward(JSONObject r,int points){boolean unlocked=r.optBoolean("unlocked"),can=r.optBoolean("can_redeem");int cost=r.optInt("points_cost"),remaining=r.optInt("quota_remaining",-1);LinearLayout c=card("#FFFFFF",22);TextView badge=tx((unlocked?"TERBUKA":"Perlu status "+titleCase(r.optString("min_tier")))+"  •  "+cost+" poin",11,unlocked?"#0878F9":"#94A3B8",true);c.addView(badge);c.addView(tx(r.optString("title"),18,"#0F172A",true));c.addView(tx(r.optString("description"),13,"#475569",false));String q=remaining<0?"Kuota tersedia":"Sisa kuota: "+remaining;c.addView(tx(q+"  •  Minimal pesanan "+rupiah(r.optInt("min_order")),11,"#64748B",false));Button b=new Button(this);b.setAllCaps(false);b.setText(can?"Tukar dengan "+cost+" poin":(!unlocked?"Perlu status anggota lebih tinggi":(points<cost?"Poin belum cukup":"Belum dapat ditukar")));b.setEnabled(can);b.setTextColor(Color.WHITE);b.setBackground(round(can?"#0878F9":"#94A3B8",16));b.setMinHeight(dp(52));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,0);c.addView(b,lp);b.setOnClickListener(v->confirmRedeem(r));root.addView(c,margin(12));}
 private void confirmRedeem(JSONObject r){if(redeeming)return;String terms=r.optString("terms_text","Voucher mengikuti syarat dan ketentuan Transiva.");new TransivaAlertDialogBuilder(this).setTitle("Konfirmasi Penukaran").setMessage(r.optString("title")+"\n\nBiaya: "+r.optInt("points_cost")+" poin\n\nS&K:\n"+terms+"\n\nPoin akan dipotong setelah Anda mengonfirmasi.").setNegativeButton("Batal",null).setPositiveButton("Tukar Voucher",(d,w)->redeem(r.optInt("id"))).show();}
 private void redeem(int id){
  if(redeeming)return;redeeming=true;
  scope.execute(()->{try{JSONObject payload=new JSONObject();payload.put("reward_id",id);JSONObject o=request(REDEEM,payload);
   scope.post(handler,()->{
    // Keep the old catalog locked until refreshed, so it cannot spend stale points twice.
    new TransivaAlertDialogBuilder(this).setTitle(o.optBoolean("success")?"Voucher berhasil ditukar":"Penukaran belum berhasil")
      .setMessage(o.optBoolean("success")?"Kode voucher: "+o.optString("voucher_code")+"\n\nLihat Voucher Saya untuk menyalin kode.":o.optString("message","Periksa kembali poin dan voucher Anda."))
      .setPositiveButton("Mengerti",null).show();loading.setVisibility(View.VISIBLE);load();
   });
  }catch(Exception error){scope.post(handler,()->error("Hasil penukaran belum dapat dipastikan. Perbarui poin dan Voucher Saya sebelum mencoba lagi."));}},
    ()->scope.post(handler,()->{redeeming=false;error("Permintaan belum dikirim karena aplikasi sedang sibuk. Silakan perbarui kembali.");}));
 }
 private JSONObject request(String u,JSONObject body)throws Exception{ return body==null ? TransivaHttpRepository.getJson(this,u,15000) : TransivaHttpRepository.postJson(this,u,body,15000); }
 private void error(String s){loading.setVisibility(View.GONE);TextView x=tx(s,14,"#B91C1C",true);root.addView(x,margin(16));Button retry=new Button(this);retry.setText("Perbarui poin & voucher");retry.setAllCaps(false);retry.setMinHeight(dp(48));retry.setOnClickListener(v->{loading.setVisibility(View.VISIBLE);load();});root.addView(retry,margin(8));}
 @Override protected void onDestroy(){scope.destroy();handler.removeCallbacksAndMessages(null);super.onDestroy();}
 private boolean isDark(){return false; /* Shared reversible theme policy. */}
 private String themeColor(String c){if(!isDark())return c;String u=c.toUpperCase(Locale.US);if(u.equals("#FFFFFF"))return "#111C2C";if(u.equals("#0F172A")||u.equals("#0B3A78")||u.equals("#334155"))return "#F1F5F9";if(u.equals("#475569")||u.equals("#64748B"))return "#AFC0D4";if(u.equals("#94A3B8"))return "#7F93A9";if(u.equals("#ECFDF5"))return "#0D2A22";if(u.equals("#065F46")||u.equals("#047857"))return "#6EE7B7";if(u.equals("#B91C1C"))return "#FCA5A5";if(u.equals("#B45309"))return "#FCD34D";return c;}
 private LinearLayout card(String c,int r){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(18),dp(18),dp(18),dp(18));x.setBackground(round(themeColor(c),r));return x;}private GradientDrawable round(String c,int r){GradientDrawable g=new GradientDrawable();g.setColor(Color.parseColor(themeColor(c)));g.setCornerRadius(dp(r));return g;}private LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(top),0,0);return p;}private TextView tx(String s,int z,String c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(Math.max(13,z));t.setTextColor(Color.parseColor(themeColor(c)));t.setLineSpacing(0,1.12f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private String fmt(int n){return NumberFormat.getIntegerInstance(new Locale("id","ID")).format(n);}private String rupiah(int n){return "Rp"+fmt(n);}private String titleCase(String s){if(s==null||s.isEmpty())return "Bronze";s=s.toLowerCase(Locale.US);return Character.toUpperCase(s.charAt(0))+s.substring(1);}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
