package com.example.vemoring

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

val Blue = Color(0xFF087BBF)
val Ink = Color(0xFF182F43)
val Navy = Color(0xFF173B54)
val Mint = Color(0xFF147D72)
val Orange = Color(0xFFB76120)
private val Background = Color(0xFFF0F4F8)
private val Muted = Color(0xFF526678)
val CareKind.color get() = when(this) { CareKind.WASH -> Blue; CareKind.FUEL -> Orange; CareKind.OIL -> Mint }

class MainActivity : ComponentActivity() {
    private val vehicleModel: VehicleViewModel by viewModels()
    private val integrationModel: IntegrationViewModel by viewModels()
    private var pendingLauncherMood: MascotMood? = null
    override fun onStart() { super.onStart(); vehicleModel.resume(); integrationModel.resume() }
    override fun onStop() {
        val next = pendingLauncherMood
        pendingLauncherMood = null
        vehicleModel.pause(); integrationModel.pause(); super.onStop()
        // Disabling a launcher alias can remove its task on Pleos. Never do this over a visible screen.
        if (next != null) lifecycleScope.launch(Dispatchers.IO) {
            runCatching { MascotLauncher.sync(applicationContext, next) }
                .onFailure { android.util.Log.w("MascotLauncher", "Unable to update launcher", it) }
        }
    }
    private var homeLaunchRequest by mutableIntStateOf(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.content.Intent.ACTION_MAIN &&
            intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)) {
            homeLaunchRequest++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        integrationModel.observeVehicle(vehicleModel.state)
        setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography, colorScheme = GarageColors) {
                GarageApp(homeLaunchRequest = homeLaunchRequest, vehicleModel = vehicleModel,
                    integrationModel = integrationModel, onLauncherMood = { pendingLauncherMood = it })
            }
        }
    }
}

@Composable
fun GarageApp(storeOverride: GarageStore? = null, startWithSetup: Boolean = false, homeLaunchRequest: Int = 0,
              careSignals: CareSignals? = null, vehicleModel: VehicleViewModel? = null,
              integrationModel: IntegrationViewModel? = null, onLauncherMood: (MascotMood) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember(storeOverride) { storeOverride ?: GarageStore(context) }
    var data by remember { mutableStateOf(AppData()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(homeLaunchRequest) { if (homeLaunchRequest > 0) tab = 0 }
    var setupRequested by rememberSaveable { mutableStateOf(startWithSetup) }
    var editCar by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    val snapshot = vehicleModel?.state?.collectAsState()?.value ?: VehicleSnapshot()
    val identity = vehicleModel?.identity?.collectAsState()?.value ?: VehicleIdentityReading()
    val mode = integrationModel?.mode?.collectAsState()?.value ?: DataMode.Real
    val environment = integrationModel?.environment?.collectAsState()?.value ?: com.example.vemoring.integration.Result<EnvironmentData>()
    val maintenance by rememberMaintenanceConfig(data.profile)
    val condition = remember(data, snapshot, maintenance, environment) { careCondition(data, snapshot, maintenance, environment) }
    val mood = (careSignals ?: condition.signals).mood()
    val hasResolvedVehicleData = snapshot.readings.values.any { it.state != ReadingState.Loading && it.state != ReadingState.Disconnected }
    LaunchedEffect(mood, mode, loaded, hasResolvedVehicleData) {
        if (loaded && mode == DataMode.Real && vehicleModel != null && hasResolvedVehicleData) onLauncherMood(mood)
    }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(store, retry) {
        runCatching { withContext(Dispatchers.IO) { store.load() } }.onSuccess { data = it; loaded = true; loadError = null }
            .onFailure { loadError = "저장 데이터를 불러오지 못했어요. 기존 데이터는 보존됩니다." }
    }
    fun save(updated: AppData, onSaved: () -> Unit = {}) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(updated) }
                data = updated
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "저장하지 못했어요."
            } finally {
                saving = false
            }
        }
    }
    LaunchedEffect(data.complete, vehicleModel) { vehicleModel?.setEnabled(data.complete) }
    if (diagnostics && integrationModel != null) {
        BackHandler { diagnostics = false }
        IntegrationScreen(integrationModel, snapshot, { next ->
            integrationModel.switchMode(next); vehicleModel?.setMode(next)
        }, data, { diagnostics = false })
        return
    }
    Box(Modifier.fillMaxSize().background(Color(0xFFE8E9ED)), contentAlignment = Alignment.TopCenter) {
        Surface(Modifier.widthIn(max = if (data.complete && !setupRequested) 1440.dp else 1120.dp).fillMaxHeight().fillMaxWidth(), color = Background) {
            when {
                loadError != null -> Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(loadError!!); Button(onClick = { retry++ }) { Text("다시 시도") }
                }
                !loaded -> Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !data.complete || setupRequested -> SetupScreen(detected = identity) { profile, dates ->
                    save(data.copy(profile = profile, records = updateLatest(data.records, dates), complete = true)) {
                        setupRequested = false
                        tab = 0
                    }
                }
                else -> Scaffold(containerColor = Background, bottomBar = {
                    GarageNavigation(tab) { tab = it }
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        when(tab) {
                            0 -> HomeScreen(data, BatteryReading(snapshot[VehicleField.SOC].realPercent(),
                                snapshot[VehicleField.SOC].statusText()), { notifications = true }, mood,
                                snapshot, condition, if (integrationModel != null) ({ diagnostics = true }) else null,
                                mode == DataMode.Demo)
                            1 -> CalendarScreen(data.records) { record, onSaved ->
                                save(data.copy(records = (data.records.filter { it.id != record.id } + record).sortedByDescending { it.date }), onSaved)
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
        save(data.copy(profile = p, records = updateLatest(data.records, dates))) { editCar = false }
    }
    if (editName) TextEditDialog("닉네임 변경", data.profile.nickname, { editName = false }) {
        save(data.copy(profile = data.profile.copy(nickname = it))) { editName = false }
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
    if (saving) Dialog(onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("저장 중이에요…")
            }
        }
    }
}

@Composable
fun PageColumn(topPadding: androidx.compose.ui.unit.Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 40.dp, top = topPadding, end = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
}

@Composable
fun Heading(eyebrow: String, title: String, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = Blue, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(title, fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        action()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(24.dp), tonalElevation = 0.dp,
        border = if (color == Color.White) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCE5ED)) else null) {
        Column(Modifier.padding(if (compact) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp), content = content)
    }
}

@Composable
fun HomeScreen(data: AppData, battery: BatteryReading, notifications: () -> Unit, mood: MascotMood = MascotMood.NORMAL,
               snapshot: VehicleSnapshot = VehicleSnapshot(), condition: CareCondition = CareCondition(),
               diagnostics: (() -> Unit)? = null, demo: Boolean = false) {
    val p = data.profile
    val latest = remember(data.records) { CareKind.entries.associateWith { data.records.latest(it) } }
    HomePage { compact ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VEMORING", fontSize = 40.sp, lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold, color = Blue, letterSpacing = 1.sp)
                Text(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy. MM. dd")),
                    color = Muted, fontSize = 22.sp)
            }
            if (diagnostics != null) TextButton(onClick = diagnostics, modifier = Modifier.heightIn(min = 64.dp)) { Text("데이터 연결") }
            OutlinedButton(onClick = notifications, modifier = Modifier.heightIn(min = 64.dp)) { Text("알림") }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)) {
                Panel(color = Color(0xFFE2F0FA), compact = compact) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CharacterStage(message = if (demo) "예시 데이터로 보는\n캐릭터예요." else
                        snapshot.priorityWarning()?.let { "차량 경고가 있어요!\n계기판을 확인해주세요." } ?: mood.message,
                        compact = compact, mood = mood)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(p.fullModel,
                        color = Muted, fontSize = 18.sp, lineHeight = 26.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Surface(color = Color.White, shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB7C9D8))) {
                        Text(if (p.plate.isNotBlank()) p.plate else "예시 · 12라 1234",
                            Modifier.padding(horizontal = 22.dp, vertical = 8.dp), color = Ink,
                            fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                    }
                    }
                    }
                }
                Text("차량 컨디션", Modifier.fillMaxWidth(), color = Ink, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!p.electric) Stat("남은 연료", snapshot[VehicleField.FUEL].realPercent(),
                        snapshot[VehicleField.FUEL].statusText(), Blue, Modifier.weight(1f), compact)
                    if (p.electric || p.hybrid) Stat("남은 배터리", battery.value, battery.status, Blue, Modifier.weight(1f), compact)
                    Stat("차량 청결도", condition.cleanliness, if (condition.cleanliness != null)
                        "관리 기록 기반 추정" else "세차 기록이 필요해요", Mint, Modifier.weight(1f), compact)
                }
                SplitContent(first = { modifier -> Panel(modifier, compact = compact) {
                    Text("최근 관리", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                    CareKind.entries.forEach { kind ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(kind.label, color = Muted, fontSize = 21.sp)
                            Text(if (kind == CareKind.OIL && condition.oilRemainingKm != null)
                                if (condition.oilRemainingKm <= 0) "교체 시기 · 설정 기준" else "약 ${condition.oilRemainingKm.toInt()} km 남음"
                                else latest[kind]?.toString() ?: "기록 없음", color = Ink, fontSize = 21.sp,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                } }, second = { modifier -> Box(modifier) { NearbyPlacesBanner(compact = compact) } })
            }
        }
    }
}

@Composable
fun Stat(title: String, value: Int?, detail: String, color: Color, modifier: Modifier, compact: Boolean = false) {
    Panel(modifier.heightIn(min = if (compact) 160.dp else 220.dp), compact = compact) {
        Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 21.sp)
        Text(value?.let { "$it %" } ?: "—", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (value != null) LinearProgressIndicator(progress = { (value ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = color)
        Text(detail, color = Muted, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
fun ProfileScreen(p: CarProfile, edit: () -> Unit, editName: () -> Unit, save: (CarProfile) -> Unit) {
    PageColumn {
        Heading("MY GARAGE", "내 차 설정")
        Text("내 차의 정보와 관리 기준을 한곳에서", color = Muted, fontSize = 22.sp)
        SplitContent(first = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Panel(color = Navy) {
                    Text("나의 드라이빙 파트너", color = Color(0xFFB5DBF1), fontSize = 20.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarCharacter(Modifier.size(180.dp, 130.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nickname, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Text(p.fullModel, color = Color.White, fontSize = 22.sp)
                        }
                    }
                    OutlinedButton(onClick = edit, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("차량 정보 편집") }
                }
                Panel {
                    Text("차량 정보", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                    SettingRow("닉네임", p.nickname, editName)
                    HorizontalDivider(color = Color(0xFFE5ECF2))
                    SettingRow("차종", p.fullModel, edit)
                    HorizontalDivider(color = Color(0xFFE5ECF2))
                    SettingRow("차량 정보 다시 등록", p.fuel, edit)
                }
            }
        }, second = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("관리 알림", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("예시 설정 · 실제 알림은 발송되지 않습니다.", color = Muted, fontSize = 19.sp, lineHeight = 28.sp)
                        }
                        Switch(checked = p.notifications, onCheckedChange = { save(p.copy(notifications = it)) })
                    }
                }
                Panel {
                    SectionLabel("나에게 맞게", "관리 기준 설정")
                    Text("선택한 수치 이하일 때 상태를 확인해요.", color = Muted, fontSize = 20.sp)
                    Threshold("세차 알림 기준", p.washAlert, 5f..50f, 8) { save(p.copy(washAlert = it)) }
                    HorizontalDivider(color = Color(0xFFE5ECF2))
                    Threshold("주유·충전 알림 기준", p.fuelAlert, 5f..40f, 6) { save(p.copy(fuelAlert = it)) }
                }
            }
        })
    }
}

@Composable
fun SettingRow(title: String, value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, color = Muted, fontSize = 21.sp, lineHeight = 30.sp) }
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
fun CalendarScreen(records: List<CareRecord>, save: (CareRecord, () -> Unit) -> Unit) {
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthValue)
    var selectedValue by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedValue?.let(LocalDate::parse)
    var all by rememberSaveable { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CareRecord?>(null) }
    var futureError by remember { mutableStateOf(false) }
    val recordsByDate = remember(records) { records.groupBy { it.date } }
    val visible = remember(records, all, selected) {
        if (all) records else if (selected != null) recordsByDate[selected].orEmpty() else records.take(3)
    }
    PageColumn {
        Heading("MY CAR LOG", "관리 기록") {
            Button(onClick = {
                if (selected != null && selected > LocalDate.now()) futureError = true
                else { editing = null; showEditor = true }
            }) { Text("＋ 추가") }
        }
        Text("차곡차곡 쌓이는 내 차의 관리 이야기", color = Muted, fontSize = 22.sp)
        SplitContent(first = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(76.dp))
                        else {
                            val date = month.atDay(day)
                            val matching = recordsByDate[date].orEmpty()
                            Column(Modifier.weight(1f).height(76.dp)
                                .background(if(selected == date) Color(0xFFD9EDFA) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { selectedValue = if(selected == date) null else date.toString(); all = false },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$day", color = when { date == LocalDate.now() -> Blue; date > LocalDate.now() -> Muted; else -> Ink },
                                    fontWeight = if(date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                if (matching.isNotEmpty()) Text(if(matching.size > 1) "${matching.size}건" else matching.first().kind.label,
                                    fontSize = 16.sp, color = matching.first().kind.color, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CareKind.entries.forEach { Text("● ${it.label}", color = it.color, fontSize = 18.sp) }
        }
        }
        }, second = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if(all) "전체 관리 기록" else selected?.let { "${it.monthValue}월 ${it.dayOfMonth}일 관리" } ?: "최근 관리",
                Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { all = !all; selectedValue = null }) { Text(if(all) "최근 보기" else "전체 보기") }
        }
        Panel {
            if (visible.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GarageGlyph(1, Modifier.size(52.dp), Blue)
                Text("아직 기록이 없어요", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("세차부터 정비까지, 첫 관리를 남겨보세요.", color = Muted, fontSize = 21.sp)
                Text("상단의 ＋ 추가로 기록할 수 있어요.", color = Muted, fontSize = 20.sp)
            }
            visible.forEach { record ->
                Column(Modifier.fillMaxWidth().clickable { editing = record; showEditor = true }.padding(vertical = 8.dp)) {
                    Text("${record.kind.label} · ${record.title}", fontWeight = FontWeight.Bold, color = record.kind.color)
                    Text(record.date.toString(), color = Muted, fontSize = 19.sp)
                    if (record.detail.isNotBlank()) Text(record.detail)
                }
            }
        }
        }
        })
    }
    if (showEditor) RecordDialog(editing, selected ?: LocalDate.now(), { showEditor = false }) {
        save(it) { monthValue = YearMonth.from(it.date).toString(); selectedValue = it.date.toString(); showEditor = false }
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
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { label -> FilterChip(selected == label, onClick = { change(label) },
            modifier = Modifier.heightIn(min = 64.dp),
            label = { Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }) }
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
