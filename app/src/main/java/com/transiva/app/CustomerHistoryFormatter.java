package com.transiva.app;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Stateless presentation formatting extracted from CustomerHistoryActivity. */
public final class CustomerHistoryFormatter {
    private CustomerHistoryFormatter() { }

    public static String shortText(String value) {
        value = first(value, "");
        if (value.length() <= 30) return value;
        return value.substring(0, 27) + "...";
    }

    public static String displayDate(JSONObject order) {
        String value = first(
                order == null ? "" : order.optString("created_at"),
                order == null ? "" : order.optString("order_date"),
                order == null ? "" : order.optString("date"),
                order == null ? "" : order.optString("updated_at"),
                ""
        );
        if (value.isEmpty()) return "Waktu tidak tersedia";
        String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};
        for (String format : formats) {
            try {
                Date date = new SimpleDateFormat(format, Locale.US).parse(value);
                if (date != null) {
                    return new SimpleDateFormat("dd MMM yyyy • HH:mm", new Locale("id", "ID")).format(date);
                }
            } catch (Exception ignored) { }
        }
        return value;
    }

    public static double orderPrice(JSONObject order) {
        if (order == null) return 0;
        String[] keys = {"total_amount", "total_price", "price", "fare", "amount", "total"};
        for (String key : keys) {
            Object value = order.opt(key);
            if (value == null) continue;
            try {
                if (value instanceof Number) return ((Number) value).doubleValue();
                String cleaned = String.valueOf(value)
                        .replaceAll("[^0-9.,-]", "")
                        .replace(".", "")
                        .replace(",", ".");
                if (!cleaned.isEmpty()) return Double.parseDouble(cleaned);
            } catch (Exception ignored) { }
        }
        return 0;
    }

    public static String compactRupiah(double amount) {
        if (amount >= 1_000_000) {
            return String.format(new Locale("id", "ID"), "Rp%.1f jt", amount / 1_000_000d);
        }
        if (amount >= 1_000) {
            return String.format(new Locale("id", "ID"), "Rp%.0f rb", amount / 1_000d);
        }
        return "Rp" + Math.round(amount);
    }

    public static String rupiah(double amount) {
        return NumberFormat.getCurrencyInstance(new Locale("id", "ID")).format(amount);
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }
}
