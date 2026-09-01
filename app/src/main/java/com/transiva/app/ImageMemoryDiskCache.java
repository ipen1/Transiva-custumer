package com.transiva.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.*;
import java.security.MessageDigest;

public final class ImageMemoryDiskCache {
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>((int)Math.max(8,Runtime.getRuntime().maxMemory()/1024/12)){
        @Override protected int sizeOf(String k,Bitmap b){ return b==null?1:Math.max(1,b.getByteCount()/1024); }
    };
    private ImageMemoryDiskCache(){}
    public static Bitmap get(Context c,String key){ Bitmap b=MEMORY.get(key); if(b!=null&&!b.isRecycled())return b; File f=file(c,key); if(!f.exists())return null; b=BitmapFactory.decodeFile(f.getAbsolutePath()); if(b!=null)MEMORY.put(key,b); return b; }
    public static void put(Context c,String key,Bitmap b){ if(c==null||key==null||b==null)return; MEMORY.put(key,b); TransivaNetworkExecutor.execute(() -> { File f=file(c,key); try(FileOutputStream o=new FileOutputStream(f)){ b.compress(Bitmap.CompressFormat.WEBP_LOSSY,88,o); }catch(Throwable ignored){} trim(c); }); }
    private static File file(Context c,String k){ File d=new File(c.getCacheDir(),"img_v2"); if(!d.exists())d.mkdirs(); return new File(d,sha(k)+".webp"); }
    private static void trim(Context c){ File d=new File(c.getCacheDir(),"img_v2"); File[] fs=d.listFiles(); if(fs==null||fs.length<120)return; java.util.Arrays.sort(fs,(a,b)->Long.compare(a.lastModified(),b.lastModified())); for(int i=0;i<fs.length-90;i++)fs[i].delete(); }
    private static String sha(String s){ try{byte[]x=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte v:x)b.append(String.format("%02x",v));return b.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());} }
}
