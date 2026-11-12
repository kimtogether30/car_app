package com.vemoring.companion

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun manualWashTime(completedAt: String?, source: String?): String =
    if(source != "Manual" || completedAt == null) "" else runCatching {
        Instant.parse(completedAt).atZone(CARE_ZONE).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")

fun washCompletion(date: LocalDate, time: String, now: Instant = Instant.now()): String? = runCatching {
    if(!Regex("\\d{2}:\\d{2}").matches(time)) return null
    val value=date.atTime(LocalTime.parse(time)).atZone(CARE_ZONE).toInstant()
    value.takeUnless {it > now}?.toString()
}.getOrNull()

@Composable fun WashCompletionInput(date: LocalDate, time: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value=time,onValueChange={change(it.take(5))},enabled=enabled,singleLine=true,
        label={Text("세차 완료 시각 (선택, HH:mm)")},
        isError=time.isNotBlank() && washCompletion(date,time)==null,
        supportingText={Text("예: 14:30 · 비워두면 세차 다음 날부터 차감해요. 미래 시각은 저장할 수 없어요.")})
}
