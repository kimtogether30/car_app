package com.example.vemoring.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vemoring.Panel

fun Reading.statusText(): String = when(state) {
    ReadingState.Loading -> "확인 중"
    ReadingState.Available -> value?.let { if (it.number != null && it.unit == null) "수신됨 · 단위 확인 전 사용 보류" else it.text } ?: "미수신"
    ReadingState.Unsupported -> "미지원"
    ReadingState.PermissionDenied -> "접근 권한 없음"
    ReadingState.Disconnected -> "차량 연결 안 됨"
    ReadingState.Error -> "조회 오류"
    ReadingState.NoData -> "미수신"
    ReadingState.Demo -> "예시 데이터"
}

@Composable
fun VehicleDetails(snapshot: VehicleSnapshot, refresh: () -> Unit) {
    snapshot.priorityWarning()?.let { warning ->
        Panel(color = Color(0xFFFFE2DC)) {
            Text("차량 안전 경고", color = Color(0xFF9D2015))
            Text(warning, color = Color(0xFF9D2015))
            Text("차량 계기판과 사용 설명서를 확인해 주세요. 정기 교체 알림과 다른 차량 경고입니다.", fontSize = 12.sp)
        }
    }
    Panel {
        Row { Text("차량 수신 정보", Modifier.weight(1f)); TextButton(onClick = refresh) { Text("새로고침") } }
        Text("차량명·차종은 사용자 입력을 유지합니다. 공조 정보는 표시된 좌석 영역의 조회값입니다.", fontSize = 12.sp)
        VehicleField.entries.filter { it != VehicleField.FUEL && it != VehicleField.SOC }.forEach { field ->
            val reading = snapshot[field]
            Text("${field.label}: ${reading.statusText()}", fontSize = 13.sp)
            if (reading.state == ReadingState.Available) Text("Pleos 수신", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
