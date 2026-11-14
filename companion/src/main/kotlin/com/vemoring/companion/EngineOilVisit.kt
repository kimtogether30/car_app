package com.vemoring.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Explicit vehicle power only; no RPM/gear/Bluetooth substitute. A supported adapter is required. */
enum class VerifiedVehiclePower { OFF, ON }
data class EngineOilVisit(val vehicleId:String,val site:WashSite,val off:FacilityFix,val on:FacilityFix?=null,
    val exit:FacilityFix?=null,val stop:FacilityFix?=null,val odometerKm:Double?=null) {
    val id get()="oil:$vehicleId:${site.id}:${off.at}"
    fun json():JSONObject=JSONObject().put("policy",1).put("powerSource","AAOS_IGNITION_STATE").put("vehicleId",vehicleId).put("id",id)
        .put("site",JSONObject().put("id",site.id).put("lat",site.latitude).put("lon",site.longitude)
            .put("boundary",JSONArray().also {a -> site.boundary.forEach {a.put(JSONArray().put(it.latitude).put(it.longitude))}}))
        .put("off",fixJson(off)).put("on",on?.let(::fixJson)).put("exit",exit?.let(::fixJson))
        .put("stop",stop?.let(::fixJson)).put("km",odometerKm)
    companion object {
        private fun fixJson(f:FacilityFix)=JSONObject().put("lat",f.latitude).put("lon",f.longitude).put("at",f.at)
            .put("received",f.receivedAt).put("phone",f.phone).put("accuracy",f.accuracyM)
            .put("speed",f.speedMps).put("speedAt",f.speedAt).put("gear",f.gear).put("gearAt",f.gearAt)
        private fun fix(j:JSONObject)=FacilityFix(j.getDouble("lat"),j.getDouble("lon"),j.getLong("at"),j.getLong("received"),
            j.optBoolean("phone"),j.optDouble("accuracy",Double.NaN).takeIf {it.isFinite()},
            j.optDouble("speed",Double.NaN).takeIf {it.isFinite()},j.optLong("speedAt").takeIf {it>0},
            j.optString("gear").takeIf {it.isNotBlank()},j.optLong("gearAt").takeIf {it>0})
        fun from(j:JSONObject):EngineOilVisit {
            require(j.getInt("policy")==1 && j.getString("powerSource")=="AAOS_IGNITION_STATE")
            val s=j.getJSONObject("site");val b=s.getJSONArray("boundary");require(b.length() in 4..256)
            val site=WashSite(s.getString("id"),s.getDouble("lat"),s.getDouble("lon"),(0 until b.length()).map {
                b.getJSONArray(it).let {p -> WashPoint(p.getDouble(0),p.getDouble(1))}})
            return EngineOilVisit(j.getString("vehicleId"),site,fix(j.getJSONObject("off")),j.optJSONObject("on")?.let(::fix),
                j.optJSONObject("exit")?.let(::fix),j.optJSONObject("stop")?.let(::fix),
                j.optDouble("km",Double.NaN).takeIf {it.isFinite() && it>=0}).also {require(it.id==j.getString("id"))}
        }
    }
}

object EngineOilVisitPolicy {
    const val DWELL_MS=30*60_000L
    const val MAX_CYCLE_MS=24*60*60_000L // Bound an unfinished power cycle; not a claimed SDK guarantee.
    const val LOCATION_ACCURACY_M=30.0 // Conservative trial, requires actual accuracy; never invent it.
    fun usable(f:FacilityFix)=!f.phone && f.valid() && f.accuracyM?.let {it<=LOCATION_ACCURACY_M}==true
    fun complete(v:EngineOilVisit,now:Long):Boolean {
        val on=v.on ?: return false;val exit=v.exit ?: return false;val stop=v.stop ?: return false
        if(listOf(v.off,on,exit,stop).any {!usable(it)} || !v.site.mappedArea()) return false
        return on.at-v.off.at in DWELL_MS..MAX_CYCLE_MS && exit.at>on.at && stop.at>exit.at &&
            stop.at-v.off.at<=MAX_CYCLE_MS && now-stop.receivedAt in 0..60_000 &&
            FacilityWashPolicy.zone(v.site,v.off)==ZoneEvidence.Inside && FacilityWashPolicy.zone(v.site,on)==ZoneEvidence.Inside &&
            FacilityWashPolicy.zone(v.site,exit)==ZoneEvidence.Outside && FacilityWashPolicy.zone(v.site,stop)==ZoneEvidence.Outside &&
            exit.liveSpeed()?.let {it>0}==true && exit.liveGear()=="D" && stop.liveSpeed()==0.0 && stop.liveGear()!=null &&
            (v.odometerKm==null || v.odometerKm.isFinite() && v.odometerKm>=0)
    }
}

/** Persists OFF/ON/exit/stop across object/process recreation. Caller uses IO and a real receiving vehicle ID. */
class EngineOilVisitStore(context:Context,private val vehicleId:String) {
    private val prefs=context.getSharedPreferences("engine_oil_visits_v1",Context.MODE_PRIVATE)
    private fun read()=prefs.getString(vehicleId,null)?.let(::JSONObject) ?: JSONObject()
    private fun save(j:JSONObject) {check(prefs.edit().putString(vehicleId,j.toString()).commit())}
    fun power(power:VerifiedVehiclePower,fix:FacilityFix,sites:List<WashSite>)=synchronized(LOCK) {
        if(!EngineOilVisitPolicy.usable(fix)) return@synchronized
        val site=sites.filter {FacilityWashPolicy.zone(it,fix)==ZoneEvidence.Inside}.singleOrNull() ?: return@synchronized
        val j=read();val previous=j.optJSONObject("active")?.let(EngineOilVisit::from)
        when(power) {
            VerifiedVehiclePower.OFF -> {
                if(previous!=null && previous.on==null && previous.site.id==site.id && fix.at-previous.off.at in 0..EngineOilVisitPolicy.MAX_CYCLE_MS) return@synchronized
                j.put("active",EngineOilVisit(vehicleId,site,fix).json());save(j)
            }
            VerifiedVehiclePower.ON -> {
                val old=previous ?: return@synchronized
                if(old.on!=null) return@synchronized // Replayed ON cannot restart the visit.
                if(site!=old.site || fix.at-old.off.at !in EngineOilVisitPolicy.DWELL_MS..EngineOilVisitPolicy.MAX_CYCLE_MS) j.remove("active")
                else j.put("active",old.copy(on=fix).json())
                save(j)
            }
        }
    }
    fun observe(fix:FacilityFix,km:Double?):Boolean=synchronized(LOCK) {
        val j=read()
        // A delayed Bluetooth delivery needs a fresh vehicle stop, never the phone owner's walking position.
        val visits=j.optJSONArray("visits")
        var refreshed=false
        if(EngineOilVisitPolicy.usable(fix) && fix.liveSpeed()==0.0 && fix.liveGear()!=null && visits!=null) {
            repeat(visits.length()) {n -> val v=EngineOilVisit.from(visits.getJSONObject(n))
                if(v.exit!=null && fix.at>v.exit.at && fix.at-v.off.at<=EngineOilVisitPolicy.MAX_CYCLE_MS &&
                    FacilityWashPolicy.zone(v.site,fix)==ZoneEvidence.Outside) {visits.put(n,v.copy(stop=fix).json());refreshed=true}
            }
        }
        val old=j.optJSONObject("active")?.let(EngineOilVisit::from) ?: run {if(refreshed) save(j);return@synchronized refreshed}
        if(!EngineOilVisitPolicy.usable(fix) || old.on==null || fix.at<=old.on.at) return@synchronized false
        if(fix.at-old.off.at>EngineOilVisitPolicy.MAX_CYCLE_MS) {j.remove("active");save(j);return@synchronized false}
        if(FacilityWashPolicy.zone(old.site,fix)!=ZoneEvidence.Outside) return@synchronized false
        val next=when {
            old.exit==null && fix.liveSpeed()?.let {it>0}==true && fix.liveGear()=="D" -> old.copy(exit=fix,odometerKm=km?.takeIf {it.isFinite() && it>=0})
            old.exit!=null && fix.at>old.exit.at && fix.liveSpeed()==0.0 && fix.liveGear()!=null -> old.copy(stop=fix)
            else->return@synchronized false
        }
        j.put("active",next.json())
        if(next.stop!=null) {
            val done=j.optJSONArray("visits") ?: JSONArray()
            if((0 until done.length()).none {done.getJSONObject(it).getString("id")==next.id}) done.put(next.json())
            while(done.length()>64) done.remove(0)
            j.put("visits",done);j.remove("active")
        }
        save(j);true
    }
    fun frame():JSONArray=synchronized(LOCK) {read().optJSONArray("visits") ?: JSONArray()}
    fun clear()=synchronized(LOCK) {check(prefs.edit().remove(vehicleId).commit())}
    companion object {private val LOCK=Any()}
}
