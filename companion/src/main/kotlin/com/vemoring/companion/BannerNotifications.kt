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
            val oilQuestion=event.bannerKey=="OIL_CONFIRMATION"
            val question = event.bannerKey == "WASH_QUESTION" || oilQuestion
            val channel = if(question) CareNotifications.WASH_CONFIRMATION_CHANNEL else CHANNEL
            manager.createNotificationChannel(NotificationChannel(channel,if(question) "관리 기록 인식 확인" else "차량 상태 배너",NotificationManager.IMPORTANCE_HIGH))
            val blocked = when {
                !enabled -> NotificationDelivery.Disabled
                !CareNotifications.allowed(c) -> NotificationDelivery.PermissionBlocked
                manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE -> NotificationDelivery.ChannelBlocked
                else -> null
            }
            if(blocked != null) { history.delivery(event.vehicleId,event.eventId,blocked); return blocked }
            val launch = notificationActivityIntent(c)
            val open = PendingIntent.getActivity(c,event.eventId.hashCode(),launch
                .setData(uri(event.vehicleId,event.eventId)).putExtra("vehicleId",event.vehicleId).putExtra("careEvent",event.eventId).putExtra("openCareRecords",oilQuestion),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val face=when(event.mood) {"FUEL"->"fuel";"WASH"->"wash";"OIL"->"oil";"ANGRY"->"angry";"EXHAUSTED"->"exhausted";else->"normal"}
            val iconId = c.resources.getIdentifier("face_$face","drawable",c.packageName).takeIf {it!=0}
                ?: c.resources.getIdentifier("ic_care_notification","drawable",c.packageName).takeIf {it!=0} ?: android.R.drawable.ic_dialog_info
            val builder = Notification.Builder(c,channel).setSmallIcon(iconId).setContentTitle(event.title).setContentText(event.body)
                .setColor(notificationColor(event.mood))
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
                builder.addAction(Notification.Action.Builder(null,if(oilQuestion) "교체 안 했어요" else "세차 안 했어요",cancel).build())
                val confirm = PendingIntent.getBroadcast(c,(event.eventId+":confirm").hashCode(),Intent(c,CareCancelReceiver::class.java)
                    .setData(uri(event.vehicleId,event.visitId).buildUpon().appendQueryParameter("answer","yes").build())
                    .putExtra("vehicleId",event.vehicleId).putExtra("visitId",event.visitId).putExtra("confirmed",true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if(!oilQuestion) builder.addAction(Notification.Action.Builder(null,"맞아요",confirm).build())
            }
            builder.addAction(Notification.Action.Builder(null,if(oilQuestion) "기록 보기" else "알림 내역",open).build())
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
    fun oilDetected(c:Context,journal:CareJournal,visit:EngineOilVisit) {
        val profile=JSONObject(requireNotNull(journal.authority()).profile)
        val d=definition("OIL_CONFIRMATION",profile)
        dispatch(c,CareNotificationEntry(journal.vehicleId,"detected:${visit.id}",d.key,d.title,d.body,d.mood,d.art,
            visit.exit!!.at,source=if(profile.optBoolean("simulation")) "Simulation" else "Real",visitId=visit.id),profile.optBoolean("notifications",true))
    }

    fun evaluate(c: Context,journal: CareJournal,authority: CareAuthority,score: WashScore,telemetry: JSONObject?,snapshot: JSONObject? = null) {
        val profile = JSONObject(authority.profile)
        val enabled = profile.optBoolean("notifications",true)
        val dirty = score.displayed?.let { it <= profile.getInt("washAlert") } == true
        val electric = profile.optString("fuel")=="전기차"
        val energy = if(electric) listOf("battery") else listOf("fuel")
        val freshSnapshot=snapshot?.takeIf {System.currentTimeMillis()-it.optLong("observedAt",System.currentTimeMillis()) in 0..60000}
        fun freshField(name:String)=freshSnapshot!=null && (!freshSnapshot.has(name) ||
            !freshSnapshot.isNull(name) && System.currentTimeMillis()-freshSnapshot.optLong(name) in 0..60000)
        val percentages = energy.mapNotNull { key -> freshSnapshot?.takeIf {freshField("${key}At")}?.takeUnless {it.isNull(key)}?.optDouble(key)?.takeIf {it.isFinite() && it in 0.0..100.0} }
        // Fuel cars: the vehicle's sticky low episode decides the alert (sloshing around T cannot re-alert); a refuel needs
        // the vehicle's three-read +10%p confirmation. The last received state covers evaluations without fresh telemetry.
        val receivedEpisode=if(electric) null else telemetry?.optJSONObject("fuelEpisode")?.let {runCatching {FuelEpisode.from(it)}.getOrNull()}
        val episode=if(electric) null else (receivedEpisode?.let {next -> journal.updateFuelEpisode {old -> if(next.episode>=old.episode) next else old}}
            ?: journal.fuelEpisode()).takeIf {it.phase!=FuelPhase.UNKNOWN}
        val fuel = if(episode!=null) episode.phase==FuelPhase.LOW else percentages.any {it<=profile.getInt("fuelAlert")}
        val now = System.currentTimeMillis()
        val source = if(profile.optBoolean("simulation")) "Simulation" else "Real"
        val oilAssessment=EngineOilCatalog.assess(c,JSONObject(profile.toString()).put("vehicleId",journal.vehicleId),authority.records)
        val oilDue=oilAssessment.due==true
        val banner = BannerPolicy.issues(fuel,dirty,oilDue)
        if(banner != null) {
            val base=BannerPolicy.forProfile(banner,profile)
            val d=if(oilDue && oilAssessment.percent==null && oilAssessment.reason.contains("설치 이후")) base.copy(body=oilAssessment.reason) else base
            val washOnly=d.key=="WASH_NEEDED"
            if(!washOnly || score.cycleId==null || !journal.hasNotified("wash:${score.cycleId}")) {
                val eventId=if(oilDue) "oil-due:${oilAssessment.cycleId}:${d.key}:${if(fuel) episode?.episode else ""}:${if(dirty) score.cycleId else ""}" else if(washOnly && score.cycleId!=null) "wash:${score.cycleId}" else NotificationHistory(c).use {
                    it.stateEvent(journal.vehicleId,"${d.key}:${if(dirty) score.cycleId else ""}:${if(oilDue) oilAssessment.cycleId else ""}")
                }
                NotificationHistory(c).use { h -> h.value(journal.vehicleId,"last:state")?.takeIf {it!=eventId}?.let {
                    c.getSystemService(NotificationManager::class.java).cancel("${journal.vehicleId}:$it",3010)
                } }
                val event=CareNotificationEntry(journal.vehicleId,eventId,d.key,d.title,d.body,d.mood,d.art,now,source=source)
                if(dispatch(c,event,enabled)==NotificationDelivery.Delivered && dirty && score.cycleId!=null) journal.markNotified("wash:${score.cycleId}")
                NotificationHistory(c).use {it.value(journal.vehicleId,"last:state",eventId)}
            }
        } else if(!fuel && !dirty && !oilDue && (percentages.isNotEmpty() || episode?.phase==FuelPhase.ARMED)) {
            NotificationHistory(c).use {it.stateEvent(journal.vehicleId,"clear")}
        }
        // One "주유 완료" per confirmed refuel event id; repeated/replayed frames are deduplicated by the history.
        episode?.refills?.filter {now-it.at in 0..6*3600_000L}?.forEach { refill ->
            val eventId="fuel-refill:${refill.id}"
            NotificationHistory(c).use { h ->
                if(h.get(journal.vehicleId,eventId)==null) h.value(journal.vehicleId,"last:state")?.let { id ->
                    // Retire this episode's low-fuel notification once the refuel is confirmed.
                    if(!fuel && h.get(journal.vehicleId,id)?.bannerKey?.contains("FUEL")==true)
                        c.getSystemService(NotificationManager::class.java).cancel("${journal.vehicleId}:$id",3010)
                }
            }
            val d=definition("FUEL_DONE",profile)
            dispatch(c,CareNotificationEntry(journal.vehicleId,eventId,d.key,d.title,d.body,d.mood,d.art,refill.at,source=source),enabled)
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
            val manual = r.optString("source","Manual") == "Manual"
            val prior = old[r.getString("id")]
            if(!manual || prior != null && prior.optString("date")==today && prior.optString("source","Manual")=="Manual") return@repeat
            val kind = r.getString("kind")
            val completion = BannerPolicy.recordCompletion(kind,MaintenanceType.from(r.optString("maintenanceType"))) ?: return@repeat
            val d = BannerPolicy.forProfile(completion,p)
            val eventId="record:${r.getString("id")}:$today"
            dispatch(c,CareNotificationEntry(after.vehicleId,eventId,d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source=if(p.optBoolean("simulation")) "Simulation" else "UserRecord"),p.optBoolean("notifications",true))
            completed += if(kind == "oil") "maintenance" else kind
        }
        if(completed.toSet().containsAll(listOf("fuel","wash","maintenance"))) {
            val d=definition("ALL_DONE",p)
            dispatch(c,CareNotificationEntry(after.vehicleId,"all-done:${after.version}",d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source=if(p.optBoolean("simulation")) "Simulation" else "UserRecord"),p.optBoolean("notifications",true))
        }
    }

    fun cancelVisit(c: Context,vehicle: String,visit: String) { NotificationHistory(c).use { it.cancelVisit(vehicle,visit) } }
    private fun uri(vehicle:String,event:String): Uri = Uri.Builder().scheme("vemoring").authority("notification").appendPath(vehicle).appendPath(event).build()
    // System UI tints the monochrome small icon/badge from this per-event accent, independently of the launcher.
    private fun notificationColor(mood:String): Int = when(mood) {
        "FUEL" -> 0xFFFFC107.toInt()
        "WASH" -> 0xFFE86BA2.toInt()
        "OIL" -> 0xFF8E5ACC.toInt()
        "ANGRY" -> 0xFFE5484D.toInt()
        "EXHAUSTED" -> 0xFF747C88.toInt()
        else -> 0xFF087BBF.toInt()
    }
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
