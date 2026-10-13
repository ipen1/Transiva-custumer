package com.transiva.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.ConcurrentHashMap;

/** Serializes money intents per account; durable storage and network always run in the executor. */
final class CustomerWalletCoordinator {
    interface Transport { JSONObject post(Context app, CustomerSessionGuard session, String endpoint, JSONObject payload) throws Exception; }
    interface Callback { void onResult(Result result); }
    private interface Work { Result run(CustomerWalletOperationJournal journal, CustomerSessionGuard session) throws Exception; }
    static final String BASE="https://transiva.my.id/server/";
    static final String STATUS=BASE+"customer_wallet_operation_status.php";
    private static final ConcurrentHashMap<String,Object> LEASES=new ConcurrentHashMap<>();
    private final Context app;
    private final String account;
    private final Transport transport;
    private final Handler main=new Handler(Looper.getMainLooper());
    static final class Result {
        final String state, message;
        final JSONObject operation, receipt;
        Result(String state,String message,JSONObject operation,JSONObject receipt){this.state=state;this.message=message;this.operation=operation;this.receipt=receipt;}
    }
    CustomerWalletCoordinator(Context context,String account){this(context,account,new CustomerWalletTransport());}
    CustomerWalletCoordinator(Context context,String account,Transport transport){this.app=context.getApplicationContext();this.account=account;this.transport=transport;}
    void inspect(Callback callback){run((journal,session)->new Result("pending","",journal.pending(),null),callback);}
    void start(String action,JSONObject payload,Callback callback){
        final JSONObject copy;
        try{copy=new JSONObject(payload.toString());}catch(Exception error){deliver(callback,unknown());return;}
        run((journal,session)->{
            JSONObject pending=journal.pending();
            if(pending!=null)return new Result("pending","Periksa operasi sebelumnya.",pending,null);
            return execute(journal,session,journal.begin(action,copy));
        },callback);
    }
    /** Status endpoint only. not_found never clears the intent and never invokes execute. */
    void check(JSONObject operation,Callback callback){
        final String id=operation.optString("id","");
        run((journal,session)->{
            JSONObject current=current(journal,id);
            JSONObject query=new JSONObject().put("action",current.optString("action")).put("operation_id",id).put("operation_owner_id",account);
            JSONObject response=transport.post(app,session,STATUS,query);
            if(!response.optBoolean("success")||!id.equals(response.optString("operation_id")))return unknown();
            String state=response.optString("operation_state","unknown");JSONObject receipt=response.optJSONObject("receipt");
            if(("succeeded".equals(state)||"rejected".equals(state))&&response.optBoolean("terminal")
                    &&receipt!=null&&state.equals(receipt.optString("operation_state")))
                return terminal(journal,session,current,receipt);
            if(!("not_found".equals(state)||"processing".equals(state)))state="unknown";
            return new Result(state,"Status belum final.",current,null);
        },callback);
    }
    /** Called only after a separate explicit resend confirmation; the ID and payload are unchanged. */
    void retry(JSONObject operation,Callback callback){
        final String id=operation.optString("id","");
        run((journal,session)->execute(journal,session,current(journal,id)),callback);
    }
    private JSONObject current(CustomerWalletOperationJournal journal,String id)throws Exception {
        JSONObject current=journal.pending();
        if(current==null||id.isEmpty()||!id.equals(current.optString("id")))throw new IllegalStateException("Operasi telah berubah. Periksa kembali.");
        return current;
    }
    private Result execute(CustomerWalletOperationJournal journal,CustomerSessionGuard session,JSONObject operation)throws Exception {
        String id=operation.optString("id"),action=operation.optString("action");JSONObject payload=operation.optJSONObject("payload");
        if(payload==null||!("withdraw".equals(action)||"transfer".equals(action))
                ||!account.equals(payload.optString("operation_owner_id"))
                ||!id.equals(payload.optString("transfer".equals(action)?"request_id":"idempotency_key")))
            throw new IllegalStateException("Data operasi tidak valid. Hubungi bantuan.");
        journal.prepareSend(id);
        JSONObject response=transport.post(app,session,BASE+"customer_wallet_"+action+".php",payload);
        return terminal(journal,session,operation,response);
    }
    private Result terminal(CustomerWalletOperationJournal journal,CustomerSessionGuard session,JSONObject operation,JSONObject receipt)throws Exception {
        String id=operation.optString("id");
        if(receipt==null||!id.equals(receipt.optString("operation_id"))||!receipt.optBoolean("terminal"))return unknown();
        boolean succeeded=receipt.optBoolean("success")&&receipt.optBoolean("committed")&&"succeeded".equals(receipt.optString("operation_state"));
        boolean rejected=!receipt.optBoolean("success")&&!receipt.optBoolean("committed")&&"rejected".equals(receipt.optString("operation_state"));
        if(!succeeded&&!rejected)return unknown();
        if(!session.matches(app))throw new java.io.InterruptedIOException("Session changed");
        journal.resolve(id);
        return new Result(succeeded?"succeeded":"rejected",receipt.optString("message","Operasi selesai."),operation,receipt);
    }
    private void run(Work work,Callback callback){
        Object lease=new Object();
        if(LEASES.putIfAbsent(account,lease)!=null){deliver(callback,new Result("busy","Operasi wallet masih berjalan. Coba kembali sebentar lagi.",null,null));return;}
        TransivaNetworkExecutor.execute(()->{
            Result result;
            try {
                CustomerSessionGuard session=CustomerSessionGuard.capture(app);
                if(!account.equals(session.account)||!session.matches(app))throw new IllegalStateException("Sesi berubah.");
                CustomerWalletOperationJournal journal=new CustomerWalletOperationJournal(app,account);
                result=work.run(journal,session);
            } catch(Exception error){result=unknown();}
            finally{LEASES.remove(account,lease);}
            deliver(callback,result);
        },()->{LEASES.remove(account,lease);deliver(callback,new Result("busy","Aplikasi sedang sibuk. Operasi tetap disimpan jika sebelumnya sudah dibuat.",null,null));});
    }
    private static Result unknown(){return new Result("unknown","Hasil belum terkonfirmasi. Periksa status operasi yang sama; jangan membuat ID baru.",null,null);}
    private void deliver(Callback callback,Result result){if(callback!=null)main.post(()->callback.onResult(result));}
}
