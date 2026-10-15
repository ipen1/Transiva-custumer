package com.transiva.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class CustomerErrorPolicyTest {
 @Test public void technicalDiagnosticsStayOutOfUserMessages() {
  for(String message:new String[]{"SQLSTATE[23000]","HTTP 500","java.lang.NullPointerException","<html>error</html>","Warning: mysqli error","JSON parse failed","/home/private/app.php"})
   assertEquals("Coba lagi sebentar.",CustomerErrorMessages.humanize(message,"Coba lagi sebentar."));
 }
 @Test public void usefulFieldAndAccountGuidanceIsPreserved() {
  for(String message:new String[]{"Email sudah digunakan.","Kata sandi tidak sesuai.","Saldo Transiva harus Rp0 sebelum akun dapat dihapus."})
   assertEquals(message,CustomerErrorMessages.humanize(message,"Coba lagi."));
 }
 @Test public void socketTimeoutIsVisibleButLifecycleInterruptionIsSilent() {
  assertFalse(CustomerAsyncError.isCancellation(new java.net.SocketTimeoutException("Read timed out")));
  assertTrue(CustomerAsyncError.isCancellation(new InterruptedException()));
  assertFalse(CustomerAsyncError.isCancellation(new java.io.IOException("Offline")));
 }
 @Test public void globalAsyncFailuresUseTheHumanMessagePolicy() {
  assertEquals("Coba lagi.",CustomerAsyncError.userMessage(new RuntimeException("SQLSTATE[23000]"),"Coba lagi."));
  assertFalse(CustomerErrorMessages.humanize("Kata sandi minimal 8 karakter, maksimal 72 byte.","Coba lagi.").contains("byte"));
 }
}
