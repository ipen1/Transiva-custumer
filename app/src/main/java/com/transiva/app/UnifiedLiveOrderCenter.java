package com.transiva.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Single source of truth for every active customer order.
 * Server remains authoritative; cache is only for instant UI/offline fallback.
 */
public final class UnifiedLiveOrderCenter {
    public static final String URL = "https://transiva.my.id/server/customer_get_active_orders.php";
    private static final String PREF = "transiva_live_order_center";
    private static final String KEY_JSON = "active_orders_json";
    private static final String KEY_AT = "active_orders_at";
    private static final long CACHE_MAX_AGE_MS = 10 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static volatile List<Order> memory = Collections.emptyList();
    private static String memoryOwner = "";
    private static long memoryAt;
    private static final java.util.concurrent.atomic.AtomicLong REQUEST = new java.util.concurrent.atomic.AtomicLong();
    private static volatile Context cacheContext;

    private UnifiedLiveOrderCenter() { }

    public interface Callback { void onResult(List<Order> orders, boolean fromCache, Throwable error); }
    public interface Listener { void onOrdersChanged(List<Order> orders); }

    public static final class Order {
        public final JSONObject raw;
        public final String id, status, source, type, serviceName, driverType, createdAt;
        public Order(JSONObject raw) {
            this.raw = raw == null ? new JSONObject() : raw;
            id = first(this.raw.optString("order_id"), this.raw.optString("id_order"), this.raw.optString("id"));
            status = CustomerOrderState.normalize(this.raw.optString("status"));
            source = first(this.raw.optString("source"), this.raw.optString("_transiva_table"), "orders");
            type = first(this.raw.optString("order_type"), this.raw.optString("service_type"));
            serviceName = first(this.raw.optString("service_name"), this.raw.optString("service_type"), readableService(type));
            driverType = normalizeDriverType(first(this.raw.optString("driver_type"), this.raw.optString("vehicle_type"), type));
            createdAt = this.raw.optString("created_at", "");
        }
        public boolean hasDriver() { return CustomerOrderState.hasDriver(status); }
        public String title() { return first(serviceName, readableService(type), "Pesanan Transiva"); }
        public String statusLabel() { return OrderStatusPresentation.label(status, type); }
    }

    public static void addListener(Listener l) { if (l != null) { LISTENERS.add(l); l.onOrdersChanged(snapshot()); } }
    public static void removeListener(Listener l) { if (l != null) LISTENERS.remove(l); }
    public static synchronized List<Order> snapshot() {
        Context c=cacheContext; if(c==null)c=TransivaCustomerApplication.appContext();
        if(c==null)return Collections.emptyList();
        CustomerSessionGuard identity=CustomerSessionGuard.capture(c);
        if(!CustomerReadSafetyRules.fresh(identity.account,memoryOwner,memoryAt,System.currentTimeMillis(),CACHE_MAX_AGE_MS))return Collections.emptyList();
        return new ArrayList<>(memory);
    }

    /** Fetches from server even when local active_order_id is missing. */
    public static void refresh(Context context, Callback cb) {
        final Context app=context.getApplicationContext();cacheContext=app;
        final CustomerSessionGuard identity=CustomerSessionGuard.capture(app);
        final long sequence=REQUEST.incrementAndGet();
        if(identity.account.isEmpty()) { MAIN.post(() -> {if(cb!=null&&CustomerSessionGuard.capture(app).account.isEmpty())cb.onResult(Collections.emptyList(),true,new IllegalStateException("Session unavailable"));}); return; }
        if(!TransivaNetworkMonitor.isOnline()) {
            long saved=app.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_AT,0);
            deliver(app,identity,sequence,cb,readCache(app,identity.account),saved,true,new IllegalStateException("offline"));return;
        }
        TransivaNetworkExecutor.execute(() -> {
            List<Order> result=new ArrayList<>();Throwable error=null;long saved=0;HttpURLConnection connection=null;
            try {
                if(!identity.matches(app))return;
                connection=CustomerApiClient.open(app,URL+"?_="+System.currentTimeMillis());
                connection.setRequestMethod("GET");connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
                int code=connection.getResponseCode();
                String body=BoundedResponseReader.read(code>=200&&code<300?connection.getInputStream():connection.getErrorStream(),2*1024*1024,12000);
                if(!identity.matches(app))return;
                CustomerApiClient.handleSessionResponse(app,code,body);
                JSONObject root=new JSONObject(body);JSONArray orders=root.optJSONArray("orders");
                if(code!=200||!root.optBoolean("success",false)||orders==null)throw new IllegalStateException("Active orders unavailable");
                for(int i=0;i<orders.length();i++) {
                    JSONObject raw=orders.optJSONObject(i);if(raw==null)continue;
                    String owner=raw.optString("user_id","");if(!owner.isEmpty()&&!owner.equals(identity.account))continue;
                    Order order=new Order(raw);if(!order.id.isEmpty()&&!CustomerOrderState.isEnded(order.status))result.add(order);
                }
                sort(result);saved=System.currentTimeMillis();
            } catch(Exception e) {error=e;saved=app.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_AT,0);result=readCache(app,identity.account);}
            finally {if(connection!=null)connection.disconnect();}
            deliver(app,identity,sequence,cb,result,saved,error!=null,error);
        }, () -> deliver(app,identity,sequence,cb,readCache(app,identity.account),app.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong(KEY_AT,0),true,new IllegalStateException("Network queue busy")));
    }

    public static Order primary(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return null;
        for (Order o : orders) if (o.hasDriver()) return o;
        return orders.get(0);
    }

    public static Intent routeIntent(Context context, Order order) {
        if (order == null) return new Intent(context, CustomerDashboardActivity.class);
        Class<?> cls = order.hasDriver() ? CustomerTripActivity.class : SearchDriverActivity.class;
        Intent i = new Intent(context, cls);
        i.putExtra("order_id", order.id); i.putExtra("active_order_id", order.id);
        i.putExtra("order_source", order.source); i.putExtra("driver_type", order.driverType);
        i.putExtra("active_driver_type", order.driverType); i.putExtra("order_json", order.raw.toString());
        return i;
    }

    public static void persistLegacyActiveOrder(Context context, Order o) {
        if (o == null) return;
        context.getSharedPreferences("transiva", Context.MODE_PRIVATE).edit()
                .putString("active_order_id", o.id).putString("active_order_status", o.status)
                .putString("active_order_source", o.source).putString("active_driver_type", o.driverType)
                .putString("active_order_type", o.type).putString("active_service_name", o.serviceName).apply();
    }

    public static void clear(Context context) {
        synchronized(UnifiedLiveOrderCenter.class) {
            REQUEST.incrementAndGet();memory=Collections.emptyList();memoryOwner="";memoryAt=0;
            context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply();
        }
        MAIN.post(UnifiedLiveOrderCenter::notifyListeners);
    }
    private static void notifyListeners(){for(Listener l:LISTENERS)try{l.onOrdersChanged(snapshot());}catch(Exception ignored){}}
    private static void deliver(Context app,CustomerSessionGuard identity,long sequence,Callback cb,List<Order> orders,long saved,boolean cache,Throwable error) {
        MAIN.post(() -> {
            if(!identity.matches(app))return;
            if(sequence!=REQUEST.get()){if(cb!=null)cb.onResult(snapshot(),true,new IllegalStateException("Newer request in progress"));return;}
            synchronized(UnifiedLiveOrderCenter.class) {
                if(!identity.matches(app)||sequence!=REQUEST.get())return;
                if(!cache)writeCache(app,identity.account,orders,saved);
                memory=Collections.unmodifiableList(new ArrayList<>(orders));memoryOwner=identity.account;memoryAt=saved;
            }
            notifyListeners();if(cb!=null)cb.onResult(snapshot(),cache,error);
        });
    }
    private static void sort(List<Order> list){ java.util.Collections.sort(list,(a,b)->{int ar=CustomerOrderState.rank(a.status),br=CustomerOrderState.rank(b.status); if((ar>=20)!=(br>=20))return ar>=20?-1:1; return b.createdAt.compareTo(a.createdAt);}); }
    private static void writeCache(Context c,String owner,List<Order> orders,long saved){
        JSONArray json=new JSONArray();for(Order o:orders)json.put(o.raw);
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("owner",owner).putString(KEY_JSON,json.toString()).putLong(KEY_AT,saved).apply();
    }
    private static List<Order> readCache(Context c,String owner){
        List<Order> result=new ArrayList<>();SharedPreferences prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!CustomerReadSafetyRules.fresh(owner,prefs.getString("owner",""),prefs.getLong(KEY_AT,0),System.currentTimeMillis(),CACHE_MAX_AGE_MS))return result;
        try{JSONArray json=new JSONArray(prefs.getString(KEY_JSON,"[]"));for(int i=0;i<json.length();i++){JSONObject raw=json.optJSONObject(i);if(raw!=null){Order o=new Order(raw);if(!CustomerOrderState.isEnded(o.status))result.add(o);}}sort(result);}catch(Exception ignored){}
        return result;
    }
    private static String normalizeDriverType(String s){String n=s==null?"":s.toLowerCase();return(n.contains("car")||n.contains("mobil"))?"car":"motor";}
    private static String readableService(String s){String n=s==null?"":s.toLowerCase();if(n.contains("food"))return"TransFood";if(n.contains("pickup")||n.contains("send"))return"TransSend";if(n.contains("car")||n.contains("mobil"))return"TransCar";return"TransRide";}
    private static String first(String...v){if(v!=null)for(String x:v)if(x!=null&&!x.trim().isEmpty()&&!"null".equalsIgnoreCase(x.trim()))return x.trim();return"";}
}
