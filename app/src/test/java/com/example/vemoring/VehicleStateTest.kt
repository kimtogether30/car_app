package com.example.vemoring.vehicle

import ai.pleos.playground.vehicle.constant.ErrorCode
import org.junit.Assert.*
import org.junit.Test

class VehicleStateTest {
    @Test fun zeroIsAvailableAndNullIsNoData() {
        val zero = Reading.percent(0)
        assertEquals(ReadingState.Available, zero.state)
        assertEquals(0.0, zero.value!!.number!!, 0.0)
        assertEquals(ReadingState.NoData, Reading.percent(null).state)
        assertEquals(ReadingState.Error, Reading.percent(-1).state)
        assertEquals(ReadingState.Error, Reading.percent(101).state)
    }
    @Test fun errorCodesAreNotAllUnsupported() {
        assertEquals(ReadingState.PermissionDenied, failureForCode(ErrorCode.ERROR_SECURITY_PERMISSION).state)
        assertEquals(ReadingState.PermissionDenied, sdkFailure(SecurityException()).state)
        assertEquals(ReadingState.Disconnected, failureForCode(ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE).state)
        assertEquals(ReadingState.Error, failureForCode(ErrorCode.ERROR_INTERNAL_SERVICE).state)
        assertEquals(ReadingState.NoData, failureForCode(ErrorCode.ERROR_EMPTY_RESULT).state)
        assertEquals(ReadingState.Unsupported, failureForCode(ErrorCode.ERROR_NOT_SUPPORTED_AREA).state)
    }
    @Test fun unknownUnitsAreNotPresentedAsKilometres() {
        val raw = Reading.available(SignalValue("10000", 10000.0))
        assertFalse(raw.statusText().contains("km"))
        assertTrue(raw.statusText().contains("단위 확인"))
    }
    @Test fun repositoryRejectsCallbacksFromClosedAndPreviousSessions() {
        val fake = FakeGateway()
        val repository = VehicleRepository(fake)
        repository.start()
        val old = fake.callback!!
        old(VehicleField.FUEL, Reading.percent(0))
        assertEquals(ReadingState.Available, repository.state.value[VehicleField.FUEL].state)
        repository.close()
        old(VehicleField.FUEL, Reading.percent(99))
        assertEquals(ReadingState.Disconnected, repository.state.value[VehicleField.FUEL].state)
        repository.start()
        old(VehicleField.FUEL, Reading.percent(99))
        assertEquals(ReadingState.Loading, repository.state.value[VehicleField.FUEL].state)
        fake.callback!!(VehicleField.FUEL, Reading.percent(null))
        assertEquals(ReadingState.NoData, repository.state.value[VehicleField.FUEL].state)
        repository.refresh()
        assertEquals(1, fake.refreshes)
        assertTrue(fake.closed)
    }
    @Test fun targetAndMeasuredTemperaturesHaveSeparateKeys() {
        val snapshot = VehicleSnapshot(VehicleSnapshot().readings + mapOf(
            VehicleField.HVAC_TARGET to Reading.available(SignalValue("22 °C",22.0,"°C")),
            VehicleField.HVAC_CURRENT to Reading.available(SignalValue("27 °C",27.0,"°C"))))
        assertEquals(22.0, snapshot[VehicleField.HVAC_TARGET].value!!.number!!, 0.0)
        assertEquals(27.0, snapshot[VehicleField.HVAC_CURRENT].value!!.number!!, 0.0)
    }
    private class FakeGateway : VehicleGateway {
        var callback: ((VehicleField, Reading) -> Unit)? = null
        var closed = false
        var refreshes = 0
        override fun start(publish: (VehicleField, Reading) -> Unit) { callback = publish }
        override fun refresh() { refreshes++ }
        override fun close() { closed = true }
    }
}
