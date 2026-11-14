package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test

class BluetoothReconnectPolicyTest {
    @Test fun absenceContinuesAfterFastBudgetWithoutBusyRetrying() {
        val waits=(0..100).map(BluetoothReconnectPolicy::delayAfterFailure)
        assertEquals(listOf(1000L,3000L,10000L),waits.take(3))
        assertEquals(listOf(30000L,60000L,120000L,300000L),waits.drop(3).take(4))
        assertTrue(waits.drop(6).all {it==300000L})
        assertEquals(300000L,BluetoothReconnectPolicy.delayAfterFailure(Int.MAX_VALUE))
    }
    @Test fun vehicleLocationRetryBudgetRemainsBounded() {
        assertNull(ConnectionRetry.delayAfterFailure(3))
        assertEquals(1000L,BluetoothReconnectPolicy.delayAfterFailure(0))
    }
}
