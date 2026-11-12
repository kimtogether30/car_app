package com.vemoring.companion

import org.json.JSONObject

/** Ordinary HEVs refuel; their traction SOC is not an external charging requirement. */
object EnergyPolicy {
    fun low(electric: Boolean, fuel: Int?, battery: Int?, threshold: Int): Boolean? =
        (if (electric) battery else fuel)?.takeIf { it in 0..100 }?.let { it <= threshold }

    fun low(profile: JSONObject, content: JSONObject): Boolean? {
        fun percent(key: String): Int? = if (!content.has(key) || content.isNull(key)) null else
            content.optDouble(key).takeIf { it.isFinite() && it in 0.0..100.0 }?.toInt()
        return low(profile.optString("fuel") == "전기차", percent("fuel"), percent("battery"), profile.getInt("fuelAlert"))
    }
}

