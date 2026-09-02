package com.transiva.app;

/** Stateless normalization used by ProfileActivity. */
public final class ProfileSupport {
    private ProfileSupport() {}

    public static String absoluteUrl(String value) {
        String clean = CustomerCommonFormatters.first(value);
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean;
        while (clean.startsWith("/")) clean = clean.substring(1);
        return "https://transiva.my.id/" + clean;
    }

    public static String localIndonesiaPhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0062")) digits = digits.substring(4);
        else if (digits.startsWith("62")) digits = digits.substring(2);
        else if (digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() > 13) digits = digits.substring(0, 13);
        return digits;
    }

    public static String normalizeIndonesiaPhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0062")) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = "62" + digits.substring(1);
        else if (digits.startsWith("8")) digits = "62" + digits;
        else if (!digits.startsWith("62")) digits = "62" + digits;
        if (digits.length() > 15) digits = digits.substring(0, 15);
        return digits.isEmpty() ? "62" : digits;
    }
}
