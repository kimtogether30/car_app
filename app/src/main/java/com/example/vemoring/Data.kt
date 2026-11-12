package com.example.vemoring

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class CareKind(val label: String, val key: String) {
    WASH("세차", "lastWash"), FUEL("주유", "lastFuel"), MAINTENANCE("정비", "lastOil");
    val wire: String get() = name.lowercase()
}

data class CareRecord(val id: String = UUID.randomUUID().toString(), val date: LocalDate,
    val kind: CareKind, val title: String, val detail: String = "", val completedAt: String? = null,
    val visitId: String? = null, val cancelled: Boolean = false, val source: String = "Manual", val appliedAt: String? = null,
    val washEvidence: String = "EstimatedVisit", val deleted: Boolean = false,
    val maintenanceType: com.vemoring.companion.MaintenanceType = com.vemoring.companion.MaintenanceType.GENERAL) {
    fun json() = JSONObject().put("id", id).put("date", date.toString()).put("kind", kind.wire)
        .put("title", title).put("detail", detail).put("completedAt", completedAt).put("visitId", visitId)
        .put("maintenanceType", if(kind == CareKind.MAINTENANCE) maintenanceType.name else null).put("cancelled", cancelled).put("source", source).put("appliedAt", appliedAt).put("washEvidence",washEvidence).put("deleted",deleted)
}

data class CarProfile(val brand: String = "", val model: String = "", val nickname: String = "",
    val plate: String = "", val fuel: String = "휘발유", val oilEstimate: String = "",
    val fuelAlert: Int = 20, val washAlert: Int = 30, val notifications: Boolean = true,
    val vehicleId: String = UUID.randomUUID().toString()) {
    val electric get() = fuel == "전기차"
    val hybrid get() = fuel == "하이브리드"
    val fullModel get() = if (model.startsWith(brand)) model else "$brand $model"
}

data class AppData(val profile: CarProfile = CarProfile(), val records: List<CareRecord> = emptyList(),
    val complete: Boolean = false)

data class GarageFleet(val vehicles: List<AppData>, val selectedId: String, val receivingId: String) {
    fun selected(): AppData = vehicles.firstOrNull {it.profile.vehicleId==selectedId} ?: vehicles.first()
    fun receiving(): AppData? = vehicles.firstOrNull {it.profile.vehicleId==receivingId && it.complete}
}

/** Reads Flutter's on-device preferences once; subsequent writes use an atomic native snapshot. */
class GarageStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("vemoring_native", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private fun legacyString(key: String) = legacy.getString("flutter.$key", null) ?: legacy.getString(key, null)
    fun load(): AppData = fleet().selected()
    /** Viewing a saved car never silently rebinds live SDK readings to it. */
    fun fleet(): GarageFleet = synchronized(LOCK) {
        prefs.getString("garage_fleet_v2",null)?.let { raw ->
            val j=JSONObject(raw);val a=j.getJSONArray("vehicles")
            val cars=(0 until a.length()).map {decode(a.getJSONObject(it))}
            require(cars.isNotEmpty() && cars.map {it.profile.vehicleId}.toSet().size==cars.size)
            return@synchronized GarageFleet(cars,j.getString("selectedId"),j.getString("receivingId"))
        }
        val old=loadLegacy()
        GarageFleet(listOf(old),old.profile.vehicleId,if(old.complete) old.profile.vehicleId else "")
    }
    fun select(vehicleId: String): AppData = synchronized(LOCK) {
        val old=fleet();require(old.vehicles.any {it.profile.vehicleId==vehicleId && it.complete})
        persist(old.copy(selectedId=vehicleId));old.vehicles.first {it.profile.vehicleId==vehicleId}
    }
    fun bindReceiver(vehicleId: String) = synchronized(LOCK) {
        val old=fleet();require(old.vehicles.any {it.profile.vehicleId==vehicleId && it.complete})
        persist(old.copy(receivingId=vehicleId))
    }
    fun receiving(): AppData? = fleet().receiving()
    private fun loadLegacy(): AppData {
        prefs.getString("garage_v1", null)?.let { return decode(JSONObject(it)) }
        val personal = legacyString("personal_setup_v1")?.let(::JSONObject) ?: return AppData()
        val recordsJson = legacyString("care_history_v1")?.let(::JSONArray)
        val records = if (recordsJson != null) readRecords(recordsJson) else CareKind.entries.mapNotNull { kind ->
            parseDay(personal.optString(kind.key))?.takeUnless { it > LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) }?.let {
                CareRecord("setup-${if(kind == CareKind.MAINTENANCE) "oil" else kind.wire}", it, kind, kind.label)
            }
        }
        val brand=legacyString("vehicle_brand") ?: "";val model=legacyString("vehicle_model") ?: ""
        val fullModel=if(model.startsWith(brand)) model else "$brand $model"
        val profile = CarProfile(brand,model,
            personal.optString("nickname"), personal.optString("plate"), personal.optString("fuel", "휘발유"),
            personal.optString("oilEstimate"), personal.optInt("fuelAlert", 20), personal.optInt("washAlert", 30),
            vehicleId=com.vemoring.companion.legacyVehicleId(fullModel,personal.optString("plate")))
        return AppData(profile, records.sortedByDescending { it.date }, profile.brand.isNotBlank() && profile.model.isNotBlank())
    }
    fun save(data: AppData,queueForPhone: Boolean = false) = synchronized(LOCK) {
        require(data.records.none { it.date > LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) }) { "미래 날짜에는 관리 기록을 등록할 수 없어요." }
        require(!data.complete || (data.profile.nickname.isNotBlank() && data.profile.model.isNotBlank())) { "차량 정보를 입력해 주세요." }
        val old=fleet()
        val previous=old.vehicles.firstOrNull {it.profile.vehicleId==data.profile.vehicleId}
        val existing=previous!=null
        val tombstones=previous?.records.orEmpty().filter {it.deleted}.associateBy {it.id}
        val incomingIds=data.records.mapTo(HashSet()) {it.id}
        val records=data.records.map {tombstones[it.id] ?: it}+tombstones.values.filter {it.id !in incomingIds}
        val saved=data.copy(records=records)
        val cars=(old.vehicles.filter {it.complete && it.profile.vehicleId!=data.profile.vehicleId}+saved)
        val selected=if(existing && cars.any {it.profile.vehicleId==old.selectedId}) old.selectedId else data.profile.vehicleId
        val receiver=old.receivingId.takeIf {id -> cars.any {it.complete && it.profile.vehicleId==id}}
            ?: data.profile.vehicleId.takeIf {data.complete}.orEmpty()
        persist(GarageFleet(cars,selected,receiver))
        if(queueForPhone) {
            val p=saved.profile
            val profile=JSONObject().put("nickname",p.nickname).put("model",p.fullModel).put("plate",p.plate).put("fuel",p.fuel)
                .put("fuelAlert",p.fuelAlert).put("washAlert",p.washAlert).put("notifications",p.notifications).put("vehicleId",p.vehicleId)
            com.vemoring.companion.CareJournal(context,p.vehicleId).queueRequest(profile,JSONArray().also {a -> saved.records.forEach {a.put(it.json())}})
        }
    }
    fun syncFromPhone(authority: com.vemoring.companion.CareAuthority) = synchronized(LOCK) {
        val current=fleet().vehicles.firstOrNull {it.profile.vehicleId==authority.vehicleId} ?: return@synchronized
        if(com.vemoring.companion.CareJournal(context,authority.vehicleId).pendingRequest()!=null) return@synchronized
        val next=applyPhoneAuthority(current,authority)
        if(next!=current) save(next)
    }
    private fun encode(data: AppData): JSONObject {
        val p=data.profile
        return JSONObject().put("complete", data.complete).put("profile", JSONObject()
            .put("brand", p.brand).put("model", p.model).put("nickname", p.nickname).put("plate", p.plate)
            .put("fuel", p.fuel).put("oilEstimate", p.oilEstimate).put("fuelAlert", p.fuelAlert)
            .put("washAlert", p.washAlert).put("notifications", p.notifications).put("vehicleId",p.vehicleId))
            .put("records", JSONArray().also { a -> data.records.forEach { a.put(it.json()) } })
    }
    private fun persist(fleet: GarageFleet) {
        val json=JSONObject().put("selectedId",fleet.selectedId).put("receivingId",fleet.receivingId)
            .put("vehicles",JSONArray().also {a -> fleet.vehicles.forEach {a.put(encode(it))}})
        check(prefs.edit().putString("garage_fleet_v2",json.toString())
            .putString("garage_v1",encode(fleet.selected()).toString()).commit()) {"저장하지 못했어요. 다시 시도해 주세요."}
    }
    private fun decode(json: JSONObject): AppData {
        val p = json.getJSONObject("profile")
        return AppData(CarProfile(p.getString("brand"), p.getString("model"), p.getString("nickname"),
            p.optString("plate"), p.getString("fuel"), p.optString("oilEstimate"), p.optInt("fuelAlert",20),
            p.optInt("washAlert",30), p.optBoolean("notifications",true),p.optString("vehicleId").takeIf {it.isNotBlank()}
                ?: com.vemoring.companion.legacyVehicleId(if(p.getString("model").startsWith(p.getString("brand"))) p.getString("model") else "${p.getString("brand")} ${p.getString("model")}",p.optString("plate"))),
            readRecords(json.getJSONArray("records")), json.getBoolean("complete"))
    }
    private fun readRecords(array: JSONArray) = (0 until array.length()).map { i ->
        val r = array.getJSONObject(i)
        careRecordFromJson(r)
    }.sortedByDescending { it.date }
    companion object {private val LOCK=Any()}
}

fun applyPhoneAuthority(data: AppData,authority: com.vemoring.companion.CareAuthority): AppData {
    if(data.profile.vehicleId!=authority.vehicleId) return data
    val p=JSONObject(authority.profile);val records=JSONArray(authority.records)
    val full=p.optString("model",data.profile.fullModel)
    val brand=VehicleCatalog.brands.firstOrNull {full.startsWith(it)} ?: data.profile.brand
    val deleted=data.records.filter {it.deleted}.associateBy {it.id}
    val incoming=(0 until records.length()).map {careRecordFromJson(records.getJSONObject(it))}
    val incomingIds=incoming.mapTo(HashSet()) {it.id}
    return data.copy(profile=data.profile.copy(brand=brand,model=full.removePrefix(brand).trim(),
        nickname=p.optString("nickname",data.profile.nickname),plate=p.optString("plate",data.profile.plate),fuel=p.optString("fuel",data.profile.fuel),
        fuelAlert=p.optInt("fuelAlert",data.profile.fuelAlert),washAlert=p.getInt("washAlert"),notifications=p.getBoolean("notifications")),
        records=(incoming.map {deleted[it.id] ?: it}+deleted.values.filter {it.id !in incomingIds}).sortedByDescending {it.date})
}

fun careRecordFromJson(r: JSONObject) = CareRecord(r.getString("id"), requireNotNull(parseDay(r.getString("date"))),
    if(r.getString("kind")=="oil") CareKind.MAINTENANCE else CareKind.entries.first { it.wire == r.getString("kind") }, r.getString("title"), r.optString("detail"),
    r.optString("completedAt").takeUnless { it.isBlank() || it == "null" },
    r.optString("visitId").takeUnless { it.isBlank() || it == "null" }, r.optBoolean("cancelled"), r.optString("source", "Manual"),
    r.optString("appliedAt").takeUnless { it.isBlank() || it == "null" },r.optString("washEvidence","EstimatedVisit"),r.optBoolean("deleted"),com.vemoring.companion.MaintenanceType.from(r.optString("maintenanceType")))

fun deleteRecord(records: List<CareRecord>,id: String): List<CareRecord> = records.map {
    if(it.id==id) it.copy(cancelled=true,deleted=true) else it
}

fun parseDay(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
fun List<CareRecord>.latest(kind: CareKind) = filter { it.kind == kind && !it.cancelled }.maxByOrNull { it.date }?.date
fun updateLatest(records: List<CareRecord>, dates: Map<CareKind, LocalDate?>): List<CareRecord> {
    val updated = records.toMutableList()
    dates.forEach { (kind, date) ->
        if (date != null && date != records.latest(kind)) {
            require(date <= LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
            val old = updated.filter { it.kind == kind && !it.cancelled }.maxByOrNull { it.date }
            if (old != null) updated.remove(old)
            updated.add(old?.copy(date = date, completedAt = null, source = "Manual", washEvidence = "UserRecord") ?: CareRecord(date=date,kind=kind,title=kind.label))
        }
    }
    return updated.sortedByDescending { it.date }
}
