package com.example.vemoring.phone

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Runs only in the disposable verification app. */
class PhoneLauncherTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pm = context.packageManager
    private val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
    private fun launcher() = pm.queryIntentActivities(query, 0).single().activityInfo.name.substringAfterLast('.')

    @Test fun eachMoodHasExactlyOneVemoringIconAndRestoresDefault() {
        check(context.packageName.endsWith(".verification"))
        try {
            for ((mood, alias) in listOf("FUEL" to "LauncherFuel", "WASH" to "LauncherWash", "OIL" to "LauncherOil",
                "ANGRY" to "LauncherAngry", "EXHAUSTED" to "LauncherExhausted", "NORMAL" to "LauncherNormal")) {
                PhoneLauncher.sync(context, mood)
                assertEquals(1, pm.queryIntentActivities(query, 0).size)
                assertEquals(alias, launcher())
                assertEquals("VEMORING", pm.queryIntentActivities(query, 0).single().loadLabel(pm).toString())
                assertNotNull(pm.getLaunchIntentForPackage(context.packageName))
            }
        } finally { PhoneLauncher.sync(context, "NORMAL") }
    }

    /** Vehicle data received while VEMORING is in the background changes the icon without opening the app. */
    @Test fun receivedVehicleStateUpdatesIconInBackground() {
        check(context.packageName.endsWith(".verification"))
        val id = "launcher-test-${System.nanoTime()}"
        fun await(alias: String) {
            val deadline = SystemClock.uptimeMillis() + 10000
            while (launcher() != alias && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertEquals(alias, launcher())
        }
        fun frame(revision: Long, lowFuel: Boolean, warning: Boolean) = MirrorSnapshot(id, revision, System.currentTimeMillis(),
            JSONObject().put("profile", JSONObject().put("nickname", "시험").put("model", "시험 차량").put("plate", "TEST")
                .put("fuel", "휘발유").put("notifications", false).put("washAlert", 30).put("fuelAlert", 20))
                .put("records", org.json.JSONArray()).put("mood", "NORMAL").put("message", "").put("demo", false)
                .put("fuel", if (lowFuel) 10 else 80).put("battery", JSONObject.NULL).put("cleanliness", JSONObject.NULL)
                .put("oilRemainingKm", JSONObject.NULL)
                .put("signals", JSONObject().put("lowFuel", lowFuel).put("vehicleWarning", warning)).toString())
        // Same order as MirrorBluetoothService: cache the frame, then apply it to the phone-owned care state.
        fun receive(snapshot: MirrorSnapshot) = kotlinx.coroutines.runBlocking {
            MirrorBus.receive(context, snapshot); CareExchange.receive(context, snapshot)
        }
        try {
            receive(frame(1, lowFuel = true, warning = false))
            await("LauncherFuel")
            receive(frame(2, lowFuel = true, warning = true))
            await("LauncherAngry")
            receive(frame(3, lowFuel = false, warning = false))
            await("LauncherNormal")
        } finally {
            CareJournal(context, id).deleteVehicle()
            PhoneLauncher.sync(context, "NORMAL")
        }
    }
}
