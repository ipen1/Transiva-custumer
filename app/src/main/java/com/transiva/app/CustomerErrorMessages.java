package com.transiva.app;

import java.util.Locale;

/** Keep implementation details in diagnostics, and give people a useful next action. */
public final class CustomerErrorMessages {
    private CustomerErrorMessages() { }
    public static boolean containsTechnicalDetails(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("sqlstate") || lower.contains("mysqli_") || lower.contains("java.lang.")
                || lower.contains("exception") || lower.contains("<html") || lower.contains("<!doctype")
                || lower.contains("http 4") || lower.contains("http 5") || lower.contains("/var/www")
                || lower.contains("/home/") || lower.contains("fatal error:") || lower.contains("warning:");
    }
    public static String humanize(String raw, String fallback) {
        String safe = fallback == null || fallback.trim().isEmpty()
                ? "Belum berhasil. Silakan coba lagi sebentar." : fallback.trim();
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) return safe;
        String message = raw.trim(), lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("antrean jaringan") || lower.contains("queue full") || lower.contains("rejectedexecution"))
            return "Aplikasi sedang sibuk. Tunggu sebentar, lalu coba lagi.";
        if (lower.contains("pendaftaran belum diaktifkan") || lower.contains("penyimpanan akun belum siap"))
            return "Pendaftaran sedang disiapkan. Coba lagi nanti atau hubungi bantuan Transiva.";
        if (lower.contains("maksimal 72 byte"))
            return "Gunakan minimal 8 karakter untuk kata sandi. Jika terlalu panjang, kurangi huruf atau simbolnya.";
        if (lower.contains("dns") || lower.contains("unknownhost") || lower.contains("unable to resolve host"))
            return "Belum bisa terhubung ke Transiva. Periksa internet Anda, lalu coba lagi.";
        if (lower.contains("timeout") || lower.contains("timed out"))
            return "Koneksi membutuhkan waktu lebih lama. Periksa internet Anda, lalu coba lagi.";
        if (lower.contains("ssl") || lower.contains("tls") || lower.contains("certificate"))
            return "Koneksi aman belum berhasil. Pastikan tanggal dan jam HP benar, lalu coba lagi.";
        if (lower.contains("oauth") || lower.contains("client id") || lower.contains("token google"))
            return "Masuk dengan Google belum berhasil. Pilih kembali akun Google Anda, lalu coba lagi.";
        if (lower.contains("token tidak ditemukan") || lower.contains("unauthorized") || lower.contains("sesi telah berakhir"))
            return "Sesi akun Anda sudah berakhir. Silakan masuk kembali.";
        if (lower.contains("sql") || lower.contains("exception") || lower.contains("stack trace")
                || lower.contains("<html") || lower.contains("<!doctype") || lower.contains("<?php")
                || lower.contains("<br") || lower.contains("json") || lower.contains("http ")
                || lower.contains("/var/www") || lower.contains("/home/") || lower.contains("fatal error")
                || lower.contains("warning:") || lower.contains("undefined ") || message.length() > 600)
            return safe;
        return message;
    }
}
