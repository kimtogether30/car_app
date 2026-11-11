package com.example.vemoring.phone

import java.time.LocalDate
import java.util.UUID

/** Third record kind: a resolved vehicle warning. Wire value stays "oil" so vehicle data and history are unchanged. */
internal const val MAINTENANCE = "정비"
/** Records saved before the rename used "엔진오일 교체"; read them as 정비 without rewriting stored data. */
internal fun normalizeKind(kind: String): String = if (kind == "엔진오일 교체") MAINTENANCE else kind

internal data class Entry(val id: String = UUID.randomUUID().toString(), val kind: String,
    val title: String, val date: LocalDate, val detail: String, val completedAt: String? = null,
    val visitId: String? = null, val cancelled: Boolean = false, val source: String = "Manual", val appliedAt: String? = null,
    val washEvidence: String = "EstimatedVisit", val deleted: Boolean = false)

internal fun deletePhoneRecord(entries: List<Entry>,id: String): List<Entry> = entries.map {
    if(it.id==id) it.copy(cancelled=true,deleted=true) else it
}

/** Match the vehicle's date editor: unknown dates keep existing records; edited dates retain IDs and notes. */
internal fun updateLatestPhoneRecords(entries: List<Entry>, dates: Map<String, LocalDate?>, today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))): List<Entry> {
    require(dates.keys.all { it in listOf("세차", "주유", MAINTENANCE) })
    require(dates.values.filterNotNull().none { it > today })
    val result = entries.toMutableList()
    dates.forEach { (kind, date) ->
        val previous = result.filter { it.kind == kind && !it.cancelled }.maxByOrNull { it.date }
        if (date != null && date != previous?.date) {
            if (previous != null) result.remove(previous)
            result.add(previous?.copy(date = date) ?: Entry(kind = kind, title = kind, date = date, detail = ""))
        }
    }
    return result.sortedByDescending { it.date }
}
