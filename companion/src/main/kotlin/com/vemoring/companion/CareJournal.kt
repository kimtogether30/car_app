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
        val old = JSONArray(records); val next = JSONArray(normalizeMaintenanceRecords(proposed))
        val tombstones = (0 until old.length()).map { old.getJSONObject(it) }.filter { record ->
            record.optBoolean("deleted") || (record.optBoolean("cancelled") &&
                record.optString("visitId").let { it.isNotBlank() && it != "null" })
        }
        if (tombstones.isEmpty()) return next.toString()
        // Index once: a long offline history must not rescan every proposed record per tombstone.
        val byId = mutableMapOf<String, MutableList<JSONObject>>()
        val byVisit = mutableMapOf<String, MutableList<JSONObject>>()
        fun index(record: JSONObject) {
            byId.getOrPut(record.optString("id")) { mutableListOf() }.add(record)
            byVisit.getOrPut(record.optString("visitId")) { mutableListOf() }.add(record)
        }
        repeat(next.length()) { index(next.getJSONObject(it)) }
        tombstones.forEach { tombstone ->
            val visit = tombstone.optString("visitId")
            if(tombstone.optBoolean("deleted")) {
                val matching=byId[tombstone.getString("id")]
                if(matching.isNullOrEmpty()) {next.put(tombstone);index(tombstone)}
                else matching.forEach {it.put("cancelled",true).put("deleted",true)}
            } else if (tombstone.optBoolean("cancelled") && visit.isNotBlank() && visit != "null") {
                val matching = byVisit[visit]
                if (matching.isNullOrEmpty()) {next.put(tombstone);index(tombstone)}
                else matching.forEach { it.put("cancelled", true) }
            }
        }
        return next.toString()
    }
    /** Explicit negative feedback wins even when the accompanying settings request has an old base. */
    fun rejectVisits(visits: Set<String>): CareAuthority {
        if (visits.isEmpty()) return this
        val next = JSONArray(records)
        var changed = false
        repeat(next.length()) { n -> val record = next.getJSONObject(n)
            if (record.optString("visitId") in visits && !record.optBoolean("cancelled")) {
                record.put("cancelled", true); changed = true
            }
        }
        return if(changed) copy(version = version + 1, records = next.toString()) else this
    }
    fun deleteRecords(ids: Set<String>): CareAuthority {
        if (ids.isEmpty()) return this
        val next=JSONArray(records);var changed=false
        repeat(next.length()) {n -> val r=next.getJSONObject(n)
            if(r.getString("id") in ids && (!r.optBoolean("deleted") || !r.optBoolean("cancelled"))) {
                r.put("cancelled",true).put("deleted",true);changed=true
            }
        }
        return if(changed) copy(version=version+1,records=next.toString()) else this
    }
    companion object {
        fun from(j: JSONObject) = CareAuthority(j.getString("vehicleId"), j.getLong("version"),
            j.getJSONObject("profile").toString(), normalizeMaintenanceRecords(j.getJSONArray("records").toString()),
            j.optJSONArray("acceptedRequests")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet())
    }
}

private val CARE_JOURNAL_LOCK = Any()

/** Caller uses Dispatchers.IO. Per-vehicle journal; only travel endpoint location, never a route trace. */
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
        if (visits.isEmpty()) return
        synchronized(CARE_JOURNAL_LOCK) {
            require(visits.all { it.isNotBlank() && it.length <= 256 })
            val j = load(); val rejected = j.optJSONObject("rejectedVisits") ?: JSONObject()
            visits.forEach { rejected.put(it, true) }; j.put("rejectedVisits", rejected); save(j)
        }
    }
    fun deletedRecords(): Set<String> = load().optJSONObject("deletedRecords")?.keys()?.asSequence()?.toSet() ?: emptySet()
    fun rememberDeletedRecords(ids: Set<String>) {
        if (ids.isEmpty()) return
        synchronized(CARE_JOURNAL_LOCK) {
            require(ids.all {it.isNotBlank() && it.length<=256})
            val j=load();val deleted=j.optJSONObject("deletedRecords") ?: JSONObject()
            ids.forEach {deleted.put(it,true)};j.put("deletedRecords",deleted);save(j)
        }
    }
    fun syncStatus(): String = syncStatus(load())
    private fun syncStatus(j: JSONObject): String = if (j.optJSONObject("pendingRequest") != null) "휴대폰 동기화 대기"
        else if(j.optJSONObject("authority") == null) "차량 임시 추정"
        else if(j.optString("requestState").startsWith("Conflict")) "설정 충돌 · 휴대폰 기준 유지" else "휴대폰 기준 동기화됨"
    /** Intervals, sync label and pending flag from one read of the journal. */
    data class ConfigState(val intervals: List<TravelInterval>, val syncStatus: String, val pending: Boolean,val authority: CareAuthority?)
    fun configState(): ConfigState {
        val j = load()
        return ConfigState(intervals(j), syncStatus(j), j.optJSONObject("pendingRequest") != null,j.optJSONObject("authority")?.let(CareAuthority::from))
    }
    fun queueRequest(profile: JSONObject, records: JSONArray) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load()
            val rejected = JSONArray()
            val deleted=JSONArray()
            repeat(records.length()) { n -> val record = records.getJSONObject(n)
                val visit = record.optString("visitId")
                if(record.optBoolean("cancelled") && visit.isNotBlank() && visit != "null") rejected.put(visit)
                if(record.optBoolean("deleted")) deleted.put(record.getString("id"))
            }
            val rejectedState=j.optJSONObject("rejectedVisits") ?: JSONObject()
            repeat(rejected.length()) {rejectedState.put(rejected.getString(it),true)}
            val deletedState=j.optJSONObject("deletedRecords") ?: JSONObject()
            repeat(deleted.length()) {deletedState.put(deleted.getString(it),true)}
            j.put("rejectedVisits",rejectedState).put("deletedRecords",deletedState)
            j.put("pendingRequest", JSONObject().put("id", java.util.UUID.randomUUID().toString())
                .put("base", j.optJSONObject("authority")?.getLong("version") ?: 0).put("profile", profile).put("records", records).put("rejectedVisits", rejected).put("deletedRecords",deleted))
            save(j)
        }
    }
    fun negativeFeedback(): Pair<JSONArray,JSONArray> = negativeFeedback(load())
    private fun negativeFeedback(j: JSONObject): Pair<JSONArray,JSONArray> {
        fun ids(name:String)=JSONArray().also {a -> j.optJSONObject(name)?.keys()?.forEach {a.put(it)}}
        return ids("rejectedVisits") to ids("deletedRecords")
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
                .put("date", wash.completedAt.atZone(CARE_ZONE).toLocalDate()).put("detail", "세차 구역 체류와 차량 상태·출차 기반 자동 추정")
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
                weather = j.optJSONObject("weather")?.let(::weatherFromJson), source = j.optString("source", "Real"),
                policy=j.optInt("policy",CARE_POLICY_VERSION),point=j.optJSONObject("point")?.let(TravelPoint::from))
        } }
    }
    fun mergeIntervals(incoming: List<TravelInterval>) {
        synchronized(CARE_JOURNAL_LOCK) { val j = load(); mergeInto(j, incoming); save(j) }
    }
    /** Merges into an already-loaded journal so callers can read and write it once. */
    private fun mergeInto(j: JSONObject, incoming: List<TravelInterval>) {
        val all = intervals(j).associateBy { it.id }.toMutableMap()
        incoming.forEach { next ->
            val old=all[next.id]
            if(old!=null && (next.start!=old.start || next.end<old.end || (old.km>0 && next.km!=old.km))) return@forEach
            if(old?.weather!=null && old.weather==next.weather && old.point==next.point) {
                val delivered=j.optJSONObject("weatherDelivered") ?: JSONObject()
                delivered.put(next.id,true);j.put("weatherDelivered",delivered)
            }
            if (all.values.none { it.id != next.id && it.start < next.end && it.end > next.start })
                all[next.id] = if(old!=null && old.end==next.end && old.km==next.km && old.point==next.point && next.weather==null)
                    next.copy(weather=old.weather) else next
        }
        val a = JSONArray()
        WashPolicy.coalesceParking(all.values.toList()).forEach { t -> a.put(JSONObject().put("id", t.id).put("start", t.start)
            .put("end", t.end).put("km", t.km).put("stationary", t.stationary).put("source", t.source)
            .put("located", t.located).put("policy", t.policy).put("point",t.point?.json()).put("weather", t.weather?.let { w -> JSONObject()
                .put("hourStart", w.hourStart).put("rainMm", w.rainMm).put("showersMm", w.showersMm)
                .put("snowCm", w.snowCm).put("pm10", w.pm10).put("pm25", w.pm25)
                .put("obtainedAt", w.obtainedAt).put("version", w.version).put("precipitationKnown",w.precipitationKnown) })) }
        j.put("intervals", a)
    }
    /** Bounded raw mileage anchor. Unknown units, restarts and disconnects cannot become parked time. */
    fun observeDistance(km: Double?, at: Long?, session: String, now: Long, point: TravelPoint? = null) {
        synchronized(CARE_JOURNAL_LOCK) {
            val j = load()
            val old=j.optJSONObject("anchor")?.let(DistanceAnchor::from)
            val step=DistanceAccumulator.observe(old,km,at?.let(Instant::ofEpochMilli),session,Instant.ofEpochMilli(now),point)
            step.interval?.let {mergeInto(j,listOf(it))}
            if(step.anchor==null) j.remove("anchor") else j.put("anchor",step.anchor.json())
            save(j)
        }
    }
    fun intervalJson(): JSONArray = load().optJSONArray("intervals") ?: JSONArray()
    /** Build one coherent outgoing payload without reparsing the complete journal for each field. */
    data class OutgoingState(val intervals: JSONArray, val request: JSONObject?, val rejectedVisits: JSONArray, val deletedRecords: JSONArray)
    fun outgoingState(): OutgoingState {
        val j = load()
        val negative = negativeFeedback(j)
        return OutgoingState(outgoingIntervals(j), j.optJSONObject("pendingRequest"), negative.first, negative.second)
    }
    fun outgoingIntervals(): JSONArray = outgoingIntervals(load())
    private fun outgoingIntervals(j: JSONObject): JSONArray {
        val all = j.optJSONArray("intervals") ?: JSONArray()
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
                weather = t.optJSONObject("weather")?.let(::weatherFromJson), source = t.optString("source", "Real"),
                policy=t.optInt("policy",CARE_POLICY_VERSION),point=t.optJSONObject("point")?.let(TravelPoint::from))
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
    /** Vehicle-side low-fuel episode and confirmed refuel events (see FuelRefill.kt). */
    fun fuelEpisode(): FuelEpisode = load().optJSONObject("fuelEpisode")?.let { runCatching { FuelEpisode.from(it) }.getOrNull() } ?: FuelEpisode()
    /** Atomic read-modify-write; writes only when the state actually changes. */
    fun updateFuelEpisode(change: (FuelEpisode) -> FuelEpisode): FuelEpisode = synchronized(CARE_JOURNAL_LOCK) {
        val j = load()
        val current = j.optJSONObject("fuelEpisode")?.let { runCatching { FuelEpisode.from(it) }.getOrNull() } ?: FuelEpisode()
        val next = change(current)
        if (next != current) { j.put("fuelEpisode", next.json()); save(j) }
        next
    }
    fun deleteVehicle() {
        synchronized(CARE_JOURNAL_LOCK) {
            check(prefs.edit().remove(key).commit())
            NotificationHistory(context).use {it.deleteVehicle(vehicleId)}
            FacilityWashStore(context,vehicleId).clear()
            EngineOilLedger(context,vehicleId).clear()
            if(WashAssistSignals.target.value?.vehicleId==vehicleId) WashAssistSignals.target.value=null
        }
    }
    companion object {
        /** Small vehicle directory from persisted authorities; caller uses IO. */
        fun vehicles(context: Context): List<CareAuthority> = context.getSharedPreferences("care_journal_v1",Context.MODE_PRIVATE)
            .all.values.filterIsInstance<String>().mapNotNull {raw ->
                runCatching {JSONObject(raw).optJSONObject("authority")?.let(CareAuthority::from)}.getOrNull()
            }.distinctBy {it.vehicleId}
    }
    private fun weatherFromJson(w: JSONObject) = HourWeather(Instant.parse(w.getString("hourStart")),
        w.getDouble("rainMm"), w.getDouble("showersMm"), w.getDouble("snowCm"),
        w.takeUnless { it.isNull("pm10") }?.getDouble("pm10"), w.takeUnless { it.isNull("pm25") }?.getDouble("pm25"),
        Instant.parse(w.getString("obtainedAt")), w.getString("version"),w.optBoolean("precipitationKnown",true))

    /** Enrichment can change only existing immutable travel evidence, never invent mileage. */
    fun attachWeather(id: String,weather: HourWeather): Boolean = attachWeather(listOf(id to weather))>0
    /** Same per-interval checks as a single attach, but one journal read and one write for the whole batch. */
    fun attachWeather(updates: List<Pair<String,HourWeather>>): Int = synchronized(CARE_JOURNAL_LOCK) {
        if(updates.isEmpty()) return@synchronized 0
        val j=load(); val current=intervals(j).associateBy {it.id}
        val accepted=updates.mapNotNull {(id,weather) ->
            val old=current[id] ?: return@mapNotNull null
            if(old.weather!=null || !DrivingWeatherPolicy.pending(old,weather.obtainedAt) ||
                weather.hourStart!=DrivingWeatherPolicy.hourStart(old.start) || weather.obtainedAt<DrivingWeatherPolicy.readyAt(old)) null
            else old.copy(weather=weather)
        }.distinctBy {it.id}
        if(accepted.isNotEmpty()) {mergeInto(j,accepted); save(j)}
        accepted.size
    }
    fun weatherReply(): JSONArray = JSONArray().also {a ->
        val j=load();val all=j.optJSONArray("intervals") ?: JSONArray();val delivered=j.optJSONObject("weatherDelivered")
        for(n in 0 until all.length()) {
            val t=all.getJSONObject(n)
            if(t.optJSONObject("weather")!=null && delivered?.optBoolean(t.getString("id"))!=true) a.put(t)
            if(a.length()>=128) break
        }
    }
    fun importWeatherReply(a: JSONArray) {
        require(a.length()<=128)
        // Parse the journal once for matching, then attach every verified entry in a single write.
        val current=intervals().associateBy {it.id}
        val updates=(0 until a.length()).mapNotNull {n -> val t=a.getJSONObject(n); val old=current[t.getString("id")]
            if(old!=null && old.start==Instant.parse(t.getString("start")) && old.end==Instant.parse(t.getString("end")) &&
                old.km==t.getDouble("km") && t.optJSONObject("point")?.let(TravelPoint::from)==old.point)
                t.optJSONObject("weather")?.let {old.id to weatherFromJson(it)} else null
        }
        attachWeather(updates)
    }
}

internal fun intervalDigest(part: JSONObject): String = java.util.UUID.nameUUIDFromBytes(part.toString().toByteArray(Charsets.UTF_8)).toString()
