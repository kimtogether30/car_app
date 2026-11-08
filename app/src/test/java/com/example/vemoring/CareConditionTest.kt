package com.example.vemoring

import com.example.vemoring.integration.MaintenanceConfig
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CareConditionTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val profile = CarProfile("현대", "아반떼", "몽글이")
    private fun snapshot(vararg values: Pair<VehicleField, Reading>) = VehicleSnapshot(VehicleSnapshot().readings + values.toMap())
    private fun evaluate(data: AppData = AppData(profile), vehicle: VehicleSnapshot = VehicleSnapshot(),
                         config: MaintenanceConfig = MaintenanceConfig()) = careCondition(data, vehicle, config, today = today)

    @Test fun missingOrDemoDataNeverTriggersFuelWarning() {
        assertEquals(MascotMood.NORMAL, evaluate().signals.mood())
        for (state in ReadingState.entries.filter { it != ReadingState.Available }) {
            val reading = Reading(state, SignalValue("0 %", 0.0, "%", true))
            assertNull(evaluate(vehicle = snapshot(VehicleField.FUEL to reading)).signals.lowFuel)
        }
    }
    @Test fun realZeroFuelAndThresholdTriggerYellow() {
        for (value in listOf(0, 20)) assertEquals(MascotMood.FUEL,
            evaluate(vehicle = snapshot(VehicleField.FUEL to Reading.percent(value))).signals.mood())
        assertEquals(false, evaluate(vehicle = snapshot(VehicleField.FUEL to Reading.percent(21))).signals.lowFuel)
    }
    @Test fun electricIgnoresFuelAndOilWhileHybridUsesEitherEnergySource() {
        val ev = AppData(profile.copy(fuel = "전기차"))
        val vehicle = snapshot(VehicleField.FUEL to Reading.percent(0), VehicleField.SOC to Reading.percent(80))
        assertEquals(MascotMood.NORMAL, evaluate(ev, vehicle).signals.mood())
        assertEquals(MascotMood.FUEL, evaluate(ev, snapshot(VehicleField.SOC to Reading.percent(0))).signals.mood())
        assertEquals(MascotMood.FUEL, evaluate(AppData(profile.copy(fuel = "하이브리드")), vehicle).signals.mood())
    }
    @Test fun oilLevelOrPressureDoesNotMeanScheduledReplacement() {
        val warning = Reading.available(SignalValue("부족", warning = true))
        val condition = evaluate(vehicle = snapshot(VehicleField.ENGINE_OIL to warning, VehicleField.OIL_PRESSURE to warning))
        assertNull(condition.signals.oilChangeDue)
        assertEquals(MascotMood.NORMAL, condition.signals.mood())
    }
    @Test fun approvedMileageAndMatchingRecordAreRequiredForOilChange() {
        val oil = CareRecord("oil", today.minusDays(100), CareKind.OIL, "교체")
        val data = AppData(profile, listOf(oil))
        val vehicle = snapshot(VehicleField.ODOMETER to Reading.available(SignalValue("15000", 15000.0)))
        val config = MaintenanceConfig("km", oilId = "oil@${oil.date}", oilKm = 10000.0, intervalKm = 5000.0)
        assertEquals(MascotMood.OIL, evaluate(data, vehicle, config).signals.mood())
        assertNull(evaluate(data, vehicle, config.copy(unit = "")).signals.oilChangeDue)
        assertNull(evaluate(data, vehicle, config.copy(oilId = "old")).signals.oilChangeDue)
        assertNull(evaluate(data, vehicle, config.copy(intervalKm = Double.NaN)).signals.oilChangeDue)
    }
    @Test fun recordsAndEnergyComposeIntoRedThenCharcoalAndClearAfterCare() {
        val wash = CareRecord("wash", today.minusDays(40), CareKind.WASH, "세차")
        val oil = CareRecord("oil", today.minusDays(100), CareKind.OIL, "교체")
        val vehicle = snapshot(VehicleField.FUEL to Reading.percent(0),
            VehicleField.ODOMETER to Reading.available(SignalValue("15000", 15000.0)))
        val data = AppData(profile, listOf(wash, oil))
        assertEquals(MascotMood.ANGRY, evaluate(data, vehicle).signals.mood())
        val config = MaintenanceConfig("km", oilId = "oil@${oil.date}", oilKm = 10000.0, intervalKm = 5000.0)
        assertEquals(MascotMood.EXHAUSTED, evaluate(data, vehicle, config).signals.mood())
        assertEquals(false, evaluate(data.copy(records = listOf(wash.copy(date = today))), vehicle).signals.needsWash)
    }
}
