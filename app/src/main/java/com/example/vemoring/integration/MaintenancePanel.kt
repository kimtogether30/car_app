package com.example.vemoring.integration

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.vemoring.*
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.mapLatest
import com.vemoring.companion.*

data class MaintenanceConfig(val unit: String = "", val washId: String = "", val washKm: Double? = null,
    val oilId: String = "", val oilKm: Double? = null, val intervalKm: Double? = null,
    val intervals: List<TravelInterval> = emptyList(), val syncStatus: String = "차량 임시 추정", val phoneSyncPending: Boolean = false,
    val authority: CareAuthority? = null)
fun vehicleCareId(profile: CarProfile): String = profile.vehicleId
class MaintenanceStore(context: Context, profile: CarProfile) {
    private val journal = CareJournal(context, vehicleCareId(profile))
    private val carePrefs = context.getSharedPreferences("care_journal_v1", Context.MODE_PRIVATE)
    private val prefs=context.getSharedPreferences("vemoring_sdk_care_v1",Context.MODE_PRIVATE)
    private val prefix=profile.vehicleId+"."
    fun distanceUnit(): String = prefs.getString(prefix+"unit", "") ?: ""
    fun load(): MaintenanceConfig {
        val care = journal.configState()
        return MaintenanceConfig(prefs.getString(prefix+"unit","") ?: "",
            prefs.getString(prefix+"washId","") ?: "", prefs.getString(prefix+"washKm",null)?.toDoubleOrNull(),
            prefs.getString(prefix+"oilId","") ?: "",prefs.getString(prefix+"oilKm",null)?.toDoubleOrNull(),
            prefs.getString(prefix+"intervalKm",null)?.toDoubleOrNull(), care.intervals, care.syncStatus, care.pending,care.authority)
    }
    fun save(value: MaintenanceConfig): Boolean = prefs.edit().putString(prefix+"unit",value.unit)
        .putString(prefix+"washId",value.washId).putString(prefix+"washKm",value.washKm?.toString())
        .putString(prefix+"oilId",value.oilId).putString(prefix+"oilKm",value.oilKm?.toString())
        .putString(prefix+"intervalKm",value.intervalKm?.toString()).commit()
    fun observe(changed: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith(prefix) == true) changed()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Other vehicles' journals do not affect this config. A null key means the file was cleared.
        val careListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == journal.key) changed()
        }
        carePrefs.registerOnSharedPreferenceChangeListener(careListener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener); carePrefs.unregisterOnSharedPreferenceChangeListener(careListener) }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun rememberMaintenanceConfig(profile: CarProfile): State<MaintenanceConfig> {
    val context = LocalContext.current.applicationContext
    val store = remember(profile.vehicleId) { MaintenanceStore(context, profile) }
    return produceState(MaintenanceConfig(), store) {
        // Collapse bursts and cancel stale reads instead of launching an independent load per key.
        callbackFlow {
            val stop = store.observe { trySend(Unit) }
            trySend(Unit)
            awaitClose { stop() }
        }.conflate().mapLatest { withContext(Dispatchers.IO) { store.load() } }.collect { value = it }
    }
}
fun confirmedKilometres(reading: Reading, userUnit: String): Double? {
    if(reading.state!=ReadingState.Available) return null
    val value=reading.value?.number?.takeIf { it.isFinite() && it>=0 } ?: return null
    return when(userUnit) { "km" -> value; "m" -> value/1000; "mi" -> value*1.609344; else -> null }
}
@Composable
fun MaintenancePanel(data: AppData, snapshot: VehicleSnapshot, weather: Result<EnvironmentData>, editable: Boolean) {
    val context=LocalContext.current
    val store=remember(data.profile.vehicleId) { MaintenanceStore(context,data.profile) }
    val config by rememberMaintenanceConfig(data.profile)
    val scope = rememberCoroutineScope()
    var status by remember(store) { mutableStateOf("") }
    var oilInput by remember(store) { mutableStateOf("") }
    var intervalInput by remember(store) { mutableStateOf(config.intervalKm?.toString() ?: "") }
    LaunchedEffect(config.intervalKm) { intervalInput = config.intervalKm?.toString() ?: "" }
    val wash=data.records.filter { it.kind==CareKind.WASH }.maxByOrNull { it.date }
    val oil=data.records.filter { it.kind==CareKind.MAINTENANCE && !it.cancelled && it.maintenanceType==com.vemoring.companion.MaintenanceType.ENGINE_OIL }.maxByOrNull { it.date }
    val current=confirmedKilometres(snapshot[VehicleField.ODOMETER],config.unit)
    val clean=careCondition(data, snapshot, config).cleanliness
    fun persist(value: MaintenanceConfig) {
        scope.launch {
            status = if(withContext(Dispatchers.IO) { store.save(value) }) "관리 기준을 저장했어요" else "저장 실패"
        }
    }
    Panel {
        Text("차량 관리 추정 · 실측 오염도/제조사 정비 진단 아님")
        Text("청결도: ${clean?.let { "$it %" } ?: "세차 기록 필요"}")
        Text("실제 거리 우선 · 미확인 구간은 하루 20km 기준 추정")
        Text("누적 거리: ${current?.let { "%.1f km".format(it) } ?: "단위 확인/실제 수신 필요"} · 단위 근거: User Input")
        val remaining=if(current!=null && oil?.let { it.id+"@"+it.date }==config.oilId && config.oilKm!=null && config.intervalKm!=null && current>=config.oilKm!!)
            CareEstimator.oilRemaining(current,config.oilKm!!,config.intervalKm!!) else null
        Text("엔진오일 교체까지: ${remaining?.let { "%.0f km (사용자 설정 기준)".format(it) } ?: "교체 거리·사용자 주기 설정 필요"}")
        if(editable) {
            Text("단위는 정상 PC에서 콘솔/SDK 응답을 대조한 뒤 선택하세요. 기존 날짜 기록의 과거 주행거리는 추측하지 않습니다.")
            Row {
                listOf("" to "미확인","km" to "km","m" to "m","mi" to "mi").forEach { (unit,label) ->
                    FilterChip(selected=config.unit==unit,onClick={persist(config.copy(unit=unit))},label={Text(label)})
                }
            }
            Text("세차 점수는 관리기록과 확인된 주행 구간으로 계산해요.")
            OutlinedTextField(value=oilInput,onValueChange={oilInput=it},label={Text("마지막 엔진오일 교체 당시 누적 거리 (km)")})
            OutlinedTextField(value=intervalInput,onValueChange={intervalInput=it},label={Text("사용 설명서로 확인한 교체 주기 (km)")})
            TextButton(enabled=oil!=null && oilInput.toDoubleOrNull()?.let { it.isFinite() && it>=0 }==true &&
                intervalInput.toDoubleOrNull()?.let { it.isFinite() && it>0 }==true,onClick={
                persist(config.copy(oilId=oil!!.id+"@"+oil.date,oilKm=oilInput.toDouble(),intervalKm=intervalInput.toDouble()))
            }) { Text("현재 마지막 교체 기록에 연결") }
            Text(status)
        }
    }
}
