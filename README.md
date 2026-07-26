# Transiva Customer Android

Native Android app khusus **Customer Transiva**.

## Scope aktif
- Login/register customer + PIN
- Dashboard customer
- TransRide / TransCar / TransPickup
- TransFood / TransLaundry / Transtour
- Pencarian driver dan customer trip
- Chat customer, notifikasi FCM, wallet/top-up, riwayat, profil
- Pemeriksaan & download update aplikasi

## Pemisahan role
Source dan komponen runtime khusus Driver, Merchant, dan Admin telah dikeluarkan dari aplikasi ini. Akun non-customer ditolak pada proses login agar tidak salah masuk ke dashboard customer.

## Build
Project memakai Android Gradle Plugin 8.6.1, Gradle 8.9 (CI), Java 17, compile/target SDK 35.

Release mengaktifkan R8 (`minifyEnabled`) dan resource shrinking untuk mengurangi ukuran APK/AAB dan membuang kode/resource yang tidak dipakai.

### GitHub Actions secrets
- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Push ke branch `main` atau jalankan workflow manual untuk menghasilkan signed APK dan AAB.

## Backend
Backend tetap memakai API Transiva yang sama (`https://transiva.my.id/server/`). Pemisahan ini hanya memisahkan aplikasi Android berdasarkan role; database/order/chat/payment tetap dapat dipakai bersama.
