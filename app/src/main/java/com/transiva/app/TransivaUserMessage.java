package com.transiva.app;

import java.util.Locale;

/** Converts server/transport errors into safe, human-readable customer messages. */
public final class TransivaUserMessage {
    private TransivaUserMessage() {}
    public static String network() { return "Koneksi sedang bermasalah. Coba lagi."; }
    public static String fromServer(String raw) {
        if (raw == null || raw.trim().isEmpty()) return network();
        String s = raw.toLowerCase(Locale.US);
        if (s.contains("http") || s.contains("timeout") || s.contains("json") || s.contains("exception") ||
            s.contains("nullpointer") || s.contains("sql") || s.contains("server error") || s.contains("500") ||
            s.contains("502") || s.contains("503") || s.contains("504") || s.contains("socket") || s.contains("network")) {
            return network();
        }
        if (raw.length() > 120) return network();
        return raw;
    }
}
