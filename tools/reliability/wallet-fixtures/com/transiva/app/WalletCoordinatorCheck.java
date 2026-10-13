package com.transiva.app;
import android.content.Context;import android.os.Looper;import org.json.JSONObject;
public class WalletCoordinatorCheck {
 static int checks;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static void flush(){Looper.markMain(false);TransivaNetworkExecutor.pending.run();Looper.markMain(true);}
 static JSONObject pending(Context c)throws Exception{Looper.markMain(false);JSONObject p=new CustomerWalletOperationJournal(c,"1").pending();Looper.markMain(true);return p;}
 static class Transport implements CustomerWalletCoordinator.Transport {
  int mutations,status;String execute="unknown",lookup="not_found";boolean throwNetwork;
  public JSONObject post(Context c,CustomerSessionGuard guard,String endpoint,JSONObject payload)throws Exception {
   if(Looper.myLooper()==Looper.getMainLooper())throw new AssertionError("Network on UI");
   if(throwNetwork)throw new java.io.IOException("fixture");
   if(CustomerWalletCoordinator.STATUS.equals(endpoint)){
    status++;String id=payload.optString("operation_id");
    if("wrong_id".equals(lookup))return new JSONObject().put("success",true).put("operation_id","wrong").put("operation_state","succeeded").put("terminal",true);
    JSONObject out=new JSONObject().put("success",true).put("operation_id",id).put("operation_state",lookup);
    boolean terminal="succeeded".equals(lookup)||"rejected".equals(lookup);out.put("terminal",terminal);
    if(terminal)out.put("receipt",receipt(id,"succeeded".equals(lookup)));return out;
   }
   mutations++;String id=payload.optString("idempotency_key",payload.optString("request_id"));
   if("succeeded".equals(execute)||"rejected".equals(execute))return receipt(id,"succeeded".equals(execute));
   if("legacy_rejected".equals(execute))return new JSONObject().put("success",false).put("operation_state","rejected");
   return new JSONObject().put("success",false).put("operation_state","unknown");
  }
  JSONObject receipt(String id,boolean success)throws Exception{return new JSONObject().put("success",success).put("committed",success).put("terminal",true).put("operation_id",id).put("operation_state",success?"succeeded":"rejected").put("message","fixture");}
 }
 public static void main(String[] args)throws Exception {
  SessionManager.account="1";SessionManager.token="token";Context c=new Context();Transport t=new Transport();
  CustomerWalletCoordinator coordinator=new CustomerWalletCoordinator(c,"1",t);
  CustomerWalletCoordinator.Result[] result={null};CustomerWalletCoordinator.Callback callback=r->result[0]=r;
  Looper.markMain(true);coordinator.start("withdraw",new JSONObject().put("amount",10000),callback);
  check(t.mutations==0&&result[0]==null,"UI start schedules work without journal or network execution");
  CustomerWalletCoordinator other=new CustomerWalletCoordinator(c,"1",t);other.inspect(callback);
  check("busy".equals(result[0].state),"account lease blocks a second coordinator during queued work");
  flush();JSONObject op=pending(c);check(op!=null&&t.mutations==1&&"unknown".equals(result[0].state),"ambiguous response preserves durable intent");
  int before=t.mutations;coordinator.check(op,callback);flush();
  check("not_found".equals(result[0].state)&&t.mutations==before&&t.status==1&&pending(c)!=null,"status not_found never executes or clears intent");
  t.lookup="processing";coordinator.check(op,callback);flush();
  check("processing".equals(result[0].state)&&pending(c)!=null&&t.mutations==before,"processing status preserves marker without mutation");
  t.lookup="wrong_id";coordinator.check(op,callback);flush();check("unknown".equals(result[0].state)&&pending(c)!=null,"mismatched status ID cannot clear journal");
  t.execute="legacy_rejected";coordinator.retry(op,callback);flush();check("unknown".equals(result[0].state)&&pending(c)!=null,"unproved rejection cannot clear journal");
  t.lookup="rejected";coordinator.check(op,callback);flush();check("rejected".equals(result[0].state)&&pending(c)==null,"durable terminal rejection clears matching journal");
  coordinator.start("withdraw",new JSONObject().put("amount",10000),callback);flush();op=pending(c);
  t.lookup="succeeded";coordinator.check(op,callback);flush();check("succeeded".equals(result[0].state)&&pending(c)==null,"read-only confirmed success clears journal");
  t.execute="unknown";coordinator.start("withdraw",new JSONObject().put("amount",10000),callback);flush();op=pending(c);
  before=t.mutations;t.execute="succeeded";coordinator.retry(op,callback);check(t.mutations==before,"explicit resend is also queued off UI");flush();
  check(t.mutations==before+1&&"succeeded".equals(result[0].state)&&pending(c)==null,"explicit confirmed retry sends original ID and resolves result");
  Context.rejectCommit=true;before=t.mutations;coordinator.start("withdraw",new JSONObject().put("amount",10000),callback);flush();
  check("unknown".equals(result[0].state)&&t.mutations==before,"disk failure prevents request dispatch");Context.rejectCommit=false;
  coordinator.inspect(callback);flush();check(result[0].operation!=null,"failed persist remains recoverable if marker exists in memory");
  op=pending(c);coordinator.check(op,callback);TransivaNetworkExecutor.reject();
  check("busy".equals(result[0].state)&&pending(c)!=null,"executor rejection releases busy lease and retains intent");
  t.lookup="succeeded";coordinator.check(op,callback);flush();check(pending(c)==null,"next status check can run after executor rejection");
  SessionManager.account="2";before=t.mutations;coordinator.start("withdraw",new JSONObject().put("amount",10000),callback);flush();
  check("unknown".equals(result[0].state)&&t.mutations==before,"account switch blocks queued operation before persistence/network");
  System.out.println("PASS "+checks+" background coordinator fixture scenarios");
 }
}
