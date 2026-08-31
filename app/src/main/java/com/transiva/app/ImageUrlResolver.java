package com.transiva.app;

/** Normalizes legacy/root/server image paths without accidentally duplicating /server/. */
public final class ImageUrlResolver {
    private ImageUrlResolver() {}

    public static String resolve(String raw) {
        if (raw == null) return "";
        String v = raw.trim().replace("\\", "/");
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) return "";
        if (v.startsWith("http://transiva.my.id/")) {
            return "https://transiva.my.id/" + v.substring("http://transiva.my.id/".length());
        }
        if (v.startsWith("https://")) return v;
        if (v.startsWith("http://")) return "";
        while (v.startsWith("./")) v = v.substring(2);
        while (v.startsWith("/")) v = v.substring(1);
        // Historical values may already contain server/. Keep those as-is.
        return ApiConfig.root(v);
    }
}
