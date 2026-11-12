package com.vemoring.companion

/** Initial attempt plus three bounded retries. A successful session starts a new budget. */
object ConnectionRetry {
    private val delays = listOf(1_000L, 3_000L, 10_000L)
    fun delayAfterFailure(retriesUsed: Int): Long? = delays.getOrNull(retriesUsed)
}
