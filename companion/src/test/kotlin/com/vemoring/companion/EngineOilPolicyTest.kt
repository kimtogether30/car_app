package com.vemoring.companion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class EngineOilPolicyTest {
    private val enrolled=LocalDate.of(2026,11,15)
    private val rule=EngineOilRule("test-v1","현대 아반떼","휘발유",5000.0,6,"fixture")
    private fun ledger(date:LocalDate=enrolled)=JSONObject().put("enrolled",date.toString())
        .put("initialIds",JSONArray().put("old")).put("anchorKm",10000).put("currentKm",10500)
    private fun record(id:String="old",date:LocalDate=enrolled.minusDays(90),km:Double?=null)=JSONObject()
        .put("id",id).put("kind","maintenance").put("maintenanceType","ENGINE_OIL")
        .put("date",date.toString()).put("odometerKm",km)
    private fun assess(j:JSONObject=ledger(),r:JSONArray=JSONArray().put(record()),today:LocalDate=enrolled)=
        EngineOilCalculation.assess(j,rule,r,today)
    @Test fun initialAverageIsAddedOnceToActualIncrease() {
        val a=assess()
        assertEquals(2700.0,a.remainingKm!!,0.0) // 90*20+500=2300
        assertTrue(a.estimated)
        assertEquals(a,assess())
        assertEquals(3199.0,assess(ledger().put("currentKm",10001)).remainingKm!!,0.0)
    }
    @Test fun actualBaselineReplacesAverage() {
        val a=assess(r=JSONArray().put(record(km=9000.0)))
        assertEquals(3500.0,a.remainingKm!!,0.0)
        assertFalse(a.estimated)
    }
    @Test fun sixCalendarMonthsIsDueAtTheBoundary() {
        val old=enrolled.minusMonths(6)
        assertEquals(true,assess(r=JSONArray().put(record(date=old))).due)
        assertEquals(false,assess(r=JSONArray().put(record(date=old.plusDays(1)))).due)
        assertEquals(true,EngineOilPolicy.assess(rule,enrolled,10000.0,15000.0,enrolled).due)
    }
    @Test fun unknownHistoryNeverBecomesOneHundredPercent() {
        val a=assess(r=JSONArray())
        assertNull(a.percent); assertNull(a.due)
        val later=assess(r=JSONArray(),today=enrolled.plusMonths(6))
        assertEquals(true,later.due); assertNull(later.percent)
        assertTrue(later.reason.contains("설치 이후"))
    }
    @Test fun newDateOnlyReplacementDoesNotInventPastMileage() {
        val a=assess(r=JSONArray().put(record("new",enrolled)))
        assertNull(a.remainingKm);assertNull(a.percent);assertFalse(a.estimated)
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(enrolled,enrolled.plusMonths(6)),a.remainingDays)
    }
    @Test fun mileageWithoutDateOnlyProvidesDistanceGuidance() {
        val a=assess(ledger().put("initialKm",9000).put("initialIds",JSONArray()),JSONArray())
        assertEquals(3500.0,a.remainingKm!!,0.0)
        assertNull(a.percent);assertNull(a.remainingDays);assertNull(a.dueDate)
        assertNull(a.due)
        assertEquals(true,assess(ledger().put("initialKm",5000).put("initialIds",JSONArray()),JSONArray()).due)
    }
    @Test fun initialActualMileageNeverAlsoReceivesTwentyKmPerDay() {
        val a=assess(ledger().put("initialKm",9500))
        assertEquals(4000.0,a.remainingKm!!,0.0)
        assertFalse(a.estimated)
    }
    @Test fun confirmedReplacementWithMileageStartsAtOneHundred() {
        val a=assess(r=JSONArray().put(record("new",enrolled,10500.0)))
        assertEquals(100.0,a.percent!!,0.0)
        assertEquals(false,a.due)
    }
    @Test fun deletionFallsBackToPreviousValidOilRecord() {
        val records=JSONArray().put(record()).put(record("new",enrolled,10500.0).put("deleted",true))
        assertEquals(assess(),assess(r=records))
        records.getJSONObject(0).put("cancelled",true)
        assertNull(assess(r=records).percent)
    }
    @Test fun generalRepairNeverResetsTheCycle() {
        val records=JSONArray().put(record()).put(record("tire",enrolled).put("maintenanceType","TIRE_PRESSURE"))
        assertEquals(assess(),assess(r=records))
    }
    @Test fun calendarMonthsRespectEndOfMonth() {
        val date=LocalDate.of(2026,8,31)
        assertEquals(0L,EngineOilPolicy.assess(rule,date,null,null,LocalDate.of(2027,2,28)).remainingDays)
        assertEquals(true,EngineOilPolicy.assess(rule,date,null,null,LocalDate.of(2027,2,28)).due)
    }
    @Test fun badOrMissingMileageDoesNotTurnIntoZero() {
        val a=assess(ledger().put("currentKm",Double.NaN.toString()))
        assertNull(a.percent);assertNull(a.remainingKm)
        assertNull(assess(ledger().put("currentKm",9999)).remainingKm)
        assertNull(assess(ledger().also {it.remove("anchorKm")}).percent)
    }
    @Test fun suppliedCatalogCoversAllThirtySevenModelsAndExcludesElectricVariants() {
        val file=listOf(File("../docs/engine-oil/engine-oil-intervals.json"),File("docs/engine-oil/engine-oil-intervals.json")).first {it.exists()}
        val j=JSONObject(file.readText());val models=j.getJSONArray("models")
        assertEquals(37,models.length())
        repeat(models.length()) {n -> val m=models.getJSONObject(n);val variants=m.getJSONArray("catalogVariants")
            repeat(variants.length()) {i -> val name=m.getString("brand")+" "+variants.getString(i)
                val selected=EngineOilCatalog.select(j,name,"휘발유")
                if(m.getBoolean("oilApplicable") && !name.endsWith(" EV")) assertNotNull(name,selected) else assertNull(name,selected)
                assertNull(EngineOilCatalog.select(j,name,"전기차"))
            }
        }
        assertEquals(5000.0,EngineOilCatalog.select(j,"기아 K4","휘발유")!!.severeKm!!,0.0)
        assertNull(EngineOilCatalog.select(j,"미등록 차종","휘발유"))
    }
}
