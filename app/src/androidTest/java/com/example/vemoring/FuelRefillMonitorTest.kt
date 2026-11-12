package com.example.vemoring

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.*
import com.vemoring.companion.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Isolated journal; never touches the user's saved cars. Real 30 s gaps, so this takes about two minutes. */
class FuelRefillMonitorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val base = instrumentation.targetContext
    private val prefix = "fuel_monitor_${UUID.randomUUID()}_"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
    }

    /** Stand-in for the SDK: answers each separate read with the next queued value and records when it was asked. */
    private class ScriptedGateway(values: List<Int?>) : VehicleGateway {
        private val queue = ArrayDeque(values)
        val requestedAt = mutableListOf<Long>()
        override fun start(publish: (VehicleField, Reading) -> Unit) = Unit
        override fun refresh() = Unit
        override fun close() = Unit
        override fun readFuel(done: (Reading) -> Unit) {
            requestedAt += System.currentTimeMillis()
            val next = queue.removeFirstOrNull()
            done(if (next == null) Reading(ReadingState.Error, reason = "scripted failure") else Reading.percent(next))
        }
    }

    private fun run(values: List<Int?>, expectArmed: Boolean): Pair<CareJournal, ScriptedGateway> {
        val gateway = ScriptedGateway(values)
        val repository = VehicleRepository(gateway)
        instrumentation.runOnMainSync { repository.start() }
        val journal = CareJournal(context, UUID.randomUUID().toString())
        journal.updateFuelEpisode { FuelRefillPolicy.observe(it, 15, 20) }
        FuelRefillMonitor.observe(context, journal, 20, Reading.percent(35), repository)
        val deadline = SystemClock.uptimeMillis() + 100_000
        while (SystemClock.uptimeMillis() < deadline && gateway.requestedAt.size < values.size) SystemClock.sleep(500)
        SystemClock.sleep(1500)
        assertEquals(if (expectArmed) FuelPhase.ARMED else FuelPhase.LOW, journal.fuelEpisode().phase)
        return journal to gateway
    }

    @Test fun threeSeparateReadsAtLeastThirtySecondsApartConfirmRefuel() {
        val (journal, gateway) = run(listOf(35, 31, 30), expectArmed = true)
        assertEquals(3, gateway.requestedAt.size)
        gateway.requestedAt.zipWithNext().forEach { (a, b) -> assertTrue("gap ${b - a}ms", b - a >= FuelRefillPolicy.GAP_MILLIS) }
        assertEquals(1, journal.fuelEpisode().refills.size)
        assertEquals(1L, journal.fuelEpisode().refills.single().episode)
        journal.deleteVehicle()
    }

    @Test fun lowerSecondReadCancelsWithoutRefuel() {
        val (journal, gateway) = run(listOf(35, 29), expectArmed = false)
        assertEquals(2, gateway.requestedAt.size)
        assertTrue(journal.fuelEpisode().refills.isEmpty())
        journal.deleteVehicle()
    }

    /** Real Pleos SDK: the separate fuel-only read used for confirmation returns a valid integer. */
    @Test fun realSdkSeparateFuelReadReturnsValue() {
        val owner = Any()
        instrumentation.runOnMainSync { SharedVehicleConnection.acquire(base, owner) }
        try {
            val repository = SharedVehicleConnection.repository(base)
            val deadline = SystemClock.uptimeMillis() + 30_000
            while (SystemClock.uptimeMillis() < deadline && repository.state.value[VehicleField.FUEL].state == ReadingState.Loading) SystemClock.sleep(200)
            val subscribed = repository.state.value[VehicleField.FUEL]
            android.util.Log.i("FuelRefillMonitorTest", "Subscribed FUEL: ${subscribed.state} ${subscribed.reason} ${subscribed.value?.text}")
            // An EV profile (like the default Pleos emulator car) has no fuel level at all; that is a skip, not a pass.
            org.junit.Assume.assumeTrue("Car reports no fuel level: ${subscribed.state} ${subscribed.reason}",
                subscribed.state == ReadingState.Available)
            var result: Reading? = null
            instrumentation.runOnMainSync { repository.readFuel { result = it } }
            val end = SystemClock.uptimeMillis() + 20_000
            while (SystemClock.uptimeMillis() < end && result == null) SystemClock.sleep(200)
            val reading = requireNotNull(result) { "No response to the separate fuel read" }
            assertEquals("Separate fuel read: ${reading.state} ${reading.reason}", ReadingState.Available, reading.state)
            assertNotNull(reading.realPercent())
        } finally { instrumentation.runOnMainSync { SharedVehicleConnection.release(owner) } }
    }
}
