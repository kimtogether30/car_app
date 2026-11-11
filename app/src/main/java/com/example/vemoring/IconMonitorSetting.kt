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
    var receiving by remember {mutableStateOf<Boolean?>(null)}
    var saving by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    if (showGuide) GnbPinGuide { showGuide = false }
    LaunchedEffect(Unit) {
        enabled = withContext(Dispatchers.IO) {
            context.getSharedPreferences(VehicleIconMonitor.PREFS, Context.MODE_PRIVATE)
                .getBoolean(VehicleIconMonitor.ENABLED, true)
        }
        receiving=withContext(Dispatchers.IO) {context.getSharedPreferences(VehicleIconMonitor.PREFS,Context.MODE_PRIVATE).getBoolean(VehicleIconMonitor.RECEIVE_ENABLED,true)}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("다른 화면에서도 차량 정보 받기",fontSize=26.sp)
            Text("앱 화면을 보지 않아도 수신 서비스를 유지해요. 차량·권한·운영체제 상태에 따라 수신이 중단될 수 있어요.",fontSize=19.sp)
        }
        Switch(checked=receiving==true,enabled=receiving!=null && !saving,onCheckedChange={next ->
            saving=true
            scope.launch {
                val saved=withContext(Dispatchers.IO) {context.getSharedPreferences(VehicleIconMonitor.PREFS,Context.MODE_PRIVATE)
                    .edit().putBoolean(VehicleIconMonitor.RECEIVE_ENABLED,next).commit()}
                if(saved) {
                    receiving=next
                    if(next) VehicleIconMonitor.start(context) else context.stopService(Intent(context,VehicleIconMonitor::class.java))
                }
                saving=false
            }
        })
    }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("앱 밖에서 상태 아이콘 갱신", fontSize = 26.sp)
            Text("아이콘 갱신을 꺼도 위의 차량 정보 수신 설정은 유지돼요.", fontSize = 19.sp)
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
                    if (receiving==true) VehicleIconMonitor.start(context)
                }
                saving = false
            }
        })
    }
    }
}
