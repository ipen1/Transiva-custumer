package com.transiva.app;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
/** Shared bounds for legacy HTTP consumers; oversized or interrupted responses never become success. */
public final class BoundedResponseReader {
    private BoundedResponseReader() {}
    public static String read(InputStream stream) throws IOException { return read(stream,4*1024*1024,30000); }
    public static String read(InputStream stream,int maxBytes,int timeoutMs) throws IOException {
        if(stream==null)return "";
        if(maxBytes<=0||timeoutMs<=0)throw new IllegalArgumentException("Positive response bounds required");
        long deadline=System.nanoTime()+timeoutMs*1000000L;
        try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];
            while(true){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Response canceled");
                if(System.nanoTime()>deadline)throw new java.net.SocketTimeoutException("Response deadline exceeded");
                int count=input.read(buffer);if(count<0)break;
                if(output.size()+count>maxBytes)throw new IOException("Response exceeds size limit");
                output.write(buffer,0,count);
            }
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
