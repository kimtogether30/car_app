package com.example.vemoring

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecordIntentTest {
    @Test fun dateCorrectionMakesAutomaticWashManualAndDiscardsOriginalTimestamp() {
        val record=CareRecord("auto",LocalDate.now().minusDays(4),CareKind.WASH,"세차",completedAt="2026-11-01T01:00:00Z",visitId="visit",source="Automatic")
        val changed=updateLatest(listOf(record),mapOf(CareKind.WASH to LocalDate.now().minusDays(1))).single()
        assertNull(changed.completedAt)
        assertEquals("Manual",changed.source)
        assertEquals("UserRecord",changed.washEvidence)
        assertEquals(record.id,changed.id)
        assertEquals(record.visitId,changed.visitId)
    }
    @Test fun aDeletedMaintenanceRecordDoesNotRemainAnEstimateAnchor() {
        val r=CareRecord("old",LocalDate.now().minusDays(4),CareKind.OIL,"삭제된 기록",cancelled=true,deleted=true)
        val data=AppData(records=listOf(r))
        val s=com.example.vemoring.vehicle.VehicleSnapshot().let {it.copy(readings=it.readings+
            (com.example.vemoring.vehicle.VehicleField.ODOMETER to com.example.vemoring.vehicle.Reading.available(
                com.example.vemoring.vehicle.SignalValue("15000km",15000.0,"km"))))}
        val config=com.example.vemoring.integration.MaintenanceConfig(unit="km",oilId="${r.id}@${r.date}",oilKm=10000.0)
        assertNull(careCondition(data,s,config).oilRemainingKm)
    }
    @Test fun unknownDoesNotCreateTodayOrRemoveExistingRecords() {
        val record=CareRecord(date=LocalDate.now().minusDays(4),kind=CareKind.WASH,title="알고 있던 기록")
        assertEquals(listOf(record),updateLatest(listOf(record),mapOf(CareKind.WASH to null)))
        assertEquals(emptyList<CareRecord>(),updateLatest(emptyList(),mapOf(CareKind.WASH to null)))
    }
    @Test fun aNewRecordAfterDeletionGetsANewIdInsteadOfResurrectingTheDeletedOne() {
        val deleted=CareRecord("setup-wash",LocalDate.now().minusDays(4),CareKind.WASH,"이전 세차",cancelled=true,deleted=true)
        val result=updateLatest(listOf(deleted),mapOf(CareKind.WASH to LocalDate.now()))
        assertEquals(2,result.size)
        assertTrue(result.first {it.id==deleted.id}.deleted)
        val active=result.single {!it.cancelled}
        assertNotEquals(deleted.id,active.id)
        assertEquals(LocalDate.now(),active.date)
    }
}
