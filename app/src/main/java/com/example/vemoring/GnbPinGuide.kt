package com.example.vemoring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pleos GNB pinning is a driver-controlled drag operation, not Android requestPinShortcut. */
@Composable
fun GnbPinGuide(close: () -> Unit) {
    AlertDialog(onDismissRequest = close,
        title = { Text("하단 바에 VEMORING 놓기", fontSize = 30.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("주차 중에 설정해 주세요.", fontSize = 24.sp, color = Blue)
                Text("1. 하단의 앱 목록 버튼을 눌러요.\n\n2. VEMORING 아이콘을 길게 눌러요.\n\n3. 하단 바의 편집 가능한 자리에 끌어 놓아요.",
                    fontSize = 24.sp, lineHeight = 34.sp)
                Text("최근 앱 영역에는 고정할 수 없어요. 고정할 자리가 찼다면 원하는 앱과 자리를 바꿔 주세요.", fontSize = 21.sp)
                HorizontalDivider()
                Text("현재 Pleos에서는 하단 바 아이콘이 상태에 따라 갱신되지 않을 수 있어요. 상태별 아이콘은 앱 목록에서 확인할 수 있어요.",
                    fontSize = 21.sp, lineHeight = 30.sp)
            }
        },
        confirmButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 64.dp)) {
            Text("방법 확인했어요", fontSize = 24.sp)
        } })
}
