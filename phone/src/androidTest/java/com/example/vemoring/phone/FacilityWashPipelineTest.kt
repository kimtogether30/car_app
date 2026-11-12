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
    private val site=WashSite("fixture-wash",37.0,127.0)
    private val start=System.currentTimeMillis()-300_000
    private fun fix(t:Long,out:Boolean=false,phone:Boolean=false)=FacilityFix(37.0+if(out) .002 else 0.0,127.0,
        start+t*1000,start+t*1000,phone,if(phone) 5.0 else null)
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
            (210L..240L step 10).forEach {producer.observeVehicle(fix(it,true),listOf(site))}
            val frame=producer.frame()
            val response=CareExchange.receive(context,incoming(id,1,frame))
            val journal=CareJournal(context,id)
            assertEquals(100,response.getInt("cleanliness"))
            val visit=journal.pendingWashConfirmation()!!.visitId
            assertTrue(visit.startsWith("radius:$id:"))
            assertEquals(1,JSONArray(journal.authority()!!.records).length())
            assertTrue(CareExchange.cancel(context,id,visit))
            assertNull(journal.score(JSONArray(journal.authority()!!.records)).value)
            CareExchange.receive(context,incoming(id,2,frame))
            val records=JSONArray(journal.authority()!!.records)
            assertEquals(1,records.length());assertTrue(records.getJSONObject(0).getBoolean("cancelled"))
        } finally {CareJournal(context,id).deleteVehicle();producer.clear()}
    }
    @Test fun phoneEvidenceSurvivesReopenAndNeedsVehicleExit()=runBlocking {
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
            assertNotNull(CareJournal(context,id).pendingWashConfirmation())
        } finally {CareJournal(context,id).deleteVehicle();store.clear()}
    }
}
