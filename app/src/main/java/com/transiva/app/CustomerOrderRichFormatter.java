package com.transiva.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rich, service-aware order summaries shared by Activity and Detail screens. */
public final class CustomerOrderRichFormatter {
    private CustomerOrderRichFormatter() { }

    public static final class Line {
        public final String label;
        public final String value;
        public final String icon;
        public Line(String label, String value, String icon) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.icon = icon == null ? "ℹ️" : icon;
        }
    }

    public static List<String> compactLines(JSONObject order) {
        List<String> out = new ArrayList<>();
        if (order == null) return out;
        String type = type(order);
        if (type.contains("food")) {
            JSONArray items = order.optJSONArray("food_items");
            if (items != null && items.length() > 0) {
                StringBuilder names = new StringBuilder();
                int shown = Math.min(2, items.length());
                for (int i = 0; i < shown; i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    if (names.length() > 0) names.append(" • ");
                    names.append(Math.max(1, item.optInt("qty", 1))).append("× ")
                            .append(first(item.optString("name"), "Menu"));
                }
                if (items.length() > shown) names.append(" • +").append(items.length() - shown).append(" menu");
                if (names.length() > 0) out.add(names.toString());
            }
            int merchantDiscount = merchantDiscount(order);
            String split = "Makanan " + rupiah(order.optDouble("food_total", 0))
                    + " • Ongkir " + rupiah(order.optDouble("delivery_fee", 0));
            out.add(split);
            if (merchantDiscount > 0) out.add("🏷 Hemat diskon merchant " + rupiah(merchantDiscount));
            int voucher = order.optInt("voucher_discount", 0);
            if (voucher > 0) out.add("🎟 Promo Transiva " + rupiah(voucher));
            return trim(out, 3);
        }

        if (type.contains("pickup") || type.contains("send")) {
            String item = first(order.optString("item_name"), "Paket");
            String cat = first(order.optString("item_category"), order.optString("package_size"), "");
            out.add(cat.isEmpty() ? item : item + " • " + cat);
            String receiver = first(order.optString("receiver_name"), "");
            if (!receiver.isEmpty()) out.add("Penerima: " + receiver);
            addDistance(out, order);
            return trim(out, 3);
        }

        if (type.contains("shop") || type.contains("mart")) {
            String note = first(order.optString("plain_note"), extractNoteText(order));
            if (!note.isEmpty()) out.add(shortText(note, 70));
            addDistance(out, order);
            return trim(out, 3);
        }

        addDistance(out, order);
        int voucher = order.optInt("voucher_discount", 0);
        if (voucher > 0) out.add("🎟 Hemat promo " + rupiah(voucher));
        int rain = order.optInt("rain_surcharge", 0);
        if (rain > 0) out.add("🌧 Penyesuaian cuaca " + rupiah(rain));
        String note = first(order.optString("plain_note"), extractNoteText(order));
        if (!note.isEmpty() && !looksJson(note)) out.add("Catatan: " + shortText(note, 62));
        return trim(out, 3);
    }

    public static List<Line> detailLines(JSONObject order) {
        List<Line> out = new ArrayList<>();
        if (order == null) return out;
        String type = type(order);

        if (type.contains("food")) {
            String resto = first(order.optString("restaurant_name"), order.optString("pickup_address"), "Merchant");
            out.add(new Line("Merchant", resto, "🍽️"));
            JSONArray items = order.optJSONArray("food_items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    int qty = Math.max(1, item.optInt("qty", 1));
                    String name = first(item.optString("name"), "Menu");
                    int subtotal = item.optInt("subtotal", item.optInt("price", 0) * qty);
                    double pct = item.optDouble("discount_percent", 0);
                    int saved = Math.max(0, item.optInt("discount_amount", 0) * qty);
                    String options = first(item.optString("options"), "");
                    StringBuilder v = new StringBuilder(rupiah(subtotal));
                    if (pct > 0.01) v.append(" • Diskon ").append(formatPercent(pct));
                    if (saved > 0) v.append(" • Hemat ").append(rupiah(saved));
                    if (!options.isEmpty()) v.append("\nPilihan: ").append(options);
                    out.add(new Line(qty + "× " + name, v.toString(), "🍴"));
                }
            }
            out.add(new Line("Subtotal makanan", rupiah(order.optDouble("food_total", 0)), "🧾"));
            int merchantDiscount = merchantDiscount(order);
            if (merchantDiscount > 0) out.add(new Line("Diskon merchant", "- " + rupiah(merchantDiscount), "🏷️"));
            out.add(new Line("Ongkir", rupiah(order.optDouble("delivery_fee", 0)), "🛵"));
            int voucher = order.optInt("voucher_discount", 0);
            if (voucher > 0) {
                String title = first(order.optString("voucher_title"), order.optString("voucher_code"), "Promo Transiva");
                out.add(new Line(title, "- " + rupiah(voucher), "🎟️"));
            }
            String mode = first(order.optString("delivery_label"), order.optString("delivery_mode"), "");
            if (!mode.isEmpty()) out.add(new Line("Mode pengantaran", humanize(mode), "🚚"));
            return out;
        }

        if (type.contains("pickup") || type.contains("send")) {
            out.add(new Line("Isi paket", first(order.optString("item_name"), "Paket"), "📦"));
            addIf(out, "Kategori", order.optString("item_category"), "🏷️");
            addIf(out, "Ukuran paket", order.optString("package_size"), "📐");
            if (order.optInt("fragile", 0) == 1) out.add(new Line("Penanganan", "Mudah pecah / fragile", "⚠️"));
            if (order.optInt("item_value", 0) > 0) out.add(new Line("Nilai barang", rupiah(order.optInt("item_value", 0)), "💎"));
            addIf(out, "Penerima", order.optString("receiver_name"), "👤");
            addIf(out, "Nomor penerima", order.optString("receiver_phone"), "☎️");
            addDistanceLine(out, order);
            addIf(out, "Catatan", order.optString("plain_note"), "📝");
            return out;
        }

        addDistanceLine(out, order);
        int duration = order.optInt("duration_minutes", 0);
        if (duration > 0) out.add(new Line("Estimasi durasi", duration + " menit", "⏱️"));
        int voucher = order.optInt("voucher_discount", 0);
        if (voucher > 0) {
            String voucherName = first(order.optString("voucher_title"), order.optString("voucher_code"), "Promo Transiva");
            out.add(new Line(voucherName, "- " + rupiah(voucher), "🎟️"));
        }
        int rain = order.optInt("rain_surcharge", 0);
        if (rain > 0) out.add(new Line("Penyesuaian cuaca", "+ " + rupiah(rain), "🌧️"));
        String note = first(order.optString("plain_note"), extractNoteText(order));
        if (!note.isEmpty() && !looksJson(note)) out.add(new Line("Catatan", note, "📝"));
        return out;
    }

    public static int merchantDiscount(JSONObject order) {
        if (order == null) return 0;
        int direct = order.optInt("merchant_discount", 0);
        if (direct > 0) return direct;
        int total = 0;
        JSONArray items = order.optJSONArray("food_items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                total += Math.max(0, item.optInt("discount_amount", 0)) * Math.max(1, item.optInt("qty", 1));
            }
        }
        return total;
    }

    private static void addDistance(List<String> out, JSONObject order) {
        double km = order.optDouble("distance_km", 0);
        if (km > 0) out.add("Jarak " + formatKm(km));
    }
    private static void addDistanceLine(List<Line> out, JSONObject order) {
        double km = order.optDouble("distance_km", 0);
        if (km > 0) out.add(new Line("Jarak", formatKm(km), "📏"));
    }
    private static String formatKm(double km) {
        return km < 10 ? String.format(new Locale("id", "ID"), "%.1f km", km) : Math.round(km) + " km";
    }
    private static void addIf(List<Line> out, String label, String value, String icon) {
        value = first(value, "");
        if (!value.isEmpty() && !looksJson(value)) out.add(new Line(label, value, icon));
    }
    private static String extractNoteText(JSONObject order) {
        String raw = first(order.optString("note"), "");
        if (raw.isEmpty()) return "";
        try {
            JSONObject note = new JSONObject(raw);
            return first(note.optString("text"), note.optString("note"), note.optString("customer_note"), "");
        } catch (Exception ignored) { return raw; }
    }
    private static boolean looksJson(String s) {
        s = first(s, "");
        return s.startsWith("{") || s.startsWith("[");
    }
    private static String type(JSONObject order) {
        return first(order.optString("service_name"), order.optString("service_type"), order.optString("order_type"), order.optString("service"), "")
                .toLowerCase(Locale.US);
    }
    private static String shortText(String value, int max) {
        value = first(value, "");
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 3)) + "...";
    }
    private static List<String> trim(List<String> source, int max) {
        if (source.size() <= max) return source;
        return new ArrayList<>(source.subList(0, max));
    }
    private static String formatPercent(double pct) {
        if (Math.abs(pct - Math.rint(pct)) < 0.01) return String.format(Locale.US, "%.0f%%", pct);
        return String.format(Locale.US, "%.1f%%", pct);
    }
    private static String humanize(String s) {
        s = first(s, "").replace('_', ' ').trim();
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private static String rupiah(double amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        f.setMaximumFractionDigits(0);
        return f.format(Math.max(0, amount));
    }
    private static String first(String... values) {
        if (values != null) for (String value : values) {
            if (value != null) {
                String v = value.trim();
                if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
            }
        }
        return "";
    }
}
