package com.transiva.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public final class TivaSimpelActivity extends CustomerAdaptiveActivity {
    static final String EXTRA_DRAFT="tiva_draft";
    private static final int MAX_CART_LINES=10;
    private static final int PICKUP=721, DELIVERY=722;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CustomerLifecycleNetworkScope network=new CustomerLifecycleNetworkScope();
    private final Runnable poll=this::checkContact;
    private final JSONArray options=new JSONArray();
    private LinearLayout messages, actions;
    private ScrollView scroll;
    private String kind="", detail="", contact="", key=UUID.randomUUID().toString();
    private String status="", problem="";
    private boolean typing, reviewFood;
    private JSONArray foodCart=new JSONArray();
    private JSONObject pickup, delivery, restaurant;
    private int driver, nextOffset;
    private double lat,lng;
    private String driverName;
    private boolean more, loading, visible, opened;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        driver=getIntent().getIntExtra("driver_id",0);
        driverName=CallPeerIdentity.first(getIntent().getStringExtra("driver_name"),"driver pilihanmu");
        lat=getIntent().getDoubleExtra("lat",0);
        lng=getIntent().getDoubleExtra("lng",0);
        if (driver<=0 || lat==0 || lng==0) { finish();return; }
        Bundle draft=state==null?getIntent().getBundleExtra(EXTRA_DRAFT):state;
        if (draft!=null) restoreDraft(draft);
        if (state!=null) {
            contact=state.getString("tiva.contact","");
            contactState=state.getString("tiva.contact_state","");
            status=state.getString("tiva.status","");
            key=state.getString("tiva.key",key);
        }
        buildScreen();
        if (draft==null) showTyping(this::render);
        else render();
    }

    private void buildScreen() {
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF2F7FD);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8),dp(8),dp(14),dp(8));
        header.setBackgroundColor(0xFFFFFFFF);
        Button back=button("‹",this::finish);
        back.setContentDescription("Kembali");
        header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        ImageView avatar=new ImageView(this);
        avatar.setImageResource(R.drawable.tiva_ai_avatar);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(CustomerUiPrimitives.round("#E9F3FF",dp(24)));
        avatar.setClipToOutline(true);
        header.addView(avatar,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout identity=new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(10),0,0,0);
        identity.addView(label("Tiva AI",18,"#123259",true));
        identity.addView(label("Trans Simpel · "+driverName,12,"#526E90",false));
        header.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(header);
        scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(12),dp(14),dp(18));
        messages=new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        body.addView(messages);
        body.addView(actions);
        scroll.addView(body);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(page);
        CustomerAppSettings.apply(this);
    }

    @Override protected void onResume() {
        super.onResume();
        visible=true;
        if (!contact.isEmpty()) checkContact();
        else if ("food".equals(kind) && !reviewFood && options.length()==0 && !loading && problem.isEmpty()) {
            loadFood(restaurant==null?0:restaurant.optInt("id"),0);
        }
    }

    @Override protected void onPause() {
        visible=false;
        main.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        network.destroy();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        saveDraft(out);
        out.putString("tiva.contact",contact);
        out.putString("tiva.contact_state",contactState);
        out.putString("tiva.status",status);
        out.putString("tiva.key",key);
    }

    private void saveDraft(Bundle out) {
        out.putString("tiva.kind",kind);
        out.putString("tiva.detail",detail);
        out.putString("tiva.cart",foodCart.toString());
        out.putBoolean("tiva.review",reviewFood);
        save(out,"tiva.pickup",pickup);
        save(out,"tiva.delivery",delivery);
        save(out,"tiva.restaurant",restaurant);
    }

    private void restoreDraft(Bundle draft) {
        kind=draft.getString("tiva.kind","");
        detail=draft.getString("tiva.detail","");
        pickup=parse(draft,"tiva.pickup");
        delivery=parse(draft,"tiva.delivery");
        restaurant=parse(draft,"tiva.restaurant");
        try { foodCart=new JSONArray(draft.getString("tiva.cart","[]")); }
        catch (org.json.JSONException ignored) { foodCart=new JSONArray(); }
        reviewFood=restaurant!=null && foodCart.length()>0 && draft.getBoolean("tiva.review");
    }

    private void chooseAnotherDriver() {
        Bundle draft=new Bundle();
        saveDraft(draft);
        setResult(RESULT_OK,new Intent().putExtra(EXTRA_DRAFT,draft));
        finish();
    }

    private static void save(Bundle state,String name,JSONObject value) {
        if (value!=null) state.putString(name,value.toString());
    }

    private static JSONObject parse(Bundle state,String name) {
        try { return new JSONObject(state.getString(name,"")); }
        catch (org.json.JSONException ignored) { return null; }
    }

    private void render() {
        messages.removeAllViews();
        actions.removeAllViews();
        bubble("Hai! Aku Tiva AI. Mau kubantu pesan apa?",false);
        if (kind.isEmpty()) {
            bubble("Setelah alamat dipilih, percakapan ini ikut dikirim ke driver.",false);
            for (String option:new String[]{"food","ride","send","shop"}) addAction(question(option),()->showTyping(()->selectKind(option)));
            return;
        }
        bubble(question(kind),true);
        if ("food".equals(kind)) {
            bubble("Sebentar, aku cek dulu apa yang ada di sekitar kamu, ya.",false);
            if (!reviewFood) { foodControls();return; }
            String selection=foodSelection();
            bubble(selection,true);
            bubble("Oke, aku catat "+selection+". Mau diantar ke mana?",false);
        } else {
            boolean cargo=!"ride".equals(kind);
            if (cargo) {
                bubble("shop".equals(kind)?"Mau belanja apa? Tulis daftar belanjanya, ya.":"Barang apa yang mau kamu kirim?",false);
                if (detail.isEmpty()) { detailControls();return; }
                bubble(detail,true);
            }
            bubble("shop".equals(kind)?"Belanjanya di mana?":("send".equals(kind)?"Barangnya diambil dari mana?":"Siap! Kamu mau dijemput di mana?"),false);
            if (pickup==null) {
                addAction("shop".equals(kind)?"Ambil lokasi toko":"Ambil lokasi jemput",()->pick(PICKUP));
                addAction("Ganti pilihan",this::reset);
                return;
            }
            bubble(pickup.optString("address"),true);
            bubble("ride".equals(kind)?"Sip, sudah aku catat. Tujuannya ke mana?":"Sudah aku catat. Mau diantar ke mana?",false);
        }
        if (delivery==null) {
            addAction("Tentukan alamat pengantaran",()->pick(DELIVERY));
            if ("food".equals(kind)) addAction("Ubah keranjang",this::editCart);
            addAction("Ganti pilihan",this::reset);
        } else {
            bubble(delivery.optString("address"),true);
            if (status.isEmpty() && contact.isEmpty()) bubble("Alamatnya sudah siap. Kirim ke "+driverName+" kalau semuanya sudah pas.",false);
            if (!status.isEmpty()) bubble(status,false);
            if (!problem.isEmpty()) bubble(problem,false);
            if (contact.isEmpty() && !loading) {
                addAction(problem.isEmpty()?"Kirim ke "+driverName:"Coba kirim lagi",this::submit);
                if ("food".equals(kind)) addAction("Ubah keranjang",this::editCart);
                addAction("Ubah alamat pengantaran",()->pick(DELIVERY));
                addAction("Ganti pilihan",this::reset);
            }
            if (!contact.isEmpty() && !terminal()) addAction("Batalkan permintaan",this::cancel);
            if (terminal()) addAction("Pilih driver lain",this::chooseAnotherDriver);
        }
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    private static String question(String type) {
        switch (type) {
            case "food":return "Mau pesan makan?";
            case "ride":return "Mau pesan ojek?";
            case "send":return "Mau kirim barang?";
            default:return "Mau belanja?";
        }
    }

    private void selectKind(String type) {
        kind=type;
        if ("food".equals(type)) loadFood(0,0);
        else render();
    }

    private void reset() {
        if (loading || !contact.isEmpty()) return;
        kind="";detail="";problem="";status="";
        restaurant=null;pickup=null;delivery=null;more=false;reviewFood=false;
        foodCart=new JSONArray();
        clearOptions();
        key=UUID.randomUUID().toString();
        render();
    }

    private void foodControls() {
        if (restaurant!=null) bubble("Kamu pilih "+restaurant.optString("name")+". Mau pesan menu apa?",false);
        if (loading) { bubble("Lagi memuat pilihan…",false);return; }
        if (foodCart.length()>0) {
            bubble("Keranjangmu",false);
            for (int i=0;i<foodCart.length();i++) {
                JSONObject line=foodCart.optJSONObject(i);
                if (line==null) continue;
                int position=i;
                bubble(line.optString("label"),true);
                addAction("Hapus "+line.optString("name"),()->{
                    foodCart.remove(position);
                    key=UUID.randomUUID().toString();
                    render();
                });
            }
            addAction(delivery==null?"Lanjut ke alamat":"Tinjau pesanan",()->showTyping(()->{
                reviewFood=true;
                render();
            }));
        }
        if (!problem.isEmpty()) bubble(problem,false);
        if (options.length()==0 && problem.isEmpty()) bubble(restaurant==null
                ?"Belum ada restoran yang tersedia di halaman ini.":"Menu di sini lagi kosong.",false);
        for (int i=0;i<options.length();i++) {
            JSONObject item=options.optJSONObject(i);
            if (item==null) continue;
            if (restaurant==null) {
                addAction(item.optString("name")+" · "+item.optDouble("distance_km")+" km",()->{
                    restaurant=item;showTyping(()->loadFood(item.optInt("id"),0));
                });
            } else {
                addMenu(item);
            }
        }
        int restaurantId=restaurant==null?0:restaurant.optInt("id");
        if (more) addAction("Tampilkan lainnya",()->loadFood(restaurantId,nextOffset));
        if (!problem.isEmpty()) addAction("Muat ulang",()->loadFood(restaurantId,0));
        if (restaurant!=null) addAction("Pilih restoran lain",()->{
            if (foodCart.length()==0) changeRestaurant();
            else new TransivaAlertDialogBuilder(this).setTitle("Ganti restoran?")
                    .setMessage("Keranjang dari restoran ini akan dikosongkan.")
                    .setNegativeButton("Kembali",null)
                    .setPositiveButton("Ganti restoran",(dialog,choice)->changeRestaurant()).show();
        });
        addAction("Ganti pilihan",this::reset);
    }

    private void addMenu(JSONObject item) {
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackground(CustomerUiPrimitives.round("#E1EFFF",dp(9)));
        photo.setClipToOutline(true);
        String url=ImageUrlResolver.resolve(item.optString("image"));
        if (url!=null && !url.isEmpty()) RemoteImageLoader.loadCenterCrop(photo,url,0);
        LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(44),dp(44));
        image.rightMargin=dp(8);
        row.addView(photo,image);
        row.addView(button(item.optString("name")+
                (item.optBoolean("requires_topping")?" · pilih topping":""),()->startMenu(item)),
                new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
        space.topMargin=dp(6);
        actions.addView(row,space);
    }

    private void showTyping(Runnable reply) {
        if (typing || loading || opened) return;
        typing=true;
        actions.removeAllViews();
        TextView indicator=label("Tiva AI sedang mengetik •",13,"#526E90",false);
        indicator.setPadding(dp(8),dp(7),dp(8),dp(7));
        indicator.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        messages.addView(indicator);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        main.postDelayed(()->indicator.setText("Tiva AI sedang mengetik ••"),220);
        main.postDelayed(()->indicator.setText("Tiva AI sedang mengetik •••"),440);
        main.postDelayed(()->{
            typing=false;
            if (isFinishing() || isDestroyed()) return;
            messages.removeView(indicator);
            reply.run();
        },650);
    }

    private void editCart() {
        reviewFood=false;
        problem="";
        status="";
        if (options.length()==0) loadFood(restaurant.optInt("id"),0);
        else render();
    }

    private void changeRestaurant() {
        restaurant=null;
        foodCart=new JSONArray();
        key=UUID.randomUUID().toString();
        loadFood(0,0);
    }

    private void startMenu(JSONObject item) {
        if (foodCart.length()>=MAX_CART_LINES) {
            toast("Keranjang maksimal 10 pilihan menu. Hapus salah satu untuk menambah lagi.");
            return;
        }
        java.util.ArrayList<JSONObject> groups=new java.util.ArrayList<>();
        JSONArray raw=item.optJSONArray("options");
        if (raw!=null) for (int i=0;i<raw.length();i++) {
            JSONObject group=raw.optJSONObject(i);
            if (group!=null) groups.add(group);
        }
        groups.sort((first,second)->Boolean.compare(
                !"bundle_topping".equals(first.optString("type")),
                !"bundle_topping".equals(second.optString("type"))));
        chooseOptions(item,groups,0,new JSONArray(),false);
    }

    private void chooseOptions(JSONObject item,java.util.List<JSONObject> groups,int position,
                               JSONArray selected,boolean bundleChosen) {
        if (position>=groups.size()) {
            pickQuantity(item,selected);
            return;
        }
        JSONObject group=groups.get(position);
        String type=group.optString("type");
        boolean bundle="bundle_topping".equals(type);
        boolean single=bundle || "variant".equals(type);
        boolean required=bundle && item.optBoolean("requires_topping");
        java.util.ArrayList<String> names=new java.util.ArrayList<>();
        JSONArray entries=group.optJSONArray("items");
        if (entries!=null) for (int i=0;i<entries.length();i++) {
            JSONObject option=entries.optJSONObject(i);
            if (option==null || (bundleChosen && option.optInt("price")>0)) continue;
            String name=option.optString("name");
            if (!name.isEmpty()) names.add(name);
        }
        if (names.isEmpty()) {
            if (required) toast("Topping sedang habis.");
            else chooseOptions(item,groups,position+1,selected,bundleChosen);
            return;
        }
        TransivaAlertDialogBuilder dialog=new TransivaAlertDialogBuilder(this);
        dialog.setTitle(group.optString("label",bundle?"Pilih topping":"Pilih tambahan"))
                .setNegativeButton("Batal",null);
        if (single) {
            if (!required) names.add(0,"Lewati");
            dialog.setItems(names.toArray(new String[0]),(d,index)->{
                boolean chosen=required || index>0;
                if (chosen) addOption(selected,type,names.get(index));
                chooseOptions(item,groups,position+1,selected,bundleChosen || (bundle && chosen));
            });
        } else {
            boolean[] checked=new boolean[names.size()];
            dialog.setMultiChoiceItems(names.toArray(new String[0]),checked,(d,index,value)->checked[index]=value);
            dialog.setPositiveButton("Lanjut",(d,w)->{
                for (int i=0;i<checked.length;i++) if (checked[i]) addOption(selected,type,names.get(i));
                chooseOptions(item,groups,position+1,selected,bundleChosen);
            });
        }
        dialog.show();
    }

    private static void addOption(JSONArray selected,String type,String name) {
        try { selected.put(new JSONObject().put("type",type).put("name",name)); }
        catch (org.json.JSONException error) { throw new IllegalStateException(error); }
    }

    private void pickQuantity(JSONObject item,JSONArray selected) {
        int remaining=item.isNull("stock")?Integer.MAX_VALUE:item.optInt("stock",0);
        for (int i=0;!item.isNull("stock") && i<foodCart.length();i++) {
            JSONObject line=foodCart.optJSONObject(i);
            if (line!=null && line.optInt("id")==item.optInt("id")) remaining-=line.optInt("qty");
        }
        int limit=Math.min(20,remaining);
        if (limit<1) {
            toast("Stok menu ini sudah masuk keranjang semua.");
            return;
        }
        android.widget.NumberPicker picker=new android.widget.NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(limit);
        new TransivaAlertDialogBuilder(this).setTitle("Jumlah porsi · "+item.optString("name"))
                .setView(picker).setNegativeButton("Batal",null)
                .setPositiveButton("Tambah ke keranjang",(dialog,choice)->{
                    picker.clearFocus();
                    addCartLine(item,selected,picker.getValue());
                }).show();
    }

    private void addCartLine(JSONObject item,JSONArray selected,int quantity) {
        String name=item.optString("name");
        StringBuilder line=new StringBuilder().append(quantity).append(" × ").append(name);
        for (int i=0;i<selected.length();i++) {
            JSONObject option=selected.optJSONObject(i);
            if (option!=null) line.append(" · ").append(option.optString("name"));
        }
        try {
            foodCart.put(new JSONObject().put("id",item.optInt("id")).put("name",name)
                    .put("qty",quantity).put("selected_options",selected).put("label",line.toString()));
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
        key=UUID.randomUUID().toString();
        problem="";
        showTyping(()->{
            render();
            bubble("Sudah masuk keranjang. Mau tambah menu lain?",false);
        });
    }

    private String foodSelection() {
        StringBuilder selection=new StringBuilder(restaurant.optString("name"));
        for (int i=0;i<foodCart.length();i++) {
            JSONObject line=foodCart.optJSONObject(i);
            if (line!=null) selection.append("\n").append(line.optString("label"));
        }
        return selection.toString();
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();
    }

    private void detailControls() {
        EditText field=new EditText(this);
        field.setHint("Misalnya dua bungkus roti");
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        field.setMaxLines(3);
        actions.addView(field,new LinearLayout.LayoutParams(-1,-2));
        addAction("Lanjut",()->{
            String value=field.getText().toString().trim();
            if (value.isEmpty()) {field.setError("Isi dulu, ya.");return;}
            detail=value;
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(field.getWindowToken(),0);
            showTyping(this::render);
        });
        addAction("Ganti pilihan",this::reset);
    }

    private void pick(int request) {
        boolean isPickup=request==PICKUP;
        startActivityForResult(new Intent(this,TransSendContactPointActivity.class)
                .putExtra("point_kind",isPickup?"pickup":"delivery")
                .putExtra("title",isPickup?"Atur Titik Jemput":"Tentukan alamat pengantaran")
                .putExtra("tiva_location",true).putExtra("lat",lat).putExtra("lng",lng),request);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if ((request!=PICKUP && request!=DELIVERY) || result!=RESULT_OK || data==null) return;
        try {
            JSONObject point=new JSONObject().put("lat",data.getDoubleExtra("lat",0))
                    .put("lng",data.getDoubleExtra("lng",0)).put("address",data.getStringExtra("address"));
            if (request==PICKUP) {pickup=point;showTyping(this::render);}
            else {
                if (pickup!=null && point.optDouble("lat")==pickup.optDouble("lat")
                        && point.optDouble("lng")==pickup.optDouble("lng")) {
                    android.widget.Toast.makeText(this,"Pilih tujuan berbeda dari lokasi jemput.",android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (delivery!=null && !delivery.toString().equals(point.toString())) key=UUID.randomUUID().toString();
                delivery=point;
                showTyping(this::submit);
            }
        } catch (org.json.JSONException error) {
            problem="Titik belum tersimpan. Coba pilih lagi.";
            render();
        }
    }

    private void loadFood(int restaurantId,int offset) {
        if (loading) return;
        if (offset==0) {clearOptions();more=false;}
        perform(()->{
            JSONObject data=new JSONObject().put("lat",lat).put("lng",lng)
                    .put("restaurant_id",restaurantId).put("offset",offset);
            return TransivaHttpRepository.postJson(this,ApiConfig.server("tiva_simpel_catalog.php"),data,9000);
        },response->{
            JSONArray page=response.optJSONArray(restaurantId==0?"stores":"menus");
            if (page!=null) for (int i=0;i<page.length();i++) options.put(page.opt(i));
            more=response.optBoolean("more");
            nextOffset=response.optInt("next_offset");
        },true);
    }

    private void clearOptions() {
        for (int i=options.length()-1;i>=0;i--) options.remove(i);
    }

    private void submit() {
        if (delivery==null || loading || !contact.isEmpty()) return;
        status="Sedang mengirim permintaan ke "+driverName+"…";
        perform(()->{
            JSONObject draft=new JSONObject().put("key",key).put("kind",kind).put("delivery",delivery);
            if ("food".equals(kind)) draft.put("restaurant_id",restaurant.optInt("id"))
                    .put("food_items",foodCart);
            else draft.put("pickup",pickup).put("detail",detail);
            JSONObject data=TransSendContactClient.action("invite","").put("service","trans_simpel")
                    .put("driver_id",driver).put("lat",lat).put("lng",lng).put("tiva",draft);
            return TransivaHttpRepository.postJsonIdempotent(this,ApiConfig.server("transsend_contact.php"),data,8000,key);
        },this::acceptResponse,true);
    }

    private void checkContact() {
        if (!visible || loading || contact.isEmpty() || terminal() || opened) return;
        perform(()->TransSendContactClient.contact(this,TransSendContactClient.action("get",contact)),
                this::acceptResponse,false);
    }

    private void cancel() {
        perform(()->TransSendContactClient.contact(this,TransSendContactClient.action("cancel",contact)),
                this::acceptResponse,true);
    }

    private String contactState="";

    private boolean terminal() {
        return "rejected".equals(contactState) || "expired".equals(contactState) || "cancelled".equals(contactState);
    }

    private void acceptResponse(JSONObject response) {
        JSONObject entry=response.optJSONObject("contact");
        if (entry==null) return;
        contact=entry.optString("id");
        contactState=entry.optString("state");
        switch (contactState) {
            case "accepted":
            case "ordered":
                status="Driver sudah menerima permintaanmu.";
                if (visible && !opened && !entry.optString("room_id").isEmpty()) {
                    opened=true;
                    TransSendContactClient.openChat(this,entry);
                    finish();
                }
                break;
            case "rejected":status="Driver belum bisa menerima. Kamu bisa pilih driver lain.";break;
            case "expired":status="Waktu menunggu sudah habis. Kamu bisa pilih driver lain.";break;
            case "cancelled":status="Permintaan sudah dibatalkan.";break;
            default:status="Sudah kukirim. Kita tunggu jawaban "+driverName+", ya.";
        }
    }

    private interface Request { JSONObject run() throws Exception; }
    private interface Response { void show(JSONObject result); }

    private void perform(Request request,Response done,boolean redraw) {
        if (loading || opened) return;
        loading=true;
        problem="";
        if (redraw) render();
        network.execute(()->{
            try {
                JSONObject result=request.run();
                if (!result.optBoolean("success")) throw new IllegalStateException(result.optString("message"));
                network.post(main,()->completed(done,result,"",redraw));
            } catch (Exception error) {
                String shown=error instanceof IllegalStateException || error instanceof TransivaHttpRepository.ServerResponseException
                        ? CallPeerIdentity.first(error.getMessage(),"Belum berhasil. Coba lagi, ya.")
                        : "Koneksi terputus. Coba lagi, ya.";
                network.post(main,()->completed(done,null,shown,redraw));
            }
        },()->network.post(main,()->completed(done,null,"Coba lagi sebentar, ya.",redraw)));
    }

    private void completed(Response done,JSONObject result,String error,boolean redraw) {
        if (isFinishing() || isDestroyed()) return;
        loading=false;
        String before=contactState;
        if (result!=null) done.show(result);
        if (!error.isEmpty() && redraw) {problem=error;if (contact.isEmpty()) status="";}
        if (!opened && (redraw || !before.equals(contactState))) render();
        main.removeCallbacks(poll);
        if (visible && !contact.isEmpty() && !terminal() && !opened) main.postDelayed(poll,2500);
    }

    private void bubble(String copy,boolean mine) {
        TextView view=label(copy,14,mine?"#FFFFFF":"#123259",false);
        view.setPadding(dp(12),dp(9),dp(12),dp(9));
        view.setBackground(CustomerUiPrimitives.round(mine?"#075DCF":"#FFFFFF",dp(15)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.gravity=mine?Gravity.END:Gravity.START;
        lp.setMargins(mine?dp(38):0,0,mine?0:dp(32),dp(8));
        messages.addView(view,lp);
    }

    private void addAction(String name,Runnable click) {
        Button option=button(name,click);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(5);
        actions.addView(option,lp);
    }

    private Button button(String name,Runnable click) {
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setTextColor(0xFF084992);
        button.setText(name);
        button.setMinHeight(dp(46));
        button.setPadding(dp(12),dp(7),dp(12),dp(7));
        button.setBackground(CustomerUiPrimitives.roundStroke(this,"#E9F3FF","#BAD8F9",dp(12),1));
        button.setOnClickListener(v->click.run());
        return button;
    }

    private TextView label(String title,int size,String color,boolean bold) {
        return CustomerUiPrimitives.text(this,title,size,color,bold);
    }

    private int dp(int value) {return CustomerUiPrimitives.dp(this,value);}
}
