package com.transiva.app;

/** Pure gesture rules shared by the five primary pages. */
public final class CustomerRefreshRules {
    private CustomerRefreshRules() { }
    public static boolean begin(float dx,float dy,float slop,boolean atTop,boolean busy,int pointers) {
        return pointers==1&&atTop&&!busy&&dy>slop*2&&dy>Math.abs(dx)*1.3f;
    }
    public static boolean release(float distance,float threshold) { return distance>=threshold; }
}
