package com.transiva.app;
import android.content.Context;
import org.json.JSONObject;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
/** Owned read request. Cancel interrupts the worker and closes its HTTP socket. Never retries mutations. */
public final class CustomerCancelableRead {
    private final Context app;
    private final CustomerSessionGuard session;
    private boolean canceled;
    private HttpURLConnection connection;
    private Future<?> future;
    private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable deadline;
    public CustomerCancelableRead(Context c) { app=c.getApplicationContext();session=CustomerSessionGuard.capture(app); }
    public boolean current() { synchronized(this){if(canceled)return false;} return session.matches(app); }
    public void start(Runnable work,Runnable rejected) { start(work,rejected,rejected); }
    public void start(Runnable work,Runnable rejected,Runnable timeout) {
        synchronized(this){
            if(canceled)return;
            deadline=() -> {cancel();if(timeout!=null)timeout.run();};main.postDelayed(deadline,45000L);
        }
        Future<?> task=TransivaNetworkExecutor.execute(() -> {try{work.run();}finally{if(deadline!=null)main.removeCallbacks(deadline);}},
                () -> {if(deadline!=null)main.removeCallbacks(deadline);if(rejected!=null)rejected.run();});
        synchronized(this){future=task;if(canceled&&task!=null)task.cancel(true);}
    }
    public void cancel() {
        HttpURLConnection c;Future<?> f;
        synchronized(this){canceled=true;c=connection;connection=null;f=future;future=null;}
        if(deadline!=null)main.removeCallbacks(deadline);
        if(f!=null)f.cancel(true);if(c!=null)c.disconnect();
    }
    public JSONObject read(String endpoint,JSONObject payload,int timeoutMs) throws Exception {
        if(!current()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Read canceled");
        HttpURLConnection c=CustomerApiClient.open(app,endpoint);
        synchronized(this){if(canceled){c.disconnect();throw new InterruptedIOException("Read canceled");}connection=c;}
        try {
            c.setConnectTimeout(timeoutMs);c.setReadTimeout(timeoutMs);c.setUseCaches(false);
            c.setRequestMethod(payload==null?"GET":"POST");c.setRequestProperty("Cache-Control","no-store");
            if(payload!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();
            String body=BoundedResponseReader.read(code>=200&&code<400?c.getInputStream():c.getErrorStream(),4*1024*1024,timeoutMs);
            if(!current())throw new InterruptedIOException("Session changed");
            CustomerApiClient.handleSessionResponse(app,code,body);
            if(code<200||code>=300)throw new java.io.IOException("HTTP "+code);
            return new JSONObject(body);
        } finally {c.disconnect();synchronized(this){if(connection==c)connection=null;}}
    }
}
