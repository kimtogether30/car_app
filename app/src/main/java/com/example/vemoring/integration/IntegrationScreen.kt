package com.example.vemoring.integration

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.vemoring.*
import com.example.vemoring.vehicle.*
import ai.pleos.playground.navi.constants.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun timestamp(value: Long?): String = value?.let { SimpleDateFormat("MM-dd HH:mm:ss",Locale.KOREA).format(Date(it)) } ?: "미수신"
@Composable
fun IntegrationScreen(model: IntegrationViewModel, snapshot: VehicleSnapshot, setMode: (DataMode) -> Unit, data: AppData, back: () -> Unit) {
    val mode by model.mode.collectAsState()
    val point by model.location.collectAsState()
    val environment by model.environment.collectAsState()
    val places by model.places.collectAsState()
    val navi by model.navigation.collectAsState()
    val events by model.logs.collectAsState()
    var parked by remember { mutableStateOf(false) }
    var continuous by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Place?>(null) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var radius by remember { mutableStateOf("300") }
    var plannedWaypoints by remember(mode) { mutableStateOf<List<Place>>(emptyList()) }
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) model.requestLocation(continuous)
    }
    DisposableEffect(model) { model.open(); onDispose { model.closeFeature() } }
    PageColumn {
        Heading("SDK DIAGNOSTICS","연결 및 주변 관리") { TextButton(onClick=back) { Text("닫기") } }
        Text("주차 후 사용하세요. 경로 변경은 확인 후 요청되며 도착만으로 관리 기록을 만들지 않습니다.")
        Row { Checkbox(checked=parked,onCheckedChange={ parked=it }); Text("주차 상태에서 테스트 중입니다") }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DataMode.entries.forEach { value -> FilterChip(selected=mode==value,enabled=parked,onClick={ setMode(value) },label={ Text(value.name) }) }
        }
        Text("현재 출처: ${mode.name} · 차량명/관리 기록: User Input")
        Panel {
            Text("위치: ${point.state} · ${point.detail}")
            Text("출처 ${point.source} · 수신 ${timestamp(point.receivedAt)}")
            point.value?.let { Text("위치 시각 ${timestamp(it.timestamp)} · 최신 여부 ${it.fresh()}") }
            Row { Checkbox(checked=continuous,onCheckedChange={continuous=it}); Text("실시간 위치 사용 (화면 종료·백그라운드에서 중지)") }
            Button(enabled=parked && mode==DataMode.Real,onClick={
                if(context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==PackageManager.PERMISSION_GRANTED) model.requestLocation(continuous)
                else permission.launch("pleos.car.permission.FUSED_LOCATION")
            }) { Text("위치 권한 요청 / 위치 수신") }
            TextButton(onClick={model.stopLocation()}) { Text("위치 업데이트 중지") }
            TextButton(enabled=parked && mode==DataMode.Real,onClick={model.blockedPopup()}) { Text("차량 위치 차단 설정 안내") }
            OutlinedTextField(value=radius,onValueChange={radius=it},label={Text("영역 반경 (m)")},singleLine=true)
            Button(enabled=parked && point.value?.fresh()==true && radius.toFloatOrNull()?.let { it.isFinite() && it>0 }==true && mode==DataMode.Real,
                onClick={model.addArea(radius.toFloat())}) { Text("현재 위치 영역 진입·이탈 등록") }
            TextButton(onClick={model.removeAreas()}) { Text("영역 알림 해제") }
        }
        Panel {
            Text("날씨 / 대기질: ${environment.state} · ${environment.detail}")
            Text("출처 ${environment.source} · ${timestamp(environment.receivedAt)}")
            environment.value?.let { weather ->
                Text("기온 ${weather.temperature ?: "—"} °C · 오늘 강수 예보 ${weather.forecastRainMm ?: "—"} mm")
                Text("PM10 ${weather.pm10 ?: "—"} · PM2.5 ${weather.pm25 ?: "—"} · dust ${weather.dust ?: "—"} μg/m³")
                Text(weather.attribution)
            }
            Button(enabled=parked && environment.state!=ResultState.Loading,onClick={model.weather()}) { Text("Open-Meteo에 위치 전송 · 날씨 조회") }
            Text("강수 예보는 이미 내린 비로 차감하지 않습니다. 관측 강수량 연동은 별도 자료가 필요합니다.")
        }
        Panel {
            Text("주변 관리 장소: ${places.state} · ${places.detail}")
            Text("출처 ${places.source} · ${timestamp(places.receivedAt)}")
            Text("검색 시 현재 위도·경도를 overpass-api.de로 전송합니다. © OpenStreetMap contributors · ODbL")
            PlaceKind.entries.forEach { kind -> OutlinedButton(enabled=parked && places.state!=ResultState.Loading,onClick={model.search(kind)}) { Text(kind.label) } }
            if(places.state==ResultState.Available && places.value.isNullOrEmpty()) Text("검색 결과 없음")
            places.value?.forEach { place ->
                Text("${place.name} · 직선거리 ${place.distanceMeters.toInt()} m")
                Text("${place.address}\n${place.latitude}, ${place.longitude}")
                Button(enabled=parked && mode==DataMode.Real,onClick={ chosen=place; model.queryNavigation() }) { Text("길 안내 선택") }
                TextButton(enabled=parked && mode==DataMode.Real && plannedWaypoints.size<2,onClick={plannedWaypoints=plannedWaypoints+place}) { Text("새 경로의 경유지 후보 담기") }
            }
            Text("새 경로 후보: " + plannedWaypoints.joinToString { it.name })
            TextButton(onClick={plannedWaypoints=emptyList()}) { Text("후보 비우기") }
        }
        Panel {
            Text("Navi: ${navi.connection} · ${navi.event}")
            TextButton(enabled=parked && mode==DataMode.Real,onClick={model.reconnectNavigation()}) { Text("내비 SDK 재연결") }
            Text("수신 ${timestamp(navi.receivedAt)} · 목적지 ${if(navi.destinationKnown) navi.destination ?: "없음" else "조회 미완료"}")
            Text("경유지: " + if(navi.waypointsKnown) navi.waypointNames.joinToString().ifBlank {"없음"} else "조회 미완료")
            Button(enabled=parked && mode==DataMode.Real,onClick={model.queryNavigation()}) { Text("내비 정보 다시 조회") }
            TextButton(enabled=parked && mode==DataMode.Real,onClick={model.navigationScreen()}) { Text("Navigation 화면 열기") }
            TextButton(enabled=parked && mode==DataMode.Real,onClick={confirmation="현재 경로를 재탐색할까요?" to {model.reroute()}}) { Text("재탐색") }
            TextButton(enabled=parked && mode==DataMode.Real,onClick={confirmation="현재 목적지와 경로를 취소할까요?" to {model.cancelRoute()}}) { Text("목적지 취소") }
            RouteOption.entries.forEach { option ->
                TextButton(enabled=parked && mode==DataMode.Real,onClick={confirmation="경로 옵션을 ${option.name}(으)로 변경할까요?" to {model.option(option)}}) { Text(option.name) }
            }
            listOf(WaypointIndex.FIRST,WaypointIndex.SECOND,WaypointIndex.ALL).forEach { index ->
                TextButton(enabled=parked && mode==DataMode.Real,onClick={confirmation="${index.name} 경유지를 제거할까요?" to {model.removeWaypoint(index)}}) { Text("경유지 제거 ${index.name}") }
            }
        }
        MaintenancePanel(data,snapshot,environment,parked && mode==DataMode.Real)
        Panel {
            Text("차량 필드별 진단")
            VehicleField.entries.forEach { field ->
                val reading=snapshot[field]
                Text("${field.label}: ${reading.state} · ${reading.statusText()} · ${reading.reason}")
                Text("원시 수신: ${reading.value?.text ?: "—"} (단위 미확인 수치는 계산 보류)")
                Text("출처 ${if(reading.state==ReadingState.Demo) "Demo" else "Real"} · 수신 ${timestamp(reading.receivedAtMillis)}")
            }
        }
        Panel {
            Text("요청·콜백 로그 (최대 100개, 위치·식별자·키 제외)")
            events.forEach { Text("${timestamp(it.time)} [${it.source}] ${it.operation}: ${it.outcome}") }
        }
    }
    chosen?.let { place ->
        AlertDialog(onDismissRequest={chosen=null},title={Text(place.name)},text={Text(
            "기존 목적지: ${if(navi.destinationKnown) navi.destination ?: "없음" else "미확인"}\n기존 목적지가 있으면 경유지 추가를 선택하세요. 새 경로는 기존 목적지·경유지를 바꿀 수 있습니다."
        )},confirmButton={
            Column {
                TextButton(enabled=parked && navi.destinationKnown && !navi.destination.isNullOrBlank() && navi.waypointsKnown && navi.waypointNames.size<2,onClick={
                    model.navigate(place,true); chosen=null
                }) { Text("기존 목적지 유지 · 경유지 추가 확인") }
                TextButton(enabled=parked,onClick={
                    confirmation="기존 경로를 바꾸고 ${place.name}(으)로 안내를 요청할까요?" to {
                        if(plannedWaypoints.isEmpty()) model.navigate(place,false) else model.routeWithWaypoints(place,plannedWaypoints)
                    }; chosen=null
                }) { Text("새 목적지·후보 경유지로 변경") }
            }
        },dismissButton={TextButton(onClick={chosen=null}){Text("취소")}})
    }
    confirmation?.let { item ->
        AlertDialog(onDismissRequest={confirmation=null},title={Text("경로 변경 확인")},text={Text(item.first)},
            confirmButton={TextButton(enabled=parked,onClick={item.second();confirmation=null}){Text("확인 후 요청")}},
            dismissButton={TextButton(onClick={confirmation=null}){Text("취소")}})
    }
}
