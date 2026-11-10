package com.vemoring.companion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@Composable fun BluetoothLinkDialog(host: Boolean, dismiss: () -> Unit) {
    val context = LocalContext.current
    val link by MirrorBus.link.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var devices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var message by remember { mutableStateOf("기기를 확인하고 있어요…") }
    var allowed by remember { mutableStateOf(MirrorBluetoothService.hasConnectPermission(context)) }
    val bodySize = if (host) 22.sp else 16.sp
    val actionSize = if (host) 21.sp else 14.sp
    val actionHeight = if (host) 64.dp else 48.dp
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it; refresh++ }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    LaunchedEffect(allowed, refresh) {
        if (!allowed) { message = "페어링한 기기에 연결하려면 주변 기기 권한이 필요해요."; return@LaunchedEffect }
        val result = withContext(Dispatchers.IO) { runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            when {
                adapter == null -> emptyList<Pair<String, String>>() to "이 기기는 블루투스를 지원하지 않아요."
                !adapter.isEnabled -> emptyList<Pair<String, String>>() to "시스템 설정에서 블루투스를 켜주세요."
                else -> adapter.bondedDevices.map { (it.name ?: "이름 없는 기기") to it.address }.sortedBy { it.first } to
                    "시스템 설정에서 페어링한 ${if (host) "휴대폰" else "차량"}을 선택해 주세요."
            }
        } }
        result.onSuccess { devices = it.first; message = it.second }.onFailure { message = "주변 기기 권한과 블루투스 설정을 확인해 주세요." }
    }
    AlertDialog(onDismissRequest = dismiss,
        modifier = Modifier.widthIn(max = if (host) 760.dp else 560.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = !host),
        title = { Text(if (host) "휴대폰 연결" else "차량 데이터 연결", fontSize = if (host) 28.sp else 24.sp) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (host && !link.running && link.message == "차량 미연결") "휴대폰 미연결" else link.message, fontSize = bodySize)
            Text(if (host) "선택한 휴대폰에 차량 프로필·기록·컨디션·캐릭터 상태를 전송해요. 차량 앱 화면을 열어두면 변경 사항을 반영해요."
                else "차량 앱에서 휴대폰 연결을 켜고 이 휴대폰을 선택해 주세요. 연결이 끊기면 마지막으로 받은 화면 데이터를 유지해요.", fontSize = bodySize, lineHeight = if (host) 32.sp else 24.sp)
            Text(message, fontSize = bodySize)
            if (!allowed && Build.VERSION.SDK_INT >= 31) Button(onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }, modifier = Modifier.heightIn(min = actionHeight)) { Text("주변 기기 권한 허용", fontSize = actionSize) }
            OutlinedButton(onClick = { settings.launch(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }, modifier = Modifier.heightIn(min = actionHeight)) { Text("블루투스 설정 열기", fontSize = actionSize) }
            devices.forEach { (name, address) -> OutlinedButton(onClick = {
                runCatching { MirrorBluetoothService.start(context, host, address) }.onFailure { message = "연결 서비스를 시작하지 못했어요. 다시 시도해 주세요." }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = actionHeight)) { Text("$name · ${address.takeLast(5)}", fontSize = actionSize) } }
            if (devices.isEmpty() && allowed) Text("페어링한 기기가 없으면 블루투스 설정에서 먼저 페어링해 주세요.", fontSize = bodySize)
            if (link.running) TextButton(onClick = { context.stopService(Intent(context, MirrorBluetoothService::class.java)) }, modifier = Modifier.heightIn(min = actionHeight)) { Text("연결 종료", fontSize = actionSize) }
            Text("일반 오디오 연결만으로 데이터가 전송되지는 않아요. 양쪽 베모링 앱의 데이터 연결이 필요해요.", fontSize = if (host) 19.sp else 12.sp, lineHeight = if (host) 28.sp else 18.sp)
        }
    }, confirmButton = { TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = actionHeight)) { Text("닫기", fontSize = actionSize) } })
}
