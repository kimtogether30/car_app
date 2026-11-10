package com.example.vemoring.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReadingState { Loading, Available, Unsupported, PermissionDenied, Disconnected, Error, Demo, NoData }

data class SignalValue(val text: String, val number: Double? = null, val unit: String? = null, val warning: Boolean = false,
    val rawState: String? = null, val color: Int? = null)
data class Reading(val state: ReadingState, val value: SignalValue? = null, val reason: String = "", val receivedAtMillis: Long? = null) {
    init { require(state != ReadingState.Available || value != null) }
    companion object {
        fun available(value: SignalValue) = Reading(ReadingState.Available, value, receivedAtMillis = System.currentTimeMillis())
        fun percent(value: Int?): Reading = when {
            value == null -> Reading(ReadingState.NoData)
            value !in 0..100 -> Reading(ReadingState.Error, reason = "유효하지 않은 잔량")
            else -> available(SignalValue("$value %", value.toDouble(), "%"))
        }
    }
}

enum class VehicleField(val label: String) {
    CAR_INFO("차량 기본 정보"), VIN("VIN 조회"), CRN("CRN 조회"), FUEL("연료 잔량"), SOC("배터리 잔량"),
    FUEL_ECONOMY("현재 연비"), EV_EFFICIENCY("현재 전비"), CHARGE_SWITCH("충전 스위치 상태"),
    ODOMETER("총 누적 주행거리"), TRIP_DISTANCE("트립 주행거리"), TRIP_TIME("트립 운행 시간"),
    HVAC_DIRECTION("공조 송풍 모드"), HVAC_AC("에어컨"), HVAC_TARGET("설정 온도"), HVAC_CURRENT("실내 센서 온도"),
    HVAC_FAN("바람 세기"), HVAC_AUTO("AUTO 모드"), HVAC_RECIRCULATION("내기 순환"), HVAC_CLEANER("공기청정"),
    GEAR("현재 기어"), TRANSMISSION_OIL("변속기 오일량"), TELLTALES("정비 경고 원본"),
    ENGINE_OIL("엔진오일량 경고"), BRAKE_FLUID("브레이크액 경고"), WASHER("워셔액 부족 경고"),
    LOW_FUEL("연료 부족 경고"), OIL_PRESSURE("엔진오일 압력 경고"), SMART_KEY("스마트키 배터리 경고")
}

data class VehicleSnapshot(val readings: Map<VehicleField, Reading> = VehicleField.entries.associateWith { Reading(ReadingState.Disconnected) }) {
    operator fun get(field: VehicleField): Reading = readings.getValue(field)
    fun priorityWarning(): String? = listOf(VehicleField.OIL_PRESSURE, VehicleField.ENGINE_OIL, VehicleField.BRAKE_FLUID, VehicleField.WASHER, VehicleField.LOW_FUEL).firstOrNull { field ->
        this[field].state == ReadingState.Available && this[field].value?.warning == true
    }?.let { it.label + ": " + this[it].value!!.text }
}

interface VehicleGateway {
    /** Vehicle 2.3.2 has no confirmed brand/model/powertrain getter. Fail closed, without guessing from VIN/SOC. */
    fun identify(publish: (VehicleIdentityReading) -> Unit) { publish(VehicleIdentityReading()) }
    fun start(publish: (VehicleField, Reading) -> Unit)
    fun refresh()
    fun close()
}

class VehicleRepository(private val gateway: VehicleGateway) {
    fun identify(publish: (VehicleIdentityReading) -> Unit) = gateway.identify(publish)
    private val mutable = MutableStateFlow(VehicleSnapshot())
    val state: StateFlow<VehicleSnapshot> = mutable.asStateFlow()
    private var generation = 0
    private var active = false
    fun start() {
        if (active) return
        active = true
        val token = ++generation
        mutable.value = VehicleSnapshot(VehicleField.entries.associateWith { Reading(ReadingState.Loading) })
        gateway.start { field, reading ->
            if (active && token == generation) mutable.value = VehicleSnapshot(mutable.value.readings + (field to reading))
        }
    }
    fun refresh() { if (active) gateway.refresh() }
    fun close() {
        active = false
        generation++
        gateway.close()
        mutable.value = VehicleSnapshot()
    }
}
