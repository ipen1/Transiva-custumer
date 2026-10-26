package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

public final class CustomerChatNotificationPoller {

    private static final String CHANNEL_ID =
            "transiva_chat_messages";

    private static final long INTERVAL_MS = 8000L;

    private static final Handler HANDLER =
            new Handler(Looper.getMainLooper());

    private static Context appContext;
    private static int userId;
    private static boolean running,requestInFlight;
    private static String openRoom="";
    private static long generation,serial,activeRequest;
    private static Future<?> future;
    private static Runnable deadline;
    private static CustomerTransportSession owner;

    private CustomerChatNotificationPoller() {}
    public static synchronized void start(Context context,int currentUserId) {
        if(context==null||currentUserId<=0)return;
        Context app=context.getApplicationContext();
        SessionManager session=new SessionManager(app);
        if(!session.isLoggedIn()||!String.valueOf(currentUserId).equals(session.getUserId()))return;
        if(running&&userId==currentUserId&&owner!=null&&owner.matches(app))return;
        stop();appContext=app;userId=currentUserId;owner=CustomerTransportSession.capture(app);running=true;
        CustomerChatNoticeState.ensure(app);
        createChannel(app);HANDLER.post(checkRunnable);
    }
    public static synchronized void stop() {
        ++generation;running=false;requestInFlight=false;activeRequest=0;
        if(future!=null)future.cancel(true);future=null;
        if(deadline!=null)HANDLER.removeCallbacks(deadline);deadline=null;
        userId=0;openRoom="";appContext=null;owner=null;HANDLER.removeCallbacks(checkRunnable);
    }
    public static synchronized void setOpenRoom(String room){openRoom=normalizeRoom(room);}
    static synchronized boolean isOpenRoom(String room){return !openRoom.isEmpty()&&openRoom.equals(normalizeRoom(room));}
    public static synchronized void clearOpenRoom(String room){if(openRoom.equals(normalizeRoom(room)))openRoom="";}

    public static void requestPermission(
            Activity activity
    ) {
        if (
                activity == null
                        || Build.VERSION.SDK_INT < 33
        ) {
            return;
        }

        if (
                ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    7301
            );
        }
    }

    private static final Runnable checkRunnable=new Runnable(){public void run(){
        synchronized(CustomerChatNotificationPoller.class){
            if(!running||appContext==null)return;
            checkNow();
            if(running)HANDLER.postDelayed(this,INTERVAL_MS);
        }
    }};
    private static synchronized boolean current(long epoch,long request,Context context,CustomerTransportSession session){
        return running&&epoch==generation&&request==activeRequest&&session==owner&&session.matches(context);
    }
    private static synchronized void finish(long epoch,long request){
        if(epoch!=generation||request!=activeRequest)return;
        requestInFlight=false;activeRequest=0;future=null;
        if(deadline!=null)HANDLER.removeCallbacks(deadline);deadline=null;
    }
    private static synchronized void checkNow(){
        if(!running||appContext==null||owner==null||requestInFlight)return;
        if(!owner.matches(appContext)){stop();return;}
        final Context context=appContext;final int id=userId;final CustomerTransportSession session=owner;
        final long epoch=generation,request=++serial;activeRequest=request;requestInFlight=true;
        final String user=String.valueOf(id);
        final long noticeBoundary=CustomerChatNoticeState.boundary(context,user);
        final int storedCursor=CustomerChatNoticeState.cursor(context,user);
        final int lastId=Math.max(0,storedCursor);
        final boolean bootstrap=storedCursor<0;
        final int maxAge=CustomerChatNoticeState.maxAge(noticeBoundary);
        deadline=()->{synchronized(CustomerChatNotificationPoller.class){
            if(epoch!=generation||request!=activeRequest)return;
            if(future!=null)future.cancel(true);finish(epoch,request);
        }};
        HANDLER.postDelayed(deadline,40000L); // Includes queue time; late work cannot publish or clear a new lease.
        Future<?> submitted=TransivaNetworkExecutor.execute(()->{
            JSONObject response=null;
            try{
                if(!current(epoch,request,context,session))return;
                String endpoint="https://transiva.my.id/server/get_customer_chat_updates.php?user_id="
                        +URLEncoder.encode(String.valueOf(id),StandardCharsets.UTF_8.name())+"&last_id="+lastId+"&notification_policy=3&bootstrap="+(bootstrap?"1":"0")
                        +"&max_age_seconds="+maxAge;
                response=TransivaHttpRepository.getJsonOnce(context,endpoint,15000,session);
            }catch(Exception ignored){}finally{
                final JSONObject result=response;
                HANDLER.post(()->{
                    synchronized(CustomerChatNotificationPoller.class){
                        try{
                            if(!current(epoch,request,context,session)||result==null||!result.optBoolean("success",false))return;
                            // Never consume legacy history if the server has not been upgraded yet.
                            if(result.optInt("notification_policy",0)!=3)return;
                            if(CustomerChatNoticeState.boundary(context,user)!=noticeBoundary)return;
                            JSONArray messages=result.optJSONArray("messages");
                            if(messages!=null)for(int i=0;i<messages.length();i++){
                                if(!current(epoch,request,context,session))return;
                                JSONObject message=messages.optJSONObject(i);
                                if(message==null||message.optInt("id",0)<=lastId)continue;
                                int messageId=message.optInt("id",0);
                                if(CustomerChatNoticeState.claim(context,user,noticeBoundary,messageId)
                                        && !isOpenRoom(message.optString("room_id","")))showNotification(context,message);
                            }
                            if(current(epoch,request,context,session)){
                                int latest=Math.max(lastId,result.optInt("last_id",lastId));
                                CustomerChatNoticeState.advance(context,user,noticeBoundary,latest);
                            }
                        }finally{finish(epoch,request);}
                    }
                });
            }
        },()->finish(epoch,request));
        if(epoch==generation&&request==activeRequest&&requestInFlight)future=submitted;
        else if(submitted!=null)submitted.cancel(true);
    }

    private static void showNotification(
            Context context,
            JSONObject message
    ) {
        if (
                Build.VERSION.SDK_INT >= 33
                        &&
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        String participant =
                first(
                        message.optString(
                                "participant_name"
                        ),
                        "Pesan Transiva"
                );

        String rawMessage =
                message.optString(
                        "message",
                        ""
                );

        String preview =
                CustomerChatPreviewFormatter.notificationText(rawMessage);

        Intent intent = new Intent(
                context,
                CustomerChatRoomActivity.class
        );

        intent.putExtra(
                "order_id",
                message.optString(
                        "order_id",
                        ""
                )
        );

        intent.putExtra(
                "room_id",
                message.optString(
                        "room_id",
                        ""
                )
        );

        intent.putExtra(
                "participant_name",
                participant
        );

        intent.putExtra(
                "order_type",
                message.optString(
                        "order_type",
                        ""
                )
        );

        intent.putExtra(
                "order_status",
                message.optString(
                        "status",
                        ""
                )
        );

        intent.putExtra(
                "read_only",
                false
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        int notificationId =
                Math.max(
                        1,
                        message.optInt(
                                "id",
                                (int)(
                                        System.currentTimeMillis()
                                                % Integer.MAX_VALUE
                                )
                        )
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        notificationId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        (
                                Build.VERSION.SDK_INT >= 23
                                        ? PendingIntent.FLAG_IMMUTABLE
                                        : 0
                        )
                );

        int smallIcon =
                context.getResources()
                        .getIdentifier(
                                "ic_notification",
                                "drawable",
                                context.getPackageName()
                        );

        if (smallIcon == 0) {
            smallIcon =
                    context.getApplicationInfo()
                            .icon;
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(smallIcon)
                        .setContentTitle(participant)
                        .setContentText(preview)
                        .setStyle(
                                new NotificationCompat
                                        .BigTextStyle()
                                        .bigText(preview)
                        )
                        .setAutoCancel(true)
                        .setPriority(
                                NotificationCompat
                                        .PRIORITY_HIGH
                        )
                        .setCategory(
                                NotificationCompat
                                        .CATEGORY_MESSAGE
                        )
                        .setContentIntent(pendingIntent);

        TransivaNotificationStyle.apply(context, builder, "chat");

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager != null) {
            manager.notify(
                    notificationId,
                    builder.build()
            );
        }
    }

    private static void createChannel(
            Context context
    ) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        NotificationManager manager =
                (NotificationManager)
                        context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (manager == null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Pesan Driver dan Merchant",
                        NotificationManager
                                .IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Notifikasi pesan baru terkait order Transiva"
        );

        channel.enableVibration(CustomerAppSettings.isVibrationEnabled(context));

        manager.createNotificationChannel(
                channel
        );
    }

    private static String normalizeRoom(
            String roomId
    ) {
        return roomId == null
                ? ""
                : roomId.trim()
                .replace('_', '-')
                .toUpperCase();
    }

    private static String first(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
            ) {
                return value.trim();
            }
        }

        return "";
    }
}
