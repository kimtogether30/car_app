package com.vemoring.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyWeatherForecast(
    val date: LocalDate,
    val weatherCode: Int?,
    val minTemperatureC: Double?,
    val maxTemperatureC: Double?,
    val maxPrecipitationProbabilityPercent: Double?,
    val precipitationMm: Double?,
    val snowfallCm: Double?
)

/** Model forecast, not a vehicle rain sensor or observed exposure. No score/recommendation side effects. */
data class WeeklyWeatherForecast(
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Instant,
    val days: List<DailyWeatherForecast>,
    val timezone: String = "Asia/Seoul",
    val attribution: String = "Open-Meteo · 모델 예보 · 현장 관측 아님"
)

/** Shared by app/phone. Caller supplies location; this class neither obtains it nor schedules refreshes. */
class WeeklyWeatherRepository(
    private val request: (String) -> JSONObject = ::requestWeeklyWeather,
    private val now: () -> Instant = Instant::now
) {
    /** Today through today+6 in Korea. Network/invalid response failures propagate; never invent a forecast. */
    suspend fun fetch(latitude: Double, longitude: Double): WeeklyWeatherForecast = withContext(Dispatchers.IO) {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val today = now().atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
        val fields = "weather_code,temperature_2m_min,temperature_2m_max,precipitation_probability_max,precipitation_sum,snowfall_sum"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&daily=$fields&forecast_days=7&timezone=Asia%2FSeoul&temperature_unit=celsius&precipitation_unit=mm"
        val response = request(url)
        require(!response.optBoolean("error") && response.getString("timezone") == "Asia/Seoul")
        val daily = response.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        require(dates.length() == 7) { "Expected seven forecast dates" }
        fields.split(',').forEach { key ->
            if (daily.has(key) && !daily.isNull(key)) require(daily.getJSONArray(key).length() == 7)
        }
        fun number(key: String, index: Int): Double? {
            val a = daily.optJSONArray(key) ?: return null
            if (a.isNull(index)) return null
            return (a.opt(index) as? Number)?.toDouble()?.takeIf { it.isFinite() }
        }
        val days = (0..6).map { index ->
            val date = LocalDate.parse(dates.getString(index))
            require(date == today.plusDays(index.toLong())) { "Forecast dates do not match Korean today" }
            val code = number("weather_code", index)?.takeIf { it in 0.0..99.0 && it % 1.0 == 0.0 }?.toInt()
            DailyWeatherForecast(date, code, number("temperature_2m_min", index), number("temperature_2m_max", index),
                number("precipitation_probability_max", index)?.takeIf { it in 0.0..100.0 },
                number("precipitation_sum", index)?.takeIf { it >= 0 },
                number("snowfall_sum", index)?.takeIf { it >= 0 })
        }
        require(days.any { it.weatherCode != null || it.minTemperatureC != null || it.maxTemperatureC != null ||
            it.maxPrecipitationProbabilityPercent != null || it.precipitationMm != null || it.snowfallCm != null })
        WeeklyWeatherForecast(latitude, longitude, now(), days)
    }
}

private fun requestWeeklyWeather(url: String): JSONObject {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Vemoring/1.0 (Android prototype)")
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("Weather HTTP $status")
        // Bound the public response; never log coordinates or response bodies.
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                require(output.size() + count <= 1_048_576) { "Weather response too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return JSONObject(bytes.toString(Charsets.UTF_8))
    } finally { connection.disconnect() }
}
