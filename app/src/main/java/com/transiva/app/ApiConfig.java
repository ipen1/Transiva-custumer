package com.transiva.app;

/** Single source of truth for Transiva-owned URLs. */
public final class ApiConfig {
    public static final String ROOT = "https://transiva.my.id/";
    public static final String SERVER = ROOT + "server/";
    private ApiConfig() {}

    public static String server(String path) {
        if (path == null) return SERVER;
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        if (p.startsWith("server/")) p = p.substring("server/".length());
        return SERVER + p;
    }

    public static String root(String path) {
        if (path == null) return ROOT;
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        return ROOT + p;
    }
}
