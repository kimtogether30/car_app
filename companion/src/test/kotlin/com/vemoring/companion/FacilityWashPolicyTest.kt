package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test

class FacilityWashPolicyTest {
    private val boundary=listOf(WashPoint(36.9995,126.9995),WashPoint(37.0005,126.9995),
        WashPoint(37.0005,127.0005),WashPoint(36.9995,127.0005),WashPoint(36.9995,126.9995))
    private val site=WashSite("osm/wash",37.0,127.0,boundary)
    private val base=1_800_000_000_000L
    private fun f(t:Long,out:Boolean=false,speed:Double=if(out) 2.0 else 0.0,gear:String=if(out) "D" else "P")=
        FacilityFix(37.0+if(out) .002 else 0.0,127.0,base+t*1000,base+t*1000,
            speedMps=speed,speedAt=base+t*1000,gear=gear,gearAt=base+t*1000)
    private fun visit(fixes:List<FacilityFix>,s:WashSite=site)=FacilityVisit("test",s,fixes)
    private fun inside()=(0L..180L step 10).map {f(it)}
    private fun exit()=(190L..220L step 10).map {f(it,true)}
    @Test fun stationaryNeedsFullObservedDwellAndMovingVehicleExit() {
        assertNull(FacilityWashPolicy.evaluate(visit(inside())).wash)
        val result=FacilityWashPolicy.evaluate(visit(inside()+exit()))
        assertNotNull(result.wash);assertEquals(180_000L,result.dwellMillis)
        assertEquals(WashCompletionEvidence.EstimatedVisit,result.wash!!.evidence)
        assertNull(FacilityWashPolicy.evaluate(visit((0L..170L step 10).map {f(it)}+(180L..210L step 10).map {f(it,true)})).wash)
    }
    @Test fun conveyorCountsMovingDwellButNeedsNeutralMotionThenDrive() {
        val moving=(0L..180L step 10).map {f(it,speed=.5,gear="N")}
        assertNotNull(FacilityWashPolicy.evaluate(visit(moving+exit(),site.copy(facility=WashFacility.Conveyor))).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(moving+exit().map {it.copy(gear="N")})).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(moving.map {it.copy(gear="D")}+exit())).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(moving.map {it.copy(speedMps=0.0)}+exit(),site.copy(facility=WashFacility.Conveyor))).wash)
    }
    @Test fun neutralSingleSpikeAndNearZeroNoiseCannotProveConveyorMotion() {
        val noise=(0L..180L step 10).map {f(it,speed=.001,gear="N")}
        assertNull(FacilityWashPolicy.evaluate(visit(noise+exit())).wash)
        val spike=(0L..180L step 10).map {f(it,speed=if(it==90L) 1.0 else 0.0,gear="N")}
        assertNull(FacilityWashPolicy.evaluate(visit(spike+exit(),site.copy(facility=WashFacility.Conveyor))).wash)
    }
    @Test fun stationaryMotionBreaksDwellButActualReverseExitAfterFullDwellQualifies() {
        val moving=inside().map {if(it.at==base+90_000) it.copy(speedMps=.001) else it}
        assertNull(FacilityWashPolicy.evaluate(visit(moving+exit())).wash)
        assertNotNull(FacilityWashPolicy.evaluate(visit(inside()+exit().map {it.copy(speedMps=-2.0,gear="R")})).wash)
    }
    @Test fun missingStaleOrMisalignedSpeedCannotInventStoppedTime() {
        for(bad in listOf<(FacilityFix)->FacilityFix>({it.copy(speedMps=null)},
            {it.copy(speedAt=it.at-5001)},{it.copy(speedAt=it.at+1)},
            {it.copy(at=it.at-6000)},{it.copy(speedMps=Double.NaN)})) {
            assertNull(FacilityWashPolicy.evaluate(visit(inside().map(bad)+exit())).wash)
        }
    }
    @Test fun staleGearAndNeutralWithoutMotionDoNotProveConveyor() {
        val moving=(0L..180L step 10).map {f(it,speed=.5,gear="N").copy(gearAt=base-40_000)}
        assertNull(FacilityWashPolicy.evaluate(visit(moving+exit())).wash)
    }
    @Test fun longGapsAndDuplicateCachedFixesNeverFillDwell() {
        assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0))+exit())).wash)
        assertEquals(0L,FacilityWashPolicy.evaluate(visit(List(40){f(0)})).dwellMillis)
        val gap=inside().filter {it.at !in base+60_000..base+100_000}.map {if(it.at>=base+110_000) it.copy(latitude=37.0003) else it}
        assertNull(FacilityWashPolicy.evaluate(visit(gap+exit())).wash)
    }
    @Test fun samePlaceResumeCountsWallTimeForAutomaticAndSelfServiceButWaitsForExit() {
        val fixes=listOf(f(0),f(10),f(180))
        for(mode in listOf(WashFacility.Unknown,WashFacility.Stationary,WashFacility.Conveyor)) {
            assertNull(FacilityWashPolicy.evaluate(visit(fixes,site.copy(facility=mode))).wash)
            val result=FacilityWashPolicy.evaluate(visit(fixes+exit(),site.copy(facility=mode)))
            assertNotNull(result.wash);assertEquals(180_000L,result.dwellMillis)
            assertEquals(WashCompletionEvidence.EstimatedVisit,result.wash!!.evidence)
        }
    }
    @Test fun resumeRejectsDifferentPositionOutsideAreaStaleSignalsAndShortDwell() {
        for(restart in listOf(f(180).copy(latitude=37.0003),f(180,true),
            f(180).copy(speedAt=base),f(180).copy(gearAt=base),f(180,speed=.5))) {
            assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0),restart)+exit())).wash)
        }
        assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0),f(120))+(130L..160L step 10).map {f(it,true)})).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0),f(180).copy(phone=true,accuracyM=5.0))+exit())).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0),f(7201)))).wash)
    }
    @Test fun reconnectLoadingDoesNotDestroyPersistedSamePlaceAnchor() {
        val fixes=listOf(f(0),f(10),f(170).copy(speedMps=null,gear=null),f(180))
        assertNotNull(FacilityWashPolicy.evaluate(visit(fixes+exit())).wash)
        val departed=listOf(f(0),f(100,true),f(180))
        assertNull(FacilityWashPolicy.evaluate(visit(departed+exit())).wash)
    }
    @Test fun freshZeroInDriveAfterRestartIsStillStationaryNotAnInventedParkRequirement() {
        val fixes=listOf(f(0,gear="D"),f(10,gear="D"),f(180,gear="D"))
        assertNotNull(FacilityWashPolicy.evaluate(visit(fixes+exit())).wash)
    }
    @Test fun mappedWashAreaIsRequiredAndFuelOrWaitingOutsideDoesNotCount() {
        assertNull(FacilityWashPolicy.candidate(listOf(site.copy(boundary=emptyList())),f(0)))
        assertNull(FacilityWashPolicy.evaluate(visit(inside()+exit(),site.copy(boundary=emptyList()))).wash)
        val fuel=(0L..180L step 10).map {f(it).copy(latitude=37.001)}
        assertNull(FacilityWashPolicy.evaluate(visit(fuel+exit())).wash)
        assertNull(FacilityWashPolicy.candidate(listOf(site,site.copy(id="second")),f(0)))
    }
    @Test fun boundaryJitterAndAccuracyUncertaintyNeverBecomeDwellOrExit() {
        val boundaryFix=f(190).copy(latitude=37.0005)
        assertEquals(ZoneEvidence.Uncertain,FacilityWashPolicy.zone(site,boundaryFix))
        assertNull(FacilityWashPolicy.evaluate(visit(inside()+listOf(boundaryFix,f(200)))).wash)
        assertEquals(ZoneEvidence.Uncertain,FacilityWashPolicy.zone(site,f(0).copy(accuracyM=50.0)))
    }
    @Test fun phoneCannotSupplyStationaryEvidenceOrCompleteVehicleVisit() {
        val phone=(20L..180L step 10).map {f(it).copy(phone=true,accuracyM=5.0)}
        assertNull(FacilityWashPolicy.evaluate(visit(listOf(f(0),f(10))+phone+exit())).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(inside()+exit().map {it.copy(phone=true,accuracyM=5.0)})).wash)
        assertNotNull(FacilityWashPolicy.evaluate(visit(inside()+phone+exit())).wash)
    }
    @Test fun briefPassThroughEndsVisitWithoutWashAndAllowsNextVisit() {
        val result=FacilityWashPolicy.evaluate(visit(listOf(f(0),f(10))+(20L..50L step 10).map {f(it,true)}))
        assertTrue(result.exited);assertNull(result.wash)
    }
    @Test fun completedStationaryDwellSurvivesDrivingAcrossWashAreaToExit() {
        val driveToGate=listOf(f(190,speed=1.0,gear="D"),f(200,speed=2.0,gear="D"))
        val outside=(210L..240L step 10).map {f(it,true)}
        assertNotNull(FacilityWashPolicy.evaluate(visit(inside()+driveToGate+outside)).wash)
    }
    @Test fun crossingBoundaryBufferAddsNoDwellButPreservesAlreadyCompletedPatterns() {
        val edge=(190L..220L step 10).map {f(it,speed=1.0,gear="D").copy(latitude=37.0005)}
        val outside=(230L..260L step 10).map {f(it,true)}
        assertNotNull(FacilityWashPolicy.evaluate(visit(inside()+edge+outside)).wash)
        val conveyor=(0L..180L step 10).map {f(it,speed=.5,gear="N")}
        assertNotNull(FacilityWashPolicy.evaluate(visit(conveyor+edge+outside)).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(inside().dropLast(1)+edge+outside)).wash)
        assertNull(FacilityWashPolicy.evaluate(visit(inside()+edge.map {it.copy(speedMps=null)}+outside)).wash)
    }
    @Test fun shortOutsideExcursionCannotJoinTwoIncompleteConveyorStays() {
        val before=(0L..90L step 10).map {f(it,speed=.5,gear="N")}
        val after=(110L..200L step 10).map {f(it,speed=.5,gear="N")}
        val outside=(210L..240L step 10).map {f(it,true)}
        assertNull(FacilityWashPolicy.evaluate(visit(before+f(100,true)+after+outside)).wash)
        val edge=f(105,speed=.5,gear="N").copy(latitude=37.0005)
        assertNull(FacilityWashPolicy.evaluate(visit(before+f(100,true)+edge+after+outside)).wash)
    }
}
