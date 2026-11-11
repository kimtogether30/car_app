package com.example.vemoring

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class GarageFleetTest {
    private val context=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val prefix="fleet_${UUID.randomUUID()}_"
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(prefix+name,mode)
    }
    private fun car(name:String)=AppData(CarProfile("현대","아반떼",name,""),listOf(
        CareRecord(date=LocalDate.now(CARE_ZONE).minusDays(2),kind=CareKind.WASH,title="$name 세차")),true)

    @Test fun sameModelsWithoutPlatesRemainSeparateAndViewingDoesNotRebindSource() {
        val store=GarageStore(context);val a=car("첫 차");val b=car("둘째 차")
        store.save(a);store.save(b)
        assertEquals(2,store.fleet().vehicles.size)
        assertNotEquals(a.profile.vehicleId,b.profile.vehicleId)
        assertEquals(b,store.load());assertEquals(a,store.receiving())
        store.select(a.profile.vehicleId);assertEquals(a,GarageStore(context).load())
        store.save(a.copy(profile=a.profile.copy(plate="12가 3456")))
        store.save(store.load().copy(profile=store.load().profile.copy(plate="")))
        assertEquals(a.profile.vehicleId,store.load().profile.vehicleId)
        assertEquals(a.records,store.load().records)
        assertEquals(b,store.fleet().vehicles.first {it.profile.vehicleId==b.profile.vehicleId})
        store.bindReceiver(b.profile.vehicleId)
        assertEquals(b,store.receiving());assertEquals(a.profile.vehicleId,store.load().profile.vehicleId)
    }
    @Test fun legacyGarageRetainsItsExistingJournalKeyAndRecords() {
        val profile=JSONObject().put("brand","현대").put("model","아반떼").put("nickname","기존 차").put("plate","")
            .put("fuel","휘발유").put("oilEstimate","").put("fuelAlert",20).put("washAlert",30).put("notifications",true)
        val record=CareRecord(date=LocalDate.now(CARE_ZONE).minusDays(4),kind=CareKind.WASH,title="보존할 메모",detail="기존 내용")
        context.getSharedPreferences("vemoring_native",Context.MODE_PRIVATE).edit().putString("garage_v1",JSONObject()
            .put("complete",true).put("profile",profile).put("records",JSONArray().put(record.json())).toString()).commit()
        val store=GarageStore(context);val migrated=store.load()
        assertEquals(legacyVehicleId("현대 아반떼",""),migrated.profile.vehicleId)
        assertEquals(listOf(record),migrated.records)
        store.save(car("추가 차량"))
        assertEquals(migrated,store.fleet().vehicles.first {it.profile.vehicleId==migrated.profile.vehicleId})
    }
    @Test fun unknownPreservesHistoryAndExplicitDeletionSurvivesStaleSave() {
        val store=GarageStore(context);val a=car("삭제 시험");store.save(a)
        val unknown=updateLatest(a.records,mapOf(CareKind.WASH to null))
        assertEquals(a.records,unknown)
        store.save(a.copy(records=deleteRecord(a.records,a.records.single().id)))
        store.save(a) // stale editor cannot resurrect a deleted ID
        assertTrue(store.load().records.single().deleted)
        assertTrue(store.load().records.single().cancelled)
    }
    @Test fun phoneSyncUpdatesItsOwnVehicleWithoutChangingSelectionOrPendingEdits() {
        val store=GarageStore(context);val a=car("수신 차");val b=car("조회 차");store.save(a);store.save(b)
        val profile=JSONObject().put("nickname","휴대폰에서 수정").put("model","현대 아반떼").put("plate","")
            .put("fuel","휘발유").put("fuelAlert",25).put("washAlert",20).put("notifications",false)
        val authority=CareAuthority(a.profile.vehicleId,2,profile.toString(),JSONArray().put(a.records.single().json()).toString())
        store.syncFromPhone(authority)
        assertEquals(b,store.load());assertEquals("휴대폰에서 수정",store.receiving()!!.profile.nickname)
        val edited=store.receiving()!!.copy(profile=store.receiving()!!.profile.copy(nickname="차량에서 편집"))
        store.save(edited,queueForPhone=true)
        store.syncFromPhone(authority)
        assertEquals("차량에서 편집",store.receiving()!!.profile.nickname)
        assertEquals(b.profile.vehicleId,store.fleet().selectedId)
    }
}
