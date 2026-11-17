package com.transiva.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/** A late validation can only affect the account that sent it. */
public final class SessionValidationClient {
    private static final String URL_VALIDATE = ApiConfig.ROOT + "server/native_validate_session.php";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SessionValidationClient() {}

    public static void validate(Context context) {
        if (context == null || !TransivaNetworkMonitor.isOnline()) return;

        Context app = context.getApplicationContext();
        SessionManager session = new SessionManager(app);
        String token = session.getToken();
        if (!session.isLoggedIn() || token == null || token.trim().isEmpty()) return;
        CustomerTransportSession owner = CustomerTransportSession.capture(app);

        TransivaNetworkExecutor.execute(() -> {
            CustomerTransportSession.bind(owner);
            try {
                JSONObject result = TransivaHttpRepository.getJsonWithin(app, URL_VALIDATE, 5000);
                if (result.optBoolean("success", false)) {
                    MAIN.post(() -> {
                        if (owner.matches(app)) session.touchSession();
                    });
                }
            } catch (Exception ignored) {
                // Explicit revocation is handled by the transport. Transient faults keep the session.
            } finally {
                CustomerTransportSession.clearOwner();
            }
        });
    }
}
