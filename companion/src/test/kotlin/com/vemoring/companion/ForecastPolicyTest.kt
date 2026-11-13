package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ForecastPolicyTest {
    private val now=Instant.parse("2026-11-12T03:00:00Z")
    private val today=LocalDate.of(2026,11,12)
    private val region=ForecastRegion("서울",37.56,126.97)
    private fun forecast()=WeeklyWeatherForecast(region.latitude,region.longitude,now,
        List(7) {DailyWeatherForecast(today.plusDays(it.toLong()),0,5.0,20.0,20.0,0.0,0.0)})

    @Test fun currentLocationRejectsStaleFixAndSharesNearbyRegionalForecast() {
        val point=TravelPoint(37.5665,126.9780,now)
        val current=ForecastLocationPolicy.region(point,now)!!
        assertEquals("현재 위치",current.name)
        assertEquals(current,ForecastLocationPolicy.region(point.copy(latitude=37.5666),now))
        assertNotEquals(current,ForecastLocationPolicy.region(point.copy(latitude=35.18),now))
        assertNull(ForecastLocationPolicy.region(point,now.plusSeconds(61)))
        assertNull(ForecastLocationPolicy.region(point,now.minusSeconds(1)))
    }

    @Test fun approximatePhoneWeatherAllowsTenMinuteFixWithoutRelaxingVehicleEvidence() {
        val point=TravelPoint(37.5665,126.9780,now.minusSeconds(120))
        assertNotNull(ForecastLocationPolicy.phoneRegion(point,now))
        assertNull(ForecastLocationPolicy.region(point,now))
        assertFalse(point.fresh(now))
        assertEquals(now.minusSeconds(120),point.at)
        assertNotNull(ForecastLocationPolicy.phoneRegion(point.copy(at=now.minusSeconds(600)),now))
        assertNull(ForecastLocationPolicy.phoneRegion(point.copy(at=now.minusSeconds(600).minusMillis(1)),now))
        assertNull(ForecastLocationPolicy.phoneRegion(point.copy(at=now.plusMillis(1)),now))
    }

    @Test fun recommendsEarliestConsecutiveDryPairWithoutUsingLastDayAlone() {
        assertEquals(today,WashDatePolicy.suggest(forecast(),now).date)
        val wet=forecast().copy(days=forecast().days.mapIndexed {i,d -> if(i < 2) d.copy(weatherCode=61,precipitationMm=1.0) else d})
        assertEquals(today.plusDays(2),WashDatePolicy.suggest(wet,now).date)
        val lastOnly=forecast().copy(days=forecast().days.mapIndexed {i,d -> if(i < 6) d.copy(snowfallCm=.1) else d})
        assertNull(WashDatePolicy.suggest(lastOnly,now).date)
    }
    @Test fun exactBoundariesMissingDataAndExtremeTemperaturesDoNotBecomeDryAdvice() {
        val original=forecast()
        val cases=listOf<(DailyWeatherForecast)->DailyWeatherForecast>(
            {it.copy(maxPrecipitationProbabilityPercent=30.1)}, {it.copy(precipitationMm=.5)},
            {it.copy(snowfallCm=.01)}, {it.copy(minTemperatureC=0.0)}, {it.copy(maxTemperatureC=35.0)},
            {it.copy(precipitationMm=null)}, {it.copy(maxPrecipitationProbabilityPercent=null)},
            {it.copy(weatherCode=null)}, {it.copy(weatherCode=95)})
        cases.forEach {change -> assertNull(WashDatePolicy.suggest(original.copy(days=original.days.map(change)),now).date)}
        val boundary=original.copy(days=original.days.map {it.copy(maxPrecipitationProbabilityPercent=30.0,precipitationMm=.49)})
        assertEquals(today,WashDatePolicy.suggest(boundary,now).date)
    }
    @Test fun expiredFutureAndPreviousKoreanDateForecastsNeverRecommend() {
        assertNull(WashDatePolicy.suggest(forecast().copy(fetchedAt=now.minusSeconds(21601)),now).date)
        assertNull(WashDatePolicy.suggest(forecast().copy(fetchedAt=now.plusSeconds(1)),now).date)
        assertNull(WashDatePolicy.suggest(forecast(),now.plusSeconds(24*3600)).date)
    }
    @Test fun hourlyExpiryLocationChangeAndKoreanMidnightTriggerRefresh() {
        assertFalse(ForecastRefreshPolicy.due(forecast(),region,now.plusSeconds(3599)))
        assertTrue(ForecastRefreshPolicy.due(forecast(),region,now.plusSeconds(3600)))
        assertTrue(ForecastRefreshPolicy.due(forecast(),region.copy(latitude=35.0),now))
        val late=Instant.parse("2026-11-12T14:59:00Z")
        val value=forecast().copy(fetchedAt=late)
        assertTrue(ForecastRefreshPolicy.due(value,region,late.plusSeconds(61)))
        assertEquals(60000L,ForecastRefreshPolicy.waitMillis(value,late))
    }
    @Test fun cacheRoundTripPreservesNullZeroAndNegativeTemperature() {
        val value=forecast().copy(days=forecast().days.map {it.copy(weatherCode=null,minTemperatureC=-2.0,maxTemperatureC=0.0,snowfallCm=null)})
        assertEquals(value,ForecastCodec.decode(ForecastCodec.encode(value)))
        assertNull(WashDatePolicy.suggest(ForecastCodec.decode(ForecastCodec.encode(value)),now).date)
        assertEquals(WeatherSymbol.UNKNOWN,value.days.first().forDisplay().symbol)
    }
}
