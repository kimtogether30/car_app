package com.example.vemoring

import com.example.vemoring.vehicle.*
import org.junit.Assert.*
import org.junit.Test

class VehicleIdentityTest {
    @Test fun everyPartialCombinationAsksOnlyMissingFields() {
        for (mask in 0..7) {
            val info = ConfirmedVehicleInfo(if (mask and 1 != 0) "현대" else null,
                if (mask and 2 != 0) "코나" else null, if (mask and 4 != 0) "전기차" else null)
            val expected = (0..2).filter { mask and (1 shl it) == 0 } + listOf(3, 4, 5)
            assertEquals(expected, setupSteps(info))
        }
    }
    @Test fun unsupportedDemoAndErrorsCannotAutoFill() {
        val info = ConfirmedVehicleInfo("현대", "코나", "전기차")
        for (state in ReadingState.entries.filter { it != ReadingState.Available }) {
            assertEquals(listOf(0,1,2,3,4,5), setupSteps(VehicleIdentityReading(state, info).confirmed()))
        }
    }
    @Test fun blanksAndUnknownFuelAreStillQuestions() {
        val info = VehicleIdentityReading(ReadingState.Available, ConfirmedVehicleInfo("  현대 ", " ", "EV?", " 12라 1234 ")).confirmed()
        assertEquals("현대", info.brand)
        assertNull(info.fuel)
        assertEquals("12라 1234", info.plate)
        assertEquals(listOf(1,2,3,4,5), setupSteps(info))
    }
}
