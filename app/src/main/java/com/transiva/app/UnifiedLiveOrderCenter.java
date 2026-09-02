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
    public static List<Order> snapshot() { return new ArrayList<>(memory); }

    /** Fetches from server even when local active_order_id is missing. */
    public static void refresh(Context context, Callback cb) {
        final Context app = context.getApplicationContext();
        if (!TransivaNetworkMonitor.isOnline()) {
            List<Order> cached = readCache(app, true);
            deliver(cb, cached, true, new IllegalStateException("offline"));
            return;
        }
        TransivaNetworkExecutor.execute(() -> {
            List<Order> result = new ArrayList<>(); Throwable error = null;
            try {
                HttpURLConnection c = CustomerApiClient.open(app, URL + "?_=" + System.currentTimeMillis());
                c.setRequestMethod("GET"); c.setConnectTimeout(12000); c.setReadTimeout(12000); c.setUseCaches(false);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                String body = read(in); c.disconnect();
                JSONObject root = body.trim().isEmpty() ? new JSONObject() : new JSONObject(body);
                if (!root.optBoolean("success", false)) throw new IllegalStateException(root.optString("message", "active orders failed"));
                JSONArray a = root.optJSONArray("orders");
                if (a != null) for (int i=0;i<a.length();i++) {
                    JSONObject o=a.optJSONObject(i); if(o==null) continue;
                    Order order=new Order(o); if(!order.id.isEmpty() && !CustomerOrderState.isEnded(order.status)) result.add(order);
                }
                sort(result); writeCache(app, result);
            } catch (Throwable t) { error=t; result=readCache(app, true); }
            final List<Order> out=result; final Throwable err=error;
            MAIN.post(() -> { setMemory(out); if(cb!=null) cb.onResult(snapshot(), err!=null, err); });
        });
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
        memory=Collections.emptyList();
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().clear().apply();
        notifyListeners();
    }

    private static void setMemory(List<Order> list) { memory=Collections.unmodifiableList(new ArrayList<>(list)); notifyListeners(); }
    private static void notifyListeners(){ for(Listener l:LISTENERS) try{l.onOrdersChanged(snapshot());}catch(Throwable ignored){} }
    private static void deliver(Callback cb,List<Order> list,boolean cache,Throwable e){ MAIN.post(()->{setMemory(list);if(cb!=null)cb.onResult(snapshot(),cache,e);}); }
    private static void sort(List<Order> list){ list.sort((a,b)->{int ar=CustomerOrderState.rank(a.status),br=CustomerOrderState.rank(b.status); if((ar>=20)!=(br>=20))return ar>=20?-1:1; return b.createdAt.compareTo(a.createdAt);}); }
    private static void writeCache(Context c,List<Order> list){ JSONArray a=new JSONArray();for(Order o:list)a.put(o.raw);c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY_JSON,a.toString()).putLong(KEY_AT,System.currentTimeMillis()).apply(); }
    private static List<Order> readCache(Context c,boolean allowStale){ List<Order> out=new ArrayList<>(); try{SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);long age=System.currentTimeMillis()-p.getLong(KEY_AT,0L);if(!allowStale&&age>CACHE_MAX_AGE_MS)return out;JSONArray a=new JSONArray(p.getString(KEY_JSON,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){Order x=new Order(o);if(!CustomerOrderState.isEnded(x.status))out.add(x);}}sort(out);}catch(Throwable ignored){} return out; }
    private static String read(InputStream in)throws Exception{if(in==null)return"";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);r.close();return b.toString();}
    private static String normalizeDriverType(String s){String n=s==null?"":s.toLowerCase();return(n.contains("car")||n.contains("mobil"))?"car":"motor";}
    private static String readableService(String s){String n=s==null?"":s.toLowerCase();if(n.contains("food"))return"TransFood";if(n.contains("pickup")||n.contains("send"))return"TransSend";if(n.contains("car")||n.contains("mobil"))return"TransCar";return"TransRide";}
    private static String first(String...v){if(v!=null)for(String x:v)if(x!=null&&!x.trim().isEmpty()&&!"null".equalsIgnoreCase(x.trim()))return x.trim();return"";}
}
