package com.vemoring.companion

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val FORECAST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

object ForecastLocationPolicy {
    /** Share a regional forecast for a 0.05-degree cell; this is not a claim of observation accuracy. */
    fun region(point: TravelPoint, now: Instant): ForecastRegion? = point.takeIf {it.fresh(now)}?.let {
        ForecastRegion("현재 위치", kotlin.math.round(it.latitude*20)/20, kotlin.math.round(it.longitude*20)/20)
    }
}

data class ForecastRegion(val name: String, val latitude: Double, val longitude: Double) {
    init {
        require(name.isNotBlank() && name.length <= 120)
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
    }
    fun json() = JSONObject().put("name",name).put("latitude",latitude).put("longitude",longitude)
    companion object {
        fun from(j: JSONObject) = ForecastRegion(j.getString("name"),j.getDouble("latitude"),j.getDouble("longitude"))
    }
}

object ForecastRefreshPolicy {
    const val INTERVAL_MILLIS = 60 * 60 * 1000L
    const val RETRY_MILLIS = 10 * 60 * 1000L
    fun due(value: WeeklyWeatherForecast?, region: ForecastRegion, now: Instant): Boolean = value == null ||
        value.latitude != region.latitude || value.longitude != region.longitude ||
        value.days.firstOrNull()?.date != now.atZone(FORECAST_ZONE).toLocalDate() ||
        Duration.between(value.fetchedAt,now).toMillis().let {it < 0 || it >= INTERVAL_MILLIS}
    fun waitMillis(value: WeeklyWeatherForecast, now: Instant): Long {
        val midnight = now.atZone(FORECAST_ZONE).toLocalDate().plusDays(1).atStartOfDay(FORECAST_ZONE).toInstant()
        return minOf(Duration.between(now,value.fetchedAt.plusMillis(INTERVAL_MILLIS)).toMillis(),
            Duration.between(now,midnight).toMillis()).coerceAtLeast(1000)
    }
}

data class WashDateSuggestion(val date: LocalDate?, val message: String)

/** Product advice only: never modifies the wash baseline, score, history, or notifications. */
object WashDatePolicy {
    private fun dry(day: DailyWeatherForecast): Boolean = day.weatherCode in setOf(0,1,2,3,45,48) &&
        day.maxPrecipitationProbabilityPercent?.let {it in 0.0..30.0} == true &&
        day.precipitationMm?.let {it >= 0 && it < 0.5} == true && day.snowfallCm == 0.0 &&
        day.minTemperatureC?.let {it.isFinite() && it > 0} == true &&
        day.maxTemperatureC?.let {it.isFinite() && it < 35 && it >= (day.minTemperatureC ?: Double.POSITIVE_INFINITY)} == true
    fun suggest(value: WeeklyWeatherForecast?, now: Instant): WashDateSuggestion {
        val today=now.atZone(FORECAST_ZONE).toLocalDate()
        if(value == null || Duration.between(value.fetchedAt,now).toMillis() !in 0..6*60*60*1000L ||
            value.days.firstOrNull()?.date != today) return WashDateSuggestion(null,"최신 예보를 받으면 세차 날짜를 추천해요.")
        val day=value.days.zipWithNext().firstOrNull { (a,b) -> a.date >= today && b.date == a.date.plusDays(1) && dry(a) && dry(b) }?.first
        return if(day == null) WashDateSuggestion(null,"이틀 연속 조건을 충족하는 세차 날짜가 아직 없어요.")
        else WashDateSuggestion(day.date,"${day.date.monthValue}/${day.date.dayOfMonth} 세차 추천 · 이틀 연속 비·눈 가능성이 낮아요.")
    }
}

fun DailyWeatherForecast.forDisplay(): DailyWeather {
    val symbol=when(weatherCode) {
        0 -> WeatherSymbol.SUN
        1,2,3 -> WeatherSymbol.CLOUD
        45,48 -> WeatherSymbol.FOG
        51,53,55,56,57,61,63,65,66,67,80,81,82 -> WeatherSymbol.RAIN
        71,73,75,77,85,86 -> WeatherSymbol.SNOW
        95,96,97,99 -> WeatherSymbol.THUNDER
        else -> WeatherSymbol.UNKNOWN
    }
    val text=when(symbol) {
        WeatherSymbol.SUN -> "맑음"; WeatherSymbol.CLOUD -> "구름"; WeatherSymbol.FOG -> "안개"
        WeatherSymbol.RAIN -> "비"; WeatherSymbol.SNOW -> "눈"; WeatherSymbol.THUNDER -> "뇌우"
        WeatherSymbol.UNKNOWN -> "날씨 정보 없음"
    }
    return DailyWeather(date,text,maxTemperatureC,minTemperatureC,maxPrecipitationProbabilityPercent?.toInt(),symbol)
}

object ForecastCodec {
    fun encode(value: WeeklyWeatherForecast): String = JSONObject().put("latitude",value.latitude).put("longitude",value.longitude)
        .put("fetchedAt",value.fetchedAt.toString()).put("days",JSONArray().also {a -> value.days.forEach {d ->
            a.put(JSONObject().put("date",d.date.toString()).put("code",d.weatherCode).put("min",d.minTemperatureC)
                .put("max",d.maxTemperatureC).put("probability",d.maxPrecipitationProbabilityPercent)
                .put("rain",d.precipitationMm).put("snow",d.snowfallCm))
        } }).toString()
    fun decode(raw: String): WeeklyWeatherForecast {
        require(raw.length <= 65536)
        val j=JSONObject(raw); val a=j.getJSONArray("days"); require(a.length()==7)
        val region=ForecastRegion("캐시",j.getDouble("latitude"),j.getDouble("longitude"))
        fun JSONObject.number(key: String) = if(isNull(key)) null else getDouble(key).also {require(it.isFinite())}
        val days=(0 until a.length()).map {n -> val d=a.getJSONObject(n)
            DailyWeatherForecast(LocalDate.parse(d.getString("date")),d.number("code")?.toInt(),d.number("min"),d.number("max"),
                d.number("probability"),d.number("rain"),d.number("snow"))
        }
        require(days.zipWithNext().all {(a,b) -> b.date==a.date.plusDays(1)})
        return WeeklyWeatherForecast(region.latitude,region.longitude,Instant.parse(j.getString("fetchedAt")),days)
    }
}
