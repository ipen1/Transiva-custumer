package com.transiva.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellable read-only auth lookup. Deadline includes time waiting in the executor queue. */
final class AuthPublicRequest {
    interface Result {void received(JSONObject response);}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished=new AtomicBoolean();
    private volatile HttpURLConnection connection;
    private volatile Future<?> future;
    private Runnable deadline;
    void start(Context context,String endpoint,JSONObject payload,Result result){
        final Context app=context.getApplicationContext();
        deadline=()->{if(finished.compareAndSet(false,true)){close();result.received(AuthRequestJson.failure("Pemeriksaan terlalu lama. Silakan coba kembali."));}};
        main.postDelayed(deadline,20000L);
        Future<?> admitted=TransivaNetworkExecutor.execute(()->{
            if(finished.get())return;
            JSONObject response;
            try{response=AuthRequestJson.post(app,endpoint,payload,null,c->{connection=c;if(finished.get())c.disconnect();});}
            catch(Exception error){response=AuthRequestJson.userFailure(error,false);}
            finally{connection=null;}
            deliver(result,response);
        },()->deliver(result,AuthRequestJson.failure("Antrean jaringan penuh. Coba kembali sebentar lagi.")));
        future=admitted;if(finished.get()&&admitted!=null)admitted.cancel(true);
    }
    private void deliver(Result result,JSONObject response){
        main.post(()->{if(finished.compareAndSet(false,true)){main.removeCallbacks(deadline);result.received(response);}});
    }
    void cancel(){finished.set(true);if(deadline!=null)main.removeCallbacks(deadline);close();}
    private void close(){Future<?> f=future;if(f!=null)f.cancel(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null;}
}
