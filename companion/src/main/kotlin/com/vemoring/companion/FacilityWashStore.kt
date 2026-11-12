package com.vemoring.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small bounded visit journal. Phone and vehicle share the format, not storage or timestamps. IO only. */
class FacilityWashStore(context:Context, private val vehicleId:String) {
    private val prefs=context.getSharedPreferences("facility_wash_visits_v1",Context.MODE_PRIVATE)
    private val key="car:"+vehicleId
    companion object { private val lock=Any() }
    private fun load():List<FacilityVisit> {
        val all=prefs.getString(key,null)?.let {decode(JSONArray(it))}.orEmpty()
        val now=System.currentTimeMillis()
        val live=all.filter {now-it.fixes.first().at in 0..FacilityWashPolicy.LIFETIME_MS}
        if(live.size!=all.size) save(live) // Prune raw visit evidence on access; never remove care records.
        return live
    }
    private fun save(visits:List<FacilityVisit>) {
        check(prefs.edit().putString(key,encode(visits).toString()).commit())
    }
    fun observeVehicle(fix:FacilityFix, sites:List<WashSite>):Boolean = synchronized(lock) {
        if(!fix.valid() || fix.phone) return@synchronized false
        val visits=load().filter {fix.receivedAt-it.fixes.first().at<=FacilityWashPolicy.LIFETIME_MS}.toMutableList()
        val active=visits.lastOrNull()?.takeUnless {FacilityWashPolicy.evaluate(it).exited}
        if(active!=null) {
            val last=active.fixes.lastOrNull { !it.phone }
            if(last!=null && fix.at-last.at<5_000) return@synchronized false
            visits[visits.lastIndex]=active.copy(fixes=(active.fixes+fix).take(FacilityWashPolicy.MAX_FIXES))
        } else {
            val site=FacilityWashPolicy.candidate(sites,fix) ?: return@synchronized false
            visits+=FacilityVisit("radius:$vehicleId:${site.id}:${fix.at}",site,listOf(fix))
        }
        save(visits.takeLast(4));true
    }
    fun frame():JSONArray = synchronized(lock) {encode(load())}
    fun clear() = synchronized(lock) {check(prefs.edit().remove(key).commit())}
    /** Merge late phone evidence with vehicle observations by measurement time, including after reconnect. */
    fun importVehicle(frame:JSONArray, now:Long):List<DetectedWash> = synchronized(lock) {
        val local=load().associateBy {it.id}
        val visits=decode(frame).filter {it.fixes.isNotEmpty() && now-it.fixes.first().at in 0..FacilityWashPolicy.LIFETIME_MS}
            .map {incoming ->
                val old=local[incoming.id]?.takeIf {it.site==incoming.site}
                val phone=old?.fixes.orEmpty().filter {it.phone}
                incoming.copy(fixes=(incoming.fixes.filter {!it.phone}+phone).distinctBy {it.at to it.phone}
                    .sortedBy {it.at}.take(FacilityWashPolicy.MAX_FIXES))
            }
        save(visits)
        visits.mapNotNull {FacilityWashPolicy.evaluate(it).wash}
    }
    fun phoneCandidate(now:Long):FacilityVisit? = synchronized(lock) {
        load().lastOrNull()?.takeIf {now-it.fixes.first().at in 0..FacilityWashPolicy.LIFETIME_MS && !FacilityWashPolicy.evaluate(it).exited}
    }
    fun observePhone(visitId:String, fix:FacilityFix):Boolean = synchronized(lock) {
        if(!fix.phone || !fix.valid()) return@synchronized false
        val visits=load().toMutableList();val i=visits.indexOfFirst {it.id==visitId}
        if(i<0) return@synchronized false
        val v=visits[i];val vehicle=v.fixes.lastOrNull {!it.phone} ?: return@synchronized false
        if(fix.at-vehicle.at<20_000 || fix.at-v.fixes.first().at !in 0..FacilityWashPolicy.LIFETIME_MS ||
            v.fixes.lastOrNull {it.phone}?.let {fix.at-it.at<5_000}==true) return@synchronized false
        visits[i]=v.copy(fixes=(v.fixes+fix).sortedBy {it.at}.take(FacilityWashPolicy.MAX_FIXES));save(visits);true
    }
    private fun encode(visits:List<FacilityVisit>)=JSONArray().also {a -> visits.forEach {v ->
        a.put(JSONObject().put("id",v.id).put("site",JSONObject().put("id",v.site.id).put("lat",v.site.latitude).put("lon",v.site.longitude))
            .put("fixes",JSONArray().also {fs -> v.fixes.forEach {f ->fs.put(JSONObject().put("lat",f.latitude).put("lon",f.longitude)
                .put("at",f.at).put("receivedAt",f.receivedAt).put("phone",f.phone).put("accuracy",f.accuracyM))}}))
    }}
    private fun decode(a:JSONArray):List<FacilityVisit> {
        require(a.length()<=4)
        return (0 until a.length()).mapNotNull {i -> runCatching {
            val v=a.getJSONObject(i);val s=v.getJSONObject("site");val fixes=v.getJSONArray("fixes")
            require(fixes.length() in 1..FacilityWashPolicy.MAX_FIXES)
            val id=v.getString("id");require(id.startsWith("radius:$vehicleId:") && id.length<=256)
            FacilityVisit(id,WashSite(s.getString("id"),s.getDouble("lat"),s.getDouble("lon")),(0 until fixes.length()).map {n ->
                val f=fixes.getJSONObject(n);FacilityFix(f.getDouble("lat"),f.getDouble("lon"),f.getLong("at"),f.getLong("receivedAt"),
                    f.optBoolean("phone"),if(f.has("accuracy")) f.getDouble("accuracy") else null)
            }.filter {it.valid()}.also {require(it.isNotEmpty())})
        }.getOrNull()}
    }
}
