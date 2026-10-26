package com.transiva.app;

import android.content.Context;
import java.net.URLEncoder;
import java.util.Map;
import org.json.JSONObject;

/** Data-only chat pushes are hints; the authenticated server decides whether to alert. */
final class CustomerChatPushGuard {
    private CustomerChatPushGuard() {}

    static boolean accept(Context c, Map<String, String> data) {
        try {
            SessionManager s = new SessionManager(c);
            if (!s.isLoggedIn() || !"customer".equalsIgnoreCase(s.getRole())) return false;
            String user = s.getUserId();
            String target = value(data, "target_user_id");
            String scope = value(data, "target_app_scope");
            if ((!target.isEmpty() && !target.equals(user))
                    || (!scope.isEmpty() && !"customer".equalsIgnoreCase(scope))) return false;
            String targetSession = value(data, "target_session_hash");
            if (!targetSession.isEmpty() && !targetSession.equals(FcmTokenCoordinator.hash(s.getToken()))) return false;
            int id = Integer.parseInt(value(data, "chat_id"));
            if (id <= 0) return false;
            CustomerChatNoticeState.ensure(c);
            long boundary = CustomerChatNoticeState.boundary(c, user);
            CustomerTransportSession owner = CustomerTransportSession.capture(c);
            int age = CustomerChatNoticeState.maxAge(boundary);
            String url = "https://transiva.my.id/server/customer_chat_notification_status.php?chat_id=" + id
                    + "&max_age_seconds=" + age
                    + "&room_id=" + enc(value(data, "room_id"))
                    + "&order_id=" + enc(value(data, "order_id"))
                    + "&order_db_id=" + enc(value(data, "order_db_id"))
                    + "&order_source=" + enc(value(data, "order_source"));
            JSONObject result = TransivaHttpRepository.getJsonOnce(c, url, 5000, owner);
            return owner.matches(c) && result != null && result.optBoolean("success", false)
                    && result.optBoolean("eligible", false) && result.optInt("chat_id", 0) == id
                    && CustomerChatNoticeState.claim(c, user, boundary, id)
                    && !CustomerChatNotificationPoller.isOpenRoom(value(data, "room_id"));
        } catch (Exception unavailable) {
            // No alert on an unverifiable hint. Foreground polling can recover the message.
            return false;
        }
    }
    private static String enc(String v) throws Exception { return URLEncoder.encode(v, "UTF-8"); }
    private static String value(Map<String, String> d, String key) {
        String v = d == null ? null : d.get(key); return v == null ? "" : v.trim();
    }
}
