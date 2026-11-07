package com.example.vemoring

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener

data class BatteryReading(val value: Int? = null, val status: String = "Pleos 미수신")

/** Native counterpart of the Flutter VehicleService, without platform channels. */
class PleosVehicle(context: Context, private val changed: (BatteryReading) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private var active = false
    private var listener: EvBatteryStateOfChargeListener? = null
    private fun publish(value: Int? = null, status: String) {
        handler.post { if (active) changed(BatteryReading(value?.coerceIn(0,100), status)) }
    }
    fun connect() {
        active = true
        try {
            vehicle.initialize()
            val battery = vehicle.getEvBattery()
            battery.checkEvBatteryStateOfChargeCapabilities(onComplete = { config ->
                if (!active) return@checkEvBatteryStateOfChargeCapabilities
                if (config.accessType == AccessType.NONE || config.accessType == AccessType.WRITE) {
                    publish(status = "SDK 항목 미지원")
                } else {
                    try {
                        val callback = object : EvBatteryStateOfChargeListener {
                            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                                publish(value, if (value == null) "Pleos 미수신" else "Pleos 실데이터")
                            }
                            override fun onFailed(error: Exception) { publish(status = "Pleos 연결 오류") }
                        }
                        if (active) { listener = callback; battery.registerEvBatteryStateOfCharge(callback) }
                        battery.getEvBatteryStateOfCharge(onComplete = { value ->
                            publish(value, if (value == null) "Pleos 미수신" else "Pleos 실데이터")
                        }, onFailed = { publish(status = "Pleos 연결 오류") })
                    } catch (_: Exception) { publish(status = "Pleos 연결 오류") }
                }
            }, onFailed = { publish(status = "SDK 항목 미지원 또는 연결 오류") })
        } catch (_: Exception) { publish(status = "Pleos 연결 오류") }
    }
    fun close() {
        active = false
        handler.removeCallbacksAndMessages(null)
        runCatching { listener?.let { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(it) } }
        listener = null
        runCatching { vehicle.release() }
    }
}
