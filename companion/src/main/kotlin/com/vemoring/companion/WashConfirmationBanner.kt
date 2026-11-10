package com.vemoring.companion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

data class WashConfirmation(val vehicleId: String, val visitId: String, val completedAt: Instant, val appliedAt: Instant)

@Composable
fun rememberWashConfirmation(vehicleId: String?, authority: CareAuthority?): State<WashConfirmation?> {
    val context = LocalContext.current.applicationContext
    val revision by CareExchange.confirmationRevision.collectAsState()
    return produceState<WashConfirmation?>(null, vehicleId, authority, revision) {
        value = if(vehicleId == null) null else withContext(Dispatchers.IO) {
            CareJournal(context, vehicleId).pendingWashConfirmation()
        }
    }
}

/** Inline, no animation or blocking dialog. Closing keeps the already-applied wash estimate. */
@Composable
fun WashConfirmationBanner(confirmation: WashConfirmation, busy: Boolean, onReject: () -> Unit,
    onDismiss: () -> Unit, modifier: Modifier = Modifier, vehicle: Boolean = false) {
    val time = remember(confirmation.completedAt) {
        confirmation.completedAt.atZone(CARE_ZONE).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
    }
    val dark=isSystemInDarkTheme()
    Surface(modifier.fillMaxWidth().testTag("wash-confirmation").semantics { liveRegion = LiveRegionMode.Polite },
        color = if(dark) Color(0xFF24292F) else Color(0xFFF9FAFC),contentColor=if(dark) Color.White else Color(0xFF202938),shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(if(vehicle) 24.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VEMORING · $time",fontSize=if(vehicle) 16.sp else 12.sp,color=if(dark) Color(0xFFB8C2D0) else Color(0xFF66758A))
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                BannerArtwork("mascot_normal","",Modifier.size(if(vehicle) 96.dp else 64.dp),mascot=true)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("세차한 것 같아!", fontSize = if(vehicle) 26.sp else 20.sp)
                    Text("세차장 방문을 감지해서 청결도를 초기화했어. 주유만 했거나 세차하지 않았다면 알려줘!",
                        color=if(dark) Color(0xFFBAC3D0) else Color(0xFF64748B),fontSize = if(vehicle) 21.sp else 16.sp, lineHeight = if(vehicle) 30.sp else 24.sp)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp,androidx.compose.ui.Alignment.End)) {
                FilledTonalButton(enabled = !busy, onClick = onReject,shape=RoundedCornerShape(8.dp),
                    colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(dark) Color(0xFF314762) else Color(0xFFDDEAFF),contentColor=if(dark) Color(0xFFE1ECFF) else Color(0xFF2563C7)),modifier = Modifier.heightIn(min = if(vehicle) 64.dp else 48.dp)
                    .testTag("wash-reject")) { Text("세차 안 했어요", fontSize = if(vehicle) 22.sp else 16.sp) }
                TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.heightIn(min = if(vehicle) 64.dp else 48.dp)
                    .testTag("wash-dismiss")) { Text("닫기", fontSize = if(vehicle) 22.sp else 16.sp) }
            }
        }
    }
}
