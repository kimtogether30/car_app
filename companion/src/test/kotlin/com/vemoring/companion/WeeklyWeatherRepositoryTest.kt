package com.vemoring.companion

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WeeklyWeatherRepositoryTest {
    /** Opt-in public API smoke test. Fixed demonstration location, never a user's live location. */
    @Test fun liveApiReturnsSevenDaysWhenExplicitlyEnabled() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("VEMORING_WEATHER_LIVE") == "1")
        val forecast = WeeklyWeatherRepository().fetch(37.5665,126.9780)
        assertEquals(7,forecast.days.size)
        assertEquals(forecast.days.first().date.plusDays(6),forecast.days.last().date)
        assertTrue(forecast.days.any { it.maxTemperatureC != null })
    }

    // UTC date differs from Korea: the seven days must start on Nov 11, not Nov 10.
    private val time = Instant.parse("2026-11-10T16:00:00Z")
    private fun response() = JSONObject().put("timezone", "Asia/Seoul").put("daily", JSONObject()
        .put("time", JSONArray((0L..6L).map { LocalDate.of(2026,11,11).plusDays(it).toString() }))
        .put("weather_code", JSONArray(listOf(0,3,61,71,0,0,0)))
        .put("temperature_2m_min", JSONArray(listOf(0,-2,1,2,3,4,5)))
        .put("temperature_2m_max", JSONArray(listOf(8,9,10,11,12,13,14)))
        .put("precipitation_probability_max", JSONArray(listOf(0,10,80,90,0,0,0)))
        .put("precipitation_sum", JSONArray(listOf(0,0,2,3,0,0,0)))
        .put("snowfall_sum", JSONArray(listOf(0,0,0,1,0,0,0))))

    @Test fun fetchesSevenKoreanDatesAndKeepsUnitsAndZero() = runBlocking {
        val repository = WeeklyWeatherRepository({ url ->
            assertTrue(url.contains("forecast_days=7"))
            assertTrue(url.contains("timezone=Asia%2FSeoul"))
            assertFalse(url.contains("hourly="))
            response()
        }, { time })
        val result = repository.fetch(37.5,127.0)
        assertEquals(7,result.days.size)
        assertEquals(LocalDate.of(2026,11,11), result.days.first().date)
        assertEquals(LocalDate.of(2026,11,17), result.days.last().date)
        assertEquals(0.0, result.days.first().precipitationMm!!,0.0)
        assertEquals(-2.0, result.days[1].minTemperatureC!!,0.0)
        assertEquals(1.0, result.days[3].snowfallCm!!,0.0)
        assertEquals(time,result.fetchedAt)
    }

    @Test fun nullAndInvalidFieldsNeverBecomeClearWeather() = runBlocking {
        val data=response()
        data.getJSONObject("daily").getJSONArray("weather_code").put(0,JSONObject.NULL)
        data.getJSONObject("daily").getJSONArray("precipitation_sum").put(0,JSONObject.NULL)
        data.getJSONObject("daily").getJSONArray("precipitation_probability_max").put(0,120)
        data.getJSONObject("daily").remove("snowfall_sum")
        val day=WeeklyWeatherRepository({data},{time}).fetch(0.0,0.0).days.first()
        assertNull(day.weatherCode)
        assertNull(day.precipitationMm)
        assertNull(day.maxPrecipitationProbabilityPercent)
        assertNull(day.snowfallCm)
    }

    @Test fun invalidLocationDoesNotCallNetwork() = runBlocking {
        var called=false
        val repository=WeeklyWeatherRepository({called=true;response()},{time})
        assertTrue(runCatching {repository.fetch(Double.NaN,127.0)}.isFailure)
        assertFalse(called)
    }

    @Test fun shiftedDatesAndMisalignedArraysAreRejected() = runBlocking {
        val shifted=response()
        shifted.getJSONObject("daily").getJSONArray("time").put(0,"2026-11-10")
        assertTrue(runCatching { WeeklyWeatherRepository({shifted},{time}).fetch(37.5,127.0) }.isFailure)
        val short=response()
        short.getJSONObject("daily").put("weather_code",JSONArray(listOf(0,0)))
        assertTrue(runCatching { WeeklyWeatherRepository({short},{time}).fetch(37.5,127.0) }.isFailure)
    }

    @Test fun networkFailureIsNotReplacedByExampleWeather() = runBlocking {
        assertTrue(runCatching { WeeklyWeatherRepository({throw java.io.IOException()},{time}).fetch(37.5,127.0) }.isFailure)
    }
}
