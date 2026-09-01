package com.transiva.app;
/** Canonical customer order state machine. Delegates normalization to the proven CustomerOrderState rules. */
public final class OrderStateMachine {
    private OrderStateMachine(){}
    public static String normalize(String s){ return CustomerOrderState.normalize(s); }
    public static String advance(String current,String incoming){ return CustomerOrderState.laterOf(current,incoming); }
    public static boolean shouldStopSearch(String s){ return !CustomerOrderState.isSearching(s); }
    public static boolean shouldOpenTrip(String s){ return CustomerOrderState.isTrip(s); }
    public static boolean canChat(String s){ return CustomerOrderState.canChat(s); }
    public static boolean ended(String s){ return CustomerOrderState.isEnded(s); }
}
