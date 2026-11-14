package com.example.vemoring.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneMoodTest {
    @Test fun sameRuleAsVehicleApp() {
        assertEquals("NORMAL", moodFor(fuel = false, wash = false, warning = false))
        assertEquals("FUEL", moodFor(fuel = true, wash = false, warning = false))
        assertEquals("WASH", moodFor(fuel = false, wash = true, warning = false))
        assertEquals("OIL", moodFor(fuel = false, wash = false, warning = true))
        assertEquals("ANGRY", moodFor(fuel = true, wash = true, warning = false))
        assertEquals("ANGRY", moodFor(fuel = false, wash = true, warning = true))
        assertEquals("EXHAUSTED", moodFor(fuel = true, wash = true, warning = true))
    }
    @Test fun recordsSavedBeforeRenameReadAsMaintenance() {
        assertEquals("엔진오일 교체", normalizeKind("정비"))
        assertEquals("세차", normalizeKind("세차"))
        assertEquals("주유", normalizeKind("주유"))
    }
}
