package com.example.vemoring.integration

import kotlin.math.*
import com.example.vemoring.vehicle.*

enum class DataMode { Real, Demo }
enum class ResultState { Idle, Loading, Available, NoData, Unsupported, PermissionDenied, Blocked, Disconnected, NotConfigured, Error }
data class Result<T>(val state: ResultState = ResultState.Idle, val value: T? = null, val detail: String = "", val receivedAt: Long? = null, val source: String = "Real") {
    companion object {
        fun <T> success(value: T, source: String = "Real") = Result(ResultState.Available, value, receivedAt = System.currentTimeMillis(), source = source)
    }
}
data class Coordinates(val latitude: Double, val longitude: Double, val timestamp: Long,
    val confidence: Double? = null, val accuracyMeters: Double? = null) {
    fun valid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun fresh(now: Long = System.currentTimeMillis()) = valid() && timestamp > 0 && now - timestamp in -60000..900000
}
enum class PlaceKind(val label: String) { Fuel("주유소"), Charge("전기차 충전소"), Wash("세차장"), Repair("자동차 정비소") }
data class Place(val id: String, val name: String, val address: String, val latitude: Double, val longitude: Double, val distanceMeters: Double,
    val washSite: com.vemoring.companion.WashSite? = null)
data class EnvironmentData(val temperature: Double?, val forecastRainMm: Double?, val pm10: Double?, val pm25: Double?, val dust: Double?,
    val fetchedAt: Long, val attribution: String = "Open-Meteo · CAMS 모델 추정/예보 · 현장 측정 아님")
data class DiagnosticEvent(val time: Long = System.currentTimeMillis(), val operation: String, val outcome: String, val source: String)
data class NavigationState(
    val connection: ResultState = ResultState.Idle,
    val destination: String? = null,
    val destinationKnown: Boolean = false,
    val waypointNames: List<String> = emptyList(),
    val waypointsKnown: Boolean = false,
    val event: String = "미연결",
    val receivedAt: Long? = null,
    val moving: Boolean? = null
)
fun distanceMeters(a: Coordinates, lat: Double, lon: Double): Double {
    val r1 = Math.toRadians(a.latitude); val r2 = Math.toRadians(lat)
    val d1 = r2-r1; val d2 = Math.toRadians(lon-a.longitude)
    val h = sin(d1/2).pow(2) + cos(r1)*cos(r2)*sin(d2/2).pow(2)
    return 6371000 * 2 * atan2(sqrt(h.coerceIn(0.0,1.0)), sqrt((1-h).coerceIn(0.0,1.0)))
}
/** Product estimate, never a dirt sensor. Forecast rain cannot be supplied as observed rain. */
object CareEstimator {
    fun cleanliness(days: Long, distanceKm: Double?, observedRainMm: Double?, pm10: Double?, dust: Double?, roadWeight: Double = 0.0): Int =
        com.vemoring.companion.CareEstimateFormula.cleanliness(days, distanceKm, observedRainMm, pm10, dust, roadWeight)
}
class DemoVehicleGateway : VehicleGateway {
    override fun start(publish: (VehicleField, Reading) -> Unit) {
        VehicleField.entries.forEach { field ->
            val value = when(field) {
                VehicleField.FUEL -> SignalValue("62 %",62.0,"%")
                VehicleField.SOC -> SignalValue("73 %",73.0,"%")
                VehicleField.ODOMETER -> SignalValue("12345 km",12345.0,"km")
                VehicleField.HVAC_CURRENT -> SignalValue("22 °C",22.0,"°C")
                else -> null
            }
            publish(field, Reading(ReadingState.Demo, value, "명시적 예시 · 실제 SDK 호출 없음", System.currentTimeMillis()))
        }
    }
    override fun refresh() {}
    override fun close() {}
}
