package com.example.vemoring

import android.content.Context
import com.example.vemoring.integration.*
import com.vemoring.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/** One shared consumer of real Pleos fixes, bound to the receiving car, not the displayed car. */
object VehicleWashRuntime {
    val revision=MutableStateFlow(0L)
    private var job:Job?=null
    private var input:Channel<Pair<Coordinates,Long>>?=null
    private var lastSubmitted=0L
    fun start(context:Context) {
        if(job?.isActive==true) return
        val app=context.applicationContext
        val channel=Channel<Pair<Coordinates,Long>>(Channel.CONFLATED);input=channel
        job=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            val garage=GarageStore(app)
            val repository=HttpExternalDataRepository()
            var center:Coordinates?=null
            var fetchedAt=0L
            var nextAttempt=0L
            var sites=emptyList<WashSite>()
            var previousId:String?=null
            for((point,receivedAt) in channel) {
                val prefs=app.getSharedPreferences(VehicleIconMonitor.PREFS,Context.MODE_PRIVATE)
                if(!prefs.getBoolean(VehicleIconMonitor.RECEIVE_ENABLED,true)) continue
                val receiving=garage.receiving() ?: continue
                if(previousId!=receiving.profile.vehicleId) {
                    previousId=receiving.profile.vehicleId;sites=emptyList();center=null;fetchedAt=0;nextAttempt=0
                }
                val fix=FacilityFix(point.latitude,point.longitude,point.timestamp,receivedAt)
                if(!fix.valid()) continue
                try {
                    val stale=center==null || receivedAt-fetchedAt>15*60_000 || center?.let {distanceMeters(it,point.latitude,point.longitude)>5000}==true
                    if(stale && receivedAt>=nextAttempt) {
                        nextAttempt=receivedAt+60_000
                        // Existing public map endpoint. Coordinate transmission approved by the user.
                        val places=repository.places(point,PlaceKind.Wash)
                        if(places.state==ResultState.Available) {
                            sites=places.value.orEmpty().map {WashSite(it.id,it.latitude,it.longitude)}
                            center=point;fetchedAt=receivedAt
                        }
                    }
                    ensureActive()
                    if(garage.receiving()?.profile?.vehicleId!=receiving.profile.vehicleId) continue
                    if(FacilityWashStore(app,receiving.profile.vehicleId).observeVehicle(fix,sites)) revision.value++
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(_:Exception) { /* No invented location, wash, or coordinate logging on failure. */ }
            }
        }
    }
    fun observe(point:Coordinates,receivedAt:Long=System.currentTimeMillis()) {
        if(point.timestamp-lastSubmitted<5000) return
        lastSubmitted=point.timestamp;input?.trySend(point to receivedAt)
    }
    fun stop() {input?.close();input=null;job?.cancel();job=null;lastSubmitted=0}
    fun frame(context:Context,vehicleId:String):JSONArray = FacilityWashStore(context,vehicleId).frame()
}
