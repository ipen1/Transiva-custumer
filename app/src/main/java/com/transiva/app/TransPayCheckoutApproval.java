package com.transiva.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class TransPayCheckoutApproval {
    private static volatile Request active;

    static final class Request {
        final String id = UUID.randomUUID().toString();
        final String user;
        final String route;
        final String attempt;
        final JSONObject payload;
        final CustomerTransportSession session;
        final CountDownLatch finished = new CountDownLatch(1);
        volatile String proof;

        Request(Activity activity, String user, String route, String attempt, JSONObject payload)
                throws Exception {
            this.user = user;
            this.route = route;
            this.attempt = attempt;
            this.payload = new JSONObject(payload.toString());
            session = CustomerTransportSession.capture(activity);
        }

        synchronized void complete(String value) {
            if (finished.getCount() == 0) return;
            proof = value;
            finished.countDown();
        }
    }

    static Request find(String id) {
        Request request = active;
        return request != null && request.id.equals(id) ? request : null;
    }

    static String request(Activity activity, String user, String route, String attempt, JSONObject order)
            throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Checkout authorization requires a worker thread");
        }
        Request request = new Request(activity, user, route, attempt, order);
        synchronized (TransPayCheckoutApproval.class) {
            if (active != null) {
                throw new ReliableOrderClient.PendingAttemptException("Verifikasi TransPay lain sedang berlangsung.");
            }
            active = request;
        }
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (find(request.id) == null) return;
                if (activity.isFinishing() || activity.isDestroyed() || !request.session.matches(activity)) {
                    request.complete(null);
                    return;
                }
                try {
                    activity.startActivity(new Intent(activity, TransPayApprovalActivity.class)
                            .putExtra("request_id", request.id));
                } catch (RuntimeException unavailable) {
                    request.complete(null);
                }
            });
            if (!request.finished.await(150, TimeUnit.SECONDS) || request.proof == null
                    || activity.isFinishing() || activity.isDestroyed() || !request.session.matches(activity)) {
                throw new ReliableOrderClient.PendingAttemptException("Pembayaran TransPay belum disetujui.");
            }
            return request.proof;
        } finally {
            synchronized (TransPayCheckoutApproval.class) {
                if (active == request) active = null;
            }
        }
    }
}
