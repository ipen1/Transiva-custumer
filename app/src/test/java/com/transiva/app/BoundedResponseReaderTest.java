package com.transiva.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
public class BoundedResponseReaderTest {
 @Test public void oversizedResponseFailsAndClosesStream()throws Exception {
  final boolean[] closed={false};InputStream input=new ByteArrayInputStream(new byte[1000]){@Override public void close(){closed[0]=true;}};
  try{BoundedResponseReader.read(input,100,1000);fail("Oversized response accepted");}catch(IOException expected){}
  assertTrue(closed[0]);
 }
 @Test public void interruptedRequestNeverReadsNetwork()throws Exception {
  InputStream input=new InputStream(){public int read(){throw new AssertionError("Canceled request performed IO");}};
  Thread.currentThread().interrupt();try{BoundedResponseReader.read(input);fail("Cancellation lost");}catch(InterruptedIOException expected){}finally{Thread.interrupted();}
 }
 @Test public void preservesUtf8()throws Exception {
  String text="{\"nama\":\"Wisata 🌴\"}";
  assertEquals(text,BoundedResponseReader.read(new ByteArrayInputStream(text.getBytes("UTF-8"))));
 }
}
