package com.vemoring.companion

/** Only while the user-enabled connected-device service is running. No scans, alarms or wake locks. */
internal object BluetoothReconnectPolicy {
    private val slowDelays = listOf(30_000L, 60_000L, 120_000L, 300_000L)
    const val MAX_FAILURES = 6
    fun delayAfterFailure(failures: Int): Long {
        require(failures >= 0)
        return ConnectionRetry.delayAfterFailure(failures)
            ?: slowDelays[(failures - 3).coerceIn(slowDelays.indices)]
    }
}
