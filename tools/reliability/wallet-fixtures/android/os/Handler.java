package android.os;
import java.util.*;
public class Handler {
 private static final Map<Runnable,Long> timers=new LinkedHashMap<>();
 public Handler(Looper l){}
 public boolean post(Runnable r){r.run();return true;}
 public boolean postDelayed(Runnable r,long delay){timers.put(r,SystemClock.now+delay);return true;}
 public void removeCallbacks(Runnable r){timers.remove(r);}
 public static void advance(long ms){SystemClock.now+=ms;for(Runnable r:new ArrayList<>(timers.keySet())){if(timers.containsKey(r)&&timers.get(r)<=SystemClock.now){timers.remove(r);r.run();}}}
}
