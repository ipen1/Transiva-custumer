from pathlib import Path
import subprocess,tempfile,os
src=Path(__file__).resolve().parents[2]/'app/src/main/java/com/transiva/app/CustomerProfileCache.java'
cp=os.environ['JSON_JAR']
with tempfile.TemporaryDirectory() as temp:
 r=Path(temp)
 def put(path,text):
  f=r/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 put('android/os/Looper.java','package android.os; public class Looper{public static Looper getMainLooper(){return new Looper();}}')
 put('android/os/Handler.java','package android.os; public class Handler{public static boolean queue;public static final java.util.List<Runnable> pending=new java.util.ArrayList<>();public Handler(Looper l){}public void post(Runnable r){if(queue)pending.add(r);else r.run();}public static void drain(){for(Runnable r:new java.util.ArrayList<>(pending))r.run();pending.clear();}}')
 put('android/content/SharedPreferences.java','''package android.content;import java.util.*;public class SharedPreferences{
 final Map<String,Object> m=new HashMap<>(); public synchronized String getString(String k,String d){return (String)m.getOrDefault(k,d);}public synchronized long getLong(String k,long d){return ((Number)m.getOrDefault(k,d)).longValue();}public Editor edit(){return new Editor();}
 public class Editor{Map<String,Object> v=new HashMap<>();boolean clear;public Editor putString(String k,String s){v.put(k,s);return this;}public Editor putLong(String k,long x){v.put(k,x);return this;}public Editor clear(){clear=true;return this;}public void apply(){synchronized(SharedPreferences.this){if(clear)m.clear();m.putAll(v);}}}}
''')
 put('android/content/Context.java','''package android.content;import java.util.*;public class Context{public static final int MODE_PRIVATE=0;final Map<String,SharedPreferences> p=new HashMap<>();public Context getApplicationContext(){return this;}public synchronized SharedPreferences getSharedPreferences(String n,int m){return p.computeIfAbsent(n,k->new SharedPreferences());}}
''')
 put('com/transiva/app/SessionManager.java','''package com.transiva.app;import android.content.Context;public class SessionManager{static volatile String id="99",token="tokenA";static volatile boolean login=true;public SessionManager(Context c){}public boolean isLoggedIn(){return login;}public String getRole(){return "customer";}public String getId(){return id;}public String getUserId(){return id;}public String getToken(){return token;}
static org.json.JSONObject raw=new org.json.JSONObject().put("id",99).put("username","session").put("role","customer");
public org.json.JSONObject getSessionJson(){return raw;}
public boolean saveUser(org.json.JSONObject value){raw=value;return true;}}
''')
 put('com/transiva/app/DeliveryAddressGate.java','package com.transiva.app;import android.content.Context;import org.json.JSONObject;public class DeliveryAddressGate{static void storeProfile(Context c,String id,JSONObject p){}}')
 put('com/transiva/app/ApiConfig.java','package com.transiva.app;public class ApiConfig{static String server(String p){return p;}}')
 put('com/transiva/app/DashboardReadExecutor.java','''package com.transiva.app;import java.util.concurrent.*;public class DashboardReadExecutor{static final ExecutorService e=Executors.newFixedThreadPool(2);public static <T> Future<T> submit(Callable<T> c){return e.submit(c);}}
''')
 put('com/transiva/app/TransivaHttpRepository.java','''package com.transiva.app;import android.content.Context;import org.json.JSONObject;import java.util.concurrent.*;import java.util.concurrent.atomic.*;public class TransivaHttpRepository{static AtomicInteger calls=new AtomicInteger();static volatile CountDownLatch started,release;static JSONObject result;static JSONObject getJson(Context c,String u,int t)throws Exception{calls.incrementAndGet();if(started!=null)started.countDown();if(release!=null)release.await();return result;}}
''')
 put('com/transiva/app/CustomerProfileCache.java',src.read_text())
 put('com/transiva/app/ProfileCacheRegression.java','''package com.transiva.app;
import android.content.*;import org.json.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class ProfileCacheRegression{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}static JSONObject user(String name)throws Exception{return new JSONObject().put("id",99).put("username",name).put("delivery_address","address").put("token","private").put("balance",900);}
 static void waitFor(CountDownLatch l)throws Exception{check(l.await(5,TimeUnit.SECONDS),"Callback timeout");}
 public static void main(String[] x)throws Exception{
  Context c=new Context();TransivaHttpRepository.result=new JSONObject().put("success",true).put("user",user("server"));
  TransivaHttpRepository.started=new CountDownLatch(1);TransivaHttpRepository.release=new CountDownLatch(1);CountDownLatch callbacks=new CountDownLatch(12);
  for(int i=0;i<12;i++)CustomerProfileCache.fetch(c,false,p->{check(p!=null,"Cold fetch missing");callbacks.countDown();});
  waitFor(TransivaHttpRepository.started);check(TransivaHttpRepository.calls.get()==1,"Duplicate cold fetch");TransivaHttpRepository.release.countDown();waitFor(callbacks);
  JSONObject p=CustomerProfileCache.cached(c);check(!p.has("token")&&!p.has("balance"),"Sensitive fields persisted");
  CustomerProfileCache.warm(c);check(TransivaHttpRepository.calls.get()==1,"Fresh cache fetched again");
  TransivaHttpRepository.started=null;TransivaHttpRepository.release=null;TransivaHttpRepository.result=new JSONObject().put("success",false);CountDownLatch failed=new CountDownLatch(1);CustomerProfileCache.fetch(c,true,v->{check(v==null,"Failed forced refresh reported stale cache as fresh");failed.countDown();});waitFor(failed);check(CustomerProfileCache.cached(c)!=null,"Failed refresh discarded cache");TransivaHttpRepository.result=new JSONObject().put("success",true).put("user",user("server"));
  android.os.Handler.queue=true;AtomicInteger guarded=new AtomicInteger();CustomerProfileCache.fetch(c,false,v->{check(v==null,"Fresh cache callback leaked after session switch");guarded.incrementAndGet();});SessionManager.token="switched";android.os.Handler.drain();check(guarded.get()==1,"Queued callback missing");SessionManager.token="tokenA";android.os.Handler.queue=false;
  SessionManager.id="100";check(CustomerProfileCache.cached(c)==null,"Account cache leak");SessionManager.id="99";SessionManager.token="tokenB";check(CustomerProfileCache.cached(c)==null,"Session cache leak");SessionManager.token="tokenA";
  TransivaHttpRepository.started=new CountDownLatch(1);TransivaHttpRepository.release=new CountDownLatch(1);CountDownLatch edit=new CountDownLatch(1);
  CustomerProfileCache.fetch(c,true,v->{check("edited".equals(v.optString("username")),"Old GET overwrote profile save");edit.countDown();});waitFor(TransivaHttpRepository.started);CustomerProfileCache.put(c,user("edited"));TransivaHttpRepository.release.countDown();waitFor(edit);
  CustomerProfileCache.confirmEmailVerified(c);
  check(CustomerProfileCache.cached(c).optInt("email_verified")==1,"Confirmed verification missing from cache");
  check(SessionManager.raw.optInt("email_verified")==1,"Confirmed verification missing from session");
  check("edited".equals(CustomerProfileCache.cached(c).optString("username")),"Verification discarded current profile");
  TransivaHttpRepository.started=new CountDownLatch(1);TransivaHttpRepository.release=new CountDownLatch(1);CountDownLatch verification=new CountDownLatch(1);
  CustomerProfileCache.fetch(c,true,v->{check(v.optInt("email_verified")==1,"Old GET undid verification");verification.countDown();});
  waitFor(TransivaHttpRepository.started);CustomerProfileCache.confirmEmailVerified(c);TransivaHttpRepository.release.countDown();waitFor(verification);
  TransivaHttpRepository.started=new CountDownLatch(1);TransivaHttpRepository.release=new CountDownLatch(1);CountDownLatch logout=new CountDownLatch(1);
  CustomerProfileCache.fetch(c,true,v->{check(v==null,"Late callback exposed old session");logout.countDown();});waitFor(TransivaHttpRepository.started);CustomerProfileCache.clear(c);SessionManager.login=false;TransivaHttpRepository.release.countDown();waitFor(logout);check(CustomerProfileCache.cached(c)==null,"Logout cache persists");
  DashboardReadExecutor.e.shutdownNow();System.out.println("PASS: cold request coalescing; fresh cache reuse; sensitive field filtering; account/session isolation; edit race; confirmed verification sync; stale GET after verification; logout late-response isolation");
 }
}
''')
 subprocess.run(['java','com.sun.tools.javac.Main','-cp',cp,'-d',str(r/'classes'),*[str(f) for f in r.rglob('*.java')]],check=True)
 subprocess.run(['java','-cp',str(r/'classes')+':'+cp,'com.transiva.app.ProfileCacheRegression'],check=True)
