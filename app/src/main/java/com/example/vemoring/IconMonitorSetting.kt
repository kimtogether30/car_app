package com.example.vemoring

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IconMonitorSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    if (showGuide) GnbPinGuide { showGuide = false }
    LaunchedEffect(Unit) {
        enabled = withContext(Dispatchers.IO) {
            context.getSharedPreferences(VehicleIconMonitor.PREFS, Context.MODE_PRIVATE)
                .getBoolean(VehicleIconMonitor.ENABLED, true)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("앱 밖에서도 상태 아이콘 갱신", fontSize = 26.sp)
            Text("다른 앱을 쓰는 동안에도 차량 상태를 확인해요.", fontSize = 19.sp)
            TextButton(onClick = { showGuide = true }, modifier = Modifier.heightIn(min = 64.dp)) {
                Text("하단 바에 추가하는 방법", fontSize = 21.sp)
            }
        }
        Switch(checked = enabled == true, enabled = enabled != null && !saving, onCheckedChange = { next ->
            saving = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    context.getSharedPreferences(VehicleIconMonitor.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(VehicleIconMonitor.ENABLED, next).commit()
                }
                if (saved) {
                    enabled = next
                    if (next) VehicleIconMonitor.start(context)
                    else context.stopService(Intent(context, VehicleIconMonitor::class.java))
                }
                saving = false
            }
        })
    }
}
