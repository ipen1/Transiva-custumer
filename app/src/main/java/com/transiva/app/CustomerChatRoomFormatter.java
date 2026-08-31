package com.transiva.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Chat room normalization/presentation extracted from the Activity. */
public final class CustomerChatRoomFormatter {
    private CustomerChatRoomFormatter() { }
    public static String normalizeRoom(String value) {
        String room = value == null ? "" : value.trim().replace('_','-').toUpperCase(Locale.US);
        room = room.replaceAll("[^A-Z0-9\\-]", "");
        if (!room.isEmpty() && !room.startsWith("ROOM-")) room = "ROOM-" + room;
        return room;
    }
    public static String serviceName(String type) {
        String t = CustomerMessageStatus.normalize(type);
        if (t.contains("food")) return "TransFood";
        if (t.contains("shop") || t.contains("mart")) return "TransShop";
        if (t.contains("car") || t.contains("mobil")) return "TransCar";
        return "TransRide";
    }
    public static String formatTime(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        for (String f : new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss"}) {
            try { Date d = new SimpleDateFormat(f, Locale.US).parse(value.trim());
                if (d != null) return new SimpleDateFormat("dd MMM • HH:mm", new Locale("id","ID")).format(d);
            } catch (Exception ignored) { }
        }
        return value;
    }
    public static String absoluteUrl(String value) {
        String path = value == null ? "" : value.trim().replace("\\", "/");
        // Never allow cleartext media URLs in the customer UI.
        if (path.startsWith("http://transiva.my.id/")) path = "https://transiva.my.id/" + path.substring("http://transiva.my.id/".length());
        if (path.startsWith("https://")) return path;
        if (path.startsWith("http://")) return "";
        if (path.startsWith("/")) return "https://transiva.my.id" + path;
        return "https://transiva.my.id/" + path;
    }
}
