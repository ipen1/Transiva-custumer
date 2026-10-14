"""Exercise production AuthPublicRequest cancellation/admission/deadline with platform adapters."""
from pathlib import Path
import tempfile,subprocess,shutil
root=Path(__file__).resolve().parents[2];src=root/'app/src/main/java/com/transiva/app'
with tempfile.TemporaryDirectory(prefix='transiva-auth-lookups-java-') as tmp:
 p=Path(tmp)
 files={
 'android/content/Context.java':'package android.content;public class Context {public Context getApplicationContext(){return this;}}',
 'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
 'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler{
 static long now;static class Task{Runnable r;long when;Task(Runnable r,long w){this.r=r;when=w;}}static final List<Task> tasks=new ArrayList<>();
 public Handler(Looper l){}public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long d){synchronized(tasks){tasks.add(new Task(r,now+d));return true;}}
 public void removeCallbacks(Runnable r){synchronized(tasks){tasks.removeIf(t->t.r==r);}}
 public static void flush(){while(true){Task found=null;synchronized(tasks){for(Task t:tasks)if(t.when<=now){found=t;break;}if(found!=null)tasks.remove(found);}if(found==null)return;found.r.run();}}
 public static void advance(long d){now+=d;flush();}}
''',
 'android/app/Application.java':'package android.app;public class Application{}',
 'android/widget/Toast.java':'package android.widget;public class Toast{public static final int LENGTH_LONG=1;public static Toast makeText(android.app.Application a,String b,int c){return new Toast();}public void show(){}}',
 'org/json/JSONObject.java':'''package org.json;import java.util.*;public class JSONObject{private final Map<String,Object> m=new HashMap<>();public JSONObject put(String k,Object v){m.put(k,v);return this;}public boolean optBoolean(String k){return Boolean.TRUE.equals(m.get(k));}public String optString(String k){Object v=m.get(k);return v==null?"":v.toString();}}''',
 'com/transiva/app/Adapters.java':'''package com.transiva.app;
import java.net.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import android.content.Context;import org.json.JSONObject;
class TransivaCrashReporter{static void record(Throwable e,String a,String b){}}class CustomerAsyncError{static boolean isCancellation(Throwable e){return e instanceof InterruptedException;}}class TransivaCustomerApplication{static android.app.Application appContext(){return null;}}
class AuthRequestJson {
 interface ConnectionObserver{void opened(HttpURLConnection c);}static volatile CountDownLatch gate;static volatile CountDownLatch started;static final AtomicInteger calls=new AtomicInteger(),closed=new AtomicInteger();
 static JSONObject post(Context c,String endpoint,JSONObject body,Object session,ConnectionObserver observe)throws Exception{
  calls.incrementAndGet();observe.opened(new HttpURLConnection(new URL("https://transiva.my.id")){public void disconnect(){closed.incrementAndGet();}public boolean usingProxy(){return false;}public void connect(){}});
  CountDownLatch current=gate;if(started!=null)started.countDown();if(current!=null)current.await();return new JSONObject().put("success",true).put("value",endpoint);
 }
 static JSONObject failure(String m){return new JSONObject().put("success",false).put("message",m);}static JSONObject userFailure(Exception e,boolean m){return failure("failed");}
}
''',
 'com/transiva/app/AuthLookupRuntimeCheck.java':'''package com.transiva.app;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;import android.os.Handler;import android.content.Context;import org.json.JSONObject;
public class AuthLookupRuntimeCheck{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);System.out.println("PASS "+m);}
 static void until(java.util.function.BooleanSupplier ok)throws Exception{long d=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!ok.getAsBoolean()&&System.nanoTime()<d){Thread.sleep(2);}if(!ok.getAsBoolean())throw new AssertionError("fixture deadline");}
 static void idle()throws Exception{until(()->TransivaNetworkExecutor.activeTasks()==0&&TransivaNetworkExecutor.queuedTasks()==0);Handler.flush();}
 static CountDownLatch fill(int n)throws Exception{CountDownLatch gate=new CountDownLatch(1),began=new CountDownLatch(n==70?6:4);for(int i=0;i<n;i++)TransivaNetworkExecutor.execute(()->{began.countDown();try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});check(began.await(5,TimeUnit.SECONDS),"fixture workers start");return gate;}
 public static void main(String[] args)throws Exception{
  Context context=new Context();AtomicInteger applied=new AtomicInteger();AuthRequestJson.gate=null;
  AuthPublicRequest normal=new AuthPublicRequest();normal.start(context,"current",new JSONObject(),r->{check(r.optBoolean("success"),"normal lookup returns success");applied.incrementAndGet();});idle();
  check(applied.get()==1,"normal response applies once");Handler.advance(20000);check(applied.get()==1,"completed deadline cannot duplicate callback");
  AuthRequestJson.started=new CountDownLatch(1);AuthRequestJson.gate=new CountDownLatch(1);AuthPublicRequest canceled=new AuthPublicRequest();int before=AuthRequestJson.closed.get();
  canceled.start(context,"old",new JSONObject(),r->applied.incrementAndGet());check(AuthRequestJson.started.await(5,TimeUnit.SECONDS),"cancel target starts");canceled.cancel();idle();check(applied.get()==1&&AuthRequestJson.closed.get()>before,"cancel closes socket and suppresses late response");
  AuthRequestJson.gate=null;AuthRequestJson.started=null;CountDownLatch queued=fill(4);int calls=AuthRequestJson.calls.get();AtomicInteger timeout=new AtomicInteger();
  AuthPublicRequest waiting=new AuthPublicRequest();waiting.start(context,"queued",new JSONObject(),r->{check(!r.optBoolean("success"),"queue deadline returns failure");timeout.incrementAndGet();});Handler.advance(20000);check(timeout.get()==1,"queue time is included in deadline");queued.countDown();idle();check(AuthRequestJson.calls.get()==calls,"timed-out queued lookup never starts HTTP");
  CountDownLatch full=fill(70);AtomicInteger rejected=new AtomicInteger();AuthPublicRequest incoming=new AuthPublicRequest();incoming.start(context,"rejected",new JSONObject(),r->{check(!r.optBoolean("success"),"saturation returns recoverable failure");rejected.incrementAndGet();});Handler.flush();Handler.advance(20000);check(rejected.get()==1,"saturation resets UI through exactly one callback");full.countDown();idle();
  AtomicInteger stale=new AtomicInteger();AuthPublicRequest completed=new AuthPublicRequest();completed.start(context,"stale",new JSONObject(),r->stale.incrementAndGet());until(()->TransivaNetworkExecutor.activeTasks()==0&&TransivaNetworkExecutor.queuedTasks()==0);completed.cancel();Handler.flush();check(stale.get()==0,"cancel suppresses response already waiting on UI thread");
  AuthRequestJson.started=new CountDownLatch(1);AuthRequestJson.gate=new CountDownLatch(1);AuthPublicRequest a=new AuthPublicRequest();a.start(context,"A",new JSONObject(),r->stale.incrementAndGet());check(AuthRequestJson.started.await(5,TimeUnit.SECONDS),"old field lookup starts");a.cancel();AuthRequestJson.gate=null;AuthRequestJson.started=null;
  AuthPublicRequest b=new AuthPublicRequest();b.start(context,"B",new JSONObject(),r->{check("B".equals(r.optString("value")),"latest field result is preserved");stale.incrementAndGet();});idle();check(stale.get()==1,"old field response cannot overwrite new lookup");
 }
}'''}
 for name,text in files.items():f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 for name in ['AuthPublicRequest','TransivaNetworkExecutor']:(p/'com/transiva/app'/f'{name}.java').write_text((src/f'{name}.java').read_text())
 compiler=['javac'] if shutil.which('javac') else ['java','--module','jdk.compiler/com.sun.tools.javac.Main']
 subprocess.run(compiler+['-d',str(p/'classes')]+[str(f) for f in p.rglob('*.java')],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'com.transiva.app.AuthLookupRuntimeCheck'],check=True,timeout=30)
