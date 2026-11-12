package com.example.vemoring.vehicle

import android.content.Context
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.Executors

/** No Android Car class is loaded on non-AAOS devices. One 1Hz subscription, no polling loop. */
class CarSpeedSource(private val context:Context) {
    private var backend:Backend?=null
    private var publish:((Reading)->Unit)?=null
    fun start(changed:(Reading)->Unit) {
        close();publish=changed
        if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            changed(Reading(ReadingState.Unsupported,reason="Android Automotive 차속 미지원"));return
        }
        if(context.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED) {
            changed(Reading(ReadingState.PermissionDenied,reason="차량 속도 접근 권한 필요"));return
        }
        try { val next=AaosBackend(context,changed);backend=next;next.start() }
        catch(_:LinkageError) {backend?.close();backend=null;changed(Reading(ReadingState.Unsupported,reason="Android Car API 없음"))}
        catch(_:Exception) {backend?.close();backend=null;changed(Reading(ReadingState.Error,reason="차속 연결 실패"))}
    }
    fun refresh() {publish?.let {start(it)}}
    fun close() {backend?.close();backend=null;publish=null}
    private interface Backend {fun start();fun close()}

    @Suppress("DEPRECATION") // API28/official Pleos API34-compatible Car APIs, read-only.
    private class AaosBackend(val context:Context,val changed:(Reading)->Unit):Backend {
        private val main=Handler(Looper.getMainLooper())
        private val executor=Executors.newSingleThreadExecutor()
        @Volatile private var active=true
        @Volatile private var connected=false
        private val latestSample=java.util.concurrent.atomic.AtomicLong(0)
        private var car:android.car.Car?=null
        private var properties:android.car.hardware.property.CarPropertyManager?=null
        private val expired=Runnable {if(active) changed(Reading(ReadingState.NoData,reason="차속 업데이트 중단"))}
        private val timeout=Runnable {if(active) changed(Reading(ReadingState.Disconnected,reason="Car 서비스 연결 시간 초과"))}
        private fun emit(reading:Reading) {main.post {if(active && (reading.state!=ReadingState.Available || connected)) {main.removeCallbacks(expired);changed(reading)
            if(reading.state==ReadingState.Available) main.postDelayed(expired,
                (5001-(System.currentTimeMillis()-(reading.receivedAtMillis ?: 0))).coerceAtLeast(1))}}}
        private fun failure(e:Exception) = emit(Reading(if(e is SecurityException) ReadingState.PermissionDenied else ReadingState.Error,
            reason=if(e is SecurityException) "차량 속도 접근 권한 없음" else "차속 조회 실패"))
        private val callback=object:android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value:android.car.hardware.CarPropertyValue<*>) {
                if(!active || !connected) return
                if(value.propertyId!=android.car.VehiclePropertyIds.PERF_VEHICLE_SPEED) return
                if(value.status!=android.car.hardware.CarPropertyValue.STATUS_AVAILABLE) {emit(Reading(ReadingState.NoData));return}
                if(value.timestamp<=0) {emit(Reading(ReadingState.NoData,reason="차속 시각 미수신"));return}
                val previous=latestSample.get()
                if(value.timestamp<=previous || !latestSample.compareAndSet(previous,value.timestamp)) return
                emit(vehicleSpeedReading((value.value as? Number)?.toDouble(),value.timestamp,SystemClock.elapsedRealtimeNanos(),System.currentTimeMillis()))
            }
            override fun onErrorEvent(propId:Int,areaId:Int) {
                if(propId==android.car.VehiclePropertyIds.PERF_VEHICLE_SPEED) emit(Reading(ReadingState.Error,reason="차속 수신 오류"))
            }
        }
        private val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                if(!active) return
                connected=true;latestSample.set(0)
                main.removeCallbacks(timeout)
                executor.execute {
                    if(active) try {
                        val manager=car?.getCarManager(android.car.Car.PROPERTY_SERVICE) as? android.car.hardware.property.CarPropertyManager
                        if(manager==null) {emit(Reading(ReadingState.Unsupported));return@execute}
                        properties=manager
                        val id=android.car.VehiclePropertyIds.PERF_VEHICLE_SPEED
                        if(manager.getCarPropertyConfig(id)==null) {emit(Reading(ReadingState.Unsupported));return@execute}
                        if(!manager.registerCallback(callback,id,android.car.hardware.property.CarPropertyManager.SENSOR_RATE_NORMAL)) {
                            emit(Reading(ReadingState.Unsupported,reason="차속 구독 불가"));return@execute
                        }
                        manager.getProperty<Float>(id,0)?.let {callback.onChangeEvent(it)}
                    } catch(e:Exception) {failure(e)}
                }
            }
            override fun onServiceDisconnected(name:ComponentName) {connected=false;emit(Reading(ReadingState.Disconnected))}
        }
        override fun start() {
            emit(Reading(ReadingState.Loading))
            main.postDelayed(timeout,10000)
            car=android.car.Car.createCar(context,connection,main)
            car?.connect() ?: emit(Reading(ReadingState.Unsupported))
        }
        override fun close() {
            active=false;connected=false;main.removeCallbacks(expired);main.removeCallbacks(timeout)
            executor.execute {runCatching {properties?.unregisterCallback(callback)};runCatching {car?.disconnect()};properties=null;car=null}
            executor.shutdown()
        }
    }
    companion object {const val PERMISSION="android.car.permission.CAR_SPEED"}
}
