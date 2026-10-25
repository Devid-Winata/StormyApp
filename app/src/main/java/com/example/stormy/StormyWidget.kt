package com.example.stormy

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget layar utama: suhu, kondisi, dan kapan hujan berikutnya di lokasi kamu. */
class StormyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
        // Kalau data sudah lama (> 30 menit), minta data baru di background
        val cached = WeatherCache.load(context, WeatherCache.SLOT_HOME)
        if (cached == null || System.currentTimeMillis() - cached.savedAt > 30 * 60 * 1000L) {
            RainAlertWorker.runNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        RainAlertWorker.runNow(context)
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StormyWidget::class.java))
            ids.forEach { update(context, mgr, it) }
        }

        private fun update(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_stormy)

            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            val cached = WeatherCache.load(context, WeatherCache.SLOT_HOME)
                ?: WeatherCache.load(context, WeatherCache.SLOT_LAST_VIEW)

            if (cached == null) {
                views.setTextViewText(R.id.widget_location, "Stormy")
                views.setTextViewText(R.id.widget_emoji, "⛈️")
                views.setTextViewText(R.id.widget_temp, "--°")
                views.setTextViewText(R.id.widget_condition, "Buka app untuk memuat")
                views.setTextViewText(R.id.widget_rain, "")
                views.setTextViewText(R.id.widget_updated, "")
                mgr.updateAppWidget(widgetId, views)
                return
            }

            val w = cached.weather
            val idx = WeatherHelper.currentHourIndex(w)
            val fresh = System.currentTimeMillis() - cached.savedAt < 45 * 60 * 1000L

            // Data "current" hanya akurat kalau masih baru; kalau lama, pakai prediksi jam ini
            val code = if (fresh) w.current.weather_code else w.hourly.weather_code[idx]
            val temp = if (fresh) w.current.temperature_2m else w.hourly.temperature_2m[idx]
            val isDay = if (fresh) w.current.is_day else WeatherHelper.isDayAt(w, idx)

            views.setTextViewText(R.id.widget_location, "📍 ${cached.city}")
            views.setTextViewText(R.id.widget_emoji, WeatherHelper.getConditionEmoji(code, isDay))
            views.setTextViewText(R.id.widget_temp, "${temp.toInt()}°")
            views.setTextViewText(R.id.widget_condition, WeatherHelper.getConditionLabel(code))
            views.setTextViewText(R.id.widget_rain, nextRainText(w, idx))
            views.setTextViewText(
                R.id.widget_updated,
                "Diperbarui " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cached.savedAt))
            )
            mgr.updateAppWidget(widgetId, views)
        }

        /** "🌧️ Hujan ~15:00 (70%)" atau "✅ Aman dari hujan 6 jam ke depan" */
        private fun nextRainText(w: WeatherResponse, curIdx: Int): String {
            for (h in 0..6) {
                val i = curIdx + h
                if (i >= w.hourly.time.size) break
                val code = w.hourly.weather_code[i]
                val prob = w.hourly.precipitation_probability[i]
                if (WeatherHelper.isRainCode(code) && prob >= 40) {
                    val label = WeatherHelper.getConditionLabel(code)
                    return if (h == 0) "${WeatherHelper.getConditionEmoji(code)} $label sekarang ($prob%)"
                    else "${WeatherHelper.getConditionEmoji(code)} $label ~${w.hourly.time[i].takeLast(5)} ($prob%)"
                }
            }
            return "✅ Aman dari hujan 6 jam ke depan"
        }
    }
}
