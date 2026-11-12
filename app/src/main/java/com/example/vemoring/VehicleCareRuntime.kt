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
            val unit = MaintenanceStore(context, data.profile).distanceUnit()
            if (!demo) journal.observeDistance(confirmedKilometres(snapshot[VehicleField.ODOMETER], unit),
                snapshot[VehicleField.ODOMETER].receivedAtMillis, session, System.currentTimeMillis(),SharedVehicleConnection.travelPoint.value)
            val warnings = JSONObject()
            listOf(VehicleField.ENGINE_OIL, VehicleField.BRAKE_FLUID, VehicleField.TRANSMISSION_OIL,
                VehicleField.WASHER, VehicleField.TELLTALES, VehicleField.OIL_PRESSURE).forEach { field ->
                val r = snapshot[field]
                warnings.put(field.name, JSONObject().put("status", r.state.name).put("at", r.receivedAtMillis)
                    .put("raw", r.value?.rawState).put("color", r.value?.color)
                    .put("warning", r.value?.warning).put("source", if(demo) "Demo" else "Real"))
            }
            val telltales=snapshot[VehicleField.TELLTALES]
            if(telltales.state==ReadingState.Available) telltales.value?.rawState?.let {raw ->
                val values=runCatching {JSONArray(raw)}.getOrNull()
                if(values!=null) repeat(values.length()) {n -> val item=values.getJSONObject(n)
                    val type=item.getString("type")
                    if(type in MaintenanceRules.heldTypes) warnings.put(type,JSONObject()
                        .put("status","Available").put("at",telltales.receivedAtMillis).put("raw",item.getString("state"))
                        .put("color",item.optInt("color")).put("eligible",false).put("source",if(demo) "Demo" else "Real"))
                }
            }
            val outgoing=journal.outgoingState()
            val motion=snapshot.motion()
            JSONObject().put("protocol", 2).put("policy", CARE_POLICY_VERSION).put("session", session).put("intervals", outgoing.intervals)
                .put("motion",JSONObject().put("state",if(demo) "Unknown" else motion.name)
                    .put("stationary",(if(demo) null else snapshot.confirmedStationary()) ?: JSONObject.NULL)
                    .put("speedMps",snapshot[VehicleField.SPEED].takeIf {!demo && motion!=VehicleMotion.Unknown && it.state==ReadingState.Available}?.value?.number ?: JSONObject.NULL)
                    .put("speedAt",snapshot[VehicleField.SPEED].receivedAtMillis)
                    .put("gear",snapshot[VehicleField.GEAR].takeIf {it.state==ReadingState.Available}?.value?.rawState?.let(::normalizedGear))
                    .put("gearAt",snapshot[VehicleField.GEAR].receivedAtMillis).put("source",if(demo) "Demo" else "Real"))
                .put("warnings", warnings).put("automaticDetection", "Held: verified wash polygons, location accuracy and motion evidence required")
                .put("request", outgoing.request).put("rejectedVisits",outgoing.rejectedVisits).put("deletedRecords",outgoing.deletedRecords)
        } }
}
