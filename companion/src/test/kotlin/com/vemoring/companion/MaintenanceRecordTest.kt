package com.vemoring.companion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MaintenanceRecordTest {
    @Test fun onlyExplicitOilReplacementUsesOilCompletion() {
        for(type in MaintenanceType.entries) {
            val banner = requireNotNull(BannerPolicy.recordCompletion("maintenance", type))
            assertEquals(if(type == MaintenanceType.ENGINE_OIL) "OIL_DONE" else "MAINTENANCE_DONE", banner.key)
            if(type != MaintenanceType.ENGINE_OIL) {
                assertEquals("정비 기록을 저장했어!", banner.title)
                assertNull(banner.art)
            }
        }
        assertEquals("MAINTENANCE_DONE", BannerPolicy.recordCompletion("oil", MaintenanceType.ENGINE_OIL)!!.key)
    }

    @Test fun legacyMigrationPreservesIdentityNotesAndDeletionWithoutGuessingWork() {
        val old = JSONObject().put("id", "same-id").put("kind", "oil").put("date", "2026-11-10")
            .put("title", "타이어 점검").put("detail", "기존 메모").put("cancelled", true).put("deleted", true)
        val migrated = JSONArray(normalizeMaintenanceRecords(JSONArray().put(old).toString())).getJSONObject(0)
        assertEquals("maintenance", migrated.getString("kind"))
        assertEquals("GENERAL", migrated.getString("maintenanceType"))
        for(key in listOf("id", "date", "title", "detail", "cancelled", "deleted")) assertEquals(old.get(key), migrated.get(key))
        assertEquals(JSONArray().put(migrated).toString(), normalizeMaintenanceRecords(JSONArray().put(migrated).toString()))
    }
}
