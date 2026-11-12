package com.example.vemoring.phone

import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Injected coordinates test the production wire/journal/cancel path, NOT live SDK/GPS/Bluetooth. */
class FacilityWashPipelineTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val site=WashSite("fixture-wash",37.0,127.0,listOf(WashPoint(36.9995,126.9995),
        WashPoint(37.0005,126.9995),WashPoint(37.0005,127.0005),WashPoint(36.9995,127.0005),WashPoint(36.9995,126.9995)))
    private val start=System.currentTimeMillis()-300_000
    private fun fix(t:Long,out:Boolean=false,phone:Boolean=false)=FacilityFix(37.0+if(out) .002 else 0.0,127.0,
        start+t*1000,start+t*1000,phone,if(phone) 5.0 else null,
        if(out) 2.0 else 0.0,start+t*1000,if(out) "D" else "P",start+t*1000)
    private fun incoming(id:String,revision:Long,frame:JSONArray):MirrorSnapshot {
        val content=JSONObject().put("demo",false).put("profile",JSONObject().put("vehicleId",id).put("nickname","검증 전용")
            .put("model","검증 전용").put("plate","").put("fuel","휘발유").put("washAlert",30).put("fuelAlert",20).put("notifications",false))
            .put("records",JSONArray()).put("care",JSONObject().put("washVisits",frame))
            .put("mood","NORMAL").put("message","").put("fuel",JSONObject.NULL).put("battery",JSONObject.NULL)
            .put("cleanliness",JSONObject.NULL).put("oilRemainingKm",JSONObject.NULL)
        return MirrorSnapshot.decode(MirrorSnapshot(id,revision,System.currentTimeMillis(),content.toString()).encode())
    }
    @Test fun liveFrameBoundaryCreatesCancelableEstimateAndReplayCannotUndoCancellation()=runBlocking {
        check(context.packageName.endsWith(".verification"))
        val id=UUID.randomUUID().toString();val producer=FacilityWashStore(context,id)
        try {
            (0L..190L step 10).forEach {producer.observeVehicle(fix(it),listOf(site))}
            (200L..230L step 10).forEach {producer.observeVehicle(fix(it,true),listOf(site))}
            val frame=producer.frame()
            val response=CareExchange.receive(context,incoming(id,1,frame))
            val journal=CareJournal(context,id)
            assertEquals(100,response.getInt("cleanliness"))
            val visit=journal.pendingWashConfirmation()!!.visitId
            assertTrue(visit.startsWith("radius:$id:"))
            assertEquals(1,JSONArray(journal.authority()!!.records).length())
            // No answer and restart keep the estimate. Explicit yes only dismisses the question.
            assertEquals(100,CareJournal(context,id).score(JSONArray(journal.authority()!!.records)).displayed)
            assertNull(journal.pendingWashConfirmation(java.time.Instant.now().plusSeconds(8*86400L)))
            assertEquals(1,JSONArray(journal.authority()!!.records).length())
            CareExchange.dismissConfirmation(context,id,visit)
            assertNull(CareJournal(context,id).pendingWashConfirmation())
            CareExchange.receive(context,incoming(id,2,frame))
            assertEquals(1,JSONArray(journal.authority()!!.records).length())
            assertNull(journal.pendingWashConfirmation())
            assertTrue(CareExchange.cancel(context,id,visit))
            assertNull(journal.score(JSONArray(journal.authority()!!.records)).value)
            CareExchange.receive(context,incoming(id,3,frame))
            val records=JSONArray(journal.authority()!!.records)
            assertEquals(1,records.length());assertTrue(records.getJSONObject(0).getBoolean("cancelled"))
        } finally {CareJournal(context,id).deleteVehicle();producer.clear()}
    }
    @Test fun phoneEvidenceSurvivesReopenButCannotInventVehicleStationaryTime()=runBlocking {
        check(context.packageName.endsWith(".verification"))
        val id=UUID.randomUUID().toString();val store=FacilityWashStore(context,id)
        try {
            store.observeVehicle(fix(0),listOf(site));store.observeVehicle(fix(10),listOf(site))
            val first=store.frame()
            CareExchange.receive(context,incoming(id,1,first))
            val visit=store.phoneCandidate(System.currentTimeMillis())!!.id
            (30L..190L step 10).forEach {assertTrue(store.observePhone(visit,fix(it,phone=true)))}
            assertNull(CareJournal(context,id).pendingWashConfirmation())
            // A restarted store retains assist samples; vehicle import must not discard them.
            val reopened=FacilityWashStore(context,id)
            (210L..240L step 10).forEach {reopened.observeVehicle(fix(it,true),listOf(site))}
            CareExchange.receive(context,incoming(id,2,reopened.frame()))
            assertNull(CareJournal(context,id).pendingWashConfirmation())
            assertTrue(JSONArray(CareJournal(context,id).authority()!!.records).length()==0)
        } finally {CareJournal(context,id).deleteVehicle();store.clear()}
    }
    @Test fun legacyRadiusEvidenceCannotCreateNewWashOrDeleteExistingRecords()=runBlocking {
        check(context.packageName.endsWith(".verification"))
        val id=UUID.randomUUID().toString();val store=FacilityWashStore(context,id)
        try {
            (0L..190L step 10).forEach {store.observeVehicle(fix(it),listOf(site))}
            (200L..230L step 10).forEach {store.observeVehicle(fix(it,true),listOf(site))}
            val qualified=store.frame()
            val legacy=JSONArray(qualified.toString()).also {it.getJSONObject(0).remove("policy")}
            CareExchange.receive(context,incoming(id,1,legacy))
            val journal=CareJournal(context,id)
            assertEquals(0,JSONArray(journal.authority()!!.records).length())
            CareExchange.receive(context,incoming(id,2,qualified))
            assertEquals(1,JSONArray(journal.authority()!!.records).length())
            CareExchange.receive(context,incoming(id,3,legacy))
            assertEquals(1,JSONArray(journal.authority()!!.records).length())
        } finally {CareJournal(context,id).deleteVehicle();store.clear()}
    }
    @Test fun samePlaceRestartRestoresStoredVisitAndCreatesCancelableQuestionAfterExit()=runBlocking {
        check(context.packageName.endsWith(".verification"))
        val id=UUID.randomUUID().toString();val store=FacilityWashStore(context,id)
        try {
            store.observeVehicle(fix(0),listOf(site));store.observeVehicle(fix(10),listOf(site))
            // Reopen the real persisted store, as after process death. No phone observations required.
            val reopened=FacilityWashStore(context,id)
            reopened.observeVehicle(fix(180),listOf(site))
            CareExchange.receive(context,incoming(id,1,reopened.frame()))
            assertNull(CareJournal(context,id).pendingWashConfirmation())
            (190L..220L step 10).forEach {reopened.observeVehicle(fix(it,true),listOf(site))}
            val frame=reopened.frame()
            CareExchange.receive(context,incoming(id,2,frame))
            val journal=CareJournal(context,id)
            val question=journal.pendingWashConfirmation()!!
            assertEquals(100,journal.score(JSONArray(journal.authority()!!.records)).displayed)
            assertEquals("EstimatedVisit",JSONArray(journal.authority()!!.records).getJSONObject(0).getString("washEvidence"))
            assertTrue(CareExchange.cancel(context,id,question.visitId))
            CareExchange.receive(context,incoming(id,3,frame))
            assertNull(journal.pendingWashConfirmation())
            assertTrue(JSONArray(journal.authority()!!.records).getJSONObject(0).getBoolean("cancelled"))
        } finally {CareJournal(context,id).deleteVehicle();store.clear()}
    }
}
