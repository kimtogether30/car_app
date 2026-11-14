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
            val reading = Reading(state, SignalValue("0 %", 0.0, "%"))
            assertNull(evaluate(vehicle = snapshot(VehicleField.FUEL to reading)).signals.lowFuel)
        }
    }
    @Test fun realZeroFuelAndThresholdTriggerYellow() {
        for (value in listOf(0, 20)) assertEquals(MascotMood.FUEL,
            evaluate(vehicle = snapshot(VehicleField.FUEL to Reading.percent(value))).signals.mood())
        assertEquals(false, evaluate(vehicle = snapshot(VehicleField.FUEL to Reading.percent(21))).signals.lowFuel)
    }
    @Test fun electricUsesSocWhileHybridUsesOnlyFuel() {
        val ev = AppData(profile.copy(fuel = "전기차"))
        val vehicle = snapshot(VehicleField.FUEL to Reading.percent(0), VehicleField.SOC to Reading.percent(80))
        assertEquals(MascotMood.NORMAL, evaluate(ev, vehicle).signals.mood())
        assertEquals(MascotMood.FUEL, evaluate(ev, snapshot(VehicleField.SOC to Reading.percent(0))).signals.mood())
        assertEquals(MascotMood.FUEL, evaluate(AppData(profile.copy(fuel = "하이브리드")), vehicle).signals.mood())
        val chargedFuel = snapshot(VehicleField.FUEL to Reading.percent(80), VehicleField.SOC to Reading.percent(0))
        assertEquals(false,evaluate(AppData(profile.copy(fuel="하이브리드")),chargedFuel).signals.lowFuel)
        assertNull(evaluate(AppData(profile.copy(fuel="하이브리드")),snapshot(VehicleField.SOC to Reading.percent(0))).signals.lowFuel)
    }
    @Test fun approvedMileageAndMatchingRecordAreRequiredForOilChange() {
        val oil = CareRecord("oil", today.minusDays(100), CareKind.OIL, "교체",
            maintenanceType = com.vemoring.companion.MaintenanceType.ENGINE_OIL)
        val data = AppData(profile, listOf(oil))
        val vehicle = snapshot(VehicleField.ODOMETER to Reading.available(SignalValue("15000", 15000.0)))
        val config = MaintenanceConfig("km", oilId = "oil@${oil.date}", oilKm = 10000.0, intervalKm = 5000.0, oilRule=com.vemoring.companion.EngineOilRule("test","현대 아반떼","휘발유",5000.0,null,"fixture"))
        assertEquals(0.0, evaluate(data, vehicle, config).oilRemainingKm!!, 0.0)
        for(type in com.vemoring.companion.MaintenanceType.entries.filter { it != com.vemoring.companion.MaintenanceType.ENGINE_OIL }) {
            assertNull(evaluate(data.copy(records = listOf(oil.copy(maintenanceType = type))), vehicle, config).oilRemainingKm)
        }
        assertEquals(MascotMood.OIL, evaluate(data, vehicle, config).signals.mood())
        assertNull(evaluate(data, vehicle, config.copy(unit = "")).oilRemainingKm)
        assertNull(evaluate(data, vehicle, config.copy(oilId = "old")).oilRemainingKm)
        assertNull(evaluate(data, vehicle, config.copy(oilRule=null)).oilRemainingKm)
    }

}
