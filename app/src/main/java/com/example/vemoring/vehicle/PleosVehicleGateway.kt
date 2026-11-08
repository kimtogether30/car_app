package com.example.vemoring.vehicle

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.callback.exception.a as SdkVehicleException
import ai.pleos.playground.vehicle.constant.ErrorCode
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.*

fun sdkFailure(error: Exception): Reading {
    val code = (error as? SdkVehicleException)?.a()
    return failureForCode(code, error is SecurityException)
}

fun failureForCode(code: ErrorCode?, security: Boolean = false): Reading {
    val state = when {
        security || code == ErrorCode.ERROR_SECURITY_PERMISSION -> ReadingState.PermissionDenied
        code == ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY || code == ErrorCode.ERROR_NOT_SUPPORTED_AREA -> ReadingState.Unsupported
        code == ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE -> ReadingState.Disconnected
        code == ErrorCode.ERROR_EMPTY_RESULT -> ReadingState.NoData
        else -> ReadingState.Error
    }
    return Reading(state, reason = code?.name ?: "차량 조회 실패")
}

/** Version-pinned read-only adapter for the public Vehicle 2.3.2 AAR. VIN/CRN values are masked immediately; no control calls. */
class PleosVehicleGateway(context: Context) : VehicleGateway {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var current: Session? = null
    override fun start(publish: (VehicleField, Reading) -> Unit) {
        close()
        val sdkVehicle = try { Vehicle(appContext) } catch (error: Exception) {
            VehicleField.entries.forEach { publish(it, sdkFailure(error)) }; return
        }
        val session = Session(sdkVehicle, publish)
        current = session
        try { session.vehicle.initialize(); session.refresh() }
        catch (error: Exception) { VehicleField.entries.forEach { session.emit(it, sdkFailure(error)) } }
    }
    override fun refresh() { current?.refresh() }
    override fun close() { current?.close(); current = null }

    private inner class Session(val vehicle: Vehicle, val publish: (VehicleField, Reading) -> Unit) {
        var active = true
        var pending = 0
        var released = false
        private val unsubscribe = mutableMapOf<VehicleField, () -> Unit>()
        private var polling = false
        // Public telltale getter is allowed; its listener requires PRIVATE_CAR_SAFETY.
        private val warningPoll = object : Runnable {
            override fun run() {
                if(!active) return
                if(pending == 0) runCatching { readSafety() }
                handler.postDelayed(this,30000)
            }
        }
        init { handler.postDelayed(warningPoll,30000) }
        fun emit(field: VehicleField, reading: Reading) {
            handler.post { if (active) {
                publish(field, reading)
                if (field == VehicleField.LOW_FUEL && reading.state in setOf(ReadingState.Error, ReadingState.PermissionDenied, ReadingState.Disconnected, ReadingState.Unsupported, ReadingState.NoData)) publish(VehicleField.OIL_PRESSURE, reading)
            } }
        }
        private fun finish() {
            pending--
            if (!active && pending == 0 && !released) {
                released = true
                runCatching { vehicle.release() }
            }
        }
        // Callback work is serialized on main; late callbacks cannot publish after close.
        private fun <T> request(field: VehicleField, call: ((T?) -> Unit, (Exception) -> Unit) -> Unit, success: (T?) -> Unit) {
            if (!active) return
            pending++
            var done = false
            val timeout = Runnable { if (!done && active) emit(field, Reading(ReadingState.Error, reason = "응답 시간 초과")) }
            handler.postDelayed(timeout, 15000)
            fun complete(action: () -> Unit) { handler.post {
                if (!done) {
                    done = true
                    handler.removeCallbacks(timeout)
                    try { if (active) action() } catch (error: Exception) { emit(field, sdkFailure(error)) } finally { finish() }
                }
            } }
            try { call({ value -> complete { success(value) } }, { error -> complete { emit(field, sdkFailure(error)) } }) }
            catch (error: Exception) { complete { emit(field, sdkFailure(error)) } }
        }
        private fun readable(config: CapabilityConfig?) = config?.accessType == AccessType.READ || config?.accessType == AccessType.READ_WRITE
        private fun allowed(field: VehicleField, permission: String): Boolean {
            if (appContext.checkSelfPermission("pleos.car.permission.$permission") != PackageManager.PERMISSION_GRANTED) {
                emit(field, Reading(ReadingState.PermissionDenied)); return false
            }
            return true
        }
        private fun <T> read(field: VehicleField, permission: String,
            check: ((CapabilityConfig?) -> Unit, (Exception) -> Unit) -> Unit,
            get: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
            convert: (T) -> SignalValue,
            subscribe: (() -> (() -> Unit))? = null) {
            if (!allowed(field, permission)) return
            request(field, check) { config ->
                if (config == null) { emit(field, Reading(ReadingState.NoData)); return@request }
                if (!readable(config)) { emit(field, Reading(ReadingState.Unsupported)); return@request }
                if (subscribe != null && !unsubscribe.containsKey(field)) {
                    try { unsubscribe[field] = subscribe() } catch (error: Exception) { emit(field, sdkFailure(error)) }
                }
                request(field, get) { value ->
                    if (value == null) emit(field, Reading(ReadingState.NoData))
                    else try { emit(field, Reading.available(convert(value))) } catch (_: IllegalArgumentException) { emit(field, Reading(ReadingState.Error, reason = "유효하지 않은 데이터")) }
                }
            }
        }
        fun refresh() {
            if (!active || polling || pending > 0) return
            polling = true
            try {
                emit(VehicleField.CAR_INFO, Reading(ReadingState.Unsupported, reason = "제조사·모델 자동 조회 API 미확인 · 사용자 입력 유지"))
                emit(VehicleField.SMART_KEY, Reading(ReadingState.Unsupported, reason = "공식 조회 API 미확인"))
                val info = vehicle.getCarInfo()
                read(VehicleField.VIN, "CAR_INFO", info::checkVINInfoCapabilities, info::getVINInfo,
                    { value: String -> require(value.isNotBlank()); SignalValue("VIN 수신됨 · 식별자 숨김") })
                read(VehicleField.CRN, "CAR_INFO", info::checkCRNInfoCapabilities, info::getCRNInfo,
                    { value: String -> require(value.isNotBlank()); SignalValue("CRN 수신됨 · 식별자 숨김") })
                val fuel = vehicle.getFuel()
                read(VehicleField.FUEL, "CAR_ENERGY", fuel::checkFuelPercentCapabilities, fuel::getFuelPercent,
                    { value: Int -> require(value in 0..100); SignalValue("$value %", value.toDouble(), "%") }, {
                        val listener = object : FuelPercentListener {
                            override fun onFuelPercentUpdated(value: Int?) = emit(VehicleField.FUEL, Reading.percent(value))
                            override fun onFailed(error: Exception) = emit(VehicleField.FUEL, sdkFailure(error))
                        }
                        fuel.registerFuelPercent(listener)
                        val remove: () -> Unit = { fuel.unregisterFuelPercent(listener) }; remove
                    })
                read(VehicleField.FUEL_ECONOMY, "CAR_ENERGY", fuel::checkCurrentFuelEconomyCapabilities, fuel::getCurrentFuelEconomy, ::rawFloat)
                val ev = vehicle.getEvBattery()
                read(VehicleField.SOC, "CAR_ENERGY", ev::checkEvBatteryStateOfChargeCapabilities, ev::getEvBatteryStateOfCharge,
                    { value: Int -> require(value in 0..100); SignalValue("$value %", value.toDouble(), "%") }, {
                        val listener = object : EvBatteryStateOfChargeListener {
                            override fun onEvBatteryStateOfChargeUpdated(value: Int?) = emit(VehicleField.SOC, Reading.percent(value))
                            override fun onFailed(error: Exception) = emit(VehicleField.SOC, sdkFailure(error))
                        }
                        ev.registerEvBatteryStateOfCharge(listener)
                        val remove: () -> Unit = { ev.unregisterEvBatteryStateOfCharge(listener) }; remove
                    })
                read(VehicleField.EV_EFFICIENCY, "CAR_ENERGY", ev::checkCurrentEvEfficiencyCapabilities, ev::getCurrentEvEfficiency, ::rawFloat)
                read(VehicleField.CHARGE_SWITCH, "CAR_ENERGY", ev::checkEvChargeSwitchCapabilities, ev::getEvChargeSwitchState, ::booleanValue)
                val odo = vehicle.getOdometer()
                read(VehicleField.ODOMETER, "CAR_DRIVE", odo::checkCurrentDistanceDrivenCapabilities, odo::getCurrentDistanceDriven, ::rawFloat, {
                    val listener = object : DistanceDrivenListener {
                        override fun onDistanceDrivenUpdated(value: Float?) {
                            emit(VehicleField.ODOMETER, if (value == null) Reading(ReadingState.NoData) else if (value.isFinite() && value >= 0) Reading.available(rawFloat(value)) else Reading(ReadingState.Error))
                        }
                        override fun onFailed(error: Exception) = emit(VehicleField.ODOMETER, sdkFailure(error))
                    }
                    odo.registerDistanceDriven(listener)
                    val remove: () -> Unit = { odo.unregisterDistanceDriven(listener) }; remove
                })
                read(VehicleField.TRIP_DISTANCE, "CAR_DRIVE", odo::checkTripCurrentDrivingDistanceCapabilities, odo::getTripCurrentDrivingDistance, ::rawFloat)
                read(VehicleField.TRIP_TIME, "CAR_DRIVE", odo::checkTripCurrentDrivingTimeCapabilities, odo::getTripCurrentDrivingTime, { value: Int -> require(value >= 0); SignalValue(value.toString(), value.toDouble()) })
                readClimate()
                readSafety()
            } catch (error: Exception) { VehicleField.entries.forEach { emit(it, sdkFailure(error)) } }
            finally { polling = false }
        }
        private fun readSafety() {
            val safety = vehicle.getSafety()
            read(VehicleField.ENGINE_OIL, "CAR_SAFETY", { ok, fail -> safety.checkEngineOilLevelStateCapabilities({ config, _ -> ok(config) }, fail) }, safety::getEngineOilLevelState, ::oilValue, {
                val listener = object : OilLevelStateListener {
                    override fun onOilLevelUpdated(value: OilLevelState?) { emitOil(VehicleField.ENGINE_OIL, value) }
                    override fun onFailed(error: Exception) = emit(VehicleField.ENGINE_OIL, sdkFailure(error))
                }
                safety.registerEngineOilLevelState(listener)
                val remove: () -> Unit = { safety.unregisterEngineOilLevelState(listener) }; remove
            })
            read(VehicleField.BRAKE_FLUID, "CAR_SAFETY", { ok, fail -> safety.checkBrakeFluidLevelStateCapabilities({ config, _ -> ok(config) }, fail) }, safety::getBrakeFluidLevelState, ::oilValue, {
                val listener = object : OilLevelStateListener {
                    override fun onOilLevelUpdated(value: OilLevelState?) { emitOil(VehicleField.BRAKE_FLUID, value) }
                    override fun onFailed(error: Exception) = emit(VehicleField.BRAKE_FLUID, sdkFailure(error))
                }
                safety.registerBrakeFluidLevelState(listener)
                val remove: () -> Unit = { safety.unregisterBrakeFluidLevelState(listener) }; remove
            })
            read(VehicleField.WASHER, "CAR_SAFETY", safety::checkLowWasherFluidWarningStateCapabilities, safety::hasLowWasherFluidWarning, ::warningValue, {
                val listener = object : LowWasherFluidWaringListener {
                    override fun onLowWasherFluidWaringUpdated(value: Boolean?) = emit(VehicleField.WASHER, value?.let { Reading.available(warningValue(it)) } ?: Reading(ReadingState.NoData))
                    override fun onFailed(error: Exception) = emit(VehicleField.WASHER, sdkFailure(error))
                }
                safety.registerLowWasherFluidWaringState(listener)
                val remove: () -> Unit = { safety.unregisterLowWasherFluidWaringState(listener) }; remove
            })
            if (!allowed(VehicleField.LOW_FUEL, "CAR_SAFETY")) { emit(VehicleField.OIL_PRESSURE, Reading(ReadingState.PermissionDenied)); return }
            request(VehicleField.LOW_FUEL, safety::checkTelltalesCapabilities) { config ->
                if (!readable(config)) { emit(VehicleField.LOW_FUEL, Reading(ReadingState.Unsupported)); emit(VehicleField.OIL_PRESSURE, Reading(ReadingState.Unsupported)); return@request }
                fun update(info: ai.pleos.playground.vehicle.api.model.telltale.Telltales?) {
                    for ((field, type) in listOf(VehicleField.LOW_FUEL to TelltaleType.LOW_FUEL, VehicleField.OIL_PRESSURE to TelltaleType.ENGINE_OIL_PRESSURE)) {
                        val value = info?.telltales?.firstOrNull { it.type == type }
                        emit(field, if (value == null) Reading(ReadingState.NoData) else Reading.available(warningValue(value.state != TelltaleState.OFF)))
                    }
                }
                request(VehicleField.LOW_FUEL, safety::getTelltalesInfo) { update(it) }
            }
        }
        private fun emitOil(field: VehicleField, value: OilLevelState?) {
            emit(field, when(value) { null -> Reading(ReadingState.NoData); OilLevelState.ERROR -> Reading(ReadingState.Error); else -> Reading.available(oilValue(value)) })
        }
        private fun readClimate() {
            val hvac = vehicle.getHVAC()
            fun <T> areaRead(field: VehicleField,
                check: ((CapabilityConfig?, List<SeatArea>?) -> Unit, (Exception) -> Unit) -> Unit,
                get: (SeatArea, (T?) -> Unit, (Exception) -> Unit) -> Unit, convert: (T) -> SignalValue) {
                if (!allowed(field, "CAR_CLIMATE")) return
                request<Pair<CapabilityConfig?, List<SeatArea>?>>(field, { ok, fail -> check({ config, areas -> ok(config to areas) }, fail) }) { result ->
                    val area = result?.second?.firstOrNull()
                    if (!readable(result?.first) || area == null) { emit(field, Reading(ReadingState.Unsupported)); return@request }
                    request<T>(field, { ok, fail -> get(area, ok, fail) }) { value -> emit(field, value?.let { Reading.available(convert(it).copy(text = "영역 ${area.values.joinToString { it.name }}: ${convert(it).text}")) } ?: Reading(ReadingState.NoData)) }
                }
            }
            areaRead(VehicleField.HVAC_DIRECTION, { ok, fail ->
                hvac.checkHvacFanDirectionCapabilities({ config: CapabilityConfig, areas: Map<SeatArea, List<ai.pleos.playground.vehicle.constant.control.FanDirectionType>> -> ok(config,areas.keys.toList()) }, fail)
            }, hvac::getHvacFanDirection, { value: ai.pleos.playground.vehicle.constant.control.FanDirectionType -> SignalValue(value.name) })
            areaRead(VehicleField.HVAC_AC, hvac::checkHvacAcCapabilities, hvac::getHvacAcState, ::booleanValue)
            areaRead(VehicleField.HVAC_AUTO, hvac::checkHvacAutoCapabilities, hvac::getHvacAutoState, ::booleanValue)
            areaRead(VehicleField.HVAC_RECIRCULATION, hvac::checkHvacRecirculationCapabilities, hvac::getHvacRecirculationState, ::booleanValue)
            areaRead(VehicleField.HVAC_CLEANER, hvac::checkHvacAirCleanerCapabilities, hvac::getHvacAirCleanerState, ::booleanValue)
            areaRead(VehicleField.HVAC_CURRENT, hvac::checkHvacTemperatureCurrentCapabilities, { area, ok, fail -> hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, ok, fail) }, ::celsius)
            areaRead(VehicleField.HVAC_TARGET, { ok, fail -> hvac.checkHvacTemperatureCapabilities({ config: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Float>>, _: HvacTemperatureConfigArray? -> ok(config, bounds.keys.toList()) }, fail) }, { area, ok, fail -> hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) }, ::celsius)
            areaRead(VehicleField.HVAC_FAN, { ok, fail -> hvac.checkHvacFanSpeedCapabilities({ config: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Int>> -> ok(config, bounds.keys.toList()) }, fail) }, hvac::getHvacFanSpeed, { value: Int -> SignalValue(value.toString(), value.toDouble()) })
        }
        fun close() {
            active = false
            handler.removeCallbacks(warningPoll)
            unsubscribe.values.forEach { runCatching { it() } }
            unsubscribe.clear()
            // Do not release the service in the middle of an SDK onComplete/onFailed call.
            if (pending == 0 && !released) { released = true; runCatching { vehicle.release() } }
        }
    }
}

private fun rawFloat(value: Float): SignalValue { require(value.isFinite() && value >= 0); return SignalValue(value.toString(), value.toDouble()) }
private fun celsius(value: Float): SignalValue { require(value.isFinite()); return SignalValue("$value °C", value.toDouble(), "°C") }
private fun booleanValue(value: Boolean) = SignalValue(if (value) "켜짐" else "꺼짐")
private fun warningValue(value: Boolean) = SignalValue(if (value) "경고 있음" else "경고 없음", warning = value)
private fun oilValue(value: OilLevelState): SignalValue {
    require(value != OilLevelState.ERROR)
    return SignalValue(when(value) { OilLevelState.NORMAL -> "정상"; OilLevelState.LOW -> "부족"; OilLevelState.HIGH -> "과다"; OilLevelState.CRITICALLY_LOW -> "매우 부족"; else -> "미확인" }, warning = value != OilLevelState.NORMAL)
}
