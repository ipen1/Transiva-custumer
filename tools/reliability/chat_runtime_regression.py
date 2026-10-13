#!/usr/bin/env python3
"""Execute real callback gate and send journal with controlled Android/executor fixtures."""
from pathlib import Path
import tempfile,subprocess,os,shutil
root=Path(__file__).resolve().parent;src=root.parents[1]/'app/src/main/java/com/transiva/app'
with tempfile.TemporaryDirectory(prefix='transiva-chat-runtime-') as d:
 p=Path(d)
 def put(name,text):
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text)
 for name in ['android/content/Context.java','android/content/SharedPreferences.java','android/os/Looper.java']:
  f=p/name;f.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/'wallet-fixtures'/name,f)
 put('android/os/Handler.java','''package android.os;import java.util.*;public class Handler{static java.util.List<Runnable> queue=new ArrayList<>();public Handler(Looper l){}public boolean postDelayed(Runnable r,long delay){queue.add(r);return true;}public static void drain(){while(!queue.isEmpty())queue.remove(0).run();}}''')
 put('com/transiva/app/Collaborators.java','''package com.transiva.app;
import android.content.Context;import android.os.Handler;import java.util.*;
class SessionManager{static String account="1",token="token";SessionManager(Context c){}boolean isLoggedIn(){return true;}String getId(){return account;}String getUserId(){return account;}String getToken(){return token;}}
class ChatImageProcessor{static class ImagePayload{byte[] previewWebp,hdJpeg;}}
class CustomerApiClient{
 static boolean switchOnRead;static int handled;static java.net.HttpURLConnection last;
 static java.net.HttpURLConnection open(Context c,String endpoint)throws Exception{return last=new java.net.HttpURLConnection(new java.net.URL(endpoint)){
 public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
 public int getResponseCode(){return 401;}
 public java.io.InputStream getErrorStream(){if(switchOnRead)SessionManager.account="2";return new java.io.ByteArrayInputStream("{\\\"success\\\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public java.io.OutputStream getOutputStream(){return new java.io.ByteArrayOutputStream();}
 };}
 static void applySecurity(Context c,java.net.HttpURLConnection n){}
 static boolean handleSessionResponse(Context c,int code,String raw){handled++;return false;}
}
class CustomerFeatureRuntimeController{boolean destroyed,reject;List<Runnable> work=new ArrayList<>();void execute(Runnable r,Runnable failed){if(reject){failed.run();return;}work.add(r);}boolean isDestroyed(){return destroyed;}boolean postDelayed(Handler h,Runnable r,long d){return h.postDelayed(r,d);}void run(){work.remove(0).run();}}
''')
 put('com/transiva/app/ChatRuntimeCheck.java','''package com.transiva.app;
import android.content.*;import android.os.*;import org.json.*;
public class ChatRuntimeCheck{
 static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception{
 Context c=new Context();Handler h=new Handler(Looper.getMainLooper());CustomerFeatureRuntimeController r=new CustomerFeatureRuntimeController();CustomerChatRequestGate gate=new CustomerChatRequestGate(c,r);int[] calls={0};
 gate.execute("read",()->gate.post(h,()->calls[0]++),null);r.run();gate.execute("read",()->gate.post(h,()->calls[0]+=10),null);r.run();Handler.drain();check(calls[0]==10,"superseded read callback ignored");
 gate.execute("read",()->gate.post(h,()->calls[0]++),null);r.run();SessionManager.account="2";Handler.drain();check(calls[0]==10,"queued callback after account switch ignored");SessionManager.account="1";
 gate.execute("send",()->calls[0]++,null);SessionManager.token="new-token";r.run();check(calls[0]==10,"queued send after token switch never runs");SessionManager.token="token";
 r.reject=true;int[] busy={1};gate.execute("send",()->{throw new AssertionError();},()->gate.post(h,()->busy[0]=0));Handler.drain();check(busy[0]==0,"rejected send resets busy without executing request");
 for(String lane:new String[]{"read","media"}){busy[0]=1;gate.execute(lane,()->{throw new AssertionError();},()->gate.post(h,()->busy[0]=0));Handler.drain();check(busy[0]==0,"rejected "+lane+" resets busy");}r.reject=false;
 gate.execute("send",()->gate.post(h,()->gate.postDelayed(h,()->calls[0]++,10)),null);r.run();gate.close();Handler.drain();check(calls[0]==10,"destroyed screen cannot deliver UI callbacks");
 CustomerChatSendJournal journal=new CustomerChatSendJournal(c,CustomerSessionGuard.capture(c),"room-A");JSONObject payload=new JSONObject().put("room_id","ROOM-A").put("message","hello");
 JSONObject first=journal.begin(payload);String id=first.getString("client_message_id");payload.put("message","edited");check("hello".equals(journal.pending().getString("message")),"journal snapshots payload before UI edits");
 CustomerChatSendJournal recreated=new CustomerChatSendJournal(c,CustomerSessionGuard.capture(c),"room-A");check(id.equals(recreated.begin(new JSONObject().put("message","hello").put("room_id","ROOM-A")).getString("client_message_id")),"retry and recreated screen preserve ID");
 boolean blocked=false;try{journal.begin(payload);}catch(Exception expected){blocked=true;}check(blocked,"different draft cannot overwrite ambiguous send");
 Context.rejectCommit=true;blocked=false;try{journal.begin(new JSONObject(first.toString()));}catch(Exception expected){blocked=true;}check(blocked,"retry must durably persist before dispatch");Context.rejectCommit=false;
 journal.resolve("unrelated");check(journal.pending()!=null,"unrelated result cannot clear pending message");
 Context.rejectCommit=true;blocked=false;try{journal.resolve(id);}catch(Exception expected){blocked=true;}Context.rejectCommit=false;check(blocked&&journal.pending()!=null,"failed clear restores replay marker");
 SessionManager.account="2";check(new CustomerChatSendJournal(c,CustomerSessionGuard.capture(c),"room-A").pending()==null,"other account cannot read pending message");SessionManager.account="1";
 journal.resolve(id);check(journal.pending()==null,"confirmed matching send clears pending marker");check(!id.equals(journal.begin(new JSONObject(first.toString())).getString("client_message_id")),"new intentional identical message gets new ID");
 CustomerMessageApi.initialize(c);CustomerSessionGuard pinned=CustomerSessionGuard.capture(c);
 CustomerApiClient.switchOnRead=true;blocked=false;try{CustomerMessageApi.get("https://transiva.my.id/server/getChat.php",pinned);}catch(Exception expected){blocked=true;}
 check(blocked&&CustomerApiClient.handled==0,"late old-session 401 cannot invalidate new account");SessionManager.account="1";CustomerApiClient.switchOnRead=false;
 CustomerMessageApi.get("https://transiva.my.id/server/getChat.php",pinned);
 check(CustomerApiClient.handled==1,"current-session response reaches session handler");
 check("Bearer token".equals(CustomerApiClient.last.getRequestProperty("Authorization")),"chat transport pins captured bearer");
 check(!CustomerApiClient.last.getInstanceFollowRedirects(),"chat transport never forwards bearer through redirects");
 System.out.println("PASS "+checks+" chat runtime scenarios");
 }}''')
 files=list(p.rglob('*.java'))+[src/(n+'.java') for n in ['CustomerChatRequestGate','CustomerChatSendJournal','CustomerSessionGuard','CustomerReadSafetyRules','NetworkSafetyRules','OrderPayloadFingerprint','CustomerMessageApi','BoundedResponseReader']]
 subprocess.run([os.getenv('JAVAC_BIN','javac'),'-cp',os.environ['JSON_JAR'],'-d',str(p/'classes'),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(p/'classes')+os.pathsep+os.environ['JSON_JAR'],'com.transiva.app.ChatRuntimeCheck'],check=True)
