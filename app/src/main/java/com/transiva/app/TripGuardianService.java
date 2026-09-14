package com.transiva.app;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-assisted trip anomaly watcher. Never changes order status. */
public class TripGuardianService extends Service {
    public static final String EXTRA_ORDER_ID="order_id";
    private static final String CH="transiva_trip_guardian";
    private final Handler h=new Handler(Looper.getMainLooper()); private final AtomicBoolean busy=new AtomicBoolean(false);
    private String orderId=""; private int lastRisk=0;
    private final Runnable poll=new Runnable(){@Override public void run(){check(); h.postDelayed(this,20000L);}};
    @Override public void onCreate(){super.onCreate(); createChannel();}
    @Override public int onStartCommand(Intent i,int f,int id){if(i!=null)orderId=i.getStringExtra(EXTRA_ORDER_ID); if(orderId==null)orderId=""; startForeground(7812,ongoing()); h.removeCallbacks(poll); h.post(poll); return START_STICKY;}
    private void check(){if(orderId.isEmpty()||!busy.compareAndSet(false,true))return; TransivaNetworkExecutor.execute(()->{try{JSONObject req=new JSONObject().put("order_id",orderId).put("action","check"); JSONObject r=RideSafetyApi.post(this,"ride_safety_monitor.php",req); int risk=r.optInt("risk_level",0); if(risk>0 && risk>=lastRisk) alert(risk,r.optString("message","Perjalanan terdeteksi tidak normal.")); lastRisk=risk;}catch(Exception ignored){}finally{busy.set(false);}});}
    private Notification ongoing(){return new NotificationCompat.Builder(this,CH).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Transiva Trip Guardian aktif").setContentText("Perjalanan dipantau untuk mendeteksi kondisi tidak normal.").setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build();}
    private void alert(int risk,String msg){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(nm==null)return; Notification n=new NotificationCompat.Builder(this,CH).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(risk>=2?"Periksa keselamatan perjalanan":"Trip Guardian").setContentText(msg).setStyle(new NotificationCompat.BigTextStyle().bigText(msg)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build(); nm.notify(7813,n);}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(nm!=null){NotificationChannel c=new NotificationChannel(CH,"Trip Guardian",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Pemantauan keselamatan perjalanan Transiva");nm.createNotificationChannel(c);}}}
    @Override public void onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
