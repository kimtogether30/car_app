package com.example.vemoring

import org.junit.Assert.assertEquals
import org.junit.Test

class IconSignalRetentionTest {
    @Test fun unavailableDataDoesNotClearWarningsButConfirmedRecoveryDoes() {
        val warning = CareSignals(true, true, true)
        assertEquals(MascotMood.EXHAUSTED, retainKnownSignals(warning, CareSignals()).mood())
        assertEquals(MascotMood.ANGRY, retainKnownSignals(warning, CareSignals(lowFuel = false)).mood())
        assertEquals(MascotMood.NORMAL, retainKnownSignals(warning, CareSignals(false, false, false)).mood())
    }
}
