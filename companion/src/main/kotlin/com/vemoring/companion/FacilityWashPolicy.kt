package com.vemoring.companion

import java.time.Instant
import kotlin.math.*

/** Radius-based estimates, including mixed fuel/wash sites. Never a verified wash completion. */
data class WashSite(val id: String, val latitude: Double, val longitude: Double)
data class FacilityFix(val latitude: Double, val longitude: Double, val at: Long, val receivedAt: Long,
    val phone: Boolean = false, val accuracyM: Double? = null) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 && at > 0 && receivedAt - at in 0..15_000 &&
        (!phone || accuracyM?.let { it.isFinite() && it in 0.0..50.0 } == true)
}
data class FacilityVisit(val id: String, val site: WashSite, val fixes: List<FacilityFix>)
data class FacilityVisitResult(val dwellMillis: Long, val exited: Boolean, val wash: DetectedWash?)

object FacilityWashPolicy {
    // Trial values, not SDK precision guarantees. Hysteresis avoids single-point boundary chatter.
    const val ENTER_M = 50.0
    const val EXIT_M = 100.0
    const val MAX_GAP_MS = 30_000L
    const val LIFETIME_MS = 2 * 60 * 60_000L
    const val MAX_FIXES = 1500
    fun distance(site: WashSite, fix: FacilityFix): Double {
        val dlat = Math.toRadians(fix.latitude-site.latitude)
        val dlon = Math.toRadians(fix.longitude-site.longitude)
        val a = sin(dlat/2).pow(2)+cos(Math.toRadians(site.latitude))*cos(Math.toRadians(fix.latitude))*sin(dlon/2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0,1.0)))
    }
    fun candidate(sites: List<WashSite>, fix: FacilityFix): WashSite? {
        if(!fix.valid() || fix.phone) return null
        val near=sites.filter {it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0}
            .map {it to distance(it,fix)}.filter {it.second<=ENTER_M}.sortedBy {it.second}
        // Overlapping facility centers cannot safely identify a visit.
        if(near.size>1 && near[1].second-near[0].second<20) return null
        return near.firstOrNull()?.first
    }
    fun evaluate(visit: FacilityVisit): FacilityVisitResult {
        val all=visit.fixes.filter {it.valid()}.sortedWith(compareBy<FacilityFix>{it.at}.thenBy {it.phone})
            .distinctBy {it.at}.take(MAX_FIXES)
        val first=all.firstOrNull { !it.phone && distance(visit.site,it)<=ENTER_M }
            ?: return FacilityVisitResult(0,false,null)
        val fixes=all.filter {it.at>=first.at && it.at-first.at<=LIFETIME_MS}
        var previous:FacilityFix?=null
        var entrySpan=0L
        var entered=false
        var dwell=0L
        var outside:Long?=null
        var lastVehicleOutside:Long?=null
        var lastVehicleAt=first.at
        var phoneUsed=false
        var vehicleReturnedAfterPhone=false
        for(f in fixes) {
            // Phone observations supplement an established vehicle visit, never start one.
            if(f.phone && f.at-lastVehicleAt<20_000) continue
            if(!f.phone) lastVehicleAt=f.at
            if(!f.phone && phoneUsed) vehicleReturnedAfterPhone=true
            val d=distance(visit.site,f)
            val p=previous
            val gap=if(p==null) 0 else f.at-p.at
            if(d<=ENTER_M) {
                outside=null;lastVehicleOutside=null
                if(p!=null && distance(visit.site,p)<=ENTER_M && gap in 1..MAX_GAP_MS) {
                    entrySpan+=gap
                    if(entrySpan>=10_000) entered=true
                    dwell+=gap
                    if(f.phone || p.phone) phoneUsed=true
                } else if(!entered) entrySpan=0
            } else if(d>=EXIT_M && !f.phone) {
                // Exit must be corroborated by vehicle fixes. Walking away with a phone cannot complete a wash.
                if(lastVehicleOutside==null || f.at-lastVehicleOutside!!>MAX_GAP_MS) outside=f.at
                lastVehicleOutside=f.at
                if(outside!=null && f.at-outside!!>=30_000) {
                    val wash=if(entered && dwell>=180_000 && (!phoneUsed || vehicleReturnedAfterPhone))
                        DetectedWash(visit.id,Instant.ofEpochMilli(outside!!),WashCompletionEvidence.EstimatedVisit) else null
                    return FacilityVisitResult(dwell,true,wash)
                }
            } else { outside=null;lastVehicleOutside=null }
            previous=f
        }
        return FacilityVisitResult(dwell,false,null)
    }
}
