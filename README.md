TRANSIVA CUSTOMER - FIX API 36 + REGRESSION TEST + OVERLAY UX
=============================================================

Cara pakai:
1. Backup project Anda.
2. Ekstrak ZIP ini di root project Transiva Customer.
3. Izinkan replace file dengan path yang sama.
4. Pastikan Android SDK Platform 36 terpasang pada environment build.
5. Build seperti biasa melalui workflow GitHub build-aab.yml.

Perubahan:
- compileSdk 35 -> 36
- targetSdk 35 -> 36
- Android Gradle Plugin 8.6.1 -> 8.10.1
- Gradle CI 8.9 -> 8.11.1
- Java tetap 17
- minSdk tetap 23
- Menambahkan JUnit 4.13.2 dan regression test untuk state order/chat customer.
- CI menjalankan testDebugUnitTest sebelum membuat APK/AAB release.
- Splash tidak lagi meminta SYSTEM_ALERT_WINDOW / overlay.
- Permission overlay tetap dipertahankan untuk kompatibilitas incoming WebRTC call.
- Pengguna dapat mengaktifkan overlay secara opsional melalui Pengaturan Aplikasi > Panggilan Masuk.
- Tanpa overlay, mekanisme full-screen call notification yang sudah ada tetap dipertahankan.

File yang di-replace/ditambahkan:
- build.gradle
- app/build.gradle
- .github/workflows/build-aab.yml
- app/src/main/java/com/transiva/app/SplashActivity.java
- app/src/main/java/com/transiva/app/CustomerSettingsActivity.java
- app/src/test/java/com/transiva/app/CustomerMessageStatusTest.java (file baru)

Catatan:
- Tidak ada endpoint API, applicationId, signing config, minSdk, struktur order, FCM service,
  WebRTC signaling, database, atau layout fitur utama yang diubah.
