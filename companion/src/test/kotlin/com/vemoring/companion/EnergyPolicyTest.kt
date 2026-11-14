package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EnergyPolicyTest {
    @Test fun hybridDoesNotUseSocOrMissingFuelAsZero() {
        assertEquals(false,EnergyPolicy.low(false,80,0,20))
        assertNull(EnergyPolicy.low(false,null,0,20))
        assertEquals(true,EnergyPolicy.low(false,0,80,20))
        assertEquals(true,EnergyPolicy.low(true,80,0,20))
    }
    @Test fun retryBudgetIsBounded() {
        assertEquals(listOf(1000L,3000L,10000L), (0..2).map {ConnectionRetry.delayAfterFailure(it)})
        assertNull(ConnectionRetry.delayAfterFailure(3))
    }
    @Test fun optionalManualTimeIsKoreanTimeAndFutureIsRejected() {
        val date=LocalDate.of(2026,11,12)
        val now=Instant.parse("2026-11-12T07:00:00Z")
        assertNull(washCompletion(date,"",now))
        assertNull(washCompletion(date,"25:00",now))
        assertNull(washCompletion(date,"16:01",now))
        assertEquals("2026-11-12T05:30:00Z",washCompletion(date,"14:30",now))
        assertEquals("14:30",manualWashTime("2026-11-12T05:30:00Z","Manual"))
        assertEquals("",manualWashTime("2026-11-12T05:30:00Z","Automatic"))
    }
}
