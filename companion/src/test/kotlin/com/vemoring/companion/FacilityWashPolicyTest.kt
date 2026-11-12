package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test

class FacilityWashPolicyTest {
    private val site=WashSite("osm/wash",37.0,127.0)
    private val base=1_800_000_000_000L
    private fun f(second:Long,out:Boolean=false,phone:Boolean=false)=FacilityFix(37.0+if(out) .002 else 0.0,127.0,
        base+second*1000,base+second*1000,phone,if(phone) 5.0 else null)
    private fun visit(fixes:List<FacilityFix>)=FacilityVisit("test",site,fixes)
    private fun exit()=(210L..240L step 10).map {f(it,true)}
    @Test fun irregularIntervalsCountElapsedTimeAndRequireVehicleExit() {
        val fixes=listOf(0L,8,17,30,48,65,83,101,120,145,170,190).map {f(it)}
        assertNull(FacilityWashPolicy.evaluate(visit(fixes)).wash)
        val result=FacilityWashPolicy.evaluate(visit(fixes+exit()))
        assertEquals(190_000L,result.dwellMillis);assertNotNull(result.wash)
        assertEquals(WashCompletionEvidence.EstimatedVisit,result.wash!!.evidence)
    }
    @Test fun longGapAndDuplicateCachedFixesNeverInventDwell() {
        assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0),f(180))+exit())).wash)
        assertEquals(0L,FacilityWashPolicy.evaluate(visit(List(40){f(0)})).dwellMillis)
        assertNull(FacilityWashPolicy.evaluate(visit((0L..60L step 10).map {f(it)}+exit())).wash)
    }
    @Test fun phoneCanBridgeShutdownButCannotCompleteWithoutVehicleReturn() {
        val bridge=listOf(f(0),f(10))+(30L..190L step 10).map {f(it,phone=true)}
        val walkAway=(210L..240L step 10).map {f(it,true,true)}
        assertNull(FacilityWashPolicy.evaluate(visit(bridge+walkAway)).wash)
        assertNotNull(FacilityWashPolicy.evaluate(visit(bridge+exit())).wash)
        val overlappingPhone=(211L..241L step 5).map {f(it,true,true)}
        assertNotNull(FacilityWashPolicy.evaluate(visit(bridge+exit()+overlappingPhone)).wash)
        assertNull(FacilityWashPolicy.evaluate(visit((0L..190L step 10).map {f(it,phone=true)}+exit())).wash)
    }
    @Test fun staleInaccurateFutureAndUnknownPhoneAccuracyDoNotCount() {
        assertFalse(f(0).copy(receivedAt=base+16_000).valid())
        assertFalse(f(0).copy(receivedAt=base-1).valid())
        assertFalse(f(0,phone=true).copy(accuracyM=null).valid())
        assertFalse(f(0,phone=true).copy(accuracyM=80.0).valid())
        assertTrue(f(0).valid())
    }
    @Test fun boundaryJitterDoesNotCauseExitAndAmbiguousSitesDoNotStartVisit() {
        val inside=(0L..190L step 10).map {f(it)}
        assertNull(FacilityWashPolicy.evaluate(visit(inside+f(200,true)+f(210))).wash)
        assertEquals(site,FacilityWashPolicy.candidate(listOf(site),f(0)))
        assertNull(FacilityWashPolicy.candidate(listOf(site,site.copy(id="second")),f(0)))
        assertNull(FacilityWashPolicy.candidate(listOf(site),f(0,phone=true)))
    }
    @Test fun passingBrieflyThroughSiteEndsVisitWithoutBlockingNextFacility() {
        val result=FacilityWashPolicy.evaluate(visit(listOf(f(0))+exit()))
        assertTrue(result.exited);assertNull(result.wash)
    }
}
