package com.example.vemoring.phone

import com.vemoring.companion.MaintenanceType
import com.vemoring.companion.BannerPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MaintenanceWireTest {
    @Test fun tireAndOilAreBothMaintenanceWithDifferentExplicitItems() {
        val tire = Entry(kind = MAINTENANCE, title = "공기압 보충", date = LocalDate.of(2026,11,11), detail = "",
            maintenanceType = MaintenanceType.TIRE_PRESSURE)
        val wire = tire.wireJson()
        assertEquals("maintenance", wire.getString("kind"))
        assertEquals("TIRE_PRESSURE", wire.getString("maintenanceType"))
        assertEquals("MAINTENANCE_DONE", BannerPolicy.recordCompletion(wire.getString("kind"), MaintenanceType.from(wire.getString("maintenanceType")))!!.key)
        val oil = tire.copy(maintenanceType = MaintenanceType.ENGINE_OIL).wireJson()
        assertEquals("maintenance", oil.getString("kind"))
        assertEquals("ENGINE_OIL", oil.getString("maintenanceType"))
        val edited = updateLatestPhoneRecords(listOf(tire), mapOf(MAINTENANCE to tire.date.minusDays(1))).single()
        assertEquals(tire.id, edited.id)
        assertEquals(tire.maintenanceType, edited.maintenanceType)
        assertTrue(deletePhoneRecord(listOf(tire), tire.id).single().wireJson().getBoolean("deleted"))
    }
}
