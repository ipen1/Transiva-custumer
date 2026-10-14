package com.transiva.app;

/** Normalizes legacy/root/server image paths without accidentally duplicating /server/. */
public final class ImageUrlResolver {
    private ImageUrlResolver() {}

    public static String resolve(String raw) {
        if (raw == null) return "";
        String v = raw.trim().replace("\\", "/");
        if (v.isEmpty() || "null".equalsIgnoreCase(v)) return "";
        String legacy="server/uploads/chat/";
        int at=v.indexOf(legacy);
        if(at>=0 && (v.startsWith(legacy)||v.startsWith("/"+legacy)||v.startsWith("https://transiva.my.id/")||v.startsWith("http://transiva.my.id/"))){
            try{return ApiConfig.server("view_chat_image.php")+"?file="+java.net.URLEncoder.encode(v.substring(at+legacy.length()),"UTF-8");}catch(Exception e){return "";}
        }
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
