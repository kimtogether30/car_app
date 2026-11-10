package com.vemoring.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

/** Phone is the only writer of canonical versions. A stale vehicle request never overwrites it. */
data class CareAuthority(val vehicleId: String, val version: Long, val profile: String, val records: String,
    val acceptedRequests: Set<String> = emptySet()) {
    fun json() = JSONObject().put("vehicleId", vehicleId).put("version", version)
        .put("profile", JSONObject(profile)).put("records", JSONArray(records))
        .put("acceptedRequests", JSONArray(acceptedRequests.toList()))
    fun request(id: String, base: Long, profile: String, records: String): CareAuthority {
        if (id in acceptedRequests || base != version) return this
        require(id.isNotBlank() && id.length <= 128)
        return copy(version = version + 1, profile = profile, records = protectCancelledVisits(records),
            acceptedRequests = (acceptedRequests.toList().takeLast(127) + id).toSet())
    }
    fun protectCancelledVisits(proposed: String): String {
        val old = JSONArray(records); val next = JSONArray(proposed)
        repeat(old.length()) { n -> val tombstone = old.getJSONObject(n)
            val visit = tombstone.optString("visitId")
            if (tombstone.optBoolean("cancelled") && visit.isNotBlank() && visit != "null") {
                val matching = (0 until next.length()).map { next.getJSONObject(it) }.filter { it.optString("visitId") == visit }
                if (matching.isEmpty()) next.put(tombstone) else matching.forEach { it.put("cancelled", true) }
            }
        }
        return next.toString()
    }
    /** Explicit negative feedback wins even when the accompanying settings request has an old base. */
    fun rejectVisits(visits: Set<String>): CareAuthority {
        val next = JSONArray(records)
        var changed = false
        repeat(next.length()) { n -> val record = next.getJSONObject(n)
            if (record.optString("visitId") in visits && !record.optBoolean("cancelled")) {
                record.put("cancelled", true); changed = true
            }
        }
        return if(changed) copy(version = version + 1, records = next.toString()) else this
    }
    companion object {
        fun from(j: JSONObject) = CareAuthority(j.getString("vehicleId"), j.getLong("version"),
            j.getJSONObject("profile").toString(), j.getJSONArray("records").toString(),
            j.optJSONArray("acceptedRequests")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet())
    }
}

private val CARE_JOURNAL_LOCK = Any()

/** Caller uses Dispatchers.IO. One vehicle per preference key; no location copied into this journal. */
class CareJournal(private val context: Context, val vehicleId: String) {
    private val prefs = context.getSharedPreferences("care_journal_v1", Context.MODE_PRIVATE)
    /** Preference key of this vehicle's journal; listeners use it to ignore other vehicles' writes. */
    val key = java.security.MessageDigest.getInstance("SHA-256").digest(vehicleId.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun load() = prefs.getString(key, null)?.let(::JSONObject) ?: JSONObject()
    private fun save(j: JSONObject) {
        val raw = j.toString()
        if (prefs.getString(key, null) != raw) check(prefs.edit().putString(key, raw).commit())
    }
    fun authority(): CareAuthority? = load().optJSONObject("authority")?.let(CareAuthority::from)
    fun receivedRevision(): Long = load().optLong("receivedRevision", 0)
    fun receivedRevision(value: Long) { synchronized(CARE_JOURNAL_LOCK) { val j = load(); j.put("receivedRevision", value); save(j) } }
    fun pendingRequest(): JSONObject? = load().optJSONObject("pendingRequest")
    fun rejectedVisits(): Set<String> {
        val rejected = load().optJSONObject("rejectedVisits") ?: return emptySet()
        return rejected.keys().asSequence().toSet()
    }
    fun rememberRejectedVisits(visits: Set<String>) {
        synchronized(CARE_JOURNAL_LOCK) {
            require(visits.all { it.isNotBlank() && it.length <= 256 })
            val j = load(); val rejected = j.optJSONObject("rejectedVisits") ?: JSONObject()
            visits.forEach { rejected.put(it, true) }; j.put("rejectedVisits", rejected); save(j)
        }
    }
    fun syncStatus(): String = syncStatus(load())
    private fun syncStatus(j: JSONObject): String = if (j.optJSONObject("pendingRequest") != null) "휴대폰 동기화 대기"
        else if(j.optJSONObject("authority") == null) "차량 임시 추정"
        else if(j.optString("requestState").startsWith("Conflict")) "설정 충돌 · 휴대폰 기준 유지" else "휴대폰 기준 동기화됨"
    /** Intervals, sync label and pending flag from one read of the journal. */
    data class ConfigState(val intervals: List<TravelInterval>, val syncStatus: String, val pending: Boolean)
    fun configState(): ConfigState {
        val j = load()
        return ConfigState(intervals(j), syncStatus(j), j.optJSONObject("pendingRequest") != null)
    }
    fun queueRequest(profile: JSONObject, records: JSONArray) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load()
            val rejected = JSONArray()
            repeat(records.length()) { n -> val record = records.getJSONObject(n)
                val visit = record.optString("visitId")
                if(record.optBoolean("cancelled") && visit.isNotBlank() && visit != "null") rejected.put(visit)
            }
            j.put("pendingRequest", JSONObject().put("id", java.util.UUID.randomUUID().toString())
                .put("base", authority()?.version ?: 0).put("profile", profile).put("records", records).put("rejectedVisits", rejected))
            save(j)
        }
    }
    fun resolveRequest(authority: CareAuthority) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load(); val request = j.optJSONObject("pendingRequest") ?: return
            val accepted = request.getString("id") in authority.acceptedRequests
            if (accepted || authority.version != request.getLong("base")) {
                j.put("lastRequest", request).put("requestState", if(accepted) "Synced" else "Conflict: phone settings retained")
                j.remove("pendingRequest"); save(j)
            }
        }
    }
    fun saveAuthority(value: CareAuthority) {
        synchronized(CARE_JOURNAL_LOCK) {
            require(value.vehicleId == vehicleId)
            val j = load(); j.put("authority", value.json()); save(j)
        }
    }
    fun acceptDetectedWash(wash: DetectedWash): CareAuthority? {
        synchronized(CARE_JOURNAL_LOCK) {
            val state = authority() ?: return null
            if(wash.visitId in rejectedVisits()) return null
            val records = JSONArray(state.records)
            if ((0 until records.length()).any { records.getJSONObject(it).optString("visitId") == wash.visitId }) return null
            records.put(JSONObject().put("id", "wash:${wash.visitId}").put("kind", "wash").put("title", "자동 추정 세차")
                .put("date", wash.completedAt.atZone(CARE_ZONE).toLocalDate()).put("detail", "세차장 방문과 차량 이동 기반 자동 추정")
                .put("visitId", wash.visitId).put("completedAt", wash.completedAt).put("appliedAt", Instant.now()).put("source", "Real")
                .put("washEvidence",wash.evidence.name).put("cancelled", false))
            return state.copy(version = state.version + 1, records = records.toString()).also(::saveAuthority)
        }
    }
    fun pendingWashConfirmation(now: Instant = Instant.now()): WashConfirmation? {
        val j = load()
        val state = j.optJSONObject("authority")?.let(CareAuthority::from) ?: return null
        val records = JSONArray(state.records)
        val dismissed = j.optJSONObject("dismissedWashConfirmations") ?: JSONObject()
        return (0 until records.length()).mapNotNull { n -> val r = records.getJSONObject(n)
            val visit = r.optString("visitId").takeUnless { it.isBlank() || it == "null" }
            val completed = r.optString("completedAt").takeUnless { it.isBlank() || it == "null" }?.let(Instant::parse)
            val applied = r.optString("appliedAt").takeUnless { it.isBlank() || it == "null" }?.let(Instant::parse) ?: completed
            if (visit == null || completed == null || applied == null || r.optBoolean("cancelled") ||
                r.optString("washEvidence") in listOf("ClearFacilityPattern","UserRecord","VerifiedCompletion") ||
                dismissed.optBoolean(visit) || now < applied || now > applied.plusSeconds(7*86400L)) null
            else WashConfirmation(vehicleId, visit, completed, applied)
        }.maxByOrNull { it.appliedAt }
    }
    fun dismissWashConfirmation(visitId: String) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load(); val dismissed = j.optJSONObject("dismissedWashConfirmations") ?: JSONObject()
            dismissed.put(visitId, true); j.put("dismissedWashConfirmations", dismissed); save(j)
        }
    }
    fun cancelDetectedWash(visitId: String, now: Instant): CareAuthority? {
        synchronized(CARE_JOURNAL_LOCK) {
            val state = authority() ?: return null
            val records = JSONArray(state.records)
            val r = (0 until records.length()).map { records.getJSONObject(it) }
                .firstOrNull { it.optString("visitId") == visitId && !it.optBoolean("cancelled") } ?: return null
            val at = (r.optString("appliedAt").takeUnless { it.isBlank() || it == "null" }
                ?: r.optString("completedAt").takeUnless { it.isBlank() || it == "null" })?.let(Instant::parse) ?: return null
            if (now < at || now > at.plusSeconds(7*86400L)) return null
            r.put("cancelled", true)
            return state.copy(version = state.version + 1, records = records.toString()).also(::saveAuthority)
        }
    }
    fun intervals(): List<TravelInterval> = intervals(load())
    private fun intervals(j: JSONObject): List<TravelInterval> {
        val a = j.optJSONArray("intervals") ?: return emptyList()
        return (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
            TravelInterval(j.getString("id"), Instant.parse(j.getString("start")), Instant.parse(j.getString("end")),
                j.getDouble("km"), j.optBoolean("stationary"), located = j.optBoolean("located"),
                weather = j.optJSONObject("weather")?.let(::weatherFromJson), source = j.optString("source", "Real"))
        } }
    }
    fun mergeIntervals(incoming: List<TravelInterval>) {
        synchronized(CARE_JOURNAL_LOCK) { val j = load(); mergeInto(j, incoming); save(j) }
    }
    /** Merges into an already-loaded journal so callers can read and write it once. */
    private fun mergeInto(j: JSONObject, incoming: List<TravelInterval>) {
        val all = intervals(j).associateBy { it.id }.toMutableMap()
        incoming.forEach { next ->
            if (all.values.none { it.id != next.id && it.start < next.end && it.end > next.start }) all[next.id] = next
        }
        val a = JSONArray()
        WashPolicy.coalesceParking(all.values.toList()).forEach { t -> a.put(JSONObject().put("id", t.id).put("start", t.start)
            .put("end", t.end).put("km", t.km).put("stationary", t.stationary).put("source", t.source)
            .put("located", t.located).put("policy", t.policy).put("weather", t.weather?.let { w -> JSONObject()
                .put("hourStart", w.hourStart).put("rainMm", w.rainMm).put("showersMm", w.showersMm)
                .put("snowCm", w.snowCm).put("pm10", w.pm10).put("pm25", w.pm25)
                .put("obtainedAt", w.obtainedAt).put("version", w.version) })) }
        j.put("intervals", a)
    }
    /** Bounded raw mileage anchor. Unknown units, restarts and disconnects cannot become parked time. */
    fun observeDistance(km: Double?, at: Long?, session: String, now: Long) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load()
            if (km == null || at == null || !km.isFinite() || km < 0 || now - at !in 0..15000) {
                if (j.has("anchor")) { j.remove("anchor"); save(j) }; return
            }
            val old = j.optJSONObject("anchor")
            if (old != null && old.getString("session") == session && at <= old.getLong("at")) return
            if (old != null && old.getString("session") == session && at > old.getLong("at")) {
                val previous = old.getDouble("km"); val elapsed = at - old.getLong("at")
                val delta = km - previous
                // Broad sanity bound, not a vehicle driving/speed or wash motion threshold.
                if (elapsed <= 15*60*1000 && delta >= 0 && delta <= elapsed / 3600000.0 * 300.0) {
                    mergeInto(j, listOf(TravelInterval("$session:${old.getLong("at")}:$at",
                        Instant.ofEpochMilli(old.getLong("at")), Instant.ofEpochMilli(at), delta, delta == 0.0)))
                }
            }
            // One read and one write per mileage sample: interval merge and anchor share the loaded journal.
            j.put("anchor", JSONObject().put("km", km).put("at", at).put("session", session)); save(j)
        }
    }
    fun intervalJson(): JSONArray = load().optJSONArray("intervals") ?: JSONArray()
    fun outgoingIntervals(): JSONArray {
        val j = load(); val all = j.optJSONArray("intervals") ?: JSONArray()
        val delivered = j.optJSONObject("deliveredIntervals") ?: JSONObject()
        val pending = JSONArray()
        for(i in 0 until all.length()) {
            val part = all.getJSONObject(i)
            if (delivered.optString(part.getString("id")) != intervalDigest(part)) pending.put(part)
            if (pending.length() == 128) break
        }
        return pending
    }
    fun acknowledgeIntervals(ack: JSONArray) {
        synchronized(CARE_JOURNAL_LOCK) {
            require(ack.length() <= 128)
            val j = load(); val delivered = j.optJSONObject("deliveredIntervals") ?: JSONObject()
            repeat(ack.length()) { n -> val part = ack.getJSONObject(n)
                delivered.put(part.getString("id"), part.getString("digest"))
            }
            j.put("deliveredIntervals", delivered); save(j)
        }
    }
    fun importIntervals(a: JSONArray) {
        require(a.length() <= 20000)
        mergeIntervals((0 until a.length()).map { i -> a.getJSONObject(i).let { t ->
            TravelInterval(t.getString("id"), Instant.parse(t.getString("start")), Instant.parse(t.getString("end")),
                t.getDouble("km"), t.optBoolean("stationary"), located = t.optBoolean("located"),
                weather = t.optJSONObject("weather")?.let(::weatherFromJson), source = t.optString("source", "Real"))
        } })
    }
    fun baselines(records: JSONArray): List<WashBaseline> = (0 until records.length()).mapNotNull { i ->
        val r = records.getJSONObject(i)
        if (r.getString("kind") != "wash") null else WashBaseline(r.getString("id"), LocalDate.parse(r.getString("date")),
            r.optString("completedAt").takeUnless { it.isBlank() || it == "null" }?.let(Instant::parse),
            r.optString("visitId").takeUnless { it.isBlank() || it == "null" }, r.optBoolean("cancelled"), r.optString("source", "Manual"))
    }
    fun score(records: JSONArray, now: Instant = Instant.now()) = WashPolicy.score(baselines(records), intervals(), now)
    fun hasNotified(key: String): Boolean = load().optJSONObject("notifications")?.has(key) == true
    fun markNotified(key: String) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load(); val sent = j.optJSONObject("notifications") ?: JSONObject()
            sent.put(key, System.currentTimeMillis()); j.put("notifications", sent); save(j)
        }
    }
    fun lastWarning(type: String): String? = load().optJSONObject("warnings")?.optString(type)?.takeIf { it.isNotBlank() }
    fun saveWarning(type: String, state: String) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load(); val states = j.optJSONObject("warnings") ?: JSONObject()
            states.put(type, state); j.put("warnings", states); save(j)
        }
    }
    fun deleteVehicle() {
        synchronized(CARE_JOURNAL_LOCK) {
            check(prefs.edit().remove(key).commit())
            NotificationHistory(context).use {it.deleteVehicle(vehicleId)}
        }
    }
    private fun weatherFromJson(w: JSONObject) = HourWeather(Instant.parse(w.getString("hourStart")),
        w.getDouble("rainMm"), w.getDouble("showersMm"), w.getDouble("snowCm"),
        w.takeUnless { it.isNull("pm10") }?.getDouble("pm10"), w.takeUnless { it.isNull("pm25") }?.getDouble("pm25"),
        Instant.parse(w.getString("obtainedAt")), w.getString("version"))
}

internal fun intervalDigest(part: JSONObject): String = java.util.UUID.nameUUIDFromBytes(part.toString().toByteArray(Charsets.UTF_8)).toString()

/** Full agreed scope is prepared; ambiguous telltales stay disabled until vehicle-specific validation. */
object MaintenanceRules {
    val heldTypes = setOf("ENGINE", "ENGINE_OIL_PRESSURE", "ENGINE_TEMPERATURE", "LOW_VOLT_BATTERY",
        "ABS", "ELECTRONIC_PARKING_BRAKE", "MDPS", "TPMS", "ESC", "AIRBAG", "AFS", "LED_HEADLAMP",
        "PARTICLE_FILTER", "SCR", "WATER_SEPARATOR", "EV_SYSTEM", "HEV_SYSTEM", "FCEV_SYSTEM",
        "EREV_SYSTEM", "H2_SENSOR", "H2_LEAK")
    const val reason = "규칙 미검증/자동 정비 알림 보류: 차종별 공식 의미·전제 신호 확인 필요"
    const val safetyApi = "https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry"
}
