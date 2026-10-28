package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Account-scoped, atomic catalog cache: bounded in bytes, entries and in-memory hot reads. */
public final class OfflineJsonCache {
    private static final long DEFAULT_TTL=600000L,MAX_STALE=86400000L,MAX_BYTES=20L*1024*1024;
    private static final int MAX_ENTRY=512*1024,MAX_ENTRIES=100;
    private static final LinkedHashMap<String,String> HOT=new LinkedHashMap<>(16,0.75f,true);
    private OfflineJsonCache(){}
    private static File folder(Context c){File f=new File(c.getApplicationContext().getCacheDir(),"catalog-json-v2");if(!f.isDirectory()&&!f.mkdirs())return null;return f;}
    public static void put(Context c,String key,JSONObject value){if(c!=null&&key!=null&&value!=null)putScoped(c,scoped(c,key),value);}
    static synchronized void putScoped(Context c,String ownerKey,JSONObject value){
        File folder=folder(c);if(folder==null)return;File temporary=null;
        try{
            String id=hash(ownerKey),raw=new JSONObject().put("at",System.currentTimeMillis()).put("value",value).toString();byte[] bytes=raw.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_ENTRY)return;
            temporary=File.createTempFile("write-",".tmp",folder);
            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes);out.getFD().sync();}
            File target=new File(folder,id+".json");if(!temporary.renameTo(target)){temporary.delete();return;}
            HOT.put(id,raw);while(HOT.size()>12)HOT.remove(HOT.keySet().iterator().next());prune(folder);
        }catch(Exception ignored){if(temporary!=null)temporary.delete();}
    }
    public static JSONObject get(Context c,String key){return get(c,key,DEFAULT_TTL,false);}
    public static JSONObject get(Context c,String key,long ttl,boolean stale){return c==null||key==null?null:getScoped(c,scoped(c,key),ttl,stale);}
    static synchronized JSONObject getScoped(Context c,String ownerKey,long ttl,boolean stale){
        try{
            File folder=folder(c);if(folder==null)return null;String id=hash(ownerKey),raw=HOT.get(id);File file=new File(folder,id+".json");
            if(raw==null){if(!file.isFile()||file.length()>MAX_ENTRY)return null;ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>MAX_ENTRY)return null;bytes.write(buffer,0,n);}}raw=new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
            JSONObject envelope=new JSONObject(raw);long at=envelope.optLong("at"),now=System.currentTimeMillis(),age=now-at;
            if(at<=0||age<0||age>(stale?MAX_STALE:Math.max(1000L,ttl)))return null;
            HOT.put(id,raw);while(HOT.size()>12)HOT.remove(HOT.keySet().iterator().next());file.setLastModified(now);
            JSONObject value=envelope.optJSONObject("value");return value==null?null:new JSONObject(value.toString());
        }catch(Exception ignored){return null;}
    }
    public static synchronized void remove(Context c,String key){if(c==null||key==null)return;String id=hash(scoped(c,key));HOT.remove(id);File f=folder(c);if(f!=null)new File(f,id+".json").delete();}
    static String scoped(Context c,String key){SessionManager s=new SessionManager(c.getApplicationContext());return s.getUserId()+":"+s.getToken()+":"+key;}
    private static void prune(File folder){File[] files=folder.listFiles((d,n)->n.endsWith(".json"));if(files==null)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long bytes=0;for(File f:files)bytes+=f.length();int count=files.length;for(File f:files){if(bytes<=MAX_BYTES&&count<=MAX_ENTRIES)break;long size=f.length();if(f.delete()){bytes-=size;count--;HOT.remove(f.getName().replace(".json",""));}}}
    private static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.US,"%02x",v));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
