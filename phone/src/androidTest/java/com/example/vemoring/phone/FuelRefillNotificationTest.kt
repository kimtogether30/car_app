package com.example.vemoring.phone

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Injected frames through the real receive/notification path. Not a Bluetooth radio or Pleos SDK test. */
class FuelRefillNotificationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "fuel_test_${UUID.randomUUID()}_"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String) = base.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
            base.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,
            errorHandler: android.database.DatabaseErrorHandler?) = base.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
    }
    private val id = UUID.randomUUID().toString()
    private var revision = 0L

    private fun frame(fuel: Int, episode: FuelEpisode): MirrorSnapshot {
        val now = System.currentTimeMillis()
        val profile = JSONObject().put("nickname", "주유 시험").put("model", "시험 모델").put("plate", "")
            .put("fuel", "휘발유").put("washAlert", 25).put("fuelAlert", 20).put("notifications", true)
        // A recent wash keeps the car clean, so only fuel decides the banner.
        val records = JSONArray().put(JSONObject().put("id", "recent-wash").put("kind", "wash")
            .put("date", LocalDate.now(CARE_ZONE).minusDays(1)).put("title", "시험 세차").put("detail", "시험 공급자"))
        val content = JSONObject().put("profile", profile).put("records", records).put("mood", "NORMAL").put("message", "")
            .put("demo", false).put("fuel", fuel).put("fuelAt", now).put("battery", JSONObject.NULL)
            .put("cleanliness", JSONObject.NULL).put("oilRemainingKm", JSONObject.NULL)
            .put("care", JSONObject().put("intervals", JSONArray()).put("warnings", JSONObject()).put("fuelEpisode", episode.json()))
        return MirrorSnapshot(id, ++revision, now, content.toString())
    }
    private fun count(key: String) = NotificationHistory(context).use { h -> h.recent(id, 200).count { it.bannerKey == key } }

    @Test fun sloshingDoesNotReAlertAndConfirmedRefuelSendsOneCompletion() = runBlocking {
        assertTrue("Grant notification permission to verification package", CareNotifications.allowed(context))
        var episode = FuelRefillPolicy.observe(FuelEpisode(), 18, 20)
        CareExchange.receive(context, frame(18, episode))
        assertEquals(1, count("FUEL_NEEDED"))

        // Fuel sloshes across the 20% threshold while driving: still one low-fuel alert.
        for (p in listOf(21, 19, 23, 18)) {
            episode = FuelRefillPolicy.observe(episode, p, 20)
            CareExchange.receive(context, frame(p, episode))
        }
        // A phone-side edit re-evaluates without fresh telemetry; the remembered state must not flip the episode.
        val authority = CareJournal(context, id).authority()!!
        CareExchange.editPhone(context, id, JSONObject(authority.profile), JSONArray(authority.records))
        CareExchange.receive(context, frame(19, episode))
        assertEquals(1, count("FUEL_NEEDED"))
        assertEquals(0, count("FUEL_DONE"))

        // 29% (< T+10) is not a refuel.
        episode = FuelRefillPolicy.observe(episode, 29, 20)
        CareExchange.receive(context, frame(29, episode))
        assertEquals(0, count("FUEL_DONE"))

        // The vehicle confirmed three separate reads >= 30%: one completion banner, also across replays.
        episode = FuelRefillPolicy.confirmed(episode, 45, 20, System.currentTimeMillis(), "refill-1")
        repeat(3) { CareExchange.receive(context, frame(45, episode)) }
        assertEquals(1, count("FUEL_DONE"))

        // Re-armed: the next drop below the threshold alerts once more.
        episode = FuelRefillPolicy.observe(episode, 20, 20)
        CareExchange.receive(context, frame(20, episode))
        CareExchange.receive(context, frame(21, FuelRefillPolicy.observe(episode, 21, 20)))
        assertEquals(2, count("FUEL_NEEDED"))
        assertEquals(1, count("FUEL_DONE"))
    }

    @After fun cleanup() {
        val manager = base.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("$id:") == true }.forEach { manager.cancel(it.tag, it.id) }
        CareJournal(context, id).deleteVehicle()
    }
}
