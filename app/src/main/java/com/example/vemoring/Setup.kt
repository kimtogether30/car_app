package com.example.vemoring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun SetupScreen(finish: (CarProfile, Map<CareKind, LocalDate?>) -> Boolean) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var fuel by rememberSaveable { mutableStateOf("") }
    var oil by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var fuelDate by rememberSaveable { mutableStateOf<String?>(null) }
    var washDate by rememberSaveable { mutableStateOf<String?>(null) }
    var fuelAnswered by rememberSaveable { mutableStateOf(false) }
    var washAnswered by rememberSaveable { mutableStateOf(false) }
    BackHandler(step > 0) { step-- }
    key(step) {
        PageColumn {
            Heading("나에게 맞는 차량 관리의 시작", "VEMORING")
            LinearProgressIndicator(progress = { (step + 1) / 6f }, modifier = Modifier.fillMaxWidth())
            if(step > 0) TextButton(onClick = { step-- }) { Text("‹ 이전") }
            Text(when(step) {
                0 -> "어떤 브랜드의\n차량을 타고 계신가요?"
                1 -> "차종을 선택해 주세요"
                2 -> "주유 방식을 선택해 주세요"
                3 -> "마지막으로 관리한 때는\n언제인가요?"
                4 -> "차량의 닉네임을 지어 주세요"
                else -> "기본 알림 기준을 설정해 뒀어요!"
            }, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            when(step) {
                0 -> ChoiceRow(VehicleCatalog.brands, brand) { brand = it; model = ""; step = 1 }
                1 -> {
                    Text("$brand 차종")
                    VehicleCatalog.categories(brand).forEach { (category, models) ->
                        Text(category, color = Blue)
                        ChoiceRow(models, model) { model = it; step = 2 }
                    }
                }
                2 -> ChoiceRow(listOf("휘발유", "경유", "하이브리드", "전기차"), fuel) { fuel = it; step = 3 }
                3 -> {
                    DateInput(if(fuel == "전기차") "마지막 충전일" else "마지막 주유일", fuelDate?.let(LocalDate::parse), {
                        fuelDate = it?.toString(); fuelAnswered = true
                    }, false)
                    Row { Checkbox(fuelAnswered && fuelDate == null, { fuelAnswered = it; fuelDate = null }); Text("주유·충전일이 기억이 안 나요") }
                    DateInput("마지막 세차일", washDate?.let(LocalDate::parse), { washDate = it?.toString(); washAnswered = true }, false)
                    Row { Checkbox(washAnswered && washDate == null, { washAnswered = it; washDate = null }); Text("세차일이 기억이 안 나요") }
                    if(fuel != "전기차") {
                        Text("엔진오일 교체 시점")
                        ChoiceRow(listOf("1개월 이내", "3개월 이내", "6개월 이내", "기억이 안 나요"), oil) { oil = it }
                    }
                    Button(enabled = fuelAnswered && washAnswered && (fuel == "전기차" || oil.isNotBlank()), onClick = { step = 4 }) { Text("다음") }
                }
                4 -> {
                    OutlinedTextField(nickname, { nickname = it }, label = { Text("차량 닉네임") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(enabled = nickname.isNotBlank(), onClick = { step = 5 }) { Text("다음") }
                }
                else -> {
                    CarCharacter(Modifier.fillMaxWidth().height(170.dp))
                    Panel {
                        Text("${nickname.trim()} · $brand $model", fontWeight = FontWeight.Bold)
                        Text("주유·충전 알림: 20% 이하\n세차 알림: 청결도 30% 이하")
                        Text("내 차 설정에서 변경할 수 있어요. 알림 발송과 날씨는 아직 예시 기능입니다.")
                    }
                    Button(onClick = {
                        finish(CarProfile(brand, model, nickname.trim(), fuel = fuel, oilEstimate = if(fuel == "전기차") "해당 없음" else oil),
                            mapOf(CareKind.FUEL to fuelDate?.let(LocalDate::parse), CareKind.WASH to washDate?.let(LocalDate::parse)))
                    }, modifier = Modifier.fillMaxWidth()) { Text("VEMORING 시작하기") }
                }
            }
        }
    }
}

@Composable
fun VehicleDialog(profile: CarProfile, records: List<CareRecord>, close: () -> Unit,
    save: (CarProfile, Map<CareKind, LocalDate?>) -> Unit) {
    var brand by rememberSaveable { mutableStateOf(profile.brand) }
    var model by rememberSaveable { mutableStateOf(profile.model) }
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var plate by rememberSaveable { mutableStateOf(profile.plate) }
    var fuel by rememberSaveable { mutableStateOf(profile.fuel) }
    var wash by rememberSaveable { mutableStateOf(records.latest(CareKind.WASH)?.toString()) }
    var lastFuel by rememberSaveable { mutableStateOf(records.latest(CareKind.FUEL)?.toString()) }
    var oil by rememberSaveable { mutableStateOf(records.latest(CareKind.OIL)?.toString()) }
    AlertDialog(onDismissRequest = close, title = { Text("차량 정보 편집") }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceRow(VehicleCatalog.brands, brand) { brand = it; model = "" }
            VehicleCatalog.categories(brand).forEach { (category, models) ->
                Text(category, fontSize = 12.sp, color = Blue)
                ChoiceRow(models, model) { model = it }
            }
            OutlinedTextField(model, { model = it }, label = { Text("차종 (직접 입력 가능)") }, singleLine = true)
            ChoiceRow(listOf("휘발유", "경유", "하이브리드", "전기차"), fuel) { fuel = it }
            OutlinedTextField(nickname, { nickname = it }, label = { Text("차량 닉네임") }, singleLine = true)
            OutlinedTextField(plate, { plate = it }, label = { Text("차량 번호") }, singleLine = true)
            DateInput("마지막 주유·충전일", lastFuel?.let(LocalDate::parse), { lastFuel = it?.toString() })
            DateInput("마지막 세차일", wash?.let(LocalDate::parse), { wash = it?.toString() })
            if(fuel != "전기차") DateInput("마지막 엔진오일 교체일", oil?.let(LocalDate::parse), { oil = it?.toString() })
        }
    }, confirmButton = { TextButton(enabled = nickname.isNotBlank() && model.isNotBlank() && plate.isNotBlank(), onClick = {
        save(profile.copy(brand = brand, model = model.trim(), nickname = nickname.trim(), plate = plate.trim(), fuel = fuel),
            mapOf(CareKind.WASH to wash?.let(LocalDate::parse), CareKind.FUEL to lastFuel?.let(LocalDate::parse),
                CareKind.OIL to if(fuel == "전기차") null else oil?.let(LocalDate::parse)))
    }) { Text("저장하기") } }, dismissButton = { TextButton(onClick = close) { Text("취소") } })
}
