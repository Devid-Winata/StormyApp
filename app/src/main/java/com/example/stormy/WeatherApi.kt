package com.example.stormy

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val utc_offset_seconds: Int? = null,
    val current: CurrentWeather,
    val hourly: HourlyWeather,
    val daily: DailyWeather? = null,
    val minutely_15: Minutely15Weather? = null
)

data class CurrentWeather(
    val temperature_2m: Double,
    val apparent_temperature: Double,
    val is_day: Int,
    val precipitation: Double,
    val weather_code: Int,
    val relative_humidity_2m: Int? = null,
    val wind_speed_10m: Double? = null,
    val wind_direction_10m: Int? = null,
    val surface_pressure: Double? = null
)

data class HourlyWeather(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val precipitation_probability: List<Int>,
    val precipitation: List<Double>,
    val weather_code: List<Int>,
    val is_day: List<Int>? = null
)

data class DailyWeather(
    val time: List<String>?,
    val weather_code: List<Int?>?,
    val temperature_2m_max: List<Double?>?,
    val temperature_2m_min: List<Double?>?,
    val precipitation_probability_max: List<Int?>?,
    val sunrise: List<String?>?,
    val sunset: List<String?>?
)

// Data per 15 menit (untuk peringatan "hujan dalam 30 menit")
data class Minutely15Weather(
    val time: List<String>?,
    val precipitation: List<Double?>?,
    val weather_code: List<Int?>?
)

// Data Kualitas Udara (AQI, PM2.5, PM10) & Indeks UV dari Open-Meteo
data class AirQualityResponse(
    val current: AirQualityCurrent?
)

data class AirQualityCurrent(
    val us_aqi: Int? = null,
    val pm2_5: Double? = null,
    val pm10: Double? = null,
    val uv_index: Double? = null
)

// Data Peringatan Dini Cuaca Ekstrem Resmi BMKG (Nowcast RSS CAP)
data class BmkgAlert(
    val title: String,
    val description: String,
    val pubDate: String,
    val link: String,
    val guid: String
)

data class BmkgAlertResult(
    val localAlert: BmkgAlert?,
    val nationalAlerts: List<BmkgAlert>
)

data class NominatimPlace(
    val display_name: String?,
    val lat: String?,
    val lon: String?,
    val address: NominatimAddress?
)

data class NominatimAddress(
    val road: String?,
    val pedestrian: String?,
    val suburb: String?,
    val village: String?,
    val neighbourhood: String?,
    val city: String?,
    val town: String?,
    val county: String?
)

data class BigDataCloudResponse(
    val locality: String?,
    val city: String?,
    val principalSubdivision: String?
)

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,is_day,precipitation,weather_code,relative_humidity_2m,wind_speed_10m,wind_direction_10m,surface_pressure",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,precipitation,weather_code,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
        @Query("minutely_15") minutely15: String = "precipitation,weather_code",
        @Query("forecast_minutely_15") minutely15Count: Int = 16,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 7,
        @Query("models") models: String = "best_match"
    ): WeatherResponse
}

interface AirQualityService {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "us_aqi,pm2_5,pm10,uv_index",
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponse
}

interface NominatimService {
    @GET("search")
    suspend fun searchStreetOrPlace(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") countryCodes: String = "id",
        @Query("accept-language") lang: String = "id"
    ): List<NominatimPlace>

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 18,
        @Query("accept-language") lang: String = "id"
    ): NominatimPlace
}

interface BigDataCloudService {
    @GET("data/reverse-geocode-client")
    suspend fun reverseFast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("localityLanguage") lang: String = "id"
    ): BigDataCloudResponse
}

object ApiClient {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) StormyApp-Dvid/2.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    val weatherService: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoService::class.java)
    }

    private val airQualityService: AirQualityService by lazy {
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityService::class.java)
    }

    /**
     * Prakiraan cuaca model ECMWF IFS 9 km — model utama yang dipakai Windy.
     * Karena model deterministik murni ECMWF tidak menyertakan persentase peluang hujan (precipitation_probability)
     * dan data 15-menit (minutely_15), kita ambil ecmwf_ifs & best_match secara paralel lalu menggabungkan
     * suhu/kondisi/curah hujan akurat ECMWF dengan peluang hujan & minutely_15 dari best_match.
     */
    suspend fun getForecastEcmwf(lat: Double, lon: Double): WeatherResponse = coroutineScope {
        val ecmwfDeferred = async {
            try { weatherService.getForecast(lat, lon, models = "ecmwf_ifs") } catch (_: Exception) { null }
        }
        val bestDeferred = async {
            try { weatherService.getForecast(lat, lon, models = "best_match") } catch (_: Exception) { null }
        }

        val ecmwf = ecmwfDeferred.await()
        val best = bestDeferred.await()

        when {
            ecmwf != null && best != null -> mergeEcmwfWithBest(ecmwf, best)
            best != null && isComplete(best) -> best
            ecmwf != null && isComplete(ecmwf) -> ecmwf
            else -> weatherService.getForecast(lat, lon, models = "best_match")
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun mergeEcmwfWithBest(ecmwf: WeatherResponse, best: WeatherResponse): WeatherResponse {
        if (best.hourly == null || best.current == null) return ecmwf
        if (ecmwf.hourly == null || ecmwf.current == null) return best

        val size = minOf(ecmwf.hourly.time.size, best.hourly.time.size)
        if (size < 24) return best

        @Suppress("UNCHECKED_CAST")
        val mergedTemp = (0 until size).map { i ->
            (ecmwf.hourly.temperature_2m as List<Double?>?)?.getOrNull(i)
                ?: (best.hourly.temperature_2m as List<Double?>?)?.getOrNull(i)
                ?: 28.0
        }
        @Suppress("UNCHECKED_CAST")
        val mergedPrecip = (0 until size).map { i ->
            (ecmwf.hourly.precipitation as List<Double?>?)?.getOrNull(i)
                ?: (best.hourly.precipitation as List<Double?>?)?.getOrNull(i)
                ?: 0.0
        }
        @Suppress("UNCHECKED_CAST")
        val mergedCode = (0 until size).map { i ->
            (ecmwf.hourly.weather_code as List<Int?>?)?.getOrNull(i)
                ?: (best.hourly.weather_code as List<Int?>?)?.getOrNull(i)
                ?: 2
        }
        @Suppress("UNCHECKED_CAST")
        val mergedProb = (0 until size).map { i ->
            (ecmwf.hourly.precipitation_probability as List<Int?>?)?.getOrNull(i)
                ?: (best.hourly.precipitation_probability as List<Int?>?)?.getOrNull(i)
                ?: 0
        }
        val mergedIsDay = (0 until size).map { i ->
            ecmwf.hourly.is_day?.getOrNull(i) ?: best.hourly.is_day?.getOrNull(i) ?: 1
        }

        val mergedDaily = if (ecmwf.daily != null && best.daily != null) {
            DailyWeather(
                time = ecmwf.daily.time ?: best.daily.time,
                weather_code = ecmwf.daily.weather_code?.mapIndexed { i, v -> v ?: best.daily.weather_code?.getOrNull(i) }
                    ?: best.daily.weather_code,
                temperature_2m_max = ecmwf.daily.temperature_2m_max?.mapIndexed { i, v -> v ?: best.daily.temperature_2m_max?.getOrNull(i) }
                    ?: best.daily.temperature_2m_max,
                temperature_2m_min = ecmwf.daily.temperature_2m_min?.mapIndexed { i, v -> v ?: best.daily.temperature_2m_min?.getOrNull(i) }
                    ?: best.daily.temperature_2m_min,
                precipitation_probability_max = best.daily.precipitation_probability_max ?: ecmwf.daily.precipitation_probability_max,
                sunrise = best.daily.sunrise ?: ecmwf.daily.sunrise,
                sunset = best.daily.sunset ?: ecmwf.daily.sunset
            )
        } else {
            best.daily ?: ecmwf.daily
        }

        val hasValidMinutely = ecmwf.minutely_15?.precipitation?.any { it != null } == true
        val mergedMinutely = if (hasValidMinutely) ecmwf.minutely_15 else best.minutely_15

        return best.copy(
            current = ecmwf.current.copy(
                relative_humidity_2m = ecmwf.current.relative_humidity_2m ?: best.current.relative_humidity_2m,
                wind_speed_10m = ecmwf.current.wind_speed_10m ?: best.current.wind_speed_10m,
                wind_direction_10m = ecmwf.current.wind_direction_10m ?: best.current.wind_direction_10m,
                surface_pressure = ecmwf.current.surface_pressure ?: best.current.surface_pressure
            ),
            hourly = HourlyWeather(
                time = best.hourly.time.take(size),
                temperature_2m = mergedTemp,
                precipitation_probability = mergedProb,
                precipitation = mergedPrecip,
                weather_code = mergedCode,
                is_day = mergedIsDay
            ),
            daily = mergedDaily,
            minutely_15 = mergedMinutely
        )
    }

    /** Ambil data Kualitas Udara (AQI, PM2.5, PM10) & UV Index secara aman. */
    suspend fun getAirQualitySafe(lat: Double, lon: Double): AirQualityCurrent? {
        return try {
            airQualityService.getAirQuality(lat, lon).current
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ambil Peringatan Dini Cuaca Ekstrem Resmi dari BMKG (RSS Nowcast CAP).
     * Mencocokkan apakah ada peringatan aktif di provinsi/kota/kecamatan user.
     */
    suspend fun fetchBmkgAlerts(cityName: String, lat: Double, lon: Double): BmkgAlertResult = withContext(Dispatchers.IO) {
        val alerts = mutableListOf<BmkgAlert>()
        try {
            val req = Request.Builder()
                .url("https://www.bmkg.go.id/alerts/nowcast/id/rss.xml")
                .build()
            val xml = httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body()?.string() else null
            }
            if (!xml.isNullOrBlank()) {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xml))
                var event = parser.eventType
                var inItem = false
                var title = ""
                var desc = ""
                var pubDate = ""
                var link = ""
                var guid = ""

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name.lowercase()) {
                                "item" -> {
                                    inItem = true
                                    title = ""; desc = ""; pubDate = ""; link = ""; guid = ""
                                }
                                "title" -> if (inItem) title = runCatching { parser.nextText().trim() }.getOrDefault("")
                                "description" -> if (inItem) desc = runCatching { parser.nextText().trim().replace(Regex("\\s+"), " ") }.getOrDefault("")
                                "pubdate" -> if (inItem) pubDate = runCatching { parser.nextText().trim() }.getOrDefault("")
                                "link" -> if (inItem) link = runCatching { parser.nextText().trim() }.getOrDefault("")
                                "guid" -> if (inItem) guid = runCatching { parser.nextText().trim() }.getOrDefault("")
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.equals("item", ignoreCase = true) && inItem) {
                                inItem = false
                                if (title.isNotEmpty() && desc.isNotEmpty()) {
                                    alerts.add(BmkgAlert(title, desc, pubDate, link, guid.ifEmpty { title }))
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        } catch (_: Exception) {}

        // Cari kata kunci lokasi user (nama jalan/kecamatan/kota + provinsi dari koordinat)
        val keywords = buildLocationKeywords(cityName, lat, lon)
        val localMatch = alerts.firstOrNull { alert ->
            val combined = "${alert.title} ${alert.description}".lowercase()
            keywords.any { kw -> kw.length >= 4 && combined.contains(kw) }
        }
        BmkgAlertResult(localAlert = localMatch, nationalAlerts = alerts)
    }

    private fun buildLocationKeywords(cityName: String, lat: Double, lon: Double): List<String> {
        val stopWords = setOf("jalan", "jl.", "kec.", "kecamatan", "kel.", "kelurahan", "kota", "kab.", "kabupaten", "barat", "timur", "utara", "selatan", "tengah", "raya", "indah")
        val rawTokens = cityName.lowercase()
            .split(",", " ", "-")
            .map { it.trim() }
            .filter { it.length >= 4 && it !in stopWords }
            .toMutableList()

        // Tambahkan kata kunci provinsi berdasarkan bounding box koordinat seluruh Indonesia
        if (lat in -3.5..-1.4 && lon in 105.0..108.5) {
            rawTokens.addAll(listOf("bangka", "belitung", "pangkalpinang", "pangkal pinang"))
        }
        if (lat in 2.0..6.0 && lon in 95.0..98.5) rawTokens.add("aceh")
        if (lat in 0.5..4.3 && lon in 97.0..100.5) rawTokens.addAll(listOf("sumatera utara", "medan"))
        if (lat in -2.5..0.9 && lon in 98.5..101.9) rawTokens.addAll(listOf("sumatera barat", "padang"))
        if (lat in -1.1..2.5 && lon in 100.0..103.8) rawTokens.addAll(listOf("riau", "pekanbaru"))
        if (lat in -0.8..4.8 && lon in 103.3..109.2) rawTokens.addAll(listOf("kepulauan riau", "batam", "bintan"))
        if (lat in -2.6..-0.7 && lon in 101.1..104.5) rawTokens.add("jambi")
        if (lat in -4.9..-1.6 && lon in 102.0..106.2) rawTokens.addAll(listOf("sumatera selatan", "palembang"))
        if (lat in -5.0..-2.2 && lon in 101.0..103.9) rawTokens.add("bengkulu")
        if (lat in -6.0..-3.7 && lon in 103.5..106.0) rawTokens.addAll(listOf("lampung", "bandar lampung"))
        if (lat in -6.5..-5.9 && lon in 106.6..107.0) rawTokens.addAll(listOf("jakarta", "jabodetabek"))
        if (lat in -7.0..-5.8 && lon in 105.1..106.7) rawTokens.addAll(listOf("banten", "tangerang", "serang"))
        if (lat in -7.9..-5.9 && lon in 106.3..108.9) rawTokens.addAll(listOf("jawa barat", "bandung", "bogor", "bekasi", "depok"))
        if (lat in -8.3..-6.4 && lon in 108.8..111.7) rawTokens.addAll(listOf("jawa tengah", "semarang", "yogyakarta"))
        if (lat in -8.8..-6.7 && lon in 111.0..114.6) rawTokens.addAll(listOf("jawa timur", "surabaya", "malang"))
        if (lat in -8.9..-8.0 && lon in 114.4..115.8) rawTokens.addAll(listOf("bali", "denpasar"))
        if (lat in -9.1..-8.0 && lon in 115.8..119.3) rawTokens.addAll(listOf("nusa tenggara barat", "lombok", "mataram"))
        if (lat in -11.0..-8.0 && lon in 118.9..125.3) rawTokens.addAll(listOf("nusa tenggara timur", "kupang"))
        if (lat in -3.1..2.1 && lon in 108.5..114.2) rawTokens.addAll(listOf("kalimantan barat", "pontianak"))
        if (lat in -3.5..0.8 && lon in 110.7..115.8) rawTokens.addAll(listOf("kalimantan tengah", "palangkaraya"))
        if (lat in -4.3..-1.3 && lon in 114.3..116.6) rawTokens.addAll(listOf("kalimantan selatan", "banjarmasin"))
        if (lat in -2.5..2.6 && lon in 113.8..119.0) rawTokens.addAll(listOf("kalimantan timur", "balikpapan", "samarinda"))
        if (lat in 1.0..4.5 && lon in 114.5..118.0) rawTokens.addAll(listOf("kalimantan utara", "tarakan"))
        if (lat in -6.0..-1.8 && lon in 118.8..120.8) rawTokens.addAll(listOf("sulawesi selatan", "makassar", "sulawesi barat"))
        if (lat in -5.8..-2.8 && lon in 120.8..124.1) rawTokens.addAll(listOf("sulawesi tenggara", "kendari"))
        if (lat in -3.2..1.4 && lon in 119.4..124.2) rawTokens.addAll(listOf("sulawesi tengah", "palu", "gorontalo"))
        if (lat in 0.3..4.5 && lon in 123.1..127.2) rawTokens.addAll(listOf("sulawesi utara", "manado"))
        if (lat in -8.5..2.7 && lon in 124.0..135.0) rawTokens.addAll(listOf("maluku", "ambon", "ternate"))
        if (lat in -9.2..0.0 && lon in 130.0..141.1) rawTokens.addAll(listOf("papua", "jayapura", "sorong", "merauke"))

        return rawTokens.distinct()
    }

    // Gson bisa memasukkan null ke List<Int>/List<Double>; cek supaya app tidak crash
    @Suppress("SENSELESS_COMPARISON", "USELESS_CAST")
    private fun isComplete(res: WeatherResponse?): Boolean {
        if (res == null || res.current == null || res.hourly == null) return false
        val h = res.hourly
        val lists: List<List<Any?>?> = listOf(
            h.time, h.temperature_2m, h.precipitation_probability, h.precipitation, h.weather_code
        )
        if (lists.any { it == null || it.size < 24 || it.any { v -> v == null } }) return false
        val sizes = lists.map { it!!.size }.toSet()
        return sizes.size == 1
    }

    val streetService: NominatimService by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimService::class.java)
    }

    val fastGeoService: BigDataCloudService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bigdatacloud.net/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BigDataCloudService::class.java)
    }
}