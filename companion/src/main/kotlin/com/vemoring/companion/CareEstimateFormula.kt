package com.vemoring.companion

import kotlin.math.roundToInt

object CareEstimateFormula {
    fun cleanliness(days: Long, distanceKm: Double?, observedRainMm: Double?, pm10: Double?, dust: Double?, roadWeight: Double = 0.0): Int {
        require(days >= 0 && roadWeight.isFinite() && roadWeight >= 0)
        listOfNotNull(distanceKm, observedRainMm, pm10, dust).forEach { require(it.isFinite() && it >= 0) }
        // Compatibility for date-only callers: next-day boundary, actual total replaces the estimate.
        // Weather without corresponding timed/located travel cannot be charged.
        val loss = (distanceKm ?: (days - 1).coerceAtLeast(0) * 20.0) * .10
        return (100-loss).roundToInt().coerceIn(0,100)
    }
    fun oilRemaining(currentKm: Double, replacedAtKm: Double, userIntervalKm: Double): Double {
        require(currentKm.isFinite() && replacedAtKm.isFinite() && userIntervalKm.isFinite())
        require(currentKm >= replacedAtKm && replacedAtKm >= 0 && userIntervalKm > 0)
        return (userIntervalKm-(currentKm-replacedAtKm)).coerceAtLeast(0.0)
    }
}
