package com.vemoring.companion

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Called on IO. Weather preferences are independent of garage receiving/selection settings. */
internal class ForecastStore(context: Context, vehicleId: String) {
    private val preferences=context.getSharedPreferences("weekly_forecast_v1",Context.MODE_PRIVATE)
    private val prefix=java.security.MessageDigest.getInstance("SHA-256").digest(vehicleId.toByteArray())
        .joinToString("") {"%02x".format(it)}+":"
    fun read(): Pair<ForecastRegion?,WeeklyWeatherForecast?> {
        val region=preferences.getString(prefix+"region",null)?.let {runCatching {ForecastRegion.from(JSONObject(it))}.getOrNull()}
        val cache=preferences.getString(prefix+"forecast",null)?.let {runCatching {ForecastCodec.decode(it)}.getOrNull()}
        return region to cache?.takeIf {region != null && it.latitude==region.latitude && it.longitude==region.longitude}
    }
    fun region(value: ForecastRegion) {check(preferences.edit().putString(prefix+"region",value.json().toString()).commit())}
    fun forecast(value: WeeklyWeatherForecast) {check(preferences.edit().putString(prefix+"forecast",ForecastCodec.encode(value)).commit())}
}

@Composable
fun ForecastWeatherPanel(vehicleId: String, vehicle: Boolean = false, enabled: Boolean = true,
    locate: (suspend () -> TravelPoint?)? = null) {
    key(vehicleId,enabled) { ForecastWeatherContent(vehicleId,vehicle,enabled,locate) }
}

@Composable
private fun ForecastWeatherContent(vehicleId: String,vehicle: Boolean,enabled: Boolean,locate: (suspend () -> TravelPoint?)?) {
    val context=LocalContext.current
    val latestLocate by rememberUpdatedState(locate)
    val repository=remember {WeeklyWeatherRepository()}
    var store by remember {mutableStateOf<ForecastStore?>(null)}
    var region by remember {mutableStateOf<ForecastRegion?>(null)}
    var forecast by remember {mutableStateOf<WeeklyWeatherForecast?>(null)}
    var loaded by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var showDetails by remember {mutableStateOf(false)}
    var refresh by remember {mutableIntStateOf(0)}
    var consumedRefresh by remember {mutableIntStateOf(0)}
    var lastManualAt by remember {mutableLongStateOf(0)}
    var now by remember {mutableStateOf(Instant.now())}
    var located by remember {mutableStateOf(false)}
    var permissionAttempted by remember {mutableStateOf(false)}
    // Weather uses a ~5 km grid, so the phone asks for approximate location only. Requesting FINE as well is what
    // makes Android 12+ show the "precise / approximate" choice; asking for COARSE alone shows a single question.
    val permissions=remember(vehicle) {if(vehicle) arrayOf("pleos.car.permission.FUSED_LOCATION") else
        arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)}
    fun allowed()=permissions.any {context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    var permitted by remember {mutableStateOf(allowed())}
    val permissionRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted=allowed();refresh++
    }
    val owner=remember(context) {context.lifecycleOwner()}
    var active by remember(owner) {mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED)==true)}
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,_ -> active=owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED)==true; now=Instant.now();permitted=allowed()}
        owner?.lifecycle?.addObserver(observer)
        onDispose {owner?.lifecycle?.removeObserver(observer)}
    }
    LaunchedEffect(vehicleId,enabled) {
        if(enabled) {
            val (next,values)=withContext(Dispatchers.IO) {val next=ForecastStore(context.applicationContext,vehicleId); next to next.read()}
            store=next
            // A previously selected fixed city is not the current location.
            region=values.first?.takeIf {it.name=="현재 위치"}
            forecast=values.second.takeIf {region != null}
        }
        loaded=true
    }
    LaunchedEffect(active,loaded,enabled) {
        if(active && loaded && enabled && !permitted && !permissionAttempted) {
            permissionAttempted=true;permissionRequest.launch(permissions)
        }
    }
    LaunchedEffect(active,refresh,loaded,enabled,permitted) {
        if(!enabled || !loaded || !active) return@LaunchedEffect
        if(!permitted) {located=false;return@LaunchedEffect}
        try {
            while(isActive) {
                now=Instant.now()
                val force=refresh!=consumedRefresh
                consumedRefresh=refresh
                loading=true
                // Keep the last current-location forecast on screen while re-checking (no blank flash on every
                // screen return); it is hidden only if this attempt fails to get a location.
                var gotLocation=false
                try {
                    val point=latestLocate?.let {it.invoke()} ?: if(latestLocate==null && !vehicle) phoneWeatherLocation(context) else null
                    val selected=point?.let {ForecastLocationPolicy.region(it,Instant.now())}
                    if(selected==null) throw IllegalStateException("location unavailable")
                    if(region!=selected) forecast=null
                    if(region!=selected) withContext(Dispatchers.IO) {store?.region(selected)}
                    region=selected;located=true;gotLocation=true
                    if(force || ForecastRefreshPolicy.due(forecast,selected,Instant.now())) {
                        val next=repository.fetch(selected.latitude,selected.longitude)
                        withContext(Dispatchers.IO) {store?.forecast(next)}
                        ensureActive();forecast=next;error=null;now=Instant.now()
                    } else error=null
                } catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {
                    if(!gotLocation) located=false
                    error=if(gotLocation) "날씨 갱신 실패 · 마지막 정보를 표시해요." else "현위치를 받지 못했어요. 위치 설정을 확인하고 새로고침해 주세요."
                }
                finally {loading=false}
                val wait=if(error != null) ForecastRefreshPolicy.RETRY_MILLIS
                    else forecast?.let {ForecastRefreshPolicy.waitMillis(it,Instant.now())} ?: ForecastRefreshPolicy.RETRY_MILLIS
                delay(wait)
            }
        } finally {loading=false}
    }
    val displayed=forecast.takeIf {permitted && located}
    val suggestion=remember(displayed,now) {WashDatePolicy.suggest(displayed,now)}
    val stamp=forecast?.fetchedAt?.atZone(FORECAST_ZONE)?.format(STAMP_FORMAT)
    val stale=forecast?.let {region?.let {r -> ForecastRefreshPolicy.due(it,r,now)}}==true
    WeeklyWeather(forecasts=displayed?.days?.map {it.forDisplay()} ?: emptyList(),
        locationName=when {
            !enabled -> "예시 모드 · 날씨 조회 안 함"
            !permitted -> "현위치 날씨 · 위치 권한 필요"
            loading && !located -> "현위치 확인 중"
            !located -> "현위치 미수신 · 위치 설정 확인"
            else -> "현재 위치"+if(stale || error != null) " · 갱신 필요" else ""
        },vehicle=vehicle,
        headerActions={
            if(!permitted) TextButton(enabled=enabled && loaded,onClick={permissionAttempted=true;permissionRequest.launch(permissions)},
                modifier=Modifier.heightIn(min=if(vehicle) 64.dp else 48.dp),contentPadding=PaddingValues(horizontal=6.dp)) {
                Text("위치 허용",fontSize=if(vehicle) 18.sp else 12.sp)
            }
            TextButton(enabled=enabled && permitted && !loading,onClick={
                val at=android.os.SystemClock.elapsedRealtime()
                if(at-lastManualAt>=15000 || lastManualAt==0L) {lastManualAt=at;refresh++}
            },modifier=Modifier.heightIn(min=if(vehicle) 64.dp else 48.dp),contentPadding=PaddingValues(horizontal=if(vehicle) 12.dp else 6.dp)) {
                Text(if(loading) "갱신 중" else "새로고침",fontSize=if(vehicle) 18.sp else 12.sp)
            }
        },footer={
            TextButton(onClick={showDetails=true},contentPadding=PaddingValues(0.dp),modifier=Modifier.fillMaxWidth().heightIn(min=if(vehicle) 48.dp else 48.dp)) {
                Text(suggestion.message,fontSize=if(vehicle) 18.sp else 12.sp,lineHeight=if(vehicle) 24.sp else 17.sp,
                    modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("기준 ›",fontSize=if(vehicle) 16.sp else 11.sp)
            }
        })
    if(showDetails) AlertDialog(onDismissRequest={showDetails=false},title={Text("날씨와 세차 추천")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("현재 위치 기준 · 위치 좌표를 Open-Meteo에 보내 예보를 조회해요.")
            Text(if(stamp==null) "아직 받은 예보가 없어요." else "마지막 수신 $stamp${if(stale) " · 갱신 필요" else ""}")
            error?.let {Text(it)}
            if(!vehicle) TextButton(onClick={
                val intent=if(!permitted) android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")) else android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                runCatching {context.startActivity(intent)}
            }) {Text("위치 설정 열기")}
            Text(suggestion.message)
            Text("당일과 다음 날 모두 강수확률 30% 이하, 총강수량 0.5mm 미만, 강설량 0cm인 가장 빠른 날짜를 추천해요. 최저 0℃ 이하·최고 35℃ 이상과 누락된 예보는 제외해요.")
            Text("6시간 넘게 갱신되지 않은 예보로는 추천하지 않아요. 7일째는 다음 날 자료가 없어 후보에서 제외해요. 앱의 참고용 기준이며 세차 기록이나 청결도는 바꾸지 않아요.")
            Text("휴대폰은 휴대폰 위치, 차량은 수신한 차량 위치를 사용해요. 화면 복귀·1시간 자동 갱신·새로고침 때 현위치를 다시 확인해요. 약 0.05도 구역의 예보를 사용하며 이동 경로는 추적하지 않아요. 위치가 없으면 임의 지역 예보를 표시하지 않아요. 실패하면 화면이 보이는 동안 10분 뒤 재시도해요.")
            Text("날씨 제공: Open-Meteo · CC BY 4.0\nopen-meteo.com · 모델 예보이며 현장 관측이 아니에요. 표시 기온은 일 최고 기온이에요.")
        }
    },confirmButton={TextButton(onClick={showDetails=false}) {Text("확인")}})
}

private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

private fun Context.lifecycleOwner(): LifecycleOwner? {
    var current: Context=this
    while(true) {
        if(current is LifecycleOwner) return current
        if(current !is ContextWrapper || current.baseContext===current) return null
        current=current.baseContext
    }
}

