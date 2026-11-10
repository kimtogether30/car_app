package com.vemoring.companion

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CarePolicyTest {
    private fun at(day: String) = LocalDate.parse(day).atStartOfDay(CARE_ZONE).toInstant()
    private val wash = WashBaseline("manual", LocalDate.parse("2026-11-01"))
    @Test fun manualStartsNextMidnightAndUnknownStaysUnknown() {
        assertNull(WashPolicy.score(emptyList(), emptyList(), at("2026-11-09")).value)
        assertEquals(100, WashPolicy.score(listOf(wash), emptyList(), at("2026-11-02")).displayed)
        assertEquals(86, WashPolicy.score(listOf(wash), emptyList(), at("2026-11-09")).displayed)
    }
    @Test fun actualReplacesEstimateAndConfirmedParkingCostsNothing() {
        val start = at("2026-11-02"); val end = start.plusSeconds(86400)
        fun score(km: Double) = WashPolicy.score(listOf(wash), listOf(TravelInterval("trip", start, end, km)), end)
        assertEquals(98, WashPolicy.score(listOf(wash), emptyList(), end).displayed)
        assertEquals(97, score(30.0).displayed)
        assertEquals(100, score(0.0).displayed)
        assertEquals("manual", score(0.0).cycleId)
    }
    @Test fun rainAndSnowAndDustAreExclusiveWithDailyDustLimit() {
        val s = at("2026-11-02"); val e = s.plusSeconds(3600)
        fun w(rain: Double = 0.0, snow: Double = 0.0) = HourWeather(s, rain, 0.0, snow, 180.0, 90.0, e, "v1")
        fun score(km: Double, weather: HourWeather?) = WashPolicy.score(listOf(wash),
            listOf(TravelInterval("trip", s, e, km, located = true, weather = weather)), e)
        assertEquals(95.0, WashPolicy.score(listOf(wash), listOf(
            TravelInterval("rain", s, s.plusSeconds(1200), 10.0, located = true, weather = w(.1)),
            TravelInterval("dry", s.plusSeconds(1200), e, 20.0)), e).value!!, .0001)
        assertEquals(85.0, score(30.0, w(.1, .1)).value!!, .0001)
        assertEquals(88.0, score(100.0, w()).value!!, .0001)
        assertEquals(97.0, score(30.0, w(.1).copy(obtainedAt = e.plusSeconds(72*3600+1))).value!!, .0001)
    }
    @Test fun cancellationAndRetransmissionDoNotResurrectFirstWash() {
        val s = at("2026-11-02"); val records = WashRecords()
        assertTrue(records.accept(DetectedWash("visit", s)))
        assertFalse(records.accept(DetectedWash("visit", s)))
        assertTrue(records.cancel("visit", s.plusSeconds(1)))
        val restored = WashRecords(records.values)
        assertFalse(restored.accept(DetectedWash("visit", s)))
        assertNull(WashPolicy.score(restored.values, emptyList(), s.plusSeconds(5)).value)
    }
    private fun observation(second: Long, zone: ZoneEvidence = ZoneEvidence.Inside, gear: String = "N",
        motion: Boolean = true, source: EvidenceSource = EvidenceSource.Vehicle, accuracy: Double? = 2.0) =
        at("2026-11-02").plusSeconds(second).let { WashObservation(it, it, accuracy, zone, source,
            true, gear, motion, vehicleReconnected = source == EvidenceSource.Vehicle, facilityExit = zone == ZoneEvidence.Outside) }
    @Test fun fuelOnlyAndUnknownOrMissingLocationsNeverCreateWash() {
        val d = WashVisitDetector("fuel", WashFacility.Unknown)
        for (t in 0L..300L step 5) assertNull(d.observe(observation(t)))
        val missing = WashVisitDetector("missing", WashFacility.Stationary)
        for (t in 0L..300L step 5) assertNull(missing.observe(observation(t, accuracy = null)))
        val gap = WashVisitDetector("gap", WashFacility.Stationary)
        gap.observe(observation(0)); gap.observe(observation(10))
        for (t in 200L..235L step 5) assertNull(gap.observe(observation(t, ZoneEvidence.Outside, "D")))
    }
    @Test fun conveyorAndStationaryRequireFullDwellAndActualExitOnce() {
        for (facility in listOf(WashFacility.Conveyor, WashFacility.Stationary)) {
            val d = WashVisitDetector(facility.name, facility)
            for (t in 0L..195L step 5) assertNull(d.observe(observation(t)))
            for (t in 200L..225L step 5) assertNull(d.observe(observation(t, ZoneEvidence.Outside, "D")))
            assertNotNull(d.observe(observation(230, ZoneEvidence.Outside, "D")))
            assertNull(d.observe(observation(235, ZoneEvidence.Outside, "D")))
        }
    }
    @Test fun phoneAloneAndGpsJitterCannotFinishVisit() {
        val d = WashVisitDetector("phone", WashFacility.Conveyor)
        for (t in 0L..200L step 5) d.observe(observation(t, motion = false))
        for (t in 205L..245L step 5) assertNull(d.observe(observation(t, ZoneEvidence.Outside, "D", false)))
        val phone = WashVisitDetector("phone-only", WashFacility.Stationary)
        for (t in 0L..200L step 5) assertNull(phone.observe(observation(t, source = EvidenceSource.Phone)))
    }
    @Test fun elapsedTimeIsNotCountedAcrossReceptionGapsOrBeforeDwell() {
        val d = WashVisitDetector("short", WashFacility.Stationary)
        for (t in 0L..170L step 5) d.observe(observation(t))
        for (t in 175L..210L step 5) assertNull(d.observe(observation(t, ZoneEvidence.Outside, "D")))
        val gap = WashVisitDetector("gap", WashFacility.Stationary)
        for (t in 0L..100L step 5) gap.observe(observation(t))
        for (t in 200L..275L step 5) gap.observe(observation(t))
        for (t in 280L..320L step 5) assertNull(gap.observe(observation(t, ZoneEvidence.Outside, "D")))
    }
    @Test fun lateWeatherReplacesSameIntervalWithoutRepeatedLossAndOldRecordsRecompute() {
        val s = at("2026-11-02"); val e = s.plusSeconds(3600)
        val part = TravelInterval("travel", s, e, 30.0, located = true)
        val wet = part.copy(weather = HourWeather(s, .1, 0.0, 0.0, null, null, e.plusSeconds(3600), "v2"))
        assertEquals(97, WashPolicy.score(listOf(wash), listOf(part), e).displayed)
        assertEquals(97, WashPolicy.score(listOf(wash), listOf(wet), e).displayed) // Future-dated weather is not usable yet.
        assertEquals(91, WashPolicy.score(listOf(wash), listOf(wet), e.plusSeconds(3600)).displayed)
        assertEquals(91, WashPolicy.score(listOf(wash), listOf(wet), e.plusSeconds(3600)).displayed)
        val recent = WashBaseline("recent", s.atZone(CARE_ZONE).toLocalDate(), e)
        assertEquals(100, WashPolicy.score(listOf(wash, recent), listOf(wet), e).displayed)
        assertEquals(91, WashPolicy.score(listOf(wash, recent.copy(cancelled = true)), listOf(wet), e.plusSeconds(3600)).displayed)
        assertEquals(38, WashPolicy.score(listOf(wash), emptyList(), e.plusSeconds(31*86400L)).displayed)
        assertTrue(WashPolicy.score(listOf(wash), listOf(wet), e.plusSeconds(31*86400L)).value!! < 40)
    }
    @Test fun canonicalVersionsRejectConflictsAndDeduplicateRequestIds() {
        val original = CareAuthority("vehicle", 5, "{}", "[]")
        assertEquals(original, original.request("stale", 4, "{\"washAlert\":20}", "[]"))
        val next = original.request("accepted", 5, "{\"washAlert\":20}", "[]")
        assertEquals(6L, next.version)
        assertEquals(next, next.request("accepted", 6, "{}", "[]"))
        assertEquals(next, CareAuthority.from(next.json()))
    }
    @Test fun parkingCompressionPreservesScoresAcrossArbitraryWashBoundaries() {
        val s = at("2026-11-02")
        val parts = (0L until 100).map { n -> TravelInterval("$n", s.plusSeconds(n*5), s.plusSeconds((n+1)*5), 0.0, stationary = true) }
        val compressed = WashPolicy.coalesceParking(parts)
        assertEquals(1, compressed.size)
        for (n in 0L..490 step 7) {
            val baseline = WashBaseline("auto", LocalDate.parse("2026-11-02"), s.plusSeconds(n), "visit")
            assertEquals(WashPolicy.score(listOf(baseline), parts, s.plusSeconds(600)).value!!,
                WashPolicy.score(listOf(baseline), compressed, s.plusSeconds(600)).value!!, .000001)
        }
    }
    @Test fun delayedAutomaticRecordHasSevenDaysFromApplicationToCancel() {
        val completed = at("2026-11-02")
        val applied = completed.plusSeconds(10*86400L)
        val records = WashRecords()
        records.accept(DetectedWash("late", completed), appliedAt = applied)
        assertTrue(records.cancel("late", applied.plusSeconds(6*86400L)))
        val expired = WashRecords()
        expired.accept(DetectedWash("expired", completed), appliedAt = applied)
        assertFalse(expired.cancel("expired", applied.plusSeconds(7*86400L+1)))
    }
    @Test fun parkedWashQualifiesWithoutNeutralAndConveyorStillNeedsItsOwnPattern() {
        val parked = WashVisitDetector("park-wash", WashFacility.Stationary)
        val conveyor = WashVisitDetector("park-only", WashFacility.Conveyor)
        for(t in 0L..195L step 5) {
            val sample = observation(t, gear = "P", motion = false)
            assertNull(parked.observe(sample)); assertNull(conveyor.observe(sample))
        }
        for(t in 200L..225L step 5) {
            val exit = observation(t, ZoneEvidence.Outside, "D")
            assertNull(parked.observe(exit)); assertNull(conveyor.observe(exit))
        }
        assertNotNull(parked.observe(observation(230, ZoneEvidence.Outside, "D")))
        assertNull(conveyor.observe(observation(230, ZoneEvidence.Outside, "D")))
    }
    @Test fun facilityCertaintyChangesBannerEvidenceOnlyAfterTheFullWashPattern() {
        for(location in WashLocationEvidence.entries) {
            for(facility in listOf(WashFacility.Stationary,WashFacility.Conveyor)) {
                val d=WashVisitDetector("$location-$facility",facility,locationEvidence=location)
                for(t in 0L..195L step 5) assertNull(d.observe(observation(t,
                    gear=if(facility==WashFacility.Stationary) "P" else "N",motion=facility==WashFacility.Conveyor)))
                for(t in 200L..225L step 5) assertNull(d.observe(observation(t,ZoneEvidence.Outside,"D")))
                val result=requireNotNull(d.observe(observation(230,ZoneEvidence.Outside,"D")))
                assertEquals(if(location==WashLocationEvidence.SharedOrUncertain) WashCompletionEvidence.EstimatedVisit
                    else WashCompletionEvidence.ClearFacilityPattern,result.evidence)
            }
            val short=WashVisitDetector("short-$location",WashFacility.Stationary,locationEvidence=location)
            for(t in 0L..100L step 5) short.observe(observation(t,gear="P",motion=false))
            for(t in 105L..145L step 5) assertNull(short.observe(observation(t,ZoneEvidence.Outside,"D")))
        }
    }
    @Test fun pumpAreaAndLongGpsUncertaintyDoNotMeetWashConditions() {
        val pump = WashVisitDetector("pump-only", WashFacility.Stationary)
        for(t in 0L..300L step 5) assertNull(pump.observe(observation(t, ZoneEvidence.Outside, "P", false)))
        val lost = WashVisitDetector("lost", WashFacility.Stationary)
        for(t in 0L..195L step 5) lost.observe(observation(t, gear = "P", motion = false))
        for(t in 200L..300L step 5) assertNull(lost.observe(observation(t, accuracy = null)))
        for(t in 305L..345L step 5) assertNull(lost.observe(observation(t, ZoneEvidence.Outside, "D")))
    }
    @Test fun movementBreaksStationaryDwellInsteadOfAddingDisconnectedStops() {
        val d = WashVisitDetector("two-short-stops", WashFacility.Stationary)
        for(t in 0L..100L step 5) d.observe(observation(t, gear = "P", motion = false))
        d.observe(observation(105, gear = "D").copy(stationary = false))
        for(t in 110L..195L step 5) d.observe(observation(t, gear = "P", motion = false))
        for(t in 200L..240L step 5) assertNull(d.observe(observation(t, ZoneEvidence.Outside, "D")))
    }
}
