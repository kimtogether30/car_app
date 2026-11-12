package com.example.vemoring.vehicle

import android.content.Context

/** Main-thread ownership: the screen and monitor share one SDK session. */
object SharedVehicleConnection {
    private var connection: VehicleRepository? = null
    private val owners = mutableSetOf<Any>()
    private var locations: com.example.vemoring.integration.PleosLocationGateway? = null
    val travelPoint = kotlinx.coroutines.flow.MutableStateFlow<com.vemoring.companion.TravelPoint?>(null)
    fun repository(context: Context): VehicleRepository = connection ?: VehicleRepository(
        PleosVehicleGateway(context.applicationContext)).also { connection = it }
    fun acquire(context: Context, owner: Any) {
        if (owners.add(owner)) repository(context).start()
        com.example.vemoring.VehicleWashRuntime.start(context)
        if(locations==null && context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locations=com.example.vemoring.integration.PleosLocationGateway(context.applicationContext, { result ->
                val p=result.value.takeIf {result.state==com.example.vemoring.integration.ResultState.Available}
                travelPoint.value=p?.let {com.vemoring.companion.TravelPoint(it.latitude,it.longitude,java.time.Instant.ofEpochMilli(it.timestamp))}
                p?.let {com.example.vemoring.VehicleWashRuntime.observe(it)}
            }, { _,_ -> /* No coordinate logging. Unavailable location means basic distance only. */ }).also {it.start(true)}
        }
    }
    fun release(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty()) {
            connection?.close(); locations?.stop(); locations=null; travelPoint.value=null
            com.example.vemoring.VehicleWashRuntime.stop()
        }
    }
}
