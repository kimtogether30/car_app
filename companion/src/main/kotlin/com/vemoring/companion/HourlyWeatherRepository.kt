package com.vemoring.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration

/** Finished hours only. Forecast-model history; never claims observed rain on a vehicle. */
class HourlyWeatherRepository(
    private val request: (String) -> JSONObject = ::requestWeatherJson,
    private val now: () -> Instant = Instant::now
) {
    private val cache=object : LinkedHashMap<String,HourWeather>(64,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,HourWeather>?) = size>64
    }
    suspend fun fetch(t: TravelInterval): HourWeather? = withContext(Dispatchers.IO) {
        val at=now()
        if(!DrivingWeatherPolicy.pending(t,at) || at<DrivingWeatherPolicy.readyAt(t)) return@withContext null
        val p=requireNotNull(t.point); val start=DrivingWeatherPolicy.hourStart(t.start)
        // About 1km cells: reuse regional model data instead of issuing one request per odometer tick.
        val latitude=(kotlin.math.floor(p.latitude*100)+.5)/100
        val longitude=(kotlin.math.floor(p.longitude*100)+.5)/100
        val key="$latitude:$longitude:$start"
        synchronized(cache) {cache[key]}?.takeIf {it.obtainedAt>=t.end && it.obtainedAt<=at}?.let {return@withContext it}
        val end=start.plusSeconds(3600)
        val date=start.atZone(ZoneOffset.UTC).toLocalDate()
        val endDate=end.atZone(ZoneOffset.UTC).toLocalDate()
        val query="latitude=$latitude&longitude=$longitude&timezone=GMT&timeformat=unixtime&start_date=$date&end_date=$endDate"
        fun safe(url:String):JSONObject? = try {request(url)} catch(e:CancellationException) {throw e} catch(_:Exception) {null}
        val weather=safe("https://api.open-meteo.com/v1/forecast?$query&hourly=rain,showers,snowfall")
        val air=safe("https://air-quality-api.open-meteo.com/v1/air-quality?$query&hourly=pm10,pm2_5")
        fun value(j:JSONObject?,key:String,time:Instant):Double? {
            if(j==null || j.optBoolean("error") || j.optInt("utc_offset_seconds",-1)!=0) return null
            val hourly=j.optJSONObject("hourly") ?: return null
            val times=hourly.optJSONArray("time") ?: return null
            val values=hourly.optJSONArray(key) ?: return null
            if(values.length()!=times.length()) return null
            for(i in 0 until times.length()) if((times.opt(i) as? Number)?.toLong()==time.epochSecond)
                return (values.opt(i) as? Number)?.toDouble()?.takeIf {it.isFinite() && it>=0}
            return null
        }
        // Rain at 15:00 covers 14:00..15:00. PM at 14:00 represents the next hour by product policy.
        val rain=value(weather,"rain",end); val showers=value(weather,"showers",end); val snow=value(weather,"snowfall",end)
        val pm10=value(air,"pm10",start); val pm25=value(air,"pm2_5",start)
        val known=rain!=null && showers!=null && snow!=null
        if(!known && (rain ?: 0.0)+(showers ?: 0.0)<.1 && (snow ?: 0.0)<.1) return@withContext null
        val obtained=now()
        if(obtained<t.end || Duration.between(t.end,obtained).seconds>72*3600L) return@withContext null
        HourWeather(start,rain ?: 0.0,showers ?: 0.0,snow ?: 0.0,pm10,pm25,obtained,
            "open-meteo-hourly-v1",known).also {synchronized(cache) {cache[key]=it}}
    }
}
