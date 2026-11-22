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
    private final LinearLayout fareRow;
    private final Button pickup;
    private final Button delivery;
    private final Button stop;
    private final Button clearStop;
    private final Button quoteRetry;
    private final Button briefButton;
    private final TextView purchaseHint;
    private JSONObject tivaBrief;
    private final Button order;
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

    TransSimpelRoutePanel(Activity activity, String id) {
        this.activity = activity;
        this.id = id;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(9),dp(4),dp(9),dp(5));
        view.setBackground(surface("#FFFFFF", "#DCE8F5"));
        routeScroll=new android.widget.ScrollView(activity) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                android.view.ViewParent parent=getParent();
                int available=parent instanceof View && ((View)parent).getHeight()>0
                        ? ((View)parent).getHeight()
                        : dp(activity.getResources().getConfiguration().screenHeightDp);
                int cap=Math.max(dp(48),Math.round(available*.42f));
                if (MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED) cap=Math.min(cap,MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
            }
        };
        routeScroll.setFillViewport(false);
        routeScroll.setVisibility(View.GONE);
        routeScroll.addView(view,new android.widget.ScrollView.LayoutParams(-1,-2));
        LinearLayout heading=new LinearLayout(activity);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title=new TextView(activity);
        title.setText("Rute Trans Simpel");
        title.setTextColor(0xFF102449);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout actions = new LinearLayout(activity);
        pickup = button("Jemput", () -> openPicker(PICKUP));
        delivery = button("Antar", () -> openPicker(DELIVERY));
        stop = button("+ Singgah", () -> openPicker(STOP));
        clearStop = button("Hapus", this::removeStop);
        style(pickup,"#E7F7F0","#096749");
        style(delivery,"#EAF1FF","#164C9C");
        style(stop,"#FFF4E1","#774508");
        style(clearStop,"#F3F6FA","#41556C");
        for (Button point : new Button[]{pickup, stop, delivery}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,dp(44),1);
            params.setMargins(dp(2),0,dp(2),0);
            actions.addView(point,params);
        }
        briefButton=button("Tiva AI  ›",()->TivaSimpelBrief.show(activity,tivaBrief));
        style(briefButton,"#EFE8FF","#502B93");
        briefButton.setContentDescription("Buka percakapan Tiva AI");
        briefButton.setPadding(dp(10),0,dp(10),0);
        briefButton.setVisibility(View.GONE);
        heading.addView(briefButton,new LinearLayout.LayoutParams(-2,dp(48)));
        view.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        view.addView(actions);
        pickupAddress = addressLine("Jemput","●",0xFF08785B,"pickup");
        stopAddress = addressLine("Singgah","◆",0xFF925200,"stop");
        deliveryAddress = addressLine("Antar","⚑",0xFF075BC8,"delivery");
        View divider=new View(activity);
        divider.setBackgroundColor(0xFFDCE8F5);
        view.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        fareRow=new LinearLayout(activity);
        fareRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.addView(fareRow,new LinearLayout.LayoutParams(-1,-2));
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

    void fitOnResize(View parent) {
        parent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if (b-t!=ob-ot) routeScroll.requestLayout();
        });
    }

    void resume() { active = true; refresh(); }
    void pause() { active = false; main.removeCallbacks(poll); }
    void close() { pause(); network.destroy(); }

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
        line.setPadding(dp(6),dp(4),dp(4),dp(4));
        line.setOnClickListener(v->new TransivaAlertDialogBuilder(activity)
                .setTitle(title).setMessage(route==null?"Belum diatur":address(key))
                .setPositiveButton("Tutup",null).show());
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
        if (active && !busy && available) send("get",null);
    }

    private void render(JSONObject update) {
        int revision=update.optInt("revision");
        if (route!=null && revision<route.optInt("revision")) return;
        if (route!=null && !route.isNull("total") && update.isNull("total")) quotedRevision=-1;
        route=update;
        String state=route.optString("state");
        boolean editable="accepted".equals(state);
        boolean ordered="ordered".equals(state);
        routeScroll.setVisibility(editable || ordered ? View.VISIBLE : View.GONE);
        boolean food="food".equals(kind());
        boolean send="send".equals(kind());
        pickup.setEnabled(editable && !busy && !food);
        stop.setVisibility(kind().isEmpty() || "ride".equals(kind())?View.VISIBLE:View.GONE);
        delivery.setEnabled(editable && !busy);
        stop.setEnabled(editable && !busy);
        boolean hasStop=route.optJSONObject("stop")!=null;
        stop.setText(hasStop?"Singgah":"+ Singgah");
        stop.setContentDescription(hasStop?"Ubah titik singgah":"Tambah titik singgah opsional");
        clearStop.setVisibility(editable && hasStop?View.VISIBLE:View.GONE);
        clearStop.setEnabled(!busy);
        clearStop.setContentDescription("Hapus titik singgah");
        long amount=route.optLong("total",0);
        showAddress(pickupAddress,"pickup");
        showAddress(stopAddress,"stop");
        stopAddress.setVisibility(hasStop?View.VISIBLE:View.GONE);
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
        summary.setVisibility(amount>0?View.VISIBLE:View.GONE);
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

    private void calculateIfReady() {
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
            send("order",new JSONObject().put("revision",route.optInt("revision")).put("total",route.optLong("total")));
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
        if (failure!=null && !"get".equals(action)) notice(failure);
        if (reply!=null && "order".equals(action) && "food".equals(kind())) {
            order.setVisibility(View.GONE);
            activity.startActivity(new Intent(activity, CustomerFoodOrderStatusActivity.class)
                    .putExtra("order_id",reply.optString("order_id")).putExtra("status",reply.optString("status")));
            activity.finish();
            return;
        }
        if (reply!=null && "order".equals(action)) {
            order.setVisibility(View.GONE);
            new AlertDialog.Builder(activity).setTitle("Order berhasil")
                    .setMessage("ID order "+reply.optString("order_id"))
                    .setNegativeButton("Kembali ke Chat",null)
                    .setPositiveButton("Buka Pesan",(d,w)-> {
                        activity.startActivity(new Intent(activity, CustomerChatActivity.class));
                        activity.finish();
                    }).show();
        }
        if (queuedPoint!=null) {
            JSONObject point=queuedPoint;
            queuedPoint=null;
            send("set",point);
            return;
        }
        if (active) {
            calculateIfReady();
            if (!busy && (route==null || "accepted".equals(route.optString("state")))) main.postDelayed(poll,3000);
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
