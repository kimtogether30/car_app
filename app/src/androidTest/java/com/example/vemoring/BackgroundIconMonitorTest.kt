package com.example.vemoring

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.SharedVehicleConnection
import com.example.vemoring.vehicle.ReadingState
import com.example.vemoring.vehicle.VehicleField
import org.junit.Assert.*
import org.junit.Test

class BackgroundIconMonitorTest {
    @Test fun receivingContinuesOffScreenEvenWhenIconUpdatesAreDisabled() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences(VehicleIconMonitor.PREFS,android.content.Context.MODE_PRIVATE)
        val hadIcon=prefs.contains(VehicleIconMonitor.ENABLED);val icon=prefs.getBoolean(VehicleIconMonitor.ENABLED,true)
        val hadReceive=prefs.contains(VehicleIconMonitor.RECEIVE_ENABLED);val receive=prefs.getBoolean(VehicleIconMonitor.RECEIVE_ENABLED,true)
        fun await(label:String,condition:()->Boolean) {
            val deadline=SystemClock.uptimeMillis()+20000
            while(!condition() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(100)
            assertTrue(label,condition())
        }
        try {
            prefs.edit().putBoolean(VehicleIconMonitor.ENABLED,false).putBoolean(VehicleIconMonitor.RECEIVE_ENABLED,true).commit()
            if (!IconMonitorVisibility.visible.value) context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Vehicle foreground not ready") {IconMonitorVisibility.visible.value}
            instrumentation.waitForIdleSync()
            VehicleIconMonitor.start(context)
            await("Actual SOC not available") {SharedVehicleConnection.repository(context).state.value[VehicleField.SOC].state==ReadingState.Available}
            val navigation=requireNotNull(context.packageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app"))
            context.startActivity(navigation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Vehicle activity remained visible") {!IconMonitorVisibility.visible.value}
            val before=System.currentTimeMillis()
            instrumentation.runOnMainSync {SharedVehicleConnection.repository(context).refresh()}
            await("SOC stopped when the screen was hidden") {SharedVehicleConnection.repository(context).state.value[VehicleField.SOC].receivedAtMillis?.let {it>=before}==true}
            val receiver=GarageStore(context).receiving()!!.profile.vehicleId
            await("No background care snapshot was published") {com.vemoring.companion.MirrorBus.outgoing.value?.let {it.vehicleId==receiver && it.capturedAt>=before}==true}
            assertFalse(IconMonitorVisibility.visible.value)
        } finally {
            prefs.edit().apply {
                if(hadIcon) putBoolean(VehicleIconMonitor.ENABLED,icon) else remove(VehicleIconMonitor.ENABLED)
                if(hadReceive) putBoolean(VehicleIconMonitor.RECEIVE_ENABLED,receive) else remove(VehicleIconMonitor.RECEIVE_ENABLED)
            }.commit()
            if(!receive) context.stopService(Intent(context,VehicleIconMonitor::class.java))
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Finish restoring before the next test can launch navigation over this pending start.
            await("Vehicle foreground was not restored") { IconMonitorVisibility.visible.value }
            instrumentation.waitForIdleSync()
        }
    }
    @Test fun changesAllIconsWhileNavigationIsVisibleAndRestoresLiveSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertTrue(message, condition())
        }
        if (!IconMonitorVisibility.visible.value) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Main screen did not open") { IconMonitorVisibility.visible.value }
        instrumentation.waitForIdleSync()
        await("No live SDK data") {
            SharedVehicleConnection.repository(context).state.value[VehicleField.SOC].state == ReadingState.Available
        }
        // Open the actual installed navigation app; HOME on Pleos can leave our split-screen visible.
        val navigation = requireNotNull(context.packageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app"))
        context.startActivity(navigation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("VEMORING is still visible") { !IconMonitorVisibility.visible.value }
        val pm = context.packageManager
        fun active(mood: MascotMood) = pm.getComponentEnabledSetting(
            ComponentName(context.packageName, "${context.packageName}.${mood.launcher}")) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        try {
            for ((stage, mood) in listOf(MascotMood.NORMAL, MascotMood.FUEL, MascotMood.WASH,
                MascotMood.OIL, MascotMood.ANGRY, MascotMood.EXHAUSTED, MascotMood.NORMAL).withIndex()) {
                context.sendBroadcast(Intent(context, IconMonitorPreviewReceiver::class.java).putExtra("stage", stage % 6))
                await("Background icon did not become $mood") { active(mood) }
                assertFalse("Icon update brought the app to the front", IconMonitorVisibility.visible.value)
                assertEquals(1, pm.queryIntentActivities(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0).size)
            }
            val before = System.currentTimeMillis()
            instrumentation.runOnMainSync { SharedVehicleConnection.repository(context).refresh() }
            await("SDK disconnected in background") {
                SharedVehicleConnection.repository(context).state.value[VehicleField.SOC].receivedAtMillis?.let { it >= before } == true
            }
        } finally {
            context.sendBroadcast(Intent(context, IconMonitorPreviewReceiver::class.java).putExtra("stage", -1))
            await("Preview source was not cleared") { IconMonitorTestSource.input.value == null }
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Main screen was not restored") { IconMonitorVisibility.visible.value }
            instrumentation.waitForIdleSync()
        }
    }
}
