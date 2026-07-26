package com.transiva.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class RepeatOrderApi {

    private static final int TIMEOUT_MS = 25000;

    private RepeatOrderApi() {
    }

    public static JSONObject get(String url)
            throws Exception {
        return request("GET", url, null);
    }

    public static JSONObject post(
            String url,
            JSONObject body
    ) throws Exception {
        return request("POST", url, body);
    }

    private static JSONObject request(
            String method,
            String url,
            JSONObject body
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(url).openConnection();

            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                try (
                        OutputStream output =
                                connection.getOutputStream()
                ) {
                    output.write(
                            body.toString().getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }
            }

            int status = connection.getResponseCode();

            InputStream stream =
                    status >= 200 && status < 400
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            if (stream == null) {
                throw new IllegalStateException(
                        "Respons server kosong"
                );
            }

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    stream,
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder raw = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                raw.append(line);
            }

            reader.close();

            JSONObject response =
                    new JSONObject(
                            raw.length() == 0
                                    ? "{}"
                                    : raw.toString()
                    );

            if (status < 200 || status >= 400) {
                throw new IllegalStateException(
                        response.optString(
                                "message",
                                "HTTP " + status
                        )
                );
            }

            return response;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
