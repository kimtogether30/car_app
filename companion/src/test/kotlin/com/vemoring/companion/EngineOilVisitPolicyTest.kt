package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test

class EngineOilVisitPolicyTest {
    private val now=2_000_000_000L
    private val site=WashSite("repair/1",37.0,127.0,listOf(WashPoint(36.999,126.999),WashPoint(36.999,127.001),
        WashPoint(37.001,127.001),WashPoint(37.001,126.999),WashPoint(36.999,126.999)))
    private fun fix(at:Long,lat:Double=37.0,speed:Double=0.0)=FacilityFix(lat,127.0,at,at,accuracyM=5.0,
        speedMps=speed,speedAt=at,gear=if(speed>0) "D" else "P",gearAt=at)
    private fun visit()=EngineOilVisit("car",site,fix(now-1_820_000),fix(now-20_000),
        fix(now-10_000,37.002,4.0),fix(now-1000,37.002),15000.0)
    @Test fun exactThirtyMinutesAndExitThenStopQualify() {
        assertTrue(EngineOilVisitPolicy.complete(visit(),now))
        assertEquals(visit(),EngineOilVisit.from(visit().json()))
    }
    @Test fun twentyNineMinutesFiftyNineSecondsFails() {
        val v=visit();assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(at=v.off.at+1000,receivedAt=v.off.at+1000)),now))
    }
    @Test fun missingPowerExitOrStopNeverQualifies() {
        val v=visit()
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=null),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(exit=null),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(stop=null),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(at=v.off.at-1)),now))
    }
    @Test fun differentFacilityPhonePositionMissingAccuracyAndBoundaryAreRejected() {
        val v=visit()
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(latitude=38.0)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(phone=true)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(accuracyM=null)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(latitude=37.001)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(site=site.copy(boundary=emptyList())),now))
    }
    @Test fun staleStopOrUnconfirmedMotionCannotDeliverDelayedQuestion() {
        val v=visit()
        assertFalse(EngineOilVisitPolicy.complete(v,now+60_000))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(exit=v.exit!!.copy(gear="N")),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(stop=v.stop!!.copy(speedMps=1.0)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(stop=v.stop!!.copy(speedAt=now-100000)),now))
    }
    @Test fun receivingGapIsAnEstimateWithThirtyMinuteBoundaryAndSeparateSerialization() {
        val v=visit().copy(evidence=OilVisitEvidence.SAME_PLACE_RECEIVING_GAP)
        assertTrue(EngineOilVisitPolicy.complete(v,now))
        assertEquals(v,EngineOilVisit.from(v.json()))
        assertEquals("SAME_PLACE_RECEIVING_GAP",v.json().getString("powerSource"))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(at=v.off.at+1000,receivedAt=v.off.at+1000)),now))
    }
    @Test fun resumeNeedsBothVehicleStopsFreshGearAndWithinTwentyMetres() {
        val v=visit().copy(evidence=OilVisitEvidence.SAME_PLACE_RECEIVING_GAP)
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(latitude=37.0003)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(speedMps=1.0)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(gearAt=v.on.at-36_000)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(phone=true)),now))
    }
    @Test fun resumeDoesNotInventAccuracyAndCannotUseCachedOrReversedTime() {
        val v=visit().copy(evidence=OilVisitEvidence.SAME_PLACE_RECEIVING_GAP)
        assertTrue(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(accuracyM=null),on=v.on!!.copy(accuracyM=null)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(receivedAt=v.on.at+20_000)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(on=v.on!!.copy(receivedAt=v.off.receivedAt)),now))
        assertFalse(EngineOilVisitPolicy.complete(v.copy(off=v.off.copy(accuracyM=40.0)),now))
    }

}
