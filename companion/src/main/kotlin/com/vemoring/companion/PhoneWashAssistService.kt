package com.vemoring.companion

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class WashAssistTarget(val vehicleId:String,val visitId:String,val expiresAt:Long)
object WashAssistSignals {
    val target=MutableStateFlow<WashAssistTarget?>(null)
    val running=MutableStateFlow(false)
    val status=MutableStateFlow("세차 위치 보완 꺼짐")
}

/** Started explicitly while the phone UI is visible. No boot/background permission bypass or vehicle control. */
class PhoneWashAssistService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val input=Channel<Pair<WashAssistTarget,FacilityFix>>(Channel.CONFLATED)
    private var collecting:Job?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() {
        super.onCreate()
        scope.launch(Dispatchers.IO) {
            MirrorBus.load(applicationContext)
            if(WashAssistSignals.target.value==null) MirrorBus.received.value?.snapshot?.let {snapshot ->
                if(!org.json.JSONObject(snapshot.content).optBoolean("demo")) {
                    val candidate=FacilityWashStore(applicationContext,snapshot.vehicleId).phoneCandidate(System.currentTimeMillis())
                    WashAssistSignals.target.value=candidate?.let {WashAssistTarget(snapshot.vehicleId,it.id,it.fixes.first().at+FacilityWashPolicy.LIFETIME_MS)}
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            for((target,fix) in input) runCatching {FacilityWashStore(applicationContext,target.vehicleId).observePhone(target.visitId,fix)}
        }
    }
    @android.annotation.SuppressLint("MissingPermission") // Shared vehicle build has no phone GPS permission; both start/target checks below fail closed.
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==STOP || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            stopSelf();return START_NOT_STICKY
        }
        try {
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"세차 위치 보완",NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,1,Intent(this,PhoneWashAssistService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
            val open=PendingIntent.getActivity(this,0,notificationActivityIntent(this),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification=Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("베모링 세차 위치 보완")
                .setContentText("차량이 시작한 세차장 방문에서만 휴대폰 위치를 사용해요")
                .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"보완 끄기",stop).build()).build()
            if(Build.VERSION.SDK_INT>=29) startForeground(3105,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(3105,notification)
            WashAssistSignals.running.value=true
            if(collecting==null) collecting=scope.launch {
                WashAssistSignals.target.collectLatest {target ->
                    if(target==null || target.expiresAt<=System.currentTimeMillis()) {
                        WashAssistSignals.status.value="세차장 방문 대기 중";return@collectLatest
                    }
                    // A later target can arrive after the start-time permission check (or a revocation).
                    if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                        WashAssistSignals.status.value="정확한 위치 권한 필요";return@collectLatest
                    }
                    val locations=getSystemService(LocationManager::class.java)
                    if(locations==null || LocationManager.GPS_PROVIDER !in locations.allProviders) {
                        WashAssistSignals.status.value="휴대폰 GPS 수신 불가";return@collectLatest
                    }
                    val listener=object:LocationListener {
                        override fun onLocationChanged(location:Location) {
                            val now=System.currentTimeMillis()
                            val age=SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos
                            if(age !in 0..15_000_000_000L) return
                            val fix=FacilityFix(location.latitude,location.longitude,now-age/1_000_000,now,true,
                                if(location.hasAccuracy()) location.accuracy.toDouble() else null)
                            input.trySend(target to fix)
                        }
                        @Deprecated("Legacy provider callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
                        override fun onProviderDisabled(provider:String) {WashAssistSignals.status.value="휴대폰 GPS 수신 불가"}
                        override fun onProviderEnabled(provider:String) {WashAssistSignals.status.value="방문 위치 보완 중"}
                    }
                    try {
                        locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000L,0f,listener,Looper.getMainLooper())
                        WashAssistSignals.status.value=if(locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
                            "방문 위치 보완 중 · 출차는 차량 재수신 후 확인" else "휴대폰 GPS를 켜 주세요"
                        delay((target.expiresAt-System.currentTimeMillis()).coerceAtLeast(1))
                        WashAssistSignals.status.value="방문 보완 시간 종료"
                    } catch(_:SecurityException) {WashAssistSignals.status.value="정확한 위치 권한 필요"}
                    finally {runCatching {locations.removeUpdates(listener)}}
                }
            }
        } catch(_:RuntimeException) {WashAssistSignals.status.value="앱을 연 상태에서 위치 보완을 켜 주세요";stopSelf()}
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        collecting?.cancel();input.close();scope.cancel();WashAssistSignals.running.value=false
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL="vemoring_wash_assist"
        private const val STOP="com.vemoring.companion.WASH_ASSIST_STOP"
        fun startVisible(context:Context) {
            try {ContextCompat.startForegroundService(context,Intent(context,PhoneWashAssistService::class.java))}
            catch(_:RuntimeException) {WashAssistSignals.status.value="위치 보완을 시작하지 못했어요"}
        }
        fun stop(context:Context) {context.stopService(Intent(context,PhoneWashAssistService::class.java))}
    }
}
