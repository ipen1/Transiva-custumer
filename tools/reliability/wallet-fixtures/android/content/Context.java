package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 public static boolean rejectCommit;
 private final Map<String,SharedPreferences> stores=new HashMap<>();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){return stores.computeIfAbsent(name,x->new Preferences());}
 static class Preferences implements SharedPreferences {
  Map<String,String> values=new HashMap<>();
  public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
  public Editor edit(){return new Editor(){Map<String,String> edits=new HashMap<>();public Editor putString(String k,String v){edits.put(k,v);return this;}public Editor remove(String k){edits.put(k,null);return this;}public boolean commit(){for(Map.Entry<String,String> e:edits.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}return !rejectCommit;}};}
 }
}
