package com.example.vemoring.phone

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The field area scrolls; heading and actions always remain above the IME. */
@Composable internal fun PhoneFormDialog(title: String, busy: Boolean, saveEnabled: Boolean,
    close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.(finishInput: () -> Unit) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val finishInput = { focus.clearFocus(); keyboard?.hide(); Unit }
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, Modifier.weight(1f), fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = finishInput, modifier = Modifier.heightIn(min = 48.dp).testTag("hide-keyboard")) {
                            Text("키보드 닫기", fontSize = 12.sp)
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) { content(finishInput) }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        TextButton(enabled = !busy, onClick = { finishInput(); close() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") }
                        Button(enabled = !busy && saveEnabled, onClick = { finishInput(); save() },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("form-save")) { Text(if (busy) "저장 중…" else "저장하기") }
                    }
                }
            }
        }
    }
}
