package com.vemoring.companion

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Phone-side event selection, durable history, and actual Android notification delivery. Caller uses IO. */
object BannerNotifications {
    const val CHANNEL = "vemoring_vehicle_banners_v1"
    private fun definition(key: String, profile: JSONObject) = BannerPolicy.forProfile(BannerCatalog[key],profile)

    @Synchronized
    @android.annotation.SuppressLint("MissingPermission") // allowed() and channel checks precede notify; revocation is caught.
    fun dispatch(c: Context, event: CareNotificationEntry, enabled: Boolean = true): NotificationDelivery {
        if(event.source == "Simulation") require(c.packageName.endsWith(".verification") &&
            c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) { "Simulation requires the isolated verification app" }
        NotificationHistory(c).use { history ->
            val saved = history.record(event)
            if(saved.cancelled || saved.delivery == NotificationDelivery.Delivered) return saved.delivery
            val manager = c.getSystemService(NotificationManager::class.java)
            val question = event.bannerKey == "WASH_QUESTION"
            val channel = if(question) CareNotifications.WASH_CONFIRMATION_CHANNEL else CHANNEL
            manager.createNotificationChannel(NotificationChannel(channel,if(question) "세차 인식 확인" else "차량 상태 배너",NotificationManager.IMPORTANCE_HIGH))
            val blocked = when {
                !enabled -> NotificationDelivery.Disabled
                !CareNotifications.allowed(c) -> NotificationDelivery.PermissionBlocked
                manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE -> NotificationDelivery.ChannelBlocked
                else -> null
            }
            if(blocked != null) { history.delivery(event.vehicleId,event.eventId,blocked); return blocked }
            val launch = c.packageManager.getLaunchIntentForPackage(c.packageName)
                ?: Intent().setClassName(c.packageName,"${c.packageName}.MainActivity")
            val open = PendingIntent.getActivity(c,event.eventId.hashCode(),launch
                .setData(uri(event.vehicleId,event.eventId)).putExtra("vehicleId",event.vehicleId).putExtra("careEvent",event.eventId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val iconId = c.resources.getIdentifier("ic_care_notification","drawable",c.packageName).takeIf {it!=0} ?: android.R.drawable.ic_dialog_info
            val builder = Notification.Builder(c,channel).setSmallIcon(iconId).setContentTitle(event.title).setContentText(event.body)
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setShowWhen(true).setWhen(event.occurredAt)
                .setLargeIcon(character(c,event.mood))
            if(event.source == "Simulation") builder.setSubText("시뮬레이션")
            val image = artwork(c,event.art)
            if(image != null) builder.setStyle(Notification.BigPictureStyle().bigPicture(image).setSummaryText(event.body))
            else builder.setStyle(Notification.BigTextStyle().bigText(event.body))
            if(question && event.visitId != null) {
                val cancel = PendingIntent.getBroadcast(c,event.eventId.hashCode(),Intent(c,CareCancelReceiver::class.java)
                    .setData(uri(event.vehicleId,event.visitId)).putExtra("vehicleId",event.vehicleId).putExtra("visitId",event.visitId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                builder.addAction(Notification.Action.Builder(null,"잘못 인식함",cancel).build())
            }
            builder.addAction(Notification.Action.Builder(null,"알림 내역",open).build())
            val result = runCatching {
                manager.notify("${event.vehicleId}:${event.eventId}",if(event.visitId != null) 3011 else 3010,builder.build())
                NotificationDelivery.Delivered
            }.getOrElse { NotificationDelivery.Error }
            history.delivery(event.vehicleId,event.eventId,result)
            return result
        }
    }

    fun washDetected(c: Context,journal: CareJournal,wash: DetectedWash,enabled: Boolean) {
        val profile = JSONObject(journal.authority()!!.profile)
        val key = if(wash.evidence == WashCompletionEvidence.EstimatedVisit) "WASH_QUESTION" else "WASH_DONE"
        val d = definition(key,profile)
        val event = CareNotificationEntry(journal.vehicleId,"detected:${wash.visitId}",d.key,d.title,d.body,d.mood,d.art,
            wash.completedAt.toEpochMilli(),source=if(profile.optBoolean("simulation")) "Simulation" else "Real",visitId=wash.visitId)
        if(dispatch(c,event,enabled) == NotificationDelivery.Delivered) journal.markNotified(event.eventId)
    }

    fun evaluate(c: Context,journal: CareJournal,authority: CareAuthority,score: WashScore,telemetry: JSONObject?,snapshot: JSONObject? = null) {
        val profile = JSONObject(authority.profile)
        val enabled = profile.optBoolean("notifications",true)
        val dirty = score.displayed?.let { it <= profile.getInt("washAlert") } == true
        val energy = if(profile.optString("fuel")=="전기차") listOf("battery") else if(profile.optString("fuel")=="하이브리드") listOf("fuel","battery") else listOf("fuel")
        val freshSnapshot=snapshot?.takeIf {System.currentTimeMillis()-it.optLong("observedAt",System.currentTimeMillis()) in 0..60000}
        fun freshField(name:String)=freshSnapshot!=null && (!freshSnapshot.has(name) ||
            !freshSnapshot.isNull(name) && System.currentTimeMillis()-freshSnapshot.optLong(name) in 0..60000)
        val percentages = energy.mapNotNull { key -> freshSnapshot?.takeIf {freshField("${key}At")}?.takeUnless {it.isNull(key)}?.optDouble(key)?.takeIf {it.isFinite() && it in 0.0..100.0} }
        val fuel = if(percentages.isNotEmpty()) percentages.any {it<=profile.getInt("fuelAlert")} else
            freshField("lowFuelAt") && freshSnapshot?.optJSONObject("signals")?.optBoolean("lowFuel")==true
        val warnings = telemetry?.optJSONObject("warnings")
        val now = System.currentTimeMillis()
        val eligible = mutableListOf<String>()
        val known = mutableMapOf<String,Boolean>()
        warnings?.keys()?.forEach { type ->
            val w = warnings.optJSONObject(type) ?: return@forEach
            if(w.optString("status") != "Available" || w.optString("source","Real") != "Real" || now-w.optLong("at") !in 0..60000) return@forEach
            val validated = w.optBoolean("eligible") || type in listOf("WASHER","ENGINE_OIL","BRAKE_FLUID","TRANSMISSION_OIL")
            if(!validated) return@forEach // Raw ON/BLINK/color alone cannot diagnose an unvalidated telltale.
            if(w.has("warning") && !w.isNull("warning")) {
                known[type]=w.getBoolean("warning")
                if(w.getBoolean("warning")) eligible += type
            }
        }
        val groups = eligible.groupBy { BannerPolicy.maintenanceGroups[it] ?: "VEHICLE_WARNING" }.toMutableMap()
        val warningSignal=freshSnapshot?.optJSONObject("signals")
        if(freshField("vehicleWarningAt") && warningSignal?.has("vehicleWarning")==true && !warningSignal.isNull("vehicleWarning"))
            known["UNCLASSIFIED_WARNING"]=warningSignal.getBoolean("vehicleWarning")
        if(groups.isEmpty() && known["UNCLASSIFIED_WARNING"]==true) groups["VEHICLE_WARNING"]=listOf("UNCLASSIFIED_WARNING")
        val source = if(profile.optBoolean("simulation")) "Simulation" else "Real"
        val candidates: List<Pair<String?,List<String>>> = if(groups.isEmpty()) listOf(null to emptyList()) else groups.entries.sortedBy {it.key}.map {it.key to it.value.sorted()}
        if(groups.isNotEmpty()) NotificationHistory(c).use { h ->
            h.value(journal.vehicleId,"last:state")?.let {c.getSystemService(NotificationManager::class.java).cancel("${journal.vehicleId}:$it",3010)}
            h.stateEvent(journal.vehicleId,"covered-by-maintenance")
        }
        for((group,types) in candidates) {
            val banner=BannerPolicy.issues(fuel,dirty,types.firstOrNull()) ?: continue
            val d=BannerPolicy.forProfile(banner,profile)
            val washOnly=d.key=="WASH_NEEDED"
            if(washOnly && score.cycleId!=null && journal.hasNotified("wash:${score.cycleId}")) continue
            val lane=group ?: "state"
            val eventId=if(washOnly && score.cycleId!=null) "wash:${score.cycleId}" else NotificationHistory(c).use {
                it.stateEvent(journal.vehicleId,"${d.key}:${types.joinToString()}:${if(dirty) score.cycleId else ""}",lane)
            }
            val occurred=types.mapNotNull {type -> warnings?.optJSONObject(type)?.optLong("at")?.takeIf {it>0}}.maxOrNull() ?: now
            val event=CareNotificationEntry(journal.vehicleId,eventId,d.key,d.title,d.body,d.mood,d.art,occurred,source=source)
            NotificationHistory(c).use { h -> h.value(journal.vehicleId,"last:$lane")?.takeIf {it!=eventId}?.let {
                c.getSystemService(NotificationManager::class.java).cancel("${journal.vehicleId}:$it",3010)
            } }
            if(dispatch(c,event,enabled)==NotificationDelivery.Delivered && dirty && score.cycleId!=null) journal.markNotified("wash:${score.cycleId}")
            NotificationHistory(c).use {
                it.value(journal.vehicleId,"active:$lane",JSONArray(types).toString())
                it.value(journal.vehicleId,"last:$lane",eventId)
            }
        }
        // Only explicit current normal readings clear their own notification. Disconnects do not.
        NotificationHistory(c).use { h ->
            for(group in BannerPolicy.maintenanceGroups.values.toSet()+"VEHICLE_WARNING") {
                if(group in groups) continue
                val prior=h.value(journal.vehicleId,"active:$group")?.let(::JSONArray) ?: continue
                val priorTypes=(0 until prior.length()).map {prior.getString(it)}
                if(priorTypes.isNotEmpty() && priorTypes.all {known[it]==false}) {
                    h.value(journal.vehicleId,"last:$group")?.let { c.getSystemService(NotificationManager::class.java).cancel("${journal.vehicleId}:$it",3010) }
                    h.stateEvent(journal.vehicleId,"clear",group)
                    h.value(journal.vehicleId,"active:$group","[]")
                }
            }
            if(!fuel && !dirty && percentages.isNotEmpty()) h.stateEvent(journal.vehicleId,"clear")
        }
        warnings?.optJSONObject("WASHER")?.let { w ->
            if(w.optString("status") == "Available" && now-w.optLong("at") in 0..60000 && w.has("warning") && !w.isNull("warning"))
                journal.saveWarning("WASHER",if(w.getBoolean("warning")) "LOW" else "NORMAL")
        }
    }

    /** Explicit current-day user records are completion events; normal warning values are not repairs. */
    fun recordChanges(c: Context,before: CareAuthority?,after: CareAuthority) {
        if(before == null || before.records == after.records) return
        val previous = JSONArray(before.records)
        val old = (0 until previous.length()).associate { previous.getJSONObject(it).getString("id") to previous.getJSONObject(it) }
        val records = JSONArray(after.records)
        val newRecords=(0 until records.length()).associate {records.getJSONObject(it).getString("id") to records.getJSONObject(it)}
        old.forEach { (id,record) ->
            val visit=record.optString("visitId").takeUnless {it.isBlank() || it=="null"}
            if(visit!=null && (newRecords[id]==null || newRecords[id]?.optBoolean("cancelled")==true)) {
                cancelVisit(c,after.vehicleId,visit)
                CareNotifications.cancelDetection(c,after.vehicleId,visit)
            }
        }
        val today = LocalDate.now(CARE_ZONE).toString()
        val p = JSONObject(after.profile)
        val completed = mutableListOf<String>()
        repeat(records.length()) { n -> val r=records.getJSONObject(n)
            if(r.optBoolean("cancelled") || r.optString("date") != today) return@repeat
            val manual = r.optString("source","Manual") == "Manual" && r.optString("completedAt").let {it.isBlank() || it=="null"}
            val prior = old[r.getString("id")]
            if(!manual || prior != null && prior.optString("date")==today && prior.optString("source","Manual")=="Manual") return@repeat
            val kind = r.getString("kind")
            val key = when(kind) { "wash" -> "WASH_DONE"; "fuel" -> "FUEL_DONE"; "oil" -> "OIL_DONE"; else -> return@repeat }
            val d = definition(key,p)
            val eventId="record:${r.getString("id")}:$today"
            dispatch(c,CareNotificationEntry(after.vehicleId,eventId,d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source=if(p.optBoolean("simulation")) "Simulation" else "UserRecord"),p.optBoolean("notifications",true))
            completed += kind
        }
        if(completed.toSet().containsAll(listOf("fuel","wash","oil"))) {
            val d=definition("ALL_DONE",p)
            dispatch(c,CareNotificationEntry(after.vehicleId,"all-done:${after.version}",d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source=if(p.optBoolean("simulation")) "Simulation" else "UserRecord"),p.optBoolean("notifications",true))
        }
    }

    fun cancelVisit(c: Context,vehicle: String,visit: String) { NotificationHistory(c).use { it.cancelVisit(vehicle,visit) } }
    private fun uri(vehicle:String,event:String): Uri = Uri.Builder().scheme("vemoring").authority("notification").appendPath(vehicle).appendPath(event).build()
    private fun character(c: Context,mood:String): Icon? {
        val name=when(mood) { "FUEL"->"fuel";"WASH"->"wash";"OIL"->"oil";"ANGRY"->"angry";"EXHAUSTED"->"exhausted";else->"normal" }
        val id=c.resources.getIdentifier("mascot_$name","drawable",c.packageName)
        return id.takeIf {it!=0}?.let {Icon.createWithResource(c,it)}
    }
    private fun artwork(c: Context,art: String?): Bitmap? {
        if(art == null) return null
        val dark = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val id = c.resources.getIdentifier(art+if(dark) "_dark" else "_light","drawable",c.packageName)
        if(id==0) return null
        val raw = BitmapFactory.decodeResource(c.resources,id) ?: return null
        if(raw.width <= 720) return raw
        return Bitmap.createScaledBitmap(raw,720,raw.height*720/raw.width,true).also { if(it!==raw) raw.recycle() }
    }
}
