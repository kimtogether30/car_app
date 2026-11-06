package com.example.vemoring

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(), EventChannel.StreamHandler {
    private val methodChannelName = "com.vemoring/pleos_vehicle"
    private val eventChannelName = "com.vemoring/pleos_vehicle/events"

    private var vehicle: Vehicle? = null
    private var eventSink: EventChannel.EventSink? = null
    private var batteryListener: EvBatteryStateOfChargeListener? = null

    override fun configureFlutterEngine(flutterEngine: io.flutter.embedding.engine.FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            methodChannelName,
        ).setMethodCallHandler(::handleMethodCall)

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            eventChannelName,
        ).setStreamHandler(this)
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> initialize(result)
            "getEvBatteryStateOfCharge" -> getEvBatteryStateOfCharge(result)
            "getDriverDoorPosition" -> getDriverDoorPosition(result)
            "release" -> release(result)
            else -> result.notImplemented()
        }
    }

    private fun initialize(result: MethodChannel.Result) {
        try {
            if (vehicle == null) {
                vehicle = Vehicle(applicationContext)
            }
            vehicle?.initialize()
            result.success(
                mapOf(
                    "status" to "ready",
                    "message" to "Pleos Vehicle SDK가 초기화됐어요.",
                ),
            )
        } catch (error: Exception) {
            result.success(
                mapOf(
                    "status" to "connection_error",
                    "message" to (error.message ?: error.toString()),
                ),
            )
        }
    }

    private fun getEvBatteryStateOfCharge(result: MethodChannel.Result) {
        val currentVehicle = vehicle
        if (currentVehicle == null) {
            result.success(mapOf("status" to "connection_error", "message" to "Vehicle SDK가 초기화되지 않았어요.", "value" to null))
            return
        }

        val evBattery = currentVehicle.getEvBattery()
        evBattery.checkEvBatteryStateOfChargeCapabilities(
            onComplete = { config ->
                if (config.accessType == AccessType.NONE || config.accessType == AccessType.WRITE) {
                    result.success(mapOf("status" to "unsupported", "message" to "현재 차량/에뮬레이터가 EV 배터리 잔량 조회를 지원하지 않아요.", "value" to null))
                    return@checkEvBatteryStateOfChargeCapabilities
                }
                evBattery.getEvBatteryStateOfCharge(
                    onComplete = { value ->
                        if (value == null) {
                            result.success(mapOf("status" to "no_data", "message" to "EV 배터리 잔량 값이 아직 도착하지 않았어요.", "value" to null))
                        } else {
                            val percent = value.coerceIn(0, 100)
                            result.success(mapOf("status" to "ok", "message" to "Pleos Vehicle SDK에서 수신한 EV 배터리 잔량", "value" to percent))
                            eventSink?.success(mapOf("status" to "ok", "value" to percent))
                        }
                    },
                    onFailed = { error ->
                        result.success(mapOf("status" to "connection_error", "message" to errorMessage(error), "value" to null))
                    },
                )
            },
            onFailed = { error ->
                result.success(mapOf("status" to "unsupported_or_error", "message" to errorMessage(error), "value" to null))
            },
        )
    }

    private fun getDriverDoorPosition(result: MethodChannel.Result) {
        val currentVehicle = vehicle
        if (currentVehicle == null) {
            result.success(mapOf("status" to "connection_error", "message" to "Vehicle SDK가 초기화되지 않았어요.", "value" to null))
            return
        }

        val door = currentVehicle.getDoor()
        val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
        door.getCurrentDoorPosition(
            area = driverDoor,
            onComplete = { position ->
                if (position == null) {
                    result.success(mapOf("status" to "no_data", "message" to "운전석 문 상태 값이 아직 도착하지 않았어요.", "value" to null))
                } else {
                    result.success(mapOf("status" to "ok", "message" to "운전석 문 상태 조회 성공", "value" to position))
                }
            },
            onFailed = { error ->
                result.success(mapOf("status" to "connection_error", "message" to errorMessage(error), "value" to null))
            },
        )
    }

    private fun errorMessage(error: Exception): String {
        return error.message ?: error.toString()
    }

    private fun release(result: MethodChannel.Result) {
        try {
            batteryListener?.let { listener ->
                vehicle?.getEvBattery()?.unregisterEvBatteryStateOfCharge(listener)
            }
            batteryListener = null
            eventSink = null
            vehicle?.release()
            vehicle = null
            result.success(mapOf("status" to "released"))
        } catch (error: Exception) {
            result.success(mapOf("status" to "connection_error", "message" to errorMessage(error)))
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        val currentVehicle = vehicle ?: return
        batteryListener?.let { listener ->
            currentVehicle.getEvBattery().unregisterEvBatteryStateOfCharge(listener)
        }
        val listener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                if (value != null) {
                    val percent = value.coerceIn(0, 100)
                    eventSink?.success(mapOf("status" to "ok", "value" to percent))
                }
            }

            override fun onFailed(error: Exception) {
                eventSink?.success(
                    mapOf(
                        "status" to "connection_error",
                        "message" to errorMessage(error),
                        "value" to null,
                    ),
                )
            }
        }
        batteryListener = listener
        currentVehicle.getEvBattery().registerEvBatteryStateOfCharge(listener)
    }

    override fun onCancel(arguments: Any?) {
        batteryListener?.let { listener ->
            vehicle?.getEvBattery()?.unregisterEvBatteryStateOfCharge(listener)
        }
        batteryListener = null
        eventSink = null
    }
}
