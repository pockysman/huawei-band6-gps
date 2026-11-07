# Huawei Band 6 • GPS HUD & Turn-by-Turn Companion

Aplikasi Android ringan (Kotlin) yang menyulap **Huawei Band 6** Anda menjadi layar **GPS Navigasi Arah (Google Maps/Waze) + Speedometer Live** menggunakan 2 jalur pintar sekaligus:

1. **Live HUD via Layar Kontrol Musik (`MediaSession`) — Tanpa Getar Terus-Menerus!**
   Saat Anda membuka menu **Musik** di Huawei Band 6, baris *Judul Lagu* dan *Nama Artis* digantikan secara *real-time* oleh instruksi belokan, kecepatan (`km/jam`), arah kompas, dan jarak tempuh!
   - Tekan tombol **Next (`⏭️`)** atau **Prev (`⏮️`)** langsung dari layar Huawei Band 6 untuk berpindah antar **3 Mode Layar**:
     - **Mode 1 (Navigasi + Speed):** `⬅️ 150 m • 42 km/j` / `Jl. Jend. Sudirman (14:30)`
     - **Mode 2 (Speedometer & Odometer):** `⚡ 42 km/j (Maks 68)` / `📏 3.42 km • 🧭 TL • ⛰️ 120m`
     - **Mode 3 (Koordinat & Akurasi GPS):** `📍 -6.20876, 106.84560` / `🎯 ±4m • ⚡ 42 km/j • 🧭 U`
   - Tekan tombol **Play/Pause (`⏯️`)** di Huawei Band 6 untuk mengirim *popup* detail lengkap ke layar jam.

2. **Notifikasi Getar Pintar Saat Mendekati Belokan (`NotificationListener`)**
   Aplikasi secara otomatis membaca arah navigasi dari **Google Maps / Waze** dan mengirimkan notifikasi bergetar ke Huawei Band 6 **hanya saat arah belokan berubah**, mencapai jarak kritis (`200m`, `100m`, `50m`), atau saat Anda melewati batas kecepatan (`80 km/jam`).

---

## Cara Build Menjadi File `.APK`

### Opsi A: Menggunakan Android Studio (Lokal)
1. Buka **Android Studio** -> pilih **Open** -> arahkan ke folder `C:\Users\erlan\.gemini\antigravity\scratch\huawei-band6-gps`.
2. Tunggu proses *Gradle Sync* selesai.
3. Pilih menu **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. Instal file `app-debug.apk` ke HP Android Anda.

### Opsi B: Tanpa Install Android Studio (Otomatis via GitHub Cloud)
Proyek ini sudah dilengkapi dengan konfigurasi [build-apk.yml](.github/workflows/build-apk.yml):
1. Upload folder ini ke repositori GitHub Anda.
2. Buka tab **Actions** di GitHub -> klik workflow **Build Band 6 GPS HUD APK** -> unduh file `HuaweiBand6-GPS-HUD-apk` yang sudah siap diinstal di HP!
