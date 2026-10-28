"""Compile/run actual pure URL resolver and network scope with minimal platform adapters."""
from pathlib import Path
import tempfile,subprocess,shutil
root=Path(__file__).resolve().parents[2]
java=root/'app/src/main/java/com/transiva/app'
with tempfile.TemporaryDirectory(prefix='transiva-auth-java-') as tmp:
 p=Path(tmp)
 files={
 'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler {private final List<Runnable> tasks=new ArrayList<>();public synchronized boolean post(Runnable r){tasks.add(r);return true;}public void flush(){List<Runnable> a;synchronized(this){a=new ArrayList<>(tasks);tasks.clear();}for(Runnable r:a)r.run();}public Handler(){}public Handler(Looper l){}}''',
 'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
 'android/app/Application.java':'package android.app;public class Application{}',
 'android/widget/Toast.java':'package android.widget;public class Toast{public static final int LENGTH_LONG=1;public static Toast makeText(android.app.Application a,String s,int n){return new Toast();}public void show(){}}',
 'com/transiva/app/Adapters.java':'''package com.transiva.app;class TransivaCrashReporter{static void record(Throwable e,String a,String b){}}class CustomerAsyncError{static boolean isCancellation(Throwable e){return e instanceof InterruptedException;}}class TransivaCustomerApplication{static android.app.Application appContext(){return null;}}class CustomerTransportSession{static CustomerTransportSession capture(android.app.Application a){return new CustomerTransportSession();}boolean matches(android.app.Application a){return true;}static void bind(CustomerTransportSession s){}static void clearOwner(){}}''',
 'com/transiva/app/AuthRuntimeCheck.java':'''package com.transiva.app;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.*;
public class AuthRuntimeCheck{
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
 public static void main(String[] args)throws Exception{
  String expected=ApiConfig.server("view_chat_image.php")+"?file=preview%2Fcustomer_ROOM-A_20261014_000000_abcdef.jpg";
  String legacy="server/uploads/chat/preview/customer_ROOM-A_20261014_000000_abcdef.jpg";
  check(expected.equals(ImageUrlResolver.resolve(legacy)),"legacy relative photo routes through authorized endpoint");
  check(expected.equals(ImageUrlResolver.resolve("https://transiva.my.id/"+legacy)),"legacy absolute photo routes through authorized endpoint");
  check(expected.equals(ImageUrlResolver.resolve("http://transiva.my.id/"+legacy)),"legacy HTTP origin upgrades to protected HTTPS");
  check(ImageUrlResolver.resolve("https://cdn.example.test/"+legacy).startsWith("https://cdn.example.test/"),"foreign origin is not mistaken for private Transiva photo");
  check(ImageUrlResolver.resolve("http://evil.test/photo.jpg").isEmpty(),"foreign insecure image is rejected");
  check(0==TransivaNetworkExecutor.queuedTasks(),"network pool starts empty");
  CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(6);
  for(int i=0;i<70;i++)TransivaNetworkExecutor.execute(()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  check(started.await(5,TimeUnit.SECONDS)&&TransivaNetworkExecutor.queuedTasks()==64,"bounded network queue reaches controlled saturation");
  AtomicInteger rejected=new AtomicInteger(),executed=new AtomicInteger();CustomerLifecycleNetworkScope scope=new CustomerLifecycleNetworkScope();
  scope.execute(executed::incrementAndGet,rejected::incrementAndGet);
  check(rejected.get()==1&&executed.get()==0,"rejection callback fires once without executing network task");
  android.os.Handler handler=new android.os.Handler();AtomicInteger applied=new AtomicInteger();scope.post(handler,applied::incrementAndGet);scope.destroy();handler.flush();
  check(applied.get()==0,"destroyed page rejects already queued UI callback");
  check(scope.execute(executed::incrementAndGet)==null,"destroyed page admits no new request");
  release.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while((TransivaNetworkExecutor.activeTasks()!=0||TransivaNetworkExecutor.queuedTasks()!=0)&&System.nanoTime()<deadline)Thread.yield();
  check(TransivaNetworkExecutor.activeTasks()==0&&TransivaNetworkExecutor.queuedTasks()==0,"saturated queue recovers after workers finish");
  CustomerLifecycleNetworkScope active=new CustomerLifecycleNetworkScope();CountDownLatch began=new CountDownLatch(1),interrupted=new CountDownLatch(1);
  active.execute(()->{began.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();}});
  check(began.await(5,TimeUnit.SECONDS),"owned request starts");active.destroy();check(interrupted.await(5,TimeUnit.SECONDS),"destroy cancels active owned request");
 }
}'''}
 for name,text in files.items():f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 for name in ['ApiConfig','ImageUrlResolver','TransivaNetworkExecutor','CustomerLifecycleNetworkScope','CustomerRequestCancellation']:(p/'com/transiva/app'/f'{name}.java').write_text((java/f'{name}.java').read_text())
 subprocess.run((['javac'] if shutil.which('javac') else ['java','--module','jdk.compiler/com.sun.tools.javac.Main'])+['-d',str(p/'classes')]+[str(f) for f in p.rglob('*.java')],check=True)
 subprocess.run(['java','-cp',str(p/'classes'),'com.transiva.app.AuthRuntimeCheck'],check=True,timeout=20)
