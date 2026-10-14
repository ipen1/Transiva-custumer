#!/usr/bin/env python3
"""Exercise production persistent queue using filesystem and controlled Android/API fixtures."""
from pathlib import Path
import os,subprocess,tempfile,shutil
root=Path(__file__).resolve().parent;src=root.parents[1]/'app/src/main/java/com/transiva/app'
with tempfile.TemporaryDirectory(prefix='transiva-pending-runtime-') as d:
 p=Path(d)
 def put(name,text):
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 for name in ['android/content/SharedPreferences.java','android/os/Looper.java']:
  target=p/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/'wallet-fixtures'/name,target)
 text=(root/'wallet-fixtures/android/content/Context.java').read_text().replace(' public Context getApplicationContext()', ' public java.io.File getFilesDir(){return new java.io.File(System.getProperty("queue.root"));}\n public Context getApplicationContext()')
 put('android/content/Context.java',text)
 put('com/transiva/app/Collaborators.java',r'''package com.transiva.app;
import android.content.*;import org.json.*;import java.util.*;
class SessionManager{static String account="1",token="token";SessionManager(Context c){}boolean isLoggedIn(){return true;}String getId(){return account;}String getUserId(){return account;}String getToken(){return token;}}
class ApiConfig{static String server(String n){return "https://transiva.my.id/server/"+n;}}
class CustomerMessageApi{
 static boolean offline,wrongAck;static int writes;static Map<String,JSONObject> receipts=new HashMap<>();
 static JSONObject get(String url,CustomerSessionGuard s)throws Exception{if(offline)throw new java.io.IOException("offline");String id=java.net.URLDecoder.decode(url.substring(url.indexOf("operation_id=")+13),"UTF-8");JSONObject receipt=receipts.get(id);return receipt==null?new JSONObject().put("success",false).put("operation_state","not_found"):new JSONObject(receipt.toString());}
 static JSONObject post(String url,JSONObject op,CustomerSessionGuard s)throws Exception{if(offline)throw new java.io.IOException("offline");String id=op.getString("client_message_id");JSONObject ack=new JSONObject().put("success",true).put("client_message_id",wrongAck?"unrelated":id);receipts.put(id,ack);writes++;return ack;}
 static JSONObject uploadPendingMedia(String url,JSONObject op,byte[] preview,byte[] media,CustomerSessionGuard s)throws Exception{if(media.length==0)throw new AssertionError("empty media");return post(url,op,s);}
}
''')
 put('com/transiva/app/PendingRuntimeCheck.java',r'''package com.transiva.app;
import android.content.*;import android.os.*;import org.json.*;import java.util.*;
public class PendingRuntimeCheck{
 static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static JSONObject base(String msg)throws Exception{return new JSONObject().put("room_id","ROOM-A").put("order_id","A").put("source","orders").put("message",msg);}
 public static void main(String[] args)throws Exception{
 Context context=new Context();CustomerSessionGuard session=CustomerSessionGuard.capture(context);CustomerPendingChatStore store=new CustomerPendingChatStore(context,session);
 JSONObject text=store.enqueue(base("original"),"text",null,null,0);String id=text.getString("client_message_id");
 CustomerPendingChatStore reopened=new CustomerPendingChatStore(new Context(),session);check(reopened.list().size()==1&&reopened.list().get(0).getString("message").equals("original"),"text survives store recreation without token persistence");
 byte[] preview={1,2},photo={3,4,5};JSONObject image=store.enqueue(base(""),"image",preview,photo,0);preview[0]=9;photo[0]=9;check(CustomerPendingChatStore.read(store.attachment(image),20)[0]==1,"photo snapshot is independent of later source edits");
 JSONObject voice=store.enqueue(base(""),"voice",null,new byte[]{5,6,7},3000);check(store.attachment(voice).isFile()&&voice.getLong("duration_ms")==3000,"voice is durable before source recording is deleted");
 CustomerMessageApi.offline=true;boolean failed=false;try{store.dispatch(image);}catch(Exception expected){failed=true;}check(failed&&store.list().size()==3&&store.attachment(image).isFile(),"network failure retains photo and original operation ID");CustomerMessageApi.offline=false;
 SessionManager.account="2";CustomerPendingChatStore other=new CustomerPendingChatStore(context,CustomerSessionGuard.capture(context));check(other.list().isEmpty(),"other account cannot enumerate pending operations");failed=false;try{store.dispatch(text);}catch(Exception expected){failed=true;}check(failed&&CustomerMessageApi.writes==0,"old session cannot send pending operations as new account");SessionManager.account="1";
 CustomerMessageApi.wrongAck=true;failed=false;try{store.dispatch(text);}catch(Exception expected){failed=true;}check(failed&&store.list().size()==3,"unrelated server acknowledgment never clears pending message");CustomerMessageApi.wrongAck=false;CustomerMessageApi.receipts.clear();
 CustomerMessageApi.receipts.put(id,new JSONObject().put("success",true).put("client_message_id",id));int writes=CustomerMessageApi.writes;store.dispatch(text);check(CustomerMessageApi.writes==writes&&store.list().size()==2,"lost response is resolved by read-only status without resending");
 store.dispatch(image);check(!store.attachment(image).exists()&&store.list().size()==1,"confirmed photo clears metadata and copied attachment");store.dispatch(voice);check(store.list().isEmpty(),"confirmed voice clears pending operation");
 JSONObject duplicate=store.enqueue(base("original"),"text",null,null,0);check(!duplicate.getString("client_message_id").equals(id),"new intentional identical text gets a fresh operation ID");
 Looper.markMain(true);failed=false;try{new CustomerPendingChatStore(context,session);}catch(Exception expected){failed=true;}Looper.markMain(false);check(failed,"pending I/O is rejected on main thread");
 java.io.File bad=new java.io.File(System.getProperty("queue.root"),"limit");try(java.io.FileOutputStream out=new java.io.FileOutputStream(bad)){out.write(new byte[100]);}failed=false;try{CustomerPendingChatStore.read(bad,50);}catch(Exception expected){failed=true;}check(failed,"attachment reader enforces byte limit");
 System.out.println("PASS "+checks+" persistent pending queue scenarios");
 }
}
''')
 classes=p/'classes';files=list(p.rglob('*.java'))+[src/(name+'.java') for name in ['CustomerPendingChatStore','CustomerSessionGuard','CustomerReadSafetyRules','NetworkSafetyRules']]
 subprocess.run([os.getenv('JAVAC_BIN','javac'),'-cp',os.environ['JSON_JAR'],'-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-Dqueue.root='+str(p/'pending-files'),'-cp',str(classes)+os.pathsep+os.environ['JSON_JAR'],'com.transiva.app.PendingRuntimeCheck'],check=True)
