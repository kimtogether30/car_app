package com.example.vemoring.vehicle

import android.content.Context

/** Main-thread ownership: the screen and monitor share one SDK session. */
object SharedVehicleConnection {
    private var connection: VehicleRepository? = null
    private val owners = mutableSetOf<Any>()
    fun repository(context: Context): VehicleRepository = connection ?: VehicleRepository(
        PleosVehicleGateway(context.applicationContext)).also { connection = it }
    fun acquire(context: Context, owner: Any) {
        if (owners.add(owner)) repository(context).start()
    }
    fun release(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty()) connection?.close()
    }
}
