package com.transiva.app;
import android.app.Activity;
/** Extracted chat lifecycle state to avoid badge/read logic spreading across Activity classes. */
public final class ChatController {
    private final Activity a; private String room="";
    public ChatController(Activity a){this.a=a;}
    public void enter(String roomId){room=roomId==null?"":roomId.trim(); ChatStateStore.setActiveRoom(a,room); CustomerAnalytics.funnel(a,"chat_open",null);}
    public void leave(){ if(room.equals(ChatStateStore.activeRoom(a))) ChatStateStore.clearRoom(a); }
}
