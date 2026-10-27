package com.transiva.app;

/** Monotonic, visible playback time. Loading and time spent holding do not consume the photo. */
final class FoodStoryPlayback {
    private long started=-1,pausedAt=-1,pausedTotal;
    private boolean paused;
    void reset(){started=-1;pausedAt=-1;pausedTotal=0;}
    void start(long now){started=now;pausedTotal=0;pausedAt=paused?now:-1;}
    boolean loaded(){return started>=0;}
    void pause(boolean value,long now){
        if(paused==value)return;
        paused=value;
        if(started<0)return;
        if(value)pausedAt=now;
        else if(pausedAt>=0){pausedTotal+=Math.max(0,now-pausedAt);pausedAt=-1;}
    }
    float fraction(long now){
        if(started<0)return 0;
        long end=paused&&pausedAt>=0?pausedAt:now;
        return Math.max(0f,Math.min(1f,(end-started-pausedTotal)/5000f));
    }
}
