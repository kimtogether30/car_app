package com.vemoring.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Looper
import androidx.core.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import kotlin.coroutines.resume

/** A bounded foreground request. No service, tracking loop, or Google Play Services dependency. */
@android.annotation.SuppressLint("MissingPermission") // Runtime COARSE/FINE checks below also guard the shared vehicle build.
internal suspend fun phoneWeatherLocation(context: Context): TravelPoint? = withContext(Dispatchers.Main.immediate) { withTimeoutOrNull(20_000) {
    val manager=context.getSystemService(LocationManager::class.java) ?: return@withTimeoutOrNull null
    val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    if(!fine && !coarse || !LocationManagerCompat.isLocationEnabled(manager)) return@withTimeoutOrNull null
    // From Android 12 an approximate-only app may read any provider; the platform coarsens the result.
    val gpsAllowed=fine || android.os.Build.VERSION.SDK_INT >= 31
    val providers=manager.getProviders(true).filter { it in setOf("fused",LocationManager.NETWORK_PROVIDER) || gpsAllowed && it==LocationManager.GPS_PROVIDER }
    if(providers.isEmpty()) return@withTimeoutOrNull null
    fun freshPoint(location: Location?): TravelPoint? {
        val age=location?.let {SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos}
        return location?.takeIf {age != null && age in 0..ForecastLocationPolicy.PHONE_WEATHER_MAX_AGE_MILLIS*1_000_000L}?.let {
            runCatching {TravelPoint(it.latitude,it.longitude,Instant.now().minusNanos(age!!))}.getOrNull()
        }
    }
    // A coarse current-location request can return null even while another provider has a fresh fix.
    providers.forEach {provider ->
        val cached=try {freshPoint(manager.getLastKnownLocation(provider))} catch(_: SecurityException) {null}
        if(cached!=null) return@withTimeoutOrNull cached
    }
    suspendCancellableCoroutine { continuation ->
        val signals=providers.map {CancellationSignal()}
        val listeners=mutableListOf<LocationListener>()
        fun cleanup() {
            signals.forEach {it.cancel()}
            listeners.forEach {runCatching {manager.removeUpdates(it)}}
        }
        fun finish(point: TravelPoint?) {
            if(continuation.isActive) {
                continuation.resume(point)
                cleanup()
            }
        }
        continuation.invokeOnCancellation {
            if(Looper.myLooper()==Looper.getMainLooper()) cleanup()
            else android.os.Handler(Looper.getMainLooper()).post {cleanup()}
        }
        providers.forEachIndexed { index,provider ->
            if(continuation.isActive) try {
                LocationManagerCompat.getCurrentLocation(manager,provider,signals[index],ContextCompat.getMainExecutor(context)) {location ->
                    if(!continuation.isActive) return@getCurrentLocation
                    val point=freshPoint(location)
                    if(point != null) finish(point)
                    else {
                        // A null/stale one-shot result must not end the entire request before a new fix arrives.
                        // This listener lasts only for the remainder of the same 20-second foreground request.
                        val listener=object : LocationListener {
                            override fun onLocationChanged(value: Location) {freshPoint(value)?.let {finish(it)}}
                            override fun onProviderEnabled(provider: String) = Unit
                            override fun onProviderDisabled(provider: String) = Unit
                            @Deprecated("Deprecated in Android")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                        }
                        listeners.add(listener)
                        try {manager.requestLocationUpdates(provider,1000L,0f,listener,Looper.getMainLooper())}
                        catch(_: SecurityException) {listeners.remove(listener)}
                        catch(_: IllegalArgumentException) {listeners.remove(listener)}
                    }
                }
            } catch(_: SecurityException) { /* Another provider may still answer; timeout cleans up. */ }
            catch(_: IllegalArgumentException) { /* Provider removed during request. */ }
        }
    }
} }
