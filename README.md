# LMS Unindra

Aplikasi Android native berbasis Jetpack Compose untuk membantu mahasiswa mengakses LMS Unindra dari perangkat mobile. Proyek ini menangani login, mengambil data dashboard, menampilkan daftar pertemuan, membuka materi, melihat rekap presensi, mengunduh file, dan mengunggah tugas langsung dari aplikasi.

## Fitur Utama

- Login ke LMS Unindra menggunakan NIM dan password.
- Auto-login menggunakan kredensial yang disimpan secara terenkripsi.
- Pemecahan captcha matematika otomatis dengan ML Kit Text Recognition.
- Menampilkan profil mahasiswa dan daftar mata kuliah dari dashboard LMS.
- Menampilkan daftar pertemuan per mata kuliah.
- Menampilkan detail materi per pertemuan, termasuk file dan tautan eksternal.
- Rekap presensi per mata kuliah.
- Download materi ke folder `Downloads` dengan progress notification.
- Upload tugas langsung dari file picker dengan progress indicator.
- Pull-to-refresh di beberapa halaman utama.

## Stack Teknologi

- Kotlin
- Android SDK
- Jetpack Compose
- Material 3
- Navigation Compose
- OkHttp
- Jsoup
- ML Kit Text Recognition
- Coil
- AndroidX Security Crypto
- Kotlin Coroutines

## Struktur Proyek

```text
.
├── app/
│   ├── src/main/java/com/gaje48/elemes/
│   │   ├── MainActivity.kt
│   │   ├── ViewModel.kt
│   │   ├── LmsRepository.kt
│   │   ├── Login.kt
│   │   ├── Dashboard.kt
│   │   ├── MeetingList.kt
│   │   ├── MeetingDetail.kt
│   │   ├── Presence.kt
│   │   ├── Task.kt
│   │   └── Models.kt
│   └── src/main/res/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Alur Aplikasi

1. Pengguna login dengan NIM dan password LMS.
2. Aplikasi mengambil halaman login, membaca captcha, lalu mencoba login otomatis.
3. Setelah berhasil, aplikasi mem-parsing dashboard LMS untuk mengambil:
   - data mahasiswa,
   - daftar mata kuliah,
   - daftar pertemuan,
   - data presensi.
4. Pengguna dapat membuka detail pertemuan, mengunduh materi, melihat tugas, upload jawaban, dan mengecek presensi.

## Persyaratan

- Android Studio versi terbaru yang mendukung:
  - Android Gradle Plugin `9.1.0`
  - Kotlin `2.3.20`
- JDK 11
- Perangkat atau emulator Android dengan minimum SDK 29
- Koneksi internet untuk mengakses `https://lms.unindra.ac.id`

## Cara Menjalankan

### 1. Clone repository

```bash
git clone <url-repository>
cd lms-unindra
```

### 2. Buka di Android Studio

- Pilih `Open` lalu arahkan ke folder proyek ini.
- Tunggu proses Gradle sync selesai.

### 3. Jalankan aplikasi

- Hubungkan perangkat Android atau jalankan emulator.
- Klik tombol `Run` di Android Studio.

Atau lewat terminal:

```bash
./gradlew installDebug
```

## Build APK

Untuk build debug:

```bash
./gradlew assembleDebug
```

Untuk build release:

```bash
./gradlew assembleRelease
```

APK hasil build biasanya berada di:

```text
app/build/outputs/apk/
```

## Permission yang Digunakan

- `INTERNET` untuk komunikasi dengan LMS Unindra.
- `POST_NOTIFICATIONS` untuk notifikasi progress download dan upload pada Android 13+.

## Catatan Implementasi

- Kredensial pengguna disimpan menggunakan `EncryptedSharedPreferences`.
- Sesi login dipertahankan menggunakan `CookieJar` dari OkHttp.
- Data LMS diambil dengan pendekatan HTML scraping menggunakan Jsoup.
- Auto-login akan mencoba ulang captcha hingga 3 kali.
- Upload tugas membatasi ukuran file maksimal 20 MB.
- Beberapa endpoint LMS dipanggil secara langsung, sehingga perubahan struktur HTML atau endpoint dari pihak LMS dapat memengaruhi aplikasi.

## Keterbatasan

- Proyek ini bergantung pada struktur halaman LMS Unindra saat ini.
- Jika captcha, form login, atau markup halaman berubah, fitur login atau parsing data bisa ikut rusak.
- Nama aplikasi yang tampil di resource saat ini masih menggunakan placeholder `Lorem Ipsum` dan bisa disesuaikan lagi.

## Disclaimer

Proyek ini tampak dibuat untuk mempermudah akses ke LMS Unindra dan bukan aplikasi resmi kampus. Gunakan secara bertanggung jawab, terutama karena aplikasi menyimpan sesi login dan berinteraksi langsung dengan layanan LMS.

## Pengembangan Lanjutan

Beberapa ide pengembangan berikut bisa dipertimbangkan:

- Menambahkan screenshot aplikasi ke README.
- Menambahkan unit test dan instrumented test yang relevan.
- Menambahkan mode offline/caching sederhana untuk data terakhir.
- Memisahkan layer jaringan, parser, dan UI agar lebih mudah dirawat.
- Mengganti placeholder nama aplikasi dan branding visual.

## Lisensi

Belum ada file lisensi di repository ini. Jika proyek akan dipublikasikan, sebaiknya tambahkan lisensi yang sesuai.
