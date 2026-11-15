package com.vemoring.companion

import android.os.SystemClock

/**
 * Android sheds (silently drops) notifications an app enqueues faster than about 5 per second, while notify()
 * still returns normally, so a burst would be recorded as delivered but never shown. Measured on Tab S7
 * (2026-11-15): "Package enqueue rate is 5.6. Shedding ...". Space every post, including status updates.
 * Worker threads only.
 */
internal object NotificationPacer {
    private const val GAP_MS = 250L
    private var last = 0L
    @Synchronized fun await() {
        val wait = last + GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) Thread.sleep(wait)
        last = SystemClock.elapsedRealtime()
    }
}
