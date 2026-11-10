package com.example.vemoring.phone

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Synthetic inputs -> real Android notifications. No Bluetooth radio or SDK success is claimed. */
class BannerNotificationSimulationTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val id="simulation-${UUID.randomUUID()}"
    private val db="banner_sim_${id}_care_notification_history_v1.db"
    private val context=object:ContextWrapper(base) {
        override fun getApplicationContext():Context=this
        override fun getDatabasePath(name:String)=base.getDatabasePath("banner_sim_${id}_$name")
        override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=base.openOrCreateDatabase("banner_sim_${id}_$name",mode,factory)
        override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler:android.database.DatabaseErrorHandler?)=base.openOrCreateDatabase("banner_sim_${id}_$name",mode,factory,errorHandler)
        override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("banner_sim_${id}_$name",mode)
    }
    private val manager get()=base.getSystemService(NotificationManager::class.java)
    private fun profile()=JSONObject().put("fuel","휘발유").put("fuelAlert",20).put("washAlert",30)
        .put("notifications",true).put("simulation",true)
    private fun seed(records:JSONArray=JSONArray()):CareJournal=CareJournal(context,id).apply {
        saveAuthority(CareAuthority(id,1,profile().toString(),records.toString()))
    }
    private fun event(key:String,event:String=key):CareNotificationEntry=BannerCatalog[key].let {
        CareNotificationEntry(id,event,it.key,it.title,it.body,it.mood,it.art,System.currentTimeMillis(),source="Simulation")
    }
    private fun awaitNotification(tag:String):android.service.notification.StatusBarNotification {
        val until=System.currentTimeMillis()+4000
        while(System.currentTimeMillis()<until) {
            manager.activeNotifications.firstOrNull {it.tag=="$id:$tag"}?.let {return it}
            Thread.sleep(40)
        }
        error("Android did not post $tag")
    }
    @After fun cleanup() {
        manager.activeNotifications.filter {it.tag?.startsWith("$id:")==true}.forEach {manager.cancel(it.tag,it.id)}
        CareJournal(context,id).deleteVehicle()
        CareJournal(base,id).deleteVehicle()
        base.deleteDatabase(db)
    }
    @Test fun allSixtySixDesignsPostWithBothThemesAndPersistOnlyOnce() {
        assertTrue(CareNotifications.allowed(context))
        val definitions=BannerCatalog.entries.values.filter {it.art!=null}
        assertEquals(66,definitions.size)
        for(dark in listOf(false,true)) {
            val config=Configuration(base.resources.configuration).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed=object:ContextWrapper(context) {
                private val configured=base.createConfigurationContext(config)
                override fun getResources()=configured.resources
            }
            for(d in definitions) {
                val resource=themed.resources.getIdentifier(d.art+if(dark) "_dark" else "_light","drawable",themed.packageName)
                assertNotEquals(d.key,0,resource)
                val dimensions=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeResource(themed.resources,resource,dimensions)
                assertEquals(1080,dimensions.outWidth);assertEquals(340,dimensions.outHeight)
                val e=event(d.key,"${d.key}:$dark")
                assertEquals(NotificationDelivery.Delivered,BannerNotifications.dispatch(themed,e))
                val posted=awaitNotification(e.eventId)
                assertEquals(d.title,posted.notification.extras.getString(Notification.EXTRA_TITLE))
                assertEquals("시뮬레이션",posted.notification.extras.getString(Notification.EXTRA_SUB_TEXT))
                assertEquals("android.app.Notification\$BigPictureStyle",posted.notification.extras.getString(Notification.EXTRA_TEMPLATE))
                val whenPosted=posted.postTime
                BannerNotifications.dispatch(themed,e)
                assertEquals(whenPosted,awaitNotification(e.eventId).postTime)
                manager.cancel(posted.tag,posted.id)
                // Android per-package rate limits still apply to a simulation.
                Thread.sleep(230)
            }
        }
        NotificationHistory(context).use {h ->
            assertEquals(132,h.recent(id,200).size)
            val e=h.recent(id,1).single();h.markRead(id,e.eventId)
        }
        NotificationHistory(context).use {assertTrue(it.recent(id,1).single().read)}
    }
    @Test fun disabledAndBlockedDeliveriesRemainInHistoryWithoutSuccessClaims() {
        val disabled=event("FUEL_NEEDED","disabled")
        assertEquals(NotificationDelivery.Disabled,BannerNotifications.dispatch(context,disabled,false))
        assertFalse(manager.activeNotifications.any {it.tag=="$id:disabled"})
        NotificationHistory(context).use {assertEquals(NotificationDelivery.Disabled,it.get(id,"disabled")!!.delivery)}
        assertEquals(NotificationDelivery.Delivered,BannerNotifications.dispatch(context,disabled,true))
        awaitNotification("disabled")
        // The permission branch is tested without modifying the device's other applications.
        val denied=object:ContextWrapper(context) {
            override fun checkSelfPermission(permission:String)=android.content.pm.PackageManager.PERMISSION_DENIED
        }
        assertEquals(NotificationDelivery.PermissionBlocked,BannerNotifications.dispatch(denied,event("WASH_NEEDED","denied")))
        NotificationHistory(context).use {assertEquals(NotificationDelivery.PermissionBlocked,it.get(id,"denied")!!.delivery)}
    }
    @Test fun uncertainWashHasRealCorrectionActionButClearFacilityUsesCompletion()=runBlocking {
        val journal=CareJournal(base,id).apply {saveAuthority(CareAuthority(id,1,profile().toString(),"[]"))}
        assertTrue(CareExchange.detected(base,id,DetectedWash("shared",Instant.now().minusSeconds(60))))
        assertEquals(100,journal.score(JSONArray(journal.authority()!!.records)).displayed)
        val question=awaitNotification("detected:shared")
        assertEquals("세차한 것 같아!",question.notification.extras.getString(Notification.EXTRA_TITLE))
        assertNotNull(journal.pendingWashConfirmation())
        question.notification.actions.single {it.title.toString()=="세차 안 했어요"}.actionIntent.send()
        val until=System.currentTimeMillis()+4000
        while(journal.pendingWashConfirmation()!=null && System.currentTimeMillis()<until) Thread.sleep(30)
        assertNull(journal.pendingWashConfirmation())
        assertNull(journal.score(JSONArray(journal.authority()!!.records)).displayed)
        assertFalse(CareExchange.detected(base,id,DetectedWash("shared",Instant.now())))
        NotificationHistory(base).use {assertTrue(it.get(id,"detected:shared")!!.cancelled)}
        assertTrue(CareExchange.detected(base,id,DetectedWash("separated",Instant.now(),WashCompletionEvidence.ClearFacilityPattern)))
        val done=awaitNotification("detected:separated")
        assertEquals(BannerCatalog["WASH_DONE"].title,done.notification.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(done.notification.actions.any {it.title.toString()=="세차 안 했어요"})
        assertNull(journal.pendingWashConfirmation())
        assertEquals(100,journal.score(JSONArray(journal.authority()!!.records)).displayed)
    }
    @Test fun stateTransitionsDeduplicateAndDoNotInventRepairsOnDisconnectOrNormal() {
        val journal=seed();val a=journal.authority()!!;val score=journal.score(JSONArray(a.records))
        fun telemetry(on:Boolean,status:String="Available",eligible:Boolean=true,at:Long=System.currentTimeMillis())=
            JSONObject().put("warnings",JSONObject().put("ENGINE",JSONObject().put("status",status).put("warning",on)
                .put("source","Real").put("eligible",eligible).put("at",at)))
        fun evaluate(t:JSONObject,fuel:Int=80)=BannerNotifications.evaluate(context,journal,a,score,t,
            JSONObject().put("observedAt",System.currentTimeMillis()).put("fuel",fuel))
        evaluate(telemetry(true))
        val first=NotificationHistory(context).use {it.recent(id).single()}
        awaitNotification(first.eventId)
        evaluate(telemetry(true));assertEquals(1,NotificationHistory(context).use {it.recent(id).size})
        evaluate(telemetry(false,"Disconnected"));assertEquals(1,NotificationHistory(context).use {it.recent(id).size})
        evaluate(telemetry(false));assertFalse(manager.activeNotifications.any {it.tag=="$id:${first.eventId}"})
        evaluate(telemetry(true,eligible=false));assertEquals(1,NotificationHistory(context).use {it.recent(id).size})
        evaluate(telemetry(true,at=System.currentTimeMillis()-61000));assertEquals(1,NotificationHistory(context).use {it.recent(id).size})
        // A newly sent envelope must not refresh old cached fuel or unclassified warning values.
        BannerNotifications.evaluate(context,journal,a,score,null,JSONObject().put("observedAt",System.currentTimeMillis())
            .put("fuel",5).put("fuelAt",System.currentTimeMillis()-61000).put("lowFuelAt",System.currentTimeMillis()-61000)
            .put("vehicleWarningAt",System.currentTimeMillis()-61000)
            .put("signals",JSONObject().put("lowFuel",true).put("vehicleWarning",true)))
        assertEquals(1,NotificationHistory(context).use {it.recent(id).size})
        evaluate(telemetry(true));assertEquals(2,NotificationHistory(context).use {it.recent(id).size})
        evaluate(telemetry(true),10)
        NotificationHistory(context).use {assertEquals("FUEL_ENGINE",it.recent(id).first().bannerKey)}
        assertFalse(NotificationHistory(context).use {it.recent(id).any {e -> e.bannerKey=="OIL_DONE"}})
    }
    @Test fun explicitCurrentRecordsCompleteButInitialImportAndMemoEditsDoNot() {
        val journal=seed();val before=journal.authority()!!
        val today=LocalDate.now(CARE_ZONE).toString()
        val records=JSONArray().put(JSONObject().put("id","manual").put("kind","wash").put("date",today).put("source","Manual"))
        val after=before.copy(version=2,records=records.toString())
        BannerNotifications.recordChanges(context,null,after)
        assertTrue(NotificationHistory(context).use {it.recent(id).isEmpty()})
        BannerNotifications.recordChanges(context,before,after)
        val event=NotificationHistory(context).use {it.recent(id).single()};assertEquals("WASH_DONE",event.bannerKey)
        awaitNotification(event.eventId)
        val memo=JSONArray(after.records).apply {getJSONObject(0).put("detail","메모 수정")}
        BannerNotifications.recordChanges(context,after,after.copy(version=3,records=memo.toString()))
        assertEquals(1,NotificationHistory(context).use {it.recent(id).size})
    }
    @Test fun notificationOpensActualLauncherAndSelectedHistoryEvent() {
        manager.cancelAll() // This is the disposable verification package only.
        val e=event("WASH_DONE","tap")
        // Use the real verification context so the notification receiver/activity sees the same DB.
        BannerNotifications.dispatch(base,e)
        val monitor=InstrumentationRegistry.getInstrumentation().addMonitor(MainActivity::class.java.name,null,false)
        try {
            awaitNotification("tap")
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.serviceInfo=automation.serviceInfo.apply {
                flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            automation.executeShellCommand("cmd statusbar expand-notifications").let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
            }
            val until=System.currentTimeMillis()+4000
            var clicked=false
            while(!clicked && System.currentTimeMillis()<until) {
                val roots=automation.windows.mapNotNull {it.root}+listOfNotNull(automation.rootInActiveWindow)
                fun find(node:android.view.accessibility.AccessibilityNodeInfo?):android.view.accessibility.AccessibilityNodeInfo? {
                    if(node==null) return null
                    if(node.text?.toString()?.contains(BannerCatalog["WASH_DONE"].title)==true) return node
                    for(n in 0 until node.childCount) find(node.getChild(n))?.let {return it}
                    return null
                }
                val node=roots.firstNotNullOfOrNull {find(it)}
                if(node!=null) {
                    val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                    val now=android.os.SystemClock.uptimeMillis()
                    val down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                    val up=android.view.MotionEvent.obtain(now,now+80,android.view.MotionEvent.ACTION_UP,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                    down.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;up.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    clicked=automation.injectInputEvent(down,true) && automation.injectInputEvent(up,true)
                    down.recycle();up.recycle()
                }
                if(!clicked) Thread.sleep(60)
            }
            if(!clicked) {
                val nodes=StringBuilder()
                fun dump(node:android.view.accessibility.AccessibilityNodeInfo?,depth:Int=0) {
                    if(node==null || depth>35) return
                    nodes.append(" ".repeat(depth)).append(node.packageName).append(':').append(node.text).append('|').append(node.contentDescription).append('\n')
                    for(n in 0 until node.childCount) dump(node.getChild(n),depth+1)
                }
                automation.windows.forEach {dump(it.root)};dump(automation.rootInActiveWindow)
                java.io.File(base.filesDir,"banner-tap-nodes.txt").writeText(nodes.toString())
                automation.executeShellCommand("screencap -p /data/local/tmp/banner-tap-failure.png").let {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
                }
            }
            assertTrue("Tap the real System UI notification instead of a background PendingIntent.send",clicked)
            val activity=monitor.waitForActivityWithTimeout(5000)
            assertNotNull("Notification activity must resolve with applicationIdSuffix",activity)
            assertEquals("tap",activity.intent.getStringExtra("careEvent"))
            assertEquals(id,activity.intent.getStringExtra("vehicleId"))
            val readUntil=System.currentTimeMillis()+4000
            while(!NotificationHistory(base).use {it.get(id,"tap")!!.read} && System.currentTimeMillis()<readUntil) Thread.sleep(50)
            assertTrue("The selected history detail should open and persist read state",NotificationHistory(base).use {it.get(id,"tap")!!.read})
            automation.executeShellCommand("screencap -p /data/local/tmp/banner-tap-history.png").let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
            NotificationHistory(base).use {it.deleteVehicle(id)}
        }
    }
}
