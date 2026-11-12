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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.vemoring.companion.WashConfirmationBanner
import com.vemoring.companion.rememberWashConfirmation

val Blue = Color(0xFF087BBF)
val Ink = Color(0xFF182F43)
val Navy = Color(0xFF173B54)
val Mint = Color(0xFF147D72)
val Orange = Color(0xFFB76120)
private val Background = Color(0xFFF0F4F8)
private val Muted = Color(0xFF526678)
// Parsed once instead of on every recomposition (vehicle signals recompose the home screen often).
private val SeoulZone = java.time.ZoneId.of("Asia/Seoul")
private val HomeDateFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy. MM. dd")
// Matches the mascot colors: fuel = yellow, maintenance (resolved warning) = purple. Darkened for text contrast on white.
val CareYellow = Color(0xFFA97C00)
val CarePurple = Color(0xFF7B4FC4)
val CareKind.color get() = when(this) { CareKind.WASH -> Blue; CareKind.FUEL -> CareYellow; CareKind.MAINTENANCE -> CarePurple }

/** One colored dot per record (max 4) so a "2건" day shows which kinds they are. */
@Composable
fun CareDots(colors: List<Color>, size: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(size / 2)) {
        colors.take(4).forEach { Box(Modifier.size(size).background(it, androidx.compose.foundation.shape.CircleShape)) }
    }
}

class MainActivity : ComponentActivity() {
    private val vehicleModel: VehicleViewModel by viewModels()
    private val integrationModel: IntegrationViewModel by viewModels()
    override fun onStart() { super.onStart(); IconMonitorVisibility.visible.value = true; vehicleModel.resume(); integrationModel.resume() }
    override fun onStop() {
        vehicleModel.pause(); integrationModel.pause(); super.onStop()
        IconMonitorVisibility.visible.value = false
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
                    integrationModel = integrationModel)
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
    var fleet by remember {mutableStateOf<GarageFleet?>(null)}
    var fleetPicker by remember {mutableStateOf(false)}
    var addingVehicle by remember {mutableStateOf(false)}
    var bindingVehicle by remember {mutableStateOf(false)}
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(homeLaunchRequest) { if (homeLaunchRequest > 0) tab = 0 }
    var setupRequested by rememberSaveable { mutableStateOf(startWithSetup) }
    var dockGuideAfterSetup by rememberSaveable { mutableStateOf(false) }
    var showDockGuide by rememberSaveable { mutableStateOf(false) }
    var editCar by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var proposedWash by remember { mutableStateOf<CareRecord?>(null) }
    var proposedWashSaved by remember { mutableStateOf<(() -> Unit)?>(null) }
    val liveSnapshot = vehicleModel?.state?.collectAsState()?.value ?: VehicleSnapshot()
    val receivesSelected=fleet?.receivingId==data.profile.vehicleId
    LaunchedEffect(data.profile.vehicleId,vehicleModel) {if(vehicleModel!=null) IconMonitorVisibility.viewedVehicleId.value=data.profile.vehicleId}
    val snapshot=if(receivesSelected) liveSnapshot else VehicleSnapshot()
    val identity = vehicleModel?.identity?.collectAsState()?.value ?: VehicleIdentityReading()
    val mode = integrationModel?.mode?.collectAsState()?.value ?: DataMode.Real
    val environment = integrationModel?.environment?.collectAsState()?.value ?: com.example.vemoring.integration.Result<EnvironmentData>()
    val maintenance by rememberMaintenanceConfig(data.profile)
    val condition = remember(data, snapshot, maintenance, environment) { careCondition(data, snapshot, maintenance, environment) }
    val mood = (careSignals ?: condition.signals).mood()
    // Same list instance across vehicle-signal recompositions, so the calendar tab can skip recomposing.
    val activeRecords = remember(data.records) { data.records.filterNot { it.cancelled } }
    PublishVehicleMirror(data, loaded && receivesSelected && vehicleModel!=null, snapshot, condition, mood, mode)
    val hasResolvedVehicleData = snapshot.readings.values.any { it.state != ReadingState.Loading && it.state != ReadingState.Disconnected }
    LaunchedEffect(mood, mode, loaded, hasResolvedVehicleData) {
        if (loaded && mode == DataMode.Real && vehicleModel != null && hasResolvedVehicleData) onLauncherMood(mood)
    }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(store, retry) {
        runCatching { withContext(Dispatchers.IO) { store.fleet() } }.onSuccess { fleet=it;data = it.selected(); loaded = true; loadError = null }
            .onFailure { loadError = "저장 데이터를 불러오지 못했어요. 기존 데이터는 보존됩니다." }
    }
    fun save(updated: AppData, fromPhone: Boolean = false, onSaved: () -> Unit = {}) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val nextFleet=withContext(Dispatchers.IO) {
                    if(fromPhone) com.vemoring.companion.CareJournal(context,updated.profile.vehicleId).authority()?.let {store.syncFromPhone(it)}
                    else store.save(updated,queueForPhone=true)
                    store.fleet()
                }
                fleet=nextFleet;data = nextFleet.selected()
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
    val phoneAuthority by com.vemoring.companion.CareExchange.canonical.collectAsState()
    val washConfirmation by rememberWashConfirmation(if(loaded && data.complete) vehicleCareId(data.profile) else null, phoneAuthority)
    LaunchedEffect(loaded, data.profile.vehicleId) {
        if (loaded && data.complete && storeOverride==null) com.vemoring.companion.CareExchange.loadVehicle(context, vehicleCareId(data.profile))
    }
    LaunchedEffect(phoneAuthority, loaded, saving, maintenance.phoneSyncPending) {
        val authority = phoneAuthority
        if (loaded && !saving && !maintenance.phoneSyncPending && authority != null && authority.vehicleId == vehicleCareId(data.profile)) {
            val next = applyPhoneAuthority(data,authority)
            if (next != data) save(next, fromPhone = true)
        }
    }
    LaunchedEffect(data.complete, vehicleModel) { vehicleModel?.setEnabled(data.complete) }
    LaunchedEffect(loaded, data.complete, mode) {
        if (vehicleModel != null && loaded) {
            val enabled = withContext(Dispatchers.IO) {
                context.getSharedPreferences(VehicleIconMonitor.PREFS, android.content.Context.MODE_PRIVATE)
                    .getBoolean(VehicleIconMonitor.RECEIVE_ENABLED, true)
            }
            if (data.complete && mode == DataMode.Real && enabled) VehicleIconMonitor.start(context)
            else context.stopService(android.content.Intent(context, VehicleIconMonitor::class.java))
        }
    }
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
                !data.complete || setupRequested -> SetupScreen(detected = identity,
                    onDockChoice = { dockGuideAfterSetup = it }) { profile, dates ->
                    save(data.copy(profile = profile, records = updateLatest(data.records, dates), complete = true)) {
                        setupRequested = false
                        tab = 0
                        showDockGuide = dockGuideAfterSetup
                        dockGuideAfterSetup = false
                    }
                }
                else -> Scaffold(containerColor = Background, bottomBar = {
                    GarageNavigation(tab) { tab = it }
                }) { padding ->
                    Column(Modifier.padding(padding)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            TextButton(enabled=!saving,onClick={fleetPicker=true},modifier=Modifier.heightIn(min=64.dp).testTag("vehicle-picker")) {
                                Text("${data.profile.nickname} · 차량 선택 (${fleet?.vehicles?.count {it.complete} ?: 0}대)",fontSize=20.sp)
                            }
                            Text(if(receivesSelected) "현재 차량 정보 수신 대상" else "기록 조회 · 수신 대상: ${fleet?.receiving()?.profile?.nickname.orEmpty()}",
                                modifier=Modifier.weight(1f),fontSize=17.sp)
                            if(!receivesSelected) TextButton(enabled=!saving,onClick={bindingVehicle=true},modifier=Modifier.heightIn(min=64.dp)) {Text("이 차량에 연결",fontSize=19.sp)}
                        }
                        Box(Modifier.weight(1f)) { key(data.profile.vehicleId) {
                        when(tab) {
                            0 -> HomeScreen(data, BatteryReading(snapshot[VehicleField.SOC].realPercent(),
                                snapshot[VehicleField.SOC].statusText()), { notifications = true }, mood,
                                snapshot, condition, if (integrationModel != null) ({ diagnostics = true }) else null,
                                mode == DataMode.Demo, confirmationBanner = {
                                    washConfirmation?.takeIf { candidate -> mode == DataMode.Real &&
                                        data.records.any { it.visitId == candidate.visitId && !it.cancelled } }?.let { candidate ->
                                        WashConfirmationBanner(candidate, saving, vehicle = true, onReject = {
                                            save(data.copy(records = data.records.map {
                                                if(it.visitId == candidate.visitId) it.copy(cancelled = true) else it
                                            }))
                                        }, onDismiss = { scope.launch {
                                            runCatching { com.vemoring.companion.CareExchange.dismissConfirmation(context, candidate.vehicleId, candidate.visitId) }
                                                .onFailure { error = "배너를 닫지 못했어요. 다시 시도해 주세요." }
                                        } })
                                    }
                                })
                            1 -> CalendarScreen(activeRecords,remove={record,onSaved -> save(data.copy(records=deleteRecord(data.records,record.id)),onSaved=onSaved)}) { record, onSaved ->
                                if (record.kind == CareKind.WASH && data.records.none { it.id == record.id } &&
                                    data.records.any { it.kind == CareKind.WASH && it.visitId != null && !it.cancelled && it.date == record.date }) {
                                    proposedWash = record; proposedWashSaved = onSaved
                                } else save(data.copy(records = (data.records.filter { it.id != record.id } + record).sortedByDescending { it.date }), onSaved = onSaved)
                            }
                            else -> ProfileScreen(data.profile, { editCar = true }, { editName = true }) {
                                save(data.copy(profile = it))
                            }
                        }
                        } }
                    }
                }
            }
        }
    }
    if (showDockGuide) GnbPinGuide { showDockGuide = false }
    proposedWash?.let { proposed ->
        AlertDialog(onDismissRequest = { proposedWash = null; proposedWashSaved = null },
            title = { Text("같은 날 자동 세차 기록이 있어요") }, text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("기존 기록을 보완하거나 별도 세차로 남겨 주세요.")
                    data.records.filter { it.kind == CareKind.WASH && it.visitId != null && !it.cancelled && it.date == proposed.date }.forEach { old ->
                        OutlinedButton(enabled = !saving, onClick = {
                            val merged = old.copy(title = proposed.title, detail = proposed.detail)
                            save(data.copy(records = data.records.map { if(it.id == old.id) merged else it })) {
                                proposedWashSaved?.invoke(); proposedWash = null; proposedWashSaved = null
                            }
                        }, modifier = Modifier.heightIn(min = 64.dp)) { Text("기존 기록 보완: ${old.title}") }
                    }
                }
            }, confirmButton = { TextButton(enabled = !saving, onClick = {
                save(data.copy(records = (data.records + proposed).sortedByDescending { it.date })) {
                    proposedWashSaved?.invoke(); proposedWash = null; proposedWashSaved = null
                }
            }) { Text("별도 세차 추가") } }, dismissButton = { TextButton(onClick = { proposedWash = null; proposedWashSaved = null }) { Text("돌아가기") } })
    }
    if (editCar) VehicleDialog(data.profile, data.records, { editCar = false }) { p, dates ->
        save(data.copy(profile = p, records = updateLatest(data.records, dates))) { editCar = false }
    }
    if(addingVehicle) {
        val fresh=remember {CarProfile()}
        VehicleDialog(fresh,emptyList(),{addingVehicle=false},register=true) {p,dates ->
            save(AppData(p,updateLatest(emptyList(),dates),true)) {addingVehicle=false;tab=0}
        }
    }
    if(fleetPicker) AlertDialog(onDismissRequest={fleetPicker=false},title={Text("등록 차량")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("다른 차량의 기록을 봐도 현재 수신 대상은 바뀌지 않아요.",fontSize=20.sp)
            fleet?.vehicles?.filter {it.complete}?.forEach {car ->
                OutlinedButton(enabled=!saving,onClick={
                    saving=true
                    scope.launch {
                        runCatching {withContext(Dispatchers.IO) {store.select(car.profile.vehicleId) to store.fleet()}}
                            .onSuccess {(selected,all) -> data=selected;fleet=all;fleetPicker=false;tab=0}
                            .onFailure {error="차량을 선택하지 못했어요."}
                        saving=false
                    }
                },modifier=Modifier.fillMaxWidth().heightIn(min=72.dp).testTag("vehicle:${car.profile.vehicleId}")) {
                    Text("${car.profile.nickname} · ${car.profile.fullModel}\n${car.profile.plate.ifBlank {"번호판 미등록"}}${if(car.profile.vehicleId==fleet?.receivingId) " · 수신 대상" else ""}",fontSize=21.sp)
                }
            }
        }
    },confirmButton={TextButton(enabled=!saving,onClick={fleetPicker=false;addingVehicle=true}) {Text("차량 추가 등록",fontSize=22.sp)}},
        dismissButton={TextButton(onClick={fleetPicker=false}) {Text("닫기",fontSize=22.sp)}})
    if(bindingVehicle) AlertDialog(onDismissRequest={if(!saving) bindingVehicle=false},title={Text("차량 정보 수신 대상을 바꿀까요?")},
        text={Text("이후 이 기기에서 받는 차량 정보를 ${data.profile.nickname}에 저장해요. 실제 탑승한 차량과 일치하는지 확인해 주세요. 다른 차량의 기록은 유지돼요.",fontSize=22.sp)},
        confirmButton={TextButton(enabled=!saving,onClick={
            saving=true
            scope.launch {
                runCatching {withContext(Dispatchers.IO) {store.bindReceiver(data.profile.vehicleId);VehicleCareRuntime.resetSession();store.fleet()}}
                    .onSuccess {fleet=it;bindingVehicle=false}.onFailure {error="수신 대상을 바꾸지 못했어요."}
                saving=false
            }
        }) {Text("수신 대상 변경",fontSize=22.sp)}},dismissButton={TextButton(enabled=!saving,onClick={bindingVehicle=false}) {Text("취소",fontSize=22.sp)}})
    if (editName) TextEditDialog("닉네임 변경", data.profile.nickname, { editName = false }) {
        save(data.copy(profile = data.profile.copy(nickname = it))) { editName = false }
    }
    if (notifications) AlertDialog(onDismissRequest = { notifications = false }, title = { Text("알림 내역") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("관리 알림은 연결된 휴대폰에서 확인해요.", color = Muted)
            Text("세차 알림 기준: 청결도 ${data.profile.washAlert}% 이하 · 주기마다 한 번")
            Text("차량 정비 경고는 실제 수신과 차종별 규칙 확인이 필요해요. 확인되지 않은 경고는 자동 알림을 보류해요.")
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
               diagnostics: (() -> Unit)? = null, demo: Boolean = false, confirmationBanner: (@Composable () -> Unit)? = null) {
    val p = data.profile
    val latest = remember(data.records) { CareKind.entries.associateWith { data.records.latest(it) } }
    HomePage { compact ->
        confirmationBanner?.invoke()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VEMORING", fontSize = 32.sp, lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold, color = Blue, letterSpacing = 1.sp)
                Text(LocalDate.now(SeoulZone).format(HomeDateFormat),
                    color = Muted, fontSize = 18.sp)
            }
            if (diagnostics != null) TextButton(onClick = diagnostics, modifier = Modifier.heightIn(min = 64.dp)) { Text("데이터 연결") }
            OutlinedButton(onClick = notifications, modifier = Modifier.heightIn(min = 64.dp)) { Text("알림") }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1120.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 16.dp)) {
                Panel(color = Color(0xFFE2F0FA), compact = compact) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CharacterStage(message = if (demo) "예시 데이터로 보는\n캐릭터예요." else
                        snapshot.priorityWarning()?.let { "차량 경고가 있어요!\n계기판을 확인해주세요." } ?: mood.message,
                        compact = compact, mood = mood)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(p.fullModel,
                        modifier = Modifier.weight(1f),
                        color = Muted, fontSize = 18.sp, lineHeight = 26.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Text(p.nickname, Modifier.weight(1f), color=Ink, fontSize=24.sp, lineHeight=32.sp,
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center, fontWeight=FontWeight.SemiBold)
                    Box(Modifier.weight(1f), contentAlignment=Alignment.CenterEnd) {
                    Surface(color = Color.White, shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB7C9D8))) {
                        Text(if (p.plate.isNotBlank()) p.plate else "번호판 미등록",
                            Modifier.padding(horizontal = 22.dp, vertical = 8.dp), color = Ink,
                            fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                    }
                    }
                    }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!p.electric) Stat("남은 연료", snapshot[VehicleField.FUEL].realPercent(),
                        snapshot[VehicleField.FUEL].statusText(), Blue, Modifier.weight(1f), compact)
                    if (p.electric || p.hybrid) Stat("남은 배터리", battery.value, battery.status, Blue, Modifier.weight(1f), compact)
                    Stat("차량 청결도", condition.cleanliness, if (condition.cleanliness != null)
                        condition.syncStatus else "세차 기록이 필요해요", Mint, Modifier.weight(1f), compact)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    CareKind.entries.forEach { kind ->
                        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Text(kind.label,color=Muted,fontSize=18.sp)
                            Text(latest[kind]?.toString() ?: "기록 없음",color=Ink,fontSize=18.sp)
                        }
                    }
                }
                VehicleHomeWeather(p.vehicleId,demo)
                NearbyPlacesBanner(compact = compact, slim = true)
            }
        }
    }
}

@Composable
private fun VehicleHomeWeather(vehicleId: String,demo: Boolean) {
    val context=LocalContext.current
    val owner=remember {Any()}
    DisposableEffect(owner) {onDispose {SharedVehicleConnection.release(owner)}}
    com.vemoring.companion.ForecastWeatherPanel(vehicleId=vehicleId,vehicle=true,enabled=!demo,locate={
        SharedVehicleConnection.acquire(context,owner)
        try {
            kotlinx.coroutines.withTimeoutOrNull(20_000) {
                SharedVehicleConnection.travelPoint.first {it?.fresh(java.time.Instant.now())==true}
            }
        } finally {SharedVehicleConnection.release(owner)}
    })
}

@Composable
fun Stat(title: String, value: Int?, detail: String, color: Color, modifier: Modifier, compact: Boolean = false) {
    Surface(modifier.fillMaxWidth(),color=Color.White,shape=RoundedCornerShape(20.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFFDCE5ED))) {
        Column(Modifier.padding(if(compact) 12.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(title,fontWeight=FontWeight.Bold,color=color,fontSize=if(compact) 18.sp else 21.sp,lineHeight=26.sp)
                Text(value?.let { "$it %" } ?: "—",fontSize=if(compact) 30.sp else 40.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=Ink)
            }
            if(value != null) LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth(),color=color)
            Text(detail,color=Muted,fontSize=if(compact) 16.sp else 18.sp,lineHeight=22.sp)
        }
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
                    IconMonitorSetting()
                }
                Panel {
                    PhoneConnectionCard()
                }
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
fun CalendarScreen(records: List<CareRecord>, remove: ((CareRecord, () -> Unit) -> Unit)? = null,save: (CareRecord, () -> Unit) -> Unit) {
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
                if (selected != null && selected > LocalDate.now(SeoulZone)) futureError = true
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
            val today = LocalDate.now(SeoulZone)
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
                                Text("$day", color = when { date == today -> Blue; date > today -> Muted; else -> Ink },
                                    fontWeight = if(date == today) FontWeight.Bold else FontWeight.Normal)
                                if (matching.size == 1) Text(matching.first().kind.label,
                                    fontSize = 16.sp, lineHeight = 20.sp, color = matching.first().kind.color, maxLines = 1)
                                else if (matching.size > 1) {
                                    Text("${matching.size}건", fontSize = 16.sp, lineHeight = 20.sp, color = Ink, maxLines = 1)
                                    CareDots(matching.sortedBy { it.kind.ordinal }.map { it.kind.color }, 8.dp)
                                }
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
                    Text("${if(record.kind == CareKind.MAINTENANCE) record.maintenanceType.label else record.kind.label} · ${record.title}", fontWeight = FontWeight.Bold, color = record.kind.color)
                    Text(record.date.toString(), color = Muted, fontSize = 19.sp)
                    if (record.detail.isNotBlank()) Text(record.detail)
                }
            }
        }
        }
        })
    }
    if (showEditor) RecordDialog(editing, selected ?: LocalDate.now(SeoulZone), { showEditor = false },remove=editing?.takeIf {remove!=null}?.let {record ->
        { remove?.invoke(record) {showEditor=false} }
    }) {
        save(it) { monthValue = YearMonth.from(it.date).toString(); selectedValue = it.date.toString(); showEditor = false }
    }
    if (futureError) AlertDialog(onDismissRequest = { futureError = false }, text = { Text("미래 날짜에는 관리 기록을 등록할 수 없어요.") },
        confirmButton = { TextButton(onClick = { futureError = false }) { Text("확인") } })
}

@Composable
fun DateInput(label: String, date: LocalDate?, update: (LocalDate?) -> Unit, allowUnknown: Boolean = true) {
    val context = LocalContext.current
    fun chooseDate() {
        val start = date ?: LocalDate.now(SeoulZone)
        DatePickerDialog(context, { _, y, m, d -> update(LocalDate.of(y,m+1,d)) }, start.year, start.monthValue-1, start.dayOfMonth)
            .apply { datePicker.maxDate = System.currentTimeMillis(); datePicker.minDate = java.util.GregorianCalendar(1980,0,1).timeInMillis }.show()
    }
    Column {
        OutlinedButton(onClick = {chooseDate()}, modifier = Modifier.fillMaxWidth()) { Text("$label: ${date ?: "날짜 모름"}") }
        if(allowUnknown) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = date == null, onCheckedChange = { if(it) update(null) else chooseDate() })
            Text("날짜 모름 (기존 기록 유지)", fontSize = 16.sp)
        }
    }
}

@Composable
fun RecordDialog(existing: CareRecord?, initial: LocalDate, close: () -> Unit,remove: (() -> Unit)? = null,save: (CareRecord) -> Unit) {
    var deleteConfirmation by remember {mutableStateOf(false)}
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var detail by rememberSaveable { mutableStateOf(existing?.detail ?: "") }
    var maintenanceType by rememberSaveable { mutableStateOf((existing?.maintenanceType ?: com.vemoring.companion.MaintenanceType.GENERAL).name) }
    var kindValue by rememberSaveable { mutableStateOf(existing?.kind?.name ?: CareKind.WASH.name) }
    var dateValue by rememberSaveable { mutableStateOf((existing?.date ?: initial).toString()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var washTime by rememberSaveable { mutableStateOf(com.vemoring.companion.manualWashTime(existing?.completedAt, existing?.source)) }
    val automatic = existing?.source != null && existing.source != "Manual" && existing.completedAt != null
    val kind = CareKind.valueOf(kindValue)
    AlertDialog(onDismissRequest = close, title = { Text(if(existing == null) "관리 기록 추가" else "관리 기록 수정") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!automatic || manual) ChoiceRow(CareKind.entries.map { it.label }, kind.label) { label -> kindValue = CareKind.entries.first { it.label == label }.name }
            if(kind == CareKind.MAINTENANCE) {
                Text("정비 항목", fontSize = 22.sp)
                ChoiceRow(com.vemoring.companion.MaintenanceType.entries.map { it.label },
                    com.vemoring.companion.MaintenanceType.from(maintenanceType).label) { label ->
                    maintenanceType = com.vemoring.companion.MaintenanceType.entries.first { it.label == label }.name
                }
            }
            OutlinedTextField(title, { title = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, label = { Text("기록 이름") }, singleLine = true)
            OutlinedTextField(detail, { detail = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, label = { Text("상세 내용 (장소·금액 등)") })
            if (!automatic || manual) DateInput("관리 날짜", LocalDate.parse(dateValue), { if(it != null) dateValue = it.toString() }, false)
            if (automatic) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(manual, { manual = it; washTime = "" }); Text("수동 기록으로 전환")
            }
            if(kind == CareKind.WASH && (!automatic || manual)) com.vemoring.companion.WashCompletionInput(LocalDate.parse(dateValue),washTime,true) {washTime=it}
            if(remove!=null) TextButton(onClick={deleteConfirmation=true},modifier=Modifier.heightIn(min=64.dp)) {Text("기록 삭제",fontSize=22.sp)}
        } }, confirmButton = { TextButton(enabled = title.isNotBlank() && LocalDate.parse(dateValue) <= LocalDate.now(SeoulZone) && (kind != CareKind.WASH || automatic && !manual || washTime.isBlank() || com.vemoring.companion.washCompletion(LocalDate.parse(dateValue),washTime)!=null), onClick = {
            save(existing?.copy(date = LocalDate.parse(dateValue), kind = kind, maintenanceType = if(kind == CareKind.MAINTENANCE) com.vemoring.companion.MaintenanceType.from(maintenanceType) else com.vemoring.companion.MaintenanceType.GENERAL, title = title.trim(), detail = detail.trim(),
                completedAt = if(kind == CareKind.WASH && (!automatic || manual)) com.vemoring.companion.washCompletion(LocalDate.parse(dateValue),washTime) else if(manual) null else existing.completedAt, source = if(manual) "Manual" else existing.source, washEvidence = if(manual) "UserRecord" else existing.washEvidence)
                ?: CareRecord(completedAt = if(kind == CareKind.WASH) com.vemoring.companion.washCompletion(LocalDate.parse(dateValue),washTime) else null, washEvidence = "UserRecord", date = LocalDate.parse(dateValue), kind = kind, maintenanceType = if(kind == CareKind.MAINTENANCE) com.vemoring.companion.MaintenanceType.from(maintenanceType) else com.vemoring.companion.MaintenanceType.GENERAL, title = title.trim(), detail = detail.trim()))
        }) { Text("기록 저장") } }, dismissButton = { TextButton(onClick = close) { Text("취소") } })
    if(deleteConfirmation) AlertDialog(onDismissRequest={deleteConfirmation=false},title={Text("기록을 삭제할까요?")},
        text={Text("${existing?.title.orEmpty()} 기록을 삭제해요. 세차 기록이면 이전 유효한 기록으로 다시 계산해요. 날짜를 모르는 경우에는 삭제하지 않아도 돼요.",fontSize=22.sp)},
        confirmButton={TextButton(onClick={deleteConfirmation=false;remove?.invoke()}) {Text("삭제",fontSize=22.sp)}},
        dismissButton={TextButton(onClick={deleteConfirmation=false}) {Text("돌아가기",fontSize=22.sp)}})
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
        OutlinedTextField(value, { value = it }, keyboardOptions = com.vemoring.companion.KoreanTextKeyboardOptions, singleLine = true, label = { Text("닉네임") })
    }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { save(value.trim()) }) { Text("저장하기") } },
        dismissButton = { TextButton(onClick = close) { Text("취소") } })
}
