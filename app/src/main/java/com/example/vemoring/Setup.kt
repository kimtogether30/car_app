package com.example.vemoring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import com.example.vemoring.vehicle.*
import kotlinx.coroutines.delay

@Composable
fun SetupScreen(detected: VehicleIdentityReading = VehicleIdentityReading(),
                onDockChoice: (Boolean) -> Unit = {},
                finish: (CarProfile, Map<CareKind, LocalDate?>) -> Unit) {
    var ready by rememberSaveable { mutableStateOf(false) }
    var confirmedBrand by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmedModel by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmedFuel by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmedPlate by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(detected, ready) {
        if (!ready) {
            if (detected.state == ReadingState.Loading) delay(4000)
            val info = detected.confirmed()
            confirmedBrand = info.brand; confirmedModel = info.model
            confirmedFuel = info.fuel; confirmedPlate = info.plate
            ready = true
        }
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                CircularProgressIndicator()
                Text("차량 정보를 확인하고 있어요", fontSize = 28.sp)
                TextButton(onClick = { ready = true }) { Text("직접 입력하기") }
            }
        }
        return
    }
    // Freeze detection once questions start: late responses must not skip a question being edited.
    SetupForm(ConfirmedVehicleInfo(confirmedBrand, confirmedModel, confirmedFuel, confirmedPlate), onDockChoice, finish)
}

@Composable
private fun SetupForm(confirmed: ConfirmedVehicleInfo, onDockChoice: (Boolean) -> Unit,
                      finish: (CarProfile, Map<CareKind, LocalDate?>) -> Unit) {
    val steps = remember(confirmed) { setupSteps(confirmed) }
    var step by rememberSaveable { mutableIntStateOf(steps.first()) }
    var brand by rememberSaveable { mutableStateOf(confirmed.brand ?: "") }
    var model by rememberSaveable { mutableStateOf(confirmed.model ?: "") }
    var fuel by rememberSaveable { mutableStateOf(confirmed.fuel ?: "") }
    val position = steps.indexOf(step).coerceAtLeast(0)
    fun next() { step = steps[(position + 1).coerceAtMost(steps.lastIndex)] }
    fun previous() { step = steps[(position - 1).coerceAtLeast(0)] }
    var oil by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var plate by rememberSaveable {mutableStateOf(confirmed.plate.orEmpty())}
    val focusManager = LocalFocusManager.current
    var fuelDate by rememberSaveable { mutableStateOf<String?>(null) }
    var washDate by rememberSaveable { mutableStateOf<String?>(null) }
    var fuelAnswered by rememberSaveable { mutableStateOf(false) }
    var washAnswered by rememberSaveable { mutableStateOf(false) }
    BackHandler(position > 0) { previous() }
    key(step) {
        SetupPage {
            Text("VEMORING", color = Blue, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("나에게 맞는 차량 관리의 시작 · ${position + 1} / ${steps.size}", color = Blue, fontSize = 20.sp)
            LinearProgressIndicator(progress = { (position + 1).toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
            Text(if (steps.size < 7) "차량에서 확인한 정보는 채워뒀어요. 나머지만 알려주세요."
                else "자동으로 확인되지 않은 차량 정보만 알려주세요.", color = Ink, fontSize = 20.sp)
            Spacer(Modifier.height(24.dp))
            if(position > 0) TextButton(onClick = { previous() }) { Text("‹ 이전") }
            Text(when(step) {
                0 -> "어떤 브랜드의\n차량을 타고 계신가요?"
                1 -> "차종을 선택해 주세요"
                2 -> "주유 방식을 선택해 주세요"
                3 -> "마지막으로 관리한 때는\n언제인가요?"
                4 -> "차량의 닉네임을 지어 주세요"
                6 -> "하단 바에 VEMORING을\n추가할까요?"
                else -> "기본 알림 기준을 설정해 뒀어요!"
            }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 44.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            when(step) {
                0 -> SetupChoices(VehicleCatalog.brands, brand) {
                    brand = it; if (confirmed.model == null) model = ""; next()
                }
                1 -> {
                    Text("$brand 차종")
                    remember(brand) { VehicleCatalog.categories(brand) }.forEach { (category, models) ->
                        Text(category, color = Blue)
                        ChoiceRow(models, model) { model = it; next() }
                    }
                    if (VehicleCatalog.categories(brand).isEmpty()) {
                        OutlinedTextField(model, { model = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, label = { Text("차종") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = { next() }, enabled = model.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("다음") }
                    }
                }
                2 -> SetupChoices(listOf("휘발유", "경유", "하이브리드", "전기차"), fuel) { fuel = it; next() }
                3 -> {
                    DateInput(if(fuel == "전기차") "마지막 충전일" else "마지막 주유일", fuelDate?.let(LocalDate::parse), {
                        fuelDate = it?.toString(); fuelAnswered = true
                    }, false)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fuelAnswered && fuelDate == null, { fuelAnswered = it; fuelDate = null }); Text("주유·충전일이 기억이 안 나요") }
                    DateInput("마지막 세차일", washDate?.let(LocalDate::parse), { washDate = it?.toString(); washAnswered = true }, false)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(washAnswered && washDate == null, { washAnswered = it; washDate = null }); Text("세차일이 기억이 안 나요") }
                    if(fuel != "전기차") {
                        Text("엔진오일 교체 시점")
                        ChoiceRow(listOf("1개월 이내", "3개월 이내", "6개월 이내", "기억이 안 나요"), oil) { oil = it }
                    }
                    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), enabled = fuelAnswered && washAnswered && (fuel == "전기차" || oil.isNotBlank()), onClick = { next() }) { Text("다음") }
                }
                4 -> {
                    OutlinedTextField(nickname, { nickname = it }, label = { Text("차량 닉네임") },
                        supportingText = { Text("한글과 영문 모두 사용할 수 있어요. 키보드의 한/영 버튼으로 전환해 주세요.") },
                        keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(plate,{plate=it}, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions,label={Text("번호판 (선택)")},singleLine=true,modifier=Modifier.fillMaxWidth(),
                        supportingText={Text("입력하지 않아도 차량을 등록할 수 있어요.")})
                    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), enabled = nickname.text.isNotBlank(), onClick = { focusManager.clearFocus(); next() }) { Text("다음") }
                }
                6 -> {
                    CarCharacter(Modifier.fillMaxWidth().height(170.dp))
                    Text("다른 앱을 쓰다가도 쉽게 돌아올 수 있어요.", fontSize = 24.sp, textAlign = TextAlign.Center)
                    Panel {
                        Text("이 차량에서는 직접 고정해 주세요", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("설정을 마치면 앱 목록에서 VEMORING을 길게 눌러 하단 바에 놓는 방법을 안내할게요.", fontSize = 22.sp)
                        Text("현재 Pleos에서는 앱 목록의 상태 아이콘은 바뀌지만, 하단 바 아이콘은 이전 모습으로 남을 수 있어요.", fontSize = 20.sp)
                    }
                    Button(onClick = { onDockChoice(true); next() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text("네, 추가 방법 볼게요") }
                    OutlinedButton(onClick = { onDockChoice(false); next() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) { Text("나중에 할게요") }
                }
                else -> {
                    CarCharacter(Modifier.fillMaxWidth().height(170.dp))
                    Panel {
                        Text("${nickname.text.trim()} · $brand $model", fontWeight = FontWeight.Bold)
                        Text(fuel + (confirmed.plate?.let { " · $it" } ?: ""))
                        if (steps.size < 7) Text("차량에서 확인한 정보도 내 차 설정에서 수정할 수 있어요.")
                        Text("주유·충전 알림: 20% 이하\n세차 알림: 청결도 30% 이하")
                        Text("내 차 설정에서 변경할 수 있어요. 알림 발송과 날씨는 아직 예시 기능입니다.")
                    }
                    Button(onClick = {
                        finish(CarProfile(brand, model, nickname.text.trim(), plate = plate.trim(), fuel = fuel, oilEstimate = if(fuel == "전기차") "해당 없음" else oil),
                            mapOf(CareKind.FUEL to fuelDate?.let(LocalDate::parse), CareKind.WASH to washDate?.let(LocalDate::parse)))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("VEMORING 시작하기") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupChoices(options: List<String>, selected: String, change: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        options.forEach { label ->
            OutlinedButton(onClick = { change(label) },
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().heightIn(min = 120.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected == label) Blue.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.White)) {
                Text(label, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Top-aligned hierarchy with one scroll container for long forms and smaller windows. */
@Composable
private fun SetupPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Top),
                content = content)
        }
    }
}

@Composable
fun VehicleDialog(profile: CarProfile, records: List<CareRecord>, close: () -> Unit,register: Boolean = false,
    save: (CarProfile, Map<CareKind, LocalDate?>) -> Unit) {
    var brand by rememberSaveable { mutableStateOf(profile.brand) }
    var model by rememberSaveable { mutableStateOf(profile.model) }
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var plate by rememberSaveable { mutableStateOf(profile.plate) }
    var fuel by rememberSaveable { mutableStateOf(profile.fuel) }
    var wash by rememberSaveable { mutableStateOf(records.latest(CareKind.WASH)?.toString()) }
    var lastFuel by rememberSaveable { mutableStateOf(records.latest(CareKind.FUEL)?.toString()) }
    var oil by rememberSaveable { mutableStateOf(records.latest(CareKind.MAINTENANCE)?.toString()) }
    AlertDialog(onDismissRequest = close, title = { Text(if(register) "차량 등록" else "차량 정보 편집") }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceRow(VehicleCatalog.brands, brand) { brand = it; model = "" }
            VehicleCatalog.categories(brand).forEach { (category, models) ->
                Text(category, fontSize = 12.sp, color = Blue)
                ChoiceRow(models, model) { model = it }
            }
            OutlinedTextField(model, { model = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, label = { Text("차종 (직접 입력 가능)") }, singleLine = true)
            ChoiceRow(listOf("휘발유", "경유", "하이브리드", "전기차"), fuel) { fuel = it }
            OutlinedTextField(nickname, { nickname = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, label = { Text("차량 닉네임") }, singleLine = true)
            OutlinedTextField(plate, { plate = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, label = { Text("번호판 (선택)") }, singleLine = true)
            DateInput("마지막 주유·충전일", lastFuel?.let(LocalDate::parse), { lastFuel = it?.toString() })
            DateInput("마지막 세차일", wash?.let(LocalDate::parse), { wash = it?.toString() })
            DateInput("마지막 정비일", oil?.let(LocalDate::parse), { oil = it?.toString() })
            Text("날짜를 모르면 기존 기록을 유지해요. 삭제는 관리 기록에서 별도로 확인해 주세요.",fontSize=18.sp)
        }
    }, confirmButton = { TextButton(enabled = nickname.isNotBlank() && model.isNotBlank(), onClick = {
        save(profile.copy(brand = brand, model = model.trim(), nickname = nickname.trim(), plate = plate.trim(), fuel = fuel),
            mapOf(CareKind.WASH to wash?.let(LocalDate::parse), CareKind.FUEL to lastFuel?.let(LocalDate::parse),
                CareKind.MAINTENANCE to oil?.let(LocalDate::parse)))
    }) { Text("저장하기") } }, dismissButton = { TextButton(onClick = close) { Text("취소") } })
}
