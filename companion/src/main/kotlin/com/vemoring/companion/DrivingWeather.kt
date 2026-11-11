package com.vemoring.companion

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class TravelPoint(val latitude: Double, val longitude: Double, val at: Instant) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
    fun fresh(at: Instant) = Duration.between(this.at, at).toMillis() in 0..60000
    fun json() = JSONObject().put("lat",latitude).put("lon",longitude).put("at",at.toString())
    companion object {
        fun from(j: JSONObject) = TravelPoint(j.getDouble("lat"),j.getDouble("lon"),Instant.parse(j.getString("at")))
    }
}

object DrivingWeatherPolicy {
    fun hourStart(t: Instant): Instant = t.truncatedTo(ChronoUnit.HOURS)
    fun representative(start: Instant,end: Instant,a: TravelPoint?,b: TravelPoint?): TravelPoint? {
        if (a==null || b==null || !a.fresh(start) || !b.fresh(end) || end<=start ||
            Duration.between(start,end).toMillis()>300000 || end>hourStart(start).plusSeconds(3600)) return null
        val lat1=Math.toRadians(a.latitude); val lat2=Math.toRadians(b.latitude)
        val x=sin((lat2-lat1)/2).pow(2)+cos(lat1)*cos(lat2)*sin(Math.toRadians(b.longitude-a.longitude)/2).pow(2)
        val metres=6371000*2*atan2(sqrt(x.coerceIn(0.0,1.0)),sqrt((1-x).coerceIn(0.0,1.0)))
        val broadMotionBound=Duration.between(start,end).toMillis()/3600000.0*300000+100
        return b.takeIf {metres<=5000 && metres<=broadMotionBound}
    }
    fun pending(t: TravelInterval,now: Instant) = t.source=="Real" && t.km>0 && t.located && t.point!=null &&
        t.weather==null && t.end<=now && now<=t.end.plusSeconds(72*3600L) &&
        Duration.between(t.start,t.end).toMillis()<=300000 && t.point.fresh(t.end) &&
        t.end<=hourStart(t.start).plusSeconds(3600)
    fun readyAt(t: TravelInterval) = hourStart(t.start).plusSeconds(3600)
}

/** Pure accumulation state. Equal SDK responses cover time but are not stationary evidence. */
data class DistanceAnchor(val km: Double,val at: Instant,val lastAt: Instant,val session: String,val point: TravelPoint?) {
    fun json() = JSONObject().put("km",km).put("at",at.toEpochMilli()).put("lastAt",lastAt.toEpochMilli())
        .put("session",session).put("point",point?.json())
    companion object {
        fun from(j: JSONObject) = DistanceAnchor(j.getDouble("km"),Instant.ofEpochMilli(j.getLong("at")),
            Instant.ofEpochMilli(j.optLong("lastAt",j.getLong("at"))),j.getString("session"),j.optJSONObject("point")?.let(TravelPoint::from))
    }
}
data class DistanceStep(val anchor: DistanceAnchor?,val interval: TravelInterval?)
object DistanceAccumulator {
    fun observe(old: DistanceAnchor?,km: Double?,at: Instant?,session: String,now: Instant,point: TravelPoint?): DistanceStep {
        // Another signal can republish a cached odometer reading. It neither breaks nor extends SDK coverage.
        if(km!=null && km.isFinite() && km>=0 && at!=null && old?.session==session && at<=old.lastAt)
            return DistanceStep(old,null)
        if(km==null || at==null || !km.isFinite() || km<0 || Duration.between(at,now).toMillis() !in 0..15000)
            return DistanceStep(null,null)
        val p=point?.takeIf {it.fresh(at)}
        val next=DistanceAnchor(km,at,at,session,p)
        if(old==null || old.session!=session) return DistanceStep(next,null)
        if(at<=old.lastAt) return DistanceStep(old,null)
        val elapsed=Duration.between(old.at,at).toMillis()
        val delta=km-old.km
        if(Duration.between(old.lastAt,at).toMillis()>900000 || delta<0 || delta>elapsed/3600000.0*300)
            return DistanceStep(next,null)
        val location=if(delta>0) DrivingWeatherPolicy.representative(old.at,at,old.point,p) else null
        val interval=TravelInterval("$session:${old.at.toEpochMilli()}",old.at,at,delta,
            stationary=false,located=location!=null,point=location)
        return DistanceStep(if(delta>0) next else old.copy(lastAt=at),interval)
    }
}
