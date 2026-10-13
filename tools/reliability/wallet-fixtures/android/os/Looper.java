package android.os;
public class Looper {
 private static final Looper MAIN=new Looper();private static final ThreadLocal<Boolean> ui=ThreadLocal.withInitial(()->false);
 public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return ui.get()?MAIN:null;}
 public static void markMain(boolean value){ui.set(value);}
}
