"""Test real auth lookup code using request-owned gates and concrete callback barriers.

Never infer request completion from ThreadPoolExecutor active/queue snapshots.
An old request is deliberately completed after the new field callback to test
stale-result rejection even when HTTP ignores thread interruption.
"""
from pathlib import Path
import tempfile,subprocess,shutil,argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--repeat",type=int,default=1);args=parser.parse_args()
if not 1<=args.repeat<=100:parser.error("--repeat must be 1..100")
root=Path(__file__).resolve().parents[2];src=root/'app/src/main/java/com/transiva/app'
with tempfile.TemporaryDirectory(prefix='transiva-auth-lookups-java-') as tmp:
 p=Path(tmp)
 files={
 'android/content/Context.java':'package android.content;public class Context {public Context getApplicationContext(){return this;}}',
 'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
 'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler{
 static long now;static final java.util.concurrent.atomic.AtomicInteger posts=new java.util.concurrent.atomic.AtomicInteger();static class Task{Runnable r;long when;Task(Runnable r,long w){this.r=r;when=w;}}static final List<Task> tasks=new ArrayList<>();
 public Handler(Looper l){}public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long d){synchronized(tasks){tasks.add(new Task(r,now+d));posts.incrementAndGet();return true;}}
 public void removeCallbacks(Runnable r){synchronized(tasks){tasks.removeIf(t->t.r==r);}}
 public static void flush(){while(true){Task found=null;synchronized(tasks){for(Task t:tasks)if(t.when<=now){found=t;break;}if(found!=null)tasks.remove(found);}if(found==null)return;found.r.run();}}
 public static int postCount(){return posts.get();}public static void advance(long d){synchronized(tasks){now+=d;}flush();}}
''',
 'android/app/Application.java':'package android.app;public class Application{}',
 'android/widget/Toast.java':'package android.widget;public class Toast{public static final int LENGTH_LONG=1;public static Toast makeText(android.app.Application a,String b,int c){return new Toast();}public void show(){}}',
 'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject{private final Map<String,Object> m=new HashMap<>();public JSONObject put(String k,Object v){m.put(k,v);return this;}public boolean optBoolean(String k){return Boolean.TRUE.equals(m.get(k));}public String optString(String k){Object v=m.get(k);return v==null?"":v.toString();}}''',
 'com/transiva/app/Adapters.java':'''package com.transiva.app;
import java.net.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import android.content.Context;import org.json.JSONObject;
class TransivaCrashReporter{static void record(Throwable e,String a,String b){}}class CustomerAsyncError{static boolean isCancellation(Throwable e){return e instanceof InterruptedException;}}class TransivaCustomerApplication{static android.app.Application appContext(){return null;}}
class AuthRequestJson {
 interface ConnectionObserver{void opened(HttpURLConnection c);}
 static class Gate {
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),exited=new CountDownLatch(1);
  final boolean ignoreInterrupt;Gate(boolean ignore){ignoreInterrupt=ignore;}
  void await()throws InterruptedException{boolean interrupted=false;while(true){try{release.await();break;}catch(InterruptedException e){if(!ignoreInterrupt)throw e;interrupted=true;}}if(interrupted)Thread.currentThread().interrupt();}
 }
 static final ConcurrentHashMap<String,Gate> gates=new ConcurrentHashMap<>();
 static final AtomicInteger calls=new AtomicInteger(),closed=new AtomicInteger();
 static Gate block(String endpoint,boolean ignore){Gate g=new Gate(ignore);gates.put(endpoint,g);return g;}
 static JSONObject post(Context c,String endpoint,JSONObject body,Object session,ConnectionObserver observe)throws Exception{
  Gate gate=gates.remove(endpoint);calls.incrementAndGet();
  try{
   observe.opened(new HttpURLConnection(new URL("https://fixture.invalid")){public void disconnect(){closed.incrementAndGet();}public boolean usingProxy(){return false;}public void connect(){}});
   if(gate!=null){gate.entered.countDown();gate.await();}
   return new JSONObject().put("success",true).put("value",endpoint);
  }finally{if(gate!=null)gate.exited.countDown();}
 }
 static JSONObject failure(String m){return new JSONObject().put("success",false).put("message",m);}static JSONObject userFailure(Exception e,boolean m){return failure("failed");}
}
''',
 'com/transiva/app/AuthLookupRuntimeCheck.java':'''package com.transiva.app;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import android.os.Handler;import android.content.Context;import org.json.JSONObject;
public class AuthLookupRuntimeCheck {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);System.out.println("PASS "+m);}
 static void until(java.util.function.BooleanSupplier ok,boolean pump)throws Exception{
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
  while(System.nanoTime()<end){if(pump)Handler.flush();if(ok.getAsBoolean())return;Thread.sleep(2);}
  throw new AssertionError("fixture deadline while awaiting concrete operation completion");
 }
 static void entered(AuthRequestJson.Gate g)throws Exception{check(g.entered.await(5,TimeUnit.SECONDS),"request-specific gate entered");}
 static void forceWorkerCleanupBeforeCancelReturns(AuthPublicRequest request,int postsBeforeCancel)throws Exception{
  java.lang.reflect.Field field=AuthPublicRequest.class.getDeclaredField("future");field.setAccessible(true);
  Future<?> original=(Future<?>)field.get(request);
  field.set(request,new Future<Object>(){
   public boolean cancel(boolean interrupt){boolean result=original.cancel(interrupt);try{
    // The response is posted only AFTER AuthPublicRequest's finally clears its
    // connection field. Force that legal interleaving on every regression run.
    until(()->Handler.postCount()>postsBeforeCancel,false);
   }catch(Exception e){throw new AssertionError(e);}return result;}
   public boolean isCancelled(){return original.isCancelled();}public boolean isDone(){return original.isDone();}
   public Object get()throws InterruptedException,ExecutionException{return original.get();}
   public Object get(long n,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{return original.get(n,unit);}
  });
 }
 static class Workers {
  final CountDownLatch release=new CountDownLatch(1);final List<Future<?>> futures=new ArrayList<>();
  Workers(int n)throws Exception{CountDownLatch began=new CountDownLatch(n==70?6:4);
   for(int x=0;x<n;x++)futures.add(TransivaNetworkExecutor.execute(()->{began.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
   check(began.await(5,TimeUnit.SECONDS),"fixture workers start");
  }
  void drain()throws Exception{release.countDown();for(Future<?> f:futures)f.get(5,TimeUnit.SECONDS);TransivaNetworkExecutor.submit(()->null).get(5,TimeUnit.SECONDS);}
 }
 public static void main(String[] ignored)throws Exception{
  Context context=new Context();AtomicInteger applied=new AtomicInteger();
  AuthPublicRequest normal=new AuthPublicRequest();normal.start(context,"current",new JSONObject(),r->{check(r.optBoolean("success"),"normal lookup returns success");applied.incrementAndGet();});
  until(()->applied.get()==1,true);check(applied.get()==1,"normal response applies once");Handler.advance(20000);check(applied.get()==1,"completed deadline cannot duplicate callback");

  AuthRequestJson.Gate cancelGate=AuthRequestJson.block("cancel",false);AuthPublicRequest canceled=new AuthPublicRequest();int before=AuthRequestJson.closed.get();
  canceled.start(context,"cancel",new JSONObject(),r->applied.incrementAndGet());entered(cancelGate);
  int posted=Handler.postCount();forceWorkerCleanupBeforeCancelReturns(canceled,posted);canceled.cancel();check(cancelGate.exited.await(5,TimeUnit.SECONDS),"canceled worker exits");
  until(()->Handler.postCount()>posted,false);Handler.flush();check(applied.get()==1&&AuthRequestJson.closed.get()>before,"cancel closes socket and suppresses late response");

  Workers queued=new Workers(4);int calls=AuthRequestJson.calls.get();AtomicInteger timeout=new AtomicInteger();
  AuthPublicRequest waiting=new AuthPublicRequest();waiting.start(context,"queued",new JSONObject(),r->{check(!r.optBoolean("success"),"queue deadline returns failure");timeout.incrementAndGet();});
  Handler.advance(20000);check(timeout.get()==1,"queue time is included in deadline");queued.drain();check(AuthRequestJson.calls.get()==calls,"timed-out queued lookup never starts HTTP");

  Workers full=new Workers(70);AtomicInteger rejected=new AtomicInteger();new AuthPublicRequest().start(context,"rejected",new JSONObject(),r->{check(!r.optBoolean("success"),"saturation returns recoverable failure");rejected.incrementAndGet();});
  Handler.flush();Handler.advance(20000);check(rejected.get()==1,"saturation resets UI through exactly one callback");full.drain();

  AtomicInteger stale=new AtomicInteger();AuthPublicRequest completed=new AuthPublicRequest();int beforeCompleted=Handler.postCount();
  completed.start(context,"stale",new JSONObject(),r->stale.incrementAndGet());until(()->Handler.postCount()>=beforeCompleted+2,false);completed.cancel();Handler.flush();check(stale.get()==0,"cancel suppresses response already waiting on UI thread");

  // Keep A alive after interruption until B has applied; no global gate can leak to B.
  AuthRequestJson.Gate old=AuthRequestJson.block("A",true);AuthPublicRequest a=new AuthPublicRequest();a.start(context,"A",new JSONObject(),r->stale.incrementAndGet());entered(old);a.cancel();
  new AuthPublicRequest().start(context,"B",new JSONObject(),r->{check("B".equals(r.optString("value")),"latest field result is preserved");stale.incrementAndGet();});
  until(()->stale.get()==1,true);int beforeLate=Handler.postCount();old.release.countDown();check(old.exited.await(5,TimeUnit.SECONDS),"old HTTP completes after new field callback");
  until(()->Handler.postCount()>beforeLate,false);Handler.flush();check(stale.get()==1,"old field response cannot overwrite new lookup");
 }
}'''}
 for name,text in files.items():f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 for name in ['AuthPublicRequest','TransivaNetworkExecutor']:(p/'com/transiva/app'/f'{name}.java').write_text((src/f'{name}.java').read_text())
 compiler=['javac'] if shutil.which('javac') else ['java','--module','jdk.compiler/com.sun.tools.javac.Main']
 subprocess.run(compiler+['-d',str(p/'classes')]+[str(f) for f in p.rglob('*.java')],check=True)
 for run in range(args.repeat):
  subprocess.run(['java','-cp',str(p/'classes'),'com.transiva.app.AuthLookupRuntimeCheck'],check=True,timeout=30)
 print(f'PASS auth lookup runtime: {args.repeat} fresh JVM runs; request-owned gates, concrete callback barriers')
