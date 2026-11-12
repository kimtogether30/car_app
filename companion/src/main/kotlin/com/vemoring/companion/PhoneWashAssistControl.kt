package com.vemoring.companion

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PhoneWashAssistControl() {
    val context=LocalContext.current
    val running by WashAssistSignals.running.collectAsState()
    val status by WashAssistSignals.status.collectAsState()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {grants ->
        if(grants[Manifest.permission.ACCESS_FINE_LOCATION]==true) PhoneWashAssistService.startVisible(context)
        else WashAssistSignals.status.value="위치 보완에는 정확한 위치 권한이 필요해요"
    }
    Text("세차 위치 보완: ${if(running) status else "꺼짐 · 필요할 때 켜 주세요"}")
    Text("차량에서 시작한 세차장 방문만 보완해요. 차량 재연결 후 출차를 확인하며 실행 중 알림이 표시돼요.")
    OutlinedButton(modifier=Modifier.heightIn(min=48.dp),onClick={
        if(running) PhoneWashAssistService.stop(context)
        else if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
            PhoneWashAssistService.startVisible(context)
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }) {Text(if(running) "세차 위치 보완 끄기" else "세차 위치 보완 켜기")}
    if(!running && status!="세차 위치 보완 꺼짐") Text(status)
}
