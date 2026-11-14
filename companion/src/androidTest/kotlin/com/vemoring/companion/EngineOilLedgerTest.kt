package com.vemoring.companion

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Persisted fixtures only. No VHAL changes, radio claims or real garage publication. */
class EngineOilLedgerTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix="oil-test-${UUID.randomUUID()}-"
    private val context=object:ContextWrapper(base) {
        override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences(prefix+name,mode)
        override fun getDatabasePath(name:String)=base.getDatabasePath(prefix+name)
        override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=
            base.openOrCreateDatabase(prefix+name,mode,factory)
        override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,handler:android.database.DatabaseErrorHandler?)=
            base.openOrCreateDatabase(prefix+name,mode,factory,handler)
    }
    private val id="fixture-${UUID.randomUUID()}"
    private fun profile()=JSONObject().put("vehicleId",id).put("model","현대 아반떼").put("fuel","휘발유")
        .put("nickname","fixture").put("plate","").put("fuelAlert",20).put("washAlert",30).put("notifications",false)
    @After fun cleanup() {
        CareJournal(context,id).deleteVehicle()
        EngineOilVisitStore(context,id).clear()
        context.getSharedPreferences("engine_oil_v1",Context.MODE_PRIVATE).edit().clear().commit()
        base.deleteDatabase(prefix+"care_notification_history_v1.db")
    }
    @Test fun packagedRulesAndLedgerSurviveReopeningAndMeterGap() {
        val now=System.currentTimeMillis();val ledger=EngineOilLedger(context,id)
        ledger.observe(10000.0,now-3000)
        val p=profile().put("oilEstimate","약 3개월 전")
        val first=ledger.assess(p,"[]")
        assertNotNull(first.remainingKm)
        ledger.observe(10000.0,now-2000)
        ledger.observe(10001.0,now-1000)
        val reopened=EngineOilLedger(context,id)
        assertEquals(first.remainingKm!!-1,reopened.assess(p,"[]").remainingKm!!,0.0)
        reopened.observe(10011.0,now)
        assertEquals(first.remainingKm!!-11,reopened.assess(p,"[]").remainingKm!!,0.0)
        reopened.observe(9999.0,now+1,now+1)
        assertEquals(10011.0,reopened.frame().getDouble("currentKm"),0.0)
        assertEquals(ledger.frame().getString("enrolled"),reopened.frame().getString("enrolled"))
    }
    @Test fun oldLedgerReplayCannotRollBackCurrentMileage() {
        val now=System.currentTimeMillis();val ledger=EngineOilLedger(context,id)
        ledger.observe(10000.0,now-1000);ledger.assess(profile(),"[]")
        val old=ledger.frame()
        ledger.observe(10020.0,now)
        ledger.importVehicle(old)
        assertEquals(10020.0,ledger.frame().getDouble("currentKm"),0.0)
        val newer=ledger.frame().put("currentKm",10030).put("meterAt",now+1)
        ledger.importVehicle(ledger.frame().put("enrolled",LocalDate.now(CARE_ZONE).minusDays(1).toString()))
        ledger.importVehicle(newer)
        assertEquals(10030.0,EngineOilLedger(context,id).frame().getDouble("currentKm"),0.0)
        ledger.importVehicle(JSONObject(newer.toString()).put("currentKm",9999).put("meterAt",now+2))
        assertEquals(10030.0,ledger.frame().getDouble("currentKm"),0.0)
        assertNull(ledger.assess(profile(),"[]").percent)
    }
    @Test fun registrationsHaveIndependentAnchors() {
        val now=System.currentTimeMillis();val a=EngineOilLedger(context,id);val b=EngineOilLedger(context,"other-$id")
        a.observe(10000.0,now);a.assess(profile(),"[]")
        b.observe(50000.0,now);b.assess(profile().put("vehicleId","other-$id"),"[]")
        assertEquals(10000.0,a.frame().getDouble("anchorKm"),0.0)
        assertEquals(50000.0,b.frame().getDouble("anchorKm"),0.0)
    }
    @Test fun manualReplacementReplayCreatesOneRecordAndOneCompletion()=runBlocking {
        val now=System.currentTimeMillis();val p=profile()
        val journal=CareJournal(context,id)
        journal.saveAuthority(CareAuthority(id,1,p.toString(),"[]"))
        val ledger=EngineOilLedger(context,id)
        ledger.observe(15000.0,now);ledger.assess(p,"[]")
        val record=JSONObject().put("id","oil-record").put("kind","maintenance").put("maintenanceType","ENGINE_OIL")
            .put("date",LocalDate.now(CARE_ZONE).toString()).put("title","엔진오일 교체").put("detail","")
        val records=JSONArray().put(record)
        CareExchange.editPhone(context,id,p,records)
        CareExchange.editPhone(context,id,p,JSONArray(journal.authority()!!.records))
        val actual=JSONArray(journal.authority()!!.records)
        assertEquals(1,actual.length())
        assertEquals(15000.0,actual.getJSONObject(0).getDouble("odometerKm"),0.0)
        assertEquals(100.0,ledger.assess(p,actual.toString()).percent!!,0.0)
        NotificationHistory(context).use {h ->assertEquals(1,h.recent(id).count {it.bannerKey=="OIL_DONE"})}
        CareExchange.editPhone(context,id,p,JSONArray().put(JSONObject(actual.getJSONObject(0).toString()).put("deleted",true).put("cancelled",true)))
        assertNull(ledger.assess(p,journal.authority()!!.records).percent)
        CareExchange.editPhone(context,id,p,actual)
        assertTrue(JSONArray(journal.authority()!!.records).getJSONObject(0).getBoolean("deleted"))
    }
    private fun oilVisit():EngineOilVisit {
        val now=System.currentTimeMillis()
        val site=WashSite("repair/fixture",37.0,127.0,listOf(WashPoint(36.999,126.999),WashPoint(36.999,127.001),
            WashPoint(37.001,127.001),WashPoint(37.001,126.999),WashPoint(36.999,126.999)))
        fun fix(at:Long,lat:Double=37.0,speed:Double=0.0)=FacilityFix(lat,127.0,at,at,accuracyM=5.0,
            speedMps=speed,speedAt=at,gear=if(speed>0) "D" else "P",gearAt=at)
        return EngineOilVisit(id,site,fix(now-1_820_000),fix(now-20_000),fix(now-10_000,37.002,4.0),fix(now-1000,37.002),15000.0)
    }
    @Test fun oilVisitIsAppliedOnceAndNoAnswerKeepsEstimateButNoRejectsReplay()=runBlocking {
        val p=profile();val journal=CareJournal(context,id)
        journal.saveAuthority(CareAuthority(id,1,p.toString(),"[]"))
        val visit=oilVisit()
        assertTrue(CareExchange.detectedOil(context,visit))
        assertFalse(CareExchange.detectedOil(context,visit))
        val records=JSONArray(journal.authority()!!.records)
        assertEquals(1,records.length());assertEquals("ENGINE_OIL",records.getJSONObject(0).getString("maintenanceType"))
        assertEquals("EstimatedOilVisit",records.getJSONObject(0).getString("source"))
        assertTrue(journal.pendingWashConfirmation()!!.oil)
        assertNotNull(CareJournal(context,id).pendingWashConfirmation()) // no answer/reopen preserves applied record
        assertTrue(CareExchange.cancel(context,id,visit.id))
        assertTrue(JSONArray(journal.authority()!!.records).getJSONObject(0).getBoolean("cancelled"))
        assertFalse(CareExchange.detectedOil(context,visit))
        CareExchange.editPhone(context,id,p,records)
        assertTrue(JSONArray(journal.authority()!!.records).getJSONObject(0).getBoolean("cancelled"))
    }
    @Test fun offOnStateSurvivesReopeningAndDuplicatesDoNotResetOffTime() {
        val visit=oilVisit();val store=EngineOilVisitStore(context,id)
        store.power(VerifiedVehiclePower.OFF,visit.off,listOf(visit.site))
        store.power(VerifiedVehiclePower.OFF,visit.off.copy(at=visit.off.at+1000,receivedAt=visit.off.at+1000),listOf(visit.site))
        val reopened=EngineOilVisitStore(context,id)
        reopened.power(VerifiedVehiclePower.ON,visit.on!!,listOf(visit.site))
        assertTrue(reopened.observe(visit.exit!!,visit.odometerKm))
        assertTrue(reopened.observe(visit.stop!!,null))
        val saved=EngineOilVisit.from(EngineOilVisitStore(context,id).frame().getJSONObject(0))
        assertEquals(visit.off.at,saved.off.at)
        assertEquals(visit.id,saved.id)
        assertTrue(EngineOilVisitPolicy.complete(saved,System.currentTimeMillis()))
    }
    @Test fun onlyPositionsOrBluetoothGapDoNotCreateOilVisit() {
        val v=oilVisit();val store=EngineOilVisitStore(context,id)
        assertFalse(store.observe(v.off,10000.0));assertFalse(store.observe(v.on!!,10000.0))
        assertFalse(store.observe(v.exit!!,15000.0));assertFalse(store.observe(v.stop!!,15000.0))
        assertEquals(0,store.frame().length())
    }

    @Test fun recentKnownReplacementDoesNotBecomeAnotherEstimate()=runBlocking {
        val p=profile().put("oilEstimate","약 1개월 전")
        val journal=CareJournal(context,id);journal.saveAuthority(CareAuthority(id,1,p.toString(),"[]"))
        assertFalse(CareExchange.detectedOil(context,oilVisit()))
        assertEquals(0,JSONArray(journal.authority()!!.records).length())
    }
    @Test fun overdueKnownReplacementAllowsEstimate()=runBlocking {
        val p=profile().put("oilEstimate","6개월 이상 전")
        val journal=CareJournal(context,id);journal.saveAuthority(CareAuthority(id,1,p.toString(),"[]"))
        assertTrue(CareExchange.detectedOil(context,oilVisit()))
    }
    @Test fun electricVehicleNeverGetsOilEstimate()=runBlocking {
        val p=profile().put("model","현대 아이오닉 5").put("fuel","전기차")
        val journal=CareJournal(context,id);journal.saveAuthority(CareAuthority(id,1,p.toString(),"[]"))
        assertFalse(CareExchange.detectedOil(context,oilVisit()))
    }

    @Test fun sameShopResumeSurvivesReopenThenExitAndStopCreatesCancelableEstimate()=runBlocking {
        val v=oilVisit();val sites=listOf(v.site);val store=EngineOilVisitStore(context,id)
        store.observe(v.off.copy(accuracyM=null),null,sites)
        val loading=v.off.copy(at=v.off.at+60_000,receivedAt=v.off.at+60_000,speedMps=null,speedAt=null,gear=null,gearAt=null)
        store.observe(loading,null,sites)
        val reopened=EngineOilVisitStore(context,id)
        reopened.observe(v.on!!.copy(accuracyM=null),null,sites)
        assertEquals(0,reopened.frame().length()) // Still inside; resume alone never records a replacement.
        reopened.observe(v.exit!!,v.odometerKm,sites)
        assertEquals(0,reopened.frame().length())
        reopened.observe(v.stop!!,null,sites)
        val saved=EngineOilVisit.from(reopened.frame().getJSONObject(0))
        assertEquals(OilVisitEvidence.SAME_PLACE_RECEIVING_GAP,saved.evidence)
        assertEquals(v.off.at,saved.off.at)
        val journal=CareJournal(context,id);journal.saveAuthority(CareAuthority(id,1,profile().toString(),"[]"))
        assertTrue(CareExchange.detectedOil(context,saved));assertFalse(CareExchange.detectedOil(context,saved))
        val record=JSONArray(journal.authority()!!.records).getJSONObject(0)
        assertTrue(record.getString("detail").contains("시동 확인 아님"))
        assertTrue(CareJournal(context,id).pendingWashConfirmation()!!.oil)
        assertTrue(CareExchange.cancel(context,id,saved.id));assertFalse(CareExchange.detectedOil(context,saved))
    }
    @Test fun continuouslyReceivedParkingNeverBecomesAReceivingGap() {
        val v=oilVisit();val store=EngineOilVisitStore(context,id);val sites=listOf(v.site)
        for(at in v.off.at..v.on!!.at step 10_000L) {
            store.observe(v.off.copy(at=at,receivedAt=at,speedAt=at,gearAt=at),null,sites)
        }
        store.observe(v.exit!!,v.odometerKm,sites);store.observe(v.stop!!,null,sites)
        assertEquals(0,store.frame().length())
    }
    @Test fun movingOutsideDifferentShopAndPhoneResumeCannotCompleteGap() {
        val v=oilVisit();val sites=listOf(v.site)
        for(mode in listOf("moving","outside","different","phone","short","far","overlap")) {
            val store=EngineOilVisitStore(context,id);store.clear();store.observe(v.off,null,sites)
            when(mode) {
                "moving" -> {val at=v.off.at+60_000;store.observe(v.off.copy(at=at,receivedAt=at,speedAt=at,gearAt=at,speedMps=2.0,gear="D"),null,sites);store.observe(v.on!!,null,sites)}
                "outside" -> {val at=v.off.at+60_000;store.observe(v.off.copy(at=at,receivedAt=at,speedAt=at,gearAt=at,latitude=37.002),null,sites);store.observe(v.on!!,null,sites)}
                "different" -> store.observe(v.on!!,null,listOf(v.site.copy(id="repair/other")))
                "phone" -> store.observe(v.on!!.copy(phone=true),null,sites)
                "short" -> store.observe(v.on!!.copy(at=v.on.at-1000,receivedAt=v.on.receivedAt-1000),null,sites)
                "far" -> store.observe(v.on!!.copy(latitude=37.0003),null,sites)
                "overlap" -> {val at=v.off.at+60_000;store.observe(v.off.copy(at=at,receivedAt=at,speedAt=at,gearAt=at),null,sites+v.site.copy(id="repair/overlap"));store.observe(v.on!!,null,sites)}
            }
            store.observe(v.exit!!,v.odometerKm,sites);store.observe(v.stop!!,null,sites)
            assertEquals(mode,0,store.frame().length())
        }
    }

    @Test fun returningInsideBeforeNextStopInvalidatesThePreviousExit() {
        val v=oilVisit();val sites=listOf(v.site);val store=EngineOilVisitStore(context,id)
        store.observe(v.off,null,sites);store.observe(v.on!!,null,sites);store.observe(v.exit!!,v.odometerKm,sites)
        val at=v.exit.at+1000
        store.observe(v.off.copy(at=at,receivedAt=at,speedAt=at,gearAt=at,speedMps=1.0,gear="D"),null,sites)
        store.observe(v.stop!!,null,sites)
        assertEquals(0,store.frame().length())
    }

}
