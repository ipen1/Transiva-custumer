# Mengukur keandalan 2.5.29 pada HP nyata

## Data yang sudah ditambahkan

Firebase Analytics yang sudah digunakan aplikasi menerima event berikut, maksimal 20 sampel read/dashboard per proses:

- `customer_startup_sample`: `application_to_screen_ms`, `java_heap_kb`, `native_heap_kb`. Ini durasi dari Application.onCreate menuju pre-draw layar normal pertama setelah Splash, bukan TTID/TTFD resmi. Login/update/network gate dapat memperpanjang hasilnya. Java/native heap bukan total PSS.
- `customer_json_load`: `category` (`profile`, `food`, `tour`, `dashboard`, `other_read`), `duration_ms`, `result`. Read JSON mengukur sampai body selesai dibaca/di-parse; bukan sampai gambar atau peta selesai terlihat. Kategori dashboard mengukur penyelesaian repository gabungan. Read yang masih memakai jalur jaringan lain belum tercakup.

Tidak mengirim URL/query, username, alamat, saldo, OTP, token atau payload. Sampel tidak menggantikan benchmark terkontrol. Profil dengan cache fresh tidak perlu HTTP sehingga tidak menghasilkan sampel read baru.

Crash tetap dilaporkan oleh Crashlytics yang sudah ada. `TransivaAnrWatchdog` melaporkan stall main thread sebagai nonfatal; jangan menghitungnya sebagai ANR resmi. Gunakan laporan Android vitals di Play Console untuk crash/ANR pengguna, serta laporan Crashlytics untuk investigasi.

## Jika hanya memakai GitHub dan HP

1. Build dan pasang versi release pada tester. Uji minimal HP RAM rendah, kelas menengah, dan Android terbaru yang tersedia; sertakan versi Android minimum yang didukung bila ada perangkatnya.
2. Gunakan jaringan normal, lambat, offline lalu pulih. Uji cache dingin, cache hangat, akun berganti, background/foreground serta 30–60 menit pemesanan/peta/notifikasi.
3. Catat waktu hingga layanan bisa digunakan, hasil pemesanan, crash, freeze dan perangkat di `device-results.csv`. Jangan menulis data pribadi tester dalam laporan.
4. Periksa event Analytics dan Crashlytics sesuai konfigurasi Firebase proyek. Laporan vitals memerlukan distribusi/penggunaan Play dan tidak muncul hanya karena build GitHub berhasil.

## Jika tersedia komputer dengan adb

Pasang build release pada satu HP dengan debugging USB, kemudian:

```bash
bash tools/performance/capture_device.sh hasil-hp
```

Script membuat 10 sampel startup cold dengan force-stop (tidak menghapus akun/cache) dan dump memori. Startup `am start -W` berakhir pada Activity Splash; jangan menyebutnya waktu dashboard siap. Jika ada lebih dari satu perangkat, pilih perangkat via environment ANDROID_SERIAL.

Untuk cache benar-benar dingin, gunakan fresh install atau bersihkan cache aplikasi melalui alur pengujian yang terkendali; jangan otomatis menjalankan `pm clear` pada HP pengguna. Profil/peta/gambar siap dan layanan end-to-end perlu diukur tersendiri dengan rekaman layar atau profiler.

## Target internal dan keputusan

Target awal yang diusulkan, bukan syarat resmi Google: pada jaringan normal cold startup menuju dashboard p95 <=3 detik, read profil/layanan p95 <=2 detik, dan tidak ada crash/ANR/ketidaksesuaian saldo yang teramati dalam skenario pengujian. Sesuaikan target setelah baseline perangkat dan jaringan diukur. Memori harus stabil pada sesi panjang, tidak terus tumbuh setiap masuk/keluar layar.

Hitung median/p95 per perangkat dan kategori; jangan gabungkan HP cepat dan HP lambat untuk menyembunyikan regresi. Bandingkan 2.5.28 vs 2.5.29 pada perangkat, kondisi cache dan jaringan yang sama. Jangan memberikan penilaian performa numerik sebelum data tersedia.
