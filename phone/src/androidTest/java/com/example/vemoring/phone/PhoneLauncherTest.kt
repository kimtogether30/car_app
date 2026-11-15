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
        // Oil due is no longer a received flag: the phone derives it from a catalogued model and its own ENGINE_OIL records.
        val profile = JSONObject().put("nickname", "시험").put("model", "현대 아반떼").put("plate", "TEST")
            .put("fuel", "휘발유").put("notifications", false).put("washAlert", 30).put("fuelAlert", 20)
        val today = java.time.LocalDate.now(com.vemoring.companion.CARE_ZONE)
        fun oil(recordId: String, date: java.time.LocalDate) = JSONObject().put("id", recordId).put("kind", "maintenance")
            .put("maintenanceType", "ENGINE_OIL").put("date", date.toString()).put("title", "엔진오일 교체").put("detail", "")
        fun frame(revision: Long, lowFuel: Boolean) = MirrorSnapshot(id, revision, System.currentTimeMillis(),
            JSONObject().put("profile", profile)
                .put("records", org.json.JSONArray().put(oil("oil-recent", today))).put("mood", "NORMAL").put("message", "").put("demo", false)
                .put("fuel", if (lowFuel) 10 else 80).put("battery", JSONObject.NULL).put("cleanliness", JSONObject.NULL)
                .put("oilRemainingKm", JSONObject.NULL)
                .put("signals", JSONObject().put("lowFuel", lowFuel).put("oilDue", true)).toString())
        // Same order as MirrorBluetoothService: cache the frame, then apply it to the phone-owned care state.
        fun receive(snapshot: MirrorSnapshot) = kotlinx.coroutines.runBlocking {
            MirrorBus.receive(context, snapshot); CareExchange.receive(context, snapshot)
        }
        fun records(vararg values: JSONObject) = kotlinx.coroutines.runBlocking {
            CareExchange.editPhone(context, id, profile, org.json.JSONArray().also {a -> values.forEach {a.put(it)}})
        }
        try {
            receive(frame(1, lowFuel = true))
            await("LauncherFuel") // A recent change is not due, and the received oilDue flag is ignored.
            records(oil("oil-old", today.minusYears(1)))
            receive(frame(2, lowFuel = true))
            await("LauncherAngry")
            records(oil("oil-new", today))
            receive(frame(3, lowFuel = false))
            await("LauncherNormal")
        } finally {
            CareJournal(context, id).deleteVehicle()
            PhoneLauncher.sync(context, "NORMAL")
        }
    }
}
