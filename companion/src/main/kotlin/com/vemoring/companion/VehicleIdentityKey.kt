package com.vemoring.companion

/** Only for migrating existing on-device keys. New registrations use a persisted UUID. */
fun legacyVehicleId(model: String,plate: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest((model+"|"+plate).toByteArray()).joinToString("") {"%02x".format(it)}
