package com.example.vemoring.phone

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Storage and companion payload simulation only: no Pleos SDK / Bluetooth radio assertion. */
class DrivingWeatherStorageTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated():Context {
        val prefix="weather_test_${UUID.randomUUID()}_"
        return object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences(prefix+name,mode)
        }
    }
    private val hour=Instant.parse("2026-11-10T05:00:00Z")
    private val start=hour.plusSeconds(600)
    private fun p(t:Instant)=TravelPoint(37.5665,126.978,t)

    @Test fun zeroMileageReplacementWeatherReplyAndReplayAreIdempotent() {
        val id=UUID.randomUUID().toString()
        val car=CareJournal(isolated(),id);val phone=CareJournal(isolated(),id)
        car.observeDistance(10000.0,start.toEpochMilli(),"s",start.toEpochMilli(),p(start))
        car.observeDistance(10000.0,start.plusSeconds(30).toEpochMilli(),"s",start.plusSeconds(30).toEpochMilli(),p(start.plusSeconds(30)))
        val provisional=JSONArray(car.intervalJson().toString())
        phone.importIntervals(provisional)
        val end=start.plusSeconds(120)
        car.observeDistance(10001.0,end.toEpochMilli(),"s",end.toEpochMilli(),p(end))
        val raw=JSONArray(car.intervalJson().toString())
        phone.importIntervals(raw);phone.importIntervals(provisional)
        assertEquals(1,phone.intervals().size)
        val trip=phone.intervals().single()
        assertEquals(1.0,trip.km,0.0);assertFalse(trip.stationary);assertNotNull(trip.point)
        val obtained=hour.plusSeconds(3600)
        val wet=HourWeather(hour,1.0,0.0,0.0,200.0,90.0,obtained,"test")
        assertTrue(phone.attachWeather(trip.id,wet));assertFalse(phone.attachWeather(trip.id,wet))
        phone.importIntervals(raw) // raw replay must never erase the enriched result
        assertEquals(wet,phone.intervals().single().weather)
        car.importWeatherReply(phone.weatherReply())
        assertEquals(wet,car.intervals().single().weather)
        phone.importIntervals(car.outgoingIntervals()) // receipt of the exact weather ends reply retransmission
        assertEquals(0,phone.weatherReply().length())
        val baseline=listOf(WashBaseline("wash",LocalDate.of(2026,11,10),completedAt=start))
        val cover=TravelInterval("observed",end,obtained,0.0)
        assertEquals(99.7,WashPolicy.score(baseline,phone.intervals()+cover,obtained).value!!,.000001)
        assertEquals(WashPolicy.score(baseline,phone.intervals()+cover,obtained),WashPolicy.score(baseline,car.intervals()+cover,obtained))
        assertNull(WashPolicy.score(baseline.map {it.copy(cancelled=true)},phone.intervals(),obtained).value)
    }

    @Test fun weatherReplyBacklogAdvancesBeyond128AndOtherCarsStaySeparate() {
        val id=UUID.randomUUID().toString();val context=isolated();val phone=CareJournal(context,id)
        val trips=(0..129).map {n -> val s=start.plusSeconds(n*120L)
            TravelInterval("trip-$n",s,s.plusSeconds(60),1.0,located=true,point=p(s.plusSeconds(60))) }
        phone.mergeIntervals(trips)
        trips.forEach { t ->
            if(t.end<=DrivingWeatherPolicy.readyAt(t))
                assertTrue(phone.attachWeather(t.id,HourWeather(DrivingWeatherPolicy.hourStart(t.start),1.0,0.0,0.0,null,null,
                    DrivingWeatherPolicy.readyAt(t),"test")))
        }
        val first=phone.weatherReply();assertEquals(128,first.length())
        phone.importIntervals(first)
        assertEquals(2,phone.weatherReply().length())
        assertTrue(CareJournal(context,"different-car").intervals().isEmpty())
    }
}
