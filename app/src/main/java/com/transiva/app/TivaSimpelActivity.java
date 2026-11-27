package com.transiva.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
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
    private boolean driverBusy;
    private String kind="", detail="", contact="", key=UUID.randomUUID().toString();
    private String status="", problem="", menuSearch="";
    private JSONObject acceptedContact;
    private boolean typing, reviewFood;
    private JSONArray foodCart=new JSONArray();
    private JSONObject pickup, delivery, restaurant, foodQuote, routeQuote;
    private String receiverName="", receiverPhone="";
    private long quotedAt;
    private int driver, nextOffset;
    private double lat,lng;
    private String driverName;
    private boolean more, loading, visible, opened;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        driver=getIntent().getIntExtra("driver_id",0);
        driverBusy=getIntent().getBooleanExtra("driver_busy",false);
        driverName=CallPeerIdentity.first(getIntent().getStringExtra("driver_name"),"driver pilihanmu");
        lat=getIntent().getDoubleExtra("lat",0);
        lng=getIntent().getDoubleExtra("lng",0);
        if (driver<=0 || lat==0 || lng==0) { finish();return; }
        Bundle draft=state==null?getIntent().getBundleExtra(EXTRA_DRAFT):state;
        if (draft!=null) restoreDraft(draft);
        if (state!=null) {
            contact=state.getString("tiva.contact","");
            opened=state.getBoolean("tiva.opened");
            acceptedContact=parse(state,"tiva.accepted_contact");
            contactState=state.getString("tiva.contact_state","");
            status=state.getString("tiva.status","");
            key=state.getString("tiva.key",key);
        }
        registerCustomBackNavigation();
        buildScreen();
        if (draft==null) showTyping(this::render);
        else render();
    }

    @Override public void onBackPressed() {
        if (contact.isEmpty() && !kind.isEmpty() && !loading) chooseAnotherDriver();
        else finish();
    }

    private void buildScreen() {
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFFF2F7FD);
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6),dp(5),dp(14),dp(5));
        header.setBackground(CustomerUiPrimitives.gradient("#082D79","#0877DF",0));
        Button back=button("‹",this::onBackPressed);
        back.setBackgroundColor(0x00000000);
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(28);
        back.setContentDescription("Kembali");
        header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        ImageView avatar=new ImageView(this);
        avatar.setImageResource(R.drawable.tiva_ai_avatar);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(CustomerUiPrimitives.round("#E9F3FF",dp(24)));
        avatar.setClipToOutline(true);
        header.addView(avatar,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout identity=new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(10),0,0,0);
        identity.addView(label("Tiva AI",19,"#FFFFFF",true));
        identity.addView(label("Trans Simpel · "+driverName,12,"#E0F0FF",false));
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
        else if ("food".equals(kind) && reviewFood && delivery!=null && foodQuote==null && !typing && problem.isEmpty()) quoteFood();
        else if (requiresRouteReview() && pickup!=null && delivery!=null && routeQuote==null
                && contact.isEmpty() && !typing && problem.isEmpty()) quoteRoute();
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
        out.putBoolean("tiva.opened",opened);
        save(out,"tiva.accepted_contact",acceptedContact);
        out.putString("tiva.contact_state",contactState);
        out.putString("tiva.status",status);
        out.putString("tiva.key",key);
    }

    private void saveDraft(Bundle out) {
        out.putString("tiva.kind",kind);
        out.putString("tiva.detail",detail);
        out.putString("tiva.menu_search",menuSearch);
        out.putString("tiva.cart",foodCart.toString());
        out.putBoolean("tiva.review",reviewFood);
        save(out,"tiva.pickup",pickup);
        save(out,"tiva.delivery",delivery);
        save(out,"tiva.restaurant",restaurant);
    }

    private void restoreDraft(Bundle draft) {
        kind=draft.getString("tiva.kind","");
        detail=draft.getString("tiva.detail","");
        menuSearch=draft.getString("tiva.menu_search","");
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
        bubble("Hai! Mau kubantu pesan apa?",false);
        if (kind.isEmpty()) {
            TextView info=label("Setelah alamat dipilih, percakapan ini ikut dikirim ke driver.",14,"#194979",false);
            info.setPadding(dp(12),dp(10),dp(12),dp(10));
            info.setBackground(CustomerUiPrimitives.round("#D5EBFF",dp(14)));
            messages.addView(info,new LinearLayout.LayoutParams(-1,-2));
            if (driverBusy) {
                bubble("Driver sedang sibuk. Ojek dan Belanja bisa masuk antrean setelah driver menyetujui permintaan dan kamu memesan.",false);
                serviceRow("ride","Pesan ojek",R.drawable.ic_service_ride_premium,"#DCEFFF",
                        "shop","Belanja",R.drawable.ic_service_shop_premium,"#DCF8E9");
            } else {
                serviceRow("food","Pesan makan",R.drawable.ic_service_food_premium,"#FFF0DC",
                        "ride","Pesan ojek",R.drawable.ic_service_ride_premium,"#DCEFFF");
                serviceRow("send","Kirim barang",R.drawable.ic_service_send_premium,"#EEE7FF",
                        "shop","Belanja",R.drawable.ic_service_shop_premium,"#DCF8E9");
            }
            return;
        }
        bubble(question(kind),true);
        if ("food".equals(kind)) {
            if (!reviewFood) {
                if (!loading && restaurant == null && menuSearch.isEmpty() && options.length() > 0) {
                    bubble("Ini restoran di sekitarmu.", false);
                }
                foodControls();
                return;
            }
            showCart(false);
            if (delivery==null) bubble("Mau diantar ke mana?",false);
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
            addPrimary("Tentukan alamat pengantaran",()->pick(DELIVERY));
            if ("food".equals(kind)) addAction("Ubah keranjang",this::editCart);
            addAction("Ganti pilihan",this::reset);
        } else {
            bubble(delivery.optString("address"),true);
            if (status.isEmpty() && problem.isEmpty() && contact.isEmpty() && !loading) {
                bubble(requiresRouteReview()?"Cek dulu rinciannya. Kalau sudah pas, aku kirim ke "+driverName+"."
                        :"Alamatnya sudah siap. Kirim ke "+driverName+" kalau semuanya sudah pas.",false);
            }
            if (!status.isEmpty()) bubble(status,false);
            if (!problem.isEmpty()) bubble(problem,false);
            if (contact.isEmpty() && "food".equals(kind) && foodQuote!=null) addFoodSummary();
            if (contact.isEmpty() && requiresRouteReview()) addRouteSummary();
            if (contact.isEmpty() && !loading) {
                if ("food".equals(kind) && foodQuote==null) addPrimary("Hitung subtotal dan ongkir",this::quoteFood);
                else if (requiresRouteReview() && routeQuote==null) addPrimary("Hitung estimasi ongkir",this::quoteRoute);
                else addPrimary("Kirim ke "+driverName,this::submit);
                if ("food".equals(kind)) addAction("Ubah keranjang",this::editCart);
                if (requiresRouteReview()) {
                    addRouteEdits();
                    if ("shop".equals(kind)) addAction("Ubah daftar belanja",()->{detail="";routeQuote=null;render();});
                } else addAction("Ubah alamat pengantaran",()->pick(DELIVERY));
                addAction("Ganti pilihan",this::reset);
            }
            if (acceptedContact!=null) addPrimary("Buka chat driver",()->TransSendContactClient.openChat(this,acceptedContact));
            else if (!contact.isEmpty() && !terminal()) addAction("Batalkan permintaan",this::cancel);
            if (terminal()) addAction("Pilih driver lain",this::chooseAnotherDriver);
        }
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    private static String question(String type) {
        switch (type) {
            case "food":return "Mau pesan makanan.";
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
        foodQuote=null;
        routeQuote=null;
        clearOptions();
        key=UUID.randomUUID().toString();
        render();
    }

    private void foodControls() {
        if (restaurant!=null) bubble("Kamu pilih "+restaurant.optString("name")+". Mau pesan menu apa?",false);
        if (loading) { bubble("Lagi memuat pilihan…",false);return; }
        if (restaurant==null) {
            addMenuSearch();
            if (!menuSearch.isEmpty()) addAction("Hapus pencarian",()->{menuSearch="";loadFood(0,0);});
        }
        if (foodCart.length()>0) {
            showCart(true);
            addPrimary(delivery==null?"Lanjut ke alamat":"Tinjau pesanan",()->showTyping(()->{
                reviewFood=true;
                if (delivery==null) render(); else quoteFood();
            }));
        }
        if (!problem.isEmpty()) bubble(problem,false);
        boolean searching=restaurant==null && !menuSearch.isEmpty();
        if (options.length()==0 && problem.isEmpty()) bubble(searching
                ? "Belum ada menu yang cocok pada halaman ini."
                : restaurant==null
                ?"Belum ada restoran yang tersedia di halaman ini.":"Menu di sini lagi kosong.",false);
        LinearLayout restaurants = null;
        if (options.length()>0) {
            TextView heading=label(searching?"Hasil untuk "+menuSearch:restaurant==null?"Restoran sekitar":"Menu "+restaurant.optString("name"),19,"#10294A",true);
            heading.setPadding(0,dp(6),0,dp(3));
            actions.addView(heading);
            if (!searching && restaurant == null) {
                restaurants = new LinearLayout(this);
                restaurants.setOrientation(LinearLayout.VERTICAL);
                restaurants.setBackground(CustomerUiPrimitives.roundStroke(this, "#FFFFFF", "#D8E8F8", dp(15), 1));
                restaurants.setClipToOutline(true);
                LinearLayout.LayoutParams panel = new LinearLayout.LayoutParams(-1, -2);
                panel.setMargins(0, dp(4), 0, dp(5));
                actions.addView(restaurants, panel);
            }
        }
        for (int i=0;i<options.length();i++) {
            JSONObject item=options.optJSONObject(i);
            if (item==null) continue;
            if (searching) {
                JSONObject store=item.optJSONObject("restaurant");
                if (store==null) continue;
                addFoodCard(null,item,store.optString("name")+" · "+menuPrice(item),
                        R.drawable.ic_service_food_premium,()->{
                    restaurant=store;
                    clearOptions();
                    more=false;
                    startMenu(item);
                    loadFood(store.optInt("id"),0);
                });
            } else if (restaurant==null) {
                String distance=String.format(new java.util.Locale("id","ID"),"%.1f km",item.optDouble("distance_km",0));
                addFoodCard(restaurants,item,distance,R.drawable.ic_service_food_premium,()->{
                    restaurant=item;
                    showTyping(()->loadFood(item.optInt("id"),0));
                });
            } else {
                addFoodCard(null,item,menuPrice(item),R.drawable.ic_service_food_premium,()->startMenu(item));
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

    private void addMenuSearch() {
        LinearLayout searchRow = new LinearLayout(this);
        boolean stack = getResources().getConfiguration().screenWidthDp < 330
                || getResources().getConfiguration().fontScale > 1.25f;
        searchRow.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(4), dp(3), dp(4), dp(3));
        searchRow.setBackground(CustomerUiPrimitives.roundStroke(this, "#FFFFFF", "#B9D3EF", dp(14), 1));

        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Contoh: bakso");
        query.setText(menuSearch);
        query.setTextSize(14);
        query.setTextColor(0xFF10294A);
        query.setHintTextColor(0xFF596F88);
        query.setPadding(dp(9), dp(4), dp(7), dp(4));
        query.setMinHeight(dp(48));
        query.setBackground(null);
        query.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_smart_search, 0, 0, 0);
        query.setCompoundDrawablePadding(dp(6));
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(query, new LinearLayout.LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));

        Runnable search = () -> {
            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
            menuSearch = query.getText().toString().trim();
            loadFood(0, 0);
        };
        query.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            search.run();
            return true;
        });
        Button submit = button("Cari menu", search);
        submit.setTextSize(13);
        submit.setTextColor(0xFFFFFFFF);
        submit.setBackground(CustomerUiPrimitives.round("#075ACB", dp(10)));
        searchRow.addView(submit, new LinearLayout.LayoutParams(stack ? -1 : -2, -2));
        actions.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
    }

    private String menuPrice(JSONObject item) {
        long price=item.optLong("price");
        String cost=price>0?"Rp "+java.text.NumberFormat.getIntegerInstance(new java.util.Locale("id","ID")).format(price):"";
        if (!item.optBoolean("requires_topping")) return cost;
        return cost.isEmpty()?"Pilih topping":"Mulai "+cost+" · pilih topping";
    }

    private LinearLayout detailCard(LinearLayout parent,String title,String color) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(9),dp(12),dp(9));
        card.setBackground(CustomerUiPrimitives.roundStroke(this,color,"#CDE0F5",dp(16),1));
        card.addView(label(title,16,"#153A60",true));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2);
        gap.bottomMargin=dp(7);
        parent.addView(card,gap);
        return card;
    }

    private void showCart(boolean editable) {
        LinearLayout card=detailCard(editable?actions:messages,editable?"Keranjang · "+restaurant.optString("name"):
                "Ringkasan · "+restaurant.optString("name"),"#FFFFFF");
        for (int i=0;i<foodCart.length();i++) {
            JSONObject line=foodCart.optJSONObject(i);
            if (line==null) continue;
            int position=i;
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0,dp(4),0,dp(3));
            row.addView(label(cartLabel(line),14,"#173A60",true),new LinearLayout.LayoutParams(0,-2,1));
            if (editable) {
                Button minus=button("−",()->changeQuantity(position,-1));
                minus.setContentDescription((line.optInt("qty")==1?"Hapus ":"Kurangi ")+line.optString("name"));
                Button plus=button("+",()->changeQuantity(position,1));
                plus.setContentDescription("Tambah "+line.optString("name"));
                plus.setEnabled(canIncrease(line));
                for (Button control:new Button[]{minus,plus}) {
                    control.setPadding(0,0,0,0);
                    LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(dp(48),-2);
                    gap.setMarginStart(dp(4));
                    row.addView(control,gap);
                }
            }
            card.addView(row);
        }
    }

    private boolean canIncrease(JSONObject line) {
        if (line.optInt("qty")>=20) return false;
        int menuId=line.optInt("id");
        int stock=line.optInt("stock",Integer.MAX_VALUE);
        for (int i=0;i<options.length();i++) {
            JSONObject item=options.optJSONObject(i);
            if (item!=null && item.optInt("id")==menuId && !item.isNull("stock")) stock=item.optInt("stock");
        }
        int ordered=0;
        for (int i=0;i<foodCart.length();i++) {
            JSONObject item=foodCart.optJSONObject(i);
            if (item!=null && item.optInt("id")==menuId) ordered+=item.optInt("qty");
        }
        return ordered<stock;
    }

    private void changeQuantity(int position,int delta) {
        JSONObject line=foodCart.optJSONObject(position);
        if (line==null || loading || (delta>0 && !canIncrease(line))) return;
        int quantity=line.optInt("qty")+delta;
        if (quantity==0) foodCart.remove(position);
        else try {
            line.put("qty",quantity).put("label",cartLabel(line));
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        foodQuote=null;
        routeQuote=null;
        key=UUID.randomUUID().toString();
        render();
    }

    private String cartLabel(JSONObject line) {
        StringBuilder copy=new StringBuilder().append(line.optInt("qty")).append(" × ").append(line.optString("name"));
        JSONArray choices=line.optJSONArray("selected_options");
        if (choices!=null) for (int i=0;i<choices.length();i++) {
            JSONObject choice=choices.optJSONObject(i);
            if (choice!=null) copy.append(" · ").append(choice.optString("name"));
        }
        return copy.toString();
    }

    private boolean requiresRouteReview() {
        return "ride".equals(kind) || "shop".equals(kind) || "send".equals(kind);
    }

    private void quoteRoute() {
        if (!requiresRouteReview() || pickup==null || delivery==null || loading || !contact.isEmpty()) return;
        routeQuote=null;
        status="Aku hitung estimasi ongkirnya dulu, ya.";
        if ("send".equals(kind)) {
            perform(()->{
                JSONObject result=TransSendContactClient.quote(this,pointIntent(pickup),pointIntent(delivery));
                return result.put("quote",new JSONObject().put("total",result.optLong("price")));
            },response->{
                status="";
                routeQuote=response.optJSONObject("quote");
                quotedAt=android.os.SystemClock.elapsedRealtime();
            },true);
            return;
        }
        perform(()->TransivaHttpRepository.postJson(this,ApiConfig.server("trans_simpel.php"),
                new JSONObject().put("action","preview_route").put("kind",kind)
                        .put("origin",new JSONObject().put("lat",lat).put("lng",lng))
                        .put("pickup",pickup).put("delivery",delivery),14000),response->{
            status="";
            routeQuote=response.optJSONObject("quote");
            quotedAt=android.os.SystemClock.elapsedRealtime();
            if (routeQuote==null) problem="Estimasi ongkir belum tersedia. Coba lagi.";
        },true);
    }

    private void addRouteEdits() {
        LinearLayout row=new LinearLayout(this);
        actions.addView(row,new LinearLayout.LayoutParams(-1,-2));
        Button from=button("shop".equals(kind)?"Ubah toko":"Ubah jemput",()->pick(PICKUP));
        Button to=button("Ubah tujuan",()->pick(DELIVERY));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,-2,1);
        space.setMargins(0,dp(5),dp(6),0);
        row.addView(from,space);
        LinearLayout.LayoutParams end=new LinearLayout.LayoutParams(0,-2,1);
        end.topMargin=dp(5);
        row.addView(to,end);
    }

    private void addRouteSummary() {
        boolean shopping="shop".equals(kind);
        LinearLayout card=detailCard(actions,shopping?"Tinjau pesanan belanja":"Tinjau perjalanan","#E3F7EE");
        card.addView(label((shopping?"Toko":"Jemput")+" · "+pickup.optString("address"),14,"#164F40",true));
        card.addView(label("Antar · "+delivery.optString("address"),14,"#164F40",false));
        if (shopping) card.addView(label("Daftar belanja · "+detail,14,"#164F40",false));
        if (routeQuote==null) return;
        addMoney(card,shopping?"Estimasi ongkir":"Estimasi tarif",routeQuote.optLong("total"),true);
        if (shopping) card.addView(label("Di luar harga barang belanjaan.",13,"#164F40",false));
    }

    private void quoteFood() {
        if (!reviewFood || restaurant==null || delivery==null || foodCart.length()==0 || loading) return;
        foodQuote=null;
        routeQuote=null;
        status="Menghitung subtotal dan ongkir…";
        perform(()->{
            JSONObject draft=new JSONObject().put("restaurant_id",restaurant.optInt("id"))
                    .put("origin",new JSONObject().put("lat",lat).put("lng",lng).put("address","Lokasi pencarian"))
                    .put("delivery",delivery).put("food_items",foodCart);
            return TransivaHttpRepository.postJson(this,ApiConfig.server("create_food_order.php"),
                    new JSONObject().put("quote_only",true).put("tiva_preview",draft),14000);
        },response->{
            status="";
            foodQuote=response.optJSONObject("quote");
            quotedAt=android.os.SystemClock.elapsedRealtime();
            if (foodQuote==null) problem="Rincian harga belum tersedia. Coba lagi.";
        },true);
    }

    private void addFoodSummary() {
        LinearLayout card=detailCard(actions,"Estimasi dari server","#E3F7EE");
        addMoney(card,"Subtotal menu",foodQuote.optLong("food_total"),false);
        addMoney(card,"Ongkir",foodQuote.optLong("delivery_fee"),false);
        addMoney(card,"Total estimasi",foodQuote.optLong("total"),true);
    }

    private void addMoney(LinearLayout card,String title,long amount,boolean bold) {
        LinearLayout row=new LinearLayout(this);
        row.addView(label(title,14,"#164F40",bold),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(label("Rp "+java.text.NumberFormat.getIntegerInstance(new java.util.Locale("id","ID")).format(amount),
                14,"#164F40",true));
        card.addView(row);
    }

    private void serviceRow(String leftKind,String leftLabel,int leftArt,String leftColor,
                            String rightKind,String rightLabel,int rightArt,String rightColor) {
        LinearLayout row=new LinearLayout(this);
        actions.addView(row,new LinearLayout.LayoutParams(-1,-2));
        addService(row,leftKind,leftLabel,leftArt,leftColor,0);
        addService(row,rightKind,rightLabel,rightArt,rightColor,8);
    }

    private void addService(LinearLayout row,String service,String title,int art,String color,int gap) {
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10),dp(8),dp(10),dp(10));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(CustomerUiPrimitives.roundStroke(this,color,"#FFFFFF",dp(16),1));
        ImageView image=new ImageView(this);
        image.setImageResource(art);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(image,new LinearLayout.LayoutParams(-1,dp(65)));
        card.addView(label(title+" ›",16,"#10294A",true));
        card.setFocusable(true);
        card.setOnClickListener(v->showTyping(()->selectKind(service)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(dp(gap),dp(8),0,0);
        row.addView(card,lp);
    }

    private void addFoodCard(LinearLayout restaurants, JSONObject item, String caption, int placeholder, Runnable click) {
        boolean store = restaurants != null;
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(7),dp(9),dp(7));
        row.setMinimumHeight(dp(store ? 58 : 66));
        if (!store) row.setBackground(CustomerUiPrimitives.roundStroke(this,"#FFFFFF","#D8E8F8",dp(15),1));

        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImageResource(placeholder);
        photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        photo.setBackground(CustomerUiPrimitives.round("#E6F1FD",dp(11)));
        photo.setClipToOutline(true);
        String url=ImageUrlResolver.resolve(item.optString("image"));
        if (url!=null && !url.isEmpty()) RemoteImageLoader.loadCenterCrop(photo,url,placeholder);
        int photoSize = dp(store ? 42 : 48);
        LinearLayout.LayoutParams photoParams=new LinearLayout.LayoutParams(photoSize,photoSize);
        photoParams.rightMargin=dp(10);
        row.addView(photo,photoParams);
        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(item.optString("name"),store ? 14 : 16,"#242A34",true));
        if (!store && !caption.isEmpty()) labels.addView(label(caption,13,"#215D43",true));
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if (store) {
            TextView distance=label(caption,13,"#16513F",true);
            distance.setPadding(dp(6),dp(3),dp(6),dp(3));
            distance.setBackground(CustomerUiPrimitives.round("#DEF6EB",dp(16)));
            row.addView(distance);
        }
        TextView arrow=label(store?" ›":" +",21,"#075DCF",true);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow);
        row.setFocusable(true);
        row.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x24075ACB), null,
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        row.setOnClickListener(v->click.run());
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,-2);
        if (store) {
            if (restaurants.getChildCount() > 0) {
                View divider = new View(this);
                divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                divider.setBackgroundColor(0xFFD8E8F8);
                LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
                line.setMargins(dp(8), 0, dp(8), 0);
                restaurants.addView(divider, line);
            }
            restaurants.addView(row, space);
        } else {
            space.topMargin = dp(6);
            actions.addView(row, space);
        }
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
        foodQuote=null;
        routeQuote=null;
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
        java.util.Collections.sort(groups, (first, second) -> Boolean.compare(
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
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(14), dp(4), dp(14), dp(6));
        panel.setFocusableInTouchMode(true);

        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER);
        panel.addView(stepper, new LinearLayout.LayoutParams(-1, -2));

        EditText quantity = new EditText(this);
        quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantity.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        quantity.setText("1");
        quantity.setSelectAllOnFocus(true);
        quantity.setSingleLine();
        quantity.setTextSize(24);
        quantity.setTextColor(0xFF252A34);
        quantity.setGravity(Gravity.CENTER);
        quantity.setPadding(dp(6), dp(6), dp(6), dp(6));
        quantity.setContentDescription("Jumlah porsi");
        quantity.setBackground(CustomerUiPrimitives.roundStroke(this, "#EDF5FF", "#B9D3EF", dp(12), 1));

        Button minus = button("−", () -> quantity.setText(String.valueOf(
                Math.max(1, Math.min(limit, portionValue(quantity) - 1)))));
        minus.setContentDescription("Kurangi porsi");
        Button plus = button("+", () -> quantity.setText(String.valueOf(
                Math.min(limit, portionValue(quantity) + 1))));
        plus.setContentDescription("Tambah porsi");
        minus.setTextSize(20);
        plus.setTextSize(20);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(76), -2);
        valueParams.setMargins(dp(8), 0, dp(8), 0);
        quantity.setMinHeight(dp(54));
        stepper.addView(quantity, valueParams);
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView hint = label("Maksimal " + limit + " porsi", 13, "#52647C", false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        panel.addView(hint);
        Runnable updateControls = () -> {
            int count = portionValue(quantity);
            minus.setEnabled(count > 1);
            plus.setEnabled(count < limit);
            minus.setAlpha(minus.isEnabled() ? 1f : .45f);
            plus.setAlpha(plus.isEnabled() ? 1f : .45f);
        };
        quantity.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateControls.run();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        updateControls.run();

        AlertDialog dialog = new TransivaAlertDialogBuilder(this)
                .setTitle("Jumlah porsi · " + item.optString("name"))
                .setView(panel).setNegativeButton("Batal", null)
                .setPositiveButton("Tambah ke keranjang", null).create();
        dialog.setOnShowListener(ignored -> {
            panel.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                int count = portionValue(quantity);
                if (count < 1 || count > limit) {
                    quantity.requestFocus();
                    quantity.setError("Isi 1 sampai " + limit + " porsi");
                    return;
                }
                dialog.dismiss();
                addCartLine(item, selected, count);
            });
        });
        dialog.show();
    }

    private static int portionValue(EditText field) {
        try {
            return Integer.parseInt(field.getText().toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void addCartLine(JSONObject item,JSONArray selected,int quantity) {
        try {
            JSONObject line=new JSONObject().put("id",item.optInt("id")).put("name",item.optString("name"))
                    .put("qty",quantity).put("selected_options",selected);
            if (!item.isNull("stock")) line.put("stock",item.optInt("stock"));
            foodCart.put(line.put("label",cartLabel(line)));
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
        foodQuote=null;
        routeQuote=null;
        key=UUID.randomUUID().toString();
        problem="";
        showTyping(this::render);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();
    }

    private void detailControls() {
        EditText field=new EditText(this);
        field.setHint("Misalnya dua bungkus roti");
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter("shop".equals(kind)?2000:300)});
        field.setMaxLines(3);
        actions.addView(field,new LinearLayout.LayoutParams(-1,-2));
        addAction("Lanjut",()->{
            String value=field.getText().toString().trim();
            if (value.isEmpty()) {field.setError("Isi dulu, ya.");return;}
            if (!detail.equals(value)) key=UUID.randomUUID().toString();
            detail=value;
            routeQuote=null;
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(field.getWindowToken(),0);
            showTyping(requiresRouteReview() && pickup!=null && delivery!=null?this::quoteRoute:this::render);
        });
        addAction("Ganti pilihan",this::reset);
    }

    private void pick(int request) {
        boolean isPickup=request==PICKUP;
        JSONObject seed=isPickup?pickup:delivery;
        Intent picker=new Intent(this,TransSendContactPointActivity.class)
                .putExtra("point_kind",isPickup?"pickup":"delivery")
                .putExtra("lat",seed==null?lat:seed.optDouble("lat"))
                .putExtra("lng",seed==null?lng:seed.optDouble("lng"));
        JSONObject origin=pickup!=null?pickup:restaurant==null?null:restaurant.optJSONObject("pickup");
        if (origin!=null) picker.putExtra("saved_pickup",origin.toString());
        if (delivery!=null) picker.putExtra("saved_delivery",delivery.toString());
        startActivityForResult(picker,request);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if ((request!=PICKUP && request!=DELIVERY) || result!=RESULT_OK || data==null) return;
        try {
            JSONObject point=new JSONObject().put("lat",data.getDoubleExtra("lat",0))
                    .put("lng",data.getDoubleExtra("lng",0)).put("address",data.getStringExtra("address"));
            if (request==PICKUP) {
                if (pickup!=null && !pickup.toString().equals(point.toString())) key=UUID.randomUUID().toString();
                pickup=point;
                routeQuote=null;
                showTyping(requiresRouteReview() && delivery!=null?this::quoteRoute:this::render);
            }
            else {
                if (pickup!=null && point.optDouble("lat")==pickup.optDouble("lat")
                        && point.optDouble("lng")==pickup.optDouble("lng")) {
                    android.widget.Toast.makeText(this,"Pilih tujuan berbeda dari lokasi jemput.",android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (delivery!=null && !delivery.toString().equals(point.toString())) key=UUID.randomUUID().toString();
                delivery=point;
                routeQuote=null;
                showTyping("food".equals(kind)?this::quoteFood:requiresRouteReview()?this::quoteRoute:this::submit);
            }
        } catch (org.json.JSONException error) {
            problem="Titik belum tersimpan. Coba pilih lagi.";
            render();
        }
    }

    private void loadFood(int restaurantId,int offset) {
        if (loading) return;
        if (offset==0) {clearOptions();more=false;}
        String search=restaurantId==0?menuSearch:"";
        perform(()->{
            JSONObject data=new JSONObject().put("lat",lat).put("lng",lng)
                    .put("restaurant_id",restaurantId).put("offset",offset).put("q",search);
            return TransivaHttpRepository.postJson(this,ApiConfig.server("tiva_simpel_catalog.php"),data,9000);
        },response->{
            JSONArray page=response.optJSONArray(restaurantId==0 && search.isEmpty()?"stores":"menus");
            if (page!=null) for (int i=0;i<page.length();i++) options.put(page.opt(i));
            more=response.optBoolean("more");
            nextOffset=response.optInt("next_offset");
        },true);
    }

    private void clearOptions() {
        for (int i=options.length()-1;i>=0;i--) options.remove(i);
    }

    private Intent pointIntent(JSONObject point) {
        return new Intent().putExtra("lat",point.optDouble("lat")).putExtra("lng",point.optDouble("lng"));
    }

    private void askRecipient() {
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20),dp(4),dp(20),0);
        android.widget.EditText name=new android.widget.EditText(this);
        name.setHint("Nama penerima");
        name.setText(receiverName);
        android.widget.EditText phone=new android.widget.EditText(this);
        phone.setHint("Nomor HP penerima");
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phone.setText(receiverPhone);
        fields.addView(name);fields.addView(phone);
        android.app.AlertDialog dialog=new TransivaAlertDialogBuilder(this).setTitle("Penerima paket")
                .setView(fields).setNegativeButton("Kembali",null).setPositiveButton("Kirim pesanan",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String label=name.getText().toString().trim();
            String number=phone.getText().toString().trim().replace(" ","").replace("-","");
            if (label.isEmpty() || label.length()>100) { name.setError("Nama penerima wajib diisi"); return; }
            if (!number.matches("[+]?[0-9]{8,16}")) { phone.setError("Periksa nomor HP penerima"); return; }
            receiverName=label;receiverPhone=number;
            dialog.dismiss();
            submit();
        }));
        dialog.show();
    }

    private void submit() {
        if (delivery==null || loading || !contact.isEmpty()) return;
        if (driverBusy && ("food".equals(kind) || "send".equals(kind))) {
            problem="Pilih driver yang siap menerima untuk layanan ini.";
            render();
            return;
        }
        if ("food".equals(kind) && (foodQuote==null || android.os.SystemClock.elapsedRealtime()-quotedAt>120000L)) {
            quoteFood();
            return;
        }
        if (requiresRouteReview() && (routeQuote==null || android.os.SystemClock.elapsedRealtime()-quotedAt>120000L)) {
            quoteRoute();
            return;
        }
        if ("send".equals(kind) && receiverPhone.isEmpty()) {
            askRecipient();
            return;
        }
        long total=("food".equals(kind)?foodQuote:routeQuote).optLong("total");
        if (total<=0) {
            problem="Tarif belum tersedia. Hitung kembali.";
            render();
            return;
        }
        status="Sedang mengirim pesanan ke "+driverName+"…";
        perform(()->{
            JSONObject draft=new JSONObject().put("key",key).put("kind",kind).put("delivery",delivery).put("authorized_total",total);
            if ("food".equals(kind)) draft.put("restaurant_id",restaurant.optInt("id"))
                    .put("food_items",foodCart);
            else draft.put("pickup",pickup).put("detail",detail);
            if ("send".equals(kind)) draft.put("receiver_name",receiverName).put("receiver_phone",receiverPhone);
            JSONObject data=TransSendContactClient.action("invite","").put("service","trans_simpel")
                    .put("driver_id",driver).put("lat",lat).put("lng",lng).put("tiva",draft).put("queue_capable",true);
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
                acceptedContact=entry;
                if (visible && !opened && !entry.optString("room_id").isEmpty()) {
                    opened=true;
                    TransSendContactClient.openChat(this,entry);
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
        if (redraw || !before.equals(contactState)) render();
        main.removeCallbacks(poll);
        if (visible && !contact.isEmpty() && !terminal() && !opened) main.postDelayed(poll,2500);
    }

    private void bubble(String copy,boolean mine) {
        TextView view=label(copy,14,mine?"#FFFFFF":"#242A34",false);
        view.setPadding(dp(12),dp(9),dp(12),dp(9));
        view.setBackground(CustomerUiPrimitives.round(mine?"#075ACB":"#E4EFFD",dp(15)));
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

    private void addPrimary(String name,Runnable click) {
        Button action=button(name,click);
        action.setTextColor(0xFFFFFFFF);
        action.setBackground(CustomerUiPrimitives.gradient("#093AAE","#087CE4",dp(15)));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2);
        gap.topMargin=dp(6);
        actions.addView(action,gap);
    }

    private Button button(String name,Runnable click) {
        Button button=new Button(this);
        button.setAllCaps(false);
        button.setTextColor(0xFF084992);
        button.setText(name);
        button.setTypeface(null,android.graphics.Typeface.BOLD);
        button.setMinHeight(dp(46));
        button.setPadding(dp(12),dp(6),dp(12),dp(6));
        button.setBackground(CustomerUiPrimitives.roundStroke(this,"#FFFFFF","#B9D3EF",dp(13),1));
        button.setOnClickListener(v->click.run());
        return button;
    }

    private TextView label(String title,int size,String color,boolean bold) {
        return CustomerUiPrimitives.text(this,title,size,color,bold);
    }

    private int dp(int value) {return CustomerUiPrimitives.dp(this,value);}
}
