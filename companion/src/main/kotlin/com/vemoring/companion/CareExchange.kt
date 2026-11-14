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
    val weatherRevision = MutableStateFlow(0L)
    suspend fun weatherChanged(context:Context,vehicleId:String) = withContext(Dispatchers.IO) {mutex.withLock {
        val store=CareJournal(context,vehicleId);val state=store.authority() ?: return@withLock
        val cached=MirrorBus.vehicles.value[vehicleId]?.snapshot
        val snapshot=cached?.let {JSONObject(it.content).put("observedAt",it.capturedAt)}
        CareNotifications.evaluate(context,store,state,store.score(JSONArray(state.records)),snapshot?.optJSONObject("care"),snapshot)
        weatherRevision.value++
    }}
    suspend fun registerPhone(context: Context,profile: JSONObject,records: JSONArray): CareAuthority = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id=java.util.UUID.randomUUID().toString()
            val next=CareAuthority(id,1,JSONObject(profile.toString()).put("vehicleId",id).toString(),records.toString())
            validate(next);CareJournal(context,id).saveAuthority(next);canonical.value=next
            CareNotifications.evaluate(context,CareJournal(context,id),next,CareJournal(context,id).score(records),null)
            next
        }
    }
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
                DrivingWeatherSync.enqueue(context,state.vehicleId)
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
    suspend fun detectedOil(context:Context,visit:EngineOilVisit):Boolean=withContext(Dispatchers.IO) {
        mutex.withLock {applyOilDetection(context,visit)}
    }
    private fun applyOilDetection(context:Context,visit:EngineOilVisit):Boolean {
        val now=System.currentTimeMillis()
        if(!EngineOilVisitPolicy.complete(visit,now)) return false
        val journal=CareJournal(context,visit.vehicleId);val state=journal.authority() ?: return false
        val profile=JSONObject(state.profile).put("vehicleId",visit.vehicleId)
        val assessment=EngineOilCatalog.assess(context,profile,state.records)
        if(assessment.reason=="엔진오일 교체 대상이 아니에요" || EngineOilCatalog.rule(context,profile.optString("model"),profile.optString("fuel"))==null) return false
        val records=JSONArray(state.records)
        val hasDate=(0 until records.length()).map {records.getJSONObject(it)}.any {
            isEngineOilRecord(it) && !it.optBoolean("cancelled") && !it.optBoolean("deleted")
        } || EngineOilLedger(context,visit.vehicleId).frame().optString("initialDate").isNotBlank()
        if(hasDate && assessment.due!=true || visit.id in journal.rejectedVisits() || "oil-record:${visit.id}" in journal.deletedRecords()) return false
        if((0 until records.length()).any {records.getJSONObject(it).optString("visitId")==visit.id}) return false
        val completed=java.time.Instant.ofEpochMilli(visit.exit!!.at)
        records.put(JSONObject().put("id","oil-record:${visit.id}").put("kind","maintenance").put("maintenanceType","ENGINE_OIL")
            .put("title","자동 추정 엔진오일 교체").put("detail","같은 정비소에서 전원 OFF→ON 30분 이상, 출차 및 다음 정차 확인")
            .put("date",completed.atZone(CARE_ZONE).toLocalDate()).put("completedAt",completed).put("appliedAt",java.time.Instant.ofEpochMilli(now))
            .put("visitId",visit.id).put("source","EstimatedOilVisit").put("odometerKm",visit.odometerKm).put("cancelled",false))
        val next=state.copy(version=state.version+1,records=records.toString())
        validate(next);journal.saveAuthority(next);canonical.value=next;confirmationRevision.value++
        BannerNotifications.oilDetected(context,journal,visit)
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
            j.optJSONObject("care")?.optJSONObject("oilLedger")?.let {EngineOilLedger(context,snapshot.vehicleId).importVehicle(it)}
            val versionBeforeFeedback=journal.authority()?.version
            // Negative feedback is terminal and commutative, independent of message/settings ordering.
            val rejection = j.optJSONObject("care")?.optJSONArray("rejectedVisits")
                ?: j.optJSONObject("care")?.optJSONObject("request")?.optJSONArray("rejectedVisits")
            if(rejection != null) {
                require(rejection.length() <= 5000)
                journal.rememberRejectedVisits((0 until rejection.length()).map { rejection.getString(it) }.toSet())
                repeat(rejection.length()) {
                    BannerNotifications.cancelVisit(context,snapshot.vehicleId,rejection.getString(it))
                    CareNotifications.cancelDetection(context,snapshot.vehicleId,rejection.getString(it))
                }
                journal.authority()?.let { current -> journal.saveAuthority(current.rejectVisits(journal.rejectedVisits())) }
            }
            val deletion=j.optJSONObject("care")?.optJSONArray("deletedRecords")
                ?: j.optJSONObject("care")?.optJSONObject("request")?.optJSONArray("deletedRecords")
            if(deletion!=null) {
                require(deletion.length()<=5000)
                val ids=(0 until deletion.length()).map {deletion.getString(it)}.toSet()
                journal.rememberDeletedRecords(ids)
                journal.authority()?.let {a ->
                    val records=JSONArray(a.records)
                    repeat(records.length()) {n -> val r=records.getJSONObject(n)
                        val visit=r.optString("visitId").takeUnless {it.isBlank() || it=="null"}
                        if(r.getString("id") in ids && visit!=null) {
                            BannerNotifications.cancelVisit(context,snapshot.vehicleId,visit)
                            CareNotifications.cancelDetection(context,snapshot.vehicleId,visit)
                        }
                    }
                    journal.saveAuthority(a.deleteRecords(journal.deletedRecords()))
                }
            }
            val previous = journal.authority()
            if (previous != null && snapshot.revision <= journal.receivedRevision()) {
                canonical.value = previous
                return@withLock JSONObject().put("schema", 2).put("vehicleId", snapshot.vehicleId)
                    .put("ack", snapshot.revision).put("authority", previous.json()).put("intervalAck", intervalAck(j)).put("weatherIntervals",journal.weatherReply())
            }
            var state = journal.authority()
            if (state == null) {
                // Registration IDs are authoritative. Blank/equal plates cannot identify the same car.
                // Old phone-only records remain under phone-local and are never overwritten by an import.
                val profile = j.getJSONObject("profile")
                val firstRequest=j.optJSONObject("care")?.optJSONObject("request")
                val accepted=if(firstRequest?.optLong("base",-1)==0L) setOf(firstRequest.getString("id").also {require(it.isNotBlank() && it.length<=128)}) else emptySet()
                state = CareAuthority(snapshot.vehicleId, 1, profile.toString(), j.getJSONArray("records").toString(),accepted)
            } else {
                val request = j.optJSONObject("care")?.optJSONObject("request")
                if (request != null && !j.optBoolean("demo")) {
                    val base=request.getLong("base")
                    state = state.request(request.getString("id"), if(base==versionBeforeFeedback) state.version else base,
                        (request.optJSONObject("profile") ?: j.getJSONObject("profile")).toString(),
                        (request.optJSONArray("records") ?: j.getJSONArray("records")).toString())
                }
            }
            state = state.rejectVisits(journal.rejectedVisits()).deleteRecords(journal.deletedRecords())
            validate(state)
            j.optJSONObject("care")?.optJSONArray("intervals")?.let { journal.importIntervals(it) }
            journal.saveAuthority(state)
            journal.receivedRevision(snapshot.revision)
            j.optJSONObject("care")?.optJSONArray("oilVisits")?.let {visits ->
                require(visits.length()<=64)
                val motion=j.optJSONObject("care")?.optJSONObject("motion")
                val now=System.currentTimeMillis()
                val stopped=motion?.optString("source")=="Real" && motion.optBoolean("stationary") &&
                    now-motion.optLong("speedAt") in 0..5000 && now-motion.optLong("gearAt") in 0..35000 && now-snapshot.capturedAt in 0..15000
                if(stopped) repeat(visits.length()) {n ->
                    val visit=EngineOilVisit.from(visits.getJSONObject(n));require(visit.vehicleId==snapshot.vehicleId)
                    applyOilDetection(context,visit)
                }
            }
            j.optJSONObject("care")?.optJSONArray("washVisits")?.let {frame ->
                val visits=FacilityWashStore(context,snapshot.vehicleId)
                visits.importVehicle(frame,System.currentTimeMillis()).forEach {wash -> applyDetection(context,snapshot.vehicleId,wash)}
                val candidate=visits.phoneCandidate(System.currentTimeMillis())
                WashAssistSignals.target.value=candidate?.let {WashAssistTarget(snapshot.vehicleId,it.id,
                    it.fixes.first().at+FacilityWashPolicy.LIFETIME_MS)}
            }
            val finalState=requireNotNull(journal.authority())
            canonical.value = finalState
            val score = journal.score(JSONArray(finalState.records))
            BannerNotifications.recordChanges(context,previous,finalState)
            if (!j.optBoolean("demo")) CareNotifications.evaluate(context, journal, finalState, score, j.optJSONObject("care"),j.put("observedAt",snapshot.capturedAt))
            DrivingWeatherSync.enqueue(context,snapshot.vehicleId)
            CareDailyCheck.schedule(context)
            JSONObject().put("schema", 2).put("vehicleId", snapshot.vehicleId).put("ack", snapshot.revision)
                .put("authority", finalState.json()).put("cleanliness", score.displayed ?: JSONObject.NULL).put("intervalAck", intervalAck(j))
                .put("weatherIntervals",journal.weatherReply())
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
            response.optJSONArray("weatherIntervals")?.let {store.importWeatherReply(it)}
            val records=JSONArray(authority.records)
            store.rememberDeletedRecords((0 until records.length()).map {records.getJSONObject(it)}.filter {it.optBoolean("deleted")}.map {it.getString("id")}.toSet())
            if ((store.authority()?.version ?: 0) <= authority.version) {
                val protected=authority.rejectVisits(store.rejectedVisits()).deleteRecords(store.deletedRecords()).copy(version=authority.version)
                store.saveAuthority(protected); store.resolveRequest(authority); canonical.value = protected
            }
            response.optJSONArray("intervalAck")?.let { store.acknowledgeIntervals(it) }
            val sent = MirrorBus.outgoing.value?.takeIf { it.vehicleId == authority.vehicleId }
            if (sent != null) {
                val content = JSONObject(sent.content)
                val care = content.optJSONObject("care")
                val next = store.outgoingIntervals()
                if (care != null) {
                    val negative=store.negativeFeedback()
                    care.put("intervals", next).put("request", store.pendingRequest()).put("rejectedVisits",negative.first).put("deletedRecords",negative.second)
                    if(content.toString()!=sent.content) MirrorBus.publish(context, content.toString(), authority.vehicleId)
                }
            }
        }
    }

    suspend fun editPhone(context: Context, vehicleId: String, profile: JSONObject, records: JSONArray): CareAuthority =
        withContext(Dispatchers.IO) { mutex.withLock {
            val store = CareJournal(context, vehicleId)
            val previous = requireNotNull(store.authority())
            val ids=(0 until records.length()).map {records.getJSONObject(it)}.filter {it.optBoolean("deleted")}.map {it.getString("id")}.toSet()
            store.rememberDeletedRecords(ids)
            val next = previous.copy(version = previous.version + 1, profile = profile.toString(), records = previous.protectCancelledVisits(EngineOilLedger(context,vehicleId).attachCurrentMileage(JSONArray(previous.records),records).toString()))
                .deleteRecords(store.deletedRecords())
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
