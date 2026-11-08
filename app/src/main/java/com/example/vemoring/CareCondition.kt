package com.example.vemoring

import com.example.vemoring.integration.*
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CareCondition(val signals: CareSignals = CareSignals(), val cleanliness: Int? = null,
                         val oilRemainingKm: Double? = null)

fun Reading.realPercent(): Int? = value?.takeIf {
    state == ReadingState.Available && it.unit == "%"
}?.number?.takeIf { it.isFinite() && it in 0.0..100.0 }?.toInt()

/** Oil-level / pressure warnings are safety signals, not a scheduled oil change. */
fun careCondition(data: AppData, snapshot: VehicleSnapshot, config: MaintenanceConfig,
                  environment: Result<EnvironmentData> = Result(), today: LocalDate = LocalDate.now(),
                  now: Long = System.currentTimeMillis()): CareCondition {
    val p = data.profile
    val fuel = snapshot[VehicleField.FUEL].realPercent().takeUnless { p.electric }
    val soc = snapshot[VehicleField.SOC].realPercent().takeIf { p.electric || p.hybrid }
    val low = snapshot[VehicleField.LOW_FUEL].takeIf { it.state == ReadingState.Available }?.value?.warning
        .takeUnless { p.electric }
    val energy = listOfNotNull(fuel, soc).map { it <= p.fuelAlert }
    val lowFuel = when { low == true || energy.any { it } -> true; energy.isNotEmpty() -> false; else -> low }
    val current = confirmedKilometres(snapshot[VehicleField.ODOMETER], config.unit)
    fun latest(kind: CareKind) = data.records.filter { it.kind == kind && it.date <= today }.maxByOrNull { it.date }
    val wash = latest(CareKind.WASH)
    val washDistance = if (wash != null && config.washId == "${wash.id}@${wash.date}" && current != null)
        config.washKm?.takeIf { it.isFinite() && it >= 0 && it <= current }?.let { current - it } else null
    val weather = environment.takeIf { it.state == ResultState.Available && it.source.startsWith("Real") &&
        it.receivedAt?.let { time -> now - time in 0..3600000 } == true }?.value
    fun valid(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }
    val clean = wash?.let { CareEstimator.cleanliness(ChronoUnit.DAYS.between(it.date, today),
        washDistance, null, valid(weather?.pm10), valid(weather?.dust)) }
    val oil = latest(CareKind.OIL)
    val oilKm = valid(config.oilKm)
    val interval = valid(config.intervalKm)?.takeIf { it > 0 }
    val remaining = if (!p.electric && oil != null && config.oilId == "${oil.id}@${oil.date}" &&
        current != null && oilKm != null && interval != null && current >= oilKm)
        CareEstimator.oilRemaining(current, oilKm, interval) else null
    return CareCondition(CareSignals(lowFuel, clean?.let { it <= p.washAlert }, remaining?.let { it <= 0 }), clean, remaining)
}
