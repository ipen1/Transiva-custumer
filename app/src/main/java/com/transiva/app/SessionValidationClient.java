package com.transiva.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

/**
 * Validasi sesi customer melalui gateway HTTP yang sama dengan API utama.
 *
 * Penting:
 * - Tidak memasang TrustManager/HostnameVerifier sendiri.
 * - Tidak melakukan hard certificate pinning.
 * - X-App-Scope, Authorization dan X-Device-UUID berasal dari CustomerApiClient.
 * - Gangguan jaringan/TLS tidak pernah dianggap sebagai sesi invalid.
 */
public final class SessionValidationClient {
    private static final String URL_VALIDATE =
            "https://transiva.my.id/server/native_validate_session.php";

    private SessionValidationClient() {}

    public static void validate(Context context) {
        if (context == null) return;

        final Context app = context.getApplicationContext();
        final SessionManager session = new SessionManager(app);
        final String token = session.getToken() == null ? "" : session.getToken().trim();
        if (!session.isLoggedIn() || token.isEmpty()) return;

        TransivaNetworkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // Satu jalur koneksi dengan dashboard/API lain agar header keamanan selalu konsisten.
                conn = CustomerApiClient.open(app, URL_VALIDATE);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setUseCaches(false);
                conn.setRequestProperty("Cache-Control", "no-store");

                final int status = conn.getResponseCode();
                final InputStream stream = status >= 200 && status < 400
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                final String raw = read(stream);

                // Logout hanya bila server memberi kode pencabutan sesi yang eksplisit.
                // Semua gateway customer memakai aturan yang sama melalui CustomerApiClient.
                boolean revoked = CustomerApiClient.handleSessionResponse(app, status, raw);
                if (!revoked && status >= 200 && status < 300) {
                    session.touchSession();
                }
            } catch (Exception ignored) {
                // Gangguan koneksi bukan alasan menghapus sesi pengguna.
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        }
    }
}
