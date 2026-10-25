package com.example.stormy

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.concurrent.TimeUnit

/**
 * Cek cuaca di background tiap 30 menit:
 *  - Simpan data terbaru (untuk widget & mode offline)
 *  - Kirim peringatan hujan TANPA spam (maksimal 1x per jenis peringatan per kejadian hujan)
 */
class RainAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val (lat, lon, city) = resolveHomeLocation(ctx)
            val res = ApiClient.getForecastEcmwf(lat, lon)

            WeatherCache.save(ctx, WeatherCache.SLOT_HOME, res, city, lat, lon)
            StormyWidget.refreshAll(ctx)

            RainAlertEngine.evaluate(ctx, res, city)
            MorningBriefingEngine.checkAndSendIfDue(ctx, res, city)
            MorningBriefingEngine.checkAndSendBmkgAlert(ctx, city, lat, lon)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Lokasi "rumah" untuk notif & widget: lokasi HP (GPS), BUKAN kota yang sedang dicari di app. */
    private suspend fun resolveHomeLocation(ctx: Context): Triple<Double, Double, String> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var lat = prefs.getFloat("gps_lat", prefs.getFloat("lat", -2.1316f)).toDouble()
        var lon = prefs.getFloat("gps_lon", prefs.getFloat("lon", 106.1169f)).toDouble()
        var city = prefs.getString("gps_city", null)
            ?: prefs.getString("city", "Pangkalpinang") ?: "Pangkalpinang"

        val hasPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            try {
                @Suppress("MissingPermission")
                val last = withTimeoutOrNull(5000L) {
                    LocationServices.getFusedLocationProviderClient(ctx).lastLocation.await()
                }
                // Pakai lokasi terakhir HP kalau masih baru (< 1 jam)
                if (last != null && System.currentTimeMillis() - last.time < 60 * 60 * 1000L) {
                    val dist = FloatArray(1)
                    Location.distanceBetween(lat, lon, last.latitude, last.longitude, dist)
                    if (dist[0] > 1000f) {
                        city = getPreciseStreetAddress(ctx, last.latitude, last.longitude)
                    }
                    lat = last.latitude
                    lon = last.longitude
                    prefs.edit()
                        .putFloat("gps_lat", lat.toFloat())
                        .putFloat("gps_lon", lon.toFloat())
                        .putString("gps_city", city)
                        .apply()
                }
            } catch (_: Exception) {}
        }
        if (city.contains("Mendeteksi", ignoreCase = true)) city = "Pangkalpinang"
        return Triple(lat, lon, city)
    }

    companion object {
        const val PREFS = "stormy_prefs"
        private const val PERIODIC_NAME = "stormy_rain_check"
        private const val NOW_NAME = "stormy_rain_check_now"

        private val netConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Jadwalkan cek tiap 30 menit (hanya jalan saat ada internet). */
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RainAlertWorker>(30, TimeUnit.MINUTES)
                .setConstraints(netConstraint)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        /** Jalankan sekali sekarang (misal setelah lokasi GPS berubah). */
        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<RainAlertWorker>()
                .setConstraints(netConstraint)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

// =========================================================
// MESIN PERINGATAN HUJAN ANTI-SPAM
// =========================================================
object RainAlertEngine {
    private const val KEY_ACTIVE = "alert_event_active"
    private const val KEY_STARTED = "alert_event_started_at"
    private const val KEY_HEADSUP = "alert_headsup_sent"
    private const val KEY_SOON = "alert_soon_sent"
    private const val KEY_STORM = "alert_storm_sent"
    private const val EVENT_MAX_MS = 6 * 60 * 60 * 1000L

    fun evaluate(ctx: Context, res: WeatherResponse, city: String) {
        val prefs = ctx.getSharedPreferences(RainAlertWorker.PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // Kejadian hujan lama (> 6 jam) dianggap selesai
        if (prefs.getBoolean(KEY_ACTIVE, false) && now - prefs.getLong(KEY_STARTED, 0L) > EVENT_MAX_MS) {
            resetEvent(prefs)
        }

        val rainingNow = WeatherHelper.isRainCode(res.current.weather_code) && res.current.precipitation > 0.0
        val soon = findRainSoon(res)          // 15-45 menit lagi (data per 15 menit)
        val headsUp = findRainNextHours(res)  // 1-3 jam lagi (data per jam)

        // Cuaca kering di seluruh jangkauan prediksi → kejadian hujan selesai, siap peringatan berikutnya
        if (!rainingNow && soon == null && headsUp == null) {
            resetEvent(prefs)
            return
        }

        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            prefs.edit().putBoolean(KEY_ACTIVE, true).putLong(KEY_STARTED, now).apply()
        }

        // Sedang hujan: user sudah tahu, jangan kirim "akan hujan"
        if (rainingNow) {
            prefs.edit().putBoolean(KEY_HEADSUP, true).putBoolean(KEY_SOON, true).apply()
            return
        }

        val stormComing = (soon?.second?.let { it >= 95 } == true) || (headsUp?.second?.let { it >= 95 } == true)

        when {
            stormComing && !prefs.getBoolean(KEY_STORM, false) -> {
                val time = (soon ?: headsUp)!!.first
                NotificationHelper.sendRainNotification(
                    ctx,
                    "⛈️ Waspada Badai Petir",
                    "${WeatherHelper.dayPrefix(time, res)} sekitar pukul ${time.takeLast(5)} diperkirakan terjadi badai petir di $city. " +
                        "Hindari area terbuka dan cabut perangkat elektronik yang tidak perlu."
                )
                prefs.edit().putBoolean(KEY_STORM, true).putBoolean(KEY_SOON, true).putBoolean(KEY_HEADSUP, true).apply()
            }
            soon != null && !prefs.getBoolean(KEY_SOON, false) -> {
                val (time, code) = soon
                NotificationHelper.sendRainNotification(
                    ctx,
                    "${WeatherHelper.getConditionEmoji(code)} ${WeatherHelper.getConditionLabel(code)} sebentar lagi",
                    "Diperkirakan ${WeatherHelper.getConditionLabel(code).lowercase()} mulai sekitar pukul ${time.takeLast(5)} " +
                        "di $city (±${minutesUntil(time, res)} menit lagi). Siapkan payung atau jas hujan ya!"
                )
                prefs.edit().putBoolean(KEY_SOON, true).putBoolean(KEY_HEADSUP, true).apply()
            }
            headsUp != null && !prefs.getBoolean(KEY_HEADSUP, false) -> {
                val idx = res.hourly.time.indexOf(headsUp.first).coerceAtLeast(0)
                val msg = WeatherHelper.buildForecastMessage(
                    isoTime = headsUp.first,
                    code = headsUp.second,
                    location = city,
                    temp = res.hourly.temperature_2m[idx].toInt(),
                    prob = res.hourly.precipitation_probability[idx],
                    res = res
                )
                NotificationHelper.sendRainNotification(
                    ctx, "Info Cuaca Stormy ${WeatherHelper.getConditionEmoji(headsUp.second)}", msg
                )
                prefs.edit().putBoolean(KEY_HEADSUP, true).apply()
            }
        }
    }

    /** Cari awal hujan dalam ~45 menit ke depan dari data per 15 menit. Return (waktu ISO, kode). */
    fun findRainSoon(res: WeatherResponse): Pair<String, Int>? {
        val m = res.minutely_15 ?: return null
        val times = m.time ?: return null
        val nowKey = WeatherHelper.locationNowMinuteKey(res)
        var checked = 0
        for (i in times.indices) {
            if (times[i] < nowKey) continue          // slot yang sudah lewat
            if (checked >= 3) break                  // 3 slot = ~45 menit
            checked++
            val code = m.weather_code?.getOrNull(i) ?: continue
            val mm = m.precipitation?.getOrNull(i) ?: 0.0
            if (WeatherHelper.isRainCode(code) && mm >= 0.1) return times[i] to code
        }
        return null
    }

    /** Cari hujan dengan peluang tinggi dalam 1-3 jam ke depan. Return (waktu ISO, kode). */
    fun findRainNextHours(res: WeatherResponse): Pair<String, Int>? {
        val cur = WeatherHelper.currentHourIndex(res)
        for (h in 1..3) {
            val idx = cur + h
            if (idx >= res.hourly.time.size) break
            val code = res.hourly.weather_code[idx]
            val prob = res.hourly.precipitation_probability[idx]
            if (WeatherHelper.isRainCode(code) && prob >= 50) return res.hourly.time[idx] to code
        }
        return null
    }

    private fun minutesUntil(isoTime: String, res: WeatherResponse): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = WeatherHelper.locationTimeZone(res) }
            val target = sdf.parse(isoTime)?.time ?: return 30
            ((target - System.currentTimeMillis()) / 60000L).toInt().coerceIn(5, 60)
        } catch (_: Exception) { 30 }
    }

    private fun resetEvent(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .putBoolean(KEY_HEADSUP, false)
            .putBoolean(KEY_SOON, false)
            .putBoolean(KEY_STORM, false)
            .apply()
    }
}

// =========================================================
// MESIN RINGKASAN PAGI (MORNING BRIEFING) & PERINGATAN BMKG
// =========================================================
object MorningBriefingEngine {
    const val KEY_ENABLED = "morning_briefing_enabled"
    private const val KEY_LAST_DATE = "morning_briefing_last_date"
    private const val KEY_LAST_BMKG_GUID = "bmkg_last_notified_guid"

    data class PeriodSlot(
        val label: String,
        val timeRange: String,
        val code: Int,
        val isDay: Int,
        val temp: Int,
        val maxProb: Int
    )

    data class DailyBriefingSummary(
        val title: String,
        val headline: String,
        val fullMessage: String,
        val periods: List<PeriodSlot>
    )

    /** Bangun ringkasan Pagi, Siang, Sore, Malam hari ini untuk UI & Notifikasi Pagi. */
    fun buildDailyBriefing(res: WeatherResponse, city: String): DailyBriefingSummary {
        val todayPrefix = WeatherHelper.locationNowHourKey(res).take(10)
        val hr = res.hourly

        fun slotForHours(label: String, rangeText: String, hours: IntRange, defaultIsDay: Int): PeriodSlot {
            val indices = hr.time.indices.filter { idx ->
                val t = hr.time[idx]
                t.startsWith(todayPrefix) && (t.substring(11, 13).toIntOrNull() ?: -1) in hours
            }
            if (indices.isEmpty()) {
                return PeriodSlot(label, rangeText, res.current.weather_code, defaultIsDay, res.current.temperature_2m.toInt(), 0)
            }
            val worstCode = indices.maxByOrNull { hr.weather_code[it] }?.let { hr.weather_code[it] } ?: 1
            val avgTemp = indices.map { hr.temperature_2m[it] }.average().toInt()
            val maxProb = indices.maxOfOrNull { hr.precipitation_probability[it] } ?: 0
            return PeriodSlot(label, rangeText, worstCode, defaultIsDay, avgTemp, maxProb)
        }

        val pagi = slotForHours("Pagi", "06:00–11:00", 6..11, 1)
        val siang = slotForHours("Siang", "12:00–15:00", 12..15, 1)
        val sore = slotForHours("Sore", "16:00–18:00", 16..18, 1)
        val malam = slotForHours("Malam", "19:00–23:00", 19..23, 0)
        val periods = listOf(pagi, siang, sore, malam)

        val todayIndices = hr.time.indices.filter { hr.time[it].startsWith(todayPrefix) && (hr.time[it].substring(11, 13).toIntOrNull() ?: 0) >= 6 }
        val maxTemp = res.daily?.temperature_2m_max?.firstOrNull()?.toInt()
            ?: todayIndices.maxOfOrNull { hr.temperature_2m[it].toInt() } ?: res.current.temperature_2m.toInt()
        val minTemp = res.daily?.temperature_2m_min?.firstOrNull()?.toInt()
            ?: todayIndices.minOfOrNull { hr.temperature_2m[it].toInt() } ?: res.current.temperature_2m.toInt()

        // Cari jam pertama yang berpotensi hujan hari ini (>= 40% atau kode >= 51)
        val firstRainIdx = todayIndices.firstOrNull {
            WeatherHelper.isRainCode(hr.weather_code[it]) && hr.precipitation_probability[it] >= 40
        }

        val headline = if (firstRainIdx != null) {
            val rainHour = hr.time[firstRainIdx].takeLast(5)
            val rainLabel = WeatherHelper.getConditionLabel(hr.weather_code[firstRainIdx]).lowercase()
            val rainProb = hr.precipitation_probability[firstRainIdx]
            "Berpotensi $rainLabel mulai sekitar pukul $rainHour ($rainProb%) • Suhu $minTemp°–$maxTemp°C"
        } else {
            "Cuaca relatif kondusif sepanjang hari • Suhu $minTemp°–$maxTemp°C"
        }

        val fullMsg = buildString {
            append("Ringkasan cuaca hari ini di $city (Suhu $minTemp°–$maxTemp°C):\n\n")
            append("• Pagi: ${WeatherHelper.getConditionLabel(pagi.code)} (${pagi.temp}°, peluang hujan ${pagi.maxProb}%)\n")
            append("• Siang: ${WeatherHelper.getConditionLabel(siang.code)} (${siang.temp}°, peluang hujan ${siang.maxProb}%)\n")
            append("• Sore: ${WeatherHelper.getConditionLabel(sore.code)} (${sore.temp}°, peluang hujan ${sore.maxProb}%)\n")
            append("• Malam: ${WeatherHelper.getConditionLabel(malam.code)} (${malam.temp}°, peluang hujan ${malam.maxProb}%)\n\n")
            if (firstRainIdx != null) {
                val rainHour = hr.time[firstRainIdx].takeLast(5)
                append("Saran: Siapkan payung/jas hujan karena ada potensi hujan sekitar pukul $rainHour.")
            } else {
                append("Saran: Cuaca cukup bersahabat untuk aktivitas luar ruangan hari ini.")
            }
        }

        return DailyBriefingSummary(
            title = "Ringkasan Cuaca Hari Ini • $city",
            headline = headline,
            fullMessage = fullMsg,
            periods = periods
        )
    }

    /** Kirim notifikasi ringkasan pagi otomatis1x sehari jika waktu lokal antara 06:00 - 08:59. */
    fun checkAndSendIfDue(ctx: Context, res: WeatherResponse, city: String) {
        val prefs = ctx.getSharedPreferences(RainAlertWorker.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, true)) return

        val nowKey = WeatherHelper.locationNowHourKey(res) // "yyyy-MM-dd'T'HH:00"
        val todayDate = nowKey.take(10)
        val hour = nowKey.substring(11, 13).toIntOrNull() ?: return

        if (hour in 6..8 && prefs.getString(KEY_LAST_DATE, "") != todayDate) {
            val summary = buildDailyBriefing(res, city)
            NotificationHelper.sendRainNotification(
                ctx,
                title = "☀️ Ringkasan Pagi Stormy • $city",
                message = summary.fullMessage,
                notifId = 1002
            )
            prefs.edit().putString(KEY_LAST_DATE, todayDate).apply()
        }
    }

    /** Cek RSS Peringatan Dini BMKG di background; jika ada peringatan lokal baru, kirim notifikasi. */
    suspend fun checkAndSendBmkgAlert(ctx: Context, city: String, lat: Double, lon: Double) {
        try {
            val prefs = ctx.getSharedPreferences(RainAlertWorker.PREFS, Context.MODE_PRIVATE)
            val result = ApiClient.fetchBmkgAlerts(city, lat, lon)
            val local = result.localAlert ?: return
            if (prefs.getString(KEY_LAST_BMKG_GUID, "") != local.guid) {
                NotificationHelper.sendRainNotification(
                    ctx,
                    title = "⚠️ Peringatan Dini BMKG: ${local.title}",
                    message = local.description,
                    notifId = 1003
                )
                prefs.edit().putString(KEY_LAST_BMKG_GUID, local.guid).apply()
            }
        } catch (_: Exception) {}
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "stormy_rain_alert_channel"
    const val EXTRA_POPUP_TITLE = "POPUP_TITLE"
    const val EXTRA_POPUP_MESSAGE = "POPUP_MESSAGE"
    const val EXTRA_FROM_NOTIFICATION = "FROM_NOTIFICATION"

    fun sendRainNotification(context: Context, title: String, message: String, notifId: Int = 1001) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Peringatan & Info Cuaca Stormy",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi info cuaca dan peringatan hujan"
            }
            manager.createNotificationChannel(channel)
        }

        // Intent agar saat notifikasi ditekan, aplikasi terbuka & memunculkan Pop-Up dengan pesan yang sama
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_POPUP_TITLE, title)
            putExtra(EXTRA_POPUP_MESSAGE, message)
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeLogo = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.stormy_logo)
        } catch (_: Exception) { null }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stormy)
            .apply { if (largeLogo != null) setLargeIcon(largeLogo) }
            .setColor(0xFF0A1124.toInt())
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // Buka Pop-Up saat diklik!
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }
}

object WeatherHelper {
    private val RAIN_CODES = setOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)

    fun isRainCode(code: Int): Boolean = code in RAIN_CODES

    fun getConditionLabel(code: Int): String {
        return when (code) {
            0 -> "Cerah"
            1 -> "Cerah Berawan"
            2 -> "Berawan"
            3 -> "Mendung"
            45, 48 -> "Berkabut"
            51, 53, 55 -> "Gerimis"
            61, 63 -> "Hujan"
            65 -> "Hujan Lebat"
            80, 81, 82 -> "Hujan Deras"
            95, 96, 99 -> "Badai Petir"
            else -> "Berawan"
        }
    }

    // Kalimat kondisi natural untuk Notifikasi & Pop-Up
    fun getActionPhrase(code: Int): String {
        return when (code) {
            0 -> "akan cerah"
            1 -> "akan cerah berawan"
            2 -> "akan berawan"
            3 -> "akan mendung"
            45, 48 -> "akan berkabut"
            51, 53, 55 -> "akan turun gerimis"
            61, 63 -> "akan turun hujan"
            65 -> "akan turun hujan lebat"
            80, 81, 82 -> "akan turun hujan deras"
            95, 96, 99 -> "akan terjadi badai petir"
            else -> "akan berawan"
        }
    }

    // ---------- Waktu sesuai zona waktu LOKASI (bukan zona waktu HP) ----------
    fun locationTimeZone(res: WeatherResponse?): java.util.TimeZone {
        val off = res?.utc_offset_seconds ?: return java.util.TimeZone.getDefault()
        return SimpleTimeZone(off * 1000, "LOC")
    }

    /** Contoh: "2026-10-26T14:00" (jam sekarang di lokasi, dibulatkan ke bawah) */
    fun locationNowHourKey(res: WeatherResponse?): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).apply { timeZone = locationTimeZone(res) }.format(java.util.Date())

    /** Contoh: "2026-10-26T14:15" (slot 15 menit sekarang di lokasi) */
    fun locationNowMinuteKey(res: WeatherResponse?): String {
        val cal = Calendar.getInstance(locationTimeZone(res))
        cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / 15) * 15)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = locationTimeZone(res) }.format(cal.time)
    }

    /** Index data per jam untuk jam sekarang di lokasi. */
    fun currentHourIndex(res: WeatherResponse): Int {
        val idx = res.hourly.time.indexOf(locationNowHourKey(res))
        if (idx >= 0) return idx
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY).coerceAtMost(res.hourly.time.size - 1)
    }

    /** "Hari ini" / "Besok" / "Lusa" berdasarkan tanggal di lokasi. */
    fun dayPrefix(isoTime: String, res: WeatherResponse?): String {
        return try {
            val today = LocalDate.parse(locationNowHourKey(res).take(10))
            when (LocalDate.parse(isoTime.take(10))) {
                today -> "Hari ini"
                today.plusDays(1) -> "Besok"
                today.plusDays(2) -> "Lusa"
                else -> "Pada ${isoTime.take(10)}"
            }
        } catch (_: Exception) { "Hari ini" }
    }

    /** Siang/malam untuk index jam tertentu. */
    fun isDayAt(res: WeatherResponse, idx: Int): Int {
        res.hourly.is_day?.getOrNull(idx)?.let { return it }
        val hour = res.hourly.time.getOrNull(idx)?.substring(11, 13)?.toIntOrNull() ?: 12
        return if (hour in 6..17) 1 else 0
    }

    /** Nama hari untuk 7-Day Forecast: "Hari ini", "Besok", atau "Senin", "Selasa", dst. */
    fun dayNameLabel(isoDate: String, res: WeatherResponse?): String {
        return try {
            val today = LocalDate.parse(locationNowHourKey(res).take(10))
            val date = LocalDate.parse(isoDate.take(10))
            when (date) {
                today -> "Hari ini"
                today.plusDays(1) -> "Besok"
                else -> when (date.dayOfWeek.value) {
                    1 -> "Senin"
                    2 -> "Selasa"
                    3 -> "Rabu"
                    4 -> "Kamis"
                    5 -> "Jumat"
                    6 -> "Sabtu"
                    else -> "Minggu"
                }
            }
        } catch (_: Exception) { isoDate }
    }

    /** Arah mata angin dari derajat (0-360). */
    fun windDirectionLabel(deg: Int?): String {
        if (deg == null) return "Tenang"
        val dirs = listOf("Utara", "Timur Laut", "Timur", "Tenggara", "Selatan", "Barat Daya", "Barat", "Barat Laut")
        val idx = (((deg % 360) + 22.5) / 45.0).toInt() % 8
        return dirs[idx]
    }

    fun buildForecastMessage(isoTime: String, code: Int, location: String, temp: Int, prob: Int, res: WeatherResponse?): String {
        val phrase = getActionPhrase(code)
        val closingTip = when {
            code >= 95 -> "Harap tetap waspada dan hindari area terbuka."
            code >= 51 || prob >= 40 -> "Jangan lupa siapkan payung atau jas hujan sebelum beraktivitas!"
            code == 0 || code == 1 -> "Cuaca sangat mendukung untuk beraktivitas di luar ruangan."
            else -> "Tetap pantau perubahan cuaca hari ini."
        }
        return "${dayPrefix(isoTime, res)} pukul ${isoTime.takeLast(5)} diperkirakan $phrase di $location (Suhu $temp°C, peluang hujan $prob%). $closingTip"
    }

    fun getConditionEmoji(code: Int, isDay: Int = 1): String {
        return when (code) {
            0 -> if (isDay == 1) "☀️" else "🌙"
            1 -> if (isDay == 1) "🌤️" else "☁️"
            2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55 -> "🌦️"
            61, 63, 65 -> "🌧️"
            80, 81, 82 -> "⛈️"
            95, 96, 99 -> "🌩️"
            else -> "☁️"
        }
    }
}