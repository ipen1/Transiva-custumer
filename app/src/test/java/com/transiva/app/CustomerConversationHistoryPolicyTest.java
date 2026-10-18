package com.transiva.app;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class CustomerConversationHistoryPolicyTest {
    @Test public void ordersWithoutMessagesAreNotConversations() throws Exception {
        assertFalse(CustomerConversationHistoryPolicy.hasConversation(new JSONObject().put("is_history",true)));
        assertFalse(CustomerConversationHistoryPolicy.hasConversation(new JSONObject().put("last_message","  \n  ")));
    }
    @Test public void realTextImageAndVoiceAreKept() throws Exception {
        for(String text : new String[]{"Saya sudah tiba", "[[IMAGE2]]/photo|/thumb", "[[VOICE]]/audio|12"})
            assertTrue(CustomerConversationHistoryPolicy.hasConversation(new JSONObject().put("last_message",text)));
    }
    @Test public void explicitServerExistenceIsAuthoritative() throws Exception {
        assertTrue(CustomerConversationHistoryPolicy.hasConversation(new JSONObject().put("has_messages",true)));
        assertFalse(CustomerConversationHistoryPolicy.hasConversation(new JSONObject().put("has_messages",false).put("last_message","stale preview")));
    }
    @Test public void nullAndLegacyEmptyValuesAreSafe() throws Exception {
        assertFalse(CustomerConversationHistoryPolicy.hasConversation(null));
        assertFalse(CustomerConversationHistoryPolicy.hasConversation(new JSONObject().put("last_message",JSONObject.NULL)));
    }
}
