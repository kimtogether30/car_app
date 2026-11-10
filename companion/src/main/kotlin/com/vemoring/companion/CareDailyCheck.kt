package com.vemoring.companion

import android.app.*
import android.content.*
import kotlinx.coroutines.*
import java.time.*

/** One inexact Seoul-midnight evaluation, not a polling loop or an exact-alarm permission. */
object CareDailyCheck {
    private var scheduledAt = 0L
    fun schedule(c: Context) {
        val next = LocalDate.now(CARE_ZONE).plusDays(1).atStartOfDay(CARE_ZONE).toInstant().toEpochMilli()
        if (scheduledAt == next) return
        val intent = PendingIntent.getBroadcast(c, 4201, Intent(c, CareDailyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
        scheduledAt = next
    }
}

class CareDailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { CareExchange.refreshAll(context) } finally { pending.finish() }
        }
    }
}
