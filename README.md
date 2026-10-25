# ⛈️ Stormy — Street-Level Weather, Live ECMWF Radar & BMKG Early Warning

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/stormy_logo.png" width="128" alt="Stormy Logo" />
</p>

**Stormy** adalah aplikasi prakiraan cuaca Android modern berbasis **Jetpack Compose** yang menggabungkan model cuaca **ECMWF IFS 9km (standar Windy)**, deteksi lokasi presisi hingga tingkat nama jalan (*street-level geocoding*), **Peringatan Dini Cuaca Ekstrem Resmi BMKG (CAP Nowcast)**, serta sistem notifikasi hujan jarak dekat anti-spam.

---

## ✨ Fitur Utama

- **📍 Street-Level GPS Accuracy**: Deteksi posisi akurat (`PRIORITY_HIGH_ACCURACY`) dengan *multi-layer reverse geocoding* (Android Google Geocoder, OpenStreetMap Nominatim, dan BigDataCloud) untuk menampilkan nama jalan dan kelurahan secara presisi.
- **🌐 Mesin Prediksi ECMWF IFS + Best Match**: Menggabungkan akurasi suhu, kode cuaca, curah hujan (`mm`), dan angin dari model **ECMWF IFS 9km** secara paralel dengan persentase peluang hujan (`%`) dan data resolusi **15-menit (`minutely_15`)**.
- **🚨 Peringatan Dini Cuaca Ekstrem Resmi BMKG**: Terintegrasi langsung dengan feed **BMKG Nowcast CAP RSS** untuk memantau peringatan cuaca ekstrem aktif di wilayah pengguna maupun pantauan nasional.
- **🌧️ Peringatan Hujan 15–45 Menit Anti-Spam**: Memeriksa potensi hujan/badai petir setiap 30 menit di *background* menggunakan **WorkManager** dan mengirimkan notifikasi tepat waktu tanpa spam berulang.
- **☀️ Ringkasan Cuaca Pagi (*Morning Briefing*)**: Rangkuman cuaca harian terstruktur (**Pagi, Siang, Sore, Malam**) lengkap dengan notifikasi otomatis setiap pagi (pukul 06:00–08:59 waktu lokal).
- **🍃 Kualitas Udara (US AQI, PM2.5, PM10) & Indeks UV**: Memantau tingkat polusi partikel debu halus dan radiasi sinar UV beserta rekomendasi kesehatan.
- **🗺️ Radar Live Windy Multi-Layer**: Peta radar interaktif langsung di dalam aplikasi dengan 4 pilihan layer (**Hujan, Angin, Awan, Petir**) dan fitur **Mode Kunci/Geser Peta** agar tidak mengganggu *scroll* layar.
- **📴 Offline Mode & Home Screen Widget**: Menyimpan data cuaca terakhir secara lokal (`WeatherCache`) agar aplikasi tetap berfungsi penuh saat tanpa internet, dilengkapi **Widget Android** untuk memantau cuaca langsung dari layar utama.

---

## 🛠️ Teknologi & Arsitektur

- **Bahasa**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3, Custom Canvas Vector Icons, Bento Grid Architecture)
- **Background Processing**: AndroidX WorkManager (`CoroutineWorker`)
- **Networking & Parsing**: Retrofit 2, OkHttp 3, Gson, Android `XmlPullParser` (BMKG CAP RSS)
- **Location Services**: Google Play Services `FusedLocationProviderClient` & `SettingsClient`
- **Data Sources**:
  - [Open-Meteo ECMWF & Air Quality API](https://open-meteo.com/)
  - [BMKG Nowcast Early Warning RSS](https://www.bmkg.go.id/)
  - [Windy Live Radar Embed](https://www.windy.com/)
  - [OpenStreetMap Nominatim](https://nominatim.org/)

---

## 🚀 Cara Menjalankan Project

1. Clone repository ini:
   ```bash
  git clone https://github.com/Devid-Winata/StormyApp.git
   ```
2. Buka folder project di **Android Studio**.
3. Tunggu proses *Gradle Sync* selesai (tidak memerlukan API Key berbayar).
4. Klik **Run (`▶`)** pada perangkat Android atau Emulator.

---

**Developed by [Devid Winata](https://github.com/Devid-Winata)**
