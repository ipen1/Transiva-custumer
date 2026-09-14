package com.transiva.app;

import android.app.*;
import android.content.*;
import android.media.MediaRecorder;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.File;

/** Opt-in safety recording stored in app-private storage. No automatic public upload. */
public class AudioProtectService extends Service {
    public static final String EXTRA_ORDER_ID="order_id"; private static final String CH="transiva_audio_protect";
    private MediaRecorder recorder; private File output; private String orderId="";
    @Override public void onCreate(){super.onCreate(); createChannel();}
    @Override public int onStartCommand(Intent i,int flags,int id){orderId=i==null?"":String.valueOf(i.getStringExtra(EXTRA_ORDER_ID)); startForeground(7820,notification()); startRecording(); return START_NOT_STICKY;}
    private void startRecording(){if(recorder!=null)return; try{File dir=new File(getFilesDir(),"safety_audio"); if(!dir.exists())dir.mkdirs(); output=new File(dir,"ride_"+orderId.replaceAll("[^A-Za-z0-9_-]","_")+"_"+System.currentTimeMillis()+".m4a"); recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(64000); recorder.setAudioSamplingRate(22050); recorder.setOutputFile(output.getAbsolutePath()); recorder.prepare(); recorder.start(); getSharedPreferences("transiva_safety",MODE_PRIVATE).edit().putString("last_audio_path",output.getAbsolutePath()).putString("last_audio_order",orderId).apply();}catch(Exception e){stopSelf();}}
    private Notification notification(){return new NotificationCompat.Builder(this,CH).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("AudioProtect aktif").setContentText("Rekaman keselamatan tersimpan privat selama perjalanan.").setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CH,"AudioProtect",NotificationManager.IMPORTANCE_LOW));}}
    @Override public void onDestroy(){try{if(recorder!=null){recorder.stop();recorder.reset();recorder.release();}}catch(Exception ignored){}recorder=null;super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
