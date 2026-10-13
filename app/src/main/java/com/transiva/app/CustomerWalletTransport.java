package com.transiva.app;

import android.content.Context;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Application-context transport. No Activity or journal I/O is needed on the UI thread. */
final class CustomerWalletTransport implements CustomerWalletCoordinator.Transport {
    public JSONObject post(Context context, CustomerSessionGuard session, String endpoint, JSONObject payload) throws Exception {
        if (!session.matches(context)) throw new java.io.InterruptedIOException("Session changed");
        HttpURLConnection connection=CustomerApiClient.open(context,endpoint);
        try {
            session.authorize(context,connection);
            String key=payload.optString("idempotency_key","");
            if(!key.isEmpty())connection.setRequestProperty("Idempotency-Key",key);
            connection.setConnectTimeout(25000);connection.setReadTimeout(25000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            int code=connection.getResponseCode();
            String raw=BoundedResponseReader.read(code>=200&&code<400?connection.getInputStream():connection.getErrorStream(),4*1024*1024,25000);
            if(!session.matches(context))throw new java.io.InterruptedIOException("Session changed");
            CustomerApiClient.handleSessionResponse(context,code,raw);
            if(raw.isEmpty())throw new IllegalStateException("Respons server kosong");
            return new JSONObject(raw);
        } finally {connection.disconnect();}
    }
}
