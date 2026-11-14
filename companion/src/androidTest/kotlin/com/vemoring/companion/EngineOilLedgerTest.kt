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
}
