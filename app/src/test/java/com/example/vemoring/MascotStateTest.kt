package com.example.vemoring

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotStateTest {
    @Test fun unknownDataKeepsDefault() {
        assertEquals(MascotMood.NORMAL, CareSignals().mood())
        assertEquals(MascotMood.NORMAL, CareSignals(false, null, false).mood())
    }

    @Test fun allEightKnownCombinationsFollowApprovedPriority() {
        val expected = listOf(MascotMood.NORMAL, MascotMood.FUEL, MascotMood.WASH,
            MascotMood.ANGRY, MascotMood.OIL, MascotMood.ANGRY, MascotMood.ANGRY, MascotMood.EXHAUSTED)
        for (mask in 0..7) {
            assertEquals("Combination $mask", expected[mask],
                CareSignals(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0).mood())
        }
    }
}
