package com.example.vemoring.vehicle

/** Only explicit identification from a trusted vehicle adapter belongs here, never energy readings or VIN guesses. */
data class ConfirmedVehicleInfo(val brand: String? = null, val model: String? = null,
                                val fuel: String? = null, val plate: String? = null) {
    fun normalized() = copy(brand = brand.clean(), model = model.clean(),
        fuel = fuel.clean()?.takeIf { it in listOf("휘발유", "경유", "하이브리드", "전기차") }, plate = plate.clean())
}
private fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }

data class VehicleIdentityReading(val state: ReadingState = ReadingState.Unsupported,
                                  val value: ConfirmedVehicleInfo? = null) {
    fun confirmed() = if (state == ReadingState.Available) value?.normalized() ?: ConfirmedVehicleInfo() else ConfirmedVehicleInfo()
}

fun setupSteps(info: ConfirmedVehicleInfo): List<Int> {
    val verified = info.normalized()
    return buildList {
        if (verified.brand == null) add(0)
        if (verified.model == null) add(1)
        if (verified.fuel == null) add(2)
        addAll(listOf(3, 4, 6, 5))
    }
}
