package com.transiva.app;

import java.net.HttpURLConnection;
import java.util.concurrent.ConcurrentHashMap;

/** Socket ownership for both legacy lifecycle threads and coordinated requests. */
final class CustomerRequestCancellation {
    private static final ConcurrentHashMap<Thread,HttpURLConnection> ACTIVE=new ConcurrentHashMap<>();
    static void attach(HttpURLConnection connection)throws InterruptedException{
        Thread thread=Thread.currentThread();ACTIVE.put(thread,connection);
        if(thread.isInterrupted()){ACTIVE.remove(thread,connection);connection.disconnect();throw new InterruptedException("Request cancelled");}
    }
    static void detach(HttpURLConnection connection){ACTIVE.remove(Thread.currentThread(),connection);}
    static void cancel(Thread thread){HttpURLConnection connection=ACTIVE.remove(thread);if(connection!=null)connection.disconnect();}
    private CustomerRequestCancellation(){}
}
