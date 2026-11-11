package com.vemoring.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.coroutines.resume

/** A bounded foreground request. No service, tracking loop, or Google Play Services dependency. */
internal suspend fun phoneWeatherLocation(context: Context): TravelPoint? = withTimeoutOrNull(20_000) {
    val manager=context.getSystemService(LocationManager::class.java) ?: return@withTimeoutOrNull null
    val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    if(!fine && !coarse || !LocationManagerCompat.isLocationEnabled(manager)) return@withTimeoutOrNull null
    // From Android 12 an approximate-only app may read any provider; the platform coarsens the result.
    val gpsAllowed=fine || android.os.Build.VERSION.SDK_INT >= 31
    val providers=manager.getProviders(true).filter { it in setOf("fused",LocationManager.NETWORK_PROVIDER) || gpsAllowed && it==LocationManager.GPS_PROVIDER }
    if(providers.isEmpty()) return@withTimeoutOrNull null
    suspendCancellableCoroutine { continuation ->
        val signals=providers.map {CancellationSignal()}
        var remaining=providers.size
        fun finish(point: TravelPoint?) {
            if(continuation.isActive) {
                continuation.resume(point)
                signals.forEach {it.cancel()}
            }
        }
        continuation.invokeOnCancellation {signals.forEach {it.cancel()}}
        providers.forEachIndexed { index,provider ->
            if(continuation.isActive) try {
                LocationManagerCompat.getCurrentLocation(manager,provider,signals[index],ContextCompat.getMainExecutor(context)) {location ->
                    val age=location?.let {SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos}
                    val point=location?.takeIf {age != null && age in 0..60_000_000_000L}?.let {
                        runCatching {TravelPoint(it.latitude,it.longitude,Instant.now().minusNanos(age!!))}.getOrNull()
                    }
                    if(point != null) finish(point)
                    else if(--remaining==0) finish(null)
                }
            } catch(_: SecurityException) {if(--remaining==0) finish(null)}
            catch(_: IllegalArgumentException) {if(--remaining==0) finish(null)}
        }
    }
}
