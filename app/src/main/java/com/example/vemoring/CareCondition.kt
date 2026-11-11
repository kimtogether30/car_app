package com.example.vemoring

import com.example.vemoring.integration.*
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import com.vemoring.companion.*
import java.time.Instant

data class CareCondition(val signals: CareSignals = CareSignals(), val cleanliness: Int? = null,
                         val oilRemainingKm: Double? = null, val syncStatus: String = "차량 임시 추정")

fun Reading.realPercent(): Int? = value?.takeIf {
    state == ReadingState.Available && it.unit == "%"
}?.number?.takeIf { it.isFinite() && it in 0.0..100.0 }?.toInt()

/** Oil-level / pressure warnings are safety signals, not a scheduled oil change. */
fun careCondition(data: AppData, snapshot: VehicleSnapshot, config: MaintenanceConfig,
                  environment: Result<EnvironmentData> = Result(), today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),
                  now: Long = System.currentTimeMillis()): CareCondition {
    val p = data.profile
    val fuel = snapshot[VehicleField.FUEL].realPercent().takeUnless { p.electric }
    val soc = snapshot[VehicleField.SOC].realPercent().takeIf { p.electric || p.hybrid }
    val low = snapshot[VehicleField.LOW_FUEL].takeIf { it.state == ReadingState.Available }?.value?.warning
        .takeUnless { p.electric }
    val energy = listOfNotNull(fuel, soc).map { it <= p.fuelAlert }
    val lowFuel = when { low == true || energy.any { it } -> true; energy.isNotEmpty() -> false; else -> low }
    val current = confirmedKilometres(snapshot[VehicleField.ODOMETER], config.unit)
    fun latest(kind: CareKind) = data.records.filter { it.kind == kind && !it.cancelled && it.date <= today }.maxByOrNull { it.date }
    fun valid(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }
    val clean = WashPolicy.score(data.records.filter { it.kind == CareKind.WASH }.map {
        WashBaseline(it.id, it.date, it.completedAt?.let(Instant::parse), it.visitId, it.cancelled, it.source)
    }, config.intervals, Instant.ofEpochMilli(now)).displayed
    val oil = data.records.filter { it.kind == CareKind.MAINTENANCE && !it.cancelled && it.date <= today &&
        it.maintenanceType == com.vemoring.companion.MaintenanceType.ENGINE_OIL }.maxByOrNull { it.date }
    val oilKm = valid(config.oilKm)
    val interval = valid(config.intervalKm)?.takeIf { it > 0 }
    val remaining = if (!p.electric && oil != null && config.oilId == "${oil.id}@${oil.date}" &&
        current != null && oilKm != null && interval != null && current >= oilKm)
        CareEstimator.oilRemaining(current, oilKm, interval) else null
    // Fuel warnings belong to yellow and must not count twice.
    val warningFields = listOf(VehicleField.ENGINE_OIL, VehicleField.OIL_PRESSURE,
        VehicleField.BRAKE_FLUID, VehicleField.TRANSMISSION_OIL, VehicleField.WASHER, VehicleField.SMART_KEY)
        .filterNot { p.electric && (it == VehicleField.ENGINE_OIL || it == VehicleField.OIL_PRESSURE) }
    val warnings = warningFields.map { snapshot[it] }.filter { it.state != ReadingState.Unsupported }
    val vehicleWarning = when {
        warnings.any { it.state == ReadingState.Available && it.value?.warning == true } -> true
        warnings.isNotEmpty() && warnings.all { it.state == ReadingState.Available } -> false
        else -> null
    }
    return CareCondition(CareSignals(lowFuel, clean?.let { it <= p.washAlert }, vehicleWarning), clean, remaining, config.syncStatus)
}
