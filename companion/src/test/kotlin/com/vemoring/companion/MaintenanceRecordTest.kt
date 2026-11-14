package com.vemoring.companion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MaintenanceRecordTest {
    @Test fun onlyExplicitOilReplacementUsesOilCompletion() {
        for(type in MaintenanceType.entries) {
            val banner=BannerPolicy.recordCompletion("maintenance",type)
            if(type==MaintenanceType.ENGINE_OIL) assertEquals("OIL_DONE",banner?.key) else assertNull(banner)
        }
        assertEquals("OIL_DONE",BannerPolicy.recordCompletion("oil",MaintenanceType.ENGINE_OIL)?.key)
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
