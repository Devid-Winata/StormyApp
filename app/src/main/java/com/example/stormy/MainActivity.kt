package com.example.stormy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private var popupTitleState by mutableStateOf<String?>(null)
    private var popupMessageState by mutableStateOf<String?>(null)
    private var openedFromNotif by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)

        // Jadwalkan cek hujan tiap 30 menit (dengan constraint internet)
        RainAlertWorker.schedule(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var showSplash by remember { mutableStateOf(!openedFromNotif) }

                LaunchedEffect(Unit) {
                    if (showSplash) {
                        delay(1100)
                        showSplash = false
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    StormyAppScreen(
                        externalPopupTitle = popupTitleState,
                        externalPopupMessage = popupMessageState,
                        onDismissExternalPopup = {
                            popupTitleState = null
                            popupMessageState = null
                        },
                        onTriggerPopupAndNotif = { title, msg ->
                            popupTitleState = title
                            popupMessageState = msg
                        },
                        onDataReady = {
                            showSplash = false
                        }
                    )

                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(400))
                    ) {
                        StormySplashScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NotificationHelper.EXTRA_FROM_NOTIFICATION, false) == true) {
            openedFromNotif = true
            popupTitleState = intent.getStringExtra(NotificationHelper.EXTRA_POPUP_TITLE)
                ?: intent.getStringExtra(" POPUP_TITLE")
                ?: "Info Cuaca Stormy"
            popupMessageState = intent.getStringExtra(NotificationHelper.EXTRA_POPUP_MESSAGE)
        }
    }
}

// =========================================================
// FUNGSI PENERJEMAH KOORDINAT -> NAMA JALAN PRESISI
// =========================================================
suspend fun getPreciseStreetAddress(context: Context, lat: Double, lon: Double): String {
    return withContext(Dispatchers.IO) {
        // 1. Geocoder bawaan HP Android (data dari Google Maps, gratis, tanpa API key)
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.forLanguageTag("id-ID"))
                val addresses: List<android.location.Address>? = withTimeoutOrNull(5000L) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(result: MutableList<android.location.Address>) {
                                    if (cont.isActive) cont.resumeWith(Result.success(result))
                                }
                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resumeWith(Result.success(null))
                                }
                            })
                        }
                    } else {
                        kotlinx.coroutines.runInterruptible {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocation(lat, lon, 1)
                        }
                    }
                }
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val street = addr.thoroughfare
                    val area = addr.subLocality ?: addr.locality ?: addr.subAdminArea
                    val parts = listOfNotNull(street, area).distinct()
                    if (parts.isNotEmpty()) return@withContext parts.joinToString(", ")
                }
            }
        } catch (_: Exception) {}

        // 2. Cadangan: Nominatim / OpenStreetMap
        try {
            val nom = withTimeoutOrNull(4000L) {
                ApiClient.streetService.reverseGeocode(lat, lon)
            }
            if (nom != null) {
                val addr = nom.address
                val road = addr?.road ?: addr?.pedestrian ?: addr?.neighbourhood
                val area = addr?.suburb ?: addr?.village ?: addr?.city ?: addr?.town ?: addr?.county
                val combined = listOfNotNull(road, area).distinct().joinToString(", ")
                if (combined.isNotBlank()) return@withContext combined

                val displayParts = nom.display_name?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                if (!displayParts.isNullOrEmpty()) {
                    return@withContext displayParts.take(2).joinToString(", ")
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback Tercepat: BigDataCloud API
        try {
            val fast = withTimeoutOrNull(3000L) {
                ApiClient.fastGeoService.reverseFast(lat, lon)
            }
            if (fast != null) {
                val loc = listOfNotNull(fast.locality, fast.city).distinct().joinToString(", ")
                if (loc.isNotBlank()) return@withContext loc
            }
        } catch (_: Exception) {}

        "Pangkalpinang (${String.format("%.2f", lat)}, ${String.format("%.2f", lon)})"
    }
}

// =========================================================
// 1. SPLASH SCREEN MINIMALIS & PROFESIONAL
// =========================================================
@Composable
fun StormySplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1120), Color(0xFF0F172A), Color(0xFF070B14))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.stormy_logo),
                    contentDescription = "Logo Stormy",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Stormy",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "ECMWF Street Weather & Live Radar",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )
        }

        Text(
            text = "by Dvid",
            color = Color(0xFF475569),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        )
    }
}

// =========================================================
// 2. LAYAR UTAMA STORMY (ARSITEKTUR BENTO PROFESIONAL)
// =========================================================
@SuppressLint("MissingPermission", "SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun StormyAppScreen(
    externalPopupTitle: String?,
    externalPopupMessage: String?,
    onDismissExternalPopup: () -> Unit,
    onTriggerPopupAndNotif: (String, String) -> Unit,
    onDataReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("stormy_prefs", Context.MODE_PRIVATE) }

    val initialCache = remember {
        WeatherCache.load(context, WeatherCache.SLOT_LAST_VIEW)
            ?: WeatherCache.load(context, WeatherCache.SLOT_HOME)
    }

    val savedCity = prefs.getString("city", initialCache?.city ?: "Pangkalpinang") ?: "Pangkalpinang"
    val cleanInitialCity = if (savedCity.contains("Mendeteksi", ignoreCase = true)) "Pangkalpinang" else savedCity

    var lat by remember { mutableStateOf(prefs.getFloat("lat", (initialCache?.lat ?: -2.1316).toFloat()).toDouble()) }
    var lon by remember { mutableStateOf(prefs.getFloat("lon", (initialCache?.lon ?: 106.1169).toFloat()).toDouble()) }
    var cityName by remember { mutableStateOf(cleanInitialCity) }
    var gpsCityName by remember { mutableStateOf(prefs.getString("gps_city", null)) }
    var isDetectingGps by remember { mutableStateOf(false) }

    var weather by remember { mutableStateOf<WeatherResponse?>(initialCache?.weather) }
    var airQuality by remember { mutableStateOf<AirQualityCurrent?>(initialCache?.airQuality) }
    var bmkgResult by remember { mutableStateOf<BmkgAlertResult?>(null) }
    var morningBriefingEnabled by remember {
        mutableStateOf(prefs.getBoolean(MorningBriefingEngine.KEY_ENABLED, true))
    }
    var isLoading by remember { mutableStateOf(initialCache == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isOfflineCache by remember { mutableStateOf(initialCache != null) }
    var lastUpdatedMs by remember { mutableLongStateOf(initialCache?.savedAt ?: 0L) }
    var loadWeatherJob by remember { mutableStateOf<Job?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<NominatimPlace>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Daftar lokasi tersimpan (format di prefs: "Nama|lat|lon;;Nama2|lat|lon")
    var savedLocations by remember {
        mutableStateOf(loadSavedLocations(prefs))
    }

    // State Radar Windy: Layer (rain, wind, clouds, thunder) & Kunci/Geser Peta
    var radarOverlay by remember { mutableStateOf("rain") }
    var radarInteractive by remember { mutableStateOf(false) }

    fun checkNotifPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    var notifGranted by remember { mutableStateOf(checkNotifPermission()) }

    // Sinkronkan status izin notifikasi saat user kembali dari Settings Android
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = checkNotifPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        notifGranted = isGranted
    }

    // Jam lokal mengikuti zona waktu lokasi cuaca yang sedang dilihat
    var localTimeText by remember { mutableStateOf("--:--") }
    LaunchedEffect(weather) {
        while (true) {
            val tz = WeatherHelper.locationTimeZone(weather)
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = tz }
            localTimeText = sdf.format(Date())
            delay(15_000L)
        }
    }

    fun createSmartWeatherMessage(res: WeatherResponse, targetLocation: String): Pair<String, String> {
        val soon = RainAlertEngine.findRainSoon(res)
        if (soon != null) {
            val (isoTime, code) = soon
            val label = WeatherHelper.getConditionLabel(code)
            val title = "$label Dalam ~30 Menit"
            val msg = "Diperkirakan ${label.lowercase()} mulai sekitar pukul ${isoTime.takeLast(5)} di $targetLocation. Siapkan payung atau jas hujan sebelum beraktivitas."
            return title to msg
        }

        val curIdx = WeatherHelper.currentHourIndex(res)
        val next1 = (curIdx + 1).coerceAtMost(res.hourly.time.size - 1)

        var targetIdx = next1
        for (h in 1..3) {
            val idx = (curIdx + h).coerceAtMost(res.hourly.time.size - 1)
            if (res.hourly.weather_code[idx] >= 51 || res.hourly.precipitation_probability[idx] >= 35) {
                targetIdx = idx
                break
            }
        }

        val isoTime = res.hourly.time[targetIdx]
        val code = res.hourly.weather_code[targetIdx]
        val temp = res.hourly.temperature_2m[targetIdx].toInt()
        val prob = res.hourly.precipitation_probability[targetIdx]

        val title = "Prakiraan Cuaca • ${isoTime.takeLast(5)}"
        val msg = WeatherHelper.buildForecastMessage(
            isoTime = isoTime,
            code = code,
            location = targetLocation,
            temp = temp,
            prob = prob,
            res = res
        )
        return title to msg
    }

    fun loadWeather(
        targetLat: Double,
        targetLon: Double,
        targetCity: String,
        isFromGps: Boolean = false
    ) {
        loadWeatherJob?.cancel()
        loadWeatherJob = scope.launch {
            isLoading = true
            loadError = null
            // Ambil Kualitas Udara + UV & Peringatan Dini BMKG secara paralel agar UI utama tidak tertahan
            val aqDeferred = async {
                ApiClient.getAirQualitySafe(targetLat, targetLon)
            }
            launch {
                val bmkg = ApiClient.fetchBmkgAlerts(targetCity, targetLat, targetLon)
                bmkgResult = bmkg
            }
            try {
                val res = ApiClient.getForecastEcmwf(targetLat, targetLon)
                val aq = aqDeferred.await()
                if (aq != null) airQuality = aq
                weather = res
                isOfflineCache = false
                lastUpdatedMs = System.currentTimeMillis()
                onDataReady()

                if (!targetCity.contains("Mendeteksi", ignoreCase = true)) {
                    val editor = prefs.edit()
                        .putFloat("lat", targetLat.toFloat())
                        .putFloat("lon", targetLon.toFloat())
                        .putString("city", targetCity)

                    if (isFromGps || !prefs.contains("gps_city")) {
                        editor.putFloat("gps_lat", targetLat.toFloat())
                            .putFloat("gps_lon", targetLon.toFloat())
                            .putString("gps_city", targetCity)
                        gpsCityName = targetCity
                        WeatherCache.save(context, WeatherCache.SLOT_HOME, res, targetCity, targetLat, targetLon, airQuality)
                    }
                    editor.apply()
                    WeatherCache.save(context, WeatherCache.SLOT_LAST_VIEW, res, targetCity, targetLat, targetLon, airQuality)
                    StormyWidget.refreshAll(context)
                }

                if (isFromGps) {
                    RainAlertEngine.evaluate(context, res, targetCity)
                    MorningBriefingEngine.checkAndSendIfDue(context, res, targetCity)
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
                if (weather == null) {
                    val cached = WeatherCache.load(context, WeatherCache.SLOT_LAST_VIEW)
                        ?: WeatherCache.load(context, WeatherCache.SLOT_HOME)
                    if (cached != null) {
                        weather = cached.weather
                        if (cached.airQuality != null) airQuality = cached.airQuality
                        isOfflineCache = true
                        lastUpdatedMs = cached.savedAt
                    }
                } else {
                    isOfflineCache = true
                }
                loadError = "Gagal memuat data terbaru. Periksa koneksi internet kamu."
                onDataReady()
            } finally {
                isLoading = false
            }
        }
    }

    // =========================================================
    // GPS PRESISI: Lokasi segar akurasi tinggi
    // =========================================================
    fun hasFinePermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun hasAnyLocationPermission() = hasFinePermission() || ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    fun fetchPreciseLocation() {
        scope.launch {
            isDetectingGps = true
            try {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                val priority = if (hasFinePermission()) Priority.PRIORITY_HIGH_ACCURACY
                               else Priority.PRIORITY_BALANCED_POWER_ACCURACY

                var location = withTimeoutOrNull(12_000L) {
                    try {
                        fused.getCurrentLocation(priority, CancellationTokenSource().token).await()
                    } catch (_: Exception) { null }
                }

                if (location == null) {
                    val last = try { fused.lastLocation.await() } catch (_: Exception) { null }
                    if (last != null && System.currentTimeMillis() - last.time < 2 * 60 * 1000L) {
                        location = last
                    }
                }

                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                    cityName = getPreciseStreetAddress(context, lat, lon)
                    loadWeather(lat, lon, cityName, isFromGps = true)
                } else {
                    loadWeather(lat, lon, cityName, isFromGps = false)
                    android.widget.Toast.makeText(
                        context,
                        "Sinyal GPS lemah. Menampilkan lokasi terakhir.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

                if (!hasFinePermission()) {
                    android.widget.Toast.makeText(
                        context,
                        "Lokasi masih 'Perkiraan'. Ubah ke 'Lokasi akurat' di Pengaturan agar nama jalan tepat.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: Exception) {
                loadWeather(lat, lon, cityName, isFromGps = false)
            } finally {
                isDetectingGps = false
            }
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            fetchPreciseLocation()
        } else {
            loadWeather(lat, lon, cityName, isFromGps = false)
        }
    }

    fun ensureLocationOnThenFetch() {
        val request = com.google.android.gms.location.LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5_000L
        ).build()
        val settingsRequest = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
            .build()

        LocationServices.getSettingsClient(context)
            .checkLocationSettings(settingsRequest)
            .addOnSuccessListener { fetchPreciseLocation() }
            .addOnFailureListener { e ->
                if (e is com.google.android.gms.common.api.ResolvableApiException) {
                    try {
                        locationSettingsLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(e.resolution).build()
                        )
                    } catch (_: Exception) { fetchPreciseLocation() }
                } else {
                    fetchPreciseLocation()
                }
            }
    }

    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            ensureLocationOnThenFetch()
        } else {
            loadWeather(lat, lon, cityName, isFromGps = false)
            android.widget.Toast.makeText(
                context,
                "Izin lokasi ditolak. Kamu tetap bisa cari lokasi lewat kolom pencarian.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestGps() {
        if (hasAnyLocationPermission()) ensureLocationOnThenFetch()
        else gpsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    var showLocationOnboarding by remember {
        mutableStateOf(!prefs.getBoolean("location_onboarding_done", false) && !hasAnyLocationPermission())
    }

    // Inisialisasi tunggal tanpa race condition:
    // Kalau izin GPS ada → ambil GPS segar; kalau belum → muat lokasi tersimpan, lalu refresh tiap 10 menit
    LaunchedEffect(Unit) {
        if (hasAnyLocationPermission()) {
            ensureLocationOnThenFetch()
        } else {
            loadWeather(lat, lon, cityName, isFromGps = false)
        }
        while (true) {
            delay(10 * 60 * 1000L)
            loadWeather(lat, lon, cityName, isFromGps = false)
        }
    }

    LaunchedEffect(showLocationOnboarding) {
        if (!showLocationOnboarding && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showLocationOnboarding) {
        Dialog(onDismissRequest = { }) {
            ProSurfaceCard(padding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Aktifkan Lokasi Presisi",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Izinkan akses lokasi (pilih \"Lokasi akurat\") agar Stormy dapat mendeteksi nama jalan dan memberi peringatan hujan tepat di posisi kamu.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            prefs.edit().putBoolean("location_onboarding_done", true).apply()
                            showLocationOnboarding = false
                            requestGps()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Aktifkan Lokasi", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            prefs.edit().putBoolean("location_onboarding_done", true).apply()
                            showLocationOnboarding = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Nanti saja", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // POP-UP DETAIL CUACA / NOTIFIKASI
    if (externalPopupMessage != null) {
        Dialog(onDismissRequest = onDismissExternalPopup) {
            ProSurfaceCard(padding = 24.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherVectorIcon(
                        code = weather?.current?.weather_code ?: 51,
                        isDay = weather?.current?.is_day ?: 1,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = externalPopupTitle ?: "Detail Prakiraan Cuaca",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(cityName, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = externalPopupMessage,
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onDismissExternalPopup,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Tutup", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    val currentCode = weather?.current?.weather_code ?: 0
    val isDay = weather?.current?.is_day ?: 1
    val isRainyNow = currentCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)
    val isCloudyNow = currentCode in listOf(2, 3, 45, 48)

    // Palet warna atmosferik profesional (deep slate & navy)
    val targetColors = when {
        isRainyNow -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF090D16))
        isCloudyNow && isDay == 1 -> listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D16))
        isDay == 0 -> listOf(Color(0xFF090D16), Color(0xFF0F172A), Color(0xFF050811))
        else -> listOf(Color(0xFF0C4A6E), Color(0xFF0F172A), Color(0xFF090D16))
    }

    val bgTop by animateColorAsState(targetColors[0], tween(1000), label = "bg1")
    val bgMid by animateColorAsState(targetColors[1], tween(1000), label = "bg2")
    val bgBot by animateColorAsState(targetColors[2], tween(1000), label = "bg3")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgMid, bgBot)))
    ) {
        WeatherParticleCanvas(isRain = isRainyNow, isCloudy = isCloudyNow, isDay = isDay == 1)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. TOP APP BAR MINIMALIS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.stormy_logo),
                        contentDescription = "Logo Stormy",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .border(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Stormy",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "Weather App oleh Dvid",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.75f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Peringatan hujan otomatis aktif (cek tiap 30 menit)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (notifGranted) Color(0xFF4ADE80) else Color(0xFFF59E0B))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (notifGranted) "Alert Aktif" else "Izinkan Alert",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.75f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable { loadWeather(lat, lon, cityName, isFromGps = false) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading && weather != null) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat ulang", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 2. SEARCH BAR + QUICK SAVED LOCATIONS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.75f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(9.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Cari jalan, kelurahan, atau kota...",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { q ->
                                    searchQuery = q
                                    searchJob?.cancel()
                                    if (q.trim().length >= 3) {
                                        searchJob = scope.launch {
                                            delay(350)
                                            try {
                                                searchResults = ApiClient.streetService.searchStreetOrPlace(q.trim())
                                            } catch (_: Exception) {}
                                        }
                                    } else {
                                        searchResults = emptyList()
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Hapus",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        focusManager.clearFocus()
                                    }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.75f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .clickable {
                                focusManager.clearFocus()
                                requestGps()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDetectingGps) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = "Deteksi GPS", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Bar Lokasi Cepat (Lokasi GPS + Lokasi Tersimpan)
                SavedLocationsBar(
                    currentCity = cityName,
                    gpsCity = gpsCityName,
                    savedLocations = savedLocations,
                    isDetectingGps = isDetectingGps,
                    onSelectGps = {
                        val gLat = prefs.getFloat("gps_lat", Float.NaN)
                        val gLon = prefs.getFloat("gps_lon", Float.NaN)
                        val gCity = prefs.getString("gps_city", null)
                        if (!gLat.isNaN() && !gLon.isNaN() && !gCity.isNullOrBlank()) {
                            lat = gLat.toDouble()
                            lon = gLon.toDouble()
                            cityName = gCity
                            loadWeather(lat, lon, cityName, isFromGps = false)
                        } else {
                            requestGps()
                        }
                    },
                    onSelectLocation = { name, sLat, sLon ->
                        lat = sLat
                        lon = sLon
                        cityName = name
                        loadWeather(lat, lon, cityName, isFromGps = false)
                    },
                    onSaveCurrent = {
                        val updated = (savedLocations + Triple(cityName, lat, lon))
                            .distinctBy { it.first.lowercase() }
                            .takeLast(6)
                        savedLocations = updated
                        saveLocationsToPrefs(prefs, updated)
                    },
                    onRemoveLocation = { name ->
                        val updated = savedLocations.filterNot { it.first.equals(name, ignoreCase = true) }
                        savedLocations = updated
                        saveLocationsToPrefs(prefs, updated)
                    }
                )

                // Dropdown Hasil Pencarian
                AnimatedVisibility(visible = searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    ) {
                        searchResults.forEachIndexed { idx, place ->
                            val disp = place.display_name ?: ""
                            val parts = disp.split(",")
                            val mainStreet = parts.firstOrNull()?.trim() ?: disp
                            val subArea = parts.drop(1).take(3).joinToString(",").trim()

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        val parsedLat = place.lat?.toDoubleOrNull() ?: lat
                                        val parsedLon = place.lon?.toDoubleOrNull() ?: lon
                                        lat = parsedLat
                                        lon = parsedLon
                                        cityName = if (parts.size >= 2) "${parts[0].trim()}, ${parts[1].trim()}" else mainStreet
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        loadWeather(lat, lon, cityName, isFromGps = false)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(mainStreet, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (subArea.isNotEmpty()) {
                                    Text(subArea, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (idx < searchResults.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }

            // Banner Offline
            AnimatedVisibility(visible = isOfflineCache && weather != null) {
                val timeText = if (lastUpdatedMs > 0L) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdatedMs))
                } else ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                        .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { loadWeather(lat, lon, cityName, isFromGps = false) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (timeText.isNotEmpty()) "Data tersimpan pukul $timeText (Offline)" else "Menampilkan data tersimpan (Offline)",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "Muat ulang",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isLoading && weather == null) {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), strokeWidth = 2.5.dp)
                }
            } else if (weather == null && loadError != null) {
                ProSurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Tidak Ada Koneksi Internet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = loadError ?: "Periksa jaringan kamu lalu coba lagi.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { loadWeather(lat, lon, cityName, isFromGps = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Coba Lagi", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (weather != null) {
                val cur = weather!!.current
                val hr = weather!!.hourly
                val curHourIdx = WeatherHelper.currentHourIndex(weather!!)
                val next1Idx = (curHourIdx + 1).coerceAtMost(hr.time.size - 1)
                val next1Prob = hr.precipitation_probability[next1Idx]
                val nextCode = hr.weather_code[next1Idx]
                val rainSoon = RainAlertEngine.findRainSoon(weather!!)

                val (smartTitle, smartBannerMsg) = createSmartWeatherMessage(weather!!, cityName)
                val showRainBanner = rainSoon != null || isRainyNow || nextCode >= 51 || next1Prob >= 35 || cur.precipitation > 0.0

                // 3. HERO WEATHER CARD (ASYMMETRIC PROFESSIONAL LAYOUT)
                HeroWeatherSection(
                    cityName = cityName,
                    isDetectingGps = isDetectingGps,
                    weather = weather!!,
                    localTimeText = localTimeText
                )

                // 3B. PERINGATAN DINI CUACA EKSTREM RESMI BMKG (CAP NOWCAST)
                BmkgWarningSection(
                    bmkgResult = bmkgResult,
                    cityName = cityName,
                    onShowDetail = { title, msg -> onTriggerPopupAndNotif(title, msg) }
                )

                // 4. BANNER PERINGATAN CUACA / HUJAN 30 MENIT
                AnimatedVisibility(visible = showRainBanner) {
                    val accent = if (rainSoon != null) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .clickable { onTriggerPopupAndNotif(smartTitle, smartBannerMsg) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (rainSoon != null) Icons.Default.WarningAmber else Icons.Default.Umbrella,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (rainSoon != null) "PERINGATAN HUJAN JARAK DEKAT" else "RINGKASAN PRAKIRAAN",
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = smartBannerMsg,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // 4B. RINGKASAN CUACA PAGI (MORNING BRIEFING: PAGI, SIANG, SORE, MALAM)
                val briefingSummary = remember(weather, cityName) {
                    MorningBriefingEngine.buildDailyBriefing(weather!!, cityName)
                }
                MorningBriefingSection(
                    summary = briefingSummary,
                    enabled = morningBriefingEnabled,
                    onToggleEnabled = { enabled ->
                        morningBriefingEnabled = enabled
                        prefs.edit().putBoolean(MorningBriefingEngine.KEY_ENABLED, enabled).apply()
                        android.widget.Toast.makeText(
                            context,
                            if (enabled) "Notifikasi Ringkasan Pagi (06:00) diaktifkan" else "Notifikasi Ringkasan Pagi dinonaktifkan",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    onTriggerPreview = {
                        val title = "Ringkasan Cuaca Hari Ini • $cityName"
                        NotificationHelper.sendRainNotification(context, title, briefingSummary.fullMessage)
                        onTriggerPopupAndNotif(title, briefingSummary.fullMessage)
                    }
                )

                // 5. PRAKIRAAN 24 JAM (DENGAN RAIN PROBABILITY BAR)
                HourlyForecastSection(
                    weather = weather!!,
                    curHourIdx = curHourIdx,
                    cityName = cityName,
                    onSelectHour = { title, msg -> onTriggerPopupAndNotif(title, msg) }
                )

                // 6. PRAKIRAAN 7 HARI (DENGAN TEMPERATURE RANGE BAR)
                SevenDayForecastSection(weather = weather!!)

                // 6B. KUALITAS UDARA (US AQI, PM2.5, PM10) & INDEKS UV
                AirQualityAndUvSection(airQuality = airQuality)

                // 7. BENTO METRICS GRID (DETAIL ATMOSFER 2x3)
                BentoMetricsGrid(weather = weather!!, nextHourProb = next1Prob)

                // 8. RADAR LIVE WINDY (DENGAN LAYER SWITCHER & SCROLL LOCK TOGGLE)
                ProSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "RADAR LIVE ECMWF",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.9.sp
                            )
                        }

                        // Tombol Kunci / Geser Peta agar scroll halaman tidak nyangkut
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (radarInteractive) Color(0xFF38BDF8).copy(alpha = 0.22f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    0.8.dp,
                                    if (radarInteractive) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.14f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { radarInteractive = !radarInteractive }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (radarInteractive) Icons.Default.PanToolAlt else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (radarInteractive) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (radarInteractive) "Mode Geser Peta" else "Ketuk utk Geser",
                                color = if (radarInteractive) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Pilihan Layer Radar Windy
                    val overlays = listOf(
                        "rain" to "Hujan",
                        "wind" to "Angin",
                        "clouds" to "Awan",
                        "thunder" to "Petir"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        overlays.forEach { (key, label) ->
                            val selected = radarOverlay == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) Color(0xFF38BDF8).copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        0.8.dp,
                                        if (selected) Color(0xFF38BDF8).copy(alpha = 0.6f)
                                        else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { radarOverlay = key }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    ) {
                        val windyUrl = "https://embed.windy.com/embed2.html?lat=$lat&lon=$lon&detailLat=$lat&detailLon=$lon&width=650&height=450&zoom=10&level=surface&overlay=$radarOverlay&product=ecmwf&menu=&message=true&marker=true&calendar=now&pressure=&type=map&location=coordinates&detail=&metricWind=km%2Fh&metricTemp=%C2%B0C&radarRange=-1"

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                    webChromeClient = WebChromeClient()
                                    webViewClient = WebViewClient()
                                    loadUrl(windyUrl)
                                }
                            },
                            update = { webView ->
                                webView.setOnTouchListener { v, _ ->
                                    v.parent.requestDisallowInterceptTouchEvent(radarInteractive)
                                    !radarInteractive
                                }
                                val coordTag = "$lat,$lon,$radarOverlay"
                                if (webView.tag != coordTag) {
                                    webView.tag = coordTag
                                    webView.loadUrl(windyUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 9. FOOTER MINIMALIS
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Stormy • Data ECMWF & Open-Meteo • by Dvid",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun loadSavedLocations(prefs: android.content.SharedPreferences): List<Triple<String, Double, Double>> {
    val raw = prefs.getString("saved_locations_v1", null) ?: return emptyList()
    return raw.split(";;").mapNotNull { item ->
        val p = item.split("|")
        if (p.size == 3) {
            val lat = p[1].toDoubleOrNull()
            val lon = p[2].toDoubleOrNull()
            if (lat != null && lon != null) Triple(p[0], lat, lon) else null
        } else null
    }
}

private fun saveLocationsToPrefs(
    prefs: android.content.SharedPreferences,
    list: List<Triple<String, Double, Double>>
) {
    val serialized = list.joinToString(";;") { "${it.first}|${it.second}|${it.third}" }
    prefs.edit().putString("saved_locations_v1", serialized).apply()
}

@Composable
fun WeatherParticleCanvas(isRain: Boolean, isCloudy: Boolean, isDay: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "weather_anim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "rain_drop"
    )

    val drops = remember { List(32) { Offset(Random.nextFloat(), Random.nextFloat()) } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (isRain) {
            drops.forEachIndexed { index, pos ->
                val speedFactor = 0.6f + (index % 5) * 0.15f
                val y = ((pos.y + animProgress * speedFactor) % 1f) * size.height
                val x = pos.x * size.width
                drawLine(
                    color = Color(0xFF7DD3FC).copy(alpha = 0.16f),
                    start = Offset(x, y),
                    end = Offset(x - 4f, y + 24f),
                    strokeWidth = 1.8f
                )
            }
        } else if (isDay && !isCloudy) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.10f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.85f, size.height * 0.10f),
                radius = size.width * 0.65f
            )
        } else if (isCloudy) {
            val cloudShift = sin(animProgress * Math.PI * 2).toFloat() * 24f
            drawCircle(
                color = Color.White.copy(alpha = 0.035f),
                center = Offset(size.width * 0.3f + cloudShift, size.height * 0.18f),
                radius = size.width * 0.45f
            )
        }
    }
}