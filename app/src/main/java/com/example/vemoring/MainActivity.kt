package com.example.vemoring

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.example.vemoring.vehicle.*
import com.example.vemoring.integration.*
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

val Blue = Color(0xFF335C81)
val Ink = Color(0xFF202C35)
val Navy = Color(0xFF253F52)
val Mint = Color(0xFF387969)
val Orange = Color(0xFFFF8A3D)
private val Background = Color(0xFFF6F7F8)
private val Muted = Color(0xFF66727D)
val CareKind.color get() = when(this) { CareKind.WASH -> Blue; CareKind.FUEL -> Orange; CareKind.OIL -> Mint }

class MainActivity : ComponentActivity() {
    private val vehicleModel: VehicleViewModel by viewModels()
    private val integrationModel: IntegrationViewModel by viewModels()
    override fun onStart() { super.onStart(); vehicleModel.resume(); integrationModel.resume() }
    override fun onStop() { vehicleModel.pause(); integrationModel.pause(); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        integrationModel.observeVehicle(vehicleModel.state)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Mint,
                surface = Color.White, background = Background, onSurface = Ink, onBackground = Ink)) {
                GarageApp(vehicleModel = vehicleModel, integrationModel = integrationModel)
            }
        }
    }
}

@Composable
fun GarageApp(storeOverride: GarageStore? = null, vehicleModel: VehicleViewModel? = null, integrationModel: IntegrationViewModel? = null) {
    val context = LocalContext.current
    val store = remember(storeOverride) { storeOverride ?: GarageStore(context) }
    var data by remember { mutableStateOf(AppData()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var editCar by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    val vehicleSnapshot = vehicleModel?.state?.collectAsState()?.value ?: VehicleSnapshot()
    LaunchedEffect(retry) {
        runCatching { store.load() }.onSuccess { data = it; loaded = true; loadError = null }
            .onFailure { loadError = "저장 데이터를 불러오지 못했어요. 기존 데이터는 보존됩니다." }
    }
    val save: (AppData) -> Boolean = { updated ->
        runCatching { store.save(updated) }.fold(onSuccess = { data = updated; true },
            onFailure = { error = it.message ?: "저장하지 못했어요."; false })
    }
    LaunchedEffect(data.complete, vehicleModel) { vehicleModel?.setEnabled(data.complete) }
    if(diagnostics && integrationModel != null) {
        BackHandler { diagnostics=false }
        IntegrationScreen(integrationModel, vehicleSnapshot, { mode ->
            integrationModel.switchMode(mode); vehicleModel?.setMode(mode)
        }, data, { diagnostics=false })
        return
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFE8E9ED)), contentAlignment = Alignment.TopCenter) {
        Surface(Modifier.widthIn(max = 760.dp).fillMaxHeight().fillMaxWidth(), color = Background) {
            when {
                loadError != null -> Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(loadError!!); Button(onClick = { retry++ }) { Text("다시 시도") }
                }
                !loaded -> Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !data.complete -> SetupScreen { profile, dates ->
                    save(AppData(profile, updateLatest(data.records, dates), true))
                }
                else -> Scaffold(containerColor = Background, bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        listOf("홈", "관리 기록", "내 차").forEachIndexed { i, title ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i },
                                icon = { Text(listOf("⌂", "▦", "◎")[i], fontSize = 24.sp) }, label = { Text(title) })
                        }
                    }
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        when(tab) {
                            0 -> HomeScreen(data, vehicleSnapshot, { notifications = true }, { editName = true }, { tab = 1 }, { vehicleModel?.refresh() }, integrationModel, { diagnostics=true })
                            1 -> CalendarScreen(data.records) { record ->
                                save(data.copy(records = (data.records.filter { it.id != record.id } + record).sortedByDescending { it.date }))
                            }
                            else -> ProfileScreen(data.profile, { editCar = true }, { editName = true }) {
                                save(data.copy(profile = it))
                            }
                        }
                    }
                }
            }
        }
    }
    if (editCar) VehicleDialog(data.profile, data.records, { editCar = false }) { p, dates ->
        if (save(data.copy(profile = p, records = updateLatest(data.records, dates)))) editCar = false
    }
    if (editName) TextEditDialog("닉네임 변경", data.profile.nickname, { editName = false }) {
        if (save(data.copy(profile = data.profile.copy(nickname = it)))) editName = false
    }
    if (notifications) AlertDialog(onDismissRequest = { notifications = false }, title = { Text("알림 내역") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("예시 알림 · 실제 알림은 발송되지 않습니다.", color = Muted)
            Text("연료를 확인해주세요\n연료가 ${data.profile.fuelAlert}% 이하가 되면 알려드릴게요.")
            Text("엔진오일 교체가 다가와요\n예시 · 교체 예정일까지 12일 남았습니다.")
            Text("세차하기 좋은 날이에요\n예시 · 오늘은 맑고 미세먼지가 적어요.")
        } }, confirmButton = { TextButton(onClick = { notifications = false }) { Text("모두 확인했어요") } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("저장 확인") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } }) }
}

@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
fun Heading(eyebrow: String, title: String, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        action()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(16.dp), tonalElevation = 0.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun HomeScreen(data: AppData, snapshot: VehicleSnapshot, notifications: () -> Unit, edit: () -> Unit, calendar: () -> Unit, refresh: () -> Unit, integrationModel: IntegrationViewModel? = null, diagnostics: () -> Unit = {}) {
    val p = data.profile
    val battery = snapshot[VehicleField.SOC]
    val fuel = snapshot[VehicleField.FUEL]
    val energy = if (p.electric) battery else fuel
    PageColumn {
        Heading("VEHICLE + EMOTION + RING", "VEMORING") { TextButton(onClick = notifications) { Text("알림") } }
        Text(LocalDate.now().toString(), color = Blue, fontWeight = FontWeight.Bold)
        Text("오늘의 차량 상태\n가볍게 확인하고 출발해요.", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.BottomCenter) {
            CarCharacter(Modifier.width(310.dp).height(205.dp), concerned = snapshot.priorityWarning()!=null || (energy.state in setOf(ReadingState.Available,ReadingState.Demo) && (energy.value?.number ?: 101.0)<=p.fuelAlert))
            Surface(Modifier.align(Alignment.TopEnd), color = Navy, shape = RoundedCornerShape(16.dp)) {
                Text(snapshot.priorityWarning() ?: if (energy.state in setOf(ReadingState.Available, ReadingState.Demo)) { if ((energy.value?.number ?: 100.0) <= p.fuelAlert) "에너지가 부족해요. 주유·충전이 필요해요!" else "오늘도 함께 안전하게 달려요!" } else "차량 상태 정보를 확인하고 있어요.", Modifier.padding(14.dp), color = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nickname, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(listOf(p.fullModel, p.plate).filter { it.isNotBlank() }.joinToString(" · "), color = Muted)
            }
            TextButton(onClick = edit) { Text("수정") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!p.electric) Stat("남은 연료", fuel.value?.number?.toInt(), fuel.statusText(), Blue, Modifier.weight(1f))
            if (p.electric || p.hybrid) Stat("남은 배터리", battery.value?.number?.toInt(), battery.statusText(), Blue, Modifier.weight(1f))
            Stat("차량 청결도", null, "마지막 세차 ${data.records.latest(CareKind.WASH) ?: "기록 없음"}", Mint, Modifier.weight(1f))
        }
        VehicleDetails(snapshot, refresh)
        integrationModel?.let { model ->
            val mode by model.mode.collectAsState()
            val weather by model.environment.collectAsState()
            MaintenancePanel(data,snapshot,weather,false)
            Panel {
                Text("데이터 모드: ${mode.name} · 차량 프로필/관리 기록: User Input")
                TextButton(onClick=diagnostics) { Text("주변 관리 장소 · 위치 · SDK 진단") }
                weather.value?.let {
                    Text("기온 ${it.temperature ?: "—"} °C · 강수 예보 ${it.forecastRainMm ?: "—"} mm")
                    Text(it.attribution)
                } ?: Text("날씨·대기질 미수신 · 위치 사용 후 진단 화면에서 조회해 주세요.")
            }
        }
        CareKind.entries.forEach { kind -> Text("마지막 ${kind.label}일: ${data.records.latest(kind) ?: "기록 없음"}", color = Muted, fontSize = 13.sp) }

        Panel(Modifier.clickable(onClick = calendar), Navy) {
            Text("다가오는 관리 일정", color = Color.White, fontWeight = FontWeight.Bold)
            Text("저장된 차량 관리 기록을 확인해 보세요  ›", color = Color.White)
        }
    }
}

@Composable
fun Stat(title: String, value: Int?, detail: String, color: Color, modifier: Modifier) {
    Panel(modifier) {
        Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
        Text(value?.let { "$it %" } ?: "—", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { (value ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = color)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun ProfileScreen(p: CarProfile, edit: () -> Unit, editName: () -> Unit, save: (CarProfile) -> Boolean) {
    PageColumn {
        Heading("MY GARAGE", "내 차 설정")
        Panel(color = Navy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarCharacter(Modifier.size(110.dp, 80.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.nickname, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(p.fullModel, color = Color.White)
                }
                TextButton(onClick = edit) { Text("편집", color = Color.White) }
            }
        }
        Text("차량 정보", fontWeight = FontWeight.Bold)
        Panel {
            SettingRow("닉네임", p.nickname, editName)
            HorizontalDivider()
            SettingRow("차종", p.fullModel, edit)
            HorizontalDivider()
            SettingRow("차량 정보 다시 등록", p.fuel, edit)
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("관리 알림", fontWeight = FontWeight.Bold)
                    Text("예시 설정 · 실제 알림은 발송되지 않습니다.", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = p.notifications, onCheckedChange = { save(p.copy(notifications = it)) })
            }
        }
        Panel {
            Text("개인 커스텀", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Threshold("세차 알림 기준", p.washAlert, 5f..50f, 8) { save(p.copy(washAlert = it)) }
            HorizontalDivider()
            Threshold("주유·충전 알림 기준", p.fuelAlert, 5f..40f, 6) { save(p.copy(fuelAlert = it)) }
        }
    }
}

@Composable
fun SettingRow(title: String, value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, color = Muted, fontSize = 13.sp) }
        Text("›", color = Blue, fontSize = 24.sp)
    }
}

@Composable
fun Threshold(label: String, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int, save: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat().coerceIn(range)) }
    Text("$label  ${current.toInt()}% 이하", fontWeight = FontWeight.Bold)
    Slider(value = current, onValueChange = { current = it }, valueRange = range, steps = steps,
        onValueChangeFinished = { save(current.toInt()) })
}

@Composable
fun CalendarScreen(records: List<CareRecord>, save: (CareRecord) -> Boolean) {
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthValue)
    var selectedValue by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedValue?.let(LocalDate::parse)
    var all by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CareRecord?>(null) }
    var futureError by remember { mutableStateOf(false) }
    val visible = if (all) records else if (selected != null) records.filter { it.date == selected } else records.take(3)
    PageColumn {
        Heading("MY CAR LOG", "관리 기록") {
            Button(onClick = {
                if (selected != null && selected > LocalDate.now()) futureError = true
                else { editing = null; showEditor = true }
            }) { Text("＋ 추가") }
        }
        Text("직접 입력한 기록 · 이 기기에 저장됩니다.", color = Muted, fontSize = 12.sp)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { monthValue = month.minusMonths(1).toString(); selectedValue = null }) { Text("‹") }
                Text("${month.year}년 ${month.monthValue}월", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { monthValue = month.plusMonths(1).toString(); selectedValue = null }) { Text("›") }
            }
            Row { listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, color = if(day == "일") Orange else Muted) }
            } }
            val offset = month.atDay(1).dayOfWeek.value % 7
            val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
            repeat(cells / 7) { week ->
                Row {
                    repeat(7) { column ->
                        val day = week * 7 + column - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(58.dp))
                        else {
                            val date = month.atDay(day)
                            val matching = records.filter { it.date == date }
                            Column(Modifier.weight(1f).height(58.dp)
                                .background(if(selected == date) Color(0xFFE5ECF2) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { selectedValue = if(selected == date) null else date.toString(); all = false },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$day", color = when { date == LocalDate.now() -> Blue; date > LocalDate.now() -> Muted; else -> Ink },
                                    fontWeight = if(date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                if (matching.isNotEmpty()) Text(if(matching.size > 1) "${matching.size}건" else matching.first().kind.label,
                                    fontSize = 10.sp, color = matching.first().kind.color, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CareKind.entries.forEach { Text("● ${it.label}", color = it.color, fontSize = 12.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if(all) "전체 관리 기록" else selected?.let { "${it.monthValue}월 ${it.dayOfMonth}일 관리" } ?: "최근 관리",
                Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { all = !all; selectedValue = null }) { Text(if(all) "최근 보기" else "전체 보기") }
        }
        Panel {
            if (visible.isEmpty()) Text("등록된 관리 기록이 없어요.", color = Muted)
            visible.forEach { record ->
                Column(Modifier.fillMaxWidth().clickable { editing = record; showEditor = true }.padding(vertical = 8.dp)) {
                    Text("${record.kind.label} · ${record.title}", fontWeight = FontWeight.Bold, color = record.kind.color)
                    Text(record.date.toString(), color = Muted, fontSize = 12.sp)
                    if (record.detail.isNotBlank()) Text(record.detail)
                }
            }
        }
    }
    if (showEditor) RecordDialog(editing, selected ?: LocalDate.now(), { showEditor = false }) {
        if(save(it)) { monthValue = YearMonth.from(it.date).toString(); selectedValue = it.date.toString(); showEditor = false }
    }
    if (futureError) AlertDialog(onDismissRequest = { futureError = false }, text = { Text("미래 날짜에는 관리 기록을 등록할 수 없어요.") },
        confirmButton = { TextButton(onClick = { futureError = false }) { Text("확인") } })
}

@Composable
fun DateInput(label: String, date: LocalDate?, update: (LocalDate?) -> Unit, allowUnknown: Boolean = true) {
    val context = LocalContext.current
    Column {
        OutlinedButton(onClick = {
            val start = date ?: LocalDate.now()
            DatePickerDialog(context, { _, y, m, d -> update(LocalDate.of(y,m+1,d)) }, start.year, start.monthValue-1, start.dayOfMonth)
                .apply { datePicker.maxDate = System.currentTimeMillis(); datePicker.minDate = java.util.GregorianCalendar(1980,0,1).timeInMillis }.show()
        }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${date ?: "날짜 선택"}") }
        if(allowUnknown) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = date == null, onCheckedChange = { if(it) update(null) else update(LocalDate.now()) })
            Text("기억이 안 나요 / 기록 없음", fontSize = 12.sp)
        }
    }
}

@Composable
fun RecordDialog(existing: CareRecord?, initial: LocalDate, close: () -> Unit, save: (CareRecord) -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var detail by rememberSaveable { mutableStateOf(existing?.detail ?: "") }
    var kindValue by rememberSaveable { mutableStateOf(existing?.kind?.name ?: CareKind.WASH.name) }
    var dateValue by rememberSaveable { mutableStateOf((existing?.date ?: initial).toString()) }
    val kind = CareKind.valueOf(kindValue)
    AlertDialog(onDismissRequest = close, title = { Text(if(existing == null) "관리 기록 추가" else "관리 기록 수정") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceRow(CareKind.entries.map { it.label }, kind.label) { label -> kindValue = CareKind.entries.first { it.label == label }.name }
            OutlinedTextField(title, { title = it }, label = { Text("기록 이름") }, singleLine = true)
            OutlinedTextField(detail, { detail = it }, label = { Text("상세 내용 (장소·금액 등)") })
            DateInput("관리 날짜", LocalDate.parse(dateValue), { if(it != null) dateValue = it.toString() }, false)
        } }, confirmButton = { TextButton(enabled = title.isNotBlank() && LocalDate.parse(dateValue) <= LocalDate.now(), onClick = {
            save(CareRecord(existing?.id ?: java.util.UUID.randomUUID().toString(), LocalDate.parse(dateValue), kind, title.trim(), detail.trim()))
        }) { Text("기록 저장") } }, dismissButton = { TextButton(onClick = close) { Text("취소") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(options: List<String>, selected: String, change: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { label -> FilterChip(selected == label, onClick = { change(label) }, label = { Text(label) }) }
    }
}

@Composable
fun TextEditDialog(title: String, initial: String, close: () -> Unit, save: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("닉네임") })
    }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { save(value.trim()) }) { Text("저장하기") } },
        dismissButton = { TextButton(onClick = close) { Text("취소") } })
}
