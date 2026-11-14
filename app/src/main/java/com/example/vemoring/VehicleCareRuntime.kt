package com.example.vemoring

import android.content.Context
import com.example.vemoring.integration.*
import com.example.vemoring.vehicle.*
import com.vemoring.companion.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object VehicleCareRuntime {
    private val mutex = Mutex()
    private var session = UUID.randomUUID().toString()
    suspend fun resetSession() = mutex.withLock {session=UUID.randomUUID().toString()}
    suspend fun payload(context: Context, data: AppData, snapshot: VehicleSnapshot, demo: Boolean): JSONObject =
        withContext(Dispatchers.IO) { mutex.withLock {
            val id = vehicleCareId(data.profile)
            val journal = CareJournal(context, id)
            if(!demo) VehicleWashRuntime.powerChanged(snapshot)
            val unit = MaintenanceStore(context, data.profile).distanceUnit()
            if (!demo) journal.observeDistance(confirmedKilometres(snapshot[VehicleField.ODOMETER], unit),
                snapshot[VehicleField.ODOMETER].receivedAtMillis, session, System.currentTimeMillis(),SharedVehicleConnection.travelPoint.value)
            val oilLedger=EngineOilLedger(context,id)
            if(!demo) {
                oilLedger.observe(confirmedKilometres(snapshot[VehicleField.ODOMETER],unit),snapshot[VehicleField.ODOMETER].receivedAtMillis)
                oilLedger.assess(JSONObject().put("model",data.profile.fullModel).put("fuel",data.profile.fuel).put("oilEstimate",data.profile.oilEstimate).put("oilBaselineKm",data.profile.oilBaselineKm),
                    JSONArray().also {a -> data.records.forEach {a.put(it.json())}}.toString())
            }
            val outgoing=journal.outgoingState()
            // Fuel cars (gasoline/diesel/HEV): sticky low episode + three-read refuel confirmation. EV keeps the battery path.
            val fuelEpisode=if(!demo && !data.profile.electric)
                FuelRefillMonitor.observe(context,journal,data.profile.fuelAlert,snapshot[VehicleField.FUEL]) else null
            val motion=snapshot.motion()
            JSONObject().put("protocol", 2).put("policy", CARE_POLICY_VERSION).put("session", session).put("intervals", outgoing.intervals)
                .put("motion",JSONObject().put("state",if(demo) "Unknown" else motion.name)
                    .put("stationary",(if(demo) null else snapshot.confirmedStationary()) ?: JSONObject.NULL)
                    .put("speedMps",snapshot[VehicleField.SPEED].takeIf {!demo && motion!=VehicleMotion.Unknown && it.state==ReadingState.Available}?.value?.number ?: JSONObject.NULL)
                    .put("speedAt",snapshot[VehicleField.SPEED].receivedAtMillis)
                    .put("gear",snapshot[VehicleField.GEAR].takeIf {it.state==ReadingState.Available}?.value?.rawState?.let(::normalizedGear))
                    .put("gearAt",snapshot[VehicleField.GEAR].receivedAtMillis).put("source",if(demo) "Demo" else "Real"))
                .put("automaticDetection", "Mapped wash area; 3-minute motion-qualified dwell and corroborated vehicle exit; cancelable estimate")
                .put("washVisits",if(demo) JSONArray() else VehicleWashRuntime.frame(context,id))
                .put("oilVisits",if(demo) JSONArray() else EngineOilVisitStore(context,id).frame())
                .put("request", outgoing.request).put("rejectedVisits",outgoing.rejectedVisits).put("deletedRecords",outgoing.deletedRecords)
                .put("fuelEpisode",fuelEpisode?.json() ?: JSONObject.NULL).put("oilLedger",if(demo) JSONObject.NULL else oilLedger.frame())
        } }
}
