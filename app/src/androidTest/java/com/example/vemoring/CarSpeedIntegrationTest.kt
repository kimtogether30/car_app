package com.example.vemoring

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Read-only real Car Service/Pleos callbacks on the official emulator. No car controls or data writes. */
class CarSpeedIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun checkEnvironment() {
        assertTrue("Run on official Pleos automotive emulator",context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        assertEquals("Allow CAR_SPEED for the test target",PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(CarSpeedSource.PERMISSION))
    }
    @Test fun speedAndGearArriveThroughProductionGateway() {
        checkEnvironment()
        val values=ConcurrentHashMap<VehicleField,Reading>()
        val gateway=PleosVehicleGateway(context)
        try {
            instrumentation.runOnMainSync {gateway.start {field,reading -> values[field]=reading}}
            val deadline=android.os.SystemClock.uptimeMillis()+25000
            while(android.os.SystemClock.uptimeMillis()<deadline) {
                if(values[VehicleField.SPEED]?.state==ReadingState.Available && values[VehicleField.GEAR]?.state==ReadingState.Available) break
                android.os.SystemClock.sleep(100)
            }
            val speed=values[VehicleField.SPEED]
            assertEquals("Car speed: $speed",ReadingState.Available,speed?.state)
            assertEquals("m/s",speed!!.value!!.unit)
            assertTrue(speed.value!!.number!!.isFinite())
            val gear=values[VehicleField.GEAR]
            assertEquals("Pleos gear: $gear",ReadingState.Available,gear?.state)
            assertNotNull(normalizedGear(gear!!.value!!.rawState))
            val snapshot=VehicleSnapshot(VehicleSnapshot().readings+values)
            assertNotEquals(VehicleMotion.Unknown,snapshot.motion())
        } finally {instrumentation.runOnMainSync {gateway.close()}}
    }
    @Test fun sourceCloseStopsPublishing() {
        checkEnvironment()
        val count=AtomicInteger(0);val last=AtomicReference<Reading>()
        val source=CarSpeedSource(context)
        try {
            instrumentation.runOnMainSync {source.start {last.set(it);count.incrementAndGet()}}
            val deadline=android.os.SystemClock.uptimeMillis()+15000
            while(last.get()?.state!=ReadingState.Available && android.os.SystemClock.uptimeMillis()<deadline) android.os.SystemClock.sleep(100)
            assertEquals(ReadingState.Available,last.get()?.state)
        } finally {instrumentation.runOnMainSync {source.close()}}
        instrumentation.waitForIdleSync()
        val stopped=count.get()
        android.os.SystemClock.sleep(2000)
        assertEquals(stopped,count.get())
    }
}
