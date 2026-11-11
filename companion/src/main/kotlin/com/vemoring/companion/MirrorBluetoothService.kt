package com.vemoring.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import org.json.JSONObject

/** Secure RFCOMM, an explicitly selected bonded peer, one channel, no discovery or polling. */
@SuppressLint("MissingPermission") // CONNECT is checked before starting and failures close the session.
class MirrorBluetoothService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private var server: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
    private var peer = ""
    private var hosting = false
    private var registered = false
    private val adapter get() = getSystemService(BluetoothManager::class.java)?.adapter

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON) {
                closeSession()
                MirrorBus.link.value = MirrorLink(false, "블루투스 꺼짐 · 마지막 데이터 유지", true)
            } else if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && hosting && session?.isActive != true) startHost()
            else if (runCatching { device?.address == peer }.getOrDefault(false)) {
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED && !hosting && session?.isActive != true) startClient()
                if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    runCatching { socket?.close() }
                    MirrorBus.link.value = MirrorLink(false, "연결 끊김 · 마지막 데이터 유지", true)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent == null || !hasConnectPermission(this) || adapter == null || adapter?.isEnabled != true) {
            MirrorBus.link.value = MirrorLink(false, "블루투스와 주변 기기 권한을 확인해 주세요.")
            stopSelf(); return START_NOT_STICKY
        }
        val address = intent.getStringExtra("peer")
        if (!BluetoothAdapter.checkBluetoothAddress(address ?: "")) { stopSelf(); return START_NOT_STICKY }
        try {
            if (adapter!!.getRemoteDevice(address).bondState != BluetoothDevice.BOND_BONDED) {
                MirrorBus.link.value = MirrorLink(false, "시스템 설정에서 먼저 기기를 페어링해 주세요.")
                stopSelf(); return START_NOT_STICKY
            }
            closeSession()
            peer = address!!
            hosting = intent.action == HOST
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "베모링 기기 연결", NotificationManager.IMPORTANCE_LOW))
            val notification = notification("블루투스 데이터 연결 준비 중")
            if (Build.VERSION.SDK_INT >= 29) startForeground(2041, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(2041, notification)
            if (!registered) {
                ContextCompat.registerReceiver(this, events, IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                }, ContextCompat.RECEIVER_EXPORTED)
                registered = true
                scope.launch { MirrorBus.link.collect { runCatching { manager.notify(2041, notification(it.message)) } } }
            }
            if (hosting) startHost() else startClient()
        } catch (_: Exception) {
            MirrorBus.link.value = MirrorLink(false, "이 기기에서 블루투스 데이터 연결을 시작할 수 없어요.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startHost() {
        session = scope.launch {
            var listening: BluetoothServerSocket? = null
            try {
                MirrorBus.loadOutgoing(this@MirrorBluetoothService)
                listening = adapter!!.listenUsingRfcommWithServiceRecord("Vemoring Care v2", SERVICE_UUID)
                server = listening
                while (isActive) {
                    MirrorBus.link.value = MirrorLink(false, "선택한 휴대폰의 연결을 기다리고 있어요.", true)
                    val connected = listening.accept()
                    if (connected.remoteDevice.address != peer || connected.remoteDevice.bondState != BluetoothDevice.BOND_BONDED) {
                        connected.close(); continue
                    }
                    socket = connected
                    try {
                        coroutineScope {
                            val timeout = launch { delay(15_000); connected.close() }
                            check(DataInputStream(connected.inputStream).readInt() == HELLO)
                            timeout.cancel()
                            ensureActive()
                            val acknowledgements = Channel<Pair<String, Long>>(Channel.CONFLATED)
                            val writer = launch {
                                try { MirrorBus.outgoing.filterNotNull().collectLatest { snapshot ->
                                    var acknowledged = false
                                    repeat(3) {
                                        if (!acknowledged) {
                                            MirrorFrames.write(connected.outputStream, snapshot)
                                            acknowledged = withTimeoutOrNull(5000) {
                                                while (true) {
                                                    val ack = acknowledgements.receive()
                                                    if (ack == (snapshot.vehicleId to snapshot.revision)) break
                                                }
                                                true
                                            } ?: false
                                        }
                                    }
                                    if (!acknowledged) throw IOException("Phone did not ACK snapshot")
                                } } finally { runCatching { connected.close() } }
                            }
                            try {
                                val input = DataInputStream(connected.inputStream)
                                while (isActive) {
                                    val length = input.readInt()
                                    require(length in 1..MirrorSnapshot.MAX_BYTES)
                                    val bytes = ByteArray(length); input.readFully(bytes)
                                    val reply = JSONObject(bytes.toString(Charsets.UTF_8))
                                    val sent = MirrorBus.outgoing.value
                                    require(sent != null && reply.getString("vehicleId") == sent.vehicleId && reply.getLong("ack") <= sent.revision)
                                    CareExchange.reply(this@MirrorBluetoothService, reply)
                                    acknowledgements.trySend(reply.getString("vehicleId") to reply.getLong("ack"))
                                    MirrorBus.link.value = MirrorLink(true, "휴대폰 수신·저장 응답 확인됨", true)
                                }
                            }
                            finally { writer.cancel() }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* The listener stays available for the selected peer. */ }
                    finally { runCatching { connected.close() }; if (socket === connected) socket = null }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (isActive) MirrorBus.link.value = MirrorLink(false, "차량 블루투스 서버를 열 수 없어요. 기기 지원 여부를 확인해 주세요.", true) }
            finally { runCatching { listening?.close() }; if (server === listening) server = null }
        }
    }

    private fun startClient() {
        if (session?.isActive == true) return
        session = scope.launch {
            var connected: BluetoothSocket? = null
            var timeout: Job? = null
            try {
                MirrorBus.load(this@MirrorBluetoothService)
                MirrorBus.link.value = MirrorLink(false, "차량 앱에 연결 중이에요…", true)
                val client = adapter!!.getRemoteDevice(peer).createRfcommSocketToServiceRecord(SERVICE_UUID)
                connected = client
                socket = client
                timeout = launch { delay(15_000); client.close() }
                client.connect()
                DataOutputStream(client.outputStream).apply { writeInt(HELLO); flush() }
                // CONNECTED is reported only after a complete, valid application snapshot.
                var first = true
                val outputMutex = Mutex()
                suspend fun send(response: JSONObject) = outputMutex.withLock {
                    val bytes = response.toString().toByteArray(Charsets.UTF_8)
                    require(bytes.size in 1..MirrorSnapshot.MAX_BYTES)
                    DataOutputStream(client.outputStream).apply { writeInt(bytes.size); write(bytes); flush() }
                }
                val settingsWriter = launch {
                    kotlinx.coroutines.flow.combine(CareExchange.canonical,CareExchange.weatherRevision) {a,_ -> a}.drop(1).collect { candidate ->
                        val received = MirrorBus.received.value?.snapshot
                        val authority=candidate?.takeIf {it.vehicleId==received?.vehicleId}
                            ?: received?.let {CareJournal(this@MirrorBluetoothService,it.vehicleId).authority()}
                        if (authority != null && received?.vehicleId == authority.vehicleId)
                            send(JSONObject().put("schema", 2).put("vehicleId", authority.vehicleId)
                                .put("ack", received.revision).put("authority", authority.json())
                                .put("weatherIntervals",CareJournal(this@MirrorBluetoothService,authority.vehicleId).weatherReply()))
                    }
                }
                try { while (isActive) {
                    val snapshot = MirrorFrames.read(client.inputStream)
                    MirrorBus.receive(this@MirrorBluetoothService, snapshot)
                    send(CareExchange.receive(this@MirrorBluetoothService, snapshot))
                    ensureActive()
                    if (first) { timeout.cancel(); first = false }
                    MirrorBus.link.value = MirrorLink(true, "차량 데이터 연결됨", true)
                } } finally { settingsWriter.cancel() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (isActive) MirrorBus.link.value = MirrorLink(false, "연결 끊김 · 마지막 데이터 유지. 차량 앱의 휴대폰 연결을 확인해 주세요.", true)
            } finally { timeout?.cancel(); runCatching { connected?.close() }; if (socket === connected) socket = null }
        }
    }

    private fun closeSession() {
        session?.cancel(); session = null
        runCatching { server?.close() }; server = null
        runCatching { socket?.close() }; socket = null
    }
    override fun onDestroy() {
        closeSession()
        if (registered) unregisterReceiver(events)
        scope.cancel()
        MirrorBus.link.value = MirrorLink(false, "연결 종료 · 마지막 데이터 유지")
        super.onDestroy()
    }
    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, notificationActivityIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MirrorBluetoothService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("베모링 기기 연결")
            .setContentText(message).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "연결 종료", stop).build()).build()
    }
    companion object {
        const val HOST = "com.vemoring.companion.HOST"
        const val CONNECT = "com.vemoring.companion.CONNECT"
        const val STOP = "com.vemoring.companion.STOP"
        private const val CHANNEL = "vemoring_companion"
        private const val HELLO = 0x56454D32
        val SERVICE_UUID: UUID = UUID.fromString("7ff67821-9d45-4a95-8c28-92306fdab461")
        fun hasConnectPermission(context: Context) = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        fun start(context: Context, host: Boolean, address: String) {
            ContextCompat.startForegroundService(context, Intent(context, MirrorBluetoothService::class.java)
                .setAction(if (host) HOST else CONNECT).putExtra("peer", address))
        }
    }
}
