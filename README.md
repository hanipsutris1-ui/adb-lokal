# ADB Lokal

Aplikasi penjaga ADB untuk head unit Android (com.omen.adblokal).

Tugasnya satu: menjaga ADB head unit tetap bisa dijangkau dari luar lewat
**port tetap 5555**, tanpa pemilik perlu membuka Opsi pengembang atau
membaca angka port acak Wireless debugging setiap sesi.

## Cara kerja

Setiap head unit selesai boot (dan kapan pun diminta dari layar utama),
aplikasi ini:

1. Menyalakan Wireless debugging sendiri — memakai izin
   `WRITE_SECURE_SETTINGS` yang diberikan sekali saat pemasangan
   (atau yang ia berikan ke dirinya sendiri lewat shell, lihat langkah 3).
2. Menyambung ke adbd perangkat yang sama lewat pustaka
   [libadb-android](https://github.com/MuntashirAkon/libadb-android)
   (port sambungan ditemukan otomatis via mDNS; pairing hanya sekali).
3. Memberi izin `WRITE_SECURE_SETTINGS` ke dirinya sendiri lewat shell ADB.
4. Meminta adbd mendengarkan port tetap 5555 (permintaan `tcpip`,
   cadangan `setprop service.adb.tcp.port`), lalu membuktikan
   `127.0.0.1:5555` menjawab.

Kegagalan awal sesudah boot (Wi-Fi belum tersambung, dll.) dicoba lagi
tiap menit sampai ±12 kali. Semua langkah tercatat di layar utama aplikasi.

## Pemasangan awal (sekali saja)

1. Pasang APK rilis.
2. Buka aplikasi → bagian Pemasangan → buka Wireless debugging di
   Opsi pengembang → "Pasangkan perangkat dengan kode pemasangan".
3. Isi port pemasangan + kode 6 digit di aplikasi, tekan **Pasangkan**
   selagi dialog pemasangan masih terbuka.
4. Aplikasi memberi izinnya sendiri dan langsung mengunci port.
   (Cadangan: `adb shell pm grant com.omen.adblokal android.permission.WRITE_SECURE_SETTINGS`.)

## Bangun & tanda tangan

Dibangun oleh GitHub Actions (Gradle 8.9, JDK 17). Berkas keystore
(ter-Base64) disimpan di repo ini sebagai `keystore.b64.txt` — repo ini
privat, dan keystore tanpa kata sandinya tidak bisa dipakai
menandatangani apa pun. Kata sandinya sendiri tidak ada di repo;
Actions mengambilnya dari secret:

- `ADBLOKAL_KEYSTORE_PASSWORD` — kata sandi keystore (alias `adblokal`)

Setiap push terbit sebagai Release `v<nomor run>` berisi `ADBLokal-<n>.apk`.
Secret lama `ADBLOKAL_KEYSTORE_B64` tidak lagi dipakai dan boleh dihapus.
