package com.transiva.app;

/** Customer language only: never writes an order, price, payment, or database status. */
public final class CustomerBeginnerCopy {
    private CustomerBeginnerCopy() {}
    public static String servicePurpose(String service) {
        if ("TransRide".equalsIgnoreCase(service)) return "Naik motor";
        if ("TransCar".equalsIgnoreCase(service)) return "Naik mobil";
        if ("TransFood".equalsIgnoreCase(service)) return "Pesan makanan";
        if ("TransShop".equalsIgnoreCase(service)) return "Belanja kebutuhan";
        if ("TransSend".equalsIgnoreCase(service)) return "Kirim barang";
        return "Lihat layanan";
    }
    public static String tripHint(boolean pickupReady, boolean destinationReady, boolean quoteReady) {
        if (!pickupReady) return "1. Pastikan lokasi jemput benar. Ketuk Jemput untuk mengubahnya atau gunakan lokasi Anda.";
        if (!destinationReady) return "2. Mau diantar ke mana? Ketuk tujuan, cari nama tempat, atau pilih titik di peta.";
        if (!quoteReady) return "3. Biaya sedang disiapkan. Periksa jemput dan tujuan; jika belum berhasil, ketuk Coba lagi.";
        return "4. Periksa tujuan, biaya, dan pembayaran. Tombol di bawah membuka konfirmasi sebelum pesanan dikirim.";
    }
    public static String paymentHelp(String method) {
        return "balance".equals(method)
                ? "Pembayaran menggunakan saldo Transiva Pay. Pastikan saldo cukup sebelum memesan."
                : "Bayar tunai kepada driver sesuai tagihan pesanan. Tidak perlu mengisi saldo untuk pembayaran tunai.";
    }
    public static final String[] SUPPORT_CHOICES = {
            "Driver belum datang", "Lokasi jemput salah", "Bingung cara membayar",
            "Barang atau makanan bermasalah", "Belum paham cara memesan"
    };
    public static int supportCategory(int choice) {
        return choice == 2 ? 1 : choice == 3 ? 3 : choice == 4 ? 4 : 0;
    }
    public static String supportDetail(int choice) {
        switch (choice) {
            case 0: return "Driver belum tiba di lokasi jemput. Mohon bantu periksa perkembangan pesanan saya.";
            case 1: return "Saya mengalami kendala pada lokasi jemput. Mohon bantu mengonfirmasi lokasi yang benar.";
            case 2: return "Saya belum memahami cara pembayaran pesanan. Mohon panduan dari admin Transiva.";
            case 3: return "Saya mengalami masalah pada barang atau makanan pesanan. Mohon bantuan admin Transiva.";
            default: return "Saya belum memahami langkah memesan di Transiva. Mohon panduan dari admin.";
        }
    }
}
