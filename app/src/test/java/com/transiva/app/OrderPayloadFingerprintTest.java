package com.transiva.app;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;
public class OrderPayloadFingerprintTest {
    @Test public void keyOrderDoesNotChangeIdentity() throws Exception {
        JSONObject a=new JSONObject("{\"pickup\":{\"lng\":2,\"lat\":1},\"note\":\"a\"}");
        JSONObject b=new JSONObject("{\"note\":\"a\",\"pickup\":{\"lat\":1,\"lng\":2}}");
        assertEquals(OrderPayloadFingerprint.canonical(a),OrderPayloadFingerprint.canonical(b));
    }
    @Test public void routeSequenceChangesIdentity() throws Exception {
        assertNotEquals(OrderPayloadFingerprint.canonical(new JSONArray("[1,2]")),OrderPayloadFingerprint.canonical(new JSONArray("[2,1]")));
    }
    @Test public void noteAndPaymentChangesAreDetected() throws Exception {
        assertNotEquals(OrderPayloadFingerprint.canonical(new JSONObject("{\"payment_method\":\"cash\"}")),OrderPayloadFingerprint.canonical(new JSONObject("{\"payment_method\":\"balance\"}")));
        assertNotEquals(OrderPayloadFingerprint.canonical(new JSONObject("{\"note\":\"a\"}")),OrderPayloadFingerprint.canonical(new JSONObject("{\"note\":\"b\"}")));
    }
    @Test public void stringsRemainValidJson() throws Exception {
        String raw="{\"note\":\"line\\n\\\"quote\\\"\",\"missing\":null}";
        assertEquals("line\n\"quote\"",new JSONObject(OrderPayloadFingerprint.canonical(new JSONObject(raw))).getString("note"));
    }
}
