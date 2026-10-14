package com.transiva.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CustomerMessageApi {

    private static final int TIMEOUT_MS = 45000;
    private static volatile Context appContext;

    public static void initialize(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private static void applySecurityHeaders(HttpURLConnection connection) {
        Context context = appContext;
        if (connection == null || context == null) return;
        CustomerApiClient.applySecurity(context, connection);
    }

    private CustomerMessageApi() {
    }

    public static JSONObject get(String endpoint)
            throws Exception {
        return request("GET", endpoint, null, null);
    }

    public static JSONObject post(
            String endpoint,
            JSONObject body
    ) throws Exception {
        return request("POST", endpoint, body, null);
    }

    public static JSONObject get(String endpoint, CustomerSessionGuard session) throws Exception {
        return request("GET", endpoint, null, session);
    }
    public static JSONObject post(String endpoint, JSONObject body, CustomerSessionGuard session) throws Exception {
        return request("POST", endpoint, body, session);
    }
    private static void authorize(HttpURLConnection connection, CustomerSessionGuard session) throws Exception {
        if(session!=null)session.authorize(appContext,connection);
    }
    private static JSONObject checkedResponse(HttpURLConnection connection, CustomerSessionGuard session) throws Exception {
        JSONObject result=readResponse(connection);
        if(session!=null&&!session.matches(appContext))throw new java.io.InterruptedIOException("Session changed");
        if(appContext!=null)CustomerApiClient.handleSessionResponse(appContext,connection.getResponseCode(),result.toString());
        return result;
    }
    public static JSONObject uploadImagePair(String endpoint, String roomId, String senderType,
            ChatImageProcessor.ImagePayload payload) throws Exception {
        return uploadImagePair(endpoint,roomId,senderType,payload,null);
    }
    public static JSONObject uploadImagePair(
            String endpoint,
            String roomId,
            String senderType,
            ChatImageProcessor.ImagePayload payload, CustomerSessionGuard session
    ) throws Exception {
        if (
                payload == null
                        || payload.previewWebp == null
                        || payload.hdJpeg == null
        ) {
            throw new IllegalArgumentException(
                    "Data foto tidak valid"
            );
        }

        String boundary =
                "----TransivaChat"
                        + System.currentTimeMillis();

        HttpURLConnection connection = null;

        try {
            connection =
                    CustomerApiClient.open(appContext, endpoint);

            authorize(connection, session);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary="
                            + boundary
            );

            try (
                    OutputStream output =
                            connection.getOutputStream()
            ) {
                writeField(
                        output,
                        boundary,
                        "room_id",
                        roomId
                );

                writeField(
                        output,
                        boundary,
                        "sender_type",
                        senderType
                );

                writeFile(
                        output,
                        boundary,
                        "preview_image",
                        "preview.webp",
                        "image/webp",
                        payload.previewWebp
                );

                writeFile(
                        output,
                        boundary,
                        "hd_image",
                        "hd.jpg",
                        "image/jpeg",
                        payload.hdJpeg
                );

                output.write(
                        ("--"
                                + boundary
                                + "--\r\n")
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

                output.flush();
            }

            return checkedResponse(connection, session);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static JSONObject uploadVoice(String endpoint, String roomId, String senderType,
            File file, long durationMs) throws Exception {
        return uploadVoice(endpoint,roomId,senderType,file,durationMs,null);
    }
    public static JSONObject uploadVoice(
            String endpoint,
            String roomId,
            String senderType,
            File file,
            long durationMs, CustomerSessionGuard session
    ) throws Exception {
        String boundary = "----TransivaVoice" + System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = CustomerApiClient.open(appContext, endpoint);
            authorize(connection, session);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream output = connection.getOutputStream()) {
                writeField(output, boundary, "room_id", roomId);
                writeField(output, boundary, "sender_type", senderType);
                writeField(output, boundary, "duration_ms", String.valueOf(durationMs));
                writeFile(output, boundary, "audio", "voice.m4a", "audio/mp4", readFile(file));
                output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            return checkedResponse(connection, session);
        } finally { if (connection != null) connection.disconnect(); }
    }

    static JSONObject uploadPendingMedia(String endpoint,JSONObject op,byte[] preview,byte[] media,CustomerSessionGuard session)throws Exception {
        String boundary="----TransivaPending"+java.util.UUID.randomUUID();HttpURLConnection connection=null;
        try{
            connection=CustomerApiClient.open(appContext,endpoint);authorize(connection,session);
            connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setConnectTimeout(TIMEOUT_MS);connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);connection.setDoOutput(true);connection.setChunkedStreamingMode(8192);
            connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
            try(OutputStream output=connection.getOutputStream()){
                for(String field:new String[]{"room_id","order_id","order_db_id","source","operation_id","duration_ms"})writeField(output,boundary,field,op.optString(field));
                // Legacy voice endpoint validates this field; the server derives the actual sender.
                writeField(output,boundary,"sender_type","customer");
                if(preview!=null){writeFile(output,boundary,"preview_image","preview.webp","image/webp",preview);writeFile(output,boundary,"hd_image","hd.jpg","image/jpeg",media);}
                else writeFile(output,boundary,"audio","voice.m4a","audio/mp4",media);
                output.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));output.flush();
            }
            return checkedResponse(connection,session);
        }finally{if(connection!=null)connection.disconnect();}
    }

    private static byte[] readFile(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n); return out.toByteArray();
        }
    }

    private static JSONObject request(
            String method,
            String endpoint,
            JSONObject body, CustomerSessionGuard session
    ) throws Exception {
        HttpURLConnection connection = null;

        try {
            connection =
                    CustomerApiClient.open(appContext, endpoint);

            authorize(connection, session);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache"
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

                    output.flush();
                }
            }

            return checkedResponse(connection, session);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject readResponse(
            HttpURLConnection connection
    ) throws Exception {
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

        String body=BoundedResponseReader.read(stream,4*1024*1024,TIMEOUT_MS).trim();

        if (body.isEmpty()) {
            throw new IllegalStateException(
                    "Respons server kosong"
            );
        }

        int firstBrace = body.indexOf('{');
        int lastBrace = body.lastIndexOf('}');

        if (
                firstBrace >= 0
                        && lastBrace > firstBrace
        ) {
            body = body.substring(
                    firstBrace,
                    lastBrace + 1
            );
        }

        JSONObject response = new JSONObject(body);
        response.put("http_status", status);
        if (status < 200 || status >= 400) {
            if (!response.has("success")) response.put("success", false);
            if (!response.has("message")) response.put("message", "HTTP " + status);
        }
        return response;
    }

    private static void writeField(
            OutputStream output,
            String boundary,
            String name,
            String value
    ) throws Exception {
        output.write(
                ("--"
                        + boundary
                        + "\r\n")
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        output.write(
                ("Content-Disposition: form-data; "
                        + "name=\""
                        + name
                        + "\"\r\n\r\n")
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        output.write(
                (value == null ? "" : value)
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        output.write(
                "\r\n".getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private static void writeFile(
            OutputStream output,
            String boundary,
            String fieldName,
            String filename,
            String mime,
            byte[] bytes
    ) throws Exception {
        output.write(
                ("--"
                        + boundary
                        + "\r\n")
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        output.write(
                ("Content-Disposition: form-data; "
                        + "name=\""
                        + fieldName
                        + "\"; "
                        + "filename=\""
                        + filename
                        + "\"\r\n")
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        output.write(
                ("Content-Type: "
                        + mime
                        + "\r\n\r\n")
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );

        output.write(bytes);

        output.write(
                "\r\n".getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }
}
