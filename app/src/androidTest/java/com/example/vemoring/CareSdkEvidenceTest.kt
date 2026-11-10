package com.example.vemoring

import android.bluetooth.BluetoothManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.*
import com.example.vemoring.integration.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** An evidence report, never an assertion that all requested capabilities exist. No fake values. */
class CareSdkEvidenceTest {
    @Test fun recordActualCapabilitiesAndCallbacks() {
        val i = InstrumentationRegistry.getInstrumentation()
        val c = i.targetContext
        val events = JSONArray()
        val wait = CountDownLatch(1)
        var gateway: PleosVehicleGateway? = null
        var location: PleosLocationGateway? = null
        i.runOnMainSync {
            gateway = PleosVehicleGateway(c)
            gateway!!.start { field, reading -> synchronized(events) {
                if (field in setOf(VehicleField.GEAR, VehicleField.ODOMETER, VehicleField.TRIP_DISTANCE,
                    VehicleField.ENGINE_OIL, VehicleField.BRAKE_FLUID, VehicleField.TRANSMISSION_OIL,
                    VehicleField.WASHER, VehicleField.TELLTALES, VehicleField.OIL_PRESSURE, VehicleField.SOC))
                    events.put(JSONObject().put("field", field.name).put("status", reading.state.name)
                        .put("text", reading.value?.text).put("raw", reading.value?.rawState)
                        .put("unit", reading.value?.unit ?: "unverified").put("at", reading.receivedAtMillis)
                        .put("reason", reading.reason))
            } }
            location = PleosLocationGateway(c, { result -> synchronized(events) {
                events.put(JSONObject().put("field", "LOCATION").put("status", result.state.name)
                    .put("generatedAt", result.value?.timestamp).put("receivedAt", result.receivedAt)
                    .put("accuracyM", JSONObject.NULL).put("confidence", result.value?.confidence).put("reason", result.detail))
            } }, { _, _ -> })
            location!!.start(true)
        }
        try { wait.await(20, TimeUnit.SECONDS) } finally {
            i.runOnMainSync { location?.stop(); gateway?.close() }
            val report = JSONObject().put("vehicleSdk", "2.3.2").put("locationSdk", "1.1.3")
                .put("api", android.os.Build.VERSION.SDK_INT).put("device", android.os.Build.MODEL)
                .put("bluetoothAdapter", runCatching { c.getSystemService(BluetoothManager::class.java)?.adapter != null }.getOrDefault(false))
                .put("events", events)
                .put("limitations", "No console gear/mileage changes; a callback of zero does not verify movement. Location accuracy is not exposed by this adapter. Physical Bluetooth untested.")
            File(c.filesDir, "care-sdk-evidence.json").writeText(report.toString(2))
        }
    }
}
