package com.transiva.app;

import java.util.Calendar;
import java.util.Locale;

/** Central formatter for urgent customer arrival messages. */
public final class CustomerArrivalMessage {
    private CustomerArrivalMessage() {}

    public static final class Content {
        public final String title;
        public final String body;
        public final String badge;
        public final String headline;

        Content(String title, String body, String badge, String headline) {
            this.title = title;
            this.body = body;
            this.badge = badge;
            this.headline = headline;
        }
    }

    public static Content build(
            String event,
            String orderType,
            String source,
            String restaurantName,
            String driverName,
            String orderId
    ) {
        boolean pickup = "arrived_pickup".equalsIgnoreCase(clean(event));
        String service = normalizeService(orderType, source);
        String driver = clean(driverName).isEmpty() ? "Driver kamu" : clean(driverName);
        String restaurant = clean(restaurantName).isEmpty() ? "restoran" : clean(restaurantName);
        String time = dayPart();
        int variant = variant(orderId, pickup ? 11 : 29);

        if ("food".equals(service)) {
            if (pickup) {
                String[] headlines = {
                        driver + " sudah tiba di " + restaurant + " untuk mengambil pesanan kamu 🍽️",
                        "Pesananmu segera dijemput — " + driver + " sudah sampai di " + restaurant + " ✨",
                        time + " makin dekat ke waktu makan — " + driver + " sudah tiba di " + restaurant + " 🙌",
                        driver + " sudah berada di restoran dan bersiap membawa pesananmu 🛵"
                };
                return new Content(
                        "Driver sudah tiba di restoran",
                        driver + " sudah tiba di " + restaurant + " untuk mengambil pesanan kamu.",
                        "🍽️ DRIVER TIBA DI RESTORAN",
                        headlines[variant % headlines.length]
                );
            }
            String[] headlines = {
                    "Pesanan makanan kamu sudah tiba di titik pengantaran 🍜",
                    "Makananmu sudah sampai — silakan cek pesanan sebelum diterima ✨",
                    time + " pesananmu tiba dengan selamat. Selamat menikmati! 🙌",
                    driver + " sudah tiba membawa pesanan makanan kamu 🛵"
            };
            return new Content(
                    "Pesanan makanan sudah tiba",
                    "Driver sudah tiba di titik pengantaran dengan pesanan makanan kamu.",
                    "🍜 PESANAN SUDAH TIBA",
                    headlines[variant % headlines.length]
            );
        }

        if ("send".equals(service)) {
            if (pickup) {
                String[] headlines = {
                        driver + " yang akan menjemput kiriman kamu sudah tiba 📦",
                        "Kurir sudah sampai di titik penjemputan kiriman kamu ✨",
                        time + " kirimanmu siap dijemput — " + driver + " sudah tiba 🙌",
                        driver + " sudah menunggu di lokasi penjemputan paket 📍"
                };
                return new Content(
                        "Driver tiba untuk menjemput kiriman",
                        driver + " sudah tiba di titik penjemputan kiriman kamu.",
                        "📦 DRIVER TIBA DI PENJEMPUTAN",
                        headlines[variant % headlines.length]
                );
            }
            String[] headlines = {
                    "Kiriman kamu sudah tiba di titik pengantaran 📦",
                    driver + " sudah sampai di lokasi penerima ✨",
                    "Paket sudah berada di titik pengantaran. Pastikan penerima siap ya 🙌",
                    time + " kirimanmu berhasil sampai ke lokasi tujuan 🎉"
            };
            return new Content(
                    "Kiriman tiba di pengantaran",
                    "Driver sudah tiba di titik pengantaran kiriman kamu.",
                    "📦 KIRIMAN SUDAH TIBA",
                    headlines[variant % headlines.length]
            );
        }

        // TransRide / TransCar and compatible passenger services.
        if (pickup) {
            String[] headlines = {
                    driver + " yang mau jemput kamu sudah tiba di titik jemput 🚘",
                    "Siap berangkat? " + driver + " sudah menunggu di titik penjemputan ✨",
                    time + " perjalananmu siap dimulai — " + driver + " sudah tiba 🙌",
                    "Driver kamu sudah sampai. Silakan menuju titik jemput 📍"
            };
            return new Content(
                    "Driver sudah tiba di titik jemput",
                    driver + " yang akan menjemput kamu sudah tiba di titik penjemputan.",
                    "📍 DRIVER SUDAH TIBA",
                    headlines[variant % headlines.length]
            );
        }

        String[] headlines = {
                "Kamu sudah tiba di titik pengantaran 🏁",
                driver + " sudah sampai di tujuan kamu ✨",
                "Perjalanan selesai dengan baik — kamu sudah sampai 🙌",
                time + " perjalananmu sampai di titik pengantaran 🎉"
        };
        return new Content(
                "Sudah tiba di titik pengantaran",
                "Driver sudah tiba di titik pengantaran sesuai tujuan perjalanan kamu.",
                "🏁 SAMPAI DI PENGANTARAN",
                headlines[variant % headlines.length]
        );
    }

    private static String normalizeService(String orderType, String source) {
        String t = (clean(orderType) + " " + clean(source)).toLowerCase(Locale.ROOT);
        if (t.contains("food")) return "food";
        if (t.contains("pickup") || t.contains("send")) return "send";
        return "ride";
    }

    private static String dayPart() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 11) return "Pagi ini";
        if (hour < 15) return "Siang ini";
        if (hour < 18) return "Sore ini";
        return "Malam ini";
    }

    private static int variant(String orderId, int salt) {
        int hourBlock = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 4;
        return Math.abs((clean(orderId) + "|" + hourBlock + "|" + salt).hashCode());
    }

    private static String clean(String v) {
        return v == null ? "" : v.trim();
    }
}
