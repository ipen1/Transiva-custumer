package com.transiva.app;

import java.text.NumberFormat;
import java.util.Locale;

/** Shared, stateless customer-side text/number formatting. */
public final class CustomerCommonFormatters {
    private CustomerCommonFormatters() {}

    public static String first(String... values) {
        return firstStrict(values);
    }

    public static String firstBasic(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value == null) continue;
            String clean = value.trim();
            if (!clean.isEmpty() && !"null".equalsIgnoreCase(clean)) return clean;
        }
        return "";
    }

    public static String firstStrict(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value == null) continue;
            String clean = value.trim();
            if (!clean.isEmpty() && !"null".equalsIgnoreCase(clean) && !"undefined".equalsIgnoreCase(clean)) return clean;
        }
        return "";
    }

    public static String rupiah(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return format.format(amount);
    }

    public static String rupiahSpacedTruncate(double amount) {
        return "Rp " + NumberFormat.getNumberInstance(new Locale("id", "ID")).format((long) amount);
    }

    public static String rupiahCompactPrefix(double amount) {
        return "Rp" + NumberFormat.getNumberInstance(new Locale("id", "ID")).format(Math.round(amount));
    }

    public static String formatMoneyInt(int value) {
        return String.format(new Locale("id", "ID"), "%,d", Math.max(0, value)).replace(',', '.');
    }

    public static String shortText(String value, int max) {
        String clean = first(value);
        if (max <= 0 || clean.length() <= max) return clean;
        if (max <= 3) return clean.substring(0, max);
        return clean.substring(0, max - 3) + "...";
    }
}
