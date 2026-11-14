package com.example.vemoring.phone

import java.time.LocalDate
import java.util.UUID
import org.json.JSONObject

/** General maintenance is a storage migration concern; new UI records are engine-oil replacements. */
internal const val MAINTENANCE = "엔진오일 교체"
/** Keep the old category readable; only an explicit ENGINE_OIL subtype is an oil baseline. */
internal fun normalizeKind(kind: String): String = if (kind == "정비") MAINTENANCE else kind

internal data class Entry(val id: String = UUID.randomUUID().toString(), val kind: String,
    val title: String, val date: LocalDate, val detail: String, val completedAt: String? = null,
    val visitId: String? = null, val cancelled: Boolean = false, val source: String = "Manual", val appliedAt: String? = null,
    val washEvidence: String = "EstimatedVisit", val deleted: Boolean = false,
    val maintenanceType: com.vemoring.companion.MaintenanceType = if(kind == MAINTENANCE) com.vemoring.companion.MaintenanceType.ENGINE_OIL else com.vemoring.companion.MaintenanceType.GENERAL, val odometerKm:Double?=null)

internal fun deletePhoneRecord(entries: List<Entry>,id: String): List<Entry> = entries.map {
    if(it.id==id) it.copy(cancelled=true,deleted=true) else it
}

/** Match the vehicle's date editor: unknown dates keep existing records; edited dates retain IDs and notes. */
internal fun updateLatestPhoneRecords(entries: List<Entry>, dates: Map<String, LocalDate?>, today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))): List<Entry> {
    require(dates.keys.all { it in listOf("세차", "주유", MAINTENANCE) })
    require(dates.values.filterNotNull().none { it > today })
    val result = entries.toMutableList()
    dates.forEach { (kind, date) ->
        val previous = result.filter { it.kind == kind && !it.cancelled && (kind != MAINTENANCE || it.maintenanceType == com.vemoring.companion.MaintenanceType.ENGINE_OIL) }.maxByOrNull { it.date }
        if (date != null && date != previous?.date) {
            if (previous != null) result.remove(previous)
            result.add(previous?.copy(date = date, completedAt = null, odometerKm=null, source = "Manual", washEvidence = "UserRecord") ?: Entry(kind = kind, title = kind, date = date, detail = ""))
        }
    }
    return result.sortedByDescending { it.date }
}

internal fun Entry.wireJson(): JSONObject = JSONObject().put("id", id).put("kind", when(kind) {
    "세차" -> "wash"; "주유" -> "fuel"; else -> "maintenance" }).put("maintenanceType", if(kind == MAINTENANCE) maintenanceType.name else null).put("title", title).put("date", date)
    .put("detail", detail).put("odometerKm",odometerKm).put("completedAt", completedAt).put("visitId", visitId).put("cancelled", cancelled).put("source", source).put("appliedAt", appliedAt).put("washEvidence",washEvidence).put("deleted",deleted)
