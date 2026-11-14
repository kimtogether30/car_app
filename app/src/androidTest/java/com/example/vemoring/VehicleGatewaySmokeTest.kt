package com.example.vemoring

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class VehicleGatewaySmokeTest {
    @Test fun emulatorReportsStatesWithoutInventingVehicleData() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val readings = ConcurrentHashMap<VehicleField, Reading>()
        val gate = CountDownLatch(VehicleField.entries.size)
        lateinit var gateway: PleosVehicleGateway
        instrumentation.runOnMainSync {
            gateway = PleosVehicleGateway(instrumentation.targetContext)
            gateway.start { field, reading ->
                if (reading.state != ReadingState.Loading) {
                    if (readings.put(field, reading) == null) gate.countDown()
                }
            }
        }
        try {
            assertTrue("Every field must resolve or report an explicit failure", gate.await(25, TimeUnit.SECONDS))
            assertEquals(ReadingState.Unsupported, readings.getValue(VehicleField.CAR_INFO).state)
            assertTrue(readings.values.none { it.state == ReadingState.Demo })
            for (field in listOf(VehicleField.FUEL, VehicleField.SOC)) {
                val reading = readings.getValue(field)
                if (reading.state == ReadingState.Available) {
                    assertNotNull(reading.value?.number)
                    assertTrue(reading.value!!.number!! in 0.0..100.0)
                } else assertNull(reading.value)
            }
        } finally { instrumentation.runOnMainSync { gateway.close() } }
    }
}
