package com.transiva.app;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public final class TransivaFreshLocation {
    private static final long MAX_CACHE_AGE_MS = 2 * 60 * 1000L;
    private static final float GOOD_ACCURACY_M = 35f;
    private static final long SEARCH_TIMEOUT_MS = 9000L;

    public interface Callback {
        void onLocation(Location location, boolean fresh);
        void onFailure(String message);
    }

    private TransivaFreshLocation() {}

    public static void request(Context context, Callback callback) throws SecurityException {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) { callback.onFailure("Layanan lokasi tidak tersedia."); return; }
        boolean gps = safeEnabled(manager, LocationManager.GPS_PROVIDER);
        boolean net = safeEnabled(manager, LocationManager.NETWORK_PROVIDER);
        if (!gps && !net) { callback.onFailure("GPS atau layanan lokasi belum aktif."); return; }

        Handler handler = new Handler(Looper.getMainLooper());
        final Location[] best = new Location[]{bestRecent(manager)};
        final boolean[] done = new boolean[]{false};
        final LocationListener[] listenerRef = new LocationListener[1];

        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (location == null || done[0]) return;
                if (better(location, best[0])) best[0] = location;
                long age = Math.max(0L, System.currentTimeMillis() - location.getTime());
                if (age <= 15000L && location.hasAccuracy() && location.getAccuracy() <= GOOD_ACCURACY_M) {
                    done[0] = true;
                    try { manager.removeUpdates(this); } catch (Exception ignored) {}
                    callback.onLocation(location, true);
                }
            }
            @Override public void onStatusChanged(String p,int s,Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        listenerRef[0] = listener;

        if (gps) manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
        if (net) manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());

        handler.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            try { manager.removeUpdates(listenerRef[0]); } catch (Exception ignored) {}
            if (best[0] != null) {
                long age = Math.max(0L, System.currentTimeMillis() - best[0].getTime());
                callback.onLocation(best[0], age <= MAX_CACHE_AGE_MS);
            } else callback.onFailure("Lokasi belum ditemukan. Coba di area yang lebih terbuka.");
        }, SEARCH_TIMEOUT_MS);
    }

    private static boolean safeEnabled(LocationManager m,String p){ try{return m.isProviderEnabled(p);}catch(Exception e){return false;} }
    private static Location bestRecent(LocationManager m) {
        Location best=null;
        for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            try{
                if(!m.isProviderEnabled(p)) continue;
                Location x=m.getLastKnownLocation(p);
                if(x==null) continue;
                long age=Math.max(0L,System.currentTimeMillis()-x.getTime());
                if(age>MAX_CACHE_AGE_MS) continue;
                if(better(x,best)) best=x;
            }catch(Exception ignored){}
        }
        return best;
    }
    private static boolean better(Location a,Location b){
        if(a==null)return false; if(b==null)return true;
        long aa=Math.max(0L,System.currentTimeMillis()-a.getTime());
        long ba=Math.max(0L,System.currentTimeMillis()-b.getTime());
        float ac=a.hasAccuracy()?a.getAccuracy():9999f, bc=b.hasAccuracy()?b.getAccuracy():9999f;
        if(aa+30000L<ba)return true; if(ba+30000L<aa)return false;
        return ac<bc;
    }
}
