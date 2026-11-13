package com.example.vemoring.phone

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in: -e replayFrame /data/local/tmp/vehicle_frame.json
 * Replays a snapshot that the vehicle app actually produced on Pleos through the same calls the Bluetooth receiver
 * makes (MirrorBus.receive, then CareExchange.receive). Delivery is treated as instant, so the frame's own timestamps
 * are shifted to now. This is not a radio test. Data stays in the verification app for a manual look afterwards.
 */
class VehicleFrameReplayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shift(o: Any?, delta: Long, near: Long): Any? = when (o) {
        is JSONObject -> o.apply { keys().asSequence().toList().forEach { k ->
            val v = opt(k)
            if ((k.endsWith("At") || k == "at") && v is Number && kotlin.math.abs(v.toLong() - near) < 86_400_000L) put(k, v.toLong() + delta)
            else shift(v, delta, near)
        } }
        is JSONArray -> o.apply { for (i in 0 until length()) shift(opt(i), delta, near) }
        else -> o
    }

    @Test fun vehicleProducedFrameUpdatesPhone() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("replayFrame")
        assumeTrue("Run with -e replayFrame <file>", path != null)
        check(context.packageName.endsWith(".verification"))
        val wire = JSONObject(File(path!!).readText())
        val captured = wire.getLong("capturedAt")
        val now = System.currentTimeMillis()
        val content = shift(JSONObject(wire.getString("content")), now - captured, captured) as JSONObject
        val frame = MirrorSnapshot(wire.getString("vehicleId"), now, now, content.toString())

        MirrorBus.receive(context, frame)
        val reply = CareExchange.receive(context, frame)
        assertEquals(now, reply.getLong("ack"))

        val history = NotificationHistory(context).use { it.recent(frame.vehicleId, 50) }
        android.util.Log.i("VehicleFrameReplay", "battery=${content.opt("battery")} mood=${content.opt("mood")} " +
            "notifications=${history.map { "${it.bannerKey}:${it.title}:${it.delivery}" }}")
        val low = history.firstOrNull { it.bannerKey == "FUEL_NEEDED" }
        assertNotNull("A low-energy frame must produce one low-energy notification", low)
        if (JSONObject(content.getJSONObject("profile").toString()).optString("fuel") == "전기차")
            assertTrue("EV wording must say charging: ${low!!.title}", low!!.title.contains("충전"))

        // Launcher icon follows the received state while no VEMORING screen is open.
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val deadline = SystemClock.uptimeMillis() + 10_000
        fun alias() = pm.queryIntentActivities(query, 0).singleOrNull()?.activityInfo?.name?.substringAfterLast('.')
        while (SystemClock.uptimeMillis() < deadline && alias() != "LauncherFuel") SystemClock.sleep(200)
        assertEquals("LauncherFuel", alias())
    }
}
