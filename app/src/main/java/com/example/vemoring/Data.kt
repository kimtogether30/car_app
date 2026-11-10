package com.example.vemoring

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class CareKind(val label: String, val key: String) {
    WASH("세차", "lastWash"), FUEL("주유", "lastFuel"), OIL("엔진오일 교체", "lastOil");
    val wire: String get() = name.lowercase()
}

data class CareRecord(val id: String = UUID.randomUUID().toString(), val date: LocalDate,
    val kind: CareKind, val title: String, val detail: String = "", val completedAt: String? = null,
    val visitId: String? = null, val cancelled: Boolean = false, val source: String = "Manual", val appliedAt: String? = null,
    val washEvidence: String = "EstimatedVisit") {
    fun json() = JSONObject().put("id", id).put("date", date.toString()).put("kind", kind.wire)
        .put("title", title).put("detail", detail).put("completedAt", completedAt).put("visitId", visitId)
        .put("cancelled", cancelled).put("source", source).put("appliedAt", appliedAt).put("washEvidence",washEvidence)
}

data class CarProfile(val brand: String = "", val model: String = "", val nickname: String = "",
    val plate: String = "", val fuel: String = "휘발유", val oilEstimate: String = "",
    val fuelAlert: Int = 20, val washAlert: Int = 30, val notifications: Boolean = true) {
    val electric get() = fuel == "전기차"
    val hybrid get() = fuel == "하이브리드"
    val fullModel get() = if (model.startsWith(brand)) model else "$brand $model"
}

data class AppData(val profile: CarProfile = CarProfile(), val records: List<CareRecord> = emptyList(),
    val complete: Boolean = false)

/** Reads Flutter's on-device preferences once; subsequent writes use an atomic native snapshot. */
class GarageStore(context: Context) {
    private val prefs = context.getSharedPreferences("vemoring_native", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private fun legacyString(key: String) = legacy.getString("flutter.$key", null) ?: legacy.getString(key, null)
    fun load(): AppData {
        prefs.getString("garage_v1", null)?.let { return decode(JSONObject(it)) }
        val personal = legacyString("personal_setup_v1")?.let(::JSONObject) ?: return AppData()
        val recordsJson = legacyString("care_history_v1")?.let(::JSONArray)
        val records = if (recordsJson != null) readRecords(recordsJson) else CareKind.entries.mapNotNull { kind ->
            parseDay(personal.optString(kind.key))?.takeUnless { it > LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) }?.let {
                CareRecord("setup-${kind.wire}", it, kind, kind.label)
            }
        }
        val profile = CarProfile(legacyString("vehicle_brand") ?: "", legacyString("vehicle_model") ?: "",
            personal.optString("nickname"), personal.optString("plate"), personal.optString("fuel", "휘발유"),
            personal.optString("oilEstimate"), personal.optInt("fuelAlert", 20), personal.optInt("washAlert", 30))
        return AppData(profile, records.sortedByDescending { it.date }, profile.brand.isNotBlank() && profile.model.isNotBlank())
    }
    fun save(data: AppData) {
        require(data.records.none { it.date > LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) }) { "미래 날짜에는 관리 기록을 등록할 수 없어요." }
        require(!data.complete || (data.profile.nickname.isNotBlank() && data.profile.model.isNotBlank())) { "차량 정보를 입력해 주세요." }
        val p = data.profile
        val json = JSONObject().put("complete", data.complete).put("profile", JSONObject()
            .put("brand", p.brand).put("model", p.model).put("nickname", p.nickname).put("plate", p.plate)
            .put("fuel", p.fuel).put("oilEstimate", p.oilEstimate).put("fuelAlert", p.fuelAlert)
            .put("washAlert", p.washAlert).put("notifications", p.notifications))
            .put("records", JSONArray().also { a -> data.records.forEach { a.put(it.json()) } })
        check(prefs.edit().putString("garage_v1", json.toString()).commit()) { "저장하지 못했어요. 다시 시도해 주세요." }
    }
    private fun decode(json: JSONObject): AppData {
        val p = json.getJSONObject("profile")
        return AppData(CarProfile(p.getString("brand"), p.getString("model"), p.getString("nickname"),
            p.optString("plate"), p.getString("fuel"), p.optString("oilEstimate"), p.optInt("fuelAlert",20),
            p.optInt("washAlert",30), p.optBoolean("notifications",true)),
            readRecords(json.getJSONArray("records")), json.getBoolean("complete"))
    }
    private fun readRecords(array: JSONArray) = (0 until array.length()).map { i ->
        val r = array.getJSONObject(i)
        careRecordFromJson(r)
    }.sortedByDescending { it.date }
}

fun careRecordFromJson(r: JSONObject) = CareRecord(r.getString("id"), requireNotNull(parseDay(r.getString("date"))),
    CareKind.entries.first { it.wire == r.getString("kind") }, r.getString("title"), r.optString("detail"),
    r.optString("completedAt").takeUnless { it.isBlank() || it == "null" },
    r.optString("visitId").takeUnless { it.isBlank() || it == "null" }, r.optBoolean("cancelled"), r.optString("source", "Manual"),
    r.optString("appliedAt").takeUnless { it.isBlank() || it == "null" },r.optString("washEvidence","EstimatedVisit"))

fun parseDay(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
fun List<CareRecord>.latest(kind: CareKind) = filter { it.kind == kind && !it.cancelled }.maxByOrNull { it.date }?.date
fun updateLatest(records: List<CareRecord>, dates: Map<CareKind, LocalDate?>): List<CareRecord> {
    val updated = records.toMutableList()
    dates.forEach { (kind, date) ->
        if (date != null && date != records.latest(kind)) {
            require(date <= LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
            val old = updated.filter { it.kind == kind && !it.cancelled }.maxByOrNull { it.date }
            if (old != null) updated.remove(old)
            updated.add(old?.copy(date = date) ?: CareRecord("setup-${kind.wire}", date, kind, kind.label))
        }
    }
    return updated.sortedByDescending { it.date }
}
