package com.transiva.app;
import android.content.Context;import java.util.*;import java.util.concurrent.*;
class SessionManager {
 static String account="1",token="old-token";SessionManager(Context c){}
 boolean isLoggedIn(){return true;}String getId(){return account;}String getUserId(){return account;}String getToken(){return token;}
}
class TransivaNetworkExecutor {
 static int submissions;static Runnable rejected;static FutureTask<?> pending;
 static Future<?> execute(Runnable work,Runnable rejection){submissions++;rejected=rejection;pending=new FutureTask<>(work,null);return pending;}
 static void reject(){rejected.run();}
}
class TransivaNetworkMonitor {
 enum State {OFFLINE,CONNECTED,VALIDATED}interface Listener{void onNetworkChanged(State s);}
 static State state=State.VALIDATED;static Set<Listener> listeners=new HashSet<>();
 static void install(Context c){}static State state(){return state;}static boolean isOnline(){return state!=State.OFFLINE;}
 static void addListener(Listener l){listeners.add(l);}static void removeListener(Listener l){listeners.remove(l);}
 static void update(State s){state=s;for(Listener l:new HashSet<>(listeners))l.onNetworkChanged(s);}
}
