package com.example.vemoring.vehicle

import kotlin.math.abs

enum class VehicleMotion { Unknown, Moving, Stopped, Parked }

/** Preserve original SDK rawState while normalizing Vehicle 2.3.2 GEAR_PARK etc. */
fun normalizedGear(raw:String?):String? = when(raw) {
    "P","GEAR_PARK" -> "P"
    "R","GEAR_REVERSE" -> "R"
    "N","GEAR_NEUTRAL" -> "N"
    "D","GEAR_DRIVE" -> "D"
    else -> null
}

/** Exact zero for now: no invented low-speed cutoff. Reverse motion is not stopped. */
fun VehicleSnapshot.motion(now: Long = System.currentTimeMillis()): VehicleMotion {
    val speed=this[VehicleField.SPEED]
    val at=speed.receivedAtMillis ?: return VehicleMotion.Unknown
    val value=speed.value?.number
    if(speed.state!=ReadingState.Available || speed.value?.unit!="m/s" || value==null || !value.isFinite() ||
        now-at !in 0..5000) return VehicleMotion.Unknown
    if(abs(value)>0) return VehicleMotion.Moving
    val gear=this[VehicleField.GEAR]
    val gearAt=gear.receivedAtMillis
    val p=gear.state==ReadingState.Available && gearAt!=null && now-gearAt in 0..35000 && normalizedGear(gear.value?.rawState)=="P"
    return if(p) VehicleMotion.Parked else VehicleMotion.Stopped
}

fun VehicleSnapshot.confirmedStationary(now:Long=System.currentTimeMillis()):Boolean? = when(motion(now)) {
    VehicleMotion.Unknown -> null
    VehicleMotion.Moving -> false
    VehicleMotion.Stopped,VehicleMotion.Parked -> true
}

/** Convert a monotonic CarPropertyValue timestamp; cached/stale/error values never become zero. */
fun vehicleSpeedReading(mps:Double?,sampleNanos:Long,elapsedNanos:Long,wallMillis:Long):Reading {
    if(mps==null || !mps.isFinite()) return Reading(ReadingState.NoData)
    val age=elapsedNanos-sampleNanos
    if(sampleNanos<=0 || age !in 0..5_000_000_000L) return Reading(ReadingState.NoData,reason="차속 값이 오래되어 재수신 필요")
    return Reading(ReadingState.Available,SignalValue("${String.format(java.util.Locale.ROOT,"%.1f",mps*3.6)} km/h",
        mps,"m/s",rawState="PERF_VEHICLE_SPEED"),receivedAtMillis=wallMillis-age/1_000_000)
}
