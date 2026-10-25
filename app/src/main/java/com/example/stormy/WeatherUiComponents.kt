package com.example.stormy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// =========================================================
// SURFACE CARD PROFESIONAL (SUBTLE MATTE GLASS, BUKAN AI SLOP)
// =========================================================
@Composable
fun ProSurfaceCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E293B).copy(alpha = 0.72f),
                        Color(0xFF0F172A).copy(alpha = 0.82f)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionHeader(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = title.uppercase(),
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.9.sp
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// =========================================================
// ILUSTRASI VEKTOR CUACA KUSTOM (MENGGANTIKAN EMOJI RAKSASA)
// =========================================================
@Composable
fun WeatherVectorIcon(
    code: Int,
    isDay: Int,
    modifier: Modifier = Modifier.size(64.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val isClear = code == 0
        val isPartlyCloudy = code in listOf(1, 2)
        val isOvercastOrFog = code in listOf(3, 45, 48)
        val isDrizzle = code in listOf(51, 53, 55)
        val isRain = code in listOf(61, 63, 65, 80, 81, 82)
        val isThunder = code >= 95

        // 1. Matahari / Bulan (untuk cerah atau berawan sebagian)
        if (isClear || isPartlyCloudy) {
            val sunCenter = if (isClear) Offset(w * 0.5f, h * 0.5f) else Offset(w * 0.36f, h * 0.36f)
            val sunRadius = if (isClear) w * 0.28f else w * 0.20f

            if (isDay == 1) {
                // Halo cahaya matahari
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFBBF24).copy(alpha = 0.42f), Color.Transparent),
                        center = sunCenter,
                        radius = sunRadius * 1.9f
                    ),
                    center = sunCenter,
                    radius = sunRadius * 1.9f
                )
                // Sinar matahari halus
                val rayCount = 8
                for (i in 0 until rayCount) {
                    val angle = (i * (360f / rayCount)) * (Math.PI / 180.0)
                    val r1 = sunRadius * 1.22f
                    val r2 = sunRadius * 1.48f
                    drawLine(
                        color = Color(0xFFFDE047),
                        start = Offset(
                            sunCenter.x + (cos(angle) * r1).toFloat(),
                            sunCenter.y + (sin(angle) * r1).toFloat()
                        ),
                        end = Offset(
                            sunCenter.x + (cos(angle) * r2).toFloat(),
                            sunCenter.y + (sin(angle) * r2).toFloat()
                        ),
                        strokeWidth = w * 0.045f,
                        cap = StrokeCap.Round
                    )
                }
                // Inti matahari bergradasi
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B)),
                        start = Offset(sunCenter.x - sunRadius, sunCenter.y - sunRadius),
                        end = Offset(sunCenter.x + sunRadius, sunCenter.y + sunRadius)
                    ),
                    center = sunCenter,
                    radius = sunRadius
                )
            } else {
                // Glow bulan malam
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF93C5FD).copy(alpha = 0.35f), Color.Transparent),
                        center = sunCenter,
                        radius = sunRadius * 1.8f
                    ),
                    center = sunCenter,
                    radius = sunRadius * 1.8f
                )
                // Bulan purnama/sabit lembut
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFE0F2FE), Color(0xFF93C5FD)),
                        start = Offset(sunCenter.x - sunRadius, sunCenter.y - sunRadius),
                        end = Offset(sunCenter.x + sunRadius, sunCenter.y + sunRadius)
                    ),
                    center = sunCenter,
                    radius = sunRadius
                )
                // Kawah lembut
                drawCircle(
                    color = Color(0xFF60A5FA).copy(alpha = 0.35f),
                    center = Offset(sunCenter.x - sunRadius * 0.25f, sunCenter.y - sunRadius * 0.15f),
                    radius = sunRadius * 0.22f
                )
            }
        }

        // 2. Awan Berlapis (jika tidak cerah murni)
        if (!isClear) {
            val cloudTopColor = when {
                isThunder -> Color(0xFF94A3B8)
                isRain || isDrizzle -> Color(0xFFE2E8F0)
                else -> Color(0xFFF8FAFC)
            }
            val cloudBotColor = when {
                isThunder -> Color(0xFF475569)
                isRain || isDrizzle -> Color(0xFF94A3B8)
                else -> Color(0xFFCBD5E1)
            }

            val baseY = if (isRain || isDrizzle || isThunder) h * 0.56f else h * 0.64f
            val cloudBrush = Brush.verticalGradient(
                colors = listOf(cloudTopColor, cloudBotColor),
                startY = h * 0.22f,
                endY = baseY
            )

            // Bayangan awan belakang jika mendung/badai
            if (isOvercastOrFog || isRain || isThunder) {
                drawCircle(
                    color = Color(0xFF64748B).copy(alpha = 0.55f),
                    center = Offset(w * 0.66f, baseY - h * 0.18f),
                    radius = w * 0.19f
                )
            }

            // Gumpalan awan utama
            drawRoundRect(
                brush = cloudBrush,
                topLeft = Offset(w * 0.16f, baseY - h * 0.18f),
                size = Size(w * 0.68f, h * 0.22f),
                cornerRadius = CornerRadius(h * 0.11f, h * 0.11f)
            )
            drawCircle(
                brush = cloudBrush,
                center = Offset(w * 0.34f, baseY - h * 0.14f),
                radius = w * 0.16f
            )
            drawCircle(
                brush = cloudBrush,
                center = Offset(w * 0.54f, baseY - h * 0.20f),
                radius = w * 0.21f
            )
            drawCircle(
                brush = cloudBrush,
                center = Offset(w * 0.70f, baseY - h * 0.11f),
                radius = w * 0.13f
            )
        }

        // 3. Rintik Hujan / Gerimis
        if (isDrizzle || isRain || isThunder) {
            val dropCount = if (isDrizzle) 2 else 3
            val dropXs = if (dropCount == 2) listOf(0.38f, 0.62f) else listOf(0.30f, 0.50f, 0.70f)
            dropXs.forEachIndexed { idx, xRatio ->
                val startX = w * xRatio
                val startY = h * (0.66f + (idx % 2) * 0.04f)
                val len = if (isDrizzle) h * 0.11f else h * 0.16f
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(startX, startY),
                    end = Offset(startX - w * 0.05f, startY + len),
                    strokeWidth = w * 0.055f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 4. Petir (Thunderstorm)
        if (isThunder) {
            val bolt = Path().apply {
                moveTo(w * 0.54f, h * 0.50f)
                lineTo(w * 0.40f, h * 0.72f)
                lineTo(w * 0.51f, h * 0.72f)
                lineTo(w * 0.44f, h * 0.94f)
                lineTo(w * 0.64f, h * 0.67f)
                lineTo(w * 0.52f, h * 0.67f)
                close()
            }
            drawPath(
                path = bolt,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFDE047), Color(0xFFF59E0B))
                )
            )
        }
    }
}

// =========================================================
// BAR LOKASI CEPAT (LOKASI GPS + LOKASI TERSIMPAN)
// =========================================================
@Composable
fun SavedLocationsBar(
    currentCity: String,
    gpsCity: String?,
    savedLocations: List<Triple<String, Double, Double>>,
    isDetectingGps: Boolean,
    onSelectGps: () -> Unit,
    onSelectLocation: (String, Double, Double) -> Unit,
    onSaveCurrent: () -> Unit,
    onRemoveLocation: (String) -> Unit
) {
    val isCurrentSaved = savedLocations.any { it.first.equals(currentCity, ignoreCase = true) }
    val isCurrentGps = gpsCity != null && currentCity.equals(gpsCity, ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chip 1: Lokasi GPS Saat Ini
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isCurrentGps) Color(0xFF38BDF8).copy(alpha = 0.22f)
                    else Color(0xFF1E293B).copy(alpha = 0.65f)
                )
                .border(
                    width = 1.dp,
                    color = if (isCurrentGps) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onSelectGps() }
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isDetectingGps) "Mencari GPS..." else (gpsCity?.substringBefore(",") ?: "Lokasi GPS"),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = if (isCurrentGps) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }

        // Chip Lokasi Favorit yang Tersimpan
        savedLocations.forEach { (name, sLat, sLon) ->
            val selected = name.equals(currentCity, ignoreCase = true)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) Color(0xFF38BDF8).copy(alpha = 0.22f)
                        else Color(0xFF1E293B).copy(alpha = 0.65f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectLocation(name, sLat, sLon) }
                    .padding(start = 11.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name.substringBefore(","),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hapus",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier
                        .size(13.dp)
                        .clickable { onRemoveLocation(name) }
                )
            }
        }

        // Tombol "+ Simpan Lokasi" jika sedang melihat lokasi hasil pencarian yang belum disimpan
        if (!isCurrentGps && !isCurrentSaved && currentCity.isNotBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.65f))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onSaveCurrent() }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Simpan Lokasi",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// =========================================================
// HERO WEATHER SECTION (ASYMMETRIC FLAGSHIP LAYOUT)
// =========================================================
@Composable
fun HeroWeatherSection(
    cityName: String,
    isDetectingGps: Boolean,
    weather: WeatherResponse,
    localTimeText: String
) {
    val cur = weather.current
    val dailyMax = weather.daily?.temperature_2m_max?.firstOrNull()?.toInt()
        ?: weather.hourly.temperature_2m.take(24).maxOrNull()?.toInt() ?: cur.temperature_2m.toInt()
    val dailyMin = weather.daily?.temperature_2m_min?.firstOrNull()?.toInt()
        ?: weather.hourly.temperature_2m.take(24).minOrNull()?.toInt() ?: cur.temperature_2m.toInt()

    ProSurfaceCard(padding = 20.dp) {
        // Baris atas: Nama jalan & waktu lokal lokasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isDetectingGps) "Mendeteksi jalan..." else cityName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$localTimeText • ECMWF",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(18.dp))

        // Baris utama: Suhu besar di kiri, Ilustrasi Vektor di kanan
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${cur.temperature_2m.toInt()}°",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp,
                    lineHeight = 72.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = WeatherHelper.getConditionLabel(cur.weather_code),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Terasa ${cur.apparent_temperature.toInt()}°  •  T:$dailyMax°  R:$dailyMin°",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center
            ) {
                WeatherVectorIcon(
                    code = cur.weather_code,
                    isDay = cur.is_day,
                    modifier = Modifier.size(92.dp)
                )
            }
        }
    }
}

// =========================================================
// PREDIKSI 24 JAM DENGAN VISUAL BAR PELUANG HUJAN
// =========================================================
@Composable
fun HourlyForecastSection(
    weather: WeatherResponse,
    curHourIdx: Int,
    cityName: String,
    onSelectHour: (String, String) -> Unit
) {
    val hr = weather.hourly
    ProSurfaceCard {
        SectionHeader(
            icon = Icons.Default.Schedule,
            title = "Prakiraan 24 Jam",
            subtitle = "Ketuk jam untuk detail"
        )
        Spacer(Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(24) { offset ->
                val idx = (curHourIdx + offset).coerceAtMost(hr.time.size - 1)
                val isoTime = hr.time[idx]
                val rawHourStr = isoTime.takeLast(5)
                val isMidnight = rawHourStr == "00:00" && offset > 0
                val timeStr = when {
                    offset == 0 -> "Sekarang"
                    isMidnight -> "Besok"
                    else -> rawHourStr
                }
                val temp = hr.temperature_2m[idx].toInt()
                val prob = hr.precipitation_probability[idx]
                val code = hr.weather_code[idx]
                val hourIsDay = WeatherHelper.isDayAt(weather, idx)

                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (offset == 0) Color(0xFF38BDF8).copy(alpha = 0.16f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (offset == 0) Color(0xFF38BDF8).copy(alpha = 0.45f)
                            else Color.White.copy(alpha = 0.07f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val hourMsg = WeatherHelper.buildForecastMessage(
                                isoTime = isoTime,
                                code = code,
                                location = cityName,
                                temp = temp,
                                prob = prob,
                                res = weather
                            )
                            val dayLabel = WeatherHelper.dayPrefix(isoTime, weather)
                            onSelectHour(
                                "$dayLabel • Pukul $rawHourStr",
                                hourMsg
                            )
                        }
                        .padding(vertical = 11.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = timeStr,
                        color = if (offset == 0 || isMidnight) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (offset == 0 || isMidnight) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    WeatherVectorIcon(
                        code = code,
                        isDay = hourIsDay,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "$temp°",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(6.dp))

                    // Mini bar visual peluang hujan
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((prob / 100f).coerceIn(0.08f, 1f))
                                .clip(CircleShape)
                                .background(
                                    if (prob >= 40) Color(0xFF38BDF8)
                                    else Color(0xFF64748B)
                                )
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "$prob%",
                        color = if (prob >= 40) Color(0xFF38BDF8) else Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = if (prob >= 40) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// =========================================================
// PRAKIRAAN 7 HARI (DENGAN TEMPERATURE RANGE BAR ALA IOS/WINDY)
// =========================================================
@Composable
fun SevenDayForecastSection(weather: WeatherResponse) {
    val d = weather.daily ?: return
    val times = d.time ?: return
    if (times.isEmpty()) return

    val maxList = d.temperature_2m_max?.filterNotNull() ?: emptyList()
    val minList = d.temperature_2m_min?.filterNotNull() ?: emptyList()
    val weekMax = (maxList.maxOrNull() ?: 34.0).toFloat()
    val weekMin = (minList.minOrNull() ?: 22.0).toFloat()
    val span = (weekMax - weekMin).coerceAtLeast(4f)

    ProSurfaceCard {
        SectionHeader(
            icon = Icons.Default.CalendarMonth,
            title = "Prakiraan 7 Hari",
            subtitle = "Rentang suhu & hujan"
        )
        Spacer(Modifier.height(12.dp))

        val count = times.size.coerceAtMost(7)
        for (i in 0 until count) {
            val dayLabel = WeatherHelper.dayNameLabel(times[i], weather)
            val code = d.weather_code?.getOrNull(i) ?: 2
            val maxT = d.temperature_2m_max?.getOrNull(i)?.toInt() ?: 30
            val minT = d.temperature_2m_min?.getOrNull(i)?.toInt() ?: 24
            val prob = d.precipitation_probability_max?.getOrNull(i) ?: 0

            val startFrac = ((minT - weekMin) / span).coerceIn(0f, 0.85f)
            val endFrac = ((maxT - weekMin) / span).coerceIn(startFrac + 0.12f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel,
                    color = if (i == 0) Color.White else Color(0xFFCBD5E1),
                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.width(74.dp),
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.width(66.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeatherVectorIcon(
                        code = code,
                        isDay = 1,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    if (prob >= 20) {
                        Text(
                            text = "$prob%",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "$minT°",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(30.dp)
                )

                // Bar rentang suhu min..max
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.10f),
                            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                        )
                        val barLeft = size.width * startFrac
                        val barWidth = (size.width * (endFrac - startFrac)).coerceAtLeast(size.height)
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF38BDF8), Color(0xFFFBBF24)),
                                startX = barLeft,
                                endX = barLeft + barWidth
                            ),
                            topLeft = Offset(barLeft, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                        )
                    }
                }

                Text(
                    text = "$maxT°",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(30.dp)
                )
            }

            if (i < count - 1) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            }
        }
    }
}

// =========================================================
// BENTO METRICS GRID (DETAIL CUACA PROFESIONAL 2x3)
// =========================================================
@Composable
fun BentoMetricsGrid(weather: WeatherResponse, nextHourProb: Int) {
    val cur = weather.current
    val humidity = cur.relative_humidity_2m ?: 80
    val windSpeed = cur.wind_speed_10m ?: 0.0
    val windDir = WeatherHelper.windDirectionLabel(cur.wind_direction_10m)
    val pressure = cur.surface_pressure?.toInt() ?: 1010
    val sunrise = weather.daily?.sunrise?.firstOrNull()?.takeLast(5) ?: "05:45"
    val sunset = weather.daily?.sunset?.firstOrNull()?.takeLast(5) ?: "17:55"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.WaterDrop,
                label = "CURAH & PELUANG",
                value = "${cur.precipitation} mm",
                sub = "Peluang jam depan $nextHourProb%"
            )
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Air,
                label = "ANGIN",
                value = "${String.format("%.1f", windSpeed)} km/j",
                sub = "Arah $windDir"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Opacity,
                label = "KELEMBAPAN",
                value = "$humidity%",
                sub = if (humidity >= 85) "Udara sangat lembap" else "Kelembapan nyaman"
            )
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = "TEKANAN UDARA",
                value = "$pressure hPa",
                sub = if (pressure < 1009) "Tekanan rendah (berawan)" else "Tekanan atmosfer stabil"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.WbSunny,
                label = "MATAHARI TERBIT",
                value = sunrise,
                sub = "Waktu lokal lokasi"
            )
            BentoMetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.NightsStay,
                label = "MATAHARI TERBENAM",
                value = sunset,
                sub = "Waktu lokal lokasi"
            )
        }
    }
}

@Composable
private fun BentoMetricTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    sub: String
) {
    ProSurfaceCard(modifier = modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sub,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================
// 1. BMKG PERINGATAN DINI CUACA EKSTREM (CAP NOWCAST)
// =========================================================
@Composable
fun BmkgWarningSection(
    bmkgResult: BmkgAlertResult?,
    cityName: String,
    onShowDetail: (String, String) -> Unit
) {
    if (bmkgResult == null) return

    val localAlert = bmkgResult.localAlert
    if (localAlert != null) {
        val alertAccent = Color(0xFFEF4444)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF450A0A).copy(alpha = 0.88f),
                            Color(0xFF1E293B).copy(alpha = 0.92f)
                        )
                    )
                )
                .border(1.dp, alertAccent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                .clickable {
                    val detailText = buildString {
                        append(localAlert.description)
                        if (localAlert.pubDate.isNotBlank()) {
                            append("\n\nWaktu Rilis BMKG: ${localAlert.pubDate}")
                        }
                    }
                    onShowDetail(localAlert.title, detailText)
                }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(alertAccent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "PERINGATAN DINI RESMI BMKG",
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp
                    )
                }

                Text(
                    text = "Detail >",
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = localAlert.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = localAlert.description,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        // Status wilayah aman dari peringatan dini ekstrem BMKG + info nasional bila ada
        val natCount = bmkgResult.nationalAlerts.size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.62f))
                .border(0.8.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable {
                    if (natCount > 0) {
                        val summary = bmkgResult.nationalAlerts.take(4).joinToString("\n\n• ", prefix = "• ") {
                            "${it.title}\n  ${it.description.take(140)}..."
                        }
                        onShowDetail(
                            "Status Peringatan Dini BMKG",
                            "Wilayah $cityName saat ini AMAN (tidak ada peringatan cuaca ekstrem aktif).\n\nPantauan Nasional BMKG ($natCount provinsi aktif):\n$summary"
                        )
                    } else {
                        onShowDetail(
                            "Status Peringatan Dini BMKG",
                            "Wilayah $cityName saat ini AMAN. Tidak ada peringatan dini cuaca ekstrem BMKG yang aktif."
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        text = "BMKG Nowcast • Wilayah Aman",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (natCount > 0) {
                            "Tidak ada cuaca ekstrem di lokasi ini ($natCount peringatan provinsi lain)"
                        } else {
                            "Tidak ada peringatan cuaca ekstrem aktif dari BMKG"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Cek",
                color = Color(0xFF34D399),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// =========================================================
// 2. RINGKASAN CUACA PAGI (MORNING BRIEFING CARD)
// =========================================================
@Composable
fun MorningBriefingSection(
    summary: MorningBriefingEngine.DailyBriefingSummary,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onTriggerPreview: () -> Unit
) {
    ProSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WbTwilight,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "RINGKASAN CUACA HARIAN",
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.9.sp
                )
            }

            // Toggle Notifikasi Otomatis Jam 06:00 Pagi
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (enabled) Color(0xFF38BDF8).copy(alpha = 0.18f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        0.8.dp,
                        if (enabled) Color(0xFF38BDF8).copy(alpha = 0.6f)
                        else Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onToggleEnabled(!enabled) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (enabled) "Notif Pagi Aktif" else "Notif Pagi Off",
                    color = if (enabled) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = summary.headline,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(12.dp))

        // 4 Slot Waktu: Pagi, Siang, Sore, Malam
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            summary.periods.forEach { slot ->
                val isWet = slot.maxProb >= 40 || slot.code >= 51
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isWet) Color(0xFF38BDF8).copy(alpha = 0.11f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .border(
                            0.8.dp,
                            if (isWet) Color(0xFF38BDF8).copy(alpha = 0.4f)
                            else Color.White.copy(alpha = 0.07f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = slot.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = slot.timeRange,
                        color = Color(0xFF64748B),
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    WeatherVectorIcon(
                        code = slot.code,
                        isDay = slot.isDay,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${slot.temp}°",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Hujan ${slot.maxProb}%",
                        color = if (slot.maxProb >= 40) Color(0xFF38BDF8) else Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable { onTriggerPreview() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lihat laporan lengkap & tes notifikasi pagi",
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// =========================================================
// 3. KUALITAS UDARA (US AQI, PM2.5, PM10) & UV INDEX CARD
// =========================================================
@Composable
fun AirQualityAndUvSection(airQuality: AirQualityCurrent?) {
    if (airQuality == null) return

    val aqi = airQuality.us_aqi ?: 35
    val pm25 = airQuality.pm2_5 ?: 8.0
    val pm10 = airQuality.pm10 ?: 15.0
    val uv = airQuality.uv_index ?: 0.0

    val (aqiLabel, aqiColor, aqiAdvice) = when {
        aqi <= 50 -> Triple("Baik", Color(0xFF34D399), "Udara bersih, sangat ideal untuk olahraga & aktivitas luar.")
        aqi <= 100 -> Triple("Sedang", Color(0xFFFBBF24), "Kualitas udara cukup baik bagi mayoritas orang.")
        aqi <= 150 -> Triple("Kurang Sehat (Sensitif)", Color(0xFFF97316), "Kelompok sensitif sebaiknya kurangi aktivitas berat di luar.")
        aqi <= 200 -> Triple("Tidak Sehat", Color(0xFFEF4444), "Gunakan masker saat beraktivitas di luar ruangan.")
        else -> Triple("Sangat Tidak Sehat", Color(0xFFA855F7), "Hindari aktivitas luar ruangan terlalu lama.")
    }

    val (uvLabel, uvColor, uvAdvice) = when {
        uv < 3.0 -> Triple("Rendah", Color(0xFF34D399), "Aman di luar ruangan tanpa proteksi khusus.")
        uv < 6.0 -> Triple("Sedang", Color(0xFFFBBF24), "Disarankan memakai kacamata hitam atau tabir surya.")
        uv < 8.0 -> Triple("Tinggi", Color(0xFFF97316), "Gunakan sunscreen SPF 30+ saat siang hari.")
        uv < 11.0 -> Triple("Sangat Tinggi", Color(0xFFEF4444), "Hindari terik matahari langsung pukul 11:00–14:00.")
        else -> Triple("Ekstrem", Color(0xFFA855F7), "Sinar UV sangat menyengat, wajib pelindung penuh.")
    }

    ProSurfaceCard {
        SectionHeader(
            icon = Icons.Default.Eco,
            title = "Kualitas Udara & Indeks UV",
            subtitle = "US AQI • PM2.5"
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sub-kartu Kiri: US AQI
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INDEKS AQI",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(aqiColor.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = aqiLabel,
                            color = aqiColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "$aqi",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                // Bar indikator AQI
                val aqiFrac = (aqi / 200f).coerceIn(0.06f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                ) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                    )
                    drawRoundRect(
                        color = aqiColor,
                        size = Size(size.width * aqiFrac, size.height),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "PM2.5: ${String.format("%.1f", pm25)} • PM10: ${String.format("%.0f", pm10)} µg/m³",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Sub-kartu Kanan: UV Index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SINAR UV",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(uvColor.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = uvLabel,
                            color = uvColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = String.format("%.1f", uv),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                // Bar indikator UV (skala 0..11)
                val uvFrac = (uv.toFloat() / 11f).coerceIn(0.06f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                ) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                    )
                    drawRoundRect(
                        color = uvColor,
                        size = Size(size.width * uvFrac, size.height),
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = uvAdvice,
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = aqiAdvice,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

