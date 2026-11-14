package com.vemoring.companion

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One home hierarchy for the phone and vehicle. Platform-specific sizing stays inside each slot. */
@Composable fun CareHomeLayout(
    spacing:Dp=12.dp,
    header:@Composable ()->Unit,
    character:@Composable ()->Unit,
    status:@Composable ()->Unit,
    records:@Composable ()->Unit,
    weather:@Composable ()->Unit,
    nearby:@Composable ()->Unit
) {
    header()
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=1120.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(spacing)) {
            character()
            status()
            records()
            weather()
            nearby()
        }
    }
}
