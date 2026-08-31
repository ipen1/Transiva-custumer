package com.transiva.app;

import java.util.Calendar;

/** Presentation-only helpers extracted from CustomerDashboardActivity. */
public final class CustomerDashboardFormatters {
    private CustomerDashboardFormatters() { }
    public static String timeGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 11) return "Selamat pagi";
        if (h < 15) return "Selamat siang";
        if (h < 18) return "Selamat sore";
        return "Selamat malam";
    }
    public static String displayName(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return "Kamu";
        String[] parts = v.split("\\s+");
        String first = parts.length == 0 ? v : parts[0];
        if (first.length() <= 18) return first;
        return first.substring(0, 18) + "…";
    }
}
