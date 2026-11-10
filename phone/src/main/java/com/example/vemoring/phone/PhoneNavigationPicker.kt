package com.example.vemoring.phone

import android.content.Context
import android.content.Intent
import com.vemoring.companion.SupportedMapApp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PhoneMapApp(val name: String, val intent: Intent)

internal fun phoneMapIntent(provider: String, keyword: String, caller: String): Intent {
    return requireNotNull(SupportedMapApp.entries.firstOrNull { it.packageName == provider }) { "Unsupported map provider" }
        .searchIntent(keyword, caller)
}

internal fun installedPhoneMaps(context: Context, keyword: String): List<PhoneMapApp> {
    val pm = context.packageManager
    return SupportedMapApp.entries.mapNotNull { provider ->
        val intent = provider.searchIntent(keyword, context.packageName)
        val available = runCatching { pm.getApplicationInfo(provider.packageName, 0).enabled && intent.resolveActivity(pm) != null }.getOrDefault(false)
        if (available) PhoneMapApp(provider.title, intent) else null
    }
}

@Composable internal fun PhoneNavigationPicker(keyword: String, close: () -> Unit) {
    val context = LocalContext.current
    var apps by remember(keyword) { mutableStateOf<List<PhoneMapApp>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(keyword) { apps = withContext(Dispatchers.IO) { installedPhoneMaps(context, keyword) } }
    AlertDialog(onDismissRequest = close, title = { Text("$keyword 검색 · 지도 앱 선택") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("앱을 선택하면 ‘$keyword’ 검색 결과를 바로 열어요.")
            when {
                apps == null -> Text("설치된 지도 앱을 확인하고 있어요…")
                apps!!.isEmpty() -> Text("검색을 지원하는 설치된 지도 앱이 없어요. 네이버지도 또는 카카오맵을 설치하면 선택할 수 있어요.")
                else -> apps!!.forEach { app ->
                    OutlinedButton(onClick = {
                        try { context.startActivity(app.intent); close() }
                        catch (_: android.content.ActivityNotFoundException) { error = "선택한 지도 앱을 열 수 없어요. 다른 앱을 선택해 주세요." }
                        catch (_: SecurityException) { error = "지도 앱의 실행 권한을 확인해 주세요." }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(app.name) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = close) { Text("취소") } })
}
