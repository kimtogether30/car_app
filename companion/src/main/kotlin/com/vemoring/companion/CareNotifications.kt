package com.vemoring.companion

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import org.json.JSONObject

/** Phone notifications keep the original public boundary; archive even when OS delivery is disabled. */
object CareNotifications {
    const val WASH_CONFIRMATION_CHANNEL = "vemoring_wash_confirmation_v2"
    fun allowed(c: Context): Boolean = (Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun cancelDetection(c: Context,vehicleId: String,visitId: String) {
        c.getSystemService(NotificationManager::class.java).cancel("$vehicleId:detected:$visitId",3011)
    }
    fun detected(c: Context,store: CareJournal,wash: DetectedWash) = BannerNotifications.washDetected(c,store,wash,
        JSONObject(store.authority()!!.profile).optBoolean("notifications",true))
    fun evaluate(c: Context,journal: CareJournal,authority: CareAuthority,score: WashScore,telemetry: JSONObject?,snapshot: JSONObject? = null) =
        BannerNotifications.evaluate(c,journal,authority,score,telemetry,snapshot)
}

class CareCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val vehicle=intent.getStringExtra("vehicleId") ?: return
        val visit=intent.getStringExtra("visitId") ?: return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if(intent.getBooleanExtra("confirmed",false)) CareExchange.dismissConfirmation(context,vehicle,visit)
                else CareExchange.cancel(context,vehicle,visit)
            } finally { pending.finish() }
        }
    }
}
