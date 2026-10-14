package com.transiva.app;
import java.net.URLConnection;
import java.util.Map;
/** API 23 alternatives without changing existing map or HTTP semantics. */
final class CustomerApi23Compat {
    private CustomerApi23Compat() { }
    static <K,V> V getOrDefault(Map<K,V> map,K key,V fallback){V value=map.get(key);return value!=null||map.containsKey(key)?value:fallback;}
    static long contentLength(URLConnection connection){try{String header=connection.getHeaderField("Content-Length");if(header==null)return -1;long size=Long.parseLong(header.trim());return size>=0?size:-1;}catch(NumberFormatException error){return -1;}}
}
