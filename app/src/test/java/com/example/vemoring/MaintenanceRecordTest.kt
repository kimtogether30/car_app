package com.example.vemoring

import com.vemoring.companion.MaintenanceType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MaintenanceRecordTest {
    @Test fun legacyOilBecomesGeneralMaintenanceAndPreservesDeletedRecord() {
        val old = JSONObject().put("id", "old-id").put("kind", "oil").put("title", "타이어 정비")
            .put("date", "2026-11-10").put("detail", "메모").put("cancelled", true).put("deleted", true)
        val record = careRecordFromJson(old)
        assertEquals(CareKind.MAINTENANCE, record.kind)
        assertEquals(MaintenanceType.GENERAL, record.maintenanceType)
        val wire = record.json()
        assertEquals("maintenance", wire.getString("kind"))
        for(key in listOf("id", "title", "date", "detail", "cancelled", "deleted")) assertEquals(old.get(key), wire.get(key))
    }

    @Test fun maintenanceSubtypesSurviveRoundTripAndDateEditing() {
        for(type in MaintenanceType.entries) {
            val record = CareRecord(date = LocalDate.of(2026,11,9), kind = CareKind.MAINTENANCE, title = type.label, maintenanceType = type)
            assertEquals(record, careRecordFromJson(record.json()))
            val edited = updateLatest(listOf(record), mapOf(CareKind.MAINTENANCE to record.date.plusDays(1))).single()
            assertEquals(record.id, edited.id)
            assertEquals(type, edited.maintenanceType)
        }
    }
}
