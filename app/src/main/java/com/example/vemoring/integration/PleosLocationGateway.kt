package com.example.vemoring.integration

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.*
import ai.pleos.playground.location.fused.model.*
import ai.pleos.playground.location.fused.exception.LocationException
import ai.pleos.playground.location.fused.constant.ErrorCode

interface LocationGateway {
    fun start(continuous: Boolean)
    fun stop()
    fun showBlocked()
    fun addArea(center: Coordinates, radius: Float)
    fun removeAreas()
}
class PleosLocationGateway(private val context: Context, private val publish: (Result<Coordinates>) -> Unit,
    private val log: (String, String) -> Unit) : LocationGateway {
    private val main = Handler(Looper.getMainLooper())
    private var sdk: FusedLocation? = null
    private var generation = 0
    private var listener: FusedLocationListener? = null
    private val alerts = mutableSetOf<String>()
    private var request = 0
    private var areaGeneration = 0
    override fun start(continuous: Boolean) {
        stop()
        val token = ++generation
        if (context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") != PackageManager.PERMISSION_GRANTED) {
            publish(Result(ResultState.PermissionDenied, detail = "Pleos 위치 권한을 허용해 주세요")); return
        }
        try {
            val client = FusedLocation(context)
            if (!client.isSupported()) { publish(Result(ResultState.Unsupported)); return }
            sdk = client
            client.initialize()
            publish(Result(ResultState.Loading))
            val callback = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) { main.post {
                    if (generation != token) return@post
                    request++
                    val point = location?.let { Coordinates(it.latitude, it.longitude, it.timestampMillis) }
                    publish(when {
                        point == null -> Result(ResultState.NoData, detail = "마지막 위치 없음")
                        !point.valid() -> Result(ResultState.Error, detail = "잘못된 위치 응답")
                        !point.fresh() -> Result(ResultState.NoData, detail = "위치가 오래되어 재수신 필요")
                        else -> Result.success(point)
                    })
                    log("Location callback", if(point?.fresh() == true) "수신" else "유효한 최신 위치 없음")
                } }
                override fun onError(exception: Exception) { main.post {
                    if(generation == token) { request++; failure(exception) }
                } }
            }
            listener = callback
            val waiting = ++request
            client.getLastKnownLocation(callback)
            if(continuous) client.registerFusedLocationCallback(callback)
            main.postDelayed({
                if(generation == token && request == waiting) publish(Result(ResultState.Error, detail = "위치 응답 시간 초과"))
            }, 15000)
            log("Location start", if(continuous) "실시간 위치 요청" else "마지막 위치 요청")
        } catch(e: Exception) { failure(e) }
    }
    private fun failure(e: Exception) {
        val code = (e as? LocationException)?.errorCode
        val state = when {
            e is SecurityException || code == ErrorCode.ERROR_SECURITY_PERMISSION -> ResultState.PermissionDenied
            code == ErrorCode.ERROR_LOCATION_DISABLED -> ResultState.Blocked
            code == ErrorCode.ERROR_EMPTY_RESULT -> ResultState.NoData
            code == ErrorCode.ERROR_SERVICE_CONNECTION_FAILURE -> ResultState.Disconnected
            else -> ResultState.Error
        }
        val message = code?.name ?: e.javaClass.simpleName
        publish(Result(state, detail = message)); log("Location error", message)
    }
    override fun showBlocked() { try { sdk?.showLocationAccessBlockedPopup() } catch(e: Exception) { failure(e) } }
    override fun addArea(center: Coordinates, radius: Float) {
        require(center.valid() && radius.isFinite() && radius > 0)
        val client = sdk ?: run { log("Proximity", "위치 SDK 미연결"); return }
        val token = generation
        val areaToken = areaGeneration
        try {
            client.addProximityAlert(ProximityAlertRequest(center.latitude, center.longitude, radius, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)),
                object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) { main.post {
                        if(token == generation && areaToken == areaGeneration) { alerts.add(alertId); log("Proximity", "등록 콜백 수신") }
                        else runCatching { client.removeProximityAlert(alertId) }
                    } }
                    override fun onTransition(event: ProximityAlertEvent) { main.post { if(token == generation && areaToken == areaGeneration) log("Proximity transition", event.transition.name) } }
                    override fun onError(exception: Exception) { main.post { if(token == generation) failure(exception) } }
                })
            log("Proximity", "등록 요청 · 콜백 대기")
        } catch(e: Exception) { failure(e) }
    }
    override fun removeAreas() {
        areaGeneration++
        alerts.toList().forEach { id -> runCatching { sdk?.removeProximityAlert(id) }.onFailure { log("Proximity remove", it.javaClass.simpleName) } }
        alerts.clear()
    }
    override fun stop() {
        generation++; request++
        val client = sdk
        removeAreas()
        listener?.let { callback -> runCatching { client?.unregisterFusedLocationCallback(callback) } }
        listener = null; sdk = null
        runCatching { client?.release() }
    }
}
