package com.transiva.app;
/** Thread-safe episode tracker; a recovered/old heartbeat cannot reset a newer stall. */
public final class CustomerStallTracker {
    private long episode, sentAt;
    private boolean waiting, reported;
    public synchronized boolean waiting() { return waiting; }
    public synchronized long begin(long now) { if(!waiting){waiting=true;reported=false;sentAt=now;++episode;}return episode; }
    public synchronized void recover(long id) { if(id==episode){waiting=false;reported=false;} }
    public synchronized boolean reportIfStalled(long now,long threshold) {
        if(!waiting||reported||now<sentAt||now-sentAt<threshold)return false;
        reported=true;return true;
    }
}
