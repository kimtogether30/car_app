package com.vemoring.companion

/** Explicit user input, independent of live vehicle warning signals. */
enum class MaintenanceType(val label: String) {
    GENERAL("일반 정비"), ENGINE_OIL("엔진오일 교체"), TIRE_PRESSURE("타이어 공기압"),
    TIRES("타이어 교체"), BRAKES("브레이크"), BATTERY("배터리"), OTHER("기타 정비");

    companion object {
        fun from(value: String?): MaintenanceType = entries.firstOrNull { it.name == value } ?: GENERAL
    }
}

/** The old oil value also described general repairs, so migration cannot infer the work item. */
fun normalizeMaintenanceRecords(raw: String): String {
    val records = org.json.JSONArray(raw)
    repeat(records.length()) { index ->
        val record = records.getJSONObject(index)
        if(record.optString("kind") == "oil") {
            record.put("kind", "maintenance").put("maintenanceType", MaintenanceType.GENERAL.name)
        }
    }
    return records.toString()
}
