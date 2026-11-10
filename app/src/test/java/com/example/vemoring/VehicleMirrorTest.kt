package com.example.vemoring

import com.example.vemoring.vehicle.*
import com.vemoring.companion.MirrorSnapshot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VehicleMirrorTest {
    @Test fun publishedViewMatchesVehicleProfileRecordsAndCondition() {
        val data = AppData(CarProfile("현대", "아반떼", "우리 차", "12가 3456"),
            listOf(CareRecord("r1", LocalDate.of(2026, 11, 8), CareKind.WASH, "손세차", "3만원")), true)
        val vehicle = VehicleSnapshot(VehicleSnapshot().readings + (VehicleField.FUEL to Reading.percent(18)))
        val content = mirrorContent(data, vehicle, CareCondition(cleanliness = 62, oilRemainingKm = 1400.0), MascotMood.FUEL, false)
        val decoded = MirrorSnapshot.decode(MirrorSnapshot("car", 1, 10, content).encode())
        val result = JSONObject(decoded.content)
        assertEquals("현대 아반떼", result.getJSONObject("profile").getString("model"))
        assertEquals("우리 차", result.getJSONObject("profile").getString("nickname"))
        assertEquals("r1", result.getJSONArray("records").getJSONObject(0).getString("id"))
        assertEquals(18, result.getInt("fuel"))
        assertEquals(62, result.getInt("cleanliness"))
        assertEquals("FUEL", result.getString("mood"))
        assertEquals(MascotMood.FUEL.message, result.getString("message"))
    }
    @Test fun unavailableAndDemoReadingsAreNotPublishedAsRealPercentages() {
        val snapshot = VehicleSnapshot(VehicleSnapshot().readings +
            (VehicleField.FUEL to Reading(ReadingState.Demo, SignalValue("예시 80%", 80.0, "%"))))
        val content = JSONObject(mirrorContent(AppData(), snapshot, CareCondition(), MascotMood.NORMAL, true))
        assertTrue(content.isNull("fuel"))
        assertTrue(content.isNull("battery"))
        assertTrue(content.isNull("cleanliness"))
        assertTrue(content.getBoolean("demo"))
        assertTrue(content.getString("message").contains("예시"))
    }
}
