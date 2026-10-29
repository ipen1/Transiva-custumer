package com.transiva.app;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/** Account-bound read owned by the reorder screen; never creates or replays an order. */
public final class SmartReorderRepository {
    private final CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
    private final Handler main=new Handler(Looper.getMainLooper());
    public interface Callback{void onResult(JSONObject data,Throwable error);}
    public void load(Context context,Callback cb){
        final CustomerSessionGuard session=CustomerSessionGuard.capture(context);
        scope.execute(()->{
            try{JSONObject result=TransivaHttpRepository.getJsonOnce(context,ApiConfig.server("customer_smart_reorder.php"),12000);
                scope.post(main,()->{if(session.matches(context))cb.onResult(result,null);});
            }catch(Exception e){scope.post(main,()->{if(session.matches(context))cb.onResult(null,e);});}
        },()->scope.post(main,()->{if(session.matches(context))cb.onResult(null,new IllegalStateException("Busy"));}));
    }
    public void close(){scope.destroy();main.removeCallbacksAndMessages(null);}
}
