package com.transiva.app;

import android.content.Context;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Durable, account-scoped pending operations. No token is written to disk. */
final class CustomerPendingChatStore {
    private static final Object LOCK=new Object();
    private final File directory;
    private final Context app;
    private final CustomerSessionGuard session;
    CustomerPendingChatStore(Context context,CustomerSessionGuard guard)throws Exception {
        app=context.getApplicationContext();session=guard;
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Storage requires background execution");
        if(!session.matches(app)||session.account.isEmpty())throw new IOException("Sesi berubah");
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(session.account.getBytes("UTF-8"));
        StringBuilder key=new StringBuilder();for(byte b:hash)key.append(String.format(Locale.US,"%02x",b&255));
        directory=new File(app.getFilesDir(),"pending_chat/"+key);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Penyimpanan pesan belum siap");
    }
    private File file(String id,String suffix)throws IOException {
        if(!id.matches("ccm-[A-Za-z0-9-]{16,76}"))throw new IOException("ID pesan tidak valid");
        return new File(directory,id+suffix);
    }
    private static void write(File target,byte[] bytes)throws Exception {
        File temp=new File(target.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}
        if(!temp.renameTo(target))throw new IOException("Pesan belum tersimpan");
    }
    static byte[] read(File file,int max)throws Exception {
        if(file.length()>max)throw new IOException("Lampiran terlalu besar");
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("Lampiran terlalu besar");out.write(buf,0,n);}return out.toByteArray();
        }
    }
    JSONObject enqueue(JSONObject payload,String kind,byte[] preview,byte[] media,long duration)throws Exception {synchronized(LOCK){
        if(!session.matches(app))throw new IOException("Sesi berubah");
        if(list().size()>=50)throw new IOException("Antrean pesan penuh. Selesaikan pesan tertunda dahulu.");
        File[] existing=directory.listFiles();long total=0;if(existing!=null)for(File f:existing)total+=f.length();
        if(total+(preview==null?0:preview.length)+(media==null?0:media.length)>150L*1024*1024)throw new IOException("Penyimpanan lampiran tertunda penuh");
        JSONObject op=new JSONObject(payload.toString());
        String id=op.optString("client_message_id");if(id.isEmpty())id="ccm-"+UUID.randomUUID();
        if(file(id,".json").exists())return new JSONObject(new String(read(file(id,".json"),65536),"UTF-8"));
        op.put("client_message_id",id).put("operation_id",id).put("kind",kind).put("duration_ms",duration).put("queued_at",System.currentTimeMillis());
        if(preview!=null)write(file(id,".preview"),preview);
        if(media!=null)write(file(id,".media"),media);
        write(file(id,".json"),op.toString().getBytes("UTF-8"));return op;
    }}
    List<JSONObject> list()throws Exception {synchronized(LOCK){
        List<JSONObject> result=new ArrayList<>();File[] files=directory.listFiles();
        if(files!=null)for(File f:files)if(f.getName().endsWith(".json"))result.add(new JSONObject(new String(read(f,65536),"UTF-8")));
        Collections.sort(result,(a,b)->Long.compare(a.optLong("queued_at"),b.optLong("queued_at")));return result;
    }}
    File attachment(JSONObject op)throws Exception {return file(op.getString("client_message_id"),"image".equals(op.optString("kind"))?".preview":".media");}
    JSONObject dispatch(JSONObject op)throws Exception {
        if(!session.matches(app))throw new IOException("Sesi berubah");
        String id=op.getString("client_message_id"),kind=op.optString("kind","text");JSONObject response;
        boolean attempted;
        synchronized(LOCK){
            File metadata=file(id,".json");
            if(metadata.exists()){JSONObject current=new JSONObject(new String(read(metadata,65536),"UTF-8"));attempted=current.optBoolean("attempted");current.put("attempted",true);write(metadata,current.toString().getBytes("UTF-8"));op.put("attempted",true);}
            else attempted=true;
        }
        response=attempted?CustomerMessageApi.get(ApiConfig.server("chat_operation_status.php")+"?operation_id="+java.net.URLEncoder.encode(id,"UTF-8"),session):new JSONObject().put("success",false).put("operation_state","not_found");
        if(!response.optBoolean("success")&&!"not_found".equals(response.optString("operation_state")))throw new IOException(response.optString("message","Status pesan belum tersedia"));
        if(!response.optBoolean("success")){
        if("image".equals(kind))response=CustomerMessageApi.uploadPendingMedia(ApiConfig.server("upload_chat_image.php"),op,read(file(id,".preview"),2*1024*1024),read(file(id,".media"),8*1024*1024),session);
        else if("voice".equals(kind))response=CustomerMessageApi.uploadPendingMedia(ApiConfig.server("upload_chat_voice.php"),op,null,read(file(id,".media"),12*1024*1024),session);
        else response=CustomerMessageApi.post(ApiConfig.server("sendChat.php"),op,session);
        }
        if(response.optBoolean("success")){
            if(!id.equals(response.optString("client_message_id",response.optString("operation_id"))))throw new IOException("Server belum mengonfirmasi ID pesan. Perbarui server dahulu.");
            synchronized(LOCK){if(!file(id,".json").delete()&&file(id,".json").exists())throw new IOException("Pesan terkirim; penanda belum dibersihkan. Retry tetap aman.");file(id,".preview").delete();file(id,".media").delete();}
        }
        return response;
    }
}
