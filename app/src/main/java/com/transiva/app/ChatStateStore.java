package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single local source for unread/active-room chat UI state. Server remains source of truth for receipts/messages. */
public final class ChatStateStore {
    public interface Listener { void onChatStateChanged(int unread, String activeRoom); }
    private static final String PREF="customer_chat_state_v1"; private static final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private ChatStateStore(){}
    public static int unread(Context c){ return p(c).getInt("unread",0); }
    public static String activeRoom(Context c){ return p(c).getString("active_room",""); }
    public static void setUnread(Context c,int n){ p(c).edit().putInt("unread",Math.max(0,n)).apply(); fire(c); }
    public static void setActiveRoom(Context c,String room){ p(c).edit().putString("active_room",room==null?"":room.trim()).apply(); fire(c); }
    public static void clearRoom(Context c){ setActiveRoom(c,""); }
    public static void addListener(Listener l){ if(l!=null)listeners.addIfAbsent(l); } public static void removeListener(Listener l){listeners.remove(l);}
    private static SharedPreferences p(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);} private static void fire(Context c){int u=unread(c);String r=activeRoom(c);for(Listener l:listeners)try{l.onChatStateChanged(u,r);}catch(Throwable ignored){}}
}
