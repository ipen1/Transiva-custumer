package com.transiva.app;

import android.os.Handler;
import java.util.concurrent.Future;

/**
 * Runtime controller for large customer features. Centralizes lifecycle ownership of
 * asynchronous work and realtime priority, leaving Activities focused on UI/business state.
 */
public final class CustomerFeatureRuntimeController {
    private final CustomerRealtimeCoordinator.Role role;
    private final CustomerLoadingCoordinator loading = new CustomerLoadingCoordinator();
    private final CustomerLifecycleNetworkScope networkScope = new CustomerLifecycleNetworkScope();

    public CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role role) {
        this.role = role == null ? CustomerRealtimeCoordinator.Role.IDLE : role;
    }

    public void onResume() { CustomerRealtimeCoordinator.enter(role); }
    public void onPause() { CustomerRealtimeCoordinator.leave(role); }
    public Future<?> execute(Runnable task) { return execute(task,null); }
    public Future<?> execute(Runnable task,Runnable rejected) { return loading.execute(null, CustomerLoadingCoordinator.CONTENT, owned(task), rejected); }
    public Future<?> executeLatest(String key,int priority,Runnable task,Runnable rejected) { return loading.execute(key,priority,owned(task),rejected); }
    private Runnable owned(Runnable task){
        if(task==null)return null;
        android.app.Application app=TransivaCustomerApplication.appContext();
        final CustomerTransportSession owner=app==null?null:CustomerTransportSession.capture(app);
        return ()->{try{if(owner!=null){owner.check(app);CustomerTransportSession.bind(owner);}task.run();}
            catch(InterruptedException changed){Thread.currentThread().interrupt();}
            finally{CustomerTransportSession.clearOwner();}};
    }
    public CustomerLoadingCoordinator.State state(String key){return loading.state(key);}
    public void cancel(String key) { loading.cancel(key); }
    public Thread newThread(Runnable task) { return networkScope.newThread(task); }
    public Thread newThread(Runnable task, String name) { return networkScope.newThread(task, name); }
    public boolean post(Handler handler, Runnable task) { return networkScope.post(handler, CustomerLoadingCoordinator.guardCurrent(task)); }
    public boolean postDelayed(Handler handler, Runnable task, long delayMs) {
        if (handler == null || task == null || networkScope.isDestroyed()) return false;
        Runnable guarded=CustomerLoadingCoordinator.guardCurrent(task);
        return handler.postDelayed(() -> networkScope.post(handler,guarded), Math.max(0L, delayMs));
    }
    public boolean isDestroyed() { return networkScope.isDestroyed(); }
    public void destroy() {
        CustomerRealtimeCoordinator.leave(role);
        loading.destroy();
        networkScope.destroy();
    }
}
