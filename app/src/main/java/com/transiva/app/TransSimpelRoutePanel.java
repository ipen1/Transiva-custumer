package com.transiva.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** Shared route state is owned by the server. Only the customer can submit the order. */
final class TransSimpelRoutePanel {
    private static final int PICKUP = 681;
    private static final int DELIVERY = 682;
    private static final int STOP = 683;
    private final Activity activity;
    private final String id;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::refresh;
    private final LinearLayout view;
    private final android.widget.ScrollView routeScroll;
    private int routeHeightLimit;
    private final LinearLayout fareRow;
    private final Button stop;
    private final Button clearStop;
    private final Button quoteRetry;
    private final Button briefButton;
    private final TextView purchaseHint;
    private JSONObject tivaBrief;
    private final Button order;
    private final Button trackOrder;
    private final Button mapToggle;
    private final SmartWaitingTextView waitingView;
    private boolean mapExpanded = true;
    private boolean checkoutBlocked;
    private final Button confirmDelivery, approveStop, rejectStop, cancelDraft;
    private TransivaGoogleMapView routeMap;
    private boolean routeMapResumed;
    private String mapOrder = "";
    private org.json.JSONArray mapPoints;
    private boolean fitMap, mapHasDriver, liveRouteRunning;
    private org.json.JSONArray liveRoutePoints;
    private long lastLiveRouteRequest;
    private String liveRouteTarget = "";
    private final TextView pickupAddress;
    private final TextView stopAddress;
    private final TextView deliveryAddress;
    private final TextView summary;
    private final CustomerLifecycleNetworkScope network = new CustomerLifecycleNetworkScope();
    private JSONObject route;
    private JSONObject previous;
    private JSONObject foodQuote;
    private int foodQuoteRevision=-1;
    private int pickerRevision = -1;
    private int quotedRevision = -1;
    private boolean active;
    private boolean busy;
    private boolean readingRoute;
    private boolean available=true;

    private final TextView routeTitle;

    TransSimpelRoutePanel(Activity activity, String id) {
        this.activity = activity;
        this.id = id;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(9),dp(4),dp(9),dp(5));
        view.setBackground(surface("#FFFFFF", "#DCE8F5"));
        routeScroll=new android.widget.ScrollView(activity) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int cap=routeHeightLimit;
                if (MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED) cap=Math.min(cap,MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
            }
        };
        routeScroll.setFillViewport(false);
        routeScroll.setVisibility(View.GONE);
        routeScroll.addView(view,new android.widget.ScrollView.LayoutParams(-1,-2));
        LinearLayout heading=new LinearLayout(activity);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        routeTitle=new TextView(activity);
        TextView title=routeTitle;
        title.setText("Atur rute");
        title.setTextColor(0xFF102449);
        title.setTextSize(15);
        title.setMaxLines(3);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        stop = button("+ Singgah", () -> openPicker(STOP));
        clearStop = button("Hapus", this::removeStop);
        style(stop,"#F5F9FF","#075BC8");
        style(clearStop,"#F3F6FA","#41556C");
        briefButton=button("Tiva AI  ›",()->TivaSimpelBrief.show(activity,tivaBrief));
        style(briefButton,"#EFE8FF","#502B93");
        briefButton.setContentDescription("Buka percakapan Tiva AI");
        briefButton.setPadding(dp(10),0,dp(10),0);
        briefButton.setVisibility(View.GONE);
        heading.addView(briefButton,new LinearLayout.LayoutParams(-2,dp(48)));
        confirmDelivery=button("Konfirmasi diterima",()->customerAction("confirm_received"));
        approveStop=button("Setujui tarif singgah",()->customerAction("approve_price"));
        rejectStop=button("Tolak tarif singgah",()->customerAction("reject_price"));
        cancelDraft=button("Batalkan permintaan",()->send("cancel_draft",null));
        for (Button action:new Button[]{confirmDelivery,approveStop,rejectStop,cancelDraft}) {
            action.setVisibility(View.GONE);
            view.addView(action,new LinearLayout.LayoutParams(-1,dp(44)));
        }
        mapToggle=button("Sembunyikan peta", () -> {
            mapExpanded=!mapExpanded;
            if (route!=null) render(route);
            if (mapExpanded) refresh();
        });
        style(mapToggle,"#EAF1FF","#075BC8");
        mapToggle.setVisibility(View.GONE);
        heading.addView(mapToggle,new LinearLayout.LayoutParams(-2,dp(44)));
        view.addView(heading,0,new LinearLayout.LayoutParams(-1,-2));
        waitingView=new SmartWaitingTextView(activity);
        waitingView.setTripPresentation();

        pickupAddress = addressLine("Jemput","●",0xFF08785B,"pickup");
        view.addView(stop,new LinearLayout.LayoutParams(-2,dp(44)));
        stopAddress = addressLine("Singgah","◆",0xFF925200,"stop");
        deliveryAddress = addressLine("Antar","⚑",0xFFB52432,"delivery");
        View divider=new View(activity);
        divider.setBackgroundColor(0xFFDCE8F5);
        view.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        fareRow=new LinearLayout(activity);
        fareRow.setOrientation(LinearLayout.VERTICAL);
        fareRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.addView(fareRow,new LinearLayout.LayoutParams(-1,-2));
        view.addView(waitingView,new LinearLayout.LayoutParams(-1,-2));
        summary = new TextView(activity);
        summary.setTextColor(0xFF143052);
        summary.setTextSize(13);
        summary.setMaxLines(2);
        summary.setPadding(dp(4),dp(8),dp(4),dp(8));
        fareRow.addView(summary,new LinearLayout.LayoutParams(-1,-2));
        summary.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        purchaseHint=new TextView(activity);
        purchaseHint.setTextColor(0xFF526E90);
        purchaseHint.setTextSize(12);
        purchaseHint.setText("Harga makanan atau belanja dibahas di chat.");
        purchaseHint.setVisibility(View.GONE);
        view.addView(purchaseHint);
        view.addView(clearStop,new LinearLayout.LayoutParams(-2,dp(44)));
        trackOrder=button("Buka peta",this::openOrderedTrip);
        trackOrder.setVisibility(View.GONE);
        style(trackOrder,"#EAF1FF","#075BC8");
        fareRow.addView(trackOrder,new LinearLayout.LayoutParams(-2,dp(44)));
        quoteRetry = button("Hitung Ulang Ongkir", () -> {
            quotedRevision = -1;
            calculateIfReady();
        });
        view.addView(quoteRetry,new LinearLayout.LayoutParams(-1,dp(44)));
        order = button("Pesan sekarang",this::submitOrder);
        style(order,"#075DCF","#FFFFFF");
        fareRow.addView(order,new LinearLayout.LayoutParams(-1,-2));
        order.setMinHeight(dp(48));
        order.setMinimumHeight(dp(48));
        fareRow.setOrientation(LinearLayout.VERTICAL);
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if (r-l!=or-ol) layoutFare(r-l);
        });
    }

    static String contactId(String orderId,String roomId) {
        if (orderId != null && orderId.matches("PUD[0-9A-F]{24}")) return orderId;
        if (roomId != null && roomId.matches("ROOM-PUD[0-9A-F]{24}")) return roomId.substring(5);
        return "";
    }

    View view() { return routeScroll; }

    LinearLayout createLayout() {
        LinearLayout column=new LinearLayout(activity) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int height=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED
                        ? dp(activity.getResources().getConfiguration().screenHeightDp)
                        : MeasureSpec.getSize(heightSpec);
                int contentHeight=Math.max(0,height-getPaddingTop()-getPaddingBottom());
                int limit=Math.round(contentHeight*(routeMap == null ? .42f : .56f));
                if (limit!=routeHeightLimit) {
                    routeHeightLimit=limit;
                    // Recheck the scroll view even when LinearLayout reuses its child MeasureSpecs.
                    routeScroll.forceLayout();
                }
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    void resume() { active = true; resumeRouteMap(); refresh(); }
    void pause() {
        active = false;
        main.removeCallbacks(poll);
        if (routeMapResumed) {
            routeMap.onPauseMap();
            routeMapResumed=false;
        }
    }
    void close() { pause(); removeRouteMap(); network.destroy(); }

    private void resumeRouteMap() {
        if (active && routeMap!=null && !routeMapResumed) {
            routeMap.onResumeMap();
            routeMapResumed=true;
        }
    }

    private void removeRouteMap() {
        if (routeMap==null) return;
        if (routeMapResumed) routeMap.onPauseMap();
        routeMapResumed=false;
        routeMap.onStopMap();
        routeMap.onDestroyMap();
        view.removeView(routeMap);
        routeMap=null;
        mapPoints=null;
        liveRoutePoints=null;
        liveRouteTarget="";
        mapHasDriver=false;
        mapOrder="";
        routeScroll.requestLayout();
    }

    private void showRouteMap(JSONObject update) {
        if (update.optJSONObject("pickup")==null || update.optJSONObject("delivery")==null) return;
        JSONObject from=update.optJSONObject("pickup"),to=update.optJSONObject("delivery");
        String key=update.optString("order_id",update.optString("state"))+":"+from.optString("lat")
                +":"+from.optString("lng")+":"+to.optString("lat")+":"+to.optString("lng")
                +":"+String.valueOf(update.optJSONObject("ecosystem"));
        if (!key.equals(mapOrder)) {
            mapOrder=key;
            mapPoints=null;
            liveRoutePoints=null;
            liveRouteTarget="";
            if (routeMap!=null) routeMap.clearRoute();
            fitMap=true;
        }
        if (update.has("map_points")) {
            if (mapPoints==null) fitMap=true;
            mapPoints=update.optJSONArray("map_points");
        }
        if (routeMap!=null) {
            paintRouteMap();
            return;
        }

        TransivaGoogleMapView map=new TransivaGoogleMapView(activity,TransivaGoogleMapView.Mode.TRIP);
        routeMap=map;
        int screenHeight=activity.getResources().getConfiguration().screenHeightDp;
        int mapHeight=Math.max(112, Math.min(172, Math.round(screenHeight*.24f)));
        view.addView(map,1,new LinearLayout.LayoutParams(-1,dp(mapHeight)));
        routeScroll.requestLayout();
        map.useNumberedRideStops();
        map.initialize(null,new TransivaGoogleMapView.Listener() {
            @Override public void onReady(double lat,double lng) {
                if (routeMap==map) paintRouteMap();
            }
            @Override public void onCenterChanged(double lat,double lng) { }
        });
        map.onStartMap();
        resumeRouteMap();
    }

    private void paintRouteMap() {
        TransivaGoogleMapView map=routeMap;
        if (map==null || route==null || !map.isReady()) return;
        JSONObject from=route.optJSONObject("pickup"), to=route.optJSONObject("delivery");
        if (from==null || to==null) return;
        map.setPickup(from.optDouble("lat"),from.optDouble("lng"),from.optString("address"));
        map.setDelivery(to.optDouble("lat"),to.optDouble("lng"),to.optString("address"));
        JSONObject ecosystem=route.optJSONObject("ecosystem");
        map.setWaypoints(ecosystem==null?null:ecosystem.optJSONArray("waypoints"));
        JSONObject driver=route.optJSONObject("driver_position");
        if (driver!=null) {
            map.setTripDriver(driver.optDouble("lat"),driver.optDouble("lng"),driver.optDouble("bearing",0),
                    "car".equalsIgnoreCase(driver.optString("vehicle")),"Posisi driver","Lokasi terakhir");
            if (!mapHasDriver) fitMap=true;
        } else if (mapHasDriver) {
            map.clearTripDriver();
        }
        mapHasDriver=driver!=null;
        String status=route.optString("order_status");
        JSONObject target=CustomerOrderState.targetsDelivery(status)?to:from;
        if (CustomerOrderState.targetsDelivery(status) && ecosystem!=null) {
            org.json.JSONArray stops=ecosystem.optJSONArray("waypoints");
            for (int index=0;stops!=null && index<stops.length();index++) {
                JSONObject stop=stops.optJSONObject(index);
                if (stop==null || !"pending".equals(stop.optString("status"))) continue;
                try {
                    target=new JSONObject().put("lat",stop.optDouble("latitude")).put("lng",stop.optDouble("longitude"));
                    break;
                } catch (org.json.JSONException invalidStop) { continue; }
            }
        }
        if (driver!=null) requestLiveRoute(driver,target);
        else if (liveRoutePoints!=null) {
            liveRouteTarget="";
            liveRoutePoints=null;
            map.clearRoute();
        }
        org.json.JSONArray points=liveRoutePoints!=null?liveRoutePoints:mapPoints;
        if (points!=null && points.length()>1) map.drawOsrmRoute(points,status);
        if (fitMap) {
            fitMap=false;
            map.post(()->{ if(routeMap==map) map.fitAll(); });
        }
    }

    private void requestLiveRoute(JSONObject driver, JSONObject target) {
        final double fromLat=driver.optDouble("lat"), fromLng=driver.optDouble("lng");
        final double toLat=target.optDouble("lat"), toLng=target.optDouble("lng");
        if (!Double.isFinite(fromLat) || !Double.isFinite(fromLng)
                || !Double.isFinite(toLat) || !Double.isFinite(toLng)) return;
        final String targetKey=mapOrder+":"+toLat+":"+toLng;
        boolean changed=!targetKey.equals(liveRouteTarget);
        if (changed) {
            liveRoutePoints=null;
            liveRouteTarget=targetKey;
        }
        long now=android.os.SystemClock.elapsedRealtime();
        if (liveRouteRunning || (!changed && now-lastLiveRouteRequest<25000)) return;
        liveRouteRunning=true;
        lastLiveRouteRequest=now;
        if (!submit(()->{
            try {
                StableRouteEngine.Result result=StableRouteEngine.fetch(fromLat,fromLng,toLat,toLng);
                post(()->{
                    liveRouteRunning=false;
                    if (!targetKey.equals(liveRouteTarget) || routeMap==null || route==null
                            || route.optJSONObject("driver_position")==null) return;
                    if (result.latLngPoints.length()>1) {
                        liveRoutePoints=result.latLngPoints;
                        routeMap.drawOsrmRoute(liveRoutePoints,route.optString("order_status"));
                    }
                });
            } catch (Exception error) {
                post(()->liveRouteRunning=false);
            }
        },()->liveRouteRunning=false)) liveRouteRunning=false;
    }

    private void openOrderedTrip() {
        if (route==null || route.optString("order_id").isEmpty()) return;
        String source=route.optString("source","orders");
        Class<?> screen="food".equals(kind())?CustomerFoodOrderStatusActivity.class:CustomerTripActivity.class;
        activity.startActivity(new Intent(activity,screen).putExtra("order_id",route.optString("order_id"))
                .putExtra("status",route.optString("order_status"))
                .putExtra("order_source",source).putExtra("source",source).putExtra("order_type",
                        "food".equals(kind())?"TransFood":"shop".equals(kind())?"TransShop":
                                "send".equals(kind())?"TransSend":"Trans Simpel"));
    }

    void restore(Bundle state) {
        if (state == null) return;
        pickerRevision = state.getInt("simpel_picker_revision",-1);
        String savedPoint = state.getString("simpel_picker_previous","");
        try { previous = savedPoint.isEmpty() ? null : new JSONObject(savedPoint); }
        catch (org.json.JSONException ignored) { previous = null; }
    }

    void save(Bundle out) {
        out.putInt("simpel_picker_revision",pickerRevision);
        if (previous != null) out.putString("simpel_picker_previous",previous.toString());
    }

    private Button button(String title,Runnable click) {
        Button b = new Button(activity);
        b.setAllCaps(false);
        b.setText(title);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(5),0,dp(5),0);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        style(b,"#EAF1FF","#17457C");
        b.setOnClickListener(v -> click.run());
        return b;
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private GradientDrawable surface(String fill,String stroke) {
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.parseColor(fill));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1),Color.parseColor(stroke));
        return bg;
    }

    private void style(Button button,String fill,String text) {
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{0xFF61748B,Color.parseColor(text)}));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x332379DB),
                surface(fill,fill),null));
    }

    private static String field(int which) {
        return which==PICKUP?"pickup":which==STOP?"stop":"delivery";
    }

    private void removeStop() {
        if ((busy && !readingRoute) || route==null || !"accepted".equals(route.optString("state"))) return;
        JSONObject selected=route.optJSONObject("stop");
        if (selected==null) return;
        try {
            send("set",new JSONObject().put("point","stop").put("revision",route.optInt("revision"))
                    .put("previous",selected).put("location",JSONObject.NULL));
        } catch (org.json.JSONException error) {
            notice("Singgah belum dapat dihapus.");
        }
    }

    private TextView addressLine(String title,String icon,int color,String key) {
        TextView line=new TextView(activity);
        line.setTextColor(color);
        line.setTextSize(13);
        line.setMaxLines(2);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        line.setMinHeight(dp(44));
        line.setBackground(surface("#F8FAFD","#E5ECF5"));
        line.setPadding(dp(6),dp(4),dp(4),dp(4));
        line.setOnClickListener(v->{
            new TransivaAlertDialogBuilder(activity)
                .setTitle(title).setMessage(route==null?"Belum diatur":address(key))
                .setPositiveButton("Tutup",null).show();
        });
        line.setTag(icon+"  "+title);
        view.addView(line,new LinearLayout.LayoutParams(-1,-2));
        return line;
    }

    private void showAddress(TextView line,String key) {
        String title=(String)line.getTag();
        String place=address(key);
        android.text.SpannableString display=new android.text.SpannableString(title+"\n    "+place+"  ›");
        display.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,title.length(),0);
        display.setSpan(new android.text.style.ForegroundColorSpan(0xFF213953),title.length()+1,display.length(),0);
        line.setText(display);
        line.setContentDescription(title+". "+place+". Ketuk untuk melihat alamat lengkap.");
    }

    private void layoutFare(int width) {
        boolean stacked=width<dp(330) || activity.getResources().getConfiguration().fontScale>1.15f
                || "food".equals(kind()) || "send".equals(kind());
        int orientation=stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL;
        if (fareRow.getOrientation()==orientation) return;
        fareRow.setOrientation(orientation);
        summary.setLayoutParams(stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        order.setLayoutParams(new LinearLayout.LayoutParams(stacked?-1:-2,-2));
    }

    private void refresh() {
        if (!active || busy || !available) return;
        JSONObject request=null;
        if (route==null || "waiting_driver".equals(route.optString("state")) || "ordered".equals(route.optString("state"))) {
            try { request=new JSONObject().put("map_live",mapExpanded).put("map",mapExpanded && (mapPoints==null || mapPoints.length()<2)); }
            catch (org.json.JSONException ignored) { }
        }
        send("get",request);
    }

    private void render(JSONObject update) {
        int revision=update.optInt("revision");
        if (route!=null && revision<route.optInt("revision")) return;
        if (route!=null && !route.isNull("total") && update.isNull("total")) quotedRevision=-1;
        route=update;
        String state=route.optString("state");
        boolean editable="accepted".equals(state) && !directCheckout();
        boolean ordered="ordered".equals(state);
        boolean waiting="waiting_driver".equals(state);
        routeScroll.setVisibility(editable || ordered || waiting || "accepted".equals(state) ? View.VISIBLE : View.GONE);
        String phase=route.optString("order_status");
        String caption="arrived_pickup".equals(phase)?"Tiba di jemput":
                "on_delivery".equals(phase)||"picked_up".equals(phase)?"Menuju antar":
                "arrived_delivery".equals(phase)?"Tiba di tujuan":
                "finished".equals(phase)||"completed".equals(phase)?"Selesai":"Menuju jemput";
        waitingView.bind(ordered?route.optJSONObject("smart_waiting"):null,phase);
        routeTitle.setText(ordered?"Rute perjalanan\n"+caption:waiting?"Pesanan dalam antrean":directCheckout()?"Menyiapkan pesanan":"Ringkasan rute");
        briefButton.setVisibility(editable && tivaBrief!=null ? View.VISIBLE : View.GONE);
        trackOrder.setVisibility(View.GONE);
        confirmDelivery.setVisibility(ordered && "arrived_delivery".equals(phase)
                && route.optInt("customer_received")==0?View.VISIBLE:View.GONE);
        JSONObject proposal=route.optJSONObject("location_proposal");
        approveStop.setVisibility(proposal==null?View.GONE:View.VISIBLE);
        rejectStop.setVisibility(proposal==null?View.GONE:View.VISIBLE);
        approveStop.setText("Setujui total Rp "+NumberFormat.getIntegerInstance(new Locale("id","ID")).format(route.optLong("requested_price")));
        cancelDraft.setVisibility(checkoutBlocked && "accepted".equals(state)?View.VISIBLE:View.GONE);
        boolean canShowMap=(waiting || ordered) && route.optJSONObject("pickup")!=null
                && route.optJSONObject("delivery")!=null;
        mapToggle.setVisibility(canShowMap?View.VISIBLE:View.GONE);
        mapToggle.setText(mapExpanded?"Tutup peta":"Tampil peta");
        if (canShowMap && mapExpanded) showRouteMap(route);
        else removeRouteMap();
        boolean food="food".equals(kind());
        boolean send="send".equals(kind());
        stop.setVisibility(View.GONE);
        stop.setEnabled(editable && !busy);
        boolean hasStop=route.optJSONObject("stop")!=null;
        stop.setText(hasStop?"Singgah":"+ Singgah");
        stop.setContentDescription(hasStop?"Ubah titik singgah":"Tambah titik singgah opsional");
        clearStop.setVisibility(View.GONE);
        clearStop.setEnabled(!busy);
        clearStop.setContentDescription("Hapus titik singgah");
        long amount=route.optLong("total",0);
        pickupAddress.setVisibility(directCheckout()?View.GONE:View.VISIBLE);
        deliveryAddress.setVisibility(directCheckout()?View.GONE:View.VISIBLE);
        showAddress(pickupAddress,"pickup");
        showAddress(stopAddress,"stop");
        stopAddress.setVisibility(hasStop && !directCheckout()?View.VISIBLE:View.GONE);
        showAddress(deliveryAddress,"delivery");
        summary.setText(amount>0?(food?"Total makanan + ongkir":ordered?"Total":"Estimasi")+"  Rp "
                +NumberFormat.getIntegerInstance(new Locale("id","ID")).format(amount)+" · Tunai":"");
        boolean assistedPurchase=tivaBrief!=null
                && "shop".equals(kind());
        purchaseHint.setVisibility(assistedPurchase?View.VISIBLE:View.GONE);
        if (food && amount>0 && foodQuote!=null && foodQuoteRevision==revision
                && foodQuote.optLong("total")==amount) {
            NumberFormat money=NumberFormat.getIntegerInstance(new Locale("id","ID"));
            summary.setText("Subtotal Rp "+money.format(foodQuote.optLong("food_total"))
                    +"  ·  Ongkir Rp "+money.format(foodQuote.optLong("delivery_fee"))
                    +"\nTotal Rp "+money.format(amount)+" · Tunai");
            summary.setMaxLines(3);
            summary.setEllipsize(TextUtils.TruncateAt.END);
        } else summary.setMaxLines(2);
        if (amount>0 && assistedPurchase) {
            summary.setText("Ongkir  Rp "+NumberFormat.getIntegerInstance(new Locale("id","ID")).format(amount)+" · Tunai");
        }
        if (waiting) summary.setText("Menunggu driver selesai"+(amount>0?" · Rp "+
                NumberFormat.getIntegerInstance(new Locale("id","ID")).format(amount):""));
        if (directCheckout() && "accepted".equals(state)) summary.setText(checkoutBlocked
                ?"Pesanan belum dapat diproses. Batalkan dan periksa tarif atau ketersediaan."
                :"Driver menerima. Menyiapkan pesanan…");
        summary.setVisibility(waiting || amount>0 || directCheckout() && "accepted".equals(state)?View.VISIBLE:View.GONE);
        boolean both=route.optJSONObject("pickup")!=null && route.optJSONObject("delivery")!=null;
        quoteRetry.setVisibility(editable && both && amount==0 && quotedRevision==revision ? View.VISIBLE : View.GONE);
        quoteRetry.setEnabled(!busy);
        order.setText(send?"Lanjut ke TransSend":food?"Pesan TransFood":"Pesan sekarang");
        order.setVisibility(editable && (amount>0 || (send && both)) ? View.VISIBLE : View.GONE);
        order.setEnabled(!busy);
        layoutFare(view.getWidth());
    }

    private String address(String key) {
        JSONObject point=route.optJSONObject(key);
        return point==null?"Belum diatur":point.optString("address").replace('\n',' ');
    }

    private String kind() {
        return tivaBrief==null?"":tivaBrief.optString("kind");
    }

    private boolean directCheckout() {
        return tivaBrief!=null && tivaBrief.optLong("authorized_total")>0;
    }

    private void customerAction(String action) {
        if (busy || route==null) return;
        try {
            JSONObject payload=new JSONObject().put("customer_action",action)
                    .put("source",route.optString("source")).put("order_id",route.optString("order_id"));
            JSONObject stop=route.optJSONObject("location_proposal");
            if (stop!=null && !"confirm_received".equals(action)) payload.put("location_request_id",stop.optLong("id"))
                    .put("expected_price",route.optLong("requested_price"));
            send("customer_order_action",payload);
        } catch (org.json.JSONException failure) { notice("Tindakan belum dapat dikirim."); }
    }

    private void calculateIfReady() {
        if (directCheckout()) {
            if (active && !busy && !checkoutBlocked && route!=null && "accepted".equals(route.optString("state"))
                    && ("food".equals(kind()) || "send".equals(kind()))) send("auto_order",null);
            return;
        }

        if ("send".equals(kind())) return;

        if (!active || busy || route==null || !"accepted".equals(route.optString("state"))
                || route.optJSONObject("pickup")==null || route.optJSONObject("delivery")==null
                || (!route.isNull("total") && !"food".equals(kind()))) return;
        int revision=route.optInt("revision");
        if (revision==quotedRevision) return;
        quotedRevision=revision;
        try {send("quote",new JSONObject().put("revision",revision));}
        catch (org.json.JSONException ignored) {quotedRevision=-1;}
    }

    private void openPicker(int which) {
        if ((busy && !readingRoute) || route==null || !"accepted".equals(route.optString("state"))) return;
        String field=field(which);
        pickerRevision=route.optInt("revision");
        previous=route.optJSONObject(field);
        JSONObject seed=previous!=null?previous:route.optJSONObject(which==PICKUP?"delivery":"pickup");
        Intent intent=new Intent(activity,TransSendContactPointActivity.class)
                .putExtra("point_kind",field)
                .putExtra("title",which==PICKUP?"Atur Titik Jemput":which==STOP?"Atur Titik Singgah":"Atur Titik Antar");
        if (seed!=null) intent.putExtra("lat",seed.optDouble("lat")).putExtra("lng",seed.optDouble("lng"));
        intent.putExtra("route_contact",id).putExtra("route_snapshot",route.toString());
        for (String marker:new String[]{"pickup","delivery","stop"}) {
            JSONObject point=route.optJSONObject(marker);
            if (point!=null) intent.putExtra("saved_"+marker,point.toString());
        }

        activity.startActivityForResult(intent,which);
    }

    boolean result(int request,int result,Intent data) {
        if (request!=PICKUP && request!=DELIVERY && request!=STOP) return false;
        if (result!=Activity.RESULT_OK || data==null || pickerRevision<0) return true;
        try {
            JSONObject payload=new JSONObject()
                    .put("point",field(request))
                    .put("revision",data.getIntExtra("route_revision",pickerRevision))
                    .put("previous",previousPoint(data))
                    .put("location",new JSONObject().put("lat",data.getDoubleExtra("lat",0))
                            .put("lng",data.getDoubleExtra("lng",0)).put("address",data.getStringExtra("address")));
            pickerRevision=-1;
            if (busy) queuedPoint=payload;
            else send("set",payload);
        } catch (Exception error) { notice("Titik belum dapat disimpan."); }
        return true;
    }

    private Object previousPoint(Intent data) throws org.json.JSONException {
        if (!data.hasExtra("route_previous")) return previous==null?JSONObject.NULL:previous;
        String point=data.getStringExtra("route_previous");
        return point==null || point.isEmpty()?JSONObject.NULL:new JSONObject(point);
    }

    private JSONObject queuedPoint;

    private void submitOrder() {
        if (busy || route==null) return;
        if ("send".equals(kind())) {
            JSONObject from=route.optJSONObject("pickup"), to=route.optJSONObject("delivery");
            if (from==null || to==null) return;
            activity.startActivity(new Intent(activity,TransPickupActivity.class)
                    .putExtra(TransSendContactClient.EXTRA_CONTACT,id)
                    .putExtra("contact_pickup",pointIntent(from)).putExtra("contact_delivery",pointIntent(to))
                    .putExtra("tiva_item",tivaBrief.optString("detail")));
            return;
        }
        if (route.optLong("total")<=0) return;
        try {
            send("order",new JSONObject().put("revision",route.optInt("revision"))
                    .put("total",route.optLong("total")).put("queue_capable",true));
        } catch (org.json.JSONException error) {
            notice("Pesanan belum dapat dibuat.");
        }
    }

    private void send(String action,JSONObject data) {
        busy=true;
        readingRoute="get".equals(action);
        main.removeCallbacks(poll);
        if (!readingRoute && route!=null) render(route);
        if ("quote".equals(action)) {
            summary.setVisibility(View.VISIBLE);
            summary.setText("Menghitung ongkir…");
        }
        if (!submit(() -> {
            try {
                JSONObject body=data==null?new JSONObject():data;
                body.put("action",action).put("contact_id",id);
                JSONObject reply=request(body);
                if (!reply.optBoolean("success")) throw new IllegalStateException(reply.optString("message","Permintaan belum berhasil."));
                post(() -> complete(action,reply,null));
            } catch (Exception error) {
                post(() -> complete(action,null,error.getMessage()));
            }
        }, () -> complete(action,null,"Koneksi sibuk. Coba lagi."))) {
            complete(action,null,"Koneksi sibuk. Coba lagi.");
        }
    }

    private void complete(String action,JSONObject reply,String failure) {
        boolean wasReading=readingRoute;
        busy=false;
        readingRoute=false;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (reply!=null && !reply.optBoolean("available",true)) {
            available=false;
            routeScroll.setVisibility(View.GONE);
            return;
        }
        boolean briefChanged=false;
        if (reply!=null && reply.has("tiva_brief")) {
            JSONObject latest=reply.optJSONObject("tiva_brief");
            briefChanged=!sameSelection(latest,tivaBrief);
            if (briefChanged) {
                tivaBrief=latest;
                briefButton.setVisibility(tivaBrief!=null?View.VISIBLE:View.GONE);
            }
        }
        JSONObject updated=reply==null?null:reply.optJSONObject("route");
        if (updated!=null && "quote".equals(action) && reply.optJSONObject("quote")!=null) {
            foodQuote=reply.optJSONObject("quote");
            foodQuoteRevision=updated.optInt("revision");
        }
        if (updated!=null && (!wasReading || briefChanged || !sameSelection(updated,route))) render(updated);
        else if (!wasReading && route!=null) render(route);
        else if (updated!=null && routeMap!=null) paintRouteMap();
        if ("auto_order".equals(action)) {
            if (reply==null) {
                checkoutBlocked=true;
                if (route!=null) render(route);
            } else {
                if (active) refresh();
                return;
            }
        }
        if (("customer_order_action".equals(action) || "cancel_draft".equals(action)) && reply!=null) {
            refresh();
            return;
        }
        if (failure!=null && !"get".equals(action)) notice(failure);
        if (reply!=null && "order".equals(action) && "food".equals(kind())) {
            order.setVisibility(View.GONE);
            activity.startActivity(new Intent(activity, CustomerFoodOrderStatusActivity.class)
                    .putExtra("order_id",reply.optString("order_id")).putExtra("status",reply.optString("status")));
            return;
        }
        if (reply!=null && "order".equals(action) && route!=null) {
            queuedPoint=null;
            boolean waiting="waiting_driver".equals(reply.optString("status"));
            try {
                route.put("state",waiting?"waiting_driver":"ordered")
                        .put("order_id",reply.optString("order_id"))
                        .put("order_status",reply.optString("status","driver_accepted"))
                        .put("source",reply.optString("source","orders"));
            } catch (org.json.JSONException ignored) { }
            render(route);
            if (!waiting && !route.optString("order_id").isEmpty()) {
                activity.startActivity(new Intent(activity,CustomerTripActivity.class)
                        .putExtra("order_id",route.optString("order_id"))
                        .putExtra("source",route.optString("source"))
                        .putExtra("order_source",route.optString("source"))
                        .putExtra("status",route.optString("order_status")));
            } else if (active) {
                main.removeCallbacks(poll);
                main.postDelayed(poll,3000);
            }
            return;
        }
        if (queuedPoint!=null) {
            JSONObject point=queuedPoint;
            queuedPoint=null;
            send("set",point);
            return;
        }
        if (active) {
            calculateIfReady();
            if (!busy && (route==null || "accepted".equals(route.optString("state"))
                    || "waiting_driver".equals(route.optString("state"))
                    || "ordered".equals(route.optString("state"))
                        && !CustomerOrderState.isEnded(route.optString("order_status")))) {
                main.postDelayed(poll,route!=null && "ordered".equals(route.optString("state")) ? 5000 : 3000);
            }
        }
    }

    private static boolean sameSelection(JSONObject left,JSONObject right) {
        return left==right || (left!=null && right!=null && left.toString().equals(right.toString()));
    }

    private void notice(String message) {
        if (!activity.isFinishing()) Toast.makeText(activity,message,Toast.LENGTH_LONG).show();
    }

    private Intent pointIntent(JSONObject point) {
        return new Intent().putExtra("lat",point.optDouble("lat")).putExtra("lng",point.optDouble("lng"))
                .putExtra("address",point.optString("address"));
    }

    private JSONObject request(JSONObject data) throws Exception {
        String action=data.optString("action");
        if ("customer_order_action".equals(action)) {
            data.put("action",data.optString("customer_action"));
            return TransivaHttpRepository.postJson(activity,ApiConfig.server("customer_order_action.php"),data,12000);
        }
        if ("cancel_draft".equals(action)) {
            return TransSendContactClient.contact(activity,TransSendContactClient.action("cancel",id));
        }
        if ("auto_order".equals(action)) {
            long total=tivaBrief.optLong("authorized_total");
            int revision=route.optInt("revision");
            String key="tiva-auto-"+id+"-"+revision+"-"+total;
            if ("food".equals(kind())) {
                JSONObject payload=new JSONObject().put("tiva_contact_id",id).put("revision",revision)
                        .put("payment_method","cash").put("quote_only",true);
                JSONObject quote=TransivaHttpRepository.postJson(activity,ApiConfig.server("create_food_order.php"),payload,15000);
                JSONObject cost=quote.optJSONObject("quote");
                if (cost==null || cost.optLong("total")!=total) throw new IllegalStateException(
                        quote.optBoolean("success")?"Harga makanan berubah. Periksa ulang pesanan.":quote.optString("message"));
                payload.put("quote_only",false).put("total",total).put("id",key);
                return TransivaHttpRepository.postJsonIdempotent(activity,ApiConfig.server("create_food_order.php"),payload,18000,key);
            }
            JSONObject from=route.getJSONObject("pickup"),to=route.getJSONObject("delivery");
            JSONObject payload=new JSONObject().put("courier_contact_id",id).put("id",key)
                    .put("pickup_lat",from.optDouble("lat")).put("pickup_lng",from.optDouble("lng"))
                    .put("pickup_address",from.optString("address"))
                    .put("delivery_lat",to.optDouble("lat")).put("delivery_lng",to.optDouble("lng"))
                    .put("delivery_address",to.optString("address"))
                    .put("item_name",tivaBrief.optString("detail")).put("package_size","small").put("payment_method","cash")
                    .put("receiver_name",tivaBrief.optString("receiver_name")).put("receiver_phone",tivaBrief.optString("receiver_phone"))
                    .put("approved_price",total);
            return TransivaHttpRepository.postJsonIdempotent(activity,ApiConfig.server("create_pickup_order.php"),payload,18000,key);
        }
        if ("food".equals(kind()) && ("order".equals(action) || "quote".equals(action))) {
            boolean quote="quote".equals(action);
            JSONObject payload=new JSONObject().put("tiva_contact_id",id).put("quote_only",quote)
                    .put("revision",data.optInt("revision")).put("payment_method","cash");
            if (quote) return TransivaHttpRepository.postJson(activity,ApiConfig.server("create_food_order.php"),payload,14000);
            long total=data.optLong("total");
            String key="tiva-food-"+id+"-"+data.optInt("revision")+"-"+total;
            payload.put("total",total).put("id",key);
            return TransivaHttpRepository.postJsonIdempotent(activity,ApiConfig.server("create_food_order.php"),payload,15000,key);
        }
        return TransivaHttpRepository.postJson(activity,ApiConfig.server("trans_simpel.php"),data,12000);
    }
    private boolean submit(Runnable work,Runnable failure) {
        network.execute(work,()->post(failure));
        return true;
    }
    private void post(Runnable work) { network.post(main,work); }
}
