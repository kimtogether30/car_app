package com.example.vemoring

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.vemoring.integration.MaintenanceStore
import com.example.vemoring.vehicle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Only lifecycle state is global. No Activity, user data, or additional SDK session is retained. */
object IconMonitorVisibility {
    val visible = MutableStateFlow(false)
    val viewedVehicleId=MutableStateFlow<String?>(null)
}

private data class MonitorEvaluation(val sample: IconMonitorSample,val visible: Boolean,val preview: Boolean,val viewingSource: Boolean)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VehicleIconMonitor : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val reload = Channel<Unit>(Channel.CONFLATED)
    private var unsubscribe: (() -> Unit)? = null
    private var listening = false
    private var acquired = false
    private lateinit var prefs: SharedPreferences
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload.trySend(Unit) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "차량 상태 아이콘", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle("VEMORING 차량 상태 확인 중")
            .setContentText("다른 화면에서도 수신 가능한 차량 정보를 받아요 · 내 차 설정에서 끌 수 있어요")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        scope.launch {
            // SharedPreferences may wait for disk on first access. Keep that off the UI thread.
            prefs = withContext(Dispatchers.IO) { getSharedPreferences(PREFS, MODE_PRIVATE).also { it.all } }
            prefs.registerOnSharedPreferenceChangeListener(changed)
            getSharedPreferences("vemoring_native", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changed)
            listening = true
            reload.trySend(Unit)
            var lastKnown = CareSignals()
            var lastVehicleId: String?=null
            val garage = withContext(Dispatchers.IO) { GarageStore(this@VehicleIconMonitor) }
            // A selection write changes the garage, but not the receiving car. Keep its collectors alive.
            reload.receiveAsFlow().mapLatest {
                withContext(Dispatchers.IO) {
                    val data = if (prefs.getBoolean(RECEIVE_ENABLED, true)) runCatching { garage.receiving() }.getOrNull() else null
                    data to prefs.getBoolean(ENABLED, true)
                }
            }.distinctUntilChanged().collectLatest { (data, _) ->
                if (data == null || !data.complete) {
                    stopSelf(); return@collectLatest
                }
                if(lastVehicleId!=data.profile.vehicleId) {lastKnown=CareSignals();lastVehicleId=data.profile.vehicleId;VehicleCareRuntime.resetSession()}
                unsubscribe?.invoke()
                val maintenance = withContext(Dispatchers.IO) { MaintenanceStore(this@VehicleIconMonitor, data.profile) }
                // Journal writes (e.g. every mileage sample) only reload the maintenance config, not the
                // stored profile or the whole pipeline. The latest load wins; equal configs are skipped.
                val configChanges = MutableStateFlow(0)
                unsubscribe = maintenance.observe { configChanges.value++ }
                var syncedState: Pair<com.vemoring.companion.CareAuthority?, Boolean>? = null
                val configs = configChanges.mapLatest { withContext(Dispatchers.IO) { maintenance.load() } }
                    .distinctUntilChanged().onEach { config ->
                        val syncState = config.authority to config.phoneSyncPending
                        if (syncState != syncedState) {
                            if (!config.phoneSyncPending) config.authority?.let { authority ->
                                withContext(Dispatchers.IO) { garage.syncFromPhone(authority) }
                            }
                            syncedState = syncState
                        }
                    }
                val repository = SharedVehicleConnection.repository(this@VehicleIconMonitor)
                if (!acquired) { SharedVehicleConnection.acquire(this@VehicleIconMonitor, this@VehicleIconMonitor); acquired = true }
                // SDK callbacks arrive in batches. One evaluation after a short quiet period avoids intermediate icons.
                val careSnapshots=repository.state.map {it to it.motion()}.distinctUntilChanged {old,next ->
                    old.second==next.second && old.first.readings.filterKeys {it!=VehicleField.SPEED}==next.first.readings.filterKeys {it!=VehicleField.SPEED}
                }.map {it.first}
                val washSnapshots=combine(careSnapshots,VehicleWashRuntime.revision) {snapshot,_ -> snapshot}
                combine(washSnapshots, IconMonitorVisibility.visible, IconMonitorTestSource.input, configs,IconMonitorVisibility.viewedVehicleId) { snapshot, visible, preview, config,viewed ->
                    MonitorEvaluation(preview ?: IconMonitorSample(data, snapshot, config),visible,preview!=null,viewed==data.profile.vehicleId)
                }.debounce(500).collect { (sample, visible, preview,viewingSource) ->
                        val snapshot = sample.snapshot
                        val condition = careCondition(sample.data, snapshot, sample.config)
                        val signals = condition.signals
                        if (!preview) lastKnown = retainKnownSignals(lastKnown, signals)
                        val mood = (if (preview) signals else lastKnown).mood()
                        if (!preview && (!visible || !viewingSource)) {
                            val care = VehicleCareRuntime.payload(applicationContext, sample.data, snapshot, false)
                            withContext(Dispatchers.IO) {
                                val content = org.json.JSONObject(mirrorContent(sample.data, snapshot, condition, mood, false)).put("care", care).toString()
                                com.vemoring.companion.MirrorBus.publish(applicationContext, content,
                                    com.example.vemoring.integration.vehicleCareId(sample.data.profile))
                            }
                        }
                        val energyKnown = if (sample.data.profile.electric) snapshot[VehicleField.SOC].realPercent() != null
                            else snapshot[VehicleField.FUEL].realPercent() != null
                        if (!visible && energyKnown && prefs.getBoolean(ENABLED,true)) {
                            withContext(Dispatchers.IO) {
                                if (!IconMonitorVisibility.visible.value) runCatching {
                                    MascotLauncher.sync(applicationContext, mood)
                                }.onFailure { android.util.Log.w("VehicleIconMonitor", "Icon update failed", it) }
                            }
                        }
                    }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (listening) {
            prefs.unregisterOnSharedPreferenceChangeListener(changed)
            getSharedPreferences("vemoring_native", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changed)
        }
        unsubscribe?.invoke()
        scope.cancel()
        if (acquired) SharedVehicleConnection.release(this)
        super.onDestroy()
    }
    companion object {
        const val PREFS = "vemoring_icon_monitor"
        const val ENABLED = "enabled"
        const val RECEIVE_ENABLED="receive_enabled"
        private const val CHANNEL = "vehicle_status_monitor"
        private const val ID = 2101
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, VehicleIconMonitor::class.java)) }
                .onFailure { android.util.Log.w("VehicleIconMonitor", "Monitoring could not start", it) }
        }
    }
}

/** A failed/unsupported reading must not silently clear a previously received warning. */
fun retainKnownSignals(previous: CareSignals, next: CareSignals) = CareSignals(
    next.lowFuel ?: previous.lowFuel, next.needsWash ?: previous.needsWash,
    next.oilDue)

class IconMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) VehicleIconMonitor.start(context)
    }
}
