package com.example.vemoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.vemoring.integration.MaintenanceConfig
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Debug APK only. Memory-only samples, never persisted and never sent to VHAL. */
internal object IconMonitorTestSource {
    val input = MutableStateFlow<IconMonitorSample?>(null)
}

class IconMonitorPreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stage = intent.getIntExtra("stage", -1)
        if (stage !in 0..5) { IconMonitorTestSource.input.value = null; return }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val saved = GarageStore(context).load()
                val values = listOf(Triple(80, 100, false), Triple(10, 100, false),
                    Triple(80, 20, false), Triple(80, 100, true),
                    Triple(10, 20, false), Triple(10, 20, true))
                val (fuel, clean, oilDue) = values[stage]
                val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
                val wash = CareRecord("preview-wash", today.minusDays((100-clean)/2L), CareKind.WASH, "아이콘 테스트")
                val data = saved.copy(profile = saved.profile.copy(fuel = "휘발유", fuelAlert = 20, washAlert = 30), records = listOf(wash), complete = true)
                val snapshot = VehicleSnapshot(VehicleSnapshot().readings + mapOf(
                    VehicleField.FUEL to Reading.percent(fuel),
                    VehicleField.ODOMETER to Reading.available(SignalValue("15000", 15000.0))))
                IconMonitorTestSource.input.value = IconMonitorSample(data, snapshot,
                    previewOilConfig(oilDue,today))
            } finally { pending.finish() }
        }
    }
}

internal fun previewOilConfig(due:Boolean,today:LocalDate)=MaintenanceConfig(
    oilRule=com.vemoring.companion.EngineOilRule("preview","현대 아반떼","휘발유",5000.0,6,"isolated fixture"),
    oilLedger=org.json.JSONObject().put("enrolled",today.toString()).put("initialIds",org.json.JSONArray())
        .put("initialDate",(if(due) today.minusMonths(6) else today).toString()).put("anchorKm",15000).put("currentKm",15000))
