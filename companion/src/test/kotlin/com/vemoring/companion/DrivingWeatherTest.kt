package com.vemoring.companion

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DrivingWeatherTest {
    private val hour=Instant.parse("2026-11-10T05:00:00Z") // Korea 14:00
    private val start=hour.plusSeconds(600)
    private fun point(t:Instant)=TravelPoint(37.5665,126.978,t)
    private fun trip()=TravelInterval("trip",start,start.plusSeconds(120),1.0,located=true,point=point(start.plusSeconds(120)))
    private fun response(air:Boolean=false,rain:Any=0,snow:Any=0)=JSONObject().put("utc_offset_seconds",0).put("hourly",JSONObject()
        .put("time",JSONArray(listOf(hour.epochSecond,hour.plusSeconds(3600).epochSecond)))
        .also {h ->
            if(air) h.put("pm10",JSONArray(listOf(160,0))).put("pm2_5",JSONArray(listOf(80,0)))
            else h.put("rain",JSONArray(listOf(0,rain))).put("showers",JSONArray(listOf(0,0))).put("snowfall",JSONArray(listOf(0,snow)))
        })
    private fun score(t:TravelInterval):Double = WashPolicy.score(listOf(WashBaseline("wash",LocalDate.of(2026,11,10),completedAt=t.start)),listOf(t),t.end).value!!

    @Test fun equalMileageDoesNotBecomeParkingAndFinalIncreaseReplacesSameInterval() {
        var a=DistanceAccumulator.observe(null,10000.0,start,"session",start,point(start)).anchor
        val same=DistanceAccumulator.observe(a,10000.0,start.plusSeconds(30),"session",start.plusSeconds(30),point(start.plusSeconds(30)))
        assertFalse(same.interval!!.stationary)
        assertEquals(start,same.anchor!!.at)
        a=DistanceAnchor.from(same.anchor.json())
        val increase=DistanceAccumulator.observe(a,10001.0,start.plusSeconds(120),"session",start.plusSeconds(120),point(start.plusSeconds(120)))
        assertEquals(same.interval.id,increase.interval!!.id)
        assertEquals(1.0,increase.interval.km,0.0)
        assertTrue(increase.interval.located)
        assertEquals(99.9,score(increase.interval),.000001)
    }

    @Test fun duplicateOldSampleAndDisconnectNeverExtendObservedCoverage() {
        val a=DistanceAccumulator.observe(null,10.0,start,"s",start,point(start)).anchor
        assertNull(DistanceAccumulator.observe(a,10.0,start,"s",start.plusSeconds(1),point(start)).interval)
        assertEquals(a,DistanceAccumulator.observe(a,10.0,start,"s",start.plusSeconds(60),point(start)).anchor)
        assertNull(DistanceAccumulator.observe(a,null,null,"s",start.plusSeconds(16),null).anchor)
        assertNull(DistanceAccumulator.observe(a,11.0,start.plusSeconds(1000),"s",start.plusSeconds(1000),point(start.plusSeconds(1000))).interval)
        assertNull(DistanceAccumulator.observe(a,11.0,start.plusSeconds(120),"new",start.plusSeconds(120),point(start.plusSeconds(120))).interval)
    }

    @Test fun missingStaleLongAndCrossHourLocationsKeepBasicDistanceOnly() {
        val end=start.plusSeconds(120)
        assertNull(DrivingWeatherPolicy.representative(start,end,null,point(end)))
        assertNull(DrivingWeatherPolicy.representative(start,end,point(start.minusSeconds(61)),point(end)))
        assertNull(DrivingWeatherPolicy.representative(start,start.plusSeconds(301),point(start),point(start.plusSeconds(301))))
        val s=hour.plusSeconds(3540);val e=hour.plusSeconds(3660)
        assertNull(DrivingWeatherPolicy.representative(s,e,point(s),point(e)))
        assertNull(DrivingWeatherPolicy.representative(start,end,point(start),TravelPoint(38.0,127.0,end)))
    }

    @Test fun rainEndHourAndPmStartHourProduceExpectedExtraLoss() = runBlocking {
        var calls=0
        val now=hour.plusSeconds(3600)
        val repo=HourlyWeatherRepository({url -> calls++; response(url.contains("air-quality"),rain=1)},{now})
        val t=trip();val weather=repo.fetch(t)!!
        assertEquals(hour,weather.hourStart)
        assertEquals(1.0,weather.rainMm,0.0)
        assertEquals(160.0,weather.pm10!!,0.0)
        // Full hour has not elapsed at t.end: calculate later, without inventing future obtainedAt.
        val result=WashPolicy.score(listOf(WashBaseline("w",LocalDate.of(2026,11,10),completedAt=start)),
            listOf(t.copy(weather=weather),TravelInterval("cover",t.end,now,0.0)),now)
        assertEquals(99.7,result.value!!,.000001)
        assertEquals(weather,repo.fetch(t))
        assertEquals(2,calls)
    }

    @Test fun snowWinsAndDryDustHasDailyCap() = runBlocking {
        val now=hour.plusSeconds(3600)
        val t=trip()
        val snow=HourlyWeatherRepository({u -> response(u.contains("air-quality"),rain=1,snow=1)},{now}).fetch(t)!!
        val baseline=listOf(WashBaseline("w",LocalDate.of(2026,11,10),completedAt=start))
        fun result(part:TravelInterval)=WashPolicy.score(baseline,listOf(part,TravelInterval("cover",part.end,now,0.0)),now)
        assertEquals(99.5,result(t.copy(weather=snow)).value!!,.000001)
        val dry=HourlyWeatherRepository({u -> response(u.contains("air-quality"))},{now}).fetch(t)!!
        val dusty=result(t.copy(km=100.0,weather=dry))
        assertEquals(2.0,dusty.dustLoss,0.0)
        assertEquals(88.0,dusty.value!!,.000001)
    }

    @Test fun unfinishedHourExpiredHistoryAndNetworkFailureDoNotInventWeather() = runBlocking {
        var calls=0
        val repo=HourlyWeatherRepository({calls++;response()},{trip().end})
        assertNull(repo.fetch(trip()));assertEquals(0,calls)
        val expired=HourlyWeatherRepository({calls++;response()},{trip().end.plusSeconds(72*3600+1)})
        assertNull(expired.fetch(trip()));assertEquals(0,calls)
        assertNull(HourlyWeatherRepository({throw java.io.IOException()},{hour.plusSeconds(3600)}).fetch(trip()))
    }

    @Test fun missingPrecipitationDoesNotMasqueradeAsDryDustWeather() = runBlocking {
        val now=hour.plusSeconds(3600)
        assertNull(HourlyWeatherRepository({u -> if(u.contains("air-quality")) response(true) else response(rain=JSONObject.NULL)},{now}).fetch(trip()))
        val wet=HourlyWeatherRepository({u -> if(u.contains("air-quality")) throw java.io.IOException() else response(rain=1)},{now}).fetch(trip())!!
        assertEquals(1.0,wet.rainMm,0.0);assertNull(wet.pm10)
        val lightSnow=HourWeather(hour,0.0,0.0,.01,200.0,100.0,trip().end,"test")
        assertEquals(99.9,score(trip().copy(weather=lightSnow)),.000001)
    }

    @Test fun livePastHourApiWhenEnabled() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("VEMORING_WEATHER_LIVE")=="1")
        val s=DrivingWeatherPolicy.hourStart(Instant.now()).minusSeconds(3600).plusSeconds(600)
        val t=TravelInterval("public-demo",s,s.plusSeconds(120),1.0,located=true,point=point(s.plusSeconds(120)))
        val w=HourlyWeatherRepository().fetch(t)
        assertNotNull(w);assertTrue(w!!.precipitationKnown)
        assertEquals(DrivingWeatherPolicy.hourStart(s),w.hourStart)
    }
}
