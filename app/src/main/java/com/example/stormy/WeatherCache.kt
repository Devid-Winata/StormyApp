package com.example.stormy

import android.content.Context
import com.google.gson.Gson

/**
 * Simpan data cuaca terakhir di HP supaya:
 *  - App tetap menampilkan data saat tidak ada internet (mode offline)
 *  - Widget bisa menampilkan cuaca tanpa membuka app
 *
 * SLOT_HOME      = lokasi HP (GPS) → dipakai widget & notifikasi
 * SLOT_LAST_VIEW = lokasi terakhir yang dilihat di app (bisa hasil pencarian)
 */
object WeatherCache {
    const val SLOT_HOME = "home"
    const val SLOT_LAST_VIEW = "last_view"

    data class Cached(
        val weather: WeatherResponse,
        val city: String,
        val lat: Double,
        val lon: Double,
        val savedAt: Long,
        val airQuality: AirQualityCurrent? = null
    )

    private val gson = Gson()

    fun save(
        ctx: Context,
        slot: String,
        weather: WeatherResponse,
        city: String,
        lat: Double,
        lon: Double,
        airQuality: AirQualityCurrent? = null
    ) {
        try {
            val json = gson.toJson(Cached(weather, city, lat, lon, System.currentTimeMillis(), airQuality))
            ctx.getSharedPreferences("stormy_cache", Context.MODE_PRIVATE)
                .edit().putString("cache_$slot", json).apply()
        } catch (_: Exception) {}
    }

    @Suppress("SENSELESS_COMPARISON")
    fun load(ctx: Context, slot: String): Cached? {
        return try {
            val json = ctx.getSharedPreferences("stormy_cache", Context.MODE_PRIVATE)
                .getString("cache_$slot", null) ?: return null
            val c = gson.fromJson(json, Cached::class.java) ?: return null
            // Validasi: Gson bisa mengisi null ke field non-null kalau data rusak
            if (c.weather == null || c.weather.current == null || c.weather.hourly == null ||
                c.weather.hourly.time == null || c.weather.hourly.time.isEmpty() || c.city == null
            ) null else c
        } catch (_: Exception) { null }
    }
}
