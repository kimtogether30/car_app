package com.example.vemoring

import android.content.Context
import com.example.vemoring.integration.*
import com.example.vemoring.vehicle.*
import com.vemoring.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/** One shared consumer of real Pleos fixes, bound to the receiving car, not the displayed car. */
object VehicleWashRuntime {
    val revision=MutableStateFlow(0L)
    private var job:Job?=null
    private data class Sample(val point:Coordinates,val receivedAt:Long,val snapshot:VehicleSnapshot)
    private var input:Channel<Sample>?=null
    private var lastSubmitted=0L
    private var lastSample:Sample?=null
    private var lastPowerAt=0L
    fun start(context:Context) {
        if(job?.isActive==true) return
        val app=context.applicationContext
        val channel=Channel<Sample>(Channel.CONFLATED);input=channel
        job=CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            val garage=GarageStore(app)
            val repository=HttpExternalDataRepository()
            var center:Coordinates?=null
            var fetchedAt=0L
            var nextAttempt=0L
            var sites=emptyList<WashSite>()
            var repairSites=emptyList<WashSite>()
            var repairsAt=0L
            var previousId:String?=null
            for((point,receivedAt,snapshot) in channel) {
                val prefs=app.getSharedPreferences(VehicleIconMonitor.PREFS,Context.MODE_PRIVATE)
                if(!prefs.getBoolean(VehicleIconMonitor.RECEIVE_ENABLED,true)) continue
                val receiving=garage.receiving() ?: continue
                if(previousId!=receiving.profile.vehicleId) {
                    previousId=receiving.profile.vehicleId;repairSites=emptyList();repairsAt=0;lastPowerAt=0;sites=emptyList();center=null;fetchedAt=0;nextAttempt=0
                }
                // Capture original measurement timestamps; cached, failed and missing signals never become zero.
                val speed=snapshot[VehicleField.SPEED].takeIf {it.state==ReadingState.Available && it.value?.unit=="m/s"}
                val gear=snapshot[VehicleField.GEAR].takeIf {it.state==ReadingState.Available}
                val fix=FacilityFix(point.latitude,point.longitude,point.timestamp,receivedAt,
                    accuracyM=point.accuracyMeters,speedMps=speed?.value?.number,speedAt=speed?.receivedAtMillis,
                    gear=normalizedGear(gear?.value?.rawState),gearAt=gear?.receivedAtMillis)
                if(!fix.valid()) continue
                try {
                    val stale=center==null || receivedAt-fetchedAt>15*60_000 || center?.let {distanceMeters(it,point.latitude,point.longitude)>5000}==true
                    if(stale && receivedAt>=nextAttempt) {
                        nextAttempt=receivedAt+60_000
                        // Existing public map endpoint. Coordinate transmission approved by the user.
                        val places=repository.places(point,PlaceKind.Wash)
                        if(places.state==ResultState.Available) {
                            sites=places.value.orEmpty().mapNotNull {it.washSite}
                            center=point;fetchedAt=receivedAt
                        }
                    }
                    ensureActive()
                    if(garage.receiving()?.profile?.vehicleId!=receiving.profile.vehicleId) continue
                    if(FacilityWashStore(app,receiving.profile.vehicleId).observeVehicle(fix,sites)) revision.value++
                    val power=snapshot[VehicleField.IGNITION]
                    val powerAt=power.receivedAtMillis ?: 0
                    val oilPower=when(power.value?.rawState) {"OFF","LOCK"->VerifiedVehiclePower.OFF;"ON"->VerifiedVehiclePower.ON;else->null}
                    val oilEnabled=EngineOilCatalog.rule(app,receiving.profile.fullModel,receiving.profile.fuel)!=null
                    if(oilEnabled && EngineOilVisitPolicy.resumeUsable(fix) &&
                        (fix.liveSpeed()==0.0 && fix.liveGear()!=null || power.state==ReadingState.Available && oilPower!=null && powerAt>lastPowerAt && receivedAt-powerAt in 0..15000) &&
                        (receivedAt-repairsAt>15*60_000 || repairSites.isEmpty() && receivedAt-repairsAt>=60_000)) {
                        repairsAt=receivedAt
                        val repair=repository.places(point,PlaceKind.Repair)
                        repairSites=if(repair.state==ResultState.Available) repair.value.orEmpty().mapNotNull {it.washSite} else emptyList()
                    }
                    if(garage.receiving()?.profile?.vehicleId!=receiving.profile.vehicleId) continue
                    if(oilEnabled && power.state==ReadingState.Available && oilPower!=null && powerAt>lastPowerAt && receivedAt-powerAt in 0..15000 && kotlin.math.abs(fix.at-powerAt)<=15000 && EngineOilVisitPolicy.usable(fix)) {
                        EngineOilVisitStore(app,receiving.profile.vehicleId).power(oilPower,fix.copy(at=powerAt),repairSites)
                        lastPowerAt=powerAt
                    }
                    val km=confirmedKilometres(snapshot[VehicleField.ODOMETER],MaintenanceStore(app,receiving.profile).distanceUnit())
                    if(EngineOilVisitStore(app,receiving.profile.vehicleId).observe(fix,km,if(oilEnabled) repairSites else emptyList())) revision.value++
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(_:Exception) { /* No invented location, wash, or coordinate logging on failure. */ }
            }
        }
    }
    fun observe(point:Coordinates,snapshot:VehicleSnapshot,receivedAt:Long=System.currentTimeMillis()) {
        lastSample=Sample(point,receivedAt,snapshot)
        if(point.timestamp-lastSubmitted<5000) return
        lastSubmitted=point.timestamp;input?.trySend(Sample(point,receivedAt,snapshot))
    }
    fun powerChanged(snapshot:VehicleSnapshot) {
        val p=snapshot[VehicleField.IGNITION]
        val prior=lastSample ?: return
        if(p.state==ReadingState.Available && (p.receivedAtMillis ?: 0)>lastPowerAt)
            input?.trySend(prior.copy(snapshot=snapshot,receivedAt=System.currentTimeMillis()))
    }
    fun stop() {lastSample=null;lastPowerAt=0;input?.close();input=null;job?.cancel();job=null;lastSubmitted=0}
    fun frame(context:Context,vehicleId:String):JSONArray = FacilityWashStore(context,vehicleId).frame()
}
