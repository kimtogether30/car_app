package com.example.vemoring

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vemoring.integration.MaintenanceConfig
import com.example.vemoring.vehicle.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Debug-only, read-only preview. Uses the real condition mapper without modifying VHAL or saved garage data. */
class StatePreviewActivity : ComponentActivity() {
    private var stage by mutableIntStateOf(0)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stage = intent.getIntExtra("stage", 0).coerceIn(0, 5)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stage = intent.getIntExtra("stage", 0).coerceIn(0, 5)
        setContent {
            var saved by remember { mutableStateOf(AppData()) }
            LaunchedEffect(Unit) { saved = withContext(Dispatchers.IO) { GarageStore(applicationContext).load() } }
            val values = listOf(
                Triple(80, 100, false), Triple(10, 100, false), Triple(80, 20, false),
                Triple(80, 100, true), Triple(10, 20, false), Triple(10, 20, true))
            val labels = listOf("정상", "연료 부족", "세차 필요", "엔진오일 교체 시기", "두 가지 문제", "세 가지 문제")
            val (fuel, clean, oilDue) = values[stage]
            val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
            val wash = CareRecord("preview-wash", today.minusDays((100-clean)/2L), CareKind.WASH, "시뮬레이션 세차")
            val data = saved.copy(profile = saved.profile.copy(fuel = "휘발유", fuelAlert = 20, washAlert = 30), records = listOf(wash), complete = true)
            val snapshot = VehicleSnapshot(VehicleSnapshot().readings + mapOf(
                VehicleField.FUEL to Reading.percent(fuel),
                VehicleField.ODOMETER to Reading.available(SignalValue("15000", 15000.0))))
            val config = previewOilConfig(oilDue,today)
            val condition = careCondition(data, snapshot, config)
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography, colorScheme = GarageColors) {
                Scaffold(topBar = {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                            Text("시뮬레이션 · ${labels[stage]} · 연료 $fuel% / 청결 ${condition.cleanliness}% / 오일 교체 시기 ${if(oilDue) "ON" else "OFF"}",
                                Modifier.weight(1f))
                            TextButton(onClick = { stage = (stage + 1) % 6 }) { Text("다음 상태") }
                            TextButton(onClick = { finish() }) { Text("실제 화면으로") }
                        }
                    }
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        HomeScreen(data, BatteryReading(), {}, condition.signals.mood(), snapshot, condition)
                    }
                }
            }
        }
    }
}
