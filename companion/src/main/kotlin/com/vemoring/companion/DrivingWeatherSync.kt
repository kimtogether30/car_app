package com.vemoring.companion

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Phone-side, one bounded batch at a time. No network on BT ACK path or Compose/main thread. */
object DrivingWeatherSync {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private val jobs=mutableMapOf<String,Job>()
    private val repository=HourlyWeatherRepository()
    fun enqueue(context:Context,vehicleId:String) {
        val app=context.applicationContext
        synchronized(jobs) {
            if(jobs[vehicleId]?.isActive==true) return
            jobs[vehicleId]=scope.launch {
                var pause=0L
                do {
                    if(pause>0) delay(pause)
                    val now=Instant.now()
                    val store=CareJournal(app,vehicleId)
                    if(store.authority()==null) break
                    val pending=store.intervals().filter {DrivingWeatherPolicy.pending(it,now)}
                    if(pending.isEmpty()) break
                    var changed=false
                    // Serialize external requests across cars; protect PC/phone/network budget.
                    lock.withLock {
                        pending.filter {DrivingWeatherPolicy.readyAt(it)<=now}.take(24).forEach {t ->
                            val weather=repository.fetch(t)
                            if(weather!=null && store.attachWeather(t.id,weather)) changed=true
                        }
                    }
                    if(changed) CareExchange.weatherChanged(app,vehicleId)
                    // Finished data may become available later. A single sleeping job, not SDK polling.
                    pause=600000L
                } while(isActive)
            }
        }
    }
}
