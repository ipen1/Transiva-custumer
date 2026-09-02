package com.transiva.app;

import android.os.Handler;
import java.util.concurrent.Future;

/**
 * Runtime controller for large customer features. Centralizes lifecycle ownership of
 * asynchronous work and realtime priority, leaving Activities focused on UI/business state.
 */
public final class CustomerFeatureRuntimeController {
    private final CustomerRealtimeCoordinator.Role role;
    private final CustomerLifecycleNetworkScope networkScope = new CustomerLifecycleNetworkScope();

    public CustomerFeatureRuntimeController(CustomerRealtimeCoordinator.Role role) {
        this.role = role == null ? CustomerRealtimeCoordinator.Role.IDLE : role;
    }

    public void onResume() { CustomerRealtimeCoordinator.enter(role); }
    public void onPause() { CustomerRealtimeCoordinator.leave(role); }
    public Future<?> execute(Runnable task) { return networkScope.execute(task); }
    public Thread newThread(Runnable task) { return networkScope.newThread(task); }
    public Thread newThread(Runnable task, String name) { return networkScope.newThread(task, name); }
    public boolean post(Handler handler, Runnable task) { return networkScope.post(handler, task); }
    public boolean isDestroyed() { return networkScope.isDestroyed(); }
    public void destroy() {
        CustomerRealtimeCoordinator.leave(role);
        networkScope.destroy();
    }
}
