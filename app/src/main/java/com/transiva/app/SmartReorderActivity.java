package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** A previous order fills a draft; the normal service still checks location, stock and current prices. */
public class SmartReorderActivity extends CustomerAdaptiveActivity {
    private LinearLayout root,list;
    private ProgressBar progress;
    private TextView state;
    private Button retry;
    private boolean loading;
    private final SmartReorderRepository repo=new SmartReorderRepository();
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(screen());CustomerAppSettings.apply(this);load();}
    private View screen(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.parseColor("#F4F8FD"));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(28));scroll.addView(root);
        TextView title=text("‹  Pesan lagi",24,true);title.setMinHeight(dp(48));title.setOnClickListener(v->finish());root.addView(title);
        root.addView(text("Pilih pesanan sebelumnya. Periksa lokasi, isi pesanan, dan biaya terbaru sebelum mengonfirmasi.",14,false));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);root.addView(progress);
        state=text("Memuat pesanan sebelumnya…",14,false);root.addView(state,spacing());
        retry=button("Coba lagi");retry.setVisibility(View.GONE);retry.setOnClickListener(v->load());root.addView(retry);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);return scroll;
    }
    private void load(){
        if(loading)return;loading=true;progress.setVisibility(View.VISIBLE);retry.setVisibility(View.GONE);
        repo.load(this,(response,error)->{
            if(isFinishing()||isDestroyed())return;loading=false;progress.setVisibility(View.GONE);
            if(error!=null||response==null||!response.optBoolean("success")){
                state.setText("Pesanan sebelumnya belum dapat dimuat. Periksa koneksi lalu coba lagi.");retry.setVisibility(View.VISIBLE);return;
            }
            render(response.optJSONArray("orders"));
        });
    }
    private void render(JSONArray orders){
        list.removeAllViews();
        if(orders==null||orders.length()==0){
            state.setText("Belum ada pesanan selesai yang dapat diulang. Setelah memakai Transiva, pilihan Anda akan muncul di sini.");
            Button start=button("Mulai pesan dari beranda");start.setOnClickListener(v->startActivity(new Intent(this,CustomerDashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));list.addView(start,spacing());return;
        }
        state.setText("Pilihan berdasarkan pesanan Anda sebelumnya");
        for(int i=0;i<orders.length();i++){
            JSONObject order=orders.optJSONObject(i);if(order==null)continue;
            String type=order.optString("order_type","ride").toLowerCase(Locale.ROOT);boolean food=type.contains("food");
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));
            card.setBackground(CustomerUiPrimitives.roundStroke(this,"#FFFFFF","#D8E5F2",dp(18),1));
            card.addView(text(food?order.optString("restaurant_name","Makanan sebelumnya"):type.contains("car")?"Naik mobil lagi":"Naik motor lagi",18,true));
            card.addView(text(order.optString("summary","Pesanan sebelumnya"),14,false),spacing());
            card.addView(text(food?"Menu dan stok diperiksa kembali. Pilihan tambahan perlu dipilih ulang.":"Tujuan diisi dari pesanan ini. Titik jemput diperiksa dari lokasi Anda sekarang.",13,false),spacing());
            Button open=button(food?"Pilih makanan lagi":"Periksa perjalanan");open.setOnClickListener(v->openDraft(order));card.addView(open,spacing());list.addView(card,spacing());
        }
        CustomerAppSettings.applyToView(this,list);
    }
    private void openDraft(JSONObject order){
        String type=order.optString("order_type","").toLowerCase(Locale.ROOT);
        if(type.contains("food")){
            if(!DeliveryAddressGate.require(this,"TransFood"))return;
            int restaurant=order.optInt("restaurant_id",0);if(restaurant<=0)return;
            Intent intent=new Intent(this,TransFoodActivity.class).putExtra("story_restaurant_id",restaurant)
                    .putExtra("story_restaurant_name",order.optString("restaurant_name",""))
                    .putExtra("beginner_repeat_food",order.toString());startActivity(intent);return;
        }
        double lat=order.optDouble("delivery_lat",Double.NaN),lng=order.optDouble("delivery_lng",Double.NaN);
        if(!CustomerGeoMath.valid(lat,lng)){new TransivaAlertDialogBuilder(this).setTitle("Pilih tujuan kembali").setMessage("Lokasi pesanan lama belum lengkap. Pilih tujuan dari halaman perjalanan.").setPositiveButton("Buka perjalanan",(d,w)->startActivity(new Intent(this,type.contains("car")?PassengerCarActivity.class:TransRideActivity.class))).show();return;}
        Intent intent=new Intent(this,type.contains("car")?PassengerCarActivity.class:TransRideActivity.class)
                .putExtra("smart_favorite",true).putExtra("smart_destination_lat",lat).putExtra("smart_destination_lng",lng)
                .putExtra("smart_destination_address",order.optString("delivery_address","")).putExtra("smart_destination_label","Tujuan sebelumnya");
        startActivity(intent);
    }
    @Override protected void onDestroy(){repo.close();super.onDestroy();}
    private TextView text(String s,int size,boolean bold){return CustomerUiPrimitives.text(this,s,size,bold?"#123B65":"#405B73",bold);}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(52));return b;}
    private LinearLayout.LayoutParams spacing(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);return p;}
    private int dp(int n){return CustomerUiPrimitives.dp(this,n);}
}
