package com.example.vemoring

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.example.vemoring.vehicle.*
import com.example.vemoring.integration.DataMode
import com.example.vemoring.integration.vehicleCareId
import com.vemoring.companion.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Export exactly the semantic values shown on the vehicle, including unknown/demo status. */
internal fun mirrorContent(data: AppData, snapshot: VehicleSnapshot, condition: CareCondition, mood: MascotMood, demo: Boolean): String {
    val p = data.profile
    return JSONObject().put("profile", JSONObject().put("nickname", p.nickname).put("model", p.fullModel)
        .put("plate", p.plate).put("fuel", p.fuel).put("oilEstimate",p.oilEstimate).put("oilBaselineKm",p.oilBaselineKm).put("notifications", p.notifications)
        .put("washAlert", p.washAlert).put("fuelAlert", p.fuelAlert).put("vehicleId",p.vehicleId))
        .put("records", JSONArray().also { a -> data.records.forEach { a.put(it.json()) } })
        .put("fuel", snapshot[VehicleField.FUEL].realPercent() ?: JSONObject.NULL)
        .put("battery", snapshot[VehicleField.SOC].realPercent() ?: JSONObject.NULL)
        .put("fuelAt",snapshot[VehicleField.FUEL].receivedAtMillis ?: JSONObject.NULL)
        .put("batteryAt",snapshot[VehicleField.SOC].receivedAtMillis ?: JSONObject.NULL)
        .put("cleanliness", condition.cleanliness ?: JSONObject.NULL)
        .put("oilRemainingKm", condition.oilRemainingKm ?: JSONObject.NULL)
        .put("signals", JSONObject().put("lowFuel", condition.signals.lowFuel)
            .put("oilDue", condition.signals.oilDue))
        .put("fuelStatus", snapshot[VehicleField.FUEL].statusText())
        .put("batteryStatus", snapshot[VehicleField.SOC].statusText())
        .put("mood", mood.name).put("demo", demo)
        .put("message", if (demo) "예시 데이터로 보는\n캐릭터예요." else
            mood.message).toString()
}

@Composable internal fun PublishVehicleMirror(data: AppData, loaded: Boolean, snapshot: VehicleSnapshot, condition: CareCondition, mood: MascotMood, mode: DataMode) {
    val context = LocalContext.current.applicationContext
    val fuel = snapshot[VehicleField.FUEL]
    val battery = snapshot[VehicleField.SOC]
    val washRevision by VehicleWashRuntime.revision.collectAsState()
    LaunchedEffect(data, loaded, condition.cleanliness, condition.oilRemainingKm, mood, mode,
        // Whole fuel reading (incl. receive time): a refuel confirmation read with an unchanged value must still publish.
        fuel, battery.realPercent(), battery.statusText(),
        snapshot[VehicleField.ODOMETER], snapshot[VehicleField.IGNITION], snapshot.motion(),snapshot[VehicleField.GEAR],washRevision) {
        if (loaded && data.complete) {
            try {
                val care = VehicleCareRuntime.payload(context, data, snapshot, mode == DataMode.Demo)
                val content = withContext(Dispatchers.IO) { JSONObject(mirrorContent(data, snapshot, condition, mood, mode == DataMode.Demo))
                    .put("care", care).toString() }
                MirrorBus.publish(context, content, vehicleCareId(data.profile))
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { MirrorBus.link.value = MirrorBus.link.value.copy(active = false,
                message = "화면 데이터를 준비하지 못했어요. 기록 크기와 저장 공간을 확인해 주세요.") }
        }
    }
}

@Composable internal fun PhoneConnectionCard() {
    var show by remember { mutableStateOf(false) }
    val link by MirrorBus.link.collectAsState()
    val locationMessage by SharedVehicleConnection.locationMessage.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("휴대폰 연결", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(if (!link.running && link.message == "차량 미연결") "휴대폰 미연결" else link.message, fontSize = 19.sp, lineHeight = 28.sp)
        Text(locationMessage, fontSize = 19.sp, lineHeight = 28.sp)
        OutlinedButton(onClick = { show = true }, modifier = Modifier.heightIn(min = 64.dp)) {
            Text("블루투스로 휴대폰 연결", fontSize = 21.sp)
        }
    }
    if (show) BluetoothLinkDialog(host = true, dismiss = { show = false })
}
