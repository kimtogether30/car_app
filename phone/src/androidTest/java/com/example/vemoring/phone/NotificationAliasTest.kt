package com.example.vemoring.phone

import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Regression: changing the home-screen icon must not invalidate an already posted notification. */
class NotificationAliasTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val vehicle="alias-test-${UUID.randomUUID()}"
    private val manager get()=context.getSystemService(NotificationManager::class.java)

    @After fun cleanup() {
        manager.cancel("$vehicle:existing",3010)
        NotificationHistory(context).use {it.deleteVehicle(vehicle)}
        PhoneLauncher.sync(context,"NORMAL")
    }

    @Test fun postedNotificationStillOpensAfterItsLauncherAliasIsDisabled() {
        check(context.packageName.endsWith(".verification"))
        PhoneLauncher.sync(context,"NORMAL")
        val oldEntry=context.packageManager.getLaunchIntentForPackage(context.packageName)!!.component!!
        val d=BannerCatalog["FUEL_NEEDED"]
        assertEquals(NotificationDelivery.Delivered,BannerNotifications.dispatch(context,CareNotificationEntry(
            vehicle,"existing",d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source="Simulation")))
        PhoneLauncher.sync(context,"FUEL")
        assertNotEquals(oldEntry,context.packageManager.getLaunchIntentForPackage(context.packageName)!!.component)
        val until=System.currentTimeMillis()+4000
        var posted=manager.activeNotifications.firstOrNull {it.tag=="$vehicle:existing"}
        while(posted==null && System.currentTimeMillis()<until) {
            Thread.sleep(40);posted=manager.activeNotifications.firstOrNull {it.tag=="$vehicle:existing"}
        }
        assertNotNull(posted)
        val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        try {
            // A visible test activity avoids confusing Android's background-start restriction with alias failure.
            posted!!.notification.contentIntent.send()
            val activity=monitor.waitForActivityWithTimeout(4000)
            assertNotNull("An old notification must open MainActivity after its original launcher alias is disabled",activity)
            assertEquals("existing",activity.intent.getStringExtra("careEvent"))
            assertEquals(vehicle,activity.intent.getStringExtra("vehicleId"))
        } finally {instrumentation.removeMonitor(monitor)}
    }
}
