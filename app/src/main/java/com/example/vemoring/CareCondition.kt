package com.example.vemoring

import com.example.vemoring.integration.*
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import com.vemoring.companion.*
import java.time.Instant

data class CareCondition(val signals: CareSignals = CareSignals(), val cleanliness: Int? = null,
                         val oilRemainingKm: Double? = null, val syncStatus: String = "차량 임시 추정", val oilAssessment: EngineOilAssessment? = null)

fun Reading.realPercent(): Int? = value?.takeIf {
    state == ReadingState.Available && it.unit == "%"
}?.number?.takeIf { it.isFinite() && it in 0.0..100.0 }?.toInt()

/** Oil-level / pressure warnings are safety signals, not a scheduled oil change. */
fun careCondition(data: AppData, snapshot: VehicleSnapshot, config: MaintenanceConfig,
                  environment: Result<EnvironmentData> = Result(), today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")),
                  now: Long = System.currentTimeMillis()): CareCondition {
    val p = data.profile
    val lowFuel = EnergyPolicy.low(p.electric, snapshot[VehicleField.FUEL].realPercent(), snapshot[VehicleField.SOC].realPercent(), p.fuelAlert)
    val current = confirmedKilometres(snapshot[VehicleField.ODOMETER], config.unit)
    fun latest(kind: CareKind) = data.records.filter { it.kind == kind && !it.cancelled && it.date <= today }.maxByOrNull { it.date }
    fun valid(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }
    val clean = WashPolicy.score(data.records.filter { it.kind == CareKind.WASH }.map {
        WashBaseline(it.id, it.date, it.completedAt?.let(Instant::parse), it.visitId, it.cancelled, it.source)
    }, config.intervals, Instant.ofEpochMilli(now)).displayed
    val oil = data.records.filter { it.kind == CareKind.OIL && !it.cancelled && it.date <= today &&
        it.maintenanceType == com.vemoring.companion.MaintenanceType.ENGINE_OIL }.maxByOrNull { it.date }
    val ledger=config.oilLedger?.let {org.json.JSONObject(it.toString())}
    val reading=snapshot[VehicleField.ODOMETER]
    if(ledger!=null && current!=null && now-(reading.receivedAtMillis ?: 0) in 0..60_000 &&
        current>=ledger.optDouble("currentKm",0.0)) ledger.put("currentKm",current)
    val assessment=if(ledger!=null) EngineOilCalculation.assess(ledger,config.oilRule,
        org.json.JSONArray().also {a -> data.records.forEach {a.put(it.json())}},today,p.electric)
        else EngineOilPolicy.assess(config.oilRule,oil?.date,config.oilKm.takeIf {config.oilId==oil?.let {"${it.id}@${it.date}"}},current,today,p.electric)
    return CareCondition(CareSignals(lowFuel,clean?.let {it<=p.washAlert},assessment.due),clean,assessment.remainingKm,config.syncStatus,assessment)
}
