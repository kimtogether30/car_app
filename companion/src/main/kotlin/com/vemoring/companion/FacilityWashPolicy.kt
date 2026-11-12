package com.vemoring.companion

import java.time.Instant
import kotlin.math.*

/** Explicit mapped wash area, never a radius around a fuel station or a POI. */
data class WashPoint(val latitude: Double, val longitude: Double) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
data class WashSite(val id: String, val latitude: Double, val longitude: Double,
    val boundary: List<WashPoint> = emptyList(), val facility: WashFacility = WashFacility.Unknown) {
    fun mappedArea() = boundary.size in 4..256 && boundary.first()==boundary.last() && boundary.all {it.valid()} &&
        boundary.distinct().size>=3
}
data class FacilityFix(val latitude: Double, val longitude: Double, val at: Long, val receivedAt: Long,
    val phone: Boolean = false, val accuracyM: Double? = null,
    val speedMps: Double? = null, val speedAt: Long? = null, val gear: String? = null, val gearAt: Long? = null) {
    fun valid() = WashPoint(latitude,longitude).valid() && at>0 && receivedAt-at in 0..15_000 &&
        (accuracyM==null || accuracyM.isFinite() && accuracyM in 0.0..50.0) &&
        (!phone || accuracyM!=null)
    fun liveSpeed(): Double? = speedMps?.takeIf { !phone && it.isFinite() && speedAt!=null &&
        receivedAt-speedAt in 0..5_000 && abs(at-speedAt)<=5_000 }
    fun liveGear(): String? = gear?.takeIf { !phone && gearAt!=null && receivedAt-gearAt in 0..35_000 && it in listOf("P","N","D","R") }
}
data class FacilityVisit(val id: String, val site: WashSite, val fixes: List<FacilityFix>)
data class FacilityVisitResult(val dwellMillis: Long, val exited: Boolean, val wash: DetectedWash?)

object FacilityWashPolicy {
    const val ENTER_M = 50.0 // Legacy radius is retained only for distance/call-site compatibility, not qualification.
    const val EXIT_M = 100.0
    const val MAX_GAP_MS = 15_000L
    const val LIFETIME_MS = 2 * 60 * 60_000L
    const val MAX_FIXES = 720 // Four visits plus speed/gear evidence remain within the existing 1MiB frame budget.
    const val DWELL_MS = 180_000L
    const val EDGE_MARGIN_M = 10.0 // Trial boundary buffer, not an invented SDK accuracy.
    const val RESUME_SAME_PLACE_M = 20.0 // Trial tolerance for stationary before/after a receiving gap.
    private fun samePlaceResume(site:WashSite, before:FacilityFix?, after:FacilityFix, gap:Long):Boolean {
        if(before==null || gap !in (MAX_GAP_MS+1)..LIFETIME_MS || before.liveSpeed()!=0.0 || after.liveSpeed()!=0.0 ||
            before.liveGear()==null || after.liveGear()==null ||
            zone(site,before)!=ZoneEvidence.Inside || zone(site,after)!=ZoneEvidence.Inside) return false
        // This is inferred dwell, not proof of ignition-off or continuous telemetry during the gap.
        return distance(WashSite("resume",before.latitude,before.longitude),after)<=RESUME_SAME_PLACE_M &&
            after.receivedAt-before.receivedAt>MAX_GAP_MS &&
            abs((after.receivedAt-before.receivedAt)-gap)<=15_000
    }
    fun distance(site: WashSite, fix: FacilityFix): Double {
        val dlat=Math.toRadians(fix.latitude-site.latitude);val dlon=Math.toRadians(fix.longitude-site.longitude)
        val a=sin(dlat/2).pow(2)+cos(Math.toRadians(site.latitude))*cos(Math.toRadians(fix.latitude))*sin(dlon/2).pow(2)
        return 6_371_000*2*asin(sqrt(a.coerceIn(0.0,1.0)))
    }
    /** Ray casting plus distance-to-edge guard. Boundary/accuracy uncertainty contributes no dwell or exit. */
    fun zone(site:WashSite, fix:FacilityFix):ZoneEvidence {
        if(!site.mappedArea() || !fix.valid()) return ZoneEvidence.Uncertain
        var inside=false;var edge=Double.POSITIVE_INFINITY
        val scale=cos(Math.toRadians(fix.latitude))*111_195.0
        for(i in 0 until site.boundary.lastIndex) {
            val a=site.boundary[i];val b=site.boundary[i+1]
            val ax=(a.longitude-fix.longitude)*scale;val ay=(a.latitude-fix.latitude)*111_195.0
            val bx=(b.longitude-fix.longitude)*scale;val by=(b.latitude-fix.latitude)*111_195.0
            val dx=bx-ax;val dy=by-ay;val length=dx*dx+dy*dy
            val t=if(length==0.0) 0.0 else (-(ax*dx+ay*dy)/length).coerceIn(0.0,1.0)
            edge=min(edge,hypot(ax+t*dx,ay+t*dy))
            if((ay>0)!=(by>0) && 0.0<(bx-ax)*(-ay)/(by-ay)+ax) inside=!inside
        }
        if(edge<=max(EDGE_MARGIN_M,fix.accuracyM ?: 0.0)) return ZoneEvidence.Uncertain
        return if(inside) ZoneEvidence.Inside else ZoneEvidence.Outside
    }
    fun candidate(sites: List<WashSite>, fix: FacilityFix): WashSite? {
        if(!fix.valid() || fix.phone) return null
        // Overlapping wash areas cannot identify which facility was visited.
        return sites.filter {zone(it,fix)==ZoneEvidence.Inside}.singleOrNull()
    }
    fun evaluate(visit: FacilityVisit): FacilityVisitResult {
        if(!visit.site.mappedArea()) return FacilityVisitResult(0,false,null)
        val all=visit.fixes.sortedWith(compareBy<FacilityFix>{it.at}.thenBy {it.phone})
            .distinctBy {it.at}.take(MAX_FIXES)
        val first=all.firstOrNull {!it.phone && zone(visit.site,it)==ZoneEvidence.Inside}
            ?: return FacilityVisitResult(0,false,null)
        var previous:FacilityFix?=null
        var resumeAnchor:FacilityFix?=null
        var anchorAreaDwell=0L;var anchorStoppedDwell=0L
        var areaDwell=0L;var stoppedDwell=0L;var neutralMs=0L;var neutralMetres=0.0
        var stationaryQualified=false
        var inferredGap=0L
        var leftArea=false
        var neutralMotion=false;var driveAfterMotion=false
        var exitAt:Long?=null;var lastOutside:Long?=null
        for(f in all.filter {it.at>=first.at && it.at-first.at<=LIFETIME_MS}) {
            // Phone fixes remain useful spatial evidence, but cannot invent vehicle speed/gear or complete a visit.
            if(f.phone) continue
            val p=previous;val gap=p?.let {f.at-it.at} ?: 0L
            val zone=zone(visit.site,f);val speed=f.liveSpeed()
            val anchor=resumeAnchor
            val resumeGap=anchor?.let {f.at-it.at} ?: 0L
            val resumed=samePlaceResume(visit.site,anchor,f,resumeGap)
            if(zone==ZoneEvidence.Outside || speed?.let {it!=0.0}==true) resumeAnchor=null
            if(zone==ZoneEvidence.Outside) leftArea=true
            if(resumed) {
                // Count from the last real stationary fix, even if reconnect first publishes Loading/NoData.
                areaDwell=anchorAreaDwell+resumeGap;stoppedDwell=anchorStoppedDwell+resumeGap;inferredGap+=resumeGap
                if(stoppedDwell>=DWELL_MS) stationaryQualified=true
                exitAt=null;lastOutside=null
            } else if(gap>MAX_GAP_MS || !f.valid() || speed==null) {
                areaDwell=0;stoppedDwell=0;neutralMs=0;neutralMetres=0.0
                inferredGap=0
                stationaryQualified=false
                neutralMotion=false;driveAfterMotion=false;exitAt=null;lastOutside=null
            } else if(zone==ZoneEvidence.Uncertain) {
                // Crossing the boundary buffer is expected during exit. It adds no time, but does not
                // erase an already-complete in-area pattern while fresh vehicle signals continue.
                if(areaDwell<DWELL_MS || !neutralMotion) {
                    areaDwell=0;neutralMs=0;neutralMetres=0.0;neutralMotion=false;driveAfterMotion=false
                }
                stoppedDwell=0;exitAt=null;lastOutside=null
            }
            if(zone==ZoneEvidence.Inside && speed!=null) {
                if(leftArea) {
                    // Leaving and re-entering is not one continuous in-area wash session.
                    areaDwell=0;stoppedDwell=0;stationaryQualified=false
                    inferredGap=0
                    neutralMs=0;neutralMetres=0.0;neutralMotion=false;driveAfterMotion=false
                    leftArea=false
                }
                exitAt=null;lastOutside=null
                val continuous=p!=null && zone(visit.site,p)==ZoneEvidence.Inside && p.liveSpeed()!=null && gap in 1..MAX_GAP_MS
                if(continuous) {
                    areaDwell+=gap
                    if(speed==0.0 && p!!.liveSpeed()==0.0) stoppedDwell+=gap else stoppedDwell=0
                    if(speed!=0.0 && !stationaryQualified) inferredGap=0
                    if(stoppedDwell>=DWELL_MS) stationaryQualified=true
                    if(f.liveGear()=="N" && p!!.liveGear()=="N" && speed*p.liveSpeed()!!>0.0) {
                        neutralMs+=gap;neutralMetres+=min(abs(speed),abs(p.liveSpeed()!!))*gap/1000.0
                        // Reject a single noisy nonzero sample as conveyor motion evidence.
                        if(neutralMs>=5_000 && neutralMetres>=1.0) neutralMotion=true
                        driveAfterMotion=false
                    }
                } else if(!resumed) stoppedDwell=0
                if(neutralMotion && f.liveGear()=="D") driveAfterMotion=true
            } else if(zone==ZoneEvidence.Outside && speed!=null && abs(speed)>0.0) {
                if(neutralMotion && f.liveGear()=="D") driveAfterMotion=true
                if(lastOutside==null || f.at-lastOutside!!>MAX_GAP_MS) exitAt=f.at
                lastOutside=f.at
                if(exitAt!=null && f.at-exitAt!!>=30_000) {
                    // The same-place resume estimate is available for automatic and self-service washes.
                    // It always asks the existing cancelable confirmation, never claims a verified conveyor pattern.
                    val stationary=stationaryQualified && (visit.site.facility!=WashFacility.Conveyor || inferredGap>0)
                    val conveyor=visit.site.facility!=WashFacility.Stationary && areaDwell>=DWELL_MS && neutralMotion && driveAfterMotion
                    val wash=if(stationary || conveyor) DetectedWash(visit.id,Instant.ofEpochMilli(exitAt!!),WashCompletionEvidence.EstimatedVisit) else null
                    return FacilityVisitResult(max(areaDwell,stoppedDwell),true,wash)
                }
            } else { exitAt=null;lastOutside=null }
            if(zone==ZoneEvidence.Inside && speed==0.0 && f.liveGear()!=null) {
                resumeAnchor=f;anchorAreaDwell=areaDwell;anchorStoppedDwell=stoppedDwell
            }
            else if(f.valid() && zone==ZoneEvidence.Uncertain) resumeAnchor=null
            previous=f
        }
        return FacilityVisitResult(max(areaDwell,stoppedDwell),false,null)
    }
}
