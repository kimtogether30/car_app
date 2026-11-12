package com.vemoring.companion

import org.json.JSONObject

object BannerPolicy {
    fun recordCompletion(kind: String, type: MaintenanceType): BannerDefinition? = when(kind) {
        "wash" -> BannerCatalog["WASH_DONE"]
        "fuel" -> BannerCatalog["FUEL_DONE"]
        "maintenance" -> if(type == MaintenanceType.ENGINE_OIL) BannerCatalog["OIL_DONE"] else BannerCatalog["MAINTENANCE_DONE"]
        "oil" -> BannerCatalog["MAINTENANCE_DONE"] // Legacy value was also used for general repairs.
        else -> null
    }

    val maintenanceGroups = mapOf(
        "ENGINE" to "ENGINE", "ENGINE_OIL_PRESSURE" to "OIL_PRESSURE", "OIL_PRESSURE" to "OIL_PRESSURE",
        "ENGINE_TEMPERATURE" to "ENGINE_TEMPERATURE", "LOW_VOLT_BATTERY" to "LOW_VOLT_BATTERY",
        "ABS" to "ABS", "ELECTRONIC_PARKING_BRAKE" to "EPB", "MDPS" to "MDPS", "TPMS" to "TPMS",
        "ESC" to "ESC", "AIRBAG" to "AIRBAG", "AFS" to "HEADLAMP", "LED_HEADLAMP" to "HEADLAMP",
        "PARTICLE_FILTER" to "EXHAUST", "SCR" to "EXHAUST", "WATER_SEPARATOR" to "EXHAUST",
        "EV_SYSTEM" to "DRIVE", "HEV_SYSTEM" to "DRIVE", "FCEV_SYSTEM" to "DRIVE", "EREV_SYSTEM" to "DRIVE",
        "H2_SENSOR" to "HYDROGEN", "H2_LEAK" to "HYDROGEN", "ENGINE_OIL" to "FLUID",
        "BRAKE_FLUID" to "FLUID", "TRANSMISSION_OIL" to "FLUID", "WASHER" to "WASHER")

    fun issues(fuel: Boolean, wash: Boolean, maintenance: String?): BannerDefinition? {
        if(maintenance != null) {
            val group = maintenanceGroups[maintenance] ?: maintenance.takeIf { it in maintenanceGroups.values }
            if(group == null || group == "WASHER") {
                val d=BannerCatalog[if(group=="WASHER") "WASHER" else "VEHICLE_WARNING"]
                val prefix=if(fuel && wash) "주유·세차·" else if(fuel) "주유와 " else if(wash) "세차와 " else ""
                return d.copy(key=(if(fuel && wash) "ALL_" else if(fuel) "FUEL_" else if(wash) "WASH_" else "")+d.key,
                    title=prefix+d.title,mood=if(fuel && wash) "EXHAUSTED" else if(fuel || wash) "ANGRY" else "OIL")
            }
            val prefix = if(fuel && wash) "ALL_" else if(fuel) "FUEL_" else if(wash) "WASH_" else ""
            return BannerCatalog[prefix+group]
        }
        return when { fuel && wash -> BannerCatalog["FUEL_WASH_NEEDED"]; fuel -> BannerCatalog["FUEL_NEEDED"]
            wash -> BannerCatalog["WASH_NEEDED"]; else -> null }
    }
    fun forProfile(definition: BannerDefinition, profile: JSONObject): BannerDefinition {
        if(profile.optString("fuel") != "전기차" || !definition.title.contains("주유")) return definition
        // Supplied artwork literally says refuelling; don't show that text for a battery-only vehicle.
        return definition.copy(title=definition.title.replace("주유","충전"), body=definition.body.replace("주유","충전"),art=null)
    }
}

enum class WashCompletionEvidence { EstimatedVisit, ClearFacilityPattern, UserRecord, VerifiedCompletion }
