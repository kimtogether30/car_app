package com.example.vemoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object IconMonitorTestSource {
    val input = MutableStateFlow<IconMonitorSample?>(null).asStateFlow()
}
