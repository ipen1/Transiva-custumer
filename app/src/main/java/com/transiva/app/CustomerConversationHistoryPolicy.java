package com.transiva.app;
import org.json.JSONObject;
/** History only: an order with no exchanged message is not a conversation. */
public final class CustomerConversationHistoryPolicy {
    private CustomerConversationHistoryPolicy() {}
    public static boolean hasConversation(JSONObject item) {
        if (item == null) return false;
        if (item.has("has_messages")) return item.optBoolean("has_messages", false);
        // Backward compatibility while the server update is being installed.
        return !item.optString("last_message", "").trim().isEmpty();
    }
}
