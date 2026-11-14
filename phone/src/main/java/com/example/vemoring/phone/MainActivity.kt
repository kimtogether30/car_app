package com.example.vemoring.phone

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.vemoring.companion.*

private val Blue = Color(0xFF087BBF)
private val LightColors = lightColorScheme(
    primary = Blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE2F0FA), onPrimaryContainer = Color(0xFF173B54),
    background = Color(0xFFF0F4F8), onBackground = Color(0xFF182F43),
    surface = Color.White, onSurface = Color(0xFF182F43),
    surfaceVariant = Color(0xFFE6EEF5), onSurfaceVariant = Color(0xFF526678),
    secondary = Color(0xFF147D72), outlineVariant = Color(0xFFDCE5ED)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ACFFF), onPrimary = Color(0xFF003450),
    primaryContainer = Color(0xFF163A50), onPrimaryContainer = Color(0xFFD5EDFF),
    background = Color(0xFF101C26), onBackground = Color(0xFFE1EDF5),
    surface = Color(0xFF1B2A36), onSurface = Color(0xFFE1EDF5),
    surfaceVariant = Color(0xFF2A3B49), onSurfaceVariant = Color(0xFFB6C9D8),
    secondary = Color(0xFF89D7C7), outlineVariant = Color(0xFF435867)
)
private val kinds = listOf("세차", "주유", MAINTENANCE)
// Parsed once instead of on every recomposition.
private val SeoulZone = java.time.ZoneId.of("Asia/Seoul")
private val HomeDateFormat = DateTimeFormatter.ofPattern("yyyy. MM. dd")
private val ReceivedTimeFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
private data class MirrorDisplay(val fuel: Int?, val battery: Int?, val cleanliness: Int?, val oilRemainingKm: Double?,
    val fuelStatus: String, val batteryStatus: String, val mood: String, val message: String, val demo: Boolean)
private data class PhoneData(val nickname: String = "나의 베모링", val model: String = "",
    val plate: String = "", val entries: List<Entry> = emptyList(),
    val fuel: String = "휘발유", val notifications: Boolean = true, val washAlert: Int = 30, val fuelAlert: Int = 20,
    val mirror: ReceivedMirror? = null, val mirrorView: MirrorDisplay? = null,
    val vehicleId: String = "",val careScore: Int? = null,val hasCareScore: Boolean = false,val oilAssessment:EngineOilAssessment?=null,val oilEstimate:String="",val oilBaselineKm:Double?=null)


private fun phoneVehicleData(context: android.content.Context,state: CareAuthority,received: ReceivedMirror?): PhoneData {
    val p=JSONObject(state.profile);val a=JSONArray(state.records)
    val score=CareJournal(context,state.vehicleId).score(a).displayed
    val entries=(0 until a.length()).map {n -> val r=a.getJSONObject(n)
        Entry(r.getString("id"),when(r.getString("kind")) {"wash"->"세차";"fuel"->"주유";else->MAINTENANCE},
            r.getString("title"),LocalDate.parse(r.getString("date")),r.optString("detail"),
            r.optString("completedAt").takeUnless {it.isBlank() || it=="null"},r.optString("visitId").takeUnless {it.isBlank() || it=="null"},
            r.optBoolean("cancelled"),r.optString("source","Manual"),r.optString("appliedAt").takeUnless {it.isBlank() || it=="null"},
            r.optString("washEvidence","EstimatedVisit"),r.optBoolean("deleted"),com.vemoring.companion.MaintenanceType.from(r.optString("maintenanceType")),r.optDouble("odometerKm",Double.NaN).takeIf {km -> km.isFinite() && km>=0})
    }.sortedByDescending {it.date}
    val oilAssessment=EngineOilCatalog.assess(context,JSONObject(p.toString()).put("vehicleId",state.vehicleId),state.records)
    val content=received?.let {JSONObject(it.snapshot.content)}
    val view=content?.let {
        val signals=it.optJSONObject("signals")
        val mood=if(it.optBoolean("demo")) it.optString("mood","NORMAL") else moodFor(EnergyPolicy.low(p, it)==true,
            score?.let {s -> s<=p.getInt("washAlert")}==true,oilAssessment.due==true)
        MirrorDisplay(it.takeUnless {j -> j.isNull("fuel")}?.getInt("fuel"),it.takeUnless {j -> j.isNull("battery")}?.getInt("battery"),score,
            it.takeUnless {j -> j.isNull("oilRemainingKm")}?.getDouble("oilRemainingKm"),it.optString("fuelStatus","미수신"),it.optString("batteryStatus","미수신"),
            mood,if(it.optBoolean("demo")) it.optString("message") else moodMessage(mood),it.optBoolean("demo"))
    }
    return PhoneData(p.optString("nickname"),p.optString("model"),p.optString("plate"),entries,p.getString("fuel"),
        p.getBoolean("notifications"),p.getInt("washAlert"),p.getInt("fuelAlert"),received,view,state.vehicleId,score,true,oilAssessment,p.optString("oilEstimate"),p.optDouble("oilBaselineKm",Double.NaN).takeIf {it.isFinite() && it>=0})
}

@Composable private fun CarePermissionButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(CareNotifications.allowed(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = CareNotifications.allowed(context)
        scope.launch { CareExchange.refreshAll(context) }
    }
    Text(if(enabled) "시스템 알림이 허용되어 있어요." else "시스템 알림이 꺼져 있어요. 상태와 기록은 앱에서 확인할 수 있어요.")
    if (!enabled) OutlinedButton(onClick = {
        if (android.os.Build.VERSION.SDK_INT >= 33) request.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("관리 알림 허용") }
}

private fun ReceivedMirror.phoneData(): PhoneData {
    val content = JSONObject(snapshot.content)
    val p = content.getJSONObject("profile")
    val records = content.getJSONArray("records")
    return PhoneData(p.getString("nickname"), p.getString("model"), p.getString("plate"),
        (0 until records.length()).map { i -> records.getJSONObject(i).let { r ->
            Entry(r.getString("id"), when (r.getString("kind")) { "wash" -> "세차"; "fuel" -> "주유"; else -> MAINTENANCE },
                r.getString("title"), LocalDate.parse(r.getString("date")), r.getString("detail"),
                r.optString("completedAt").takeUnless { it.isBlank() || it == "null" },
                r.optString("visitId").takeUnless { it.isBlank() || it == "null" }, r.optBoolean("cancelled"), r.optString("source", "Manual"),
                r.optString("appliedAt").takeUnless { it.isBlank() || it == "null" },r.optString("washEvidence","EstimatedVisit"),r.optBoolean("deleted"),com.vemoring.companion.MaintenanceType.from(r.optString("maintenanceType")),r.optDouble("odometerKm",Double.NaN).takeIf {km -> km.isFinite() && km>=0})
        } }.sortedByDescending { it.date }, p.getString("fuel"), p.getBoolean("notifications"), p.getInt("washAlert"), p.getInt("fuelAlert"), this,
        MirrorDisplay(content.takeUnless { it.isNull("fuel") }?.getInt("fuel"), content.takeUnless { it.isNull("battery") }?.getInt("battery"),
            content.takeUnless { it.isNull("cleanliness") }?.getInt("cleanliness"), content.takeUnless { it.isNull("oilRemainingKm") }?.getDouble("oilRemainingKm"),
            content.optString("fuelStatus", "미수신"), content.optString("batteryStatus", "미수신"), content.getString("mood"), content.getString("message"), content.getBoolean("demo")))
}

private fun ReceivedMirror.timeLabel(): String = java.time.Instant.ofEpochMilli(receivedAt)
    .atZone(java.time.ZoneId.systemDefault()).format(ReceivedTimeFormat)

/** Phone-only local prototype store. Never reads or overwrites the vehicle app's data. */
private class PhoneStore(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences("phone_ui_v1", android.content.Context.MODE_PRIVATE) }
    fun migrateLegacy() {
        if(!prefs.getBoolean("fleetMigrated",false) && prefs.contains("data")) {
            if(CareJournal(appContext,"phone-local").authority()==null) load()
            check(prefs.edit().putBoolean("fleetMigrated",true).commit())
        }
    }
    fun selectedId(): String? = appContext.getSharedPreferences("phone_vehicle_selection",android.content.Context.MODE_PRIVATE).getString("selectedId",null)
    fun select(id: String) {check(appContext.getSharedPreferences("phone_vehicle_selection",android.content.Context.MODE_PRIVATE).edit().putString("selectedId",id).commit())}
    fun load(): PhoneData {
        val raw = prefs.getString("data", null) ?: return PhoneData()
        val j = JSONObject(raw)
        val a = j.getJSONArray("entries")
        return PhoneData(j.getString("nickname"), j.getString("model"), j.getString("plate"),
            (0 until a.length()).map { i -> a.getJSONObject(i).let {
                Entry(it.getString("id"), normalizeKind(it.getString("kind")), it.getString("title"),
                    LocalDate.parse(it.getString("date")), it.getString("detail"),
                    it.optString("completedAt").takeUnless { v -> v.isBlank() || v == "null" },
                    it.optString("visitId").takeUnless { v -> v.isBlank() || v == "null" }, it.optBoolean("cancelled"), it.optString("source", "Manual"),
                    it.optString("appliedAt").takeUnless { v -> v.isBlank() || v == "null" },it.optString("washEvidence","EstimatedVisit"),it.optBoolean("deleted"),com.vemoring.companion.MaintenanceType.from(it.optString("maintenanceType")),it.optDouble("odometerKm",Double.NaN).takeIf {km -> km.isFinite() && km>=0})
            } }, j.optString("fuel", "휘발유"), j.optBoolean("notifications", true),
            j.optInt("washAlert", 30), j.optInt("fuelAlert", 20)).also { refreshLocal(it,false) }
    }
    @android.annotation.SuppressLint("UseKtx") // Need commit's boolean result to report failed writes.
    fun save(data: PhoneData) {
        val a = JSONArray()
        data.entries.forEach { e -> a.put(JSONObject().put("id", e.id).put("kind", e.kind).put("maintenanceType",e.maintenanceType.name)
            .put("odometerKm",e.odometerKm).put("title", e.title).put("date", e.date).put("detail", e.detail).put("completedAt", e.completedAt).put("deleted",e.deleted)
            .put("visitId", e.visitId).put("cancelled", e.cancelled).put("source", e.source).put("appliedAt", e.appliedAt).put("washEvidence",e.washEvidence)) }
        val j = JSONObject().put("nickname", data.nickname).put("model", data.model)
            .put("plate", data.plate).put("entries", a).put("fuel", data.fuel)
            .put("notifications", data.notifications).put("washAlert", data.washAlert).put("fuelAlert", data.fuelAlert)
        check(prefs.edit().putString("data", j.toString()).commit())
        refreshLocal(data,true)
    }
    private fun refreshLocal(data: PhoneData, notifyChanges: Boolean) {
        val journal = CareJournal(appContext, "phone-local")
        val old = journal.authority()
        val profile = JSONObject().put("nickname", data.nickname).put("model", data.model).put("plate", data.plate)
            .put("fuel", data.fuel).put("washAlert", data.washAlert).put("fuelAlert", data.fuelAlert).put("notifications", data.notifications)
        val records = JSONArray().also { a -> data.entries.forEach { a.put(it.wireJson()) } }
        val state = if (old?.profile == profile.toString() && old.records == records.toString()) old else
            CareAuthority("phone-local", (old?.version ?: 0) + 1, profile.toString(), records.toString()).also(journal::saveAuthority)
        if(notifyChanges) BannerNotifications.recordChanges(appContext,old ?: CareAuthority("phone-local",0,profile.toString(),"[]"),state)
        CareNotifications.evaluate(appContext, journal, state, journal.score(records), null)
        CareDailyCheck.schedule(appContext)
    }
}

private data class NotificationRequest(val vehicle: String,val event: String,val nonce: Long = System.nanoTime())
class MainActivity : ComponentActivity() {
    private var notificationRequest by mutableStateOf<NotificationRequest?>(null)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent);setIntent(intent);readNotification(intent)
    }
    private fun readNotification(intent: android.content.Intent) {
        val vehicle=intent.getStringExtra("vehicleId");val event=intent.getStringExtra("careEvent")
        if(vehicle!=null && event!=null) notificationRequest=NotificationRequest(vehicle,event)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readNotification(intent)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.vemoring.companion.MirrorBluetoothService.restore(applicationContext) }
        setContent { PhoneTheme { PhoneApp(notificationRequest) } }
    }
}

@Composable private fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable private fun PhoneApp(notificationRequest: NotificationRequest? = null) {
    val context = LocalContext.current.applicationContext
    val store = remember { PhoneStore(context) }
    var loadFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var garageReady by remember {mutableStateOf(false)}
    var selectedVehicleId by rememberSaveable {mutableStateOf<String?>(null)}
    var vehiclePicker by remember {mutableStateOf(false)}
    var addingCar by remember {mutableStateOf(false)}
    var busy by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editCar by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEntry by remember { mutableStateOf<Entry?>(null) }
    var recordDate by rememberSaveable { mutableStateOf(LocalDate.now(SeoulZone).toString()) }
    var lastSavedId by rememberSaveable { mutableStateOf<String?>(null) }
    var connection by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var historyVehicle by rememberSaveable {mutableStateOf<String?>(null)}
    var historyEvent by remember {mutableStateOf<Pair<String,String>?>(null)}
    LaunchedEffect(notificationRequest) {notificationRequest?.let {
        historyVehicle=it.vehicle;historyEvent=it.vehicle to it.event;notifications=true
    }}
    var pendingSearch by rememberSaveable { mutableStateOf<String?>(null) }
    var bluetoothDialog by rememberSaveable { mutableStateOf(false) }
    val received by MirrorBus.received.collectAsState()
    val receivedVehicles by MirrorBus.vehicles.collectAsState()
    val authority by CareExchange.canonical.collectAsState()
    val weatherRevision by CareExchange.weatherRevision.collectAsState()
    val link by MirrorBus.link.collectAsState()
    val directory by produceState<Pair<List<CareAuthority>,String?>>(emptyList<CareAuthority>() to null,authority,receivedVehicles.keys,retry) {
        runCatching {withContext(Dispatchers.IO) {
            store.migrateLegacy()
            CareJournal.vehicles(context).sortedBy {JSONObject(it.profile).optString("nickname")} to store.selectedId()
        }}.onSuccess {value=it;garageReady=true;loadFailed=false}.onFailure {loadFailed=true;garageReady=true}
    }
    val fleet=directory.first
    LaunchedEffect(fleet) {
        if(fleet.none {it.vehicleId==selectedVehicleId}) selectedVehicleId=directory.second?.takeIf {id -> fleet.any {it.vehicleId==id}}
            ?: received?.snapshot?.vehicleId?.takeIf {id -> fleet.any {it.vehicleId==id}} ?: fleet.firstOrNull()?.vehicleId
    }
    LaunchedEffect(selectedVehicleId) {selectedVehicleId?.let {withContext(Dispatchers.IO) {store.select(it)}}}
    val selectedAuthority=fleet.firstOrNull {it.vehicleId==selectedVehicleId}
    val selectedReceived=receivedVehicles[selectedVehicleId]
    val vehicleData by produceState<PhoneData?>(null,selectedAuthority,selectedReceived,weatherRevision) {
        value=selectedAuthority?.let {state -> withContext(Dispatchers.IO) {phoneVehicleData(context,state,selectedReceived)}}
    }
    val viewingVehicle = vehicleData?.mirror!=null
    val readOnly = false
    val washConfirmation by rememberWashConfirmation(selectedVehicleId, authority)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { MirrorBus.load(context); CareExchange.refreshAll(context) }
    fun editLocal(action: () -> Unit) {
        if (readOnly) scope.launch { snackbar.showSnackbar("차량에서 받은 데이터예요. 차량 앱에서 수정하거나 데이터 연결에서 휴대폰 기록 보기로 전환해 주세요.") }
        else action()
    }
    fun save(updated: PhoneData, done: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                val state=withContext(Dispatchers.IO) {
                    val old=updated.vehicleId.takeIf {it.isNotBlank()}?.let {CareJournal(context,it).authority()}
                    val p=(old?.profile?.let(::JSONObject) ?: JSONObject()).put("washAlert",updated.washAlert)
                        .put("fuelAlert",updated.fuelAlert).put("notifications",updated.notifications).put("nickname",updated.nickname)
                        .put("model",updated.model).put("plate",updated.plate).put("fuel",updated.fuel).put("oilEstimate",updated.oilEstimate).put("oilBaselineKm",updated.oilBaselineKm)
                    val records=JSONArray().also {a -> updated.entries.forEach {a.put(it.wireJson())}}
                    if(old==null) CareExchange.registerPhone(context,p,records) else CareExchange.editPhone(context,updated.vehicleId,p,records)
                }
                withContext(Dispatchers.IO) {store.select(state.vehicleId)}
                selectedVehicleId=state.vehicleId
            }.onSuccess { done() }
                .onFailure { snackbar.showSnackbar("저장하지 못했어요. 다시 시도해 주세요.") }
            busy = false
        }
    }
    val labels = listOf("홈", "관리 기록", "내 차")
    val pageScroll = key(tab,selectedVehicleId) { rememberScrollState() }
    BackHandler(enabled = tab != 0 && !editor && !editCar && !editName) { tab = 0 }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { GarageGlyph(index, Modifier.size(24.dp), if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(label, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary, selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val current = vehicleData
            if (current == null) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (loadFailed) {
                        Text("저장된 정보를 읽지 못했어요.")
                        Button(onClick = { retry++ }) { Text("다시 시도") }
                    } else if(garageReady && fleet.isEmpty()) {
                        Text("등록한 차량이 없어요.")
                        Button(onClick={addingCar=true}) {Text("차량 등록")}
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick={bluetoothDialog=true},modifier=Modifier.heightIn(min=48.dp)) {
                            Text("차량 앱에서 가져오기")
                        }
                    } else CircularProgressIndicator()
                }
            } else Column(Modifier.widthIn(max = 600.dp).fillMaxSize()
                .verticalScroll(pageScroll).imePadding().padding(horizontal = if(tab == 0) 16.dp else 24.dp, vertical = if(tab == 0) 8.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if(tab == 0) 6.dp else 24.dp)) {
                OutlinedButton(enabled=!busy,onClick={vehiclePicker=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("vehicle-picker")) {
                    Text("${current.nickname} · 차량 선택 (${fleet.size}대)")
                }
                washConfirmation?.takeIf { candidate -> current.mirrorView?.demo != true &&
                    current.entries.any { it.visitId == candidate.visitId && !it.cancelled } }?.let { candidate ->
                    WashConfirmationBanner(candidate, busy, onReject = {
                        run {
                            busy = true
                            scope.launch {
                                try {
                                    val cancelled = CareExchange.cancel(context, candidate.vehicleId, candidate.visitId)
                                    snackbar.showSnackbar(if(cancelled) "잘못 인식한 세차를 취소했어요. 이전 기준으로 다시 계산했어요."
                                        else "이미 처리됐거나 취소 가능한 기간이 지난 기록이에요.")
                                } catch (_: Exception) { snackbar.showSnackbar("취소를 저장하지 못했어요. 다시 시도해 주세요.") }
                                finally { busy = false }
                            }
                        }
                    }, onDismiss = { scope.launch {
                        runCatching { CareExchange.dismissConfirmation(context, candidate.vehicleId, candidate.visitId) }
                            .onFailure { snackbar.showSnackbar("배너를 닫지 못했어요. 다시 시도해 주세요.") }
                    } })
                }
                key(current.vehicleId) {when (tab) {
                    0 -> Home(current, connect = { connection = true }, alerts = { historyVehicle=current.vehicleId;historyEvent=null;notifications = true }, search = { pendingSearch = it }, link = link,
                        receivingSelected=current.vehicleId==received?.snapshot?.vehicleId)
                    1 -> History(current.entries.filter { !it.cancelled && (it.kind != MAINTENANCE || it.maintenanceType == com.vemoring.companion.MaintenanceType.ENGINE_OIL) }, add = { date -> editLocal { editingId = null; recordDate = date.toString(); editor = true } },
                        edit = { id -> editLocal { editingId = id; editor = true } }, lastSaved = current.entries.firstOrNull { it.id == lastSavedId })
                    2 -> Car(current, busy || readOnly, edit = { editLocal { editCar = true } },
                        editNickname = { editLocal { editName = true } }, update = { save(it) {} })
                }}
                if(tab != 0) Spacer(Modifier.height(8.dp))
            }
        }
    }
    vehicleData?.let { current ->
        if (editor) RecordEditor(current.entries.firstOrNull { it.id == editingId }, LocalDate.parse(recordDate), busy,
            close = { if (!busy) editor = false }, save = { entry ->
                if (editingId == null && entry.kind == "세차" && current.entries.any {
                    !it.cancelled && it.visitId != null && it.date == entry.date }) pendingEntry = entry
                else save(current.copy(entries = (current.entries.filterNot { it.id == entry.id } + entry)
                    .sortedByDescending { it.date })) { lastSavedId = entry.id; editor = false }
            }, remove = if (editingId == null) null else ({
                val old = current.entries.first { it.id == editingId }
                val entries = deletePhoneRecord(current.entries,old.id)
                save(current.copy(entries = entries)) { editor = false }
            }))
        pendingEntry?.let { proposed ->
            AlertDialog(onDismissRequest = { pendingEntry = null }, title = { Text("같은 날 자동 세차 기록이 있어요") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("기존 기록을 보완하거나 별도 세차로 남겨 주세요.")
                    current.entries.filter { !it.cancelled && it.visitId != null && it.date == proposed.date }.forEach { old ->
                        OutlinedButton(onClick = {
                            val merged = old.copy(title = proposed.title, detail = proposed.detail)
                            save(current.copy(entries = current.entries.map { if(it.id == old.id) merged else it })) {
                                pendingEntry = null; editor = false; lastSavedId = old.id
                            }
                        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("기존 기록 보완: ${old.title}") }
                    }
                } }, confirmButton = { TextButton(enabled = !busy, onClick = {
                    save(current.copy(entries = (current.entries + proposed).sortedByDescending { it.date })) {
                        pendingEntry = null; editor = false; lastSavedId = proposed.id
                    }
                }) { Text("별도 세차 추가") } }, dismissButton = { TextButton(onClick = { pendingEntry = null }) { Text("돌아가기") } })
        }
        if (editCar) CarEditor(current, busy, close = { if (!busy) editCar = false },
            save = { save(it) { editCar = false } })
        if (editName) NicknameEditor(current.nickname, busy, close = { if (!busy) editName = false },
            save = { save(current.copy(nickname = it)) { editName = false } })
    }
    pendingSearch?.let { PhoneNavigationPicker(it) { pendingSearch = null } }
    if (connection) AlertDialog(onDismissRequest = { connection = false }, title = { Text("데이터 연결") },
        text = { Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(link.message)
            received?.let {latest -> Text("현재 수신 차량: ${JSONObject(latest.snapshot.content).getJSONObject("profile").optString("nickname")}")}
            vehicleData?.let {Text("조회 중인 차량: ${it.nickname}")}
            selectedReceived?.let { Text("마지막 수신: ${it.timeLabel()}\n차량: ${vehicleData?.nickname}") }
            Text("연결 차량의 관리 기준과 기록은 이 휴대폰에서 확정해요. 차량에는 연결 시 동기화됩니다.")
            PhoneWashAssistControl()
            OutlinedButton(onClick = { connection = false; bluetoothDialog = true }) { Text("블루투스 차량 연결") }
            OutlinedButton(onClick={connection=false;vehiclePicker=true}) {Text("등록 차량 선택")}
        } }, confirmButton = { TextButton(onClick = { connection = false }) { Text("확인") } })
    if (bluetoothDialog) BluetoothLinkDialog(host = false, dismiss = { bluetoothDialog = false })
    if(addingCar) CarEditor(PhoneData(),busy,close={if(!busy) addingCar=false},save={save(it) {addingCar=false}},register=true)
    if(vehiclePicker) AlertDialog(onDismissRequest={vehiclePicker=false},title={Text("등록 차량")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("차량을 바꿔 봐도 다른 차량의 기록과 수신은 유지돼요.")
            fleet.forEach {car -> val p=JSONObject(car.profile)
                OutlinedButton(enabled=!busy,onClick={selectedVehicleId=car.vehicleId;vehiclePicker=false;editor=false;editCar=false;editName=false},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("vehicle:${car.vehicleId}")) {
                    Text("${p.optString("nickname")} · ${p.optString("model")}\n${p.optString("plate").ifBlank {"번호판 미등록"}}${if(car.vehicleId==selectedVehicleId) " · 선택됨" else ""}")
                }
            }
        }
    },confirmButton={TextButton(enabled=!busy,onClick={vehiclePicker=false;addingCar=true},modifier=Modifier.testTag("add-vehicle")) {Text("차량 추가 등록")}},
        dismissButton={TextButton(onClick={vehiclePicker=false}) {Text("닫기")}})
    if (notifications) key(notificationRequest?.nonce) {
        NotificationHistoryDialog(historyVehicle,historyEvent,onDismiss={notifications=false},permission={CarePermissionButton()})
    }
}

@Composable private fun Title(eyebrow: String, title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, fontSize = 12.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
    }
}

@Composable private fun Panel(tinted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = if (tinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable private fun Home(data: PhoneData, connect: () -> Unit, alerts: () -> Unit, search: (String) -> Unit, link: MirrorLink = MirrorLink(),receivingSelected: Boolean = true) {
    val mirrored = data.mirrorView
    val today = LocalDate.now(SeoulZone)
    val estimatedCleanliness = remember(data.entries, today) {
        WashPolicy.score(data.entries.filter { it.kind == "세차" }.map {
            WashBaseline(it.id, it.date, it.completedAt?.let(java.time.Instant::parse), it.visitId, it.cancelled, it.source)
        }, emptyList(), java.time.Instant.now()).displayed
    }
    val localCleanliness=if(data.hasCareScore) data.careScore else estimatedCleanliness
    val needsWash = localCleanliness != null && localCleanliness <= data.washAlert
    val displayMood=mirrored?.mood ?: moodFor(false,needsWash,data.oilAssessment?.due==true)
    fun percent(key: String): Int? = when (key) { "fuel" -> mirrored?.fuel; "battery" -> mirrored?.battery; else -> if (mirrored != null) mirrored.cleanliness else localCleanliness }
    CareHomeLayout(header={
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("VEMORING", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(today.format(HomeDateFormat), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = connect) { Text("데이터 연결", fontSize = 12.sp) }
        OutlinedButton(onClick = alerts, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("알림", fontSize = 12.sp) }
    }
    data.mirror?.let { cached ->
        Text("${if (mirrored?.demo == true) "예시 차량 데이터 · " else ""}${if(!receivingSelected) "현재 수신 대상 아님 · 저장 정보" else if (link.active) "차량 연결됨" else "${link.message} · 마지막 데이터"}\n마지막 수신 ${cached.timeLabel()}",
            fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    },character={
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        val mascot = when (displayMood) {
            "FUEL" -> R.drawable.mascot_fuel; "WASH" -> R.drawable.mascot_wash; "OIL" -> R.drawable.mascot_oil
            "ANGRY" -> R.drawable.mascot_angry; "EXHAUSTED" -> R.drawable.mascot_exhausted; else -> if (needsWash) R.drawable.mascot_wash else R.drawable.mascot_normal
        }
            Box(Modifier.fillMaxWidth().height(190.dp)) {
                Image(painterResource(mascot), "베모링 캐릭터 · ${mirrored?.mood ?: "NORMAL"}",
                    Modifier.align(Alignment.Center).height(190.dp).fillMaxWidth())
                Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(14.dp),
                    modifier=Modifier.align(Alignment.TopEnd).widthIn(max=180.dp)) {
                    Text(if(mirrored?.demo==true) mirrored.message else moodMessage(displayMood),
                        Modifier.padding(horizontal=10.dp,vertical=8.dp),fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(data.model,Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(data.nickname,Modifier.weight(1f),textAlign=TextAlign.Center,
                    fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
                Text(data.plate.ifBlank { "번호판 미등록" },Modifier.weight(1f),textAlign=TextAlign.End,
                    fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            }
        }
    }
    },status={
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        buildList {
            if (data.fuel != "전기차") add("남은 연료" to (mirrored?.fuelStatus ?: "차량 연결 필요"))
            if (data.fuel == "전기차" || data.fuel == "하이브리드") add("남은 배터리" to (mirrored?.batteryStatus ?: "차량 연결 필요"))
            add("차량 청결도" to (if (percent("cleanliness") != null) "관리 기록 기반 추정"
                else if (mirrored == null && data.entries.any { it.kind == "세차" }) "차량 연결 필요" else "세차 기록이 필요해요"))
        }.forEach { (label, hint) ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                    val value = percent(when (label) { "남은 연료" -> "fuel"; "남은 배터리" -> "battery"; else -> "cleanliness" })
                    Text(value?.let { "$it %" } ?: "—", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    if (value != null) LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(hint, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    },records={
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        kinds.forEach { kind ->
            val date = data.entries.filter {it.kind == kind && !it.cancelled && (kind != MAINTENANCE || it.maintenanceType == com.vemoring.companion.MaintenanceType.ENGINE_OIL)}.maxByOrNull {it.date}?.date
            Text("$kind ${date?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: "—"}",Modifier.weight(1f),
                fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    },weather={ForecastWeatherPanel(vehicleId=data.vehicleId,enabled=mirrored?.demo!=true)},
        nearby={NearbyQuickLinks(onSearch=search)})
}

@Composable internal fun NearbyPlaces(search: (String) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("주유소", "세차장", "정비소")
    val details = listOf("주유할 곳이 필요할 때, 주변에서 찾아보세요", "깨끗한 드라이브를 위한 가까운 세차장", "차량 점검이 필요할 때, 가까운 정비소")
    val measurer = rememberTextMeasurer(cacheSize = 3)
    val density = LocalDensity.current
    val detailStyle = LocalTextStyle.current.copy(lineHeight = 22.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val contentWidth = with(density) { (maxWidth - 40.dp).roundToPx().coerceAtLeast(1) }
    val detailHeight = remember(contentWidth, detailStyle, measurer) {
        details.maxOf { measurer.measure(it, detailStyle, constraints = Constraints(maxWidth = contentWidth)).size.height }
    }
    Surface(color = listOf(Blue, Color(0xFF147D72), Color(0xFF173B54))[page], shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("nearby-banner").clickable { search(labels[page]) }) {
        Column(Modifier.fillMaxWidth().heightIn(min = 208.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("내 주변 찾기", color = Color.White, fontSize = 14.sp)
            Text("가까운 ${labels[page]} 찾기", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(details[page], color = Color.White, style = detailStyle,
                modifier = Modifier.fillMaxWidth().height(with(density) { detailHeight.toDp() }))
            Text("지도에서 검색하기  ›", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        labels.forEachIndexed { index, label -> TextButton(onClick = { page = index }) {
            Text(label, fontWeight = if (page == index) FontWeight.Bold else FontWeight.Normal)
        } }
    }
}

@Composable private fun RecordRow(entry: Entry) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${if(entry.kind == MAINTENANCE) entry.maintenanceType.label else entry.kind} · ${entry.title}", fontWeight = FontWeight.Bold, color = careColor(entry.kind))
            Text(entry.date.toString(), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.detail.isNotBlank()) Text(entry.detail, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
}

// Same as the vehicle app: wash = blue, fuel = yellow, 정비 = purple (lighter tones for the dark theme).
@Composable private fun careColor(kind: String): Color = when (kind) {
    "세차" -> MaterialTheme.colorScheme.primary
    "주유" -> if (isSystemInDarkTheme()) Color(0xFFFFD25E) else Color(0xFFA97C00)
    else -> if (isSystemInDarkTheme()) Color(0xFFC9A7FF) else Color(0xFF7B4FC4)
}

/** One colored dot per record (max 4) so a "2건" day shows which kinds they are. */
@Composable private fun CareDots(kinds: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        kinds.take(4).forEach { Box(Modifier.size(6.dp).background(careColor(it), androidx.compose.foundation.shape.CircleShape)) }
    }
}

@Composable private fun History(entries: List<Entry>, add: (LocalDate) -> Unit, edit: (String) -> Unit, lastSaved: Entry? = null) {
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    var futureError by remember { mutableStateOf(false) }
    LaunchedEffect(lastSaved?.id, lastSaved?.date) {
        lastSaved?.let { monthString = YearMonth.from(it.date).toString(); selected = it.date.toString(); all = false }
    }
    val month = YearMonth.parse(monthString)
    val byDate = remember(entries) { entries.groupBy { it.date } }
    val today = LocalDate.now(SeoulZone)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Title("MY CAR LOG", "관리 기록") }
        Button(onClick = {
            val date = selected?.let(LocalDate::parse) ?: LocalDate.now(SeoulZone)
            if (date > LocalDate.now(SeoulZone)) futureError = true else add(date)
        }) { Text("＋ 추가") }
    }
    Text("차곡차곡 쌓이는 내 차의 관리 이야기", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthString = month.minusMonths(1).toString(); selected = null }) { Text("이전 달") }
            Text("${month.year}년 ${month.monthValue}월", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            TextButton(onClick = { monthString = month.plusMonths(1).toString(); selected = null }) { Text("다음 달") }
        }
        Row { listOf("일", "월", "화", "수", "목", "금", "토").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp) } }
        val offset = month.atDay(1).dayOfWeek.value % 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row {
                repeat(7) { day ->
                    val number = week * 7 + day - offset + 1
                    val date = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                    Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        if (date != null) Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .background(if (selected == date.toString()) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { selected = if (selected == date.toString()) null else date.toString(); all = false }.padding(vertical = 8.dp)) {
                            Text(number.toString(), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                            val matching = byDate[date].orEmpty()
                            if (matching.size == 1) {
                                Text(matching.first().kind, fontSize = 10.sp, lineHeight = 14.sp, color = careColor(matching.first().kind))
                            } else if (matching.size > 1) {
                                Text("${matching.size}건", fontSize = 10.sp, lineHeight = 14.sp)
                                CareDots(matching.map { it.kind }.sortedBy { kinds.indexOf(it) })
                            }
                        }
                    }
                }
            }
        }
        }
        Text("날짜를 누르면 해당 날짜의 기록을 볼 수 있어요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        kinds.forEach { Text("● $it", fontSize = 12.sp, color = careColor(it)) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (all) "전체 관리 기록" else selected?.let { val date = LocalDate.parse(it); "${date.monthValue}월 ${date.dayOfMonth}일 관리" } ?: "최근 관리",
            Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = { all = !all; selected = null }) { Text(if (all) "최근 보기" else "전체 보기") }
    }
    val filtered = if (all) entries else if (selected != null) entries.filter { it.date.toString() == selected } else entries.take(3)
    Panel {
        if (filtered.isEmpty()) {
            Text("아직 기록이 없어요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("세차부터 엔진오일 교체까지, 첫 관리를 남겨보세요.\n상단의 ＋ 추가로 기록할 수 있어요.", lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        filtered.forEach { entry ->
            Column(Modifier.fillMaxWidth().clickable { edit(entry.id) }.padding(vertical = 8.dp)) { RecordRow(entry) }
            HorizontalDivider()
        }
    }
    if (futureError) AlertDialog(onDismissRequest = { futureError = false }, text = { Text("미래 날짜에는 관리 기록을 등록할 수 없어요.") },
        confirmButton = { TextButton(onClick = { futureError = false }) { Text("확인") } })
}

@Composable private fun Car(data: PhoneData, busy: Boolean, edit: () -> Unit, editNickname: () -> Unit, update: (PhoneData) -> Unit) {
    Title("MY GARAGE", "내 차 설정", "내 차의 정보와 관리 기준을 한곳에서")
    Surface(color = Color(0xFF173B54), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("나의 드라이빙 파트너", color = Color(0xFFB5DBF1), fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.mascot_normal), null, Modifier.size(96.dp, 100.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(data.nickname, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(data.model, color = Color.White, fontSize = 16.sp)
                }
            }
            OutlinedButton(onClick = edit, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("차량 정보 편집") }
        }
    }
    Panel {
        Text("차량 정보", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        ProfileRow("닉네임", data.nickname, editNickname)
        HorizontalDivider()
        ProfileRow("차종", data.model, edit)
        HorizontalDivider()
        ProfileRow("차량 정보 다시 등록", data.fuel, edit)
    }
    Panel {
        Text("앱 밖에서도 상태 아이콘 갱신", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("차량 화면에서 사용하는 기능이에요. 차량 앱에서 설정할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("관리 알림", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("예시 설정 · 실제 알림은 발송되지 않습니다.", fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = data.notifications, enabled = !busy, onCheckedChange = { update(data.copy(notifications = it)) })
        }
    }
    Panel {
        Title("나에게 맞게", "관리 기준 설정")
        Text("선택한 수치 이하일 때 상태를 확인해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CareThreshold("세차 알림 기준", data.washAlert, 5f..50f, 8, !busy) { update(data.copy(washAlert = it)) }
        HorizontalDivider()
        CareThreshold("주유·충전 알림 기준", data.fuelAlert, 5f..40f, 6, !busy) { update(data.copy(fuelAlert = it)) }
    }
}

@Composable private fun ProfileRow(title: String, value: String, edit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = edit).padding(vertical = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun CareThreshold(label: String, value: Int, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, save: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat().coerceIn(range)) }
    Text("$label  ${current.toInt()}% 이하", fontWeight = FontWeight.Bold)
    Slider(value = current, onValueChange = { current = it }, valueRange = range, steps = steps,
        enabled = enabled, onValueChangeFinished = { save(current.toInt()) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun RecordEditor(existing: Entry?, initial: LocalDate, busy: Boolean, close: () -> Unit, save: (Entry) -> Unit,
    remove: (() -> Unit)? = null) {
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: kinds[0]) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var detail by rememberSaveable { mutableStateOf(existing?.detail ?: "") }
    var dateString by rememberSaveable { mutableStateOf((existing?.date ?: initial).toString()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var washTime by rememberSaveable { mutableStateOf(manualWashTime(existing?.completedAt,existing?.source)) }
    val automatic = existing?.source != null && existing.source != "Manual" && existing.completedAt != null
    var deleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val memoFocus = remember { FocusRequester() }
    PhoneFormDialog(if (existing == null) "관리 기록 추가" else "관리 기록 수정", busy,
        title.isNotBlank() && LocalDate.parse(dateString) <= LocalDate.now(SeoulZone) && (kind != "세차" || automatic && !manual || washTime.isBlank() || washCompletion(LocalDate.parse(dateString),washTime)!=null), close,
        save = { save(existing?.copy(kind = kind, odometerKm=existing.odometerKm.takeIf {existing.date==LocalDate.parse(dateString) && kind==MAINTENANCE}, maintenanceType = if(kind == MAINTENANCE) com.vemoring.companion.MaintenanceType.ENGINE_OIL else com.vemoring.companion.MaintenanceType.GENERAL, title = title.trim(), date = LocalDate.parse(dateString), detail = detail.trim(),
            completedAt = if(kind == "세차" && (!automatic || manual)) washCompletion(LocalDate.parse(dateString),washTime) else if(manual) null else existing.completedAt, source = if(manual) "Manual" else existing.source, washEvidence = if(manual) "UserRecord" else existing.washEvidence)
            ?: Entry(completedAt = if(kind == "세차") washCompletion(LocalDate.parse(dateString),washTime) else null, washEvidence = "UserRecord", kind = kind, maintenanceType = if(kind == MAINTENANCE) com.vemoring.companion.MaintenanceType.ENGINE_OIL else com.vemoring.companion.MaintenanceType.GENERAL, title = title.trim(), date = LocalDate.parse(dateString), detail = detail.trim())) }) { finishInput ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { value -> FilterChip(selected = kind == value, enabled = !busy && (!automatic || manual), onClick = { kind = value }, label = { Text(value) }) }
                }
                OutlinedTextField(title, { title = it }, enabled = !busy, label = { Text("기록 이름 (필수)") }, singleLine = true,
                    keyboardOptions = KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { memoFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("record-title"))
                OutlinedButton(onClick = {
                    finishInput()
                    val d = LocalDate.parse(dateString)
                    DatePickerDialog(context, { _, y, m, day -> dateString = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }, enabled = !busy && (!automatic || manual), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("날짜: $dateString") }
                OutlinedTextField(detail, { detail = it }, enabled = !busy, label = { Text("장소, 금액, 메모") },
                    modifier = Modifier.fillMaxWidth().focusRequester(memoFocus).testTag("record-memo"), minLines = 2, maxLines = 5,
                    keyboardOptions = KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishInput() }))
                Text("자동 기록의 메모를 수정해도 원래 감지 시점은 유지돼요.", fontSize = 12.sp)
                if (automatic) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(manual, { manual = it; washTime = "" }); Text("수동 기록으로 전환")
                }
                if(kind == "세차" && (!automatic || manual)) WashCompletionInput(LocalDate.parse(dateString),washTime,!busy) {washTime=it}
                if (remove != null) TextButton(onClick = { deleteDialog = true }, enabled = !busy) { Text("기록 삭제") }
    }
    if(deleteDialog) AlertDialog(onDismissRequest = { if(!busy) deleteDialog = false }, title = { Text("기록을 삭제할까요?") },
        text = { Text("${existing?.title.orEmpty()} 기록을 삭제해요. 세차 기록이면 이전 유효한 기록부터 청결도를 다시 계산해요. 날짜를 모르는 경우에는 삭제하지 않아도 돼요.") },
        confirmButton = { TextButton(enabled=!busy,onClick = { deleteDialog = false; remove?.invoke() }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("돌아가기") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CarEditor(data: PhoneData, busy: Boolean, close: () -> Unit, save: (PhoneData) -> Unit,register: Boolean = false) {
    var nickname by rememberSaveable { mutableStateOf(data.nickname) }
    var brand by rememberSaveable { mutableStateOf(VehicleCatalogData.brands.firstOrNull { data.model.startsWith(it) } ?: "") }
    var model by rememberSaveable { mutableStateOf(data.model.removePrefix(brand).trim()) }
    var plate by rememberSaveable { mutableStateOf(data.plate) }
    var fuel by rememberSaveable { mutableStateOf(data.fuel) }
    var wash by rememberSaveable { mutableStateOf(data.entries.filter { it.kind == "세차" && !it.cancelled }.maxByOrNull { it.date }?.date?.toString()) }
    var lastFuel by rememberSaveable { mutableStateOf(data.entries.filter { it.kind == "주유" && !it.cancelled }.maxByOrNull { it.date }?.date?.toString()) }
    var oilKm by rememberSaveable {mutableStateOf(data.oilBaselineKm?.toString().orEmpty())}
    var oilEstimate by rememberSaveable {mutableStateOf(data.oilEstimate.ifBlank {"모르겠음"})}
    var oil by rememberSaveable { mutableStateOf(data.entries.filter { it.kind == MAINTENANCE && !it.cancelled && it.maintenanceType == com.vemoring.companion.MaintenanceType.ENGINE_OIL }.maxByOrNull { it.date }?.date?.toString()) }
    PhoneFormDialog(if(register) "차량 등록" else "차량 정보 편집", busy, nickname.isNotBlank() && model.isNotBlank(), close, save = {
        val fullModel = if (brand.isBlank() || model.startsWith(brand)) model.trim() else "$brand ${model.trim()}"
        save(data.copy(nickname = nickname.trim(), model = fullModel, plate = plate.trim(), fuel = fuel,oilEstimate=oilEstimate,oilBaselineKm=oilKm.toDoubleOrNull()?.takeIf {it.isFinite() && it>=0},
            entries = updateLatestPhoneRecords(data.entries, mapOf("세차" to wash?.let(LocalDate::parse), "주유" to lastFuel?.let(LocalDate::parse),
                MAINTENANCE to oil?.let(LocalDate::parse)))))
    }) { finishInput ->
            Text("제조사", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleCatalogData.brands.forEach { value -> FilterChip(selected = brand == value, enabled = !busy,
                    onClick = { finishInput(); brand = value; model = "" }, label = { Text(value) }) }
            }
            VehicleCatalogData.categories(brand).forEach { (category, models) ->
                Text(category, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { value -> FilterChip(selected = model == value, enabled = !busy,
                        onClick = { finishInput(); model = value }, label = { Text(value) }) }
                }
            }
            OutlinedTextField(model, { model = it }, enabled = !busy, label = { Text("차종 (직접 입력 가능)") }, singleLine = true,
                keyboardOptions = KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishInput() }), modifier = Modifier.fillMaxWidth().testTag("car-model"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("휘발유", "경유", "하이브리드", "전기차").forEach { value ->
                FilterChip(selected = fuel == value, enabled = !busy, onClick = { fuel = value }, label = { Text(value) })
            } }
            OutlinedTextField(nickname, { nickname = it }, enabled = !busy, label = { Text("차량 닉네임 (필수)") }, singleLine = true,
                keyboardOptions = KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishInput() }), modifier = Modifier.fillMaxWidth().testTag("car-nickname"))
            OutlinedTextField(plate, { plate = it }, enabled = !busy, label = { Text("번호판 (선택)") }, singleLine = true,
                keyboardOptions = KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishInput() }), modifier = Modifier.fillMaxWidth().testTag("car-plate"))
            OptionalCareDate("마지막 주유·충전일", lastFuel, !busy, finishInput) { lastFuel = it }
            OptionalCareDate("마지막 세차일", wash, !busy, finishInput) { wash = it }
            if(fuel!="전기차") {
                if(register) {
                    OutlinedTextField(oilKm,{oilKm=it},enabled=!busy,label={Text("교체 당시 실제 누적거리 (km, 선택)")},
                        supportingText={Text("모르면 비워 주세요. 현재 누적거리와 달라요.")},singleLine=true,
                        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
                    Text("마지막 엔진오일 교체 시점")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("날짜 입력","약 1개월 전","약 3개월 전","6개월 이상 전","모르겠음").forEach {value ->
                            FilterChip(selected=oilEstimate==value,enabled=!busy,onClick={oilEstimate=value;oil=null},label={Text(value)})
                        }
                    }
                }
                if(!register || oilEstimate=="날짜 입력") OptionalCareDate("마지막 엔진오일 교체일", oil, !busy, finishInput) { oil = it }
            }
            Text("날짜를 모르면 기존 기록은 유지해요. 삭제는 관리 기록에서 기록을 선택한 뒤 확인해 주세요.",fontSize=13.sp)
    }
}

@Composable private fun OptionalCareDate(label: String, value: String?, enabled: Boolean, finishInput: () -> Unit, change: (String?) -> Unit) {
    val context = LocalContext.current
    fun chooseDate() {
        finishInput()
        val date = value?.let(LocalDate::parse) ?: LocalDate.now(SeoulZone)
        DatePickerDialog(context, { _, y, m, day -> change(LocalDate.of(y, m + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth)
            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }
    OutlinedButton(enabled = enabled, onClick = {chooseDate()}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("$label: ${value ?: "날짜 모름"}") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = value == null, enabled = enabled, onCheckedChange = { if(it) change(null) else chooseDate() })
        Text("날짜 모름 (기존 기록 유지)", fontSize = 12.sp)
    }
}

@Composable private fun NicknameEditor(initial: String, busy: Boolean, close: () -> Unit, save: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    PhoneFormDialog("닉네임 변경", busy, value.isNotBlank(), close, { save(value.trim()) }) { finishInput ->
        OutlinedTextField(value, { value = it }, enabled = !busy, label = { Text("닉네임") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KoreanTextKeyboardOptions.copy(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { finishInput() }))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun HomePreview() { PhoneTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { Home(PhoneData(), {}, {}, {}) } } } }
