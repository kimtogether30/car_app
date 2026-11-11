package com.example.vemoring

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.runtime.saveable.rememberSaveable

private enum class NearbyPlace(val label: String, val description: String, val color: Color) {
    FUEL("주유소", "주유할 곳이 필요할 때, 주변에서 찾아보세요", Blue),
    WASH("세차장", "깨끗한 드라이브를 위한 가까운 세차장", Mint),
    REPAIR("정비소", "차량 점검이 필요할 때, 가까운 정비소", Navy)
}

/** Three on-demand pages; no location polling, map instance, or automatic slideshow. */
@Composable
fun NearbyPlacesBanner(compact: Boolean = false, slim: Boolean = false, onSearch: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val pager = rememberPagerState(pageCount = { NearbyPlace.entries.size })
    val scope = rememberCoroutineScope()
    var searchError by remember { mutableStateOf<String?>(null) }
    var pendingSearch by rememberSaveable { mutableStateOf<String?>(null) }
    val pleos = remember(context) { PleosPlaceSearch(context) { searchError = it } }
    DisposableEffect(pleos) { onDispose { pleos.close() } }
    fun search(label: String) {
        if (onSearch != null) {
            onSearch(label)
            return
        }
        pendingSearch = label
    }
    if(slim) com.vemoring.companion.NearbyQuickLinks(vehicle = true, onSearch = ::search)
    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(),
            key = { NearbyPlace.entries[it].name }) { page ->
            val place = NearbyPlace.entries[page]
            Panel(Modifier.heightIn(min = if (compact) 124.dp else 180.dp).clickable { search(place.label) }
                .semantics { contentDescription = "가까운 ${place.label} 검색" }, place.color, compact) {
                Text("내 주변 찾기", color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp)
                Text("가까운 ${place.label} 찾기", color = Color.White,
                    fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                if (!compact) Text(place.description, color = Color.White, fontSize = 20.sp, lineHeight = 28.sp)
                Text("지도에서 검색하기  ›", color = Color.White, fontSize = 22.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = pager.currentPage > 0,
                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp)
                    .semantics { contentDescription = "이전 장소 배너" }) { Text("‹", fontSize = 32.sp) }
            Text("${pager.currentPage + 1} / ${NearbyPlace.entries.size} · 좌우로 넘겨보세요",
                fontSize = 18.sp, color = Blue)
            TextButton(enabled = pager.currentPage < NearbyPlace.entries.lastIndex,
                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(NearbyPlace.entries.lastIndex)) } },
                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp)
                    .semantics { contentDescription = "다음 장소 배너" }) { Text("›", fontSize = 32.sp) }
        }
    }
    pendingSearch?.let { keyword ->
        NavigationAppPicker(keyword, { pendingSearch = null }) { app ->
            try {
                if (app == NavigationApp.PLEOS) pleos.search(keyword)
                else context.startActivity(app.searchIntent(keyword, context.packageName))
                pendingSearch = null
            } catch (_: TimeoutCancellationException) {
                searchError = "차량 내비게이션 연결이 지연되고 있어요. 잠시 후 다시 시도해 주세요."
                pendingSearch = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ActivityNotFoundException) {
                searchError = "선택한 지도 앱을 열 수 없어요. 이 차량에 설치되어 있는지 확인해 주세요."
                pendingSearch = null
            } catch (_: Exception) {
                searchError = "지도 검색에 연결하지 못했어요. 내비게이션 연결 상태를 확인해 주세요."
                pendingSearch = null
            }
        }
    }
    searchError?.let { message -> AlertDialog(onDismissRequest = { searchError = null },
        title = { Text("장소 검색") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { searchError = null }) { Text("확인") } }) }
}

@Composable
fun NavigationAppPicker(keyword: String, dismiss: () -> Unit,
    availableOverride: Set<NavigationApp>? = null,
    select: suspend (NavigationApp) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember(keyword, availableOverride) { mutableStateOf(availableOverride) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(keyword, availableOverride) {
        if (availableOverride == null) available = withContext(Dispatchers.IO) {
            NavigationApp.entries.filter { it.isAvailable(context, keyword) }.toSet()
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text("${keyword} 검색 · 지도 앱 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("앱을 선택하면 ‘${keyword}’ 검색 결과를 바로 열어요.", fontSize = 20.sp)
                if (available == null) {
                    Text("설치된 지도 앱을 확인하고 있어요…", fontSize = 20.sp)
                } else if (available!!.isEmpty()) {
                    Text("이 차량에서 사용할 수 있는 지도 앱이 없어요.", fontSize = 20.sp)
                }
                NavigationApp.entries.filter { available?.contains(it) == true }.forEach { app ->
                    OutlinedButton(enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                        onClick = {
                            busy = true
                            scope.launch { try { select(app) } finally { busy = false } }
                        }) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(app.title, fontSize = 24.sp)
                        }
                    }
                }
                if (busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("검색 연결 중…", fontSize = 20.sp)
                }
            }
        }, confirmButton = {}, dismissButton = {
            TextButton(enabled = !busy, onClick = dismiss) { Text("취소") }
        })
}
