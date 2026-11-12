package com.example.vemoring.vehicle

import android.content.Context

/** Main-thread ownership: the screen and monitor share one SDK session. */
object SharedVehicleConnection {
    private var connection: VehicleRepository? = null
    private val owners = mutableSetOf<Any>()
    private var locations: com.example.vemoring.integration.PleosLocationGateway? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var locationRetry: Runnable? = null
    private var retries = 0
    private var generation = 0
    private var lastFixAt = Long.MIN_VALUE
    val locationMessage = kotlinx.coroutines.flow.MutableStateFlow("위치 미연결")
    val travelPoint = kotlinx.coroutines.flow.MutableStateFlow<com.vemoring.companion.TravelPoint?>(null)
    fun repository(context: Context): VehicleRepository = connection ?: VehicleRepository(
        PleosVehicleGateway(context.applicationContext)).also { connection = it }
    fun acquire(context: Context, owner: Any) {
        if (owners.add(owner)) repository(context).start()
        com.example.vemoring.VehicleWashRuntime.start(context)
        if(locations==null) startLocation(context)
    }
    /** Explicit refresh restarts the source even when an earlier SDK object still exists. */
    fun retryLocation(context: Context) {
        retries=0
        if(owners.isNotEmpty()) startLocation(context)
    }
    private fun startLocation(context: Context) {
        locationRetry?.let(handler::removeCallbacks); locationRetry=null
        generation++
        locations?.stop(); locations=null; travelPoint.value=null
        if(owners.isEmpty()) return
        locationMessage.value="위치 연결 중"
        val token=generation
        if(context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locations=com.example.vemoring.integration.PleosLocationGateway(context.applicationContext, { result ->
                if(token!=generation) return@PleosLocationGateway
                val p=result.value.takeIf {result.state==com.example.vemoring.integration.ResultState.Available}
                travelPoint.value=p?.let {com.vemoring.companion.TravelPoint(it.latitude,it.longitude,java.time.Instant.ofEpochMilli(it.timestamp))}
                p?.let {com.example.vemoring.VehicleWashRuntime.observe(it)}
                if(p!=null) {
                    if(p.timestamp > lastFixAt) {
                        lastFixAt=p.timestamp; retries=0
                        locationRetry?.let(handler::removeCallbacks); locationRetry=null
                        locationMessage.value="위치 수신 중"
                    }
                } else if(result.state in setOf(com.example.vemoring.integration.ResultState.Error,
                        com.example.vemoring.integration.ResultState.Disconnected,com.example.vemoring.integration.ResultState.NoData)) {
                    if(locationRetry==null) {
                        val wait=com.vemoring.companion.ConnectionRetry.delayAfterFailure(retries)
                        if(wait==null) locationMessage.value="위치 재연결 실패 · 권한·위치 설정을 확인한 뒤 새로고침해 주세요"
                        else {
                            retries++
                            locationMessage.value="위치 재연결 중 ($retries/3)"
                            locationRetry=Runnable {if(token==generation) startLocation(context.applicationContext)}.also {handler.postDelayed(it,wait)}
                        }
                    }
                } else if(result.state!=com.example.vemoring.integration.ResultState.Loading) {
                    locationRetry?.let(handler::removeCallbacks); locationRetry=null
                    locationMessage.value="위치 사용 불가 · 권한과 차량 위치 설정을 확인한 뒤 새로고침해 주세요"
                }
            }, { _,_ -> /* No coordinate logging. Unavailable location means basic distance only. */ }).also {it.start(true)}
        } else locationMessage.value="차량 위치 권한을 허용한 뒤 새로고침해 주세요"
    }
    fun release(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty()) {
            generation++; locationRetry?.let(handler::removeCallbacks); locationRetry=null; retries=0
            locationMessage.value="위치 미연결"
            connection?.close(); locations?.stop(); locations=null; travelPoint.value=null
            com.example.vemoring.VehicleWashRuntime.stop()
        }
    }
}
