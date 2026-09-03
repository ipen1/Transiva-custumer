package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.*;
import java.security.MessageDigest;

/** Memory + disk image cache. Disk I/O is intentionally exposed separately so UI callers can stay non-blocking. */
public final class ImageMemoryDiskCache {
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>((int)Math.max(8,Runtime.getRuntime().maxMemory()/1024/12)){
        @Override protected int sizeOf(String k,Bitmap b){ return b==null?1:Math.max(1,b.getByteCount()/1024); }
    };
    private ImageMemoryDiskCache(){}

    public static Bitmap getMemory(String key){
        if(key==null)return null;
        Bitmap b=MEMORY.get(key);
        return b!=null&&!b.isRecycled()?b:null;
    }

    public static Bitmap getDisk(Context c,String key){
        if(c==null||key==null)return null;
        Bitmap memory=getMemory(key);
        if(memory!=null)return memory;
        File f=file(c,key);
        if(!f.exists())return null;
        Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());
        if(b!=null)MEMORY.put(key,b);
        try{f.setLastModified(System.currentTimeMillis());}catch(Throwable ignored){}
        return b;
    }

    /** Compatibility method. Do not call from the Android main thread. */
    public static Bitmap get(Context c,String key){ Bitmap b=getMemory(key); return b!=null?b:getDisk(c,key); }

    public static void put(Context c,String key,Bitmap b){
        if(c==null||key==null||b==null||b.isRecycled())return;
        MEMORY.put(key,b);
        final Context app=c.getApplicationContext();
        TransivaImageExecutor.execute(() -> {
            File f=file(app,key);
            if(f.exists())return;
            try(FileOutputStream o=new FileOutputStream(f)){ b.compress(Bitmap.CompressFormat.WEBP_LOSSY,86,o); }catch(Throwable ignored){}
            trim(app);
        });
    }

    private static File file(Context c,String k){ File d=new File(c.getCacheDir(),"img_v2"); if(!d.exists())d.mkdirs(); return new File(d,sha(k)+".webp"); }
    private static void trim(Context c){ File d=new File(c.getCacheDir(),"img_v2"); File[] fs=d.listFiles(); if(fs==null||fs.length<120)return; java.util.Arrays.sort(fs,(a,b)->Long.compare(a.lastModified(),b.lastModified())); for(int i=0;i<fs.length-90;i++)fs[i].delete(); }
    private static String sha(String s){ try{byte[]x=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte v:x)b.append(String.format("%02x",v));return b.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());} }
}
