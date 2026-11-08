package com.example.vemoring

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteStateInfo
import com.example.vemoring.vehicle.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit real reception tests. Never run automatically as part of the JVM suite.
 * They FAIL without a real callback/value; an error/no-data state is not a reception success.
 * Grant the Pleos location permission through the app first.
 */
class RealSdkReceptionTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    @Test fun vehicleReceivesAtLeastOneNumericSignal() {
        val latch=CountDownLatch(1)
        var gateway: PleosVehicleGateway?=null
        val fields=setOf(VehicleField.FUEL,VehicleField.SOC,VehicleField.ODOMETER)
        instrumentation.runOnMainSync {
            gateway=PleosVehicleGateway(instrumentation.targetContext)
            gateway!!.start { field,reading ->
                if(field in fields && reading.state==ReadingState.Available && reading.value?.number!=null) {
                    println("PLEOS_REAL_RESPONSE ${field.name}=${reading.value.number} unit=${reading.value.unit ?: "unverified"}")
                    latch.countDown()
                }
            }
        }
        try { assertTrue("미검증: 45초 내 실제 차량 수치 수신 없음",latch.await(45,TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { gateway?.close() } }
    }
    @Test fun fusedLocationReceivesValidLocation() {
        val context=instrumentation.targetContext
        assertEquals("앱에서 Pleos 위치 권한을 먼저 허용하세요",PackageManager.PERMISSION_GRANTED,context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION"))
        val latch=CountDownLatch(1)
        var client: FusedLocation?=null
        val listener=object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if(location!=null && location.latitude.isFinite() && location.longitude.isFinite() &&
                    location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
                    location.timestampMillis>0 && System.currentTimeMillis()-location.timestampMillis in -60000..900000) {
                    println("PLEOS_REAL_RESPONSE location=valid coordinates_redacted timestamp=${location.timestampMillis}"); latch.countDown()
                }
            }
            override fun onError(exception: Exception) { println("PLEOS_LOCATION_ERROR ${exception.javaClass.simpleName}") }
        }
        instrumentation.runOnMainSync {
            client=FusedLocation(context)
            if(client!!.isSupported()) { client!!.initialize(); client!!.getLastKnownLocation(listener); client!!.registerFusedLocationCallback(listener) }
        }
        try { assertTrue("미검증: 30초 내 유효 위치 수신 없음",latch.await(30,TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { client?.let { runCatching { it.unregisterFusedLocationCallback(listener) }; runCatching { it.release() } } } }
    }
    @Test fun navigationReceivesRouteStateCallback() {
        val latch=CountDownLatch(1)
        var client: NaviHelper?=null
        val listener=object : NaviHelperEventListener {
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) { println("PLEOS_REAL_RESPONSE navi_route_state=received"); latch.countDown() }
        }
        instrumentation.runOnMainSync {
            client=NaviHelper(instrumentation.targetContext)
            client!!.initialize({
                client!!.addListener(listener); client!!.getRouteStateInfo()
            }, { error -> println("PLEOS_NAVI_ERROR ${error.javaClass.simpleName}") })
        }
        try { assertTrue("미검증: 30초 내 내비 경로 상태 콜백 없음",latch.await(30,TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { client?.let { runCatching { it.removeListener(listener) }; runCatching { it.release() } } } }
    }
}
