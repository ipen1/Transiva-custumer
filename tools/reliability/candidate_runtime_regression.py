#!/usr/bin/env python3
"""Production candidate queue, controlled signaling/time. Does not initialize JNI or contact a server."""
import os,subprocess,tempfile,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent;app=root.parents[1]/'app/src/main/java/com/transiva/app';sdk=Path(os.environ['ANDROID_HOME'])/'platforms/android-36/android.jar'
aars=list((Path.home()/'.gradle/caches/modules-2/files-2.1/io.github.webrtc-sdk/android/144.7559.09').rglob('*.aar'));assert aars
with tempfile.TemporaryDirectory(prefix='ice-regression-') as directory:
 p=Path(directory)
 with zipfile.ZipFile(aars[0]) as z:(p/'webrtc.jar').write_bytes(z.read('classes.jar'))
 files={
 'android/content/Context.java':'package android.content;public class Context{public static final String AUDIO_SERVICE="audio";public Context getApplicationContext(){return this;}public Object getSystemService(String n){return new android.media.AudioManager();}public String getPackageName(){return "com.transiva.customer";}public void sendBroadcast(Intent i){}}',
 'android/content/Intent.java':'package android.content;public class Intent{public Intent(){}public Intent(String s){}public String getStringExtra(String k){return "fixture";}public boolean getBooleanExtra(String k,boolean d){return d;}public Intent setPackage(String p){return this;}}',
 'android/media/AudioManager.java':'package android.media;public class AudioManager{public static final int MODE_NORMAL=0,MODE_IN_COMMUNICATION=3;public void setSpeakerphoneOn(boolean b){}public void setMode(int n){}}',
 'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
 'android/os/SystemClock.java':'package android.os;public class SystemClock{public static volatile long now;public static long elapsedRealtime(){return now;}}',
 'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler{static class E{Runnable r;long at;E(Runnable r,long at){this.r=r;this.at=at;}}static List<E> q=new ArrayList<>();public Handler(Looper l){}public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long d){synchronized(q){q.add(new E(r,SystemClock.now+d));}return true;}public void removeCallbacks(Runnable r){synchronized(q){q.removeIf(e->e.r==r);}}public void removeCallbacksAndMessages(Object o){synchronized(q){q.clear();}}public static void step(){for(int n=0;n<1000;n++){E next=null;synchronized(q){for(E e:q)if(e.at<=SystemClock.now){next=e;break;}if(next!=null)q.remove(next);}if(next==null)return;next.r.run();}throw new AssertionError("callback loop");}}''',
 'com/transiva/app/Fixtures.java':'''package com.transiva.app;import android.content.*;import org.json.*;import java.util.concurrent.*;
class SessionManager{SessionManager(Context c){}}
class CustomerTransportSession{static boolean valid=true;static CustomerTransportSession capture(Context c){return new CustomerTransportSession();}boolean matches(Context c){return valid;}}
class WebRtcNativeRuntime{static void initialize(Context c){}}
class CallPeerIdentity{static String first(String... values){for(String s:values)if(s!=null&&!s.isEmpty())return s;return "";}}
class TransivaHttpRepository{static class ServerResponseException extends Exception{final int status;ServerResponseException(int n){status=n;}}}
class WebRtcSignalApi{static volatile int calls,failures,status;static volatile CountDownLatch block;static JSONObject post(SessionManager s,JSONObject p,CustomerTransportSession o)throws Exception{return new JSONObject().put("success",true);}static JSONObject postCandidate(SessionManager s,JSONObject p,CustomerTransportSession o)throws Exception{calls++;if(block!=null)block.await();if(status!=0)throw new TransivaHttpRepository.ServerResponseException(status);if(calls<=failures)throw new java.io.IOException("fixture");return new JSONObject().put("success",true);}}
''',
 'com/transiva/app/CandidateProbe.java':'''package com.transiva.app;import android.content.*;import android.os.*;import org.webrtc.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;
public class CandidateProbe{static int checks;static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}static Object field(Object o,String n)throws Exception{Field f=WebRtcSessionEngine.class.getDeclaredField(n);f.setAccessible(true);return f.get(o);}static void send(WebRtcSessionEngine e,String id)throws Exception{Method m=WebRtcSessionEngine.class.getDeclaredMethod("queueCandidate",IceCandidate.class);m.setAccessible(true);m.invoke(e,new IceCandidate("0",0,id));}static WebRtcSessionEngine engine(){WebRtcSignalApi.calls=0;WebRtcSignalApi.failures=0;WebRtcSignalApi.status=0;WebRtcSignalApi.block=null;CustomerTransportSession.valid=true;SystemClock.now=0;return new WebRtcSessionEngine(new Context(),new Intent(),()->{});}static void tick(int steps)throws Exception{for(int n=0;n<steps;n++){Thread.sleep(5);Handler.step();SystemClock.now+=100;}}static void close(WebRtcSessionEngine e)throws Exception{e.destroy();tick(2);}
public static void main(String[] args)throws Exception{
 WebRtcSessionEngine e=engine();WebRtcSignalApi.failures=2;send(e,"one");send(e,"one");tick(80);check(WebRtcSignalApi.calls==3&&((Map<?,?>)field(e,"outgoingIce")).isEmpty(),"losses retry same candidate and deduplicate pending event");close(e);
 e=engine();WebRtcSignalApi.failures=100;send(e,"two");tick(100);check(WebRtcSignalApi.calls==3,"at most three attempts");tick(180);check(e.ended,"finite media recovery window");close(e);
 e=engine();e.connected=true;WebRtcSignalApi.failures=100;send(e,"extra");tick(310);check(WebRtcSignalApi.calls==3&&e.connected&&!e.ended,"redundant candidate failure preserves connected media");close(e);
 e=engine();WebRtcSignalApi.status=400;send(e,"invalid");tick(20);check(WebRtcSignalApi.calls==1,"permanent HTTP rejection does not retry");close(e);
 e=engine();CustomerTransportSession.valid=false;send(e,"stale");tick(10);check(WebRtcSignalApi.calls==0&&e.ended,"changed session sends no candidate");close(e);
 e=engine();WebRtcSignalApi.block=new CountDownLatch(1);send(e,"blocked");tick(2);e.end(false,"done");tick(100);check(WebRtcSignalApi.calls==1&&((Map<?,?>)field(e,"outgoingIce")).isEmpty(),"hangup cancels pending retries");close(e);
 e=engine();WebRtcSignalApi.block=new CountDownLatch(1);send(e,"same");tick(2);((Map<?,?>)field(e,"outgoingIce")).clear();send(e,"same");WebRtcSignalApi.block.countDown();tick(20);check(WebRtcSignalApi.calls==2&&((Map<?,?>)field(e,"outgoingIce")).isEmpty(),"old generation completion cannot delete replacement candidate");close(e);
 e=engine();e.connected=true;WebRtcSignalApi.block=new CountDownLatch(1);for(int n=0;n<100;n++)send(e,"cap-"+n);check(((Map<?,?>)field(e,"outgoingIce")).size()<=64,"gathering burst remains bounded");close(e);
 System.out.println("PASS "+checks+" candidate runtime scenarios");}}
'''}
 for name,text in files.items():
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 cp=os.pathsep.join([os.environ['JSON_JAR'],str(sdk),str(p/'webrtc.jar')]);sources=list(p.rglob('*.java'))+[app/(name+'.java') for name in ['WebRtcSessionEngine','CallRecoveryPolicy','CallMediaState','NetworkSafetyRules']]
 subprocess.run([os.getenv('JAVAC_BIN','javac'),'-cp',cp,'-d',str(p/'classes'),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',str(p/'classes')+os.pathsep+cp,'com.transiva.app.CandidateProbe'],check=True)
