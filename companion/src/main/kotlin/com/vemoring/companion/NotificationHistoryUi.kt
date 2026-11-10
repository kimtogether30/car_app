package com.vemoring.companion

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Bounded (12 MB, about 8 banners) so scrolling back to a row does not decode the same PNG again. */
private object ArtworkCache : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

/** Images decode on IO and only for composed rows. No animation, polling, or bitmap copies on recomposition. */
@Composable
fun BannerArtwork(art: String?, description: String, modifier: Modifier = Modifier, mascot: Boolean = false) {
    val context=LocalContext.current.applicationContext
    // Match the enclosing app theme, including dialogs and a user-selected theme override.
    val dark=MaterialTheme.colorScheme.surface.luminance()<.5f
    val name=if(mascot) art else art+if(dark) "_dark" else "_light"
    val cacheKey="$name@${if(mascot) 4 else 1}"
    val bitmap by produceState(ArtworkCache.get(cacheKey),cacheKey) {
        ArtworkCache.get(cacheKey)?.let { value=it; return@produceState }
        value=withContext(Dispatchers.IO) {
            val id=context.resources.getIdentifier(name,"drawable",context.packageName)
            if(id==0) null else BitmapFactory.decodeResource(context.resources,id,BitmapFactory.Options().apply {inSampleSize=if(mascot) 4 else 1})
                ?.also {ArtworkCache.put(cacheKey,it)}
        }
    }
    if(bitmap!=null) Image(bitmap!!.asImageBitmap(),description,modifier)
    else Box(modifier)
}

@Composable
fun NotificationHistoryDialog(vehicleId: String? = null,initialSelection: Pair<String,String>? = null,onDismiss: () -> Unit,permission: @Composable () -> Unit = {}) {
    val context=LocalContext.current.applicationContext
    val revision by NotificationHistory.revision.collectAsState()
    val scope=rememberCoroutineScope()
    var offset by remember(vehicleId) {mutableIntStateOf(0)}
    var selected by remember(initialSelection) {mutableStateOf(initialSelection)}
    var loadError by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    var failure by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val events by produceState<List<CareNotificationEntry>>(emptyList(),vehicleId,revision,offset,retry) {
        runCatching {withContext(Dispatchers.IO) {NotificationHistory(context).use {it.recent(vehicleId,50,offset)}}}
            .onSuccess {value=it;loadError=null}.onFailure {loadError="알림 내역을 불러오지 못했어요."}
    }
    val detail by produceState<CareNotificationEntry?>(null,selected,revision) {
        val key=selected
        value=if(key==null) null else withContext(Dispatchers.IO) {NotificationHistory(context).use {it.get(key.first,key.second)}}
    }
    LaunchedEffect(initialSelection) {initialSelection?.let { e -> withContext(Dispatchers.IO) {NotificationHistory(context).use {it.markRead(e.first,e.second)}} }}
    Dialog(onDismiss,DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=640.dp).fillMaxWidth().fillMaxHeight(.9f).padding(16.dp),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("알림 내역",style=MaterialTheme.typography.headlineSmall)
                permission()
                if(loadError!=null) {Text(loadError!!);TextButton(onClick={retry++}) {Text("다시 시도")}}
                else if(events.isEmpty()) Text("아직 저장된 알림이 없어요.")
                LazyColumn(Modifier.weight(1f).testTag("notification-history"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(events,key={"${it.vehicleId}:${it.eventId}"}) { e ->
                        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,
                            modifier=Modifier.fillMaxWidth().clickable {
                                selected=e.vehicleId to e.eventId
                                scope.launch {withContext(Dispatchers.IO) {NotificationHistory(context).use {it.markRead(e.vehicleId,e.eventId)}}}
                            }) {
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                val time=remember(e.createdAt) {Instant.ofEpochMilli(e.createdAt).atZone(CARE_ZONE).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))}
                                Text("$time${if(!e.read) " · 새 알림" else ""}${if(e.source=="Simulation") " · 시뮬레이션" else ""}",fontSize=12.sp)
                                if(e.art!=null && LocalConfiguration.current.fontScale < 1.25f)
                                    BannerArtwork(e.art,"${e.title} ${e.body}",Modifier.fillMaxWidth().aspectRatio(1080f/340f).testTag("banner-art:${e.bannerKey}"))
                                else {Text(e.title,style=MaterialTheme.typography.titleMedium);Text(e.body,style=MaterialTheme.typography.bodyMedium)}
                                Text(if(e.cancelled) "잘못 인식함 · 취소됨" else deliveryLabel(e.delivery),fontSize=12.sp)
                            }
                        }
                    }
                    if(events.size==50) item {TextButton(onClick={offset+=50}) {Text("이전 알림 보기")}}
                    if(offset>0) item {TextButton(onClick={offset=(offset-50).coerceAtLeast(0)}) {Text("더 최근 알림 보기")}}
                }
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("닫기")}
            }
        }
    }
    detail?.let { e ->
        AlertDialog(onDismissRequest={selected=null},title={Text(e.title)},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(e.body)
                Text(if(e.cancelled) "취소된 세차 추정이에요. 이전 기준으로 재계산됐어요." else deliveryLabel(e.delivery))
                if(e.source=="Simulation") Text("시험 입력으로 생성한 알림이에요. 실제 차량 수신을 뜻하지 않아요.")
                failure?.let {Text(it)}
            }
        },confirmButton={TextButton(onClick={selected=null}) {Text("확인")}},dismissButton={
            if(e.bannerKey=="WASH_QUESTION" && e.visitId!=null && !e.cancelled) TextButton(enabled=!busy,onClick={
                busy=true
                scope.launch {
                    try {if(!CareExchange.cancel(context,e.vehicleId,e.visitId)) failure="취소 가능한 기간이 지났거나 이미 처리된 기록이에요."}
                    catch(_:Exception) {failure="취소를 저장하지 못했어요. 다시 시도해 주세요."}
                    finally {busy=false}
                }
            }) {Text("잘못 인식함")}
        })
    }
}

private fun deliveryLabel(value: NotificationDelivery): String = when(value) {
    NotificationDelivery.Delivered -> "시스템 알림 게시됨"
    NotificationDelivery.PermissionBlocked -> "알림 권한 꺼짐 · 내역에 저장됨"
    NotificationDelivery.ChannelBlocked -> "알림 채널 꺼짐 · 내역에 저장됨"
    NotificationDelivery.Disabled -> "앱 알림 설정 꺼짐 · 내역에 저장됨"
    NotificationDelivery.Error -> "시스템 알림 게시 실패 · 내역에 저장됨"
    NotificationDelivery.Pending -> "알림 준비 중"
}
