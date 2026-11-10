package com.vemoring.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Protocol v2 reply both ACKs the exact snapshot and returns phone-owned canonical state. */
object CareExchange {
    private val mutex = Mutex()
    val canonical = MutableStateFlow<CareAuthority?>(null)
    val confirmationRevision = MutableStateFlow(0L)
    suspend fun dismissConfirmation(context: Context, vehicleId: String, visitId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            CareJournal(context, vehicleId).dismissWashConfirmation(visitId)
            CareNotifications.cancelDetection(context, vehicleId, visitId)
            confirmationRevision.value++
        }
    }
    suspend fun loadVehicle(context: Context, vehicleId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { canonical.value = CareJournal(context, vehicleId).authority() }
    }
    suspend fun refreshAll(context: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        context.getSharedPreferences("care_journal_v1", Context.MODE_PRIVATE).all.values.filterIsInstance<String>().forEach { raw ->
            val state = runCatching { JSONObject(raw).optJSONObject("authority")?.let(CareAuthority::from) }.getOrNull()
            if (state != null) {
                val store = CareJournal(context, state.vehicleId)
                CareNotifications.evaluate(context, store, state, store.score(JSONArray(state.records)), null)
            }
        }
        CareDailyCheck.schedule(context)
    } }

    suspend fun detected(context: Context, vehicleId: String, wash: DetectedWash): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { applyDetection(context, vehicleId, wash) }
    }
    /** Phone-side observation boundary: qualifying facility-specific evidence is applied immediately. */
    suspend fun observeWashVisit(context: Context, vehicleId: String, detector: WashVisitDetector,
        observation: WashObservation): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val wash = detector.observe(observation) ?: return@withLock false
        applyDetection(context, vehicleId, wash)
    } }
    private fun applyDetection(context: Context, vehicleId: String, wash: DetectedWash): Boolean {
        val store = CareJournal(context, vehicleId)
        val next = store.acceptDetectedWash(wash) ?: return false
        canonical.value = next
        CareNotifications.detected(context, store, wash)
        return true
    }
    suspend fun cancel(context: Context, vehicleId: String, visitId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = CareJournal(context, vehicleId)
            val next = store.cancelDetectedWash(visitId, java.time.Instant.now()) ?: run {
                confirmationRevision.value++; return@withLock false
            }
            store.rememberRejectedVisits(setOf(visitId))
            BannerNotifications.cancelVisit(context,vehicleId,visitId)
            canonical.value = next
            CareNotifications.cancelDetection(context, vehicleId, visitId)
            CareNotifications.evaluate(context, store, next, store.score(JSONArray(next.records)), null)
            true
        }
    }

    suspend fun receive(context: Context, snapshot: MirrorSnapshot): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            val j = JSONObject(snapshot.content)
            if (j.optBoolean("demo")) return@withLock JSONObject().put("schema", 2)
                .put("vehicleId", snapshot.vehicleId).put("ack", snapshot.revision).put("demo", true)
            val journal = CareJournal(context, snapshot.vehicleId)
            // Negative feedback is terminal and commutative, independent of message/settings ordering.
            val rejection = j.optJSONObject("care")?.optJSONObject("request")?.optJSONArray("rejectedVisits")
            if(rejection != null) {
                require(rejection.length() <= 5000)
                journal.rememberRejectedVisits((0 until rejection.length()).map { rejection.getString(it) }.toSet())
                repeat(rejection.length()) { BannerNotifications.cancelVisit(context,snapshot.vehicleId,rejection.getString(it)) }
                journal.authority()?.let { current -> journal.saveAuthority(current.rejectVisits(journal.rejectedVisits())) }
            }
            val previous = journal.authority()
            if (previous != null && snapshot.revision <= journal.receivedRevision()) {
                canonical.value = previous
                return@withLock JSONObject().put("schema", 2).put("vehicleId", snapshot.vehicleId)
                    .put("ack", snapshot.revision).put("authority", previous.json()).put("intervalAck", intervalAck(j))
            }
            var state = journal.authority()
            if (state == null) {
                // Existing phone preferences win on first import. Never overwrite them with vehicle defaults.
                val profile = j.getJSONObject("profile")
                val local = context.getSharedPreferences("phone_ui_v1", Context.MODE_PRIVATE).getString("data", null)?.let(::JSONObject)
                    ?.takeIf { it.optString("model") == profile.optString("model") && it.optString("plate") == profile.optString("plate") }
                val records = if (local == null) j.getJSONArray("records") else JSONArray().also { a ->
                    val entries = local.optJSONArray("entries") ?: JSONArray()
                    repeat(entries.length()) { n -> val e = entries.getJSONObject(n)
                        a.put(JSONObject(e.toString()).put("kind", when(e.getString("kind")) {
                            "세차" -> "wash"; "주유" -> "fuel"; else -> "oil" }))
                    }
                    profile.put("washAlert", local.optInt("washAlert", 30)).put("notifications", local.optBoolean("notifications", true))
                }
                state = CareAuthority(snapshot.vehicleId, 1, profile.toString(), records.toString())
            } else {
                val request = j.optJSONObject("care")?.optJSONObject("request")
                if (request != null && !j.optBoolean("demo")) {
                    state = state.request(request.getString("id"), request.getLong("base"),
                        (request.optJSONObject("profile") ?: j.getJSONObject("profile")).toString(),
                        (request.optJSONArray("records") ?: j.getJSONArray("records")).toString())
                }
            }
            state = state.rejectVisits(journal.rejectedVisits())
            validate(state)
            j.optJSONObject("care")?.optJSONArray("intervals")?.let { journal.importIntervals(it) }
            journal.saveAuthority(state)
            journal.receivedRevision(snapshot.revision)
            canonical.value = state
            val score = journal.score(JSONArray(state.records))
            BannerNotifications.recordChanges(context,previous,state)
            if (!j.optBoolean("demo")) CareNotifications.evaluate(context, journal, state, score, j.optJSONObject("care"),j.put("observedAt",snapshot.capturedAt))
            CareDailyCheck.schedule(context)
            JSONObject().put("schema", 2).put("vehicleId", snapshot.vehicleId).put("ack", snapshot.revision)
                .put("authority", state.json()).put("cleanliness", score.displayed ?: JSONObject.NULL).put("intervalAck", intervalAck(j))
        }
    }

    suspend fun reply(context: Context, response: JSONObject) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(response.getInt("schema") == 2)
            if (!response.has("authority")) return@withLock
            val authority = CareAuthority.from(response.getJSONObject("authority"))
            require(authority.vehicleId == response.getString("vehicleId"))
            validate(authority)
            val store = CareJournal(context, authority.vehicleId)
            if ((store.authority()?.version ?: 0) <= authority.version) {
                store.saveAuthority(authority); store.resolveRequest(authority); canonical.value = authority
            }
            response.optJSONArray("intervalAck")?.let { store.acknowledgeIntervals(it) }
            val sent = MirrorBus.outgoing.value?.takeIf { it.vehicleId == authority.vehicleId }
            if (sent != null) {
                val content = JSONObject(sent.content)
                val care = content.optJSONObject("care")
                val next = store.outgoingIntervals()
                if (care != null && (next.length() > 0 || (care.optJSONArray("intervals")?.length() ?: 0) > 0)) {
                    care.put("intervals", next).put("request", store.pendingRequest())
                    MirrorBus.publish(context, content.toString(), authority.vehicleId)
                }
            }
        }
    }

    suspend fun editPhone(context: Context, vehicleId: String, profile: JSONObject, records: JSONArray): CareAuthority =
        withContext(Dispatchers.IO) { mutex.withLock {
            val store = CareJournal(context, vehicleId)
            val previous = requireNotNull(store.authority())
            val next = previous.copy(version = previous.version + 1, profile = profile.toString(), records = previous.protectCancelledVisits(records.toString()))
            validate(next)
            store.saveAuthority(next); canonical.value = next
            BannerNotifications.recordChanges(context,previous,next)
            CareNotifications.evaluate(context, store, next, store.score(JSONArray(next.records)), null)
            next
        } }
    private fun validate(state: CareAuthority) {
        require(state.version > 0 && state.acceptedRequests.size <= 128)
        val content = JSONObject().put("profile", JSONObject(state.profile)).put("records", JSONArray(state.records))
            .put("mood", "NORMAL").put("message", "").put("demo", false)
            .put("fuel", JSONObject.NULL).put("battery", JSONObject.NULL).put("cleanliness", JSONObject.NULL).put("oilRemainingKm", JSONObject.NULL)
        MirrorSnapshot.decode(MirrorSnapshot(state.vehicleId, state.version, 1, content.toString()).encode())
    }
    private fun intervalAck(content: JSONObject): JSONArray {
        val parts = content.optJSONObject("care")?.optJSONArray("intervals") ?: JSONArray()
        require(parts.length() <= 128)
        return JSONArray().also { a -> repeat(parts.length()) { n -> val part = parts.getJSONObject(n)
            a.put(JSONObject().put("id", part.getString("id")).put("digest", intervalDigest(part)))
        } }
    }
}
