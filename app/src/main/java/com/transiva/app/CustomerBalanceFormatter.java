package com.transiva.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Presentation policy extracted from CustomerBalanceHistoryActivity. */
public final class CustomerBalanceFormatter {
    private CustomerBalanceFormatter() {}

    public static String transactionTitle(String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.US);
        if (value.contains("deposit")) return "Deposit Saldo";
        if (value.contains("transfer_in")) return "Transfer Masuk";
        if (value.contains("transfer_out")) return "Transfer Keluar";
        if (value.contains("withdraw")) return "Withdraw Dana";
        if (value.contains("refund")) return "Refund Pesanan";
        if (value.contains("payment") || value.contains("order")) return "Pembayaran Pesanan";
        return "Mutasi Saldo";
    }

    public static String statusLabel(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.US);
        if (value.equals("pending") || value.equals("processing")) return "Diproses";
        if (value.equals("failed") || value.equals("rejected") || value.equals("cancelled")) return "Gagal";
        return "Berhasil";
    }

    public static String statusColor(String status) {
        String label = statusLabel(status);
        if (label.equals("Diproses")) return "#D97706";
        if (label.equals("Gagal")) return "#D9485F";
        return "#0E9F4B";
    }

    public static String statusBackground(String status) {
        String label = statusLabel(status);
        if (label.equals("Diproses")) return "#FFF7E6";
        if (label.equals("Gagal")) return "#FFF0F2";
        return "#ECFDF5";
    }

    public static String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"};
        for (String format : formats) {
            try {
                Date date = new SimpleDateFormat(format, Locale.US).parse(value.trim());
                if (date != null) return new SimpleDateFormat("dd MMM yyyy • HH:mm", new Locale("id", "ID")).format(date);
            } catch (Exception ignored) {}
        }
        return value;
    }
}
