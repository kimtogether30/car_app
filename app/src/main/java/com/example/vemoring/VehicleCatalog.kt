package com.example.vemoring

/** Shared with the phone app to keep brand/model choices identical. */
object VehicleCatalog {
    val brands get() = com.vemoring.companion.VehicleCatalogData.brands
    fun categories(brand: String) = com.vemoring.companion.VehicleCatalogData.categories(brand)
}
