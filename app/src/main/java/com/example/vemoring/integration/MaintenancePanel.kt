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

data class MaintenanceConfig(val unit: String = "", val washId: String = "", val washKm: Double? = null,
    val oilId: String = "", val oilKm: Double? = null, val intervalKm: Double? = null)
class MaintenanceStore(context: Context, profile: CarProfile) {
    private val prefs=context.getSharedPreferences("vemoring_sdk_care_v1",Context.MODE_PRIVATE)
    private val prefix=java.security.MessageDigest.getInstance("SHA-256").digest((profile.fullModel+"|"+profile.plate).toByteArray())
        .joinToString("") { "%02x".format(it) } + "."
    fun load() = MaintenanceConfig(prefs.getString(prefix+"unit","") ?: "",
        prefs.getString(prefix+"washId","") ?: "", prefs.getString(prefix+"washKm",null)?.toDoubleOrNull(),
        prefs.getString(prefix+"oilId","") ?: "",prefs.getString(prefix+"oilKm",null)?.toDoubleOrNull(),
        prefs.getString(prefix+"intervalKm",null)?.toDoubleOrNull())
    fun save(value: MaintenanceConfig): Boolean = prefs.edit().putString(prefix+"unit",value.unit)
        .putString(prefix+"washId",value.washId).putString(prefix+"washKm",value.washKm?.toString())
        .putString(prefix+"oilId",value.oilId).putString(prefix+"oilKm",value.oilKm?.toString())
        .putString(prefix+"intervalKm",value.intervalKm?.toString()).commit()
}
fun confirmedKilometres(reading: Reading, userUnit: String): Double? {
    if(reading.state!=ReadingState.Available) return null
    val value=reading.value?.number?.takeIf { it.isFinite() && it>=0 } ?: return null
    return when(userUnit) { "km" -> value; "m" -> value/1000; "mi" -> value*1.609344; else -> null }
}
@Composable
fun MaintenancePanel(data: AppData, snapshot: VehicleSnapshot, weather: Result<EnvironmentData>, editable: Boolean) {
    val context=LocalContext.current
    val store=remember(data.profile.fullModel,data.profile.plate) { MaintenanceStore(context,data.profile) }
    var config by remember(store) { mutableStateOf(store.load()) }
    var status by remember(store) { mutableStateOf("") }
    var oilInput by remember(store) { mutableStateOf("") }
    var washInput by remember(store) { mutableStateOf("") }
    var intervalInput by remember(store) { mutableStateOf(config.intervalKm?.toString() ?: "") }
    val wash=data.records.filter { it.kind==CareKind.WASH }.maxByOrNull { it.date }
    val oil=data.records.filter { it.kind==CareKind.OIL }.maxByOrNull { it.date }
    val current=confirmedKilometres(snapshot[VehicleField.ODOMETER],config.unit)
    val washDistance=if(wash?.let { it.id+"@"+it.date }==config.washId && current!=null) config.washKm?.let { (current-it).takeIf { distance -> distance>=0 } } else null
    val days=wash?.let { ChronoUnit.DAYS.between(it.date,LocalDate.now()).coerceAtLeast(0) }
    val realWeather=weather.takeIf { it.state==ResultState.Available && !it.source.startsWith("Demo") &&
        it.receivedAt?.let { at -> System.currentTimeMillis()-at in 0..3600000 }==true }?.value
    val clean=days?.let { CareEstimator.cleanliness(it,washDistance,null,realWeather?.pm10,realWeather?.dust) }
    fun persist(value: MaintenanceConfig) {
        if(store.save(value)) { config=value; status="별도 SDK 관리 설정 저장됨 · User Input" } else status="저장 실패"
    }
    Panel {
        Text("차량 관리 추정 · 실측 오염도/제조사 정비 진단 아님")
        Text("청결도: ${clean?.let { "$it %" } ?: "세차 기록 필요"}")
        Text("날짜 기반${if(washDistance!=null) " + 확인 단위 거리" else " · 거리 기준 미연결"}${if(realWeather!=null) " + 모델 대기질" else ""}")
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
            OutlinedTextField(value=washInput,onValueChange={washInput=it},label={Text("마지막 세차 당시 누적 거리 (km, 사용자 입력)")})
            TextButton(enabled=wash!=null && washInput.toDoubleOrNull()?.let { it.isFinite() && it>=0 }==true,onClick={
                persist(config.copy(washId=wash!!.id+"@"+wash.date,washKm=washInput.toDouble()))
            }) { Text("현재 마지막 세차 기록에 연결") }
            TextButton(enabled=wash?.date==LocalDate.now() && current!=null,onClick={persist(config.copy(washId=wash!!.id+"@"+wash.date,washKm=current))}) { Text("오늘 세차 기록에 현재 수신 거리 연결") }
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
