package com.transiva.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;
import org.json.JSONObject;

/** One process-wide health probe, subscribed only by visible navigation indicators. Main-thread API. */
final class CustomerServerConnectionMonitor {
    interface Listener { void onStatus(String color, String message); }
    private static final CustomerServerConnectionMonitor INSTANCE = new CustomerServerConnectionMonitor();
    static CustomerServerConnectionMonitor shared() { return INSTANCE; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private final Object lock = new Object();
    private volatile long generation;
    private volatile boolean active;
    private HttpURLConnection connection;
    private Future<?> request;
    private boolean busy;
    private long completedAt;
    private boolean hasResult;
    private boolean healthy;
    private String color="#FBBF24", message="Menghubungkan ke server Transiva";
    private TransivaNetworkMonitor.State previous;
    private final Runnable poll=this::check;
    private final Runnable deadline=() -> { if (active && busy) { cancel(); complete(generation, false, true); } };
    private final TransivaNetworkMonitor.Listener networkListener=state -> main.post(() -> {
        if (!active) return;
        TransivaNetworkMonitor.State old=previous; previous=state;
        if (state == TransivaNetworkMonitor.State.OFFLINE) {
            cancel(); main.removeCallbacks(poll); hasResult=false;
            publish("#EF4444", "Terputus • perangkat tidak memiliki koneksi internet");
        } else if (old == TransivaNetworkMonitor.State.OFFLINE) { hasResult=false; check(); }
    });
    void subscribe(Context context, Listener listener) {
        if (!listeners.add(listener)) return;
        listener.onStatus(color, message);
        if (active) return;
        active=true;
        TransivaNetworkMonitor.install(context.getApplicationContext());
        previous=TransivaNetworkMonitor.state(); TransivaNetworkMonitor.addListener(networkListener);
        if (!TransivaNetworkMonitor.isOnline()) {
            hasResult=false; publish("#EF4444", "Terputus • perangkat tidak memiliki koneksi internet"); return;
        }
        long age=SystemClock.elapsedRealtime()-completedAt;
        long ttl=healthy?30000L:15000L;
        if (hasResult && age>=0 && age<ttl) {
            publish(healthy?"#22C55E":"#EF4444",healthy?"Terhubung • pemeriksaan server/database terakhir berhasil":"Terputus • server belum dapat dihubungi");
            main.postDelayed(poll,ttl-age);
        }
        else check();
    }
    void unsubscribe(Listener listener) {
        listeners.remove(listener);
        if (!listeners.isEmpty()) return;
        active=false; TransivaNetworkMonitor.removeListener(networkListener);
        main.removeCallbacks(poll); cancel();
    }
    void refresh() { if(active&&!busy)check(); }
    private void check() {
        if (!active || busy) return;
        main.removeCallbacks(poll);
        if (!TransivaNetworkMonitor.isOnline()) { hasResult=false; publish("#EF4444", "Terputus • perangkat tidak memiliki koneksi internet"); return; }
        busy=true; final long token=++generation;
        publish("#FBBF24", "Menghubungkan ke server Transiva");
        main.postDelayed(deadline,12000L);
        request=TransivaNetworkExecutor.execute(() -> {
            boolean result=false; HttpURLConnection candidate=null;
            try {
                candidate=(HttpURLConnection)new URL("https://transiva.my.id/server/customer_connection_status.php").openConnection();
                candidate.setConnectTimeout(5000); candidate.setReadTimeout(5000);
                candidate.setInstanceFollowRedirects(false); candidate.setUseCaches(false);
                candidate.setRequestProperty("Accept","application/json"); candidate.setRequestProperty("Cache-Control","no-cache, no-store");
                synchronized(lock) { if(!active||token!=generation)return; connection=candidate; }
                if(candidate.getResponseCode()==200) {
                    JSONObject response=new JSONObject(BoundedResponseReader.read(candidate.getInputStream(),4096,5000));
                    result=response.optBoolean("success",false)&&"transiva-customer-connection".equals(response.optString("service"));
                }
            } catch(Exception ignored) { }
            finally { if(candidate!=null)candidate.disconnect(); synchronized(lock){if(connection==candidate)connection=null;} }
            final boolean value=result; main.post(() -> complete(token,value,false));
        }, () -> main.post(() -> complete(token,false,false)));
    }
    private void complete(long token, boolean result, boolean timedOut) {
        if(!active||token!=generation||(!busy&&!timedOut))return;
        busy=false; request=null; main.removeCallbacks(deadline);
        hasResult=true; healthy=result; completedAt=SystemClock.elapsedRealtime();
        publish(result?"#22C55E":"#EF4444",result?"Terhubung • pemeriksaan server/database terakhir berhasil":"Terputus • server belum dapat dihubungi");
        main.postDelayed(poll,result?30000L:15000L);
    }
    private void cancel() {
        ++generation; busy=false; main.removeCallbacks(deadline);
        Future<?> pending=request; request=null; if(pending!=null)pending.cancel(true);
        synchronized(lock){if(connection!=null){connection.disconnect();connection=null;}}
    }
    private void publish(String nextColor,String nextMessage) {
        color=nextColor; message=nextMessage;
        for(Listener listener:new LinkedHashSet<>(listeners))listener.onStatus(color,message);
    }
}
