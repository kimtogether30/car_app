package com.example.vemoring

import com.example.vemoring.vehicle.*
import org.junit.Assert.*
import org.junit.Test

class VehicleMotionTest {
    private val now=100000L
    private fun snapshot(mps:Double?,gear:String?="P",speedAt:Long=now,gearAt:Long=now,
                         state:ReadingState=ReadingState.Available):VehicleSnapshot {
        val speed=Reading(state,SignalValue("speed",mps,"m/s"),receivedAtMillis=speedAt)
        val g=if(gear==null) Reading(ReadingState.NoData) else Reading(ReadingState.Available,SignalValue(gear,rawState=gear),receivedAtMillis=gearAt)
        return VehicleSnapshot(VehicleSnapshot().readings + mapOf(VehicleField.SPEED to speed,VehicleField.GEAR to g))
    }
    @Test fun zeroWithFreshParkIsParkedAndOtherGearsAreStopped() {
        assertEquals(VehicleMotion.Parked,snapshot(0.0).motion(now))
        assertEquals(VehicleMotion.Parked,snapshot(0.0,"GEAR_PARK").motion(now))
        assertEquals("N",normalizedGear("GEAR_NEUTRAL"))
        assertEquals("R",normalizedGear("GEAR_REVERSE"))
        assertEquals("D",normalizedGear("GEAR_DRIVE"))
        for(gear in listOf("D","R","N",null)) {
            assertEquals(VehicleMotion.Stopped,snapshot(0.0,gear).motion(now))
            assertEquals(true,snapshot(0.0,gear).confirmedStationary(now))
        }
    }
    @Test fun forwardReverseAndSlowMovementNeverBecomeStopped() {
        for(speed in listOf(5.0,-5.0,.001,-.001)) {
            assertEquals(VehicleMotion.Moving,snapshot(speed).motion(now))
            assertEquals(false,snapshot(speed).confirmedStationary(now))
        }
    }
    @Test fun missingInvalidDemoAndStaleSpeedAreUnknown() {
        for(state in ReadingState.entries.filter {it!=ReadingState.Available}) {
            assertEquals(VehicleMotion.Unknown,snapshot(0.0,state=state).motion(now))
            assertNull(snapshot(0.0,state=state).confirmedStationary(now))
        }
        for(speed in listOf(null,Double.NaN,Double.POSITIVE_INFINITY)) assertEquals(VehicleMotion.Unknown,snapshot(speed).motion(now))
        assertEquals(VehicleMotion.Unknown,snapshot(0.0,speedAt=now-5001).motion(now))
        assertEquals(VehicleMotion.Unknown,snapshot(0.0,speedAt=now+1).motion(now))
    }
    @Test fun staleParkCannotTurnFreshZeroIntoParking() {
        assertEquals(VehicleMotion.Stopped,snapshot(0.0,gearAt=now-35001).motion(now))
        assertEquals(VehicleMotion.Stopped,snapshot(0.0,gearAt=now+1).motion(now))
    }
    @Test fun monotonicTimestampChecksAndSignedMetresPerSecondArePreserved() {
        val reading=vehicleSpeedReading(-2.0,9_000_000_000,10_000_000_000,now)
        assertEquals(ReadingState.Available,reading.state)
        assertEquals(-2.0,reading.value!!.number!!,0.0)
        assertEquals("m/s",reading.value!!.unit)
        assertEquals(now-1000,reading.receivedAtMillis)
        assertEquals(ReadingState.NoData,vehicleSpeedReading(0.0,1,10_000_000_000,now).state)
        assertEquals(ReadingState.NoData,vehicleSpeedReading(0.0,0,10_000_000_000,now).state)
        assertEquals(ReadingState.NoData,vehicleSpeedReading(0.0,11_000_000_000,10_000_000_000,now).state)
        assertEquals(ReadingState.NoData,vehicleSpeedReading(Double.NaN,9_000_000_000,10_000_000_000,now).state)
    }
}
