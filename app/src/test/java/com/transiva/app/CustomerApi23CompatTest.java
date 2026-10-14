package com.transiva.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

public class CustomerApi23CompatTest {
    @Test public void defaultKeepsExplicitNullAndMissingKeysDistinct(){
        HashMap<String,Integer> values=new HashMap<>();values.put("null",null);values.put("value",7);
        assertNull(CustomerApi23Compat.getOrDefault(values,"null",0));
        assertEquals(Integer.valueOf(0),CustomerApi23Compat.getOrDefault(values,"missing",0));
        assertEquals(Integer.valueOf(7),CustomerApi23Compat.getOrDefault(values,"value",0));
    }
    private URLConnection connection(String header)throws Exception {
        return new URLConnection(new URL("https://example.invalid")){
            public void connect(){}
            public String getHeaderField(String name){return header;}
        };
    }
    @Test public void contentLengthPreservesLongValues()throws Exception {
        assertEquals(5000000000L,CustomerApi23Compat.contentLength(connection(" 5000000000 ")));
    }
    @Test public void missingMalformedOrNegativeLengthIsUnknown()throws Exception {
        for(String header:new String[]{null,"", "invalid","-1","99999999999999999999999"})assertEquals(-1,CustomerApi23Compat.contentLength(connection(header)));
    }
}
