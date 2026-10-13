package com.transiva.app;
import android.content.Context;import org.json.JSONObject;import java.net.*;
public class WalletRuntimeCheck {
 static int checks;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception {
  Context context=new Context();android.os.Looper.markMain(true);boolean forbidden=false;
  try{new CustomerWalletOperationJournal(context,"1");}catch(IllegalStateException expected){forbidden=true;}
  check(forbidden,"journal construction and disk reads forbidden on UI thread");android.os.Looper.markMain(false);
  CustomerWalletOperationJournal a=new CustomerWalletOperationJournal(context,"1");
  JSONObject original=new JSONObject().put("amount",10000);JSONObject op=a.begin("withdraw",original);String id=op.getString("id");
  check(id.equals(op.getJSONObject("payload").getString("idempotency_key"))&&"1".equals(op.getJSONObject("payload").getString("operation_owner_id")),"intent gets stable key and authenticated owner");
  original.put("amount",999);check(a.pending().getJSONObject("payload").getInt("amount")==10000,"persisted request immutable after UI edits");
  check(new CustomerWalletOperationJournal(context,"1").pending().getString("id").equals(id),"recreated journal retains same intent");
  a.prepareSend(id);check(a.pending().getString("id").equals(id),"recovery send persists the original intent again");
  Context.rejectCommit=true;boolean refused=false;try{a.prepareSend(id);}catch(IllegalStateException expected){refused=true;}check(refused,"retry cannot send without durable journal");Context.rejectCommit=false;
  check(new CustomerWalletOperationJournal(context,"2").pending()==null,"other account cannot read pending operation");
  boolean blocked=false;try{a.begin("withdraw",new JSONObject());}catch(IllegalStateException expected){blocked=true;}check(blocked&&a.pending().getString("id").equals(id),"new intent blocked while result unknown");
  a.resolve("different");check(a.pending()!=null,"late unrelated result cannot erase pending operation");
  Context.rejectCommit=true;blocked=false;try{a.resolve(id);}catch(IllegalStateException expected){blocked=true;}check(blocked&&a.pending()!=null,"failed completion persistence retains replay marker");
  Context.rejectCommit=false;a.resolve(id);check(a.pending()==null,"confirmed matching result clears intent");
  Context.rejectCommit=true;blocked=false;try{a.begin("withdraw",new JSONObject());}catch(IllegalStateException expected){blocked=true;}check(blocked,"failed initial persistence prevents send");Context.rejectCommit=false;a.resolve(a.pending().getString("id"));
  JSONObject transfer=a.begin("transfer",new JSONObject().put("request_id","quote-intent"));check(transfer.getString("id").equals("quote-intent"),"transfer preserves signed quote request ID");a.resolve("quote-intent");
  CustomerWalletOperationJournal c1=new CustomerWalletOperationJournal(context,"3"),c2=new CustomerWalletOperationJournal(context,"3");
  java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger created=new java.util.concurrent.atomic.AtomicInteger();
  Thread t1=new Thread(()->{try{gate.await();c1.begin("withdraw",new JSONObject());created.incrementAndGet();}catch(Exception expected){}});
  Thread t2=new Thread(()->{try{gate.await();c2.begin("withdraw",new JSONObject());created.incrementAndGet();}catch(Exception expected){}});
  t1.start();t2.start();gate.countDown();t1.join();t2.join();check(created.get()==1,"two journal instances cannot create competing intents");
  context.getSharedPreferences("transiva_wallet_operation_1",0).edit().putString("pending","bad-json").commit();blocked=false;try{a.pending();}catch(Exception expected){blocked=true;}check(blocked,"corrupt journal fails closed without new debit");
  CustomerSessionGuard guard=CustomerSessionGuard.capture(context);
  HttpURLConnection connection=new HttpURLConnection(new URL("https://transiva.my.id/server/customer_wallet_withdraw.php")){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}};
  connection.setRequestProperty("Authorization","Bearer another-session");guard.authorize(context,connection);
  check("Bearer old-token".equals(connection.getRequestProperty("Authorization")),"money request pins captured bearer");
  HttpURLConnection foreign=new HttpURLConnection(new URL("https://example.test/")){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}};
  blocked=false;try{guard.authorize(context,foreign);}catch(java.io.IOException expected){blocked=true;}check(blocked,"captured bearer is never applied to foreign endpoint");
  SessionManager.token="new-token";blocked=false;try{guard.authorize(context,connection);}catch(java.io.IOException expected){blocked=true;}check(blocked,"session switch before send is rejected");
  CustomerServerConnectionMonitor monitor=CustomerServerConnectionMonitor.shared();String[] first={""},second={""};
  CustomerServerConnectionMonitor.Listener l1=(c,m)->first[0]=c,l2=(c,m)->second[0]=c;
  monitor.subscribe(context,l1);monitor.subscribe(context,l2);check(TransivaNetworkExecutor.submissions==1,"two visible indicators share one probe");
  TransivaNetworkExecutor.reject();check("#EF4444".equals(first[0])&&first[0].equals(second[0]),"probe result broadcasts consistently");
  monitor.unsubscribe(l1);monitor.unsubscribe(l2);monitor.subscribe(context,l1);
  check(TransivaNetworkExecutor.submissions==1&&"#EF4444".equals(first[0]),"tab switch reuses fresh server snapshot");
  monitor.refresh();check(TransivaNetworkExecutor.submissions==2,"manual refresh starts only one new probe");
  android.os.Handler.advance(12000);check(TransivaNetworkExecutor.pending.isCancelled()&&"#EF4444".equals(first[0]),"probe deadline cancels queued work and marks disconnected");
  TransivaNetworkMonitor.update(TransivaNetworkMonitor.State.OFFLINE);int before=TransivaNetworkExecutor.submissions;monitor.refresh();check(before==TransivaNetworkExecutor.submissions,"offline status does not enqueue network probe");
  monitor.unsubscribe(l1);check(TransivaNetworkMonitor.listeners.isEmpty(),"hidden indicators release network subscription");
  System.out.println("PASS "+checks+" wallet/connection fixture scenarios");
 }
}
