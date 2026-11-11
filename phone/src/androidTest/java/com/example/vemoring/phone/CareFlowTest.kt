package com.example.vemoring.phone

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.*
import java.util.UUID

/** Injected protocol/storage/notification tests; this does NOT use a Bluetooth radio or Pleos SDK. */
class CareFlowTest {
    @Test fun outgoingStateKeepsRequestsAndNegativeFeedbackAfterIntervalAcknowledgement() {
        val id=UUID.randomUUID().toString()
        val journal=CareJournal(context,id)
        journal.saveAuthority(CareAuthority(id,7,"{}","[]"))
        val start=Instant.parse("2026-11-01T00:00:00Z")
        journal.mergeIntervals(listOf(TravelInterval("trip",start,start.plusSeconds(60),1.0)))
        val records=JSONArray().put(JSONObject().put("id","deleted-record").put("visitId","rejected-visit")
            .put("deleted",true).put("cancelled",true))
        journal.queueRequest(JSONObject(),records)
        val first=journal.outgoingState()
        assertEquals(7L,first.request!!.getLong("base"))
        assertEquals("trip",first.intervals.getJSONObject(0).getString("id"))
        assertEquals("rejected-visit",first.rejectedVisits.getString(0))
        assertEquals("deleted-record",first.deletedRecords.getString(0))
        journal.acknowledgeIntervals(JSONArray().put(JSONObject().put("id","trip")
            .put("digest",intervalDigest(first.intervals.getJSONObject(0)))))
        val second=journal.outgoingState()
        assertEquals(0,second.intervals.length())
        assertEquals(first.request.toString(),second.request.toString())
        assertEquals(first.rejectedVisits.toString(),second.rejectedVisits.toString())
        assertEquals(first.deletedRecords.toString(),second.deletedRecords.toString())
        journal.deleteVehicle()
    }
    @Test fun multipleCarsKeepTheirOwnCachedFramesAndRejectOldReplays()=runBlocking {
        val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString()
        val one=snapshot(a,7);val two=snapshot(b,20)
        MirrorBus.receive(context,one);MirrorBus.receive(context,two)
        MirrorBus.receive(context,one.copy(revision=6))
        assertEquals(7L,MirrorBus.vehicles.value.getValue(a).snapshot.revision)
        assertEquals(20L,MirrorBus.vehicles.value.getValue(b).snapshot.revision)
        val reopened=MirrorCacheStore(context).loadAll()
        assertEquals(7L,reopened.getValue(a).snapshot.revision)
        assertEquals(20L,reopened.getValue(b).snapshot.revision)
        assertEquals(b,MirrorCacheStore(context).load()!!.snapshot.vehicleId)
    }
    @Test fun explicitRecordDeletionWinsAcrossOldVersionsWithoutTouchingOtherCars()=runBlocking {
        val id=UUID.randomUUID().toString();val other=UUID.randomUUID().toString();val first=snapshot(id)
        CareExchange.receive(context,first);CareExchange.receive(context,snapshot(other))
        val old=CareJournal(context,id).authority()!!
        CareExchange.editPhone(context,id,JSONObject(old.profile).put("washAlert",20),JSONArray(old.records))
        val j=JSONObject(first.content)
        j.getJSONObject("care").put("deletedRecords",JSONArray().put("old-wash"))
        CareExchange.receive(context,first.copy(content=j.toString())) // duplicate/reordered envelope
        assertTrue(JSONArray(CareJournal(context,id).authority()!!.records).getJSONObject(0).getBoolean("deleted"))
        CareExchange.receive(context,first.copy(revision=2))
        assertTrue(JSONArray(CareJournal(context,id).authority()!!.records).getJSONObject(0).getBoolean("cancelled"))
        assertFalse(JSONArray(CareJournal(context,other).authority()!!.records).getJSONObject(0).optBoolean("cancelled"))
        CareJournal(context,id).deleteVehicle();CareJournal(context,other).deleteVehicle()
        base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun deletionAndCurrentSettingsRequestCanApplyTogether()=runBlocking {
        val id=UUID.randomUUID().toString();val first=snapshot(id);CareExchange.receive(context,first)
        val old=CareJournal(context,id).authority()!!
        val request=JSONObject().put("id","delete-and-edit").put("base",old.version)
            .put("profile",JSONObject(old.profile).put("fuelAlert",25)).put("records",JSONArray(old.records))
            .put("deletedRecords",JSONArray().put("old-wash"))
        val j=JSONObject(first.content);j.getJSONObject("care").put("request",request)
        CareExchange.receive(context,first.copy(revision=2,content=j.toString()))
        val next=CareJournal(context,id).authority()!!
        assertEquals(25,JSONObject(next.profile).getInt("fuelAlert"))
        assertTrue(JSONArray(next.records).getJSONObject(0).getBoolean("deleted"))
        assertTrue("delete-and-edit" in next.acceptedRequests)
        CareJournal(context,id).deleteVehicle();base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "care_test_${UUID.randomUUID()}_"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String) = base.getDatabasePath(prefix+name)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) = base.openOrCreateDatabase(prefix+name,mode,factory)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler: android.database.DatabaseErrorHandler?) = base.openOrCreateDatabase(prefix+name,mode,factory,errorHandler)
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
    }
    private fun snapshot(id: String, revision: Long = 1, lowWasher: Boolean = false): MirrorSnapshot {
        val profile = JSONObject().put("nickname", "시험 차량").put("model", "시험 모델").put("plate", "")
            .put("fuel", "휘발유").put("washAlert", 25).put("fuelAlert", 20).put("notifications", true)
        val date = LocalDate.now(CARE_ZONE).minusDays(45)
        val records = JSONArray().put(JSONObject().put("id", "old-wash").put("kind", "wash").put("date", date)
            .put("title", "시험 세차 기록").put("detail", "시험 공급자: 실제 SDK 아님"))
        val now = System.currentTimeMillis()
        val content = JSONObject().put("profile", profile).put("records", records).put("mood", "WASH")
            .put("message", "시험 데이터").put("demo", false).put("fuel", JSONObject.NULL)
            .put("battery", JSONObject.NULL).put("cleanliness", JSONObject.NULL).put("oilRemainingKm", JSONObject.NULL)
            .put("care", JSONObject().put("intervals", JSONArray()).put("warnings", JSONObject().put("WASHER",
                JSONObject().put("status", "Available").put("warning", lowWasher).put("at", now))))
        return MirrorSnapshot(id, revision, now, content.toString())
    }
    @Test fun framedMessageIsPersistedAcknowledgedAndThresholdNotifiedOnce() = runBlocking {
        val id = UUID.randomUUID().toString()
        val stream = ByteArrayOutputStream(); MirrorFrames.write(stream, snapshot(id))
        val received = MirrorFrames.read(ByteArrayInputStream(stream.toByteArray()))
        val response = CareExchange.receive(context, received)
        assertEquals(1L, response.getLong("ack"))
        val store = CareJournal(context, id)
        assertEquals(25, JSONObject(store.authority()!!.profile).getInt("washAlert"))
        assertTrue("Grant notification permission to verification package", CareNotifications.allowed(context))
        assertTrue(store.hasNotified("wash:old-wash"))
        val before = base.getSystemService(NotificationManager::class.java).activeNotifications
            .first { it.tag == "$id:wash:old-wash" }.postTime
        CareExchange.receive(context, received)
        assertEquals(before, base.getSystemService(NotificationManager::class.java).activeNotifications
            .first { it.tag == "$id:wash:old-wash" }.postTime)
        base.getSystemService(NotificationManager::class.java).cancel("$id:wash:old-wash", 3010)
        store.deleteVehicle()
    }
    @Test fun duplicateCancelledVisitCannotReturnAndUnrelatedVehicleSurvives() = runBlocking {
        val id = UUID.randomUUID().toString(); val other = UUID.randomUUID().toString()
        CareExchange.receive(context, snapshot(id)); CareExchange.receive(context, snapshot(other))
        val visit = DetectedWash("test-visit", Instant.now().minusSeconds(60))
        assertTrue(CareExchange.detected(context, id, visit))
        assertFalse(CareExchange.detected(context, id, visit))
        assertTrue(CareExchange.cancel(context, id, visit.visitId))
        assertFalse(CareExchange.detected(context, id, visit))
        val reloaded = CareJournal(context, id)
        assertTrue(reloaded.baselines(JSONArray(reloaded.authority()!!.records)).first { it.visitId == visit.visitId }.cancelled)
        val cancelled = reloaded.authority()!!
        CareExchange.editPhone(context, id, JSONObject(cancelled.profile), JSONArray(snapshot(id).content.let(::JSONObject).getJSONArray("records").toString()))
        assertTrue(reloaded.baselines(JSONArray(reloaded.authority()!!.records)).first { it.visitId == visit.visitId }.cancelled)
        assertNotNull(CareJournal(context, other).authority())
        reloaded.deleteVehicle(); assertNotNull(CareJournal(context, other).authority())
        CareJournal(context, other).deleteVehicle()
        base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun staleVehicleSettingsCannotOverwritePhoneAndValidRequestAppliesOnce() = runBlocking {
        val id = UUID.randomUUID().toString(); val first = snapshot(id)
        CareExchange.receive(context, first)
        val store = CareJournal(context, id); val original = store.authority()!!
        val changed = JSONObject(original.profile).put("washAlert", 20)
        val phone = CareExchange.editPhone(context, id, changed, JSONArray(original.records))
        fun request(baseVersion: Long, revision: Long) = first.copy(revision = revision,
            content = JSONObject(first.content).put("care", JSONObject().put("request",
                JSONObject().put("id", "request-$baseVersion").put("base", baseVersion))).toString())
        CareExchange.receive(context, request(1, 2))
        assertEquals(phone.version, store.authority()!!.version)
        val valid = request(phone.version, 3)
        CareExchange.receive(context, valid)
        val accepted = store.authority()!!.version
        CareExchange.receive(context, valid)
        assertEquals(accepted, store.authority()!!.version)
        store.deleteVehicle(); base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun demoAndDisconnectedReadingsDoNotBecomeMaintenanceAlerts() = runBlocking {
        val id = UUID.randomUUID().toString(); val real = snapshot(id, lowWasher = true)
        val demo = real.copy(content = JSONObject(real.content).put("demo", true).toString())
        CareExchange.receive(context, demo)
        assertNull(CareJournal(context, id).authority())
        val disconnected = real.copy(content = JSONObject(real.content).also {
            it.getJSONObject("care").getJSONObject("warnings").getJSONObject("WASHER").put("status", "Disconnected")
        }.toString())
        CareExchange.receive(context, disconnected)
        assertNull(CareJournal(context, id).lastWarning("WASHER"))
        CareExchange.receive(context, real.copy(revision = 2))
        assertEquals("LOW", CareJournal(context, id).lastWarning("WASHER"))
        CareExchange.receive(context, disconnected.copy(revision = 3))
        assertEquals("LOW", CareJournal(context, id).lastWarning("WASHER"))
        CareJournal(context, id).deleteVehicle(); base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun intervalsAreTransferredInBoundedBatchesAndWeatherSurvivesReload() = runBlocking {
        val sourceContext = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + "source_" + name, mode)
        }
        val id = UUID.randomUUID().toString()
        val source = CareJournal(sourceContext, id)
        val start = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.HOURS).minusHours(4).toInstant()
        val parts = (0L until 130).map { n ->
            val s = start.plusSeconds(n*60); val e = s.plusSeconds(60)
            TravelInterval("part-$n", s, e, 1.0, located = n == 0L, weather = if(n == 0L)
                HourWeather(start, .1, 0.0, 0.0, 160.0, 90.0, e, "test-weather") else null)
        }
        source.mergeIntervals(parts)
        for (revision in 1L..2L) {
            val pending = source.outgoingIntervals()
            assertEquals(if(revision == 1L) 128 else 2, pending.length())
            val sample = snapshot(id, revision)
            val content = JSONObject(sample.content)
            content.getJSONObject("care").put("intervals", pending)
            val response = CareExchange.receive(context, sample.copy(content = content.toString()))
            CareExchange.reply(sourceContext, response)
        }
        assertEquals(0, CareJournal(sourceContext, id).outgoingIntervals().length())
        assertEquals(parts, CareJournal(context, id).intervals())
        source.deleteVehicle(); CareJournal(context, id).deleteVehicle()
        base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun disabledSettingDoesNotConsumeFutureNotificationAndPhoneEnableEvaluatesOnce() = runBlocking {
        val id = UUID.randomUUID().toString(); val initial = snapshot(id)
        val content = JSONObject(initial.content)
        content.getJSONObject("profile").put("notifications", false)
        CareExchange.receive(context, initial.copy(content = content.toString()))
        val store = CareJournal(context, id); assertFalse(store.hasNotified("wash:old-wash"))
        val state = store.authority()!!
        CareExchange.editPhone(context, id, JSONObject(state.profile).put("notifications", true), JSONArray(state.records))
        assertTrue(store.hasNotified("wash:old-wash"))
        store.deleteVehicle(); base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun falseRecognitionWinsOverOldSettingsVersionAndCannotResurrect() = runBlocking {
        val id = UUID.randomUUID().toString()
        val first = snapshot(id)
        CareExchange.receive(context, first)
        val wash = DetectedWash("false-wash", Instant.now().minusSeconds(60))
        CareExchange.detected(context, id, wash)
        val store = CareJournal(context, id)
        val before = store.authority()!!
        CareExchange.editPhone(context, id, JSONObject(before.profile).put("washAlert", 20), JSONArray(before.records))
        CareExchange.receive(context, first.copy(revision = 5))
        val content = JSONObject(first.content).put("care", JSONObject().put("request", JSONObject()
            .put("id", "offline-reject").put("base", 1).put("rejectedVisits", JSONArray().put(wash.visitId).put("late-evidence"))))
        CareExchange.receive(context, first.copy(revision = 2, content = content.toString()))
        assertEquals(20, JSONObject(store.authority()!!.profile).getInt("washAlert"))
        assertTrue(store.baselines(JSONArray(store.authority()!!.records)).first { it.visitId == wash.visitId }.cancelled)
        val version = store.authority()!!.version
        CareExchange.receive(context, first.copy(revision = 3, content = content.toString()))
        assertEquals(version, store.authority()!!.version)
        assertFalse(CareExchange.detected(context, id, wash))
        assertFalse(CareExchange.detected(context, id, DetectedWash("late-evidence", Instant.now())))
        store.deleteVehicle(); base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun dismissingBannerKeepsAppliedWashAndSurvivesReload() = runBlocking {
        val id = UUID.randomUUID().toString()
        val first = snapshot(id).let { it.copy(content = JSONObject(it.content).put("records", JSONArray()).toString()) }
        CareExchange.receive(context, first)
        val wash = DetectedWash("uncertain-but-qualified", Instant.now().minusSeconds(60))
        assertTrue(CareExchange.detected(context, id, wash))
        val store = CareJournal(context, id)
        assertEquals(100, store.score(JSONArray(store.authority()!!.records)).displayed)
        assertEquals(wash.visitId, store.pendingWashConfirmation()!!.visitId)
        CareExchange.dismissConfirmation(context, id, wash.visitId)
        val restarted = CareJournal(context, id)
        assertNull(restarted.pendingWashConfirmation())
        assertEquals(100, restarted.score(JSONArray(restarted.authority()!!.records)).displayed)
        assertTrue(CareExchange.cancel(context, id, wash.visitId))
        assertNull(restarted.score(JSONArray(restarted.authority()!!.records)).displayed)
        store.deleteVehicle(); base.getSystemService(NotificationManager::class.java).cancelAll()
    }
    @Test fun notificationWrongRecognitionActionTargetsOnlyItsVehicle() = runBlocking {
        assertTrue(base.packageName.endsWith(".verification"))
        val a = UUID.randomUUID().toString(); val b = UUID.randomUUID().toString()
        val manager = base.getSystemService(NotificationManager::class.java)
        try {
            CareExchange.receive(base, snapshot(a)); CareExchange.receive(base, snapshot(b))
            val wash = DetectedWash("same-visit-in-two-vehicles", Instant.now().minusSeconds(60))
            CareExchange.detected(base, a, wash); CareExchange.detected(base, b, wash)
            val first = manager.activeNotifications.first { it.tag == "$a:detected:${wash.visitId}" }.notification
            val other = manager.activeNotifications.first { it.tag == "$b:detected:${wash.visitId}" }.notification
            assertEquals("세차한 것 같아!", first.extras.getString(android.app.Notification.EXTRA_TITLE))
            assertEquals("세차 안 했어요", first.actions.first().title.toString())
            assertNotEquals(first.actions.first().actionIntent, other.actions.first().actionIntent)
            first.actions.first().actionIntent.send()
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            fun cancelled() = CareJournal(base, a).baselines(JSONArray(CareJournal(base, a).authority()!!.records))
                .first { it.visitId == wash.visitId }.cancelled
            while(!cancelled() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50)
            assertTrue(cancelled())
            assertFalse(CareJournal(base, b).baselines(JSONArray(CareJournal(base, b).authority()!!.records))
                .first { it.visitId == wash.visitId }.cancelled)
        } finally { CareJournal(base, a).deleteVehicle(); CareJournal(base, b).deleteVehicle(); manager.cancelAll() }
    }
    @Test fun qualifiedParkedVisitIsAppliedWithoutAnyExtraConfirmationGate() = runBlocking {
        val id = UUID.randomUUID().toString()
        val first = snapshot(id).let { it.copy(content = JSONObject(it.content).put("records", JSONArray()).toString()) }
        CareExchange.receive(context, first)
        val detector = WashVisitDetector("parked-wash", WashFacility.Stationary)
        val start = Instant.now().minusSeconds(240)
        fun sample(t: Long, outside: Boolean = false) = start.plusSeconds(t).let { at ->
            WashObservation(at, at, 2.0, if(outside) ZoneEvidence.Outside else ZoneEvidence.Inside,
                EvidenceSource.Vehicle, !outside, if(outside) "D" else "P", consistentMotion = outside, facilityExit = outside)
        }
        for(t in 0L..195L step 5) assertFalse(CareExchange.observeWashVisit(context, id, detector, sample(t)))
        for(t in 200L..225L step 5) assertFalse(CareExchange.observeWashVisit(context, id, detector, sample(t, true)))
        assertTrue(CareExchange.observeWashVisit(context, id, detector, sample(230, true)))
        val store = CareJournal(context, id)
        assertEquals(100, store.score(JSONArray(store.authority()!!.records)).displayed)
        assertEquals("parked-wash", store.pendingWashConfirmation()!!.visitId)
        assertTrue(CareExchange.cancel(context, id, "parked-wash"))
        assertNull(store.score(JSONArray(store.authority()!!.records)).value)
        store.deleteVehicle(); base.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
