package com.vemoring.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Opt-in physical RFCOMM harness. Only disposable app IDs; no real garage, SDK, or external weather API. */
@SuppressLint("MissingPermission")
class BluetoothPhysicalHarnessTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val commands = Mutex()
    private val reportLock = Mutex()
    private val stopped = CompletableDeferred<Unit>()
    private val failures = mutableListOf<String>()
    private val adapter get() = context.getSystemService(BluetoothManager::class.java).adapter
    private val runId get() = args.getString("runId")!!
    private val host get() = args.getString("role")=="host"
    private var currentVehicle = ""
    private var payload: JSONObject? = null
    private var rawServer: BluetoothServerSocket? = null
    private var rawSocket: BluetoothSocket? = null
    private var rawJob: Job? = null
    private fun id(suffix: String = "A") = "$runId-$suffix"
    private fun peer(): BluetoothDevice = adapter.bondedDevices.single {
        it.name.orEmpty().contains(args.getString("peerName")!!,ignoreCase=true)
    }

    private fun profile(vehicle: String) = JSONObject().put("vehicleId",vehicle).put("nickname","BT 검증 차량")
        .put("model","무선 시험 표본 · 실제 센서 아님").put("plate","").put("fuel","하이브리드")
        .put("washAlert",25).put("fuelAlert",20).put("notifications",true).put("simulation",true)

    private fun authority(vehicle: String): CareAuthority = CareJournal(context,vehicle).authority()
        ?: CareAuthority(vehicle,1,profile(vehicle).toString(),"[]").also {CareJournal(context,vehicle).saveAuthority(it)}

    private fun envelope(vehicle: String, message: String, fuel: Int? = 80, washer: Boolean? = null): JSONObject {
        val state=authority(vehicle)
        val out=CareJournal(context,vehicle).outgoingState()
        val now=System.currentTimeMillis()
        return JSONObject().put("profile",JSONObject(state.profile)).put("records",JSONArray(state.records))
            .put("mood","NORMAL").put("message",message).put("demo",false)
            .put("fuel",fuel ?: JSONObject.NULL).put("battery",0).put("cleanliness",JSONObject.NULL)
            .put("oilRemainingKm",JSONObject.NULL).put("fuelAt",now).put("batteryAt",now)
            .put("signals",JSONObject().put("lowFuel",false).put("vehicleWarning",washer==true))
            .put("care",JSONObject().put("authority",state.json()).put("intervals",out.intervals)
                .put("request",out.request).put("rejectedVisits",out.rejectedVisits).put("deletedRecords",out.deletedRecords)
                .put("snapshot",JSONObject().put("at",now).put("fuel",fuel ?: JSONObject.NULL).put("battery",0))
                .put("warnings",JSONObject().also { warnings -> if(washer!=null) warnings.put("WASHER",JSONObject()
                    .put("status","Available").put("source","Real").put("at",now).put("warning",washer)) }))
    }

    private suspend fun publish(message: String, vehicle: String = currentVehicle, fuel: Int? = 80, washer: Boolean? = null) {
        currentVehicle=vehicle
        payload=envelope(vehicle,message,fuel,washer)
        MirrorBus.publish(context,payload.toString(),vehicle)
    }

    private fun status(): JSONObject {
        val link=MirrorBus.link.value
        val incoming=MirrorBus.received.value?.snapshot
        val outgoing=MirrorBus.outgoing.value
        val ids=(MirrorBus.vehicles.value.keys+listOf(id(),id("B"))).filter {it.startsWith(runId)}.toSet()
        val states=JSONObject()
        for(vehicle in ids) {
            val journal=CareJournal(context,vehicle)
            val intervals=journal.intervals()
            states.put(vehicle,JSONObject().put("authority",journal.authority()?.json())
                .put("intervals",intervals.size).put("pendingIntervals",journal.outgoingIntervals().length())
                .put("weatherIntervals",intervals.count {it.weather!=null})
                .put("pendingRequest",journal.pendingRequest()).put("rejected",JSONArray(journal.rejectedVisits().toList()))
                .put("deleted",JSONArray(journal.deletedRecords().toList())))
        }
        val history=NotificationHistory(context).use {store -> JSONArray().also {a ->
            store.recent(limit=100).filter {it.vehicleId.startsWith(runId)}.forEach {
                a.put(JSONObject().put("vehicleId",it.vehicleId).put("key",it.bannerKey).put("title",it.title)
                    .put("delivery",it.delivery.name).put("visitId",it.visitId))
            }
        } }
        return JSONObject().put("at",System.currentTimeMillis()).put("role",if(host) "host" else "client")
            .put("package",context.packageName).put("model",android.os.Build.MODEL).put("api",android.os.Build.VERSION.SDK_INT)
            .put("active",link.active).put("running",link.running).put("message",link.message)
            .put("receivedVehicle",incoming?.vehicleId).put("receivedRevision",incoming?.revision)
            .put("receivedMessage",incoming?.let {JSONObject(it.content).optString("message")})
            .put("receivedAt",MirrorBus.received.value?.receivedAt)
            .put("receivedCapturedAt",incoming?.capturedAt)
            .put("receivedFuel",incoming?.let {JSONObject(it.content).opt("fuel")})
            .put("receivedBattery",incoming?.let {JSONObject(it.content).opt("battery")})
            .put("outgoingVehicle",outgoing?.vehicleId).put("outgoingRevision",outgoing?.revision)
            .put("states",states).put("history",history)
    }

    private suspend fun report(event: String, extra: JSONObject? = null): JSONObject = reportLock.withLock {
        val report=status().put("event",event).put("detail",extra ?: JSONObject())
        File(context.filesDir,"bt-status.json").writeText(report.toString(2))
        File(context.filesDir,"bt-events.jsonl").appendText(report.toString()+"\n")
        report
    }

    @Test fun session() = runBlocking {
        assumeTrue("Explicit physical-device run only",args.getString("physicalBluetooth")=="1")
        check(context.packageName.endsWith(".verification"))
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0)
        check(args.getString("runId")?.startsWith("bt-")==true)
        currentVehicle=id()
        compose.setContent {MaterialTheme {Text("블루투스 실기기 검증 · 격리된 시험 데이터")}}
        compose.waitForIdle()
        MirrorBus.load(context)
        MirrorBus.loadOutgoing(context)
        val receiver=object: BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if(intent.getStringExtra("runId")!=runId) return
                val pending=goAsync()
                scope.launch { commands.withLock {
                    val command=intent.getStringExtra("command").orEmpty()
                    try {
                        val result=execute(command,intent).put("ok",true)
                        pending.resultCode=0;pending.resultData=result.toString()
                    } catch(e: Exception) {
                        failures.add("$command: ${e.javaClass.simpleName}: ${e.message}")
                        val result=report("FAILED:$command",JSONObject().put("error",failures.last())).put("ok",false)
                        pending.resultCode=1;pending.resultData=result.toString()
                    } finally {pending.finish();if(command=="stop") stopped.complete(Unit)}
                } }
            }
        }
        ContextCompat.registerReceiver(context,receiver,IntentFilter(ACTION),Manifest.permission.DUMP,
            Handler(Looper.getMainLooper()),ContextCompat.RECEIVER_EXPORTED)
        val observer=scope.launch {
            combine(MirrorBus.link,MirrorBus.received,CareExchange.canonical) {link,received,state ->
                Triple(link,received?.snapshot?.revision,state?.version)
            }.distinctUntilChanged().collect {report("state")}
        }
        try {
            report("ready")
            withTimeout(3_600_000) {stopped.await()}
            assertTrue(failures.joinToString("\n"),failures.isEmpty())
        } finally {
            context.unregisterReceiver(receiver)
            observer.cancelAndJoin()
            rawJob?.cancel();runCatching {rawSocket?.close()};runCatching {rawServer?.close()}
            context.stopService(Intent(context,MirrorBluetoothService::class.java))
            report("finished")
            scope.cancel()
        }
    }

    private suspend fun execute(command: String, i: Intent): JSONObject {
        val vehicle=id(i.getStringExtra("car") ?: "A")
        when(command) {
            "start" -> {
                val preferences=context.getSharedPreferences("physical_bt_harness",Context.MODE_PRIVATE)
                val address=if(MirrorBluetoothService.hasConnectPermission(context)) peer().address.also {
                    check(preferences.edit().putString("peer",it).commit())
                } else requireNotNull(preferences.getString("peer",null))
                MirrorBluetoothService.start(context,host,address)
            }
            "invalidPeer" -> MirrorBluetoothService.start(context,host,"00:00:00:00:00:00")
            "disconnect" -> context.stopService(Intent(context,MirrorBluetoothService::class.java))
            "publish" -> publish(i.getStringExtra("message") ?: "frame",vehicle,
                if(i.getBooleanExtra("missingFuel",false)) null else i.getIntExtra("fuel",80),
                if(i.hasExtra("washer")) i.getBooleanExtra("washer",false) else null)
            "awaitMessage" -> withTimeout(20_000) {MirrorBus.received.first {
                it?.snapshot?.vehicleId==vehicle && JSONObject(it.snapshot.content).optString("message")==i.getStringExtra("message")
            }}
            "awaitLink" -> withTimeout(25_000) {MirrorBus.link.first {it.active==i.getBooleanExtra("active",true)}}
            "awaitCanonical" -> withTimeout(20_000) {CareExchange.canonical.first {state ->
                state?.vehicleId==vehicle && (!i.hasExtra("fuelAlert") || JSONObject(state.profile).getInt("fuelAlert")==i.getIntExtra("fuelAlert",0)) &&
                    (!i.hasExtra("recordId") || JSONArray(state.records).let {a -> (0 until a.length()).any {n ->
                        val r=a.getJSONObject(n)
                        r.getString("id")==i.getStringExtra("recordId") && (!i.hasExtra("cancelled") || r.optBoolean("cancelled")==i.getBooleanExtra("cancelled",false)) &&
                            (!i.hasExtra("deleted") || r.optBoolean("deleted")==i.getBooleanExtra("deleted",false))
                    }})
            }}
            "edit", "add", "delete", "queue" -> {
                val old=authority(vehicle)
                val p=JSONObject(old.profile)
                val records=JSONArray(old.records)
                if(i.hasExtra("fuelAlert")) p.put("fuelAlert",i.getIntExtra("fuelAlert",20))
                if(i.hasExtra("nickname")) p.put("nickname",i.getStringExtra("nickname"))
                if(i.hasExtra("notifications")) p.put("notifications",i.getBooleanExtra("notifications",true))
                if(command=="add") records.put(JSONObject().put("id",i.getStringExtra("recordId") ?: "manual-record")
                    .put("kind",i.getStringExtra("kind") ?: "maintenance").put("date",LocalDate.now(CARE_ZONE).toString())
                    .put("title","실제 무선 검증 기록").put("detail","검증 앱 전용").put("source","Manual")
                    .put("maintenanceType",i.getStringExtra("type") ?: "TIRE_PRESSURE"))
                if(command=="delete") repeat(records.length()) {n -> val r=records.getJSONObject(n)
                    if(r.getString("id")==i.getStringExtra("recordId")) r.put("deleted",true).put("cancelled",true)
                }
                if(command=="queue") {CareJournal(context,vehicle).queueRequest(p,records);publish("queued",vehicle)}
                else CareExchange.editPhone(context,vehicle,p,records)
            }
            "intervals" -> {
                val now=Instant.now().minusSeconds(7200)
                CareJournal(context,vehicle).mergeIntervals(List(i.getIntExtra("count",130)) {n ->
                    TravelInterval("radio-$n",now.plusSeconds(n*30L),now.plusSeconds(n*30L+20),.1,
                        source="PhysicalBluetoothFixture")
                })
                publish("interval-batches",vehicle)
            }
            "weatherFixture" -> {
                val journal=CareJournal(context,vehicle)
                val start=LocalDate.now(CARE_ZONE).minusDays(1).atTime(9,1).atZone(CARE_ZONE).toInstant()
                val end=start.plusSeconds(120)
                val weather=if(host) null else HourWeather(DrivingWeatherPolicy.hourStart(start),1.0,0.0,0.0,
                    10.0,5.0,Instant.now(),"PhysicalBluetoothFixture")
                journal.mergeIntervals(listOf(TravelInterval("radio-weather-proof",start,end,1.0,located=true,
                    point=TravelPoint(37.5665,126.9780,end),weather=weather)))
                if(!host) assertNotNull(journal.intervals().single {it.id=="radio-weather-proof"}.weather)
                // The receiver is preseeded with explicit public-coordinate weather. No external request is needed.
                if(host) publish("cached-weather-proof",vehicle)
            }
            "detect" -> CareExchange.detected(context,vehicle,DetectedWash(i.getStringExtra("visit") ?: "radio-visit",Instant.now()))
            "cancel" -> assertTrue(CareExchange.cancel(context,vehicle,i.getStringExtra("visit") ?: "radio-visit"))
            "dismiss" -> CareExchange.dismissConfirmation(context,vehicle,i.getStringExtra("visit") ?: "radio-visit")
            "tapNotification" -> context.getSystemService(NotificationManager::class.java).activeNotifications
                .first {it.tag?.startsWith("$vehicle:")==true && it.notification.contentIntent!=null}.notification.contentIntent.send()
            "reject" -> {
                CareJournal(context,vehicle).rememberRejectedVisits(setOf(i.getStringExtra("visit") ?: "radio-visit"))
                publish("offline-rejection",vehicle)
            }
            "rawClient" -> rawClient(i.getStringExtra("mode") ?: "noAck")
            "rawHost" -> {
                check(rawJob?.isActive!=true)
                rawJob=scope.launch {try {rawHost(i.getStringExtra("mode") ?: "badLength",i.getIntExtra("clockOffsetSeconds",0),i.getStringExtra("car") ?: "A",i.getStringExtra("revision")?.toLong())}
                    catch(e: AssertionError) {failures.add("rawHost: ${e.message}");report("rawHost-assertion-failed",JSONObject().put("error",e.message))}
                    catch(e: Exception) {report("rawHost-finished",JSONObject().put("result",e.javaClass.simpleName))}}
            }
            "closeRaw" -> {rawJob?.cancel();rawSocket?.close();rawServer?.close();rawJob=null;rawSocket=null;rawServer=null}
            "assert" -> {
                val s=status()
                val field=i.getStringExtra("field")!!
                assertEquals(i.getStringExtra("expected"),s.opt(field)?.toString())
            }
            "status" -> Unit
            "stop" -> Unit
            else -> error("Unknown command")
        }
        return report(command)
    }

    private suspend fun rawClient(mode: String) {
        val socket=peer().createRfcommSocketToServiceRecord(if(mode=="wrongUuid") UUID.fromString("00000000-0000-0000-0000-000000000001") else MirrorBluetoothService.SERVICE_UUID)
        rawSocket=socket
        val timeout=scope.launch {delay(30_000);socket.close()}
        try {
            if(mode=="wrongUuid") {
                try {socket.connect();error("Unexpected service at wrong UUID")} catch(_:IOException) {report("rawClient:wrongUuid-rejected")}
                return
            }
            socket.connect()
            val out=DataOutputStream(socket.outputStream)
            fun expectClosed() {try {assertEquals(-1,socket.inputStream.read())} catch(_: IOException) { /* RFCOMM reports EOF as IOException on Samsung. */ }}
            out.writeInt(if(mode=="wrongHello") 1 else HELLO);out.flush()
            if(mode=="wrongHello") {expectClosed();return}
            val frames=mutableListOf<MirrorSnapshot>()
            repeat(if(mode=="noAck") 3 else 1) {frames.add(MirrorFrames.read(socket.inputStream))}
            if(mode=="noAck") {
                assertEquals(1,frames.map {it.revision}.toSet().size)
                report("rawClient:noAck",JSONObject().put("frames",frames.size).put("sameRevision",true))
                expectClosed()
            } else {
                val frame=frames.single()
                if(mode=="saveNoAck") {
                    MirrorBus.receive(context,frame)
                    CareExchange.receive(context,frame)
                    report("rawClient:saved-without-ack",JSONObject().put("vehicleId",frame.vehicleId).put("revision",frame.revision))
                    return
                }
                if(mode=="delayedAck") {
                    delay(6000)
                    val resent=MirrorFrames.read(socket.inputStream)
                    assertEquals(frame.revision,resent.revision)
                    assertEquals(frame.vehicleId,resent.vehicleId)
                    report("rawClient:delayedAck",JSONObject().put("sameRevision",true))
                }
                val reply=JSONObject().put("schema",if(mode=="wrongAckSchema") 99 else 2).put("vehicleId",if(mode=="wrongVehicle") "wrong-car" else frame.vehicleId)
                    .put("ack",if(mode=="futureAck") frame.revision+1 else frame.revision)
                val bytes=reply.toString().toByteArray()
                out.writeInt(bytes.size);out.write(bytes);out.flush()
                if(mode=="duplicateAck") {out.writeInt(bytes.size);out.write(bytes);out.flush();delay(1000);report("rawClient:duplicateAck")}
                if(mode !in setOf("ack","delayedAck","duplicateAck")) expectClosed()
            }
        } finally {timeout.cancel();socket.close();rawSocket=null}
    }

    private suspend fun rawHost(mode: String, clockOffsetSeconds: Int, car: String, cachedRevision: Long?) {
        val server=adapter.listenUsingRfcommWithServiceRecord("Vemoring physical verification",MirrorBluetoothService.SERVICE_UUID)
        rawServer=server
        val timeout=scope.launch {delay(95_000);rawSocket?.close();server.close()}
        try {
            report("rawHost-ready",JSONObject().put("mode",mode))
            repeat(if(mode in setOf("sequence","fragment","notifications","blockedNotification","ev","fuelEpisode","cachedSwitch")) 1 else 4) {attempt ->
                val socket=server.accept();rawSocket=socket
                try {
                    assertEquals(peer().address,socket.remoteDevice.address)
                    assertEquals(HELLO,DataInputStream(socket.inputStream).readInt())
                    val out=DataOutputStream(socket.outputStream)
                    suspend fun send(s: MirrorSnapshot, fragmented: Boolean=false) {
                        if(fragmented) {
                            val bytes=ByteArrayOutputStream().also {MirrorFrames.write(it,s)}.toByteArray()
                            for(chunk in bytes.toList().chunked(37)) {out.write(chunk.toByteArray());out.flush();delay(5)}
                        } else MirrorFrames.write(out,s)
                        val input=DataInputStream(socket.inputStream)
                        var matched=false
                        while(!matched) {
                            val n=input.readInt();require(n in 1..MirrorSnapshot.MAX_BYTES)
                            val b=ByteArray(n);input.readFully(b)
                            val ack=JSONObject(b.toString(Charsets.UTF_8));assertEquals(s.vehicleId,ack.getString("vehicleId"))
                            // The real client can also send a settings update ACK while we await its direct ACK.
                            matched=ack.getLong("ack")==s.revision
                        }
                    }
                    when(mode) {
                        "cachedSwitch" -> {
                            val fixedAt=System.currentTimeMillis()+clockOffsetSeconds*1000L-2000
                            send(MirrorSnapshot(id(car),requireNotNull(cachedRevision),fixedAt,envelope(id(car),"cached-switch").toString()))
                            report("rawHost-cached-switch-complete")
                            delay(2000)
                        }
                        "badLength" -> {out.writeInt(MirrorSnapshot.MAX_BYTES+1);out.flush()}
                        "negativeLength" -> {out.writeInt(-1);out.flush()}
                        "truncated" -> {out.writeInt(1000);out.write(byteArrayOf(1,2,3));out.flush()}
                        "badJson" -> {val b="not-json".toByteArray();out.writeInt(b.size);out.write(b);out.flush()}
                        "badSchema" -> {
                            val b=JSONObject(MirrorSnapshot(id(),100,System.currentTimeMillis(),envelope(id(),"invalid-schema").toString()).encode())
                                .put("schema",99).toString().toByteArray();out.writeInt(b.size);out.write(b);out.flush()
                        }
                        "sequence", "fragment" -> {
                            val frame=MirrorSnapshot(id(),System.currentTimeMillis(),System.currentTimeMillis(),envelope(id(),"raw-$mode-good").toString())
                            val stale=envelope(id(),"raw-stale")
                            val staleRecords=stale.getJSONArray("records")
                            repeat(staleRecords.length()) {n -> staleRecords.getJSONObject(n).remove("cancelled");staleRecords.getJSONObject(n).remove("deleted")}
                            stale.getJSONObject("care").getJSONObject("authority").put("version",1).put("records",staleRecords)
                            send(frame,mode=="fragment");send(frame,mode=="fragment");send(frame.copy(revision=frame.revision-1,content=stale.toString()),mode=="fragment")
                            report("rawHost-sequence-complete",JSONObject().put("mode",mode).put("duplicates",1).put("stale",1).put("acks",3))
                            delay(2000)
                        }
                        "notifications", "blockedNotification" -> {
                            // Explicit synthetic clock domain; personal device clocks and real evidence are unchanged.
                            val fixedAt=System.currentTimeMillis()+clockOffsetSeconds*1000L-2000
                            val values: List<Triple<Int?,Boolean?,String>> = if(mode=="blockedNotification") listOf(Triple(10,null,"blocked-low")) else listOf(
                                Triple(80,null,"aligned-normal"),Triple(10,null,"aligned-low-1"),Triple(10,null,"aligned-low-2"),
                                Triple(80,null,"aligned-normal-again"),Triple(80,true,"aligned-washer-1"),Triple(80,true,"aligned-washer-2"),
                                Triple(80,false,"aligned-washer-clear"),Triple(80,true,"aligned-washer-recur"),
                                Triple(null,null,"aligned-missing-fuel"))
                            values.forEachIndexed {n,value ->
                                val content=envelope(id(car),value.third,value.first,value.second)
                                    .put("fuelAt",fixedAt).put("batteryAt",fixedAt).put("vehicleWarningAt",fixedAt)
                                content.getJSONObject("care").getJSONObject("warnings").optJSONObject("WASHER")?.put("at",fixedAt)
                                send(MirrorSnapshot(id(car),System.currentTimeMillis()+n,fixedAt,content.toString()))
                            }
                            report("rawHost-notifications-complete",JSONObject().put("frames",values.size))
                            delay(5000)
                        }
                        "ev", "fuelEpisode" -> {
                            val fixedAt=System.currentTimeMillis()+clockOffsetSeconds*1000L-2000
                            var serial=System.currentTimeMillis()
                            suspend fun event(percent: Int, message: String, episode: FuelEpisode?=null) {
                                val content=envelope(id(car),message,if(mode=="ev") 80 else percent)
                                    .put("fuelAt",fixedAt).put("batteryAt",fixedAt)
                                if(mode=="ev") {content.getJSONObject("profile").put("fuel","전기차");content.put("battery",percent)}
                                episode?.let {content.getJSONObject("care").put("fuelEpisode",it.json())}
                                send(MirrorSnapshot(id(car),++serial,fixedAt,content.toString()))
                            }
                            if(mode=="ev") {event(80,"ev-normal");event(10,"ev-low");event(10,"ev-low-repeat")}
                            else {
                                val low=FuelRefillPolicy.observe(FuelEpisode(),18,20)
                                event(18,"episode-low",low);event(21,"episode-slosh",low);event(19,"episode-low-repeat",low)
                                val confirmed=FuelRefillPolicy.confirmed(low,35,20,fixedAt,"radio-refill")
                                event(35,"episode-confirmed",confirmed);event(35,"episode-confirmed-repeat",confirmed)
                                event(18,"episode-rearmed-low",FuelRefillPolicy.observe(confirmed,18,20))
                            }
                            report("rawHost-energy-complete",JSONObject().put("mode",mode))
                            delay(5000)
                        }
                    }
                    report("rawHost-attempt",JSONObject().put("mode",mode).put("attempt",attempt+1))
                } finally {socket.close();rawSocket=null}
            }
            report("rawHost-complete",JSONObject().put("mode",mode))
        } finally {timeout.cancel();server.close();rawServer=null}
    }

    companion object {
        const val ACTION="com.vemoring.companion.physicaltest.CONTROL"
        const val HELLO=0x56454D32
    }
}
