package com.example.vemoring

import com.example.vemoring.integration.MaintenanceConfig
import com.example.vemoring.vehicle.VehicleSnapshot

/** Build variants supply an empty source in release and an explicitly controlled source in debug. */
data class IconMonitorSample(val data: AppData, val snapshot: VehicleSnapshot, val config: MaintenanceConfig)
