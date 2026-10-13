package com.transiva.app;

import android.content.Context;
import android.os.Handler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Every callback belongs to a screen session and the latest request in its lane. */
final class CustomerChatRequestGate {
    private final Context app;
    private final CustomerSessionGuard session;
    private final CustomerFeatureRuntimeController runtime;
    private final ConcurrentHashMap<String, Long> lanes = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadLocal<Ticket> active = new ThreadLocal<>();
    private volatile boolean closed;
    interface ScreenOpen { boolean get(); }
    private final ScreenOpen screenOpen;
    private static final class Ticket {
        final String lane; final long generation;
        Ticket(String lane, long generation) { this.lane=lane; this.generation=generation; }
    }
    CustomerChatRequestGate(Context context, CustomerFeatureRuntimeController runtime) {
        this(context,runtime,() -> true);
    }
    CustomerChatRequestGate(Context context, CustomerFeatureRuntimeController runtime, ScreenOpen screenOpen) {
        this.app=context.getApplicationContext();this.runtime=runtime;this.screenOpen=screenOpen;
        this.session=CustomerSessionGuard.capture(app);
    }
    CustomerSessionGuard session() { return session; }
    boolean current() { return !closed && !runtime.isDestroyed() && screenOpen.get() && session.matches(app); }
    private boolean current(Ticket ticket) {
        return current() && (ticket==null || Long.valueOf(ticket.generation).equals(lanes.get(ticket.lane)));
    }
    void execute(String lane, Runnable work, Runnable rejected) {
        if (!current()) return;
        Ticket ticket=new Ticket(lane,sequence.incrementAndGet()); lanes.put(lane,ticket.generation);
        runtime.execute(() -> {
            if (!current(ticket) || Thread.currentThread().isInterrupted()) return;
            active.set(ticket);
            try { work.run(); } finally { active.remove(); }
        }, () -> {
            active.set(ticket);
            try { if(current(ticket) && rejected!=null) rejected.run(); } finally { active.remove(); }
        });
    }
    void post(Handler handler, Runnable callback) { postDelayed(handler, callback, 0L); }
    void postDelayed(Handler handler, Runnable callback, long delay) {
        Ticket ticket=active.get();
        runtime.postDelayed(handler, () -> {
            if(!current(ticket))return;
            Ticket previous=active.get();if(ticket!=null)active.set(ticket);
            try{callback.run();}finally{if(previous==null)active.remove();else active.set(previous);}
        }, delay);
    }
    void close() { closed=true; lanes.clear(); }
}
