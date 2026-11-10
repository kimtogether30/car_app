package com.example.vemoring.phone

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PhoneCareRecordsTest {
    private val today = LocalDate.of(2026, 11, 9)
    @Test fun editingLatestDatePreservesRecordIdentityTitleAndMemo() {
        val older = Entry("older", "세차", "예전 세차", today.minusDays(20), "보존 메모")
        val latest = Entry("latest", "세차", "손세차", today.minusDays(3), "장소·금액·한글 메모")
        val result = updateLatestPhoneRecords(listOf(latest, older), mapOf("세차" to today.minusDays(1)), today)
        assertEquals(2, result.size)
        assertEquals(latest.copy(date = today.minusDays(1)), result.first())
        assertEquals(older, result.last())
    }
    @Test fun unknownDatesKeepExistingHistoryAndNewCategoryAddsOnlyOneRecord() {
        val record = Entry("wash", "세차", "기존 기록", today.minusDays(3), "기존 메모")
        val result = updateLatestPhoneRecords(listOf(record), mapOf("세차" to null, "주유" to today), today)
        assertTrue(result.contains(record))
        assertEquals(1, result.count { it.kind == "주유" })
        assertEquals(today, result.first().date)
    }
    @Test fun futureDatesAreRejectedWithoutMutatingTheExistingList() {
        val records = listOf(Entry("wash", "세차", "세차", today, "메모"))
        assertThrows(IllegalArgumentException::class.java) { updateLatestPhoneRecords(records, mapOf("세차" to today.plusDays(1)), today) }
        assertEquals(today, records.single().date)
    }
}
