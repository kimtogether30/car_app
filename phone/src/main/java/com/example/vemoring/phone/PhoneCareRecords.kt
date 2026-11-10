package com.example.vemoring.phone

import java.time.LocalDate
import java.util.UUID

internal data class Entry(val id: String = UUID.randomUUID().toString(), val kind: String,
    val title: String, val date: LocalDate, val detail: String, val completedAt: String? = null,
    val visitId: String? = null, val cancelled: Boolean = false, val source: String = "Manual", val appliedAt: String? = null,
    val washEvidence: String = "EstimatedVisit")

/** Match the vehicle's date editor: unknown dates keep existing records; edited dates retain IDs and notes. */
internal fun updateLatestPhoneRecords(entries: List<Entry>, dates: Map<String, LocalDate?>, today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))): List<Entry> {
    require(dates.keys.all { it in listOf("세차", "주유", "엔진오일 교체") })
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
